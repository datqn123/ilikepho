package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.entity.Admin;
import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra luồng đăng nhập/đăng xuất admin.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private SessionIdGenerator sessionIdGenerator;

    @Mock
    private AdminSessionService adminSessionService;

    @InjectMocks
    private AdminAuthService adminAuthService;

    @Test
    void login_dungTaiKhoanVaMatKhau_traVeSessionIdVaTaoPhien() {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash("hash");
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordService.matches("matkhau", "hash")).thenReturn(true);
        when(sessionIdGenerator.generate()).thenReturn("raw-session-id");

        Optional<String> result = adminAuthService.login("admin", "matkhau");

        assertThat(result).contains("raw-session-id");
        verify(adminSessionService).createSession(1L, "raw-session-id");
    }

    @Test
    void login_saiMatKhau_traVeRongVaKhongTaoPhien() {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setPasswordHash("hash");
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordService.matches(anyString(), anyString())).thenReturn(false);

        assertThat(adminAuthService.login("admin", "sai")).isEmpty();
        verify(adminSessionService, never()).createSession(any(), any());
    }

    @Test
    void login_taiKhoanKhongTonTai_traVeRong() {
        when(adminRepository.findByUsername("khong-co")).thenReturn(Optional.empty());

        assertThat(adminAuthService.login("khong-co", "matkhau")).isEmpty();
    }

    @Test
    void logout_khoaPhienDangHoatDong() {
        AdminSession session = new AdminSession();
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.of(session));

        adminAuthService.logout("raw-id");

        verify(adminSessionService).lockSession(session);
    }
}
