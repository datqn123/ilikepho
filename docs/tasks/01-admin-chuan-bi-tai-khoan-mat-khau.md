# Tài liệu triển khai Task — Chuẩn bị tài khoản admin & mật khẩu (Phần 1)

## Thông tin task

- **Số thứ tự**: 01
- **Hệ**: admin
- **Nhiệm vụ**: Chuẩn bị tài khoản admin & mật khẩu (triển khai Phần 1 trong `PLAN_ADMIN_LOGIN.md`)
- **Ngày tạo**: 2026-08-22

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Hệ admin cần chức năng đăng nhập (session-based). Hiện tại `AuthenticationController` chưa có nguồn dữ liệu tài khoản admin, chưa có bảng lưu tài khoản, chưa có cơ chế hash mật khẩu. Phần 1 là bước chuẩn bị nền tảng dữ liệu & bảo mật cho toàn bộ luồng đăng nhập.
- **Mục tiêu**: Tạo bảng `admin` để quản lý tài khoản quản trị viên, và xây dựng cơ chế lưu mật khẩu bằng hash chậm **bcrypt** (kèm salt riêng từng user), không lưu plain text.
- **Người dùng / người hưởng lợi**: Quản trị viên; kết quả task phục vụ trực tiếp các Phần 2–8 (session, login, middleware, logout…) của `PLAN_ADMIN_LOGIN.md`.

## 2. Phạm vi

### In scope (việc thuộc task)
- Tạo entity `Admin` + bảng `admin` (username, password_hash, timestamps) — đặt trong package admin.
- Tạo `AdminRepository` (trong package admin) để truy vấn tài khoản admin (theo username).
- Tạo `PasswordService` dùng **bcrypt** để hash mật khẩu và kiểm tra mật khẩu (per-user salt do bcrypt nhúng sẵn) — **chỉ dùng riêng cho hệ admin**, đặt trong package admin, KHÔNG tách thành service common.
- Thêm dependency `spring-security-crypto` để dùng `BCryptPasswordEncoder`.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- Không seed/tạo sẵn tài khoản admin mặc định lúc khởi động (người dùng chọn: chỉ tạo cấu trúc + service).
- Không làm luồng đăng nhập thực tế (xác thực, tạo session) — thuộc Phần 4.
- Không tạo bảng session, cookie, middleware, logout, CSRF, bảo mật production — các Phần 2–8.
- Không thêm các trường quản lý tài khoản mở rộng (email, role, trạng thái active…) lần này.
- Không tạo service hash dùng chung cho hệ user: hệ user sẽ tự làm riêng, không dùng chung `PasswordService` admin.

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Tạo entity `Admin` ánh xạ bảng `admin` gồm: `id` (khóa chính tự tăng), `username` (duy nhất, không rỗng), `passwordHash` (không rỗng), `createdAt`, `updatedAt`. | Đặt trong `com.restaurant.ilikepho.admin.entity`; không dùng quan hệ JPA. |
| FR-02 | `AdminRepository` kế thừa `JpaRepository<Admin, Long>` có method `Optional<Admin> findByUsername(String username)`. | Đặt trong `com.restaurant.ilikepho.admin.repository`; phục vụ tra cứu tài khoản. |
| FR-03 | `PasswordService` (admin) có method `String hash(String rawPassword)` trả về chuỗi bcrypt (salt riêng từng user nhúng trong hash). | Không lưu plain text; chỉ dùng riêng cho hệ admin. |
| FR-04 | `PasswordService` (admin) có method `boolean matches(String rawPassword, String passwordHash)` để so sánh mật khẩu. | Phục vụ xác thực ở Phần 4. |
| FR-05 | Dùng `BCryptPasswordEncoder` (spring-security-crypto) để hash/kiểm tra. | |

## 4. Yêu cầu phi chức năng

- **Hiệu năng**: bcrypt mặc định (strength 10) đủ chậm để chống brute-force; đảm bảo hash ổn định.
- **Bảo mật / quyền hạn**: mật khẩu KHÔNG bao giờ lưu plain text; mỗi user có salt riêng (bcrypt sinh salt ngẫu nhiên mỗi lần hash). Không log/hiển thị mật khẩu.
- **Khả năng mở rộng / bảo trì**: `PasswordService` nằm trong package admin, tách biệt với hệ user; mỗi method có Javadoc mô tả chức năng.
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (phân tách package admin/user rõ ràng), quy tắc đặt tên, comment (Javadoc cho method), không comment rác.

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring Boot (JPA + Thymeleaf + Web MVC), PostgreSQL, Lombok. Hệ admin có package gốc `com.restaurant.ilikepho.admin` (đã có `admin.controller`, `admin.dto`); task này bổ sung `admin.entity`, `admin.repository`, `admin.service`.
- **Luồng dữ liệu / luồng xử lý**: `Admin` (entity) ↔ `AdminRepository` (JPA) ↔ dữ liệu bảng `admin`. `PasswordService` (admin) dùng `BCryptPasswordEncoder` để `hash()` khi tạo tài khoản và `matches()` khi xác thực (dùng ở Phần 4).
- **Các quyết định thiết kế**:
  - **bcrypt** thay vì argon2: đã được người dùng chọn; chuẩn, không cần thư viện native, salt nhúng sẵn trong chuỗi hash nên đáp ứng "salt riêng từng user".
  - **Toàn bộ thành phần admin đặt trong package `...admin...`**: entity → `admin.entity`, repository → `admin.repository`, service → `admin.service`. Tuân theo `admin-build`; hệ admin và hệ user hoàn toàn tách biệt.
  - **`PasswordService` chỉ dùng riêng cho hệ admin**, không tách thành service common — hệ user sẽ tự làm riêng.
  - **Không seed tài khoản mặc định** — theo lựa chọn của người dùng; chỉ tạo cấu trúc + service.
- **Điểm chạm hệ thống**: bảng `admin` trong PostgreSQL (JPA `ddl-auto=update` tự tạo); `pom.xml` thêm dependency.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Thêm dependency `spring-security-crypto` vào `pom.xml`.
- **S-02**: Tạo entity `Admin` trong `com.restaurant.ilikepho.admin.entity`.
- **S-03**: Tạo `AdminRepository` trong `com.restaurant.ilikepho.admin.repository`.
- **S-04**: Tạo `PasswordService` trong `com.restaurant.ilikepho.admin.service` dùng `BCryptPasswordEncoder` (method `hash`, `matches`).
- **S-05**: Biên dịch (`mvn -q compile`) để xác nhận dự án build được.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|--------------------|-------|
| `pom.xml` | sửa | Thêm dependency `spring-security-crypto` (FR-05 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/entity/Admin.java` | tạo | Entity ánh xạ bảng `admin`, đặt trong package admin (FR-01 / S-02). |
| `src/main/java/com/restaurant/ilikepho/admin/repository/AdminRepository.java` | tạo | Truy vấn tài khoản admin (FR-02 / S-03). |
| `src/main/java/com/restaurant/ilikepho/admin/service/PasswordService.java` | tạo | Hash/kiểm tra mật khẩu bcrypt riêng cho hệ admin (FR-03, FR-04, FR-05 / S-04). |
| `src/main/java/com/restaurant/ilikepho/entity/Admin.java` | xoá (nếu đã tạo nhầm) | Do lần đầu đặt nhầm vào package entity dùng chung; chuyển sang `admin.entity`. |

## 8. Rủi ro & giả định

- **Rủi ro**: phiên bản Spring Boot 4.1 có thể đóng gói `spring-security-crypto` với cách khởi tạo khác; cần biên dịch/test xác nhận. Giảm thiểu bằng S-05.
- **Rủi ro**: `ddl-auto=update` tự tạo bảng có thể không đúng cấu trúc mong muốn nếu entity khai báo sai; giảm thiểu bằng khai báo `@Column` rõ ràng.
- **Giả định**: DB PostgreSQL `ilikepho` chạy được và JPA tự tạo bảng `admin` khi khởi động (không cần migration tay).

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [ ] **DoD-01**: `pom.xml` có dependency `spring-security-crypto` và dự án `mvn -q compile` thành công.
- [ ] **DoD-02**: Entity `Admin` tồn tại trong `com.restaurant.ilikepho.admin.entity`, ánh xạ bảng `admin`, có các trường theo FR-01, KHÔNG khai quan hệ JPA.
- [ ] **DoD-03**: `AdminRepository.findByUsername(String)` tồn tại trong package admin.
- [ ] **DoD-04**: `PasswordService.hash(raw)` (trong `admin.service`) tạo chuỗi bcrypt khác nhau cho cùng mật khẩu (salt riêng từng user), không phải plain text.
- [ ] **DoD-05**: `PasswordService.matches(raw, hash)` trả về `true` với mật khẩu đúng, `false` với mật khẩu sai.
- [ ] **DoD-06**: Không còn entity `Admin` trong package `entity` dùng chung (không lẫn admin vào vùng user/shared).

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Seed/tạo sẵn tài khoản admin mặc định.
- Luồng đăng nhập, session, cookie, middleware, logout, CSRF, bảo mật production (Phần 2–8).
- Trường quản lý tài khoản mở rộng (email, role, trạng thái…).
- Service hash dùng chung / cho hệ user.
