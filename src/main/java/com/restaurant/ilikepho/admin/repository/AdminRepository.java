package com.restaurant.ilikepho.admin.repository;

import com.restaurant.ilikepho.admin.entity.Admin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository truy cập dữ liệu tài khoản quản trị viên.
 */
public interface AdminRepository extends JpaRepository<Admin, Long> {

    /**
     * Tìm tài khoản admin theo tên đăng nhập.
     *
     * @param username tên đăng nhập cần tìm
     * @return tài khoản admin tìm thấy hoặc rỗng nếu không tồn tại
     */
    Optional<Admin> findByUsername(String username);

    /**
     * Tìm tài khoản admin theo id và khoá dòng (pessimistic write) để tuần tự hoá
     * các thao tác đồng thời trên cùng tài khoản (vd: đăng nhập tạo phiên).
     *
     * @param id id tài khoản cần khoá
     * @return tài khoản admin tìm thấy hoặc rỗng nếu không tồn tại
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.id = :id")
    Optional<Admin> findWithLockingById(@Param("id") Long id);
}
