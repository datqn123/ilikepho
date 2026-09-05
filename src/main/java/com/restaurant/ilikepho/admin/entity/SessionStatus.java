package com.restaurant.ilikepho.admin.entity;

/**
 * Trạng thái của phiên đăng nhập admin.
 * ACTIVE: phiên đang hoạt động; LOCKED: phiên bị khoá (xoá logic, giữ dấu vết).
 */
public enum SessionStatus {
    ACTIVE,
    LOCKED
}
