# Tài liệu triển khai Task — Remember-me (Ghi nhớ đăng nhập) thật

## Thông tin task

- **Số thứ tự**: 06
- **Hệ**: admin
- **Nhiệm vụ**: Triển khai remember-me thật cho ô "Ghi nhớ đăng nhập" (cookie bền vững + token lưu DB, tự nối lại phiên)
- **Ngày tạo**: 2026-09-05

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Form đăng nhập admin hiện có ô **"Ghi nhớ đăng nhập"** (`login.html`, field `rememberMe` trong `UserLoginRequest`) nhưng **chưa có logic backend** — tick vào cũng không làm gì (tính năng giả). Chính sách phiên hiện tại là session cookie (hết hạn khi đóng trình duyệt) + `admin.session.timeout-minutes` sliding expiration (Task 04).
- **Mục tiêu**: Làm remember-me **thật**: khi người dùng tick "Ghi nhớ đăng nhập", phát cookie bền vững `ADMIN_REMEMBER` (token ngẫu nhiên, chỉ lưu hash trong DB) có MaxAge dài; khi session cookie hết hạn/bị mất mà remember cookie còn hợp lệ → tự tạo phiên mới (đăng nhập lại ngầm) để người dùng không phải nhập lại mật khẩu trong thời gian cho phép.
- **Người dùng / người hưởng lợi**: Quản trị viên dùng máy cá nhân/quán, muốn đăng nhập một lần không phải gõ lại mật khẩu nhiều ngày; vẫn giữ đủ bảo mật (token hash, hạn dùng, 1 token sống).

## 2. Phạm vi

### In scope (việc thuộc task)
- Bảng `admin_remember_me` (entity + repository) trong package admin: `admin_id` (khoá ngoại Long), `token_hash` (SHA-256, unique), `created_at`, `expires_at`.
- `AdminRememberMeService`: tạo token (xoá token cũ của admin → giữ 1 token sống), tra token hợp lệ, rotate token, xoá token theo admin.
- `SessionCookieService`: thêm tạo cookie `ADMIN_REMEMBER` (MaxAge theo cấu hình) và cookie hết hạn để xoá.
- Nối `rememberMe` vào luồng login/logout (`AdminAuthService`, `AuthenticationController`).
- `RememberMeInterceptor` (đăng ký trước `AdminAuthInterceptor`): khi chưa có phiên hợp lệ nhưng remember token hợp lệ → tự tạo phiên + set cookie `ADMIN_SESSION` + rotate remember token.
- Property `admin.remember-me.max-age-days`.
- Unit test cho service/cookie/interceptor.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- CSRF token — đã xử lý riêng ở Task 05 (form logout phải có `_csrf` sau Task 05).
- Xoá định kỳ (cleanup) các dòng remember token hết hạn khỏi DB (bỏ qua lần này; xoá logic khi logout/rotate là đủ cho phạm vi hiện tại).
- Remember-me cho hệ user.
- Thay đổi giao diện form đăng nhập (chỉ giữ nguyên ô checkbox hiện có).

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Entity `AdminRememberMe` ánh xạ bảng `admin_remember_me`: `id`, `adminId` (khoá ngoại Long, không quan hệ JPA), `tokenHash` (unique, không rỗng), `createdAt`, `expiresAt`. | Chỉ lưu **hash SHA-256** của token, không lưu chuỗi gốc. |
| FR-02 | `SessionCookieService.createRememberMeCookie(String token)` trả về cookie `ADMIN_REMEMBER` path `/admin`, HttpOnly, SameSite=Lax, Secure theo property, MaxAge = `admin.remember-me.max-age-days` (mặc định 30). | |
| FR-03 | Đăng nhập với `rememberMe=true` → tạo remember token mới (SHA-256 lưu DB, hết hạn sau N ngày) + set cookie `ADMIN_REMEMBER` bên cạnh cookie `ADMIN_SESSION`. | Xoá token cũ của admin đó trước khi tạo (1 admin = 1 remember token sống). |
| FR-04 | Đăng nhập với `rememberMe=false` → hành vi như cũ (chỉ set `ADMIN_SESSION`), không tạo/giữ remember token (xoá nếu có). | |
| FR-05 | `RememberMeInterceptor` (chạy **trước** `AdminAuthInterceptor`, trừ `/admin/logout`): nếu request **đã có phiên hợp lệ** → bỏ qua. Nếu **chưa có phiên** nhưng có cookie `ADMIN_REMEMBER` hợp lệ & chưa hết hạn → tạo phiên mới qua `createSession`, set cookie `ADMIN_SESSION` mới, **rotate** remember token (xoá token cũ, tạo token mới, set cookie mới). | |
| FR-06 | Nếu remember token không tồn tại / hết hạn → không tạo phiên, để `AdminAuthInterceptor` xử lý redirect login như hiện tại. | |
| FR-07 | POST `/admin/logout`: khoá phiên hiện tại + **xoá toàn bộ remember token của admin** + gửi cookie hết hạn cho cả `ADMIN_SESSION` lẫn `ADMIN_REMEMBER` → redirect login. | |
| FR-08 | Property `admin.remember-me.max-age-days=30` trong `application.properties`. | |

## 4. Yêu cầu phi chức năng

- **Bảo mật / quyền hạn**: DB chỉ chứa hash SHA-256 của token (không lưu chuỗi gốc — nếu DB lộ không tái dùng được); token entropy 256-bit; cookie HttpOnly + SameSite=Lax + Secure (theo môi trường, dùng chung cờ với cookie phiên); **rotate** token mỗi lần dùng để vô hiệu token cũ nếu bị đánh cắp; xoá token khi logout.
- **Hiệu năng**: mỗi lần "nối lại phiên" chỉ thêm 1 lần đọc + 1 lần tạo token; không ảnh hưởng request đã có phiên.
- **Khả năng mở rộng / bảo trì**: tách `AdminRememberMeService` + `RememberMeInterceptor` (một class một trách nhiệm) trong package admin; tái sử dụng `AdminSessionService` (hash + tạo phiên) để giữ logic session tập trung.
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (package admin, khoá ngoại Long, Javadoc đủ cho method, không comment rác); không phá vỡ luồng login/logout/session hiện có.

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring MVC (HandlerInterceptor, ResponseCookie), Spring Data JPA, Thymeleaf. Package admin: `admin.entity`, `admin.repository`, `admin.service`, `admin.controller`, `admin.interceptor`, `admin.config`.
- **Luồng dữ liệu / luồng xử lý**:
  - **Login có nhớ**: `AdminAuthService.login(username, password, rememberMe)` → xác thực → tạo session (như cũ) → nếu `rememberMe` → tạo remember token + hash lưu `admin_remember_me` → trả về cả session id lẫn remember token → controller set 2 cookie (`ADMIN_SESSION`, `ADMIN_REMEMBER`).
  - **Request chưa có phiên, có remember cookie hợp lệ**: `RememberMeInterceptor.preHandle` → hash token từ cookie → tra DB (còn hạn) → `AdminSessionService.createSession(adminId, rawSessionIdMoi)` → set cookie `ADMIN_SESSION` → rotate remember token (xoá cũ + tạo mới + set cookie mới) → `return true` → `AdminAuthInterceptor` (chạy sau) thấy phiên hợp lệ, cho qua.
  - **Logout**: controller gọi `AdminAuthService.logout` (khoá phiên) + xoá remember token theo `adminId` (lấy từ phiên vừa khoá) → gửi cookie hết hạn cho cả 2 cookie.
- **Các quyết định thiết kế**:
  - **Bảng riêng `admin_remember_me`** (không nhét thêm trường vào `admin_session`): tách biệt vòng đời (session ngắn, remember dài ngày); `admin_id` khoá ngoại Long theo đúng admin-build; chỉ lưu hash SHA-256.
  - **1 admin = 1 remember token sống**: đối xứng chính sách 1 phiên hoạt động; đơn giản hoá việc vô hiệu hoá toàn bộ khi logout.
  - **Rotate token mỗi lần nối lại phiên**: nếu token bị lộ từ cookie cũ, chỉ dùng được một lần; chi phí 1 cặp xoá/ghi DB mỗi lần nối phiên là chấp nhận được (hiếm khi xảy ra).
  - **Dùng lại `AdminSessionService.hashSessionId` cho việc hash token**: cùng SHA-256 hex, giữ một chỗ băm; `AdminRememberMeService` phụ thuộc `AdminSessionService` để băm + tạo phiên (đã phụ thuộc sẵn).
  - **Cookie `ADMIN_REMEMBER` giống cấu hình cookie phiên** (path `/admin`, HttpOnly, SameSite Lax, Secure theo `admin.session.cookie.secure`) chỉ khác MaxAge dài.
  - **Không làm cleanup hết hạn** định kỳ ở task này — dòng hết hạn chỉ bị bỏ qua khi tra cứu; sẽ dọn khi có nhu cầu.
- **Điểm chạm hệ thống**: bảng `admin_remember_me` (JPA `ddl-auto=update`); `application.properties` (property mới); cookie mới trên response login/logout; interceptor mới trong chuỗi.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Tạo entity `AdminRememberMe` + enum/constant (không có enum mới) trong `admin.entity`; tạo `AdminRememberMeRepository` (`findByTokenHash`, `@Modifying deleteByAdminId`, `delete`).
- **S-02**: Tạo `AdminRememberMeService`: `createToken(Long adminId)` (xoá cũ → sinh raw token → hash → lưu → trả raw), `findValidByTokenHash(String rawToken)` (hash → tra → kiểm tra hết hạn), `rotate(Long adminId, String oldRawToken)`, `deleteAllByAdminId(Long adminId)`.
- **S-03**: Sửa `SessionCookieService`: thêm `createRememberMeCookie(String token, long maxAgeDays)` và `createExpiredRememberMeCookie()`.
- **S-04**: Sửa `AdminAuthService`: `login` nhận thêm `boolean rememberMe`, tạo remember token khi cần, trả kết quả gồm session id + remember token (nullable); `logout` xoá remember token của admin.
- **S-05**: Sửa `AuthenticationController.handleLogin` truyền `rememberMe` và set thêm cookie `ADMIN_REMEMBER` khi có; `handleLogout` gửi cookie hết hạn cho cả 2 loại.
- **S-06**: Tạo `RememberMeInterceptor` trong `admin.interceptor` (nối lại phiên + rotate).
- **S-07**: Đăng ký `RememberMeInterceptor` trong `AdminWebMvcConfigurer` **trước** `AdminAuthInterceptor`, cho `/admin/**` trừ `/admin/login` và `/admin/logout`.
- **S-08**: Thêm property `admin.remember-me.max-age-days=30` vào `application.properties`.
- **S-09**: Cập nhật/viết unit test (service, cookie, interceptor, auth service, controller nếu cần); `mvn -q compile` + chạy test toàn bộ.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Hệ (admin/user) | Package đích | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|------------------|--------------|--------------------|-------|
| `src/main/java/com/restaurant/ilikepho/admin/entity/AdminRememberMe.java` | admin | `admin.entity` | tạo | Entity bảng `admin_remember_me` (FR-01 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/repository/AdminRememberMeRepository.java` | admin | `admin.repository` | tạo | Truy vấn token remember (FR-01 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminRememberMeService.java` | admin | `admin.service` | tạo | Tạo/tra/rotate/xoá remember token (FR-03, FR-05, FR-06, FR-07 / S-02). |
| `src/main/java/com/restaurant/ilikepho/admin/service/SessionCookieService.java` | admin | `admin.service` | sửa | Thêm method cookie `ADMIN_REMEMBER` (FR-02 / S-03). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminAuthService.java` | admin | `admin.service` | sửa | Login nhận `rememberMe`, tạo token; logout xoá token (FR-03, FR-04, FR-07 / S-04). |
| `src/main/java/com/restaurant/ilikepho/admin/controller/AuthenticationController.java` | admin | `admin.controller` | sửa | Set/xoá cookie remember khi login/logout (FR-03, FR-07 / S-05). |
| `src/main/java/com/restaurant/ilikepho/admin/interceptor/RememberMeInterceptor.java` | admin | `admin.interceptor` | tạo | Nối lại phiên bằng remember token (FR-05, FR-06 / S-06). |
| `src/main/java/com/restaurant/ilikepho/admin/config/AdminWebMvcConfigurer.java` | admin | `admin.config` | sửa | Đăng ký `RememberMeInterceptor` trước auth (S-07). |
| `src/main/resources/application.properties` | admin | resources | sửa | Thêm `admin.remember-me.max-age-days=30` (FR-08 / S-08). |
| Các test service/cookie/interceptor | admin | test | sửa/tạo | Kiểm chứng remember-me (S-09). |

## 8. Rủi ro & giả định

- **Rủi ro**: tạo session qua remember-me vi phạm chính sách "1 phiên hoạt động" nếu thiếu lock — giảm thiểu: đi qua đúng `AdminSessionService.createSession` (đã có pessimistic lock + khoá phiên cũ ở Task 04).
- **Rủi ro**: cookie `ADMIN_REMEMBER` bị gửi sang site khác → SameSite=Lax chặn POST chéo mang cookie; token có rotate + hạn dùng nên thiệt hại nếu lộ là giới hạn.
- **Rủi ro**: trang cũ giữ CSRF token của phiên đã chết (khi remember tạo phiên mới) → POST từ trang đó trả 403 (Task 05). Giảm thiểu: `RememberMeInterceptor` loại trừ `/admin/logout`; khi gặp 403 người dùng vào lại trang để lấy token mới — chấp nhận, chưa có POST nghiệp vụ khác ở giai đoạn này.
- **Giả định**: form login đã gửi đúng field `rememberMe`; DB `ddl-auto=update` tự tạo bảng `admin_remember_me`.

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [ ] **DoD-01**: `mvn -q compile` thành công và toàn bộ unit test pass.
- [ ] **DoD-02**: `createRememberMeCookie` trả cookie tên `ADMIN_REMEMBER`, path `/admin`, HttpOnly, SameSite=Lax, MaxAge theo cấu hình (test pass).
- [ ] **DoD-03**: Login với `rememberMe=true` → tạo 1 dòng `admin_remember_me` (chỉ lưu hash), xoá token cũ của admin, trả remember token để set cookie (test `AdminRememberMeService`/`AdminAuthService`).
- [ ] **DoD-04**: Login với `rememberMe=false` → không tạo remember token; xoá token cũ nếu có (test pass).
- [ ] **DoD-05**: Request chưa có phiên + remember token hợp lệ → `RememberMeInterceptor` tạo phiên ACTIVE mới và set cookie `ADMIN_SESSION`; phiên cũ của admin bị khoá (test pass).
- [ ] **DoD-06**: Remember token được rotate sau mỗi lần nối lại phiên: token cũ vô hiệu, token mới còn hạn (test pass).
- [ ] **DoD-07**: Remember token không tồn tại hoặc hết hạn → không tạo phiên, không ném lỗi (test pass).
- [ ] **DoD-08**: Request **đã có phiên hợp lệ** → remember-me không làm gì thêm (test pass).
- [ ] **DoD-09**: Logout → khoá phiên + xoá toàn bộ remember token của admin + cookie `ADMIN_REMEMBER` hết hạn (test pass).
- [ ] **DoD-10**: Các thành phần mới nằm trong package `...admin...`, không lẫn vào vùng user/shared.

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Cleanup định kỳ dòng remember token hết hạn.
- Remember-me cho hệ user.
- Giao diện/tuỳ chọn thời hạn nhớ trên UI.
- Thay đổi chính sách phiên hiện có ngoài việc bổ sung nối lại phiên.
