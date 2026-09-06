package com.restaurant.ilikepho.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật scheduler cho các job định kỳ của hệ admin
 * (hiện tại: dọn remember token hết hạn theo lịch cấu hình trong AdminRememberMeService).
 */
@Configuration
@EnableScheduling
public class AdminSchedulingConfig {
}
