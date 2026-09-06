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

    private static final String SESSION_COOKIE_PATH = "/admin";

    private final boolean secure;

    private final long rememberMeMaxAgeDays;

    public SessionCookieService(@Value("${admin.session.cookie.secure:false}") boolean secure,
                                @Value("${admin.remember-me.max-age-days:30}") long rememberMeMaxAgeDays) {
        this.secure = secure;
        this.rememberMeMaxAgeDays = rememberMeMaxAgeDays;
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
}
