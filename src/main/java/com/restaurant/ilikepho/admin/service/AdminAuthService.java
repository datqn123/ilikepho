package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.entity.Admin;
import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Dịch vụ điều phối luồng đăng nhập và đăng xuất của tài khoản admin.
 */
@Service
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final SessionIdGenerator sessionIdGenerator;
    private final AdminSessionService adminSessionService;

    public AdminAuthService(AdminRepository adminRepository,
                            PasswordService passwordService,
                            SessionIdGenerator sessionIdGenerator,
                            AdminSessionService adminSessionService) {
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
        this.sessionIdGenerator = sessionIdGenerator;
        this.adminSessionService = adminSessionService;
    }

    /**
     * Xác thực tài khoản admin và tạo phiên đăng nhập mới khi thành công.
     * Không tiết lộ tài khoản có tồn tại hay không khi xác thực thất bại.
     *
     * @param username    tên đăng nhập
     * @param rawPassword mật khẩu dạng thô
     * @return chuỗi session ID gốc (đặt vào cookie) nếu đăng nhập thành công, rỗng nếu thất bại
     */
    public Optional<String> login(String username, String rawPassword) {
        Optional<Admin> admin = adminRepository.findByUsername(username);
        if (admin.isEmpty() || !passwordService.matches(rawPassword, admin.get().getPasswordHash())) {
            return Optional.empty();
        }
        String rawSessionId = sessionIdGenerator.generate();
        adminSessionService.createSession(admin.get().getId(), rawSessionId);
        return Optional.of(rawSessionId);
    }

    /**
     * Khoá phiên đăng nhập hiện tại (xoá logic) khi đăng xuất; bỏ qua nếu phiên không tồn tại.
     *
     * @param rawSessionId chuỗi session ID gốc lấy từ cookie
     */
    public void logout(String rawSessionId) {
        String sessionHash = adminSessionService.hashSessionId(rawSessionId);
        adminSessionService.findActiveSession(sessionHash).ifPresent(adminSessionService::lockSession);
    }
}
