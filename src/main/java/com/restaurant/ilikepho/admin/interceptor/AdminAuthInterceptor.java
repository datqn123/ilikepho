package com.restaurant.ilikepho.admin.interceptor;

import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.service.AdminSessionService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Middleware xác thực mỗi request vào khu vực admin:
 * đọc cookie phiên, tra hash trong DB; phiên không hợp lệ (thiếu, khoá, không tồn tại
 * hoặc hết hạn) sẽ bị chuyển về trang đăng nhập; phiên hợp lệ được cập nhật
 * thời điểm hoạt động gần nhất (sliding expiration).
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    /**
     * Request attribute chứa phiên admin hợp lệ sau khi xác thực đạt,
     * dùng chung cho các thành phần phía sau (ví dụ CsrfTokenInterceptor, template).
     */
    public static final String SESSION_ATTRIBUTE = "adminSession";

    private final AdminSessionService adminSessionService;
    private final long timeoutMinutes;

    public AdminAuthInterceptor(AdminSessionService adminSessionService,
                                @Value("${admin.session.timeout-minutes:30}") long timeoutMinutes) {
        this.adminSessionService = adminSessionService;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Object rememberedSession = request.getAttribute(SESSION_ATTRIBUTE);
        if (rememberedSession instanceof AdminSession session) {
            // Phiên do RememberMeInterceptor vừa nối lại: cookie mới chỉ nằm trên response
            // nên không đọc lại cookie từ request được; cho qua ngay và vẫn cấp CSRF token cho trang.
            request.setAttribute("csrfToken", session.getCsrfToken());
            return true;
        }

        String rawSessionId = readSessionCookie(request);
        if (rawSessionId == null) {
            redirectToLogin(response);
            return false;
        }

        String sessionHash = adminSessionService.hashSessionId(rawSessionId);
        Optional<AdminSession> sessionOpt = adminSessionService.findActiveSession(sessionHash);
        if (sessionOpt.isEmpty()) {
            redirectToLogin(response);
            return false;
        }

        AdminSession session = sessionOpt.get();
        if (adminSessionService.isExpired(session, timeoutMinutes)) {
            adminSessionService.lockSession(session);
            redirectToLogin(response);
            return false;
        }

        adminSessionService.updateLastActivity(session);
        request.setAttribute(SESSION_ATTRIBUTE, session);
        request.setAttribute("csrfToken", session.getCsrfToken());
        return true;
    }

    private void redirectToLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect("/admin/login");
    }

    private String readSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (SessionCookieService.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
