package com.restaurant.ilikepho.admin.config;

import com.restaurant.ilikepho.admin.interceptor.AdminAuthInterceptor;
import com.restaurant.ilikepho.admin.interceptor.CsrfTokenInterceptor;
import com.restaurant.ilikepho.admin.interceptor.RememberMeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Web MVC cho hệ admin: đăng ký ba interceptor cho /admin/** theo thứ tự —
 * nối lại phiên bằng remember token (trừ login và logout), xác thực phiên (trừ login và logout),
 * kiểm tra CSRF chạy sau (trừ login, bao gồm logout khi có phiên).
 */
@Configuration
public class AdminWebMvcConfigurer implements WebMvcConfigurer {

    /**
     * Pattern khu vực admin áp dụng interceptor; dùng chung cho cấu hình và test chuỗi interceptor.
     */
    public static final String ADMIN_PATH_PATTERN = "/admin/**";

    /**
     * Đường dẫn trang đăng nhập, nằm ngoài remember-me, auth và CSRF phiên.
     */
    public static final String LOGIN_PATH = "/admin/login";

    /**
     * Đường dẫn đăng xuất, nằm ngoài remember-me và auth (CSRF vẫn kiểm tra khi còn phiên).
     */
    public static final String LOGOUT_PATH = "/admin/logout";

    private final RememberMeInterceptor rememberMeInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CsrfTokenInterceptor csrfTokenInterceptor;

    public AdminWebMvcConfigurer(RememberMeInterceptor rememberMeInterceptor,
                                 AdminAuthInterceptor adminAuthInterceptor,
                                 CsrfTokenInterceptor csrfTokenInterceptor) {
        this.rememberMeInterceptor = rememberMeInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.csrfTokenInterceptor = csrfTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rememberMeInterceptor)
                .addPathPatterns(ADMIN_PATH_PATTERN)
                .excludePathPatterns(LOGIN_PATH, LOGOUT_PATH);
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(ADMIN_PATH_PATTERN)
                .excludePathPatterns(LOGIN_PATH, LOGOUT_PATH);
        registry.addInterceptor(csrfTokenInterceptor)
                .addPathPatterns(ADMIN_PATH_PATTERN)
                .excludePathPatterns(LOGIN_PATH);
    }
}
