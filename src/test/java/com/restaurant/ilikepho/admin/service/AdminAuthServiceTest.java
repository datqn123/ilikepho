package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.dto.LoginResult;
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
 * Kiểm tra luồng đăng nhập/đăng xuất admin, bao gồm remember token khi ghi nhớ đăng nhập.
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

    @Mock
    private AdminRememberMeService adminRememberMeService;

    @InjectMocks
    private AdminAuthService adminAuthService;

    private Admin adminCoSan() {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash("hash");
        return admin;
    }

    @Test
    void login_dungTaiKhoanKhongNho_traVeSessionIdVaXoaTokenCu() {
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(adminCoSan()));
        when(passwordService.matches("matkhau", "hash")).thenReturn(true);
        when(sessionIdGenerator.generate()).thenReturn("raw-session-id");

        Optional<LoginResult> result = adminAuthService.login("admin", "matkhau", false);

        assertThat(result).isPresent();
        assertThat(result.get().getRawSessionId()).isEqualTo("raw-session-id");
        assertThat(result.get().getRawRememberToken()).isNull();
        verify(adminSessionService).createSession(1L, "raw-session-id");
        verify(adminRememberMeService).deleteAllByAdminId(1L);
        verify(adminRememberMeService, never()).createToken(any());
    }

    @Test
    void login_coGhiNho_taoRememberTokenMoi() {
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(adminCoSan()));
        when(passwordService.matches("matkhau", "hash")).thenReturn(true);
        when(sessionIdGenerator.generate()).thenReturn("raw-session-id");
        when(adminRememberMeService.createToken(1L)).thenReturn("raw-remember");

        Optional<LoginResult> result = adminAuthService.login("admin", "matkhau", true);

        assertThat(result).isPresent();
        assertThat(result.get().getRawRememberToken()).isEqualTo("raw-remember");
        verify(adminRememberMeService, never()).deleteAllByAdminId(any());
    }

    @Test
    void login_saiMatKhau_traVeRongVaKhongTaoPhien() {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setPasswordHash("hash");
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordService.matches(anyString(), anyString())).thenReturn(false);

        assertThat(adminAuthService.login("admin", "sai", true)).isEmpty();
        verify(adminSessionService, never()).createSession(any(), anyString());
        verify(adminRememberMeService, never()).createToken(any());
    }

    @Test
    void login_taiKhoanKhongTonTai_traVeRong() {
        when(adminRepository.findByUsername("khong-co")).thenReturn(Optional.empty());

        assertThat(adminAuthService.login("khong-co", "matkhau", false)).isEmpty();
    }

    @Test
    void logout_khoaPhienVaXoaRememberTheoAdminId() {
        AdminSession session = new AdminSession();
        session.setAdminId(5L);
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.of(session));

        adminAuthService.logout("raw-id", null);

        verify(adminSessionService).lockSession(session);
        verify(adminRememberMeService).deleteAllByAdminId(5L);
        verify(adminRememberMeService, never()).deleteByRawToken(anyString());
    }

    @Test
    void logout_phienChetVanXoaRememberTheoToken() {
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.empty());

        adminAuthService.logout("raw-id", "raw-remember");

        verify(adminSessionService, never()).lockSession(any());
        verify(adminRememberMeService).deleteByRawToken("raw-remember");
    }
}
