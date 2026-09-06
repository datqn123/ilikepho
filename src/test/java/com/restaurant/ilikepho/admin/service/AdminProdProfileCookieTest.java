package com.restaurant.ilikepho.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm chứng profile production: khi chạy với profile {@code prod}
 * (application-prod.properties bật admin.session.cookie.secure=true),
 * mọi cookie của hệ admin được dựng với cờ Secure.
 */
@SpringBootTest(properties = "spring.profiles.active=prod")
class AdminProdProfileCookieTest {

    @Autowired
    private SessionCookieService sessionCookieService;

    @Test
    void profileProd_moCookiePhienRememberVaLoginCsrf_deuCooSecure() {
        assertThat(sessionCookieService.createSessionCookie("session-id").isSecure()).isTrue();
        assertThat(sessionCookieService.createRememberMeCookie("remember-token").isSecure()).isTrue();
        assertThat(sessionCookieService.createLoginCsrfCookie("login-csrf").isSecure()).isTrue();
    }
}
