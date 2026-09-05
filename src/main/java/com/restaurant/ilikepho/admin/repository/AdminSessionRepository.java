package com.restaurant.ilikepho.admin.repository;

import com.restaurant.ilikepho.admin.entity.AdminSession;
import com.restaurant.ilikepho.admin.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository truy cập dữ liệu phiên đăng nhập admin.
 */
public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {

    /**
     * Tìm phiên admin theo hash của session ID.
     *
     * @param sessionHash hash của session ID cần tìm
     * @return phiên tìm thấy hoặc rỗng nếu không tồn tại
     */
    Optional<AdminSession> findBySessionHash(String sessionHash);

    /**
     * Tìm phiên admin theo hash của session ID và trạng thái.
     *
     * @param sessionHash hash của session ID cần tìm
     * @param status      trạng thái phiên cần lọc
     * @return phiên tìm thấy hoặc rỗng nếu không tồn tại
     */
    Optional<AdminSession> findBySessionHashAndStatus(String sessionHash, SessionStatus status);

    /**
     * Khoá (xoá logic) mọi phiên đang hoạt động của một admin (chính sách 1 admin = 1 phiên hoạt động).
     *
     * @param adminId id tài khoản admin cần khoá phiên
     * @param active  trạng thái phiên đang hoạt động (ACTIVE)
     * @param locked  trạng thái phiên bị khoá (LOCKED)
     * @return số phiên đã bị khoá
     */
    @Modifying
    @Query("update AdminSession s set s.status = :locked where s.adminId = :adminId and s.status = :active")
    int lockAllActiveByAdminId(@Param("adminId") Long adminId,
                               @Param("active") SessionStatus active,
                               @Param("locked") SessionStatus locked);
}
