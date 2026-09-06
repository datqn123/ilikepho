package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.entity.AdminRememberMe;
import com.restaurant.ilikepho.admin.repository.AdminRememberMeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Dịch vụ token "ghi nhớ đăng nhập" của admin: tạo token (giữ 1 admin = 1 token sống),
 * tra token còn hạn, xoay vòng token sau mỗi lần nối lại phiên và xoá token.
 * DB chỉ lưu hash SHA-256 của token; thời gian thao tác theo {@link AdminSessionService#SESSION_ZONE}.
 */
@Service
public class AdminRememberMeService {

    private final AdminRememberMeRepository adminRememberMeRepository;
    private final AdminSessionService adminSessionService;
    private final SessionIdGenerator sessionIdGenerator;
    private final long maxAgeDays;

    public AdminRememberMeService(AdminRememberMeRepository adminRememberMeRepository,
                                  AdminSessionService adminSessionService,
                                  SessionIdGenerator sessionIdGenerator,
                                  @Value("${admin.remember-me.max-age-days:30}") long maxAgeDays) {
        this.adminRememberMeRepository = adminRememberMeRepository;
        this.adminSessionService = adminSessionService;
        this.sessionIdGenerator = sessionIdGenerator;
        this.maxAgeDays = maxAgeDays;
    }

    /**
     * Tạo remember token mới cho admin: xoá token cũ (1 admin = 1 token sống),
     * sinh token ngẫu nhiên 256-bit và lưu hash SHA-256 kèm hạn dùng.
     *
     * @param adminId id tài khoản admin
     * @return chuỗi token gốc đặt vào cookie (không lưu chuỗi gốc vào DB)
     */
    @Transactional
    public String createToken(Long adminId) {
        adminRememberMeRepository.deleteByAdminId(adminId);
        String rawToken = sessionIdGenerator.generate();
        AdminRememberMe token = new AdminRememberMe();
        token.setAdminId(adminId);
        token.setTokenHash(adminSessionService.hashSessionId(rawToken));
        LocalDateTime now = LocalDateTime.now(AdminSessionService.SESSION_ZONE);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusDays(maxAgeDays));
        adminRememberMeRepository.save(token);
        return rawToken;
    }

    /**
     * Tra remember token theo chuỗi token gốc từ cookie: băm SHA-256 rồi tra DB,
     * chỉ nhận token còn hạn (đã hết hạn coi như không tồn tại).
     *
     * @param rawToken chuỗi token gốc đọc từ cookie ghi nhớ
     * @return token còn hạn hoặc rỗng nếu không tồn tại / đã hết hạn
     */
    public Optional<AdminRememberMe> findValidByTokenHash(String rawToken) {
        return adminRememberMeRepository.findByTokenHash(adminSessionService.hashSessionId(rawToken))
                .filter(token -> !isExpired(token));
    }

    /**
     * Xoay vòng remember token sau khi nối lại phiên: vô hiệu token cũ
     * (nếu đúng thuộc admin) rồi tạo token mới kèm hạn dùng mới.
     *
     * @param adminId      id tài khoản admin đang nối lại phiên
     * @param oldRawToken  chuỗi token gốc cũ đọc từ cookie
     * @return chuỗi token gốc mới đặt vào cookie
     */
    @Transactional
    public String rotate(Long adminId, String oldRawToken) {
        adminRememberMeRepository.findByTokenHash(adminSessionService.hashSessionId(oldRawToken))
                .filter(token -> token.getAdminId().equals(adminId))
                .ifPresent(adminRememberMeRepository::delete);
        return createToken(adminId);
    }

    /**
     * Xoá remember token theo chuỗi token gốc từ cookie (dùng khi đăng xuất,
     * bao phủ cả trường hợp phiên đã chết nên không tra được adminId).
     *
     * @param rawToken chuỗi token gốc đọc từ cookie ghi nhớ
     */
    @Transactional
    public void deleteByRawToken(String rawToken) {
        adminRememberMeRepository.findByTokenHash(adminSessionService.hashSessionId(rawToken))
                .ifPresent(adminRememberMeRepository::delete);
    }

    /**
     * Xoá mọi remember token của một admin (dùng khi đăng nhập không ghi nhớ hoặc đăng xuất).
     *
     * @param adminId id tài khoản admin cần xoá token
     */
    @Transactional
    public void deleteAllByAdminId(Long adminId) {
        adminRememberMeRepository.deleteByAdminId(adminId);
    }

    /**
     * Dọn dẹp định kỳ các remember token đã hết hạn khỏi DB để giới hạn tăng trưởng bảng
     * (token hết hạn đã bị bỏ qua khi tra cứu từ trước); xoá theo điều kiện nên job
     * idempotent, an toàn khi nhiều instance cùng chạy. Cron cấu hình qua property
     * {@code admin.remember-me.cleanup-cron}, mặc định 3h sáng theo múi giờ phiên.
     */
    @Scheduled(cron = "${admin.remember-me.cleanup-cron:0 0 3 * * *}",
            zone = AdminSessionService.SESSION_ZONE_ID)
    @Transactional
    public void cleanupExpiredTokens() {
        adminRememberMeRepository.deleteAllExpiredBefore(
                LocalDateTime.now(AdminSessionService.SESSION_ZONE));
    }

    /**
     * Kiểm tra token đã quá hạn dùng hay chưa; so sánh giữa hai kiểu nhận biết múi giờ
     * theo {@link AdminSessionService#SESSION_ZONE}, không so LocalDateTime trần.
     *
     * @param token token cần kiểm tra
     * @return {@code true} nếu thời điểm hiện tại đã qua hạn dùng
     */
    private boolean isExpired(AdminRememberMe token) {
        return ZonedDateTime.now(AdminSessionService.SESSION_ZONE)
                .isAfter(token.getExpiresAt().atZone(AdminSessionService.SESSION_ZONE));
    }
}
