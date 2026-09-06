package com.restaurant.ilikepho.admin.interceptor;

import com.restaurant.ilikepho.admin.entity.AdminRememberMe;
import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.service.AdminRememberMeService;
import com.restaurant.ilikepho.admin.service.AdminSessionService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import com.restaurant.ilikepho.admin.service.SessionIdGenerator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra middleware tự nối lại phiên đăng nhập bằng cookie "ghi nhớ đăng nhập".
 */
@ExtendWith(MockitoExtension.class)
class RememberMeInterceptorTest {

    @Mock
    private AdminSessionService adminSessionService;

    @Mock
    private AdminRememberMeService adminRememberMeService;

    @Mock
    private SessionCookieService sessionCookieService;

    @Mock
    private SessionIdGenerator sessionIdGenerator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RememberMeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RememberMeInterceptor(adminSessionService, adminRememberMeService,
                sessionCookieService, sessionIdGenerator, 30);
    }

    private void stubCookies(Cookie... cookies) {
        when(request.getCookies()).thenReturn(cookies);
    }

    private Cookie cookie(String name, String value) {
        return new Cookie(name, value);
    }

    private AdminRememberMe rememberToken(Long adminId) {
        AdminRememberMe token = new AdminRememberMe();
        token.setAdminId(adminId);
        token.setExpiresAt(LocalDateTime.now(AdminSessionService.SESSION_ZONE).plusDays(20));
        return token;
    }

    private void stubTaoPhienMoi(Long adminId, AdminSession newSession) {
        when(sessionIdGenerator.generate()).thenReturn("raw-new-session");
        when(adminSessionService.createSession(adminId, "raw-new-session")).thenReturn(newSession);
        when(adminRememberMeService.rotate(adminId, "raw-remember")).thenReturn("raw-new-remember");
        when(sessionCookieService.createSessionCookie("raw-new-session"))
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "raw-new-session").build());
        when(sessionCookieService.createRememberMeCookie("raw-new-remember"))
                .thenReturn(ResponseCookie.from(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-new-remember").build());
    }

    @Test
    void preHandle_khongCoCookieRemember_boQuaNgayKhongTruyVan() {
        stubCookies();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verifyNoInteractions(adminSessionService, adminRememberMeService, sessionIdGenerator);
        verify(response, never()).addHeader(anyString(), anyString());
    }

    @Test
    void preHandle_daCoPhienHopLe_boQuaKhongTaoPhien() {
        stubCookies(cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-session"),
                cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember"));
        AdminSession session = new AdminSession();
        when(adminSessionService.hashSessionId("raw-session")).thenReturn("session-hash");
        when(adminSessionService.findActiveSession("session-hash")).thenReturn(Optional.of(session));
        when(adminSessionService.isExpired(session, 30)).thenReturn(false);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(adminRememberMeService, never()).findValidByTokenHash(anyString());
        verify(adminSessionService, never()).createSession(any(), anyString());
        verify(response, never()).addHeader(anyString(), anyString());
    }

    @Test
    void preHandle_phienChetVaRememberHopLe_taoPhienMoiVaXoayToken() {
        stubCookies(cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-session"),
                cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember"));
        when(adminSessionService.hashSessionId("raw-session")).thenReturn("session-hash");
        when(adminSessionService.findActiveSession("session-hash")).thenReturn(Optional.empty());
        when(adminRememberMeService.findValidByTokenHash("raw-remember"))
                .thenReturn(Optional.of(rememberToken(5L)));
        AdminSession newSession = new AdminSession();
        newSession.setAdminId(5L);
        stubTaoPhienMoi(5L, newSession);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(adminSessionService).createSession(5L, "raw-new-session");
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), contains("raw-new-session"));
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), contains("raw-new-remember"));
        verify(request).setAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE, newSession);
    }

    @Test
    void preHandle_phienHetHanVaRememberHopLe_taoPhienMoi() {
        stubCookies(cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-session"),
                cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember"));
        AdminSession sessionHetHan = new AdminSession();
        when(adminSessionService.hashSessionId("raw-session")).thenReturn("session-hash");
        when(adminSessionService.findActiveSession("session-hash")).thenReturn(Optional.of(sessionHetHan));
        when(adminSessionService.isExpired(sessionHetHan, 30)).thenReturn(true);
        when(adminRememberMeService.findValidByTokenHash("raw-remember"))
                .thenReturn(Optional.of(rememberToken(5L)));
        AdminSession newSession = new AdminSession();
        stubTaoPhienMoi(5L, newSession);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(adminSessionService).createSession(5L, "raw-new-session");
        verify(request).setAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE, newSession);
    }

    @Test
    void preHandle_matCookiePhienVaRememberHopLe_taoPhienMoi() {
        stubCookies(cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember"));
        when(adminRememberMeService.findValidByTokenHash("raw-remember"))
                .thenReturn(Optional.of(rememberToken(5L)));
        AdminSession newSession = new AdminSession();
        stubTaoPhienMoi(5L, newSession);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(adminSessionService, never()).hashSessionId(anyString());
        verify(adminSessionService).createSession(5L, "raw-new-session");
        verify(request).setAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE, newSession);
    }

    @Test
    void preHandle_rememberKhongHopLe_khongTaoPhienDeAuthXuLy() {
        stubCookies(cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember"));
        when(adminRememberMeService.findValidByTokenHash("raw-remember")).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(adminSessionService, never()).createSession(any(), anyString());
        verify(response, never()).addHeader(anyString(), anyString());
        verify(request, never()).setAttribute(anyString(), any());
    }
}
