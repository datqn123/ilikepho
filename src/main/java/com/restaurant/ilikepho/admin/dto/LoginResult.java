package com.restaurant.ilikepho.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * Kết quả đăng nhập admin thành công: thông tin đặt cookie phiên và cookie ghi nhớ đăng nhập.
 */
@Data
@AllArgsConstructor
public class LoginResult {

    /**
     * Chuỗi session ID gốc đặt vào cookie phiên ADMIN_SESSION.
     */
    @ToString.Exclude
    private final String rawSessionId;

    /**
     * Chuỗi remember token gốc đặt vào cookie ADMIN_REMEMBER; null khi đăng nhập không ghi nhớ.
     */
    @ToString.Exclude
    private final String rawRememberToken;

}
