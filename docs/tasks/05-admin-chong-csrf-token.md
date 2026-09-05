# Tài liệu triển khai Task — Chống CSRF bằng Synchronizer Token (Phần 7)

## Thông tin task

- **Số thứ tự**: 05
- **Hệ**: admin
- **Nhiệm vụ**: Chống CSRF bằng Synchronizer Token (triển khai Phần 7 trong `PLAN_ADMIN_LOGIN.md`)
- **Ngày tạo**: 2026-09-05

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Hệ admin đã có luồng login/logout/session hoàn chỉnh (Task 04). Hiện chỉ mới có **lớp 1 chống CSRF** là `SameSite=Lax` trên cookie phiên (Task 03). Các POST trong khu vực admin (`/admin/logout`, và các POST nghiệp vụ sau này) **chưa có lớp token**, nên vẫn chịu rủi ro CSRF khi SameSite bị nới lỏng hoặc trình duyệt cũ.
- **Mục tiêu**: Bổ sung **lớp 2 — Synchronizer Token** cho mọi thao tác thay đổi trạng thái (POST/PUT/PATCH/DELETE) dưới `/admin/**` khi phiên hợp lệ: token được sinh cho từng phiên, nhúng vào form (hidden field), server so khớp trước khi cho qua; token vô hiệu khi phiên kết thúc (khoá/đăng xuất).
- **Người dùng / người hưởng lợi**: Quản trị viên; đây là yêu cầu bảo mật còn thiếu của `PLAN_ADMIN_LOGIN.md` Phần 7.

## 2. Phạm vi

### In scope (việc thuộc task)
- Thêm cột `csrf_token` trên bảng `admin_session` + field tương ứng trong entity `AdminSession`.
- Sinh CSRF token an toàn cho mỗi phiên mới ngay tại `AdminSessionService.createSession` (dùng `SessionIdGenerator`, 256-bit).
- Tạo `CsrfTokenInterceptor` kiểm tra token cho request thay đổi trạng thái dưới `/admin/**` khi có phiên hợp lệ.
- Đăng ký interceptor trong `AdminWebMvcConfigurer` (chạy sau xác thực phiên; bao gồm cả `/admin/logout`).
- Expose token qua request attribute để template đọc; thêm hidden input `_csrf` vào form logout trong `admin-topbar.html`.
- Unit test cho interceptor & cho việc sinh token khi tạo phiên.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- CSRF cho `/admin/login` (chưa có phiên để gắn token; SameSite=Lax + form POST chéo bị chặn cookie đã giảm rủi ro đăng nhập CSRF — ghi chú, không làm lần này).
- Tự sinh token kiểu cookie double-submit / JS đọc token (cookie HttpOnly không cho JS đọc).
- Cơ chế CSRF cho các form nghiệp vụ tương lai (thực đơn, đơn hàng…) — khi có form mới chỉ cần nhúng hidden `_csrf` theo mẫu form logout.
- Mọi thứ thuộc hệ user.

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Entity `AdminSession` có thêm cột `csrf_token` (nullable) lưu token chống CSRF của phiên. | Cột chỉ tồn tại trên phiên, token sống chết cùng phiên. |
| FR-02 | `AdminSessionService.createSession` sinh CSRF token ngẫu nhiên an toàn (256-bit, `SessionIdGenerator`) và lưu vào phiên mới khi tạo. | Mỗi phiên ACTIVE mới có token mới; token không tái sử dụng giữa các phiên. |
| FR-03 | `CsrfTokenInterceptor` chặn request thuộc method `POST/PUT/PATCH/DELETE` dưới `/admin/**`: nếu request có phiên ACTIVE hợp lệ → yêu cầu token khớp; thiếu token hoặc sai token → trả về 403 và không cho qua. | Trừ `/admin/login`. |
| FR-04 | `GET/HEAD/OPTIONS/TRACE` không bị kiểm tra CSRF (không thay đổi trạng thái). | |
| FR-05 | Request **không có phiên hợp lệ** (thiếu cookie, phiên không tồn tại/hết hạn/khoá) → bỏ qua kiểm tra CSRF (không có phiên để bảo vệ). | Đảm bảo `/admin/logout` vẫn hoạt động khi phiên đã chết (controller chỉ xoá cookie). |
| FR-06 | `/admin/logout` POST được bảo vệ CSRF khi có phiên hợp lệ; form logout trong `admin-topbar.html` có hidden input `_csrf`. | Token lấy từ request attribute. |
| FR-07 | `AdminAuthInterceptor` expose `csrfToken` (của phiên hiện tại) qua request attribute sau khi xác thực đạt, để template dựng hidden input. | |

## 4. Yêu cầu phi chức năng

- **Bảo mật / quyền hạn**: token entropy ≥ 128-bit (dùng 256-bit); token riêng cho từng phiên và vô hiệu khi phiên kết thúc — đúng yêu cầu Phần 7 ("token vô hiệu khi phiên kết thúc"); không log token.
- **Hiệu năng**: kiểm tra CSRF chỉ thêm 1 so sánh chuỗi trên request thay đổi trạng thái — chi phí không đáng kể.
- **Khả năng mở rộng / bảo trì**: tách `CsrfTokenInterceptor` riêng (một class một trách nhiệm) trong package `admin.interceptor`; dễ đăng ký theo path pattern.
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (package admin, Javadoc đủ cho method, không comment rác); không phá vỡ luồng login/logout hiện có.

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring MVC (HandlerInterceptor), JPA (`AdminSession` + `AdminSessionRepository`), Thymeleaf. Các package admin: `admin.entity`, `admin.service`, `admin.interceptor`, `admin.config`.
- **Luồng dữ liệu / luồng xử lý**:
  - **Tạo phiên**: `AdminSessionService.createSession(...)` → sinh CSRF token (qua `SessionIdGenerator.generate()`) → set vào `session.csrfToken` → lưu DB.
  - **Request GET hợp lệ**: `AdminAuthInterceptor.preHandle` xác thực phiên → set `request` attribute `adminSession` + `csrfToken` → template đọc `csrfToken` để nhúng hidden input.
  - **Request POST có phiên** (vd `/admin/logout`): auth cho qua (hoặc logout không chạy auth) → `CsrfTokenInterceptor.preHandle` đọc cookie → hash → tìm phiên ACTIVE → so `_csrf` (param hoặc header `X-CSRF-TOKEN`) với `session.csrfToken`; đúng → cho qua; thiếu/sai → `response.sendError(403)`, chặn.
- **Các quyết định thiết kế**:
  - **Lưu token ngay trên phiên** (`admin_session.csrf_token`) thay vì bảng riêng hay token phi trạng thái (HMAC): đơn giản, token chắc chắn vô hiệu khi phiên bị khoá/xoá — khớp đúng tinh thần Phần 7. `ddl-auto=update` tự thêm cột nullable nên không phá dữ liệu hiện có.
  - **Dùng lại `SessionIdGenerator`** (SecureRandom, 256-bit Base64 URL-safe) để sinh token — không thêm cơ chế sinh mới.
  - **Interceptor riêng** (`CsrfTokenInterceptor`) chạy **sau** `AdminAuthInterceptor`: chỉ kiểm tra khi đã có phiên hợp lệ; giữ cho class xác thực phiên không phình thêm trách nhiệm. Đăng ký cho `/admin/**` trừ `/admin/login`, **bao gồm** `/admin/logout` (logout khi có phiên phải được bảo vệ).
  - **So khớp linh hoạt param/header**: đọc `_csrf` từ request param trước, fallback header `X-CSRF-TOKEN` — thuận cho form và cho request sau này.
  - **403 khi thiếu/sai token** (đã chốt với người dùng): rõ ràng, không tạo vòng lặp redirect; form nghiệp vụ tương lai nhúng token mới mỗi lần render.
- **Điểm chạm hệ thống**: bảng `admin_session` thêm cột (JPA tự cập nhật); template `admin-topbar.html`; cấu hình interceptor.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Thêm field `csrfToken` (+ `@Column(name = "csrf_token")`) vào entity `AdminSession`.
- **S-02**: Inject `SessionIdGenerator` vào `AdminSessionService`; trong `createSession` sinh và set `csrfToken` trước khi `save`.
- **S-03**: Tạo `CsrfTokenInterceptor` trong `com.restaurant.ilikepho.admin.interceptor` (kiểm tra method, đọc cookie, tìm phiên ACTIVE, so token).
- **S-04**: Sửa `AdminAuthInterceptor` để set request attribute `csrfToken` (từ phiên) khi xác thực đạt.
- **S-05**: Đăng ký `CsrfTokenInterceptor` trong `AdminWebMvcConfigurer` cho `/admin/**` trừ `/admin/login`, sau `AdminAuthInterceptor`.
- **S-06**: Thêm hidden input `_csrf` vào form logout trong `fragment/admin-topbar.html`.
- **S-07**: Cập nhật/viết unit test: `AdminSessionServiceTest` (phiên mới có token), mới `CsrfTokenInterceptorTest` (các kịch bản 403/cho qua); `mvn -q compile` + chạy test toàn bộ.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Hệ (admin/user) | Package đích | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|------------------|--------------|--------------------|-------|
| `src/main/java/com/restaurant/ilikepho/admin/entity/AdminSession.java` | admin | `admin.entity` | sửa | Thêm cột `csrfToken` (FR-01 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminSessionService.java` | admin | `admin.service` | sửa | Sinh CSRF token khi tạo phiên (FR-02 / S-02). |
| `src/main/java/com/restaurant/ilikepho/admin/interceptor/CsrfTokenInterceptor.java` | admin | `admin.interceptor` | tạo | Kiểm tra CSRF cho request thay đổi trạng thái (FR-03, FR-04, FR-05, FR-06 / S-03). |
| `src/main/java/com/restaurant/ilikepho/admin/interceptor/AdminAuthInterceptor.java` | admin | `admin.interceptor` | sửa | Expose `csrfToken` qua request attribute (FR-07 / S-04). |
| `src/main/java/com/restaurant/ilikepho/admin/config/AdminWebMvcConfigurer.java` | admin | `admin.config` | sửa | Đăng ký `CsrfTokenInterceptor` (FR-03 / S-05). |
| `src/main/resources/templates/fragment/admin-topbar.html` | admin | templates | sửa | Thêm hidden `_csrf` vào form logout (FR-06 / S-06). |
| `src/test/java/com/restaurant/ilikepho/admin/service/AdminSessionServiceTest.java` | admin | test | sửa | Khẳng định phiên mới có CSRF token (S-07). |
| `src/test/java/com/restaurant/ilikepho/admin/interceptor/CsrfTokenInterceptorTest.java` | admin | test | tạo | Kiểm chứng các kịch bản CSRF (S-07). |

## 8. Rủi ro & giả định

- **Rủi ro**: request POST hợp lệ bị chặn nhầm nếu token trên trang cũ không khớp phiên mới (vd phiên bị khoá rồi tạo lại qua remember-me — sẽ có ở Task 06). Giảm thiểu: interceptor chỉ kiểm tra khi phiên ACTIVE hiện tại; trang form phải render token của đúng phiên đang dùng; khi phiên đổi, người dùng vào lại trang để lấy token mới.
- **Rủi ro**: thứ tự interceptor sai khiến logout bị chặn oan hoặc bỏ sót kiểm tra. Giảm thiểu: đăng ký auth → csrf; logout nằm trong pattern csrf nhưng ngoài pattern auth (thiết kế đã nêu), kiểm chứng bằng test.
- **Giả định**: `ddl-auto=update` thêm được cột `csrf_token` nullable; toàn bộ POST hiện có trong `/admin/**` chỉ là `/admin/logout` (form đã biết) — chưa có POST nghiệp vụ khác bị ảnh hưởng.

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [ ] **DoD-01**: `mvn -q compile` thành công và toàn bộ unit test pass.
- [ ] **DoD-02**: `AdminSessionService.createSession` tạo phiên mới có `csrfToken` khác rỗng, khác nhau giữa các lần tạo (test xác nhận).
- [ ] **DoD-03**: POST tới đường dẫn admin có phiên hợp lệ nhưng **thiếu `_csrf`** → 403, không cho qua (test pass).
- [ ] **DoD-04**: POST có phiên hợp lệ và `_csrf` **đúng** → cho qua (test pass).
- [ ] **DoD-05**: POST có phiên hợp lệ và `_csrf` **sai** → 403, không cho qua (test pass).
- [ ] **DoD-06**: Request **GET** không bị chặn CSRF (test pass).
- [ ] **DoD-07**: Request POST **không có phiên hợp lệ** (thiếu cookie) → bỏ qua kiểm tra CSRF (test pass).
- [ ] **DoD-08**: Form logout trong topbar có hidden input `_csrf` lấy từ request attribute; logout khi có phiên dùng đúng token sẽ thành công (kiểm tra khi chạy UI).
- [ ] **DoD-09**: Các thành phần mới nằm trong package `...admin...`, không lẫn vào vùng user/shared.

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- CSRF cho `/admin/login`.
- CSRF qua cookie double-submit / token phi trạng thái.
- Nhúng `_csrf` cho các form nghiệp vụ tương lai (làm khi form đó được tạo, theo mẫu form logout).
- Remember-me (Task 06) và mọi tính năng hệ user.
