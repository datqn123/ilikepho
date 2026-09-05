package com.restaurant.ilikepho.admin.controller;

import com.restaurant.ilikepho.admin.dto.UserLoginRequest;
import com.restaurant.ilikepho.admin.service.AdminAuthService;
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

    public AuthenticationController(AdminAuthService adminAuthService,
                                    SessionCookieService sessionCookieService) {
        this.adminAuthService = adminAuthService;
        this.sessionCookieService = sessionCookieService;
    }

    @GetMapping("/login")
    public String loginPage(@NotNull Model model) {
        UserLoginRequest userLoginRequest = new UserLoginRequest();
        model.addAttribute("userLogin", userLoginRequest);
        return "admin/login";
    }

    @PostMapping("/login")
    public String handleLogin(@Valid @ModelAttribute("userLogin") UserLoginRequest userLoginRequest,
                              BindingResult bindingResult,
                              HttpServletResponse response) {
        validatePassword(userLoginRequest, bindingResult);
        if (bindingResult.hasErrors()) {
            return "admin/login";
        }
        Optional<String> rawSessionId = adminAuthService.login(userLoginRequest.getUserName(),
                userLoginRequest.getUserPassword());
        if (rawSessionId.isEmpty()) {
            return "redirect:/admin/login?error";
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createSessionCookie(rawSessionId.get()).toString());
        return "redirect:/admin/home";
    }

    @PostMapping("/logout")
    public String handleLogout(HttpServletRequest request, HttpServletResponse response) {
        String rawSessionId = readSessionCookie(request);
        if (rawSessionId != null) {
            adminAuthService.logout(rawSessionId);
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookieService.createExpiredSessionCookie().toString());
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

    private @Nullable String readSessionCookie(@NotNull HttpServletRequest request) {
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
