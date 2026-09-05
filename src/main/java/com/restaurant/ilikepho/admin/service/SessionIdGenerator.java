package com.restaurant.ilikepho.admin.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bộ sinh session ID ngẫu nhiên an toàn cho phiên đăng nhập admin.
 */
@Service
public class SessionIdGenerator {

    private static final int SESSION_ID_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Sinh session ID ngẫu nhiên an toàn (256-bit) dạng Base64 URL-safe không padding.
     *
     * @return chuỗi session ID mới
     */
    public String generate() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
