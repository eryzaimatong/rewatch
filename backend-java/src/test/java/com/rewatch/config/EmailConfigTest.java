package com.rewatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

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
}
