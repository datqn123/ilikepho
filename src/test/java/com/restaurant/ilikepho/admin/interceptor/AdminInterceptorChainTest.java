package com.restaurant.ilikepho.admin.interceptor;

import com.restaurant.ilikepho.admin.entity.AdminRememberMe;
import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.service.AdminRememberMeService;
import com.restaurant.ilikepho.admin.service.AdminSessionService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import com.restaurant.ilikepho.admin.service.SessionIdGenerator;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test chuỗi đầy đủ 3 interceptor thật (Remember-me → Auth → CSRF) đăng ký theo đúng
 * thứ tự và pattern/exclude của AdminWebMvcConfigurer, ghim các kịch bản đã mô tả
 * trong mục 8 Task 06: nối phiên bằng remember, chặn CSRF token cũ ngay sau khi nối
 * phiên, và logout bằng CSRF token vừa được render cho trang.
 */
@ExtendWith(MockitoExtension.class)
class AdminInterceptorChainTest {

    @Mock
    private AdminSessionService adminSessionService;

    @Mock
    private AdminRememberMeService adminRememberMeService;

    @Mock
    private SessionCookieService sessionCookieService;

    @Mock
    private SessionIdGenerator sessionIdGenerator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RememberMeInterceptor rememberMeInterceptor =
                new RememberMeInterceptor(adminSessionService, adminRememberMeService,
                        sessionCookieService, sessionIdGenerator, 30);
        AdminAuthInterceptor adminAuthInterceptor = new AdminAuthInterceptor(adminSessionService, 30);
        CsrfTokenInterceptor csrfTokenInterceptor = new CsrfTokenInterceptor(adminSessionService);
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyAdminController())
                .addInterceptors(
                        new MappedInterceptor(new String[]{"/admin/**"},
                                new String[]{"/admin/login", "/admin/logout"}, rememberMeInterceptor),
                        new MappedInterceptor(new String[]{"/admin/**"},
                                new String[]{"/admin/login", "/admin/logout"}, adminAuthInterceptor),
                        new MappedInterceptor(new String[]{"/admin/**"},
                                new String[]{"/admin/login"}, csrfTokenInterceptor))
                .build();
    }

    /**
     * Controller giả thay cho controller admin thật, đủ method GET và POST để chạy qua chuỗi interceptor.
     */
    @RestController
    static class DummyAdminController {

        /**
         * Trang admin giả để test request GET đi qua chuỗi interceptor.
         *
         * @return nội dung phản hồi cố định
         */
        @GetMapping("/admin/home")
        public String home() {
            return "home";
        }

        /**
         * Endpoint thay đổi trạng thái giả để test CSRF chặn request POST.
         *
         * @return nội dung phản hồi cố định
         */
        @PostMapping("/admin/products")
        public String save() {
            return "saved";
        }

        /**
         * Endpoint đăng xuất giả để test luồng logout qua CSRF interceptor (nằm ngoài auth).
         *
         * @return nội dung phản hồi cố định
         */
        @PostMapping("/admin/logout")
        public String logout() {
            return "logged-out";
        }
    }

    @Test
    void chuoiInterceptor_chiCoRememberCookieHopLe_noiPhienVaNhan2CookieMoi() throws Exception {
        stubNoiLaiPhien("csrf-moi");

        MvcResult result = mockMvc.perform(get("/admin/home")
                        .cookie(new Cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember")))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(value -> value.contains("ADMIN_SESSION=raw-session-moi"));
        assertThat(setCookies).anyMatch(value -> value.contains("ADMIN_REMEMBER=raw-remember-moi"));
    }

    @Test
    void chuoiInterceptor_postMangCsrfTokenPhienCu_ngaySauKhiNoiPhien_biChan403() throws Exception {
        stubNoiLaiPhien("csrf-moi");

        mockMvc.perform(post("/admin/products")
                        .param(CsrfTokenInterceptor.CSRF_PARAM_NAME, "csrf-cua-phien-cu")
                        .cookie(new Cookie(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember")))
                .andExpect(status().isForbidden());
    }

    @Test
    void chuoiInterceptor_getTrangRoiPostLogoutVoiTokenVuaRender_quaVaSaiTokenBiChan() throws Exception {
        AdminSession session = phienHoatDong("csrf-hien-tai");
        when(adminSessionService.hashSessionId("raw-session-cu")).thenReturn("session-hash");
        when(adminSessionService.findActiveSession("session-hash")).thenReturn(Optional.of(session));
        when(adminSessionService.isExpired(session, 30)).thenReturn(false);

        MvcResult getPage = mockMvc.perform(get("/admin/home")
                        .cookie(new Cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-cu")))
                .andExpect(status().isOk())
                .andReturn();

        String csrfTokenVuaRender = (String) getPage.getRequest().getAttribute("csrfToken");
        assertThat(csrfTokenVuaRender).isEqualTo("csrf-hien-tai");

        mockMvc.perform(post("/admin/logout")
                        .param(CsrfTokenInterceptor.CSRF_PARAM_NAME, "csrf-sai")
                        // standalone MockMvc không tự set servletPath; interceptor tra phiên logout theo getServletPath()
                        .servletPath("/admin/logout")
                        .cookie(new Cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-cu")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/logout")
                        .param(CsrfTokenInterceptor.CSRF_PARAM_NAME, csrfTokenVuaRender)
                        .servletPath("/admin/logout")
                        .cookie(new Cookie(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-cu")))
                .andExpect(status().isOk());
    }

    /**
     * Stub chung cho kịch bản nối lại phiên bằng remember token: token hợp lệ của admin 7,
     * tạo phiên mới (CSRF token "csrf-moi") và xoay vòng remember token, kèm 2 cookie thật.
     *
     * @param csrfTokenMoi CSRF token của phiên mới được tạo
     */
    private void stubNoiLaiPhien(String csrfTokenMoi) {
        AdminRememberMe rememberToken = new AdminRememberMe();
        rememberToken.setAdminId(7L);
        when(adminRememberMeService.findValidByTokenHash("raw-remember")).thenReturn(Optional.of(rememberToken));
        when(sessionIdGenerator.generate()).thenReturn("raw-session-moi");
        when(adminSessionService.createSession(7L, "raw-session-moi"))
                .thenReturn(phienHoatDong(csrfTokenMoi));
        when(adminRememberMeService.rotate(7L, "raw-remember")).thenReturn("raw-remember-moi");
        when(sessionCookieService.createSessionCookie("raw-session-moi"))
                .thenReturn(ResponseCookie.from(SessionCookieService.SESSION_COOKIE_NAME, "raw-session-moi").build());
        when(sessionCookieService.createRememberMeCookie("raw-remember-moi"))
                .thenReturn(ResponseCookie.from(SessionCookieService.REMEMBER_ME_COOKIE_NAME, "raw-remember-moi").build());
    }

    /**
     * Tạo đối tượng phiên admin hoạt động (ACTIVE) với CSRF token cho trước.
     *
     * @param csrfToken CSRF token đặt trên phiên
     * @return phiên admin giả lập
     */
    private AdminSession phienHoatDong(String csrfToken) {
        AdminSession session = new AdminSession();
        session.setAdminId(7L);
        session.setCsrfToken(csrfToken);
        return session;
    }
}
