package com.restaurant.ilikepho.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller trang tổng quan khu vực admin (trang đích sau khi đăng nhập).
 */
@Controller
public class AdminHomeController {

    @GetMapping("/admin/home")
    public String home() {
        return "admin/home";
    }
}
