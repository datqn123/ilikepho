package com.restaurant.ilikepho.admin.controller;

import com.restaurant.ilikepho.admin.dto.LoginResult;
import com.restaurant.ilikepho.admin.dto.UserLoginRequest;
import com.restaurant.ilikepho.admin.interceptor.CsrfTokenInterceptor;
import com.restaurant.ilikepho.admin.service.AdminAuthService;
import com.restaurant.ilikepho.admin.service.AdminLoginCsrfService;
import com.restaurant.ilikepho.admin.service.SessionCookieService;
import com.restaurant.ilikepho.common.MessageConstant;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AuthenticationController {

    private final AdminAuthService adminAuthService;
    private final SessionCookieService sessionCookieService;
    private final AdminLoginCsrfService adminLoginCsrfService;

    public AuthenticationController(AdminAuthService adminAuthService,
                                    SessionCookieService sessionCookieService,
                                    AdminLoginCsrfService adminLoginCsrfService) {
        this.adminAuthService = adminAuthService;
        this.sessionCookieService = sessionCookieService;
        this.adminLoginCsrfService = adminLoginCsrfService;
    }

    @GetMapping("/login")
    public String loginPage(@NotNull Model model, @NotNull HttpServletResponse response) {
        model.addAttribute("userLogin", new UserLoginRequest());
        issueLoginCsrfToken(model, response);
        return "admin/login";
    }

    @PostMapping("/login")
    public String handleLogin(@Valid @ModelAttribute("userLogin") UserLoginRequest userLoginRequest,
                              BindingResult bindingResult,
                              @NotNull HttpServletRequest request,
                              @NotNull Model model,
                              HttpServletResponse response) {
        if (!adminLoginCsrfService.isValid(readCookie(request, SessionCookieService.LOGIN_CSRF_COOKIE_NAME),
                request.getParameter(CsrfTokenInterceptor.CSRF_PARAM_NAME))) {
            // Thiếu/sai token login-CSRF: từ chối xác thực, quay lại trang login (GET cấp token mới)
            return "redirect:/admin/login";
        }
        validatePassword(userLoginRequest, bindingResult);
        if (bindingResult.hasErrors()) {
            // Render lại trang login trực tiếp nên phải cấp token login-CSRF mới cho lần submit tiếp theo
            issueLoginCsrfToken(model, response);
            return "admin/login";
        }
        Optional<LoginResult> loginResult = adminAuthService.login(userLoginRequest.getUserName(),
                userLoginRequest.getUserPassword(), userLoginRequest.isRememberMe());
        if (loginResult.isEmpty()) {
            return "redirect:/admin/login?error";
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createSessionCookie(loginResult.get().getRawSessionId()).toString());
        if (loginResult.get().getRawRememberToken() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    sessionCookieService.createRememberMeCookie(loginResult.get().getRawRememberToken()).toString());
        } else {
            // Login không ghi nhớ: xoá cookie remember cũ còn sót trên trình duyệt (token đã bị xoá ở DB)
            response.addHeader(HttpHeaders.SET_COOKIE,
                    sessionCookieService.createExpiredRememberMeCookie().toString());
        }
        return "redirect:/admin/home";
    }

    @PostMapping("/logout")
    public String handleLogout(HttpServletRequest request, HttpServletResponse response) {
        adminAuthService.logout(readCookie(request, SessionCookieService.SESSION_COOKIE_NAME),
                readCookie(request, SessionCookieService.REMEMBER_ME_COOKIE_NAME));
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createExpiredSessionCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createExpiredRememberMeCookie().toString());
        return "redirect:/admin/login";
    }

    private void validatePassword(@NotNull UserLoginRequest userLoginRequest, BindingResult bindingResult) {
        if (userLoginRequest.getUserPassword() == null ||
                userLoginRequest.getUserPassword().isBlank()) {
            bindingResult.rejectValue("userPassword", "error.userPassword", MessageConstant.USERPASSWORD_NOT_BLANK);
        } else {
            String password = userLoginRequest.getUserPassword();
            if (password.length() < 8 || password.length() > 12) {
                bindingResult.rejectValue("userPassword", "error.userPassword", MessageConstant.USERPASSWORD_SIZE);
            }
        }
    }

    /**
     * Sinh token login-CSRF mới: đặt cookie ngắn hạn {@code ADMIN_LOGIN_CSRF} trên response
     * và đưa token vào model cho hidden field của form đăng nhập.
     *
     * @param model    model của view đăng nhập
     * @param response response hiện tại để gắn cookie
     */
    private void issueLoginCsrfToken(@NotNull Model model, @NotNull HttpServletResponse response) {
        String loginCsrfToken = adminLoginCsrfService.generateToken();
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createLoginCsrfCookie(loginCsrfToken).toString());
        model.addAttribute("loginCsrfToken", loginCsrfToken);
    }

    /**
     * Đọc giá trị cookie theo tên khỏi request; trả về null khi request không có cookie đó.
     *
     * @param request    request hiện tại
     * @param cookieName tên cookie cần đọc
     * @return giá trị cookie hoặc null nếu không có
     */
    private @Nullable String readCookie(@NotNull HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
