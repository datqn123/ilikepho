package com.restaurant.ilikepho.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Phiên đăng nhập của tài khoản admin.
 * Chỉ lưu hash của session ID (không bao giờ lưu chuỗi gốc);
 * phiên bị khoá bằng xoá logic (status = LOCKED) để giữ dấu vết.
 */
@Entity
@Table(name = "admin_session")
@Data
@NoArgsConstructor
public class AdminSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_hash", nullable = false, unique = true, length = 100)
    private String sessionHash;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

}
