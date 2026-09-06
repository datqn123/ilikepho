package com.restaurant.ilikepho.admin.controller;

import com.restaurant.ilikepho.admin.dto.LoginResult;
import com.restaurant.ilikepho.admin.service.AdminAuthService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra xử lý submit form đăng nhập admin (POST /admin/login) và đăng xuất (POST /admin/logout).
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    @Mock
    private SessionCookieService sessionCookieService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthenticationController controller = new AuthenticationController(adminAuthService, sessionCookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void handleLogin_taiKhoanHopLeKhongNho_chuyenVeHomeVaChiTaoCookiePhien() throws Exception {
        when(adminAuthService.login("admin", "Admin@123", false))
                .thenReturn(Optional.of(new LoginResult("raw-session-id", null)));
        when(sessionCookieService.createSessionCookie("raw-session-id"))
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-id").build());

        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "Admin@123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"));

        verify(adminAuthService).login("admin", "Admin@123", false);
        verify(sessionCookieService, never()).createRememberMeCookie(anyString());
    }

    @Test
    void handleLogin_coGhiNho_setThemCookieRemember() throws Exception {
        when(adminAuthService.login("admin", "Admin@123", true))
                .thenReturn(Optional.of(new LoginResult("raw-session-id", "raw-remember")));
        when(sessionCookieService.createSessionCookie("raw-session-id"))
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-id").build());
        when(sessionCookieService.createRememberMeCookie("raw-remember"))
                .thenReturn(ResponseCookie.from(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember").build());

        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "Admin@123")
                        .param("rememberMe", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/home"));

        verify(sessionCookieService).createSessionCookie("raw-session-id");
        verify(sessionCookieService).createRememberMeCookie("raw-remember");
    }

    @Test
    void handleLogin_matKhauDeTrong_giuTrangLoginVaKhongDangNhap() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("userLogin", "userPassword"));

        verify(adminAuthService, never()).login(anyString(), anyString(), anyBoolean());
    }

    @Test
    void handleLogin_matKhauQuaNgan_giuTrangLoginVaKhongDangNhap() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .param("userName", "admin")
                        .param("userPassword", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("userLogin", "userPassword"));

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
