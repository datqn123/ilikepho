package com.restaurant.ilikepho.admin.config;

import com.restaurant.ilikepho.admin.entity.Admin;
import com.restaurant.ilikepho.admin.repository.AdminRepository;
import com.restaurant.ilikepho.admin.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra seed tài khoản admin cho dev: tạo khi chưa tồn tại, bỏ qua khi đã tồn tại
 * hoặc mật khẩu seed rỗng, chạy lại không tạo trùng (idempotent).
 */
@ExtendWith(MockitoExtension.class)
class AdminDataSeederTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordService passwordService;

    private AdminDataSeeder seeder() {
        return new AdminDataSeeder(adminRepository, passwordService, "admin", "Admin@123");
    }

    @Test
    void run_chuaTonTai_taoAdminVoiMatKhauDaHash() {
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordService.hash("Admin@123")).thenReturn("bcrypt-hash");

        seeder().run();

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void run_daTonTai_khongTaoTrungKhongGhiDeMatKhau() {
        Admin daCo = new Admin();
        daCo.setUsername("admin");
        daCo.setPasswordHash("hash-cu");
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(daCo));

        seeder().run();

        verify(adminRepository, never()).save(any());
    }

    @Test
    void run_matKhauSeedDeTrong_khongLamGi() {
        AdminDataSeeder seederMatKhauRong =
                new AdminDataSeeder(adminRepository, passwordService, "admin", "  ");

        seederMatKhauRong.run();

        verifyNoInteractions(adminRepository);
        verifyNoInteractions(passwordService);
    }

    @Test
    void run_usernameSeedDeTrong_khongLamGi() {
        AdminDataSeeder seederUsernameRong =
                new AdminDataSeeder(adminRepository, passwordService, " ", "Admin@123");

        seederUsernameRong.run();

        verifyNoInteractions(adminRepository);
        verifyNoInteractions(passwordService);
    }

    @Test
    void run_chayHaiLan_chiTaoMotLan() {
        when(adminRepository.findByUsername("admin"))
                .thenReturn(Optional.empty(), Optional.of(new Admin()));
        when(passwordService.hash("Admin@123")).thenReturn("bcrypt-hash");

        seeder().run();
        seeder().run();

        verify(adminRepository).save(any(Admin.class));
    }
}
