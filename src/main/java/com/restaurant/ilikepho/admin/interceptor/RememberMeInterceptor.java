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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Middleware tự nối lại phiên đăng nhập bằng cookie "ghi nhớ đăng nhập":
 * chạy trước {@link AdminAuthInterceptor}; khi request chưa có phiên hợp lệ
 * nhưng remember token trong cookie còn hiệu lực thì tạo phiên mới (đăng nhập lại ngầm),
 * set cookie phiên mới và xoay vòng remember token, rồi đặt phiên vào request attribute
 * để auth interceptor cho qua ngay (cookie mới chỉ nằm trên response, request không tự thấy).
 * Không có cookie remember thì bỏ qua ngay, không truy vấn DB.
 */
@Component
public class RememberMeInterceptor implements HandlerInterceptor {

    private final AdminSessionService adminSessionService;
    private final AdminRememberMeService adminRememberMeService;
    private final SessionCookieService sessionCookieService;
    private final SessionIdGenerator sessionIdGenerator;
    private final long timeoutMinutes;

    public RememberMeInterceptor(AdminSessionService adminSessionService,
                                 AdminRememberMeService adminRememberMeService,
                                 SessionCookieService sessionCookieService,
                                 SessionIdGenerator sessionIdGenerator,
                                 @Value("${admin.session.timeout-minutes:30}") long timeoutMinutes) {
        this.adminSessionService = adminSessionService;
        this.adminRememberMeService = adminRememberMeService;
        this.sessionCookieService = sessionCookieService;
        this.sessionIdGenerator = sessionIdGenerator;
        this.timeoutMinutes = timeoutMinutes;
    }

    /**
     * Nối lại phiên đăng nhập bằng remember token khi request chưa có phiên hợp lệ:
     * không có cookie remember thì cho qua ngay (không truy vấn DB); đã có phiên hợp lệ
     * thì cho qua để auth xử lý bình thường; remember token hợp lệ thì tạo phiên mới,
     * set 2 cookie mới (phiên + remember đã xoay vòng) và đặt phiên vào request attribute
     * cho auth cho qua. Token không hợp lệ thì cho qua để auth redirect về trang login.
     *
     * @param request  request hiện tại
     * @param response response hiện tại
     * @param handler  handler sẽ xử lý request
     * @return luôn {@code true} — interceptor này chỉ nối thêm phiên, không chặn request
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawRememberToken = readRememberMeCookie(request);
        if (rawRememberToken == null) {
            return true;
        }
        if (hasValidSession(request)) {
            return true;
        }
        Optional<AdminRememberMe> rememberToken = adminRememberMeService.findValidByTokenHash(rawRememberToken);
        if (rememberToken.isEmpty()) {
            return true;
        }
        Long adminId = rememberToken.get().getAdminId();
        String rawSessionId = sessionIdGenerator.generate();
        AdminSession newSession = adminSessionService.createSession(adminId, rawSessionId);
        String newRememberToken = adminRememberMeService.rotate(adminId, rawRememberToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createSessionCookie(rawSessionId).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createRememberMeCookie(newRememberToken).toString());
        request.setAttribute(AdminAuthInterceptor.SESSION_ATTRIBUTE, newSession);
        return true;
    }

    /**
     * Kiểm tra request đã mang phiên hợp lệ (đang ACTIVE và chưa quá hạn hoạt động) để bỏ qua nối phiên;
     * phiên chỉ hết hạn do không hoạt động vẫn coi như chưa có phiên hợp lệ để remember-me nối lại.
     *
     * @param request request hiện tại
     * @return {@code true} nếu request đã có phiên hợp lệ
     */
    private boolean hasValidSession(HttpServletRequest request) {
        String rawSessionId = readSessionCookie(request);
        if (rawSessionId == null) {
            return false;
        }
        return adminSessionService
                .findActiveSession(adminSessionService.hashSessionId(rawSessionId))
                .filter(session -> !adminSessionService.isExpired(session, timeoutMinutes))
                .isPresent();
    }

    /**
     * Đọc chuỗi remember token gốc khỏi cookie "ghi nhớ đăng nhập"; trả về null khi không có cookie.
     *
     * @param request request hiện tại
     * @return chuỗi token gốc hoặc null nếu không có
     */
    private String readRememberMeCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (SessionCookieService.REMEMBER_ME_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Đọc chuỗi session ID gốc khỏi cookie phiên; trả về null khi không có cookie.
     *
     * @param request request hiện tại
     * @return chuỗi session ID gốc hoặc null nếu không có
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
