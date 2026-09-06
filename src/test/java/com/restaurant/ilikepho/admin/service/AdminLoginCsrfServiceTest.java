package com.restaurant.ilikepho.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra sinh và xác thực token chống login-CSRF cho form đăng nhập admin.
 */
@ExtendWith(MockitoExtension.class)
class AdminLoginCsrfServiceTest {

    @Mock
    private SessionIdGenerator sessionIdGenerator;

    @InjectMocks
    private AdminLoginCsrfService adminLoginCsrfService;

    @Test
    void generateToken_traVeChuoiNgauNhienKhacNhauMoiLan() {
        when(sessionIdGenerator.generate()).thenReturn("token-1", "token-2");

        String first = adminLoginCsrfService.generateToken();
        String second = adminLoginCsrfService.generateToken();

        assertThat(first).isEqualTo("token-1");
        assertThat(second).isEqualTo("token-2");
    }

    @Test
    void isValid_haiTokenKhop_traVeTrue() {
        assertThat(adminLoginCsrfService.isValid("token-abc", "token-abc")).isTrue();
    }

    @Test
    void isValid_saiToken_traVeFalse() {
        assertThat(adminLoginCsrfService.isValid("token-dung", "token-sai")).isFalse();
    }

    @Test
    void isValid_thieuCookieHoacThieuField_traVeFalse() {
        assertThat(adminLoginCsrfService.isValid(null, "token")).isFalse();
        assertThat(adminLoginCsrfService.isValid("token", null)).isFalse();
    }

    @Test
    void isValid_tokenRongKhoangTrang_traVeFalse() {
        assertThat(adminLoginCsrfService.isValid("", "token")).isFalse();
        assertThat(adminLoginCsrfService.isValid("token", "  ")).isFalse();
    }
}
