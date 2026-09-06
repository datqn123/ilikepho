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
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/logout");
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/logout");
        registry.addInterceptor(csrfTokenInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");
    }
}
