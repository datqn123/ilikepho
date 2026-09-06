package com.restaurant.ilikepho.admin.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Sinh và xác thực token chống login-CSRF cho POST /admin/login (cơ chế double-submit,
 * độc lập với CSRF token của phiên đã đăng nhập): token ngắn hạn được đặt vào cookie
 * riêng và hidden field của form login; POST phải mang hai giá trị khớp nhau
 * mới được đưa vào xác thực tài khoản.
 */
@Service
public class AdminLoginCsrfService {

    private final SessionIdGenerator sessionIdGenerator;

    public AdminLoginCsrfService(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
     * Sinh token login-CSRF ngẫu nhiên an toàn (256-bit) để đặt vào cookie và hidden field
     * của form đăng nhập; không lưu trạng thái server (double-submit).
     *
     * @return chuỗi token gốc
     */
    public String generateToken() {
        return sessionIdGenerator.generate();
    }

    /**
     * So token gửi từ form với token trong cookie bằng {@link MessageDigest#isEqual}
     * (constant-time, chống dò token qua thời gian phản hồi); thiếu một trong hai
     * hoặc token rỗng coi như không hợp lệ.
     *
     * @param cookieToken    token đọc từ cookie login-CSRF, có thể null
     * @param submittedToken token gửi kèm form đăng nhập, có thể null
     * @return {@code true} nếu hai token khớp
     */
    public boolean isValid(String cookieToken, String submittedToken) {
        if (cookieToken == null || cookieToken.isBlank()
                || submittedToken == null || submittedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                submittedToken.getBytes(StandardCharsets.UTF_8));
    }
}
