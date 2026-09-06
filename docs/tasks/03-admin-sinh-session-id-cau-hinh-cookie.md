# Tài liệu triển khai Task — Sinh session ID & cấu hình cookie (Phần 3)

## Thông tin task

- **Số thứ tự**: 03
- **Hệ**: admin
- **Nhiệm vụ**: Sinh session ID & cấu hình cookie (triển khai Phần 3 trong `PLAN_ADMIN_LOGIN.md`)
- **Ngày tạo**: 2026-08-22

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Hệ admin đã có bảng tài khoản & hash mật khẩu (Task 01) và bảng `admin_session` (Task 02). Để luồng đăng nhập (Phần 4) hoạt động, cần có cơ chế sinh session ID an toàn và đặt chuỗi gốc vào cookie với cấu hình bảo mật chuẩn.
- **Mục tiêu**: Xây dựng (1) bộ sinh session ID bằng nguồn ngẫu nhiên an toàn phía server (≥ 128-bit) và (2) bộ tạo cookie phiên với **HttpOnly + Secure + SameSite**; cờ `Secure` bật/tắt theo môi trường (tắt khi dev HTTP, bật khi production HTTPS).
- **Người dùng / người hưởng lợi**: Quản trị viên; đây là thành phần nền tảng cho Phần 4 (login), Phần 5 (middleware), Phần 6 (logout).

## 2. Phạm vi

### In scope (việc thuộc task)
- Tạo `SessionIdGenerator` (admin): sinh session ID ngẫu nhiên an toàn ≥ 128-bit bằng `SecureRandom`.
- Tạo `SessionCookieService` (admin): tạo `ResponseCookie` phiên admin với HttpOnly + Secure (theo cấu hình) + SameSite.
- Thêm property cấu hình cờ `Secure` của cookie vào `application.properties` (mặc định `false` cho dev HTTP; bật `true` ở production HTTPS).

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- Không tạo/lưu session vào DB (đã xong ở Task 02), không hash session ID khi lưu (đã quyết định ở Task 02 — chỉ lưu hash, việc hash chuỗi gốc khi ghi DB thuộc Phần 4).
- Không làm luồng login gán cookie cho response thực tế (Phần 4).
- Không làm middleware đọc cookie / xác thực mỗi request (Phần 5).
- Không làm logout xoá cookie (Phần 6).
- Không làm cơ chế remember-me / MaxAge bền vững (nếu có sẽ xử lý ở Phần 4 cùng luồng login).
- Không dùng chung cookie/session với hệ user.

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | `SessionIdGenerator.generate()` trả về chuỗi session ID ngẫu nhiên an toàn từ `SecureRandom`, độ dài entropy ≥ 128-bit (dùng 32 bytes = 256-bit, mã hoá Base64 URL-safe không padding). | Nguồn ngẫu nhiên an toàn phía server, không dùng UUID thường. |
| FR-02 | `SessionCookieService.createSessionCookie(String sessionId)` trả về `ResponseCookie` có thuộc tính **HttpOnly = true** luôn. | |
| FR-03 | Cookie có thuộc tính **Secure** lấy từ cấu hình property (dev: `false`, production: `true`). | Đúng yêu cầu "Secure tắt lúc dev HTTP, bật ở production HTTPS". |
| FR-04 | Cookie có thuộc tính **SameSite = Lax**. | Lớp 1 chống CSRF (lớp 2 là token, thuộc Phần 7). |
| FR-05 | Cookie có tên cố định `ADMIN_SESSION` và path `/admin` (chỉ áp dụng trong vùng admin). | |
| FR-06 | `application.properties` có property `admin.session.cookie.secure` (mặc định `false`). | |

## 4. Yêu cầu phi chức năng

- **Hiệu năng**: sinh session ID bằng `SecureRandom` (một lần gọi) — không ảnh hưởng đáng kể tới thời gian xử lý login.
- **Bảo mật / quyền hạn**: session ID entropy ≥ 128-bit chống đoán/brute-force; cookie HttpOnly chống đọc qua JS (XSS); Secure chống gửi qua HTTP ở production; SameSite chống CSRF lớp 1; cookie giới hạn path `/admin`.
- **Khả năng mở rộng / bảo trì**: tách riêng `SessionIdGenerator` và `SessionCookieService` trong `admin.service`; cờ Secure cấu hình qua property nên dễ bật/tắt theo môi trường.
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (package admin, Javadoc cho method, không comment rác).

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring Boot Web MVC (có sẵn `org.springframework.http.ResponseCookie`), Spring Security Crypto (Task 01). Các package admin: `admin.entity`, `admin.repository`, `admin.service`, `admin.controller`, `admin.dto`.
- **Luồng dữ liệu / luồng xử lý**: Khi login (Phần 4): `SessionIdGenerator.generate()` → chuỗi gốc đặt trong cookie qua `SessionCookieService.createSessionCookie(...)` (gửi về trình duyệt) → hash chuỗi gốc lưu vào `admin_session.session_hash` (việc hash khi ghi DB thuộc Phần 4). Khi middleware (Phần 5): đọc cookie `ADMIN_SESSION` → hash → tra `AdminSessionRepository.findBySessionHash`.
- **Các quyết định thiết kế**:
  - **256-bit (32 bytes) Base64 URL-safe** thay vì tối thiểu 128-bit: dư độ an toàn, định dạng an toàn khi đặt trong cookie.
  - **`ResponseCookie` của Spring** để tận dụng sẵn các thuộc tính HttpOnly/Secure/SameSite, không tự build header tay.
  - **Cờ Secure qua property `admin.session.cookie.secure`**: mặc định `false` (dev HTTP trên localhost); production set `true`.
  - **SameSite = Lax**: cân bằng bảo mật CSRF và trải nghiệm; Phần 7 bổ sung lớp token.
  - **Tên cookie `ADMIN_SESSION`, path `/admin`**: riêng cho hệ admin, không lẫn với cookie hệ user.
  - **Không làm remember-me / MaxAge** ở task này: cookie mặc định là session cookie; remember-me nếu cần sẽ bàn ở Phần 4.
- **Điểm chạm hệ thống**: `application.properties` (property mới); các service mới trong package `admin.service`; chưa chạm DB.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Tạo `SessionIdGenerator` trong `com.restaurant.ilikepho.admin.service` (method `generate`).
- **S-02**: Tạo `SessionCookieService` trong `com.restaurant.ilikepho.admin.service` (method `createSessionCookie`, đọc property Secure).
- **S-03**: Thêm property `admin.session.cookie.secure=false` vào `application.properties`.
- **S-04**: Biên dịch (`mvn -q compile`) và chạy test kiểm chứng (sinh ID + thuộc tính cookie) để xác nhận.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|--------------------|-------|
| `src/main/java/com/restaurant/ilikepho/admin/service/SessionIdGenerator.java` | tạo | Sinh session ID an toàn ≥ 128-bit (FR-01 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/service/SessionCookieService.java` | tạo | Tạo cookie phiên admin HttpOnly+Secure+SameSite (FR-02 → FR-05 / S-02). |
| `src/main/resources/application.properties` | sửa | Thêm property `admin.session.cookie.secure=false` (FR-06 / S-03). |
| `src/test/java/com/restaurant/ilikepho/admin/service/SessionCookieServiceTest.java` | tạo | Kiểm chứng thuộc tính cookie & sinh ID (S-04). |

## 8. Rủi ro & giả định

- **Rủi ro**: nếu đặt cookie có Secure mà trình duyệt chạy HTTP (dev) thì cookie không được gửi → login lỗi. Giảm thiểu: mặc định `false` ở dev, chỉ bật `true` khi production có HTTPS.
- **Rủi ro**: `ResponseCookie` yêu cầu thuộc tính `SameSite` hỗ trợ từ Spring phiên bản mới; Spring Boot 4.1 hỗ trợ đầy đủ.
- **Giả định**: hệ admin chạy trên context path `/` với các trang admin dưới `/admin/**`; cookie path `/admin` phủ toàn bộ vùng admin.

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [x] **DoD-01**: `mvn -q compile` thành công. *(Tick lại trong Task 08: compile trong lần chạy `./mvnw test` tổng thể — 75/75 test xanh.)*
- [x] **DoD-02**: `SessionIdGenerator.generate()` trả về chuỗi ngẫu nhiên an toàn, entropy ≥ 128-bit (32 bytes Base64 URL-safe), hai lần gọi cho kết quả khác nhau. *(SecureRandom 32 byte → chuỗi 43 ký tự (~256-bit entropy); kiểm chứng thực tế Task 08: mỗi lần sinh (cookie phiên, token login-CSRF) đều cho giá trị khác nhau.)*
- [x] **DoD-03**: `SessionCookieService.createSessionCookie(id)` trả về cookie tên `ADMIN_SESSION`, path `/admin`, **HttpOnly = true**. *(SessionCookieServiceTest; live Task 08: header `Set-Cookie: ADMIN_SESSION=…; Path=/admin; HttpOnly; SameSite=Lax`.)*
- [x] **DoD-04**: Cookie có **SameSite = Lax**. *(Cùng bằng chứng với DoD-03.)*
- [x] **DoD-05**: Thuộc tính **Secure** của cookie lấy từ property `admin.session.cookie.secure` (test với `false` → Secure tắt; `true` → Secure bật). *(SessionCookieServiceTest với cả 2 giá trị; `AdminProdProfileCookieTest` xác nhận bật Secure khi chạy profile prod.)*
- [x] **DoD-06**: `application.properties` có property `admin.session.cookie.secure`.
- [x] **DoD-07**: Các thành phần mới nằm trong package `admin.service`, không lẫn vào vùng user/shared.

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Gán cookie vào response thực tế & lưu hash session vào DB (Phần 4).
- Middleware xác thực mỗi request & sliding expiration (Phần 5).
- Logout xoá cookie (Phần 6).
- CSRF token (lớp 2 — Phần 7), bảo mật production (Phần 8).
- Cơ chế remember-me / cookie bền vững.
- Cookie/session cho hệ user.
