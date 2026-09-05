package com.restaurant.ilikepho.admin.interceptor;

import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.service.AdminSessionService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra middleware xác thực request vào khu vực admin.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private AdminSessionService adminSessionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(adminSessionService, 30);
    }

    private void stubSessionCookie(String value) {
        Cookie cookie = new Cookie(SessionCookieService.SESSION_COOKIE_NAME, value);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
    }

    @Test
    void preHandle_thieuCookie_chuyenVeLoginVaChan() throws Exception {
        when(request.getCookies()).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendRedirect("/admin/login");
    }

    @Test
    void preHandle_phienKhongTonTai_chuyenVeLoginVaChan() throws Exception {
        stubSessionCookie("raw-id");
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendRedirect("/admin/login");
    }

    @Test
    void preHandle_phienHetHan_khoaPhienVaChan() throws Exception {
        stubSessionCookie("raw-id");
        AdminSession session = new AdminSession();
        session.setLastActivityAt(LocalDateTime.now().minusMinutes(60));
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.of(session));
        when(adminSessionService.isExpired(session, 30)).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(adminSessionService).lockSession(session);
        verify(response).sendRedirect("/admin/login");
    }

    @Test
    void preHandle_phienHopLe_choQuaVaCapNhatHoatDong() throws Exception {
        stubSessionCookie("raw-id");
        AdminSession session = new AdminSession();
        session.setLastActivityAt(LocalDateTime.now());
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.of(session));
        when(adminSessionService.isExpired(session, 30)).thenReturn(false);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(adminSessionService).updateLastActivity(session);
        verify(response, never()).sendRedirect(anyString());
    }
}
