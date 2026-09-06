package com.restaurant.ilikepho.admin.service;

import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.entity.SessionStatus;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import com.restaurant.ilikepho.admin.repository.AdminSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Dịch vụ thao tác phiên đăng nhập admin: băm session ID, tạo phiên nguyên tử,
 * tra cứu phiên hoạt động, cập nhật hoạt động và khoá phiên.
 */
@Service
public class AdminSessionService {

    private final AdminRepository adminRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final SessionIdGenerator sessionIdGenerator;

    /**
     * Múi giờ cố định cho mọi thao tác thời gian của phiên (ghi timestamp và tính khoảng cách),
     * không phụ thuộc múi giờ mặc định của máy chạy ứng dụng.
     */
    private static final ZoneId SESSION_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public AdminSessionService(AdminRepository adminRepository,
                               AdminSessionRepository adminSessionRepository,
                               SessionIdGenerator sessionIdGenerator) {
        this.adminRepository = adminRepository;
        this.adminSessionRepository = adminSessionRepository;
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
     * Băm chuỗi session ID gốc bằng SHA-256 (hex) để lưu vào DB, không lưu chuỗi gốc.
     *
     * @param rawSessionId chuỗi session ID gốc cần băm
     * @return chuỗi hash SHA-256 dạng hex
     */
    public String hashSessionId(String rawSessionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng", e);
        }
    }

    /**
     * Tạo phiên đăng nhập mới cho admin trong một transaction:
     * khoá dòng admin (pessimistic write) để tuần tự hoá đăng nhập đồng thời,
     * khoá mọi phiên ACTIVE cũ (1 admin = 1 phiên hoạt động),
     * sinh CSRF token riêng cho phiên rồi chèn phiên ACTIVE mới.
     *
     * @param adminId      id tài khoản admin
     * @param rawSessionId chuỗi session ID gốc (chỉ lưu hash vào DB)
     * @return phiên mới đã lưu
     */
    @Transactional
    public AdminSession createSession(Long adminId, String rawSessionId) {
        adminRepository.findWithLockingById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản admin không tồn tại: " + adminId));

        adminSessionRepository.lockAllActiveByAdminId(adminId, SessionStatus.ACTIVE, SessionStatus.LOCKED);

        AdminSession session = new AdminSession();
        session.setAdminId(adminId);
        session.setSessionHash(hashSessionId(rawSessionId));
        session.setCsrfToken(sessionIdGenerator.generate());
        session.setStatus(SessionStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now(SESSION_ZONE);
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        return adminSessionRepository.save(session);
    }

    /**
     * Tìm phiên đang hoạt động theo hash của session ID.
     *
     * @param sessionHash hash của session ID cần tìm
     * @return phiên ACTIVE tìm thấy hoặc rỗng nếu không tồn tại
     */
    public Optional<AdminSession> findActiveSession(String sessionHash) {
        return adminSessionRepository.findBySessionHashAndStatus(sessionHash, SessionStatus.ACTIVE);
    }

    /**
     * Cập nhật thời điểm hoạt động gần nhất của phiên (phục vụ sliding expiration).
     *
     * @param session phiên cần cập nhật
     */
    @Transactional
    public void updateLastActivity(AdminSession session) {
        session.setLastActivityAt(LocalDateTime.now(SESSION_ZONE));
        adminSessionRepository.save(session);
    }

    /**
     * Khoá phiên (xoá logic) — đổi trạng thái sang LOCKED.
     *
     * @param session phiên cần khoá
     */
    @Transactional
    public void lockSession(AdminSession session) {
        session.setStatus(SessionStatus.LOCKED);
        adminSessionRepository.save(session);
    }

    /**
     * Kiểm tra phiên đã hết hạn do không hoạt động quá số phút cho phép.
     * Khoảng thời gian được tính giữa hai kiểu nhận biết múi giờ theo {@link #SESSION_ZONE}.
     *
     * @param session        phiên cần kiểm tra
     * @param timeoutMinutes số phút không hoạt động tối đa
     * @return {@code true} nếu phiên đã hết hạn, ngược lại {@code false}
     */
    public boolean isExpired(AdminSession session, long timeoutMinutes) {
        return Duration.between(session.getLastActivityAt().atZone(SESSION_ZONE), ZonedDateTime.now(SESSION_ZONE))
                .toMinutes() > timeoutMinutes;
    }
}
