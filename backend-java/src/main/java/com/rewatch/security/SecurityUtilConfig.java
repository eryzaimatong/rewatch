package com.rewatch.security;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Wires {@code rewatch.security.trust-proxy-headers} into {@link SecurityUtil}, which stays a static utility so every existing call site is unaffected. */
@Component
public class SecurityUtilConfig {

    public SecurityUtilConfig(@Value("${rewatch.security.trust-proxy-headers:false}") boolean trustProxyHeaders) {
        SecurityUtil.setTrustProxyHeaders(trustProxyHeaders);
    }
}
