package com.restaurant.ilikepho.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token "ghi nhớ đăng nhập" (remember-me) của tài khoản admin.
 * Chỉ lưu hash SHA-256 của token (không lưu chuỗi gốc — DB lộ không tái dùng được);
 * mỗi admin chỉ có tối đa một token sống (tạo token mới thì xoá token cũ).
 */
@Entity
@Table(name = "admin_remember_me")
@Data
@NoArgsConstructor
public class AdminRememberMe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Hash SHA-256 (hex) của token gốc đặt trong cookie ghi nhớ đăng nhập.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 100)
    private String tokenHash;

    /**
     * Khoá ngoại logic tới tài khoản admin (chỉ dùng id, không dùng quan hệ JPA).
     */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

}
