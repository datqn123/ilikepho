package com.restaurant.ilikepho.admin.interceptor;

import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.service.AdminSessionService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Middleware chống CSRF lớp 2 (Synchronizer Token) cho khu vực admin:
 * với request thay đổi trạng thái (POST/PUT/PATCH/DELETE) khi có phiên ACTIVE hợp lệ,
 * yêu cầu token `_csrf` (param hoặc header {@code X-CSRF-TOKEN}) khớp token lưu trên phiên;
 * thiếu hoặc sai token bị chặn bằng 403. Request không có phiên hợp lệ được bỏ qua
 * (không có phiên để bảo vệ, ví dụ logout khi phiên đã chết).
 */
@Component
public class CsrfTokenInterceptor implements HandlerInterceptor {

    /**
     * Tên request parameter chứa CSRF token trong form (quy ước Spring: `_csrf`).
     */
    public static final String CSRF_PARAM_NAME = "_csrf";

    /**
     * Tên header chứa CSRF token cho các request không qua form.
     */
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

    /**
     * Đường dẫn logout: nằm ngoài pattern của AdminAuthInterceptor nên interceptor
     * tự tra phiên từ cookie thay vì đọc request attribute.
     */
    private static final String LOGOUT_PATH = "/admin/logout";

    /**
     * Các HTTP method thay đổi trạng thái, bắt buộc kiểm tra CSRF khi có phiên.
     */
    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AdminSessionService adminSessionService;

    public CsrfTokenInterceptor(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    /**
     * Kiểm tra CSRF token cho request thay đổi trạng thái dưới /admin/**:
     * không phải method thay đổi trạng thái hoặc không có phiên hợp lệ thì cho qua;
     * có phiên thì so token (constant-time) trước khi cho qua, sai/thiếu trả 403.
     *
     * @param request  request hiện tại
     * @param response response hiện tại
     * @param handler  handler sẽ xử lý request
     * @return {@code true} nếu cho request đi tiếp, {@code false} nếu đã chặn bằng 403
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!STATE_CHANGING_METHODS.contains(request.getMethod())) {
            return true;
        }

        AdminSession session = resolveSession(request);
        if (session == null) {
            return true;
        }

        if (!isTokenValid(session, resolveSubmittedToken(request))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    /**
     * Lấy phiên admin của request: ưu tiên request attribute do AdminAuthInterceptor đặt;
     * riêng /admin/logout (nằm ngoài pattern auth) tự đọc cookie và tra phiên ACTIVE trong DB.
     *
     * @param request request hiện tại
     * @return phiên ACTIVE hợp lệ hoặc {@code null} nếu không có phiên để bảo vệ
     */
    private AdminSession resolveSession(HttpServletRequest request) {
        Object attribute = request.getAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE);
        if (attribute instanceof AdminSession session) {
            return session;
        }
        if (!LOGOUT_PATH.equals(request.getServletPath())) {
            return null;
        }
        String rawSessionId = readSessionCookie(request);
        if (rawSessionId == null) {
            return null;
        }
        return adminSessionService.findActiveSession(adminSessionService.hashSessionId(rawSessionId))
                .orElse(null);
    }

    /**
     * Đọc CSRF token do client gửi: lấy từ request parameter `_csrf` trước,
     * fallback sang header {@code X-CSRF-TOKEN}.
     *
     * @param request request hiện tại
     * @return token client gửi hoặc {@code null} nếu không gửi
     */
    private String resolveSubmittedToken(HttpServletRequest request) {
        String token = request.getParameter(CSRF_PARAM_NAME);
        if (token == null || token.isBlank()) {
            token = request.getHeader(CSRF_HEADER_NAME);
        }
        return (token == null || token.isBlank()) ? null : token;
    }

    /**
     * So token client gửi với token lưu trên phiên bằng {@link MessageDigest#isEqual}
     * (constant-time, chống dò token qua thời gian phản hồi); token phiên NULL
     * (phiên cũ trước migration) coi như không khớp.
     *
     * @param session          phiên admin hiện tại
     * @param submittedToken   token client gửi (đã loại trường hợp rỗng)
     * @return {@code true} nếu token khớp, ngược lại {@code false}
     */
    private boolean isTokenValid(AdminSession session, String submittedToken) {
        String expectedToken = session.getCsrfToken();
        if (expectedToken == null || expectedToken.isBlank() || submittedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                submittedToken.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Đọc giá trị cookie phiên admin từ request.
     *
     * @param request request hiện tại
     * @return giá trị cookie phiên hoặc {@code null} nếu không có cookie phiên
     */
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
