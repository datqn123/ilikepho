package com.restaurant.ilikepho.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Bộ tạo cookie phiên đăng nhập và cookie "ghi nhớ đăng nhập" của admin.
 * Cookie có HttpOnly, Secure (theo cấu hình môi trường) và SameSite=Lax.
 */
@Service
public class SessionCookieService {

    public static final String SESSION_COOKIE_NAME = "ADMIN_SESSION";

    /**
     * Tên cookie "ghi nhớ đăng nhập" chứa remember token gốc.
     */
    public static final String REMEMBER_ME_COOKIE_NAME = "ADMIN_REMEMBER";

    /**
     * Tên cookie chống login-CSRF cho form đăng nhập (double-submit với hidden field).
     */
    public static final String LOGIN_CSRF_COOKIE_NAME = "ADMIN_LOGIN_CSRF";

    private static final String SESSION_COOKIE_PATH = "/admin";

    private final boolean secure;

    private final long rememberMeMaxAgeDays;

    private final long loginCsrfMaxAgeMinutes;

    public SessionCookieService(@Value("${admin.session.cookie.secure:false}") boolean secure,
                                @Value("${admin.remember-me.max-age-days:30}") long rememberMeMaxAgeDays,
                                @Value("${admin.login-csrf.max-age-minutes:30}") long loginCsrfMaxAgeMinutes) {
        this.secure = secure;
        this.rememberMeMaxAgeDays = rememberMeMaxAgeDays;
        this.loginCsrfMaxAgeMinutes = loginCsrfMaxAgeMinutes;
    }

    /**
     * Tạo cookie phiên admin chứa chuỗi session ID gốc.
     *
     * @param sessionId chuỗi session ID gốc đặt trong cookie
     * @return cookie phiên admin
     */
    public ResponseCookie createSessionCookie(String sessionId) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .build();
    }

    /**
     * Tạo cookie phiên admin hết hạn ngay (maxAge 0) để xoá cookie trên trình duyệt khi đăng xuất.
     *
     * @return cookie phiên admin đã hết hạn
     */
    public ResponseCookie createExpiredSessionCookie() {
        return ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /**
     * Tạo cookie "ghi nhớ đăng nhập" chứa remember token gốc, sống lâu theo số ngày cấu hình.
     *
     * @param token chuỗi remember token gốc đặt trong cookie
     * @return cookie ghi nhớ đăng nhập
     */
    public ResponseCookie createRememberMeCookie(String token) {
        return ResponseCookie.from(REMEMBER_ME_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(Duration.ofDays(rememberMeMaxAgeDays))
                .build();
    }

    /**
     * Tạo cookie "ghi nhớ đăng nhập" hết hạn ngay (maxAge 0) để xoá cookie trên trình duyệt khi đăng xuất.
     *
     * @return cookie ghi nhớ đăng nhập đã hết hạn
     */
    public ResponseCookie createExpiredRememberMeCookie() {
        return ResponseCookie.from(REMEMBER_ME_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    /**
     * Tạo cookie chống login-CSRF ngắn hạn chứa token form đăng nhập
     * (double-submit: giá trị trong cookie phải khớp hidden field khi POST login).
     *
     * @param token chuỗi token login-CSRF đặt trong cookie
     * @return cookie login-CSRF
     */
    public ResponseCookie createLoginCsrfCookie(String token) {
        return ResponseCookie.from(LOGIN_CSRF_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(Duration.ofMinutes(loginCsrfMaxAgeMinutes))
                .build();
    }
}
