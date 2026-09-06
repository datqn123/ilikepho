package com.restaurant.ilikepho.admin.repository;

import com.restaurant.ilikepho.admin.entity.AdminRememberMe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository truy cập dữ liệu token "ghi nhớ đăng nhập" của admin.
 */
public interface AdminRememberMeRepository extends JpaRepository<AdminRememberMe, Long> {

    /**
     * Tìm remember token theo hash của token gốc.
     *
     * @param tokenHash hash SHA-256 của token cần tìm
     * @return token tìm thấy hoặc rỗng nếu không tồn tại
     */
    Optional<AdminRememberMe> findByTokenHash(String tokenHash);

    /**
     * Xoá mọi remember token của một admin (duy trì chính sách 1 admin = 1 token sống).
     *
     * @param adminId id tài khoản admin cần xoá token
     * @return số dòng đã xoá
     */
    @Modifying
    @Query("delete from AdminRememberMe r where r.adminId = :adminId")
    int deleteByAdminId(@Param("adminId") Long adminId);

    /**
     * Xoá mọi remember token đã quá hạn dùng theo mốc thời gian cho trước
     * (job định kỳ giới hạn tăng trưởng bảng; xoá theo điều kiện nên idempotent).
     *
     * @param now mốc thời điểm hiện tại theo múi giờ phiên
     * @return số dòng đã xoá
     */
    @Modifying
    @Query("delete from AdminRememberMe r where r.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") LocalDateTime now);
}
