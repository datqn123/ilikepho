package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.entity.AdminRememberMe;
import com.restaurant.ilikepho.admin.repository.AdminRememberMeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra dịch vụ token "ghi nhớ đăng nhập": tạo token, tra token còn hạn,
 * xoay vòng token và xoá token.
 */
@ExtendWith(MockitoExtension.class)
class AdminRememberMeServiceTest {

    @Mock
    private AdminRememberMeRepository adminRememberMeRepository;

    @Mock
    private AdminSessionService adminSessionService;

    @Mock
    private SessionIdGenerator sessionIdGenerator;

    private AdminRememberMeService service;

    @BeforeEach
    void setUp() {
        service = new AdminRememberMeService(adminRememberMeRepository, adminSessionService,
                sessionIdGenerator, 30);
    }

    private AdminRememberMe tokenHetHanSauNhieuNgay(long days) {
        AdminRememberMe token = new AdminRememberMe();
        token.setAdminId(1L);
        token.setTokenHash("hash");
        token.setExpiresAt(LocalDateTime.now(AdminSessionService.SESSION_ZONE).plusDays(days));
        return token;
    }

    @Test
    void createToken_xoaTokenCuVaLuuHashTraVeTokenGoc() {
        when(sessionIdGenerator.generate()).thenReturn("raw-token");
        when(adminSessionService.hashSessionId("raw-token")).thenReturn("hash");
        ArgumentCaptor<AdminRememberMe> captor = ArgumentCaptor.forClass(AdminRememberMe.class);

        String rawToken = service.createToken(1L);

        assertThat(rawToken).isEqualTo("raw-token");
        verify(adminRememberMeRepository).deleteByAdminId(1L);
        verify(adminRememberMeRepository).save(captor.capture());
        assertThat(captor.getValue().getAdminId()).isEqualTo(1L);
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash");
        assertThat(captor.getValue().getExpiresAt()).isAfter(captor.getValue().getCreatedAt());
    }

    @Test
    void createToken_hanDungTheoSoNgayCauHinh() {
        AdminRememberMeService serviceHan7Ngay = new AdminRememberMeService(adminRememberMeRepository,
                adminSessionService, sessionIdGenerator, 7);
        when(sessionIdGenerator.generate()).thenReturn("raw-token");
        when(adminSessionService.hashSessionId("raw-token")).thenReturn("hash");
        ArgumentCaptor<AdminRememberMe> captor = ArgumentCaptor.forClass(AdminRememberMe.class);

        serviceHan7Ngay.createToken(1L);

        verify(adminRememberMeRepository).save(captor.capture());
        LocalDateTime expected = LocalDateTime.now(AdminSessionService.SESSION_ZONE).plusDays(7);
        assertThat(captor.getValue().getExpiresAt()).isCloseTo(expected, within(10, ChronoUnit.SECONDS));
    }

    @Test
    void findValidByTokenHash_tokenConHan_traVeToken() {
        AdminRememberMe token = tokenHetHanSauNhieuNgay(1);
        when(adminSessionService.hashSessionId("raw")).thenReturn("hash");
        when(adminRememberMeRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThat(service.findValidByTokenHash("raw")).contains(token);
    }

    @Test
    void findValidByTokenHash_tokenHetHan_traVeRong() {
        AdminRememberMe tokenHetHan = tokenHetHanSauNhieuNgay(-1);
        when(adminSessionService.hashSessionId("raw")).thenReturn("hash");
        when(adminRememberMeRepository.findByTokenHash("hash")).thenReturn(Optional.of(tokenHetHan));

        assertThat(service.findValidByTokenHash("raw")).isEmpty();
    }

    @Test
    void rotate_xoaTokenCuVaTaoTokenMoi() {
        AdminRememberMe tokenCu = tokenHetHanSauNhieuNgay(1);
        tokenCu.setAdminId(5L);
        when(adminSessionService.hashSessionId("old-raw")).thenReturn("old-hash");
        when(adminRememberMeRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(tokenCu));
        when(sessionIdGenerator.generate()).thenReturn("new-raw");
        when(adminSessionService.hashSessionId("new-raw")).thenReturn("new-hash");

        String newRawToken = service.rotate(5L, "old-raw");

        assertThat(newRawToken).isEqualTo("new-raw");
        verify(adminRememberMeRepository).delete(tokenCu);
        verify(adminRememberMeRepository).deleteByAdminId(5L);
        ArgumentCaptor<AdminRememberMe> captor = ArgumentCaptor.forClass(AdminRememberMe.class);
        verify(adminRememberMeRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("new-hash");
    }

    @Test
    void rotate_tokenCuKhongThuocAdmin_khongXoaDongCuaAdminKhac() {
        AdminRememberMe tokenCuaAdminKhac = tokenHetHanSauNhieuNgay(1);
        tokenCuaAdminKhac.setAdminId(9L);
        when(adminSessionService.hashSessionId("old-raw")).thenReturn("old-hash");
        when(adminRememberMeRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(tokenCuaAdminKhac));
        when(sessionIdGenerator.generate()).thenReturn("new-raw");
        when(adminSessionService.hashSessionId("new-raw")).thenReturn("new-hash");

        service.rotate(5L, "old-raw");

        verify(adminRememberMeRepository, never()).delete(any());
        verify(adminRememberMeRepository).deleteByAdminId(5L);
    }

    @Test
    void deleteByRawToken_xoaDongTheoHashToken() {
        AdminRememberMe token = tokenHetHanSauNhieuNgay(1);
        when(adminSessionService.hashSessionId("raw")).thenReturn("hash");
        when(adminRememberMeRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        service.deleteByRawToken("raw");

        verify(adminRememberMeRepository).delete(token);
    }

    @Test
    void deleteByRawToken_tokenKhongTonTai_khongLoi() {
        when(adminSessionService.hashSessionId("raw")).thenReturn("hash");
        when(adminRememberMeRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        service.deleteByRawToken("raw");

        verify(adminRememberMeRepository, never()).delete(any());
    }

    @Test
    void deleteAllByAdminId_xoaMoiTokenCuaAdmin() {
        service.deleteAllByAdminId(7L);

        verify(adminRememberMeRepository).deleteByAdminId(7L);
    }
}
