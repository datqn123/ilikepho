package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.entity.Admin;
import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.entity.SessionStatus;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import com.restaurant.ilikepho.admin.repository.AdminSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra thao tác phiên admin: băm session ID, tạo phiên nguyên tử, kiểm tra hết hạn.
 */
@ExtendWith(MockitoExtension.class)
class AdminSessionServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AdminSessionRepository adminSessionRepository;

    @InjectMocks
    private AdminSessionService adminSessionService;

    @Test
    void hashSessionId_traVeChuoiHexOnDinhKhongPhaiChuoiGoc() {
        String first = adminSessionService.hashSessionId("raw-session-id");
        String second = adminSessionService.hashSessionId("raw-session-id");

        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(first).isNotEqualTo("raw-session-id");
    }

    @Test
    void createSession_khoaPhienCuVaTaoPhienActivateMoi() {
        Admin admin = new Admin();
        admin.setId(1L);
        when(adminRepository.findWithLockingById(1L)).thenReturn(Optional.of(admin));
        when(adminSessionRepository.save(any(AdminSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminSession session = adminSessionService.createSession(1L, "raw-id");

        verify(adminSessionRepository).lockAllActiveByAdminId(
                eq(1L), eq(SessionStatus.ACTIVE), eq(SessionStatus.LOCKED));
        assertThat(session.getAdminId()).isEqualTo(1L);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getSessionHash()).isEqualTo(adminSessionService.hashSessionId("raw-id"));
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getLastActivityAt()).isNotNull();
    }

    @Test
    void createSession_adminKhongTonTaiNemExceptionVaKhongLuu() {
        when(adminRepository.findWithLockingById(99L)).thenReturn(Optional.empty());

        try {
            adminSessionService.createSession(99L, "raw-id");
            org.junit.jupiter.api.Assertions.fail("Phải ném IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("99");
        }
        verify(adminSessionRepository, never()).save(any());
    }

    @Test
    void isExpired_dungKhiQuaTimeoutVaSaiKhiConHan() {
        AdminSession session = new AdminSession();
        session.setLastActivityAt(LocalDateTime.now().minusMinutes(40));
        assertThat(adminSessionService.isExpired(session, 30)).isTrue();

        session.setLastActivityAt(LocalDateTime.now().minusMinutes(10));
        assertThat(adminSessionService.isExpired(session, 30)).isFalse();
    }
}
