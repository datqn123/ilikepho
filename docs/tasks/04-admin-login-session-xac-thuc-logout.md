# Tài liệu triển khai Task — Luồng đăng nhập, xác thực request & đăng xuất (Phần 4, 5, 6)

## Thông tin task

- **Số thứ tự**: 04
- **Hệ**: admin
- **Nhiệm vụ**: Luồng đăng nhập (login), xác thực mỗi request (middleware) và đăng xuất (logout) — triển khai Phần 4, 5, 6 trong `PLAN_ADMIN_LOGIN.md`
- **Ngày tạo**: 2026-08-22

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Hệ admin đã có: bảng `admin` + hash bcrypt (Task 01), bảng `admin_session` + enum trạng thái (Task 02), bộ sinh session ID + cookie `ADMIN_SESSION` (HttpOnly/Secure/SameSite) (Task 03). `AuthenticationController` hiện vẫn là placeholder (validate rồi trả `index`), chưa nối với dữ liệu thật.
- **Mục tiêu**: Hoàn thiện vòng đời phiên đăng nhập admin:
  - **Phần 4 — Login**: xác thực tài khoản/mật khẩu, tạo phiên mới, khoá mọi phiên cũ của admin đó (chính sách **1 admin = 1 phiên hoạt động**, xử lý nguyên tử khi đăng nhập đồng thời), đặt cookie, chuyển về trang tổng quan.
  - **Phần 5 — Middleware**: chặn mọi request `/admin/**`, đọc cookie, tra hash trong DB; phiên khoá/hết hạn → về trang đăng nhập; cập nhật `last_activity_at` (sliding expiration).
  - **Phần 6 — Logout**: khoá phiên trong DB + xoá cookie → về trang đăng nhập.
- **Người dùng / người hưởng lợi**: Quản trị viên; đây là lõi bảo mật của toàn bộ khu vực admin.

## 2. Phạm vi

### In scope (việc thuộc task)
- Nối luồng login thật vào `AuthenticationController` (xác thực + tạo phiên + cookie + redirect `/admin/home`).
- `AdminAuthService`: điều phối login/logout.
- `AdminSessionService`: hash session ID (SHA-256), tạo phiên nguyên tử (khoá phiên cũ), tra phiên ACTIVE, cập nhật `last_activity_at`, khoá phiên.
- `AdminAuthInterceptor` (HandlerInterceptor) + đăng ký cho `/admin/**` (trừ login/logout).
- Trang `/admin/home` tối giản dùng layout admin hiện có.
- Logout POST `/admin/logout` + nút đăng xuất trong topbar.
- Property `admin.session.timeout-minutes` (sliding expiration).
- Unit test cho service & interceptor; biên dịch toàn dự án.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- CSRF token (lớp 2 — Phần 7), bảo mật production (Phần 8).
- Seed tài khoản admin mặc định (đã loại trừ từ Task 01).
- Cơ chế remember-me / cookie bền vững.
- Các trang quản trị khác (thực đơn, đơn hàng…) ngoài trang tổng quan tối giản.
- Bảo vệ / tính năng cho hệ user (không đụng vùng user).

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | POST `/admin/login`: tìm admin theo username qua `AdminRepository.findByUsername`; nếu không tồn tại hoặc `PasswordService.matches` sai → redirect `/admin/login?error` (hiển thị thông báo chung, không lộ tài khoản tồn tại). | |
| FR-02 | Login thành công: `SessionIdGenerator.generate()` → hash SHA-256 chuỗi gốc → lưu `AdminSession` (ACTIVE, `createdAt`/`lastActivityAt` = now) → set cookie `ADMIN_SESSION` (chuỗi gốc) qua `SessionCookieService` → redirect `/admin/home`. | Chỉ lưu hash trong DB, cookie chứa chuỗi gốc. |
| FR-03 | **1 admin = 1 phiên hoạt động**: trước khi tạo phiên mới, khoá (xoá logic `LOCKED`) mọi phiên ACTIVE cũ của admin đó. Xử lý **nguyên tử**: trong 1 transaction, khoá dòng admin bằng pessimistic write lock rồi mới khoá phiên cũ + chèn phiên mới (chống đăng nhập đồng thời tạo 2 phiên). | |
| FR-04 | GET `/admin/home` hiển thị trang tổng quan tối giản dùng layout admin (sidebar + topbar) — trang đích sau đăng nhập. | |
| FR-05 | Interceptor chặn `/admin/**` (trừ `/admin/login`, `/admin/logout`): đọc cookie `ADMIN_SESSION` → hash → tra phiên ACTIVE theo hash; nếu thiếu cookie / phiên không tồn tại / phiên `LOCKED` → redirect `/admin/login`. | |
| FR-06 | Sliding expiration: mỗi request hợp lệ cập nhật `last_activity_at` = now; nếu `now - lastActivityAt > admin.session.timeout-minutes` → khoá phiên và redirect `/admin/login`. | |
| FR-07 | POST `/admin/logout`: khoá phiên hiện tại (nếu có) + gửi cookie xoá (maxAge 0) → redirect `/admin/login`. | |
| FR-08 | Nút "Đăng xuất" trong topbar admin là form POST tới `/admin/logout`. | |

## 4. Yêu cầu phi chức năng

- **Hiệu năng**: tra phiên theo `session_hash` (unique); cập nhật `last_activity_at` mỗi request (chi phí nhỏ).
- **Bảo mật / quyền hạn**: không lưu chuỗi session ID gốc trong DB (chỉ SHA-256); thông báo lỗi đăng nhập chung chống user enumeration; khoá phiên cũ khi đăng nhập nơi khác; interceptor bảo vệ toàn bộ `/admin/**`.
- **Khả năng mở rộng / bảo trì**: tách `AdminAuthService` (điều phối) / `AdminSessionService` (thao tác phiên) / `AdminAuthInterceptor` (xác thực) — mỗi class một trách nhiệm, dễ test; toàn bộ trong package `...admin...`.
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (package admin, khoá ngoại Long, Javadoc cho method, không comment rác).

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring MVC (Controller + HandlerInterceptor), JPA (repository, pessimistic lock), Thymeleaf (layout fragment). Các package admin: `admin.controller`, `admin.service`, `admin.repository`, `admin.entity`, `admin.interceptor`, `admin.config`.
- **Luồng dữ liệu / luồng xử lý**:
  - **Login**: `AuthenticationController` → `AdminAuthService.login(username, rawPassword)` → `AdminRepository.findByUsername` + `PasswordService.matches` → `SessionIdGenerator.generate()` → `AdminSessionService.createSession(adminId, rawId)` (transaction: lock admin row → lock phiên ACTIVE cũ → insert phiên ACTIVE) → trả chuỗi gốc → controller set cookie `ADMIN_SESSION` → redirect `/admin/home`.
  - **Request**: `AdminAuthInterceptor.preHandle` → đọc cookie → `AdminSessionService.hashSessionId` → `findActiveSession(hash)` → kiểm tra hết hạn → `updateLastActivity` → cho qua; nếu lỗi → redirect `/admin/login`.
  - **Logout**: `AuthenticationController` POST `/admin/logout` → đọc cookie → `AdminAuthService.logout(rawId)` (khoá phiên) → xoá cookie → redirect `/admin/login`.
- **Các quyết định thiết kế**:
  - **SHA-256 cho session ID** (thay vì bcrypt): bcrypt chậm không phù hợp hash session tra cứu mỗi request; SHA-256 đủ an toàn cho chuỗi 256-bit ngẫu nhiên.
  - **Pessimistic write lock trên dòng `admin`** + toàn bộ trong 1 transaction: tuần tự hoá 2 lần đăng nhập đồng thời của cùng tài khoản, đảm bảo chỉ 1 phiên ACTIVE sống sót (đáp ứng "xử lý nguyên tử khi đăng nhập đồng thời").
  - **HandlerInterceptor** làm middleware (idiomatic Spring MVC) thay vì Filter: dễ đăng ký theo path pattern, có `preHandle`.
  - **`layout.html` chuyển thành layout fragment** `layout(title, content)` để `/admin/home` tái sử dụng shell admin (sidebar/topbar/JS) — hiện chưa trang nào tham chiếu layout.html nên an toàn.
  - **Logout POST** (đã chốt) — chờ CSRF ở Phần 7.
  - **Sliding expiration qua property** `admin.session.timeout-minutes` (mặc định 30).
- **Điểm chạm hệ thống**: `application.properties` (property mới); bảng `admin_session` (đã có từ Task 02); các repository được bổ sung method; templates admin.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Bổ sung method repository: `AdminRepository.findWithLockingById` (@Lock PESSIMISTIC_WRITE); `AdminSessionRepository.findBySessionHashAndStatus` + `lockAllActiveByAdminId` (@Modifying bulk update).
- **S-02**: Tạo `AdminSessionService` (hash SHA-256, `createSession` nguyên tử, `findActiveSession`, `updateLastActivity`, `lockSession`, `isExpired`).
- **S-03**: Tạo `AdminAuthService` (`login`, `logout`).
- **S-04**: Sửa `SessionCookieService`: thêm `createExpiredSessionCookie()` (xoá cookie).
- **S-05**: Sửa `AuthenticationController`: nối login thật (FR-01, FR-02) + thêm POST `/admin/logout` (FR-07).
- **S-06**: Tạo `AdminAuthInterceptor` (FR-05, FR-06) + `AdminWebMvcConfigurer` đăng ký interceptor.
- **S-07**: Tạo `AdminHomeController` GET `/admin/home` (FR-04).
- **S-08**: Thêm property `admin.session.timeout-minutes=30` vào `application.properties`.
- **S-09**: HTML: chuyển `layout.html` thành layout fragment; tạo `admin/home.html`; sửa `admin-topbar.html` (form logout POST).
- **S-10**: Thêm test dependency Mockito vào `pom.xml`; viết unit test (service + interceptor); `mvn -q compile` + chạy test toàn bộ.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|--------------------|-------|
| `pom.xml` | sửa | Thêm `mockito-core` + `mockito-junit-jupiter` (scope test, version do Spring Boot BOM quản lý) — cần cho unit test service/interceptor (S-10). |
| `src/main/java/com/restaurant/ilikepho/admin/repository/AdminRepository.java` | sửa | Thêm `findWithLockingById` (@Lock) phục vụ nguyên tử hoá login (FR-03 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/repository/AdminSessionRepository.java` | sửa | Thêm `findBySessionHashAndStatus`, `lockAllActiveByAdminId` (FR-03, FR-05 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminSessionService.java` | tạo | Thao tác phiên: hash, tạo nguyên tử, tra cứu, cập nhật, khoá, kiểm tra hết hạn (FR-02, FR-03, FR-05, FR-06 / S-02). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminAuthService.java` | tạo | Điều phối login/logout (FR-01, FR-02, FR-07 / S-03). |
| `src/main/java/com/restaurant/ilikepho/admin/service/SessionCookieService.java` | sửa | Thêm `createExpiredSessionCookie()` (FR-07 / S-04). |
| `src/main/java/com/restaurant/ilikepho/admin/controller/AuthenticationController.java` | sửa | Nối login thật + POST logout (FR-01, FR-02, FR-07 / S-05). |
| `src/main/java/com/restaurant/ilikepho/admin/interceptor/AdminAuthInterceptor.java` | tạo | Middleware xác thực mỗi request (FR-05, FR-06 / S-06). |
| `src/main/java/com/restaurant/ilikepho/admin/config/AdminWebMvcConfigurer.java` | tạo | Đăng ký interceptor cho `/admin/**` (S-06). |
| `src/main/java/com/restaurant/ilikepho/admin/controller/AdminHomeController.java` | tạo | GET `/admin/home` (FR-04 / S-07). |
| `src/main/resources/application.properties` | sửa | Thêm `admin.session.timeout-minutes=30` (FR-06 / S-08). |
| `src/main/resources/templates/admin/layout.html` | sửa | Chuyển thành layout fragment `layout(title, content)` (FR-04 / S-09). |
| `src/main/resources/templates/admin/home.html` | tạo | Trang tổng quan tối giản (FR-04 / S-09). |
| `src/main/resources/templates/fragment/admin-topbar.html` | sửa | Nút Đăng xuất thành form POST `/admin/logout` (FR-08 / S-09). |
| `src/test/java/com/restaurant/ilikepho/admin/service/AdminSessionServiceTest.java` | tạo | Kiểm chứng hash, tạo phiên nguyên tử, hết hạn (S-10). |
| `src/test/java/com/restaurant/ilikepho/admin/service/AdminAuthServiceTest.java` | tạo | Kiểm chứng login đúng/sai, logout (S-10). |
| `src/test/java/com/restaurant/ilikepho/admin/interceptor/AdminAuthInterceptorTest.java` | tạo | Kiểm chứng chặn request hợp lệ/không hợp lệ (S-10). |

## 8. Rủi ro & giả định

- **Rủi ro**: pessimistic lock yêu cầu transaction hoạt động đúng (JPA/Hibernate); nếu DB không hỗ trợ lock theo query sẽ lỗi lúc runtime — giảm thiểu bằng bulk-update test và kiểm tra khi chạy.
- **Rủi ro**: `@Modifying` bulk update không cập nhật persistence context; các thao tác sau trong cùng transaction phải dựa trên DB — đã đảm bảo bằng cách insert phiên mới sau bulk update.
- **Rủi ro**: chuyển `layout.html` thành fragment có thể ảnh hưởng hiển thị nếu fragment expression sai — kiểm tra bằng cách chạy thử trang `/admin/home`.
- **Giả định**: chưa có tài khoản admin trong DB (chưa seed) nên **không thể kiểm chứng end-to-end login qua UI** ở task này; kiểm chứng bằng unit test + compile. Khi có tài khoản (seed hoặc insert tay) sẽ chạy thử đầy đủ.
- **Giả định**: hệ admin chạy trên context path `/`, trang admin dưới `/admin/**`, cookie path `/admin` phủ đúng vùng cần bảo vệ.

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [ ] **DoD-01**: `mvn -q compile` thành công và toàn bộ unit test (mới + cũ) pass.
- [ ] **DoD-02**: Login sai tài khoản/mật khẩu → redirect `/admin/login?error`; không tạo phiên, không set cookie.
- [ ] **DoD-03**: Login đúng → tạo 1 phiên ACTIVE (DB chỉ có hash SHA-256), set cookie `ADMIN_SESSION`, redirect `/admin/home` (test qua `AdminAuthService`).
- [ ] **DoD-04**: `createSession` khoá mọi phiên ACTIVE cũ của admin đó trong cùng transaction (test verify bulk-update được gọi; lock dòng admin trước).
- [ ] **DoD-05**: Interceptor: thiếu cookie / phiên không tồn tại / phiên LOCKED → redirect `/admin/login` và không cho qua (test pass).
- [ ] **DoD-06**: Interceptor: phiên hợp lệ → cho qua, `last_activity_at` được cập nhật (test pass).
- [ ] **DoD-07**: Phiên quá `admin.session.timeout-minutes` → bị khoá + redirect login (test `isExpired` pass).
- [ ] **DoD-08**: POST `/admin/logout` → phiên bị khoá + cookie xoá (maxAge 0) → redirect `/admin/login`.
- [ ] **DoD-09**: GET `/admin/home` render được trang dùng layout admin (sidebar + topbar + nút logout POST).
- [ ] **DoD-10**: Toàn bộ thành phần mới nằm trong package `...admin...`, không lẫn vào vùng user/shared.

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- CSRF token (lớp 2 — Phần 7) và bảo mật production (Phần 8).
- Seed tài khoản admin mặc định (đã loại trừ từ Task 01) — cần cho kiểm thử end-to-end sau này.
- Remember-me / cookie bền vững.
- Các trang quản trị chức năng khác (thực đơn, đơn hàng, danh mục…).
- Mọi tính năng thuộc hệ user.
