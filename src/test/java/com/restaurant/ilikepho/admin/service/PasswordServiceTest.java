package com.restaurant.ilikepho.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra hành vi hash và so sánh mật khẩu của PasswordService.
 */
class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void hash_taoChuoiKhacNhauChoCungMotMatKhau() {
        String raw = "Admin@123";

        String first = passwordService.hash(raw);
        String second = passwordService.hash(raw);

        // bcrypt sinh salt riêng mỗi lần -> cùng mật khẩu cho hash khác nhau, không phải plain text
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(raw);
    }

    @Test
    void matches_traVeTrueVoiMatKhauDung() {
        String raw = "Admin@123";
        String hash = passwordService.hash(raw);

        assertThat(passwordService.matches(raw, hash)).isTrue();
    }

    @Test
    void matches_traVeFalseVoiMatKhauSai() {
        String hash = passwordService.hash("Admin@123");

        assertThat(passwordService.matches("SaiMatKhau", hash)).isFalse();
    }
}
