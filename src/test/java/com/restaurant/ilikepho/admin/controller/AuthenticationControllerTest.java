package com.restaurant.ilikepho.admin.controller;

import com.restaurant.ilikepho.admin.dto.LoginResult;
import com.restaurant.ilikepho.admin.service.AdminAuthService;
import com.restaurant.ilikepho.admin.service.AdminLoginCsrfService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra trang đăng nhập, submit form đăng nhập (POST /admin/login, có login-CSRF)
 * và đăng xuất (POST /admin/logout) của admin.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    @Mock
    private SessionCookieService sessionCookieService;

    @Mock
    private AdminLoginCsrfService adminLoginCsrfService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthenticationController controller =
                new AuthenticationController(adminAuthService, sessionCookieService, adminLoginCsrfService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void loginPage_taoTokenLoginCsrf_datCookieVaHiddenFieldKhopNhau() throws Exception {
        when(adminLoginCsrfService.generateToken()).thenReturn("login-csrf");
        when(sessionCookieService.createLoginCsrfCookie("login-csrf"))
                .thenReturn(ResponseCookie.from(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "login-csrf").build());

        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(cookie().value(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "login-csrf"))
                .andExpect(model().attribute("loginCsrfToken", "login-csrf"));
    }

    @Test
    void handleLogin_taiKhoanHopLeKhongNho_chuyenVeHomeVaGuiCookiePhienVaRememberHetHan() throws Exception {
        when(adminLoginCsrfService.isValid("login-csrf", "login-csrf")).thenReturn(true);
        when(adminAuthService.login("admin", "Admin@123", false))
                .thenReturn(Optional.of(new LoginResult("raw-session-id", null)));
        when(sessionCookieService.createSessionCookie("raw-session-id"))
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-id").build());
        when(sessionCookieService.createExpiredRememberMeCookie())
                .thenReturn(ResponseCookie.from(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "").maxAge(0).build());

        MvcResult result = mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "Admin@123")
                        .param("_csrf", "login-csrf")
                        .cookie(new Cookie(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "login-csrf")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).anyMatch(value -> value.contains("ADMIN_SESSION=raw-session-id"));
        assertThat(setCookies).anyMatch(value -> value.contains("ADMIN_REMEMBER=") && value.contains("Max-Age=0"));
        verify(sessionCookieService, never()).createRememberMeCookie(anyString());
    }

    @Test
    void handleLogin_coGhiNho_setThemCookieRemember() throws Exception {
        when(adminLoginCsrfService.isValid("login-csrf", "login-csrf")).thenReturn(true);
        when(adminAuthService.login("admin", "Admin@123", true))
                .thenReturn(Optional.of(new LoginResult("raw-session-id", "raw-remember")));
        when(sessionCookieService.createSessionCookie("raw-session-id"))
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-id").build());
        when(sessionCookieService.createRememberMeCookie("raw-remember"))
                .thenReturn(ResponseCookie.from(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember").build());

        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "Admin@123")
                        .param("rememberMe", "true")
                        .param("_csrf", "login-csrf")
                        .cookie(new Cookie(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "login-csrf")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"));

        verify(sessionCookieService).createSessionCookie("raw-session-id");
        verify(sessionCookieService).createRememberMeCookie("raw-remember");
    }

    @Test
    void handleLogin_thieuTokenLoginCsrf_tuChoiXacThucVaQuayLaiTrangLogin() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "Admin@123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(adminAuthService, never()).login(anyString(), anyString(), anyBoolean());
    }

    @Test
    void handleLogin_saiTokenLoginCsrf_tuChoiXacThucVaQuayLaiTrangLogin() throws Exception {
        when(adminLoginCsrfService.isValid("token-trong-cookie", "token-sai")).thenReturn(false);

        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "Admin@123")
                        .param("_csrf", "token-sai")
                        .cookie(new Cookie(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "token-trong-cookie")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(adminAuthService, never()).login(anyString(), anyString(), anyBoolean());
    }

    @Test
    void handleLogin_matKhauDeTrong_giuTrangLoginVaCapTokenMoi() throws Exception {
        when(adminLoginCsrfService.isValid("login-csrf", "login-csrf")).thenReturn(true);
        when(adminLoginCsrfService.generateToken()).thenReturn("fresh-token");
        when(sessionCookieService.createLoginCsrfCookie("fresh-token"))
                .thenReturn(ResponseCookie.from(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "fresh-token").build());

        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "")
                        .param("_csrf", "login-csrf")
                        .cookie(new Cookie(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "login-csrf")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("userLogin", "userPassword"))
                .andExpect(model().attribute("loginCsrfToken", "fresh-token"));

        verify(adminAuthService, never()).login(anyString(), anyString(), anyBoolean());
    }

    @Test
    void handleLogin_matKhauQuaNgan_giuTrangLoginVaKhongDangNhap() throws Exception {
        when(adminLoginCsrfService.isValid("login-csrf", "login-csrf")).thenReturn(true);
        when(adminLoginCsrfService.generateToken()).thenReturn("fresh-token");
        when(sessionCookieService.createLoginCsrfCookie("fresh-token"))
                .thenReturn(ResponseCookie.from(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "fresh-token").build());

        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "abc")
                        .param("_csrf", "login-csrf")
                        .cookie(new Cookie(SessionCookieService.LOGIN_CSRF_COOKIE_NAME, "login-csrf")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("userLogin", "userPassword"))
                .andExpect(model().attribute("loginCsrfToken", "fresh-token"));

        verify(adminAuthService, never()).login(anyString(), anyString(), anyBoolean());
    }

    @Test
    void handleLogout_xoaPhienVaRememberVaGuiHaiCookieHetHan() throws Exception {
        when(sessionCookieService.createExpiredSessionCookie())
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "").maxAge(0).build());
        when(sessionCookieService.createExpiredRememberMeCookie())
                .thenReturn(ResponseCookie.from(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "").maxAge(0).build());

        mockMvc.perform(post("/admin/logout")
                        .cookie(new Cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-id"),
                                new Cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(adminAuthService).logout("raw-id", "raw-remember");
        verify(sessionCookieService).createExpiredSessionCookie();
        verify(sessionCookieService).createExpiredRememberMeCookie();
    }
}
