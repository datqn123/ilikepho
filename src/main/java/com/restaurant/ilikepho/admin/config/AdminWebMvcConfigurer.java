package com.restaurant.ilikepho.admin.config;

import com.restaurant.ilikepho.admin.interceptor.AdminAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Web MVC cho hệ admin: đăng ký interceptor xác thực phiên
 * cho toàn bộ /admin/** trừ trang đăng nhập và đăng xuất.
 */
@Configuration
public class AdminWebMvcConfigurer implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    public AdminWebMvcConfigurer(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/logout");
    }
}
