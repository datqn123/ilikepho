package com.restaurant.ilikepho.admin.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ hash và kiểm tra mật khẩu cho tài khoản admin.
 * Dùng bcrypt: salt riêng từng user được sinh ngẫu nhiên và nhúng sẵn trong chuỗi hash,
 * nên không bao giờ lưu mật khẩu dạng plain text.
 */
@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * Băm mật khẩu dạng thô thành chuỗi bcrypt kèm salt riêng.
     *
     * @param rawPassword mật khẩu dạng thô cần băm
     * @return chuỗi hash bcrypt của mật khẩu
     */
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * So sánh mật khẩu dạng thô với chuỗi hash đã lưu.
     *
     * @param rawPassword  mật khẩu dạng thô cần kiểm tra
     * @param passwordHash chuỗi hash bcrypt đã lưu
     * @return {@code true} nếu mật khẩu khớp với hash, ngược lại {@code false}
     */
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
