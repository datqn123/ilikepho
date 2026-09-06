package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.dto.LoginResult;
import com.restaurant.ilikepho.admin.entity.Admin;
import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Dịch vụ điều phối luồng đăng nhập và đăng xuất của tài khoản admin,
 * bao gồm tạo/xoá remember token cho tính năng "ghi nhớ đăng nhập".
 * Mỗi luồng chạy trong một transaction: mọi ghi (tạo phiên, tạo/xoá token)
 * cùng thành công hoặc cùng rollback, đồng thời pessimistic lock trên dòng admin
 * (lấy khi tạo phiên) tuần tự hoá hai login/logout đồng thời của cùng admin.
 */
@Service
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final SessionIdGenerator sessionIdGenerator;
    private final AdminSessionService adminSessionService;
    private final AdminRememberMeService adminRememberMeService;

    public AdminAuthService(AdminRepository adminRepository,
                            PasswordService passwordService,
                            SessionIdGenerator sessionIdGenerator,
                            AdminSessionService adminSessionService,
                            AdminRememberMeService adminRememberMeService) {
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
        this.sessionIdGenerator = sessionIdGenerator;
        this.adminSessionService = adminSessionService;
        this.adminRememberMeService = adminRememberMeService;
    }

    /**
     * Xác thực tài khoản admin, tạo phiên đăng nhập mới khi thành công;
     * bật ghi nhớ đăng nhập thì tạo remember token mới, không bật thì xoá remember token cũ (nếu có).
     * Chạy trong một transaction: pessimistic lock lấy tại {@link AdminSessionService#createSession}
     * giữ đến commit nên hai login đồng thời của cùng admin được tuần tự hoá, không thể tạo 2 token sống;
     * lỗi khi tạo token sẽ rollback cả phiên — không còn phiên mồ côi.
     * Không tiết lộ tài khoản có tồn tại hay không khi xác thực thất bại.
     *
     * @param username    tên đăng nhập
     * @param rawPassword mật khẩu dạng thô
     * @param rememberMe  có bật "ghi nhớ đăng nhập" hay không
     * @return kết quả đăng nhập chứa session ID gốc và remember token gốc (null khi không nhớ), rỗng nếu thất bại
     */
    @Transactional
    public Optional<LoginResult> login(String username, String rawPassword, boolean rememberMe) {
        Optional<Admin> admin = adminRepository.findByUsername(username);
        if (admin.isEmpty() || !passwordService.matches(rawPassword, admin.get().getPasswordHash())) {
            return Optional.empty();
        }
        Long adminId = admin.get().getId();
        String rawSessionId = sessionIdGenerator.generate();
        adminSessionService.createSession(adminId, rawSessionId);
        String rawRememberToken = null;
        if (rememberMe) {
            rawRememberToken = adminRememberMeService.createToken(adminId);
        } else {
            adminRememberMeService.deleteAllByAdminId(adminId);
        }
        return Optional.of(new LoginResult(rawSessionId, rawRememberToken));
    }

    /**
     * Đăng xuất: khoá phiên hiện tại (nếu còn hoạt động) và xoá remember token —
     * theo token gốc từ cookie (bao phủ cả khi phiên đã chết, không tra được adminId)
     * và theo adminId của phiên vừa khoá (bao phủ cả khi cookie remember không còn trên máy).
     * Chạy trong một transaction để mọi ghi khoá phiên + xoá token là nguyên tử.
     *
     * @param rawSessionId     chuỗi session ID gốc đọc từ cookie phiên, có thể null
     * @param rawRememberToken chuỗi remember token gốc đọc từ cookie ghi nhớ, có thể null
     */
    @Transactional
    public void logout(String rawSessionId, String rawRememberToken) {
        if (rawSessionId != null) {
            String sessionHash = adminSessionService.hashSessionId(rawSessionId);
            adminSessionService.findActiveSession(sessionHash).ifPresent(session -> {
                adminSessionService.lockSession(session);
                adminRememberMeService.deleteAllByAdminId(session.getAdminId());
            });
        }
        if (rawRememberToken != null) {
            adminRememberMeService.deleteByRawToken(rawRememberToken);
        }
    }
}
