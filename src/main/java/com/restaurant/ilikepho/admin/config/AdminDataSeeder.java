package com.restaurant.ilikepho.admin.config;

import com.restaurant.ilikepho.admin.entity.Admin;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import com.restaurant.ilikepho.admin.service.AdminSessionService;
import com.restaurant.ilikepho.admin.service.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seed tài khoản admin chỉ dùng cho môi trường dev/local nhằm kiểm chứng end-to-end:
 * bean chỉ được tạo khi bật property {@code admin.seed.enabled=true} nên tuyệt đối
 * không tự chạy ở production. Seed idempotent: chỉ tạo khi username cấu hình chưa tồn tại
 * và mật khẩu seed cấu hình khác rỗng; không bao giờ ghi đè mật khẩu đã có.
 */
@Component
@ConditionalOnProperty(name = "admin.seed.enabled", havingValue = "true")
public class AdminDataSeeder implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final String seedUsername;
    private final String seedPassword;

    public AdminDataSeeder(AdminRepository adminRepository,
                           PasswordService passwordService,
                           @Value("${admin.seed.username:admin}") String seedUsername,
                           @Value("${admin.seed.password:}") String seedPassword) {
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
    }

    /**
     * Chạy seed lúc khởi động: bỏ qua khi username hoặc mật khẩu seed rỗng (không cấu hình
     * mặc định có giá trị) hoặc username đã tồn tại; ngược lại tạo tài khoản admin mới
     * với mật khẩu đã băm bcrypt.
     *
     * @param args tham số dòng lệnh (không dùng)
     */
    @Override
    public void run(String... args) {
        if (seedUsername == null || seedUsername.isBlank()
                || seedPassword == null || seedPassword.isBlank()) {
            return;
        }
        if (adminRepository.findByUsername(seedUsername).isPresent()) {
            return;
        }
        Admin admin = new Admin();
        admin.setUsername(seedUsername);
        admin.setPasswordHash(passwordService.hash(seedPassword));
        LocalDateTime now = LocalDateTime.now(AdminSessionService.SESSION_ZONE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminRepository.save(admin);
    }
}
