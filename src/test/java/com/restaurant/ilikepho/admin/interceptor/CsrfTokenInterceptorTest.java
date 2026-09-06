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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra middleware chống CSRF: chặn 403 khi thiếu/sai token, cho qua khi token đúng,
 * bỏ qua kiểm tra cho method an toàn và cho request không có phiên hợp lệ.
 */
@ExtendWith(MockitoExtension.class)
class CsrfTokenInterceptorTest {

    private static final String TOKEN = "csrf-token-abc123";

    @Mock
    private AdminSessionService adminSessionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private CsrfTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CsrfTokenInterceptor(adminSessionService);
    }

    /**
     * Tạo phiên admin với token CSRF cho trước.
     *
     * @param csrfToken token CSRF đặt trên phiên
     * @return phiên admin để đặt vào request attribute
     */
    private AdminSession sessionWithToken(String csrfToken) {
        AdminSession session = new AdminSession();
        session.setCsrfToken(csrfToken);
        return session;
    }

    /**
     * Gắn cookie phiên admin vào request mock.
     *
     * @param value giá trị session ID gốc trong cookie
     */
    private void stubSessionCookie(String value) {
        Cookie cookie = new Cookie(SessionCookieService.SESSION_COOKIE_NAME, value);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
    }

    @Test
    void preHandle_requestGet_khongKiemTraTokenVaChoQua() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(request, never()).getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE);
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postCoPhienThieuToken_tra403VaChan() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(TOKEN));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postCoPhienTokenDungQuaParam_choQua() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(TOKEN));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn(TOKEN);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postCoPhienTokenDungQuaHeader_choQua() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(TOKEN));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn(null);
        when(request.getHeader(CsrfTokenInterceptor.CSRF_HEADER_NAME)).thenReturn(TOKEN);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postCoPhienTokenSai_tra403VaChan() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(TOKEN));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn("token-khac");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postPhienTokenNull_trongDb_tra403VaChan() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(null));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn(TOKEN);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_deleteCoPhienThieuToken_tra403VaChan() throws Exception {
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(TOKEN));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postCoPhienTokenKhoangTrang_coLaThieuToken_tra403VaChan() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(sessionWithToken(TOKEN));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn("   ");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postKhongCoPhienKhongPhaiLogout_boQuaKiemTraVaChoQua() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(null);
        when(request.getServletPath()).thenReturn("/admin/home");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(adminSessionService, never()).hashSessionId(anyString());
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postLogoutCoPhienVaTokenDung_choQua() throws Exception {
        AdminSession session = sessionWithToken(TOKEN);
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(null);
        when(request.getServletPath()).thenReturn("/admin/logout");
        stubSessionCookie("raw-id");
        when(adminSessionService.hashSessionId("raw-id")).thenReturn("hash");
        when(adminSessionService.findActiveSession("hash")).thenReturn(Optional.of(session));
        when(request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME)).thenReturn(TOKEN);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void preHandle_postLogoutKhongCoPhien_boQuaKiemTraVaChoQua() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE)).thenReturn(null);
        when(request.getServletPath()).thenReturn("/admin/logout");
        when(request.getCookies()).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
