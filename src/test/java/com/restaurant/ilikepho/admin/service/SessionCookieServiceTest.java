package com.restaurant.ilikepho.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra sinh session ID và thuộc tính cookie phiên / cookie ghi nhớ đăng nhập của admin.
 */
class SessionCookieServiceTest {

    private final SessionIdGenerator sessionIdGenerator = new SessionIdGenerator();

    @Test
    void generate_traVeChuoiNganNhienAnToanKhacNhauMoiLan() {
        String first = sessionIdGenerator.generate();
        String second = sessionIdGenerator.generate();

        // 32 bytes = 256-bit -> 43 ký tự Base64 URL-safe không padding
        assertThat(first).hasSize(43);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createSessionCookie_dungTenPathHttpOnlySameSite() {
        SessionCookieService service = new SessionCookieService(false, 30);

        ResponseCookie cookie = service.createSessionCookie("session-id-abc");

        assertThat(cookie.getName()).isEqualTo("ADMIN_SESSION");
        assertThat(cookie.getValue()).isEqualTo("session-id-abc");
        assertThat(cookie.getPath()).isEqualTo("/admin");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void createSessionCookie_secureTheoCauHinhMoiTruong() {
        SessionCookieService dev = new SessionCookieService(false, 30);
        assertThat(dev.createSessionCookie("x").isSecure()).isFalse();

        SessionCookieService prod = new SessionCookieService(true, 30);
        assertThat(prod.createSessionCookie("x").isSecure()).isTrue();
    }

    @Test
    void createRememberMeCookie_dungTenPathVaMaxAgeTheoCauHinh() {
        SessionCookieService service = new SessionCookieService(false, 30);

        ResponseCookie cookie = service.createRememberMeCookie("remember-token");

        assertThat(cookie.getName()).isEqualTo("ADMIN_REMEMBER");
        assertThat(cookie.getValue()).isEqualTo("remember-token");
        assertThat(cookie.getPath()).isEqualTo("/admin");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void createExpiredRememberMeCookie_maxAge0DeXoaCookie() {
        SessionCookieService service = new SessionCookieService(false, 30);

        ResponseCookie cookie = service.createExpiredRememberMeCookie();

        assertThat(cookie.getName()).isEqualTo("ADMIN_REMEMBER");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
