package com.rewatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.rewatch.service.email.EmailSender;
import com.rewatch.service.email.HttpEmailSender;
import com.rewatch.service.email.SmtpEmailSender;

/**
 * The P0 requirement this covers: "fail fast at boot: if the prod profile
 * is missing email config, the app refuses to start rather than starting
 * broken." Proven here at the Spring context level, not just read off the
 * source — an ApplicationContextRunner actually attempts bean creation the
 * same way a real boot would, so a missing @Value with no fallback fails
 * context refresh exactly as it would in production.
 */
class EmailConfigTest {

    @Configuration
    static class FakeMailSenderConfig {
        @Bean
        JavaMailSender javaMailSender() {
            return new JavaMailSenderImpl();
        }

        // Only load-bearing for the 4th test below (WebConfig carries
        // @EnableCaching at the class level, which requires a CacheManager
        // bean to exist in ANY context that includes it, regardless of
        // whether anything in that specific context is actually
        // @Cacheable) — harmless extra bean in the other three tests here,
        // which never load WebConfig at all.
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    // PropertyPlaceholderAutoConfiguration matters here, not just boilerplate:
    // without it, ApplicationContextRunner does NOT resolve ${...} in @Value
    // at all — it silently injects the literal unresolved string ("${resend.
    // api-key}") instead of throwing, which the first version of this test
    // caught doing exactly that (a passing context, with a bean holding the
    // literal placeholder text as its "api key"). A real Spring Boot app
    // auto-configures this; a bare ApplicationContextRunner doesn't unless
    // told to.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(FakeMailSenderConfig.class, EmailConfig.class)
            .withPropertyValues("rewatch.mail.from=noreply@rewatch.local");

    @Test
    void defaultsToSmtpAndNeverRequiresAResendApiKey() {
        runner.run((AssertableApplicationContext context) -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
        });
    }

    @Test
    void httpModeWithNoApiKeyRefusesToStartInsteadOfStartingBroken() {
        runner.withPropertyValues("rewatch.mail.provider=http")
                .run((AssertableApplicationContext context) -> {
                    // resend.api-key has no fallback default — this is the actual
                    // fail-fast: a missing required property fails context
                    // refresh, the same as JWT_SECRET/MAIL_USERNAME already do in
                    // application-prod.properties, rather than booting with a
                    // null/blank API key that would silently fail every send.
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void httpModeWithAnApiKeySelectsHttpEmailSender() {
        runner.withPropertyValues("rewatch.mail.provider=http", "resend.api-key=test-key-123")
                .run((AssertableApplicationContext context) -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class)).isInstanceOf(HttpEmailSender.class);
                });
    }

    /**
     * A stand-in for TmdbClient's exact injection shape (a single-arg
     * constructor taking a plain, unqualified RestTemplate) WITHOUT its
     * unrelated dependencies (tmdb.api.url/api.key, @Cacheable's CacheManager
     * requirement) — those kept surfacing as new test-setup gaps unrelated to
     * the actual thing under test, each costing a full context-boot attempt
     * to discover on an environment where that took 9-14 minutes per run.
     * This isolates the one real question: does an unqualified RestTemplate
     * constructor param still resolve unambiguously once EmailConfig adds a
     * second RestTemplate bean to the context.
     */
    @Component
    static class UnqualifiedRestTemplateConsumer {
        final RestTemplate restTemplate;
        UnqualifiedRestTemplateConsumer(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }
    }

    /**
     * Reproduces the actual production failure's mechanism directly, not a
     * plausible guess at its cause: deploying EmailConfig's resendRestTemplate
     * bean alongside WebConfig's existing tmdbRestTemplate crashed real boot
     * with "expected single matching bean but found 2" the moment
     * TmdbClient's unqualified `RestTemplate rest` constructor param needed
     * resolving — confirmed live in Render's logs. EmailConfigTest alone
     * never combined WebConfig and a RestTemplate consumer in the same
     * context, so it could not have caught this; this test exists
     * specifically to close that gap rather than trust @Primary fixes it by
     * reading the annotation.
     */
    @Test
    void addingEmailConfigDoesNotBreakAnUnqualifiedRestTemplateConsumer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RestTemplateAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(FakeMailSenderConfig.class, WebConfig.class, EmailConfig.class,
                        UnqualifiedRestTemplateConsumer.class)
                .withPropertyValues(
                        "rewatch.mail.from=noreply@rewatch.local",
                        "rewatch.cors.allowed-origins=http://localhost:5173",
                        "tmdb.connect-timeout-ms=3000",
                        "tmdb.read-timeout-ms=5000")
                .run((AssertableApplicationContext context) -> {
                    // The real regression check: this failed to even boot before
                    // the @Primary fix, with an UnsatisfiedDependencyException
                    // naming exactly resendRestTemplate/tmdbRestTemplate as the
                    // two competing candidates. A clean boot IS the proof.
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UnqualifiedRestTemplateConsumer.class);
                    RestTemplate resolved = context.getBean(UnqualifiedRestTemplateConsumer.class).restTemplate;
                    RestTemplate tmdbBean = context.getBean("tmdbRestTemplate", RestTemplate.class);
                    assertThat(resolved).isSameAs(tmdbBean);
                });
    }
}
