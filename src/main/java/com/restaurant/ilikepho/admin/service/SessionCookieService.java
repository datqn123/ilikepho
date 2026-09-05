package com.restaurant.ilikepho.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Bộ tạo cookie phiên đăng nhập admin.
 * Cookie có HttpOnly, Secure (theo cấu hình môi trường) và SameSite=Lax.
 */
@Service
public class SessionCookieService {

    public static final String SESSION_COOKIE_NAME = "ADMIN_SESSION";

    private static final String SESSION_COOKIE_PATH = "/admin";

    private final boolean secure;

    public SessionCookieService(@Value("${admin.session.cookie.secure:false}") boolean secure) {
        this.secure = secure;
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
}
