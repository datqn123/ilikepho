# Tài liệu triển khai Task — Bảo mật production (Phần 8)

## Thông tin task

- **Số thứ tự**: 07
- **Hệ**: admin
- **Nhiệm vụ**: Bảo mật production (triển khai Phần 8 trong `PLAN_ADMIN_LOGIN.md`) — bật cookie Secure + cấu hình chạy sau reverse proxy HTTPS
- **Ngày tạo**: 2026-09-05

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Cookie phiên `ADMIN_SESSION` (Task 03) đã có cờ `Secure` đọc từ property `admin.session.cookie.secure`, nhưng đang **mặc định `false`** và chưa có cấu hình production riêng nào bật nó. Khi app chạy HTTPS ở production mà cookie không có `Secure`, cookie có thể bị gửi qua HTTP — rò rỉ phiên. Ứng dụng cũng chưa được cấu hình để chạy đúng phía sau reverse proxy HTTPS (redirect/schema sai).
- **Mục tiêu**: Bổ sung cấu hình production (profile `prod`) để: cookie phiên **và** cookie remember-me bật `Secure`; app nhận diện đúng scheme/port từ proxy (forward headers) để redirect giữ nguyên HTTPS; kèm ghi chú triển khai ngắn (chạy HTTPS, đặt `SPRING_PROFILES_ACTIVE=prod`).
- **Người dùng / người hưởng lợi**: Quản trị viên khi hệ thống đưa lên production qua HTTPS.

## 2. Phạm vi

### In scope (việc thuộc task)
- Tạo `application-prod.properties` bật `admin.session.cookie.secure=true`.
- Thêm `server.forward-headers-strategy=framework` trong profile prod để app đọc đúng scheme từ proxy (nginx) — redirect giữ HTTPS.
- Tài liệu ngắn (trong chính task doc + cập nhật ghi chú `PLAN_ADMIN_LOGIN.md` Phần 8) về cách chạy production: HTTPS qua reverse proxy, bật profile `prod`, không chạy dev seed.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- Cài đặt/khởi tạo hạ tầng HTTPS thật (chứng chỉ, nginx...) — việc vận hành, ngoài mã nguồn.
- Migration DB / thay đổi schema.
- Các cấu hình production khác không liên quan bảo mật cookie/session (vd log, metrics).
- Mọi tính năng hệ user.

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Tồn tại `application-prod.properties` bật `admin.session.cookie.secure=true`. | Cookie `ADMIN_SESSION` và `ADMIN_REMEMBER` (Task 06) đều dùng chung cờ này qua `SessionCookieService` → cả hai thành `Secure` ở prod. |
| FR-02 | Profile prod có `server.forward-headers-strategy=framework` để đọc scheme/port từ header proxy. | Giúp redirect (`redirect:/admin/login`...) và các URL dựng theo request giữ đúng HTTPS sau proxy. |
| FR-03 | Môi trường dev (không bật profile `prod`) giữ nguyên hành vi cũ: `admin.session.cookie.secure=false`. | Không phá luồng dev HTTP. |
| FR-04 | Ghi chú triển khai production trong tài liệu: chạy HTTPS, `SPRING_PROFILES_ACTIVE=prod`, không bật seed admin (Task 08). | |

## 4. Yêu cầu phi chức năng

- **Bảo mật**: khi production chạy HTTPS, cookie phiên/remember đều có `Secure` → trình duyệt không gửi cookie qua kênh HTTP; giảm rò rỉ session/token.
- **Khả năng mở rộng / bảo trì**: tách cấu hình theo Spring profile (`prod`) — dev giữ nguyên mặc định, dễ bật/tắt, không đụng code.
- **Trải nghiệm / quy ước**: không đổi hành vi dev; tuân theo quy tắc dự án (không hardcode trong code, cấu hình qua property/profile).

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring Boot profile (`application-{profile}.properties`), cookie qua `SessionCookieService` (đọc `admin.session.cookie.secure`), redirect Spring MVC.
- **Luồng xử lý**: Khi chạy với `SPRING_PROFILES_ACTIVE=prod`, Spring nạp chồng `application-prod.properties` lên `application.properties`: `admin.session.cookie.secure` thành `true` → `SessionCookieService` (đã inject giá trị) dựng cookie có `Secure`. `server.forward-headers-strategy=framework` → container tin header `X-Forwarded-Proto/Host/Port` từ proxy khi dựng URL/redirect.
- **Các quyết định thiết kế**:
  - **Profile `prod` riêng** thay vì sửa mặc định: giữ dev chạy HTTP không Secure (đúng yêu cầu plan: "Secure tắt lúc dev HTTP, bật ở production HTTPS").
  - **`server.forward-headers-strategy=framework`**: cần thiết khi đặt sau reverse proxy — nếu thiếu, mặc dù cookie Secure vẫn đúng, các redirect do MVC sinh có thể rơi về `http`. Cấu hình ở prod là đủ (dev không đứng sau proxy).
  - **Không đưa mật khẩu DB vào profile prod**: giữ đọc từ biến môi trường `db_password` như hiện tại (không commit secret).
- **Điểm chạm hệ thống**: file cấu hình mới `src/main/resources/application-prod.properties`; không đụng DB, không đụng code Java.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Tạo `src/main/resources/application-prod.properties` với `admin.session.cookie.secure=true` và `server.forward-headers-strategy=framework`.
- **S-02**: Xác minh dev không bật profile prod vẫn dùng `false` (không đổi `application.properties`).
- **S-03**: (Closure sẽ làm ở Task 08) cập nhật ghi chú triển khai + `mvn -q compile`/chạy test đảm bảo không phá gì.
- **S-04**: Ghi chú triển khai production vào cuối `PLAN_ADMIN_LOGIN.md` (hoặc mục Phần 8) — thực hiện khi closure.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Hệ (admin/user) | Package đích | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|------------------|--------------|--------------------|-------|
| `src/main/resources/application-prod.properties` | admin | resources | tạo | Bật cookie Secure + forward headers cho production (FR-01, FR-02 / S-01). |
| `PLAN_ADMIN_LOGIN.md` | — | docs | sửa | Ghi nhận trạng thái triển khai Phần 8 (FR-04 / S-04). |

## 8. Rủi ro & giả định

- **Rủi ro**: bật `Secure` mà trình duyệt chạy HTTP (cấu hình sai) → cookie không gửi, login hỏng. Giảm thiểu: chỉ bật trong profile `prod`; yêu cầu production chạy HTTPS.
- **Rủi ro**: tin header proxy không đúng nếu proxy không set/ghi đè `X-Forwarded-*`. Giảm thiểu: ghi chú rõ proxy phải set header đúng; chỉ dùng `forward-headers-strategy` ở prod sau proxy.
- **Giả định**: production chạy HTTPS qua reverse proxy (nginx) và DB dùng biến môi trường `db_password`; không có gì khác phụ thuộc cấu hình mặc định bị thay đổi.

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [ ] **DoD-01**: Tồn tại `application-prod.properties` với `admin.session.cookie.secure=true` và `server.forward-headers-strategy=framework`.
- [ ] **DoD-02**: `application.properties` (dev) vẫn giữ `admin.session.cookie.secure=false` — không đổi hành vi dev.
- [ ] **DoD-03**: Khởi động với `SPRING_PROFILES_ACTIVE=prod` → cookie `ADMIN_SESSION` (và `ADMIN_REMEMBER` nếu có) dựng với `Secure; SameSite=Lax` (kiểm tra log/header khi chạy thử production) — xác nhận bằng chạy thử hoặc test dùng profile prod.
- [ ] **DoD-04**: `mvn -q compile` và toàn bộ unit test không bị ảnh hưởng (pass).
- [ ] **DoD-05**: Ghi chú triển khai production (HTTPS + profile `prod` + không seed admin) được bổ sung vào tài liệu.

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Vận hành HTTPS thật (chứng chỉ, reverse proxy, CDN).
- Migration DB, CI/CD, container build.
- Các cấu hình production ngoài phạm vi bảo mật cookie/session.
