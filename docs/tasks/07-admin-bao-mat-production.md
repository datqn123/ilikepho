# Tài liệu triển khai Task — Bảo mật production (Phần 8) & hợp nhất mục sau code review

## Thông tin task

- **Số thứ tự**: 07
- **Hệ**: admin
- **Nhiệm vụ**: Bảo mật production (triển khai Phần 8 trong `PLAN_ADMIN_LOGIN.md`) — bật cookie Secure + cấu hình chạy sau reverse proxy HTTPS; kèm **hợp nhất các mục phát sinh sau code review Task 05/06** (login-CSRF, nguyên tử hoá login/logout, dọn token hết hạn, dọn cookie remember cũ, test chuỗi interceptor).
- **Ngày tạo**: 2026-09-05
- **Ngày cập nhật**: 2026-09-06 — hợp nhất các mục còn lại sau review Task 05 (CSRF) và Task 06 (remember-me) vào task này theo quyết định của người chủ dự án; **cùng ngày: hoàn thành triển khai** (coding + test, 71/71 pass). **Code review Task 07: Ready to merge — 0 Critical, 0 Important, 5 Minor** (đã sửa chính xác hoá mô tả hiệu năng lock tại mục 4 và 8; các Minor còn lại đã chuyển vào Task 08 — mục 11).

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Cookie phiên `ADMIN_SESSION` (Task 03) đã có cờ `Secure` đọc từ property `admin.session.cookie.secure`, nhưng đang **mặc định `false`** và chưa có cấu hình production riêng nào bật nó. Khi app chạy HTTPS ở production mà cookie không có `Secure`, cookie có thể bị gửi qua HTTP — rò rỉ phiên. Ứng dụng cũng chưa được cấu hình để chạy đúng phía sau reverse proxy HTTPS (redirect/schema sai).
- **Bổ sung 2026-09-06**: Code review Task 05/06 kết luận "ready to merge" nhưng để lại một số điểm cần xử lý trước khi lên production: (1) `AdminAuthService.login/logout` gồm nhiều transaction rời rạc và tạo remember token chưa được tuần tự hoá (race có thể tạm giữ 2 token của cùng admin); (2) POST `/admin/login` chưa được bảo vệ login-CSRF; (3) login không nhớ không dọn cookie `ADMIN_REMEMBER` cũ trên trình duyệt; (4) dòng remember token hết hạn tích tụ trong DB; (5) DoD-11 Task 06 được khâu từ các test rời rạc, chưa có test chạy cả chuỗi 3 interceptor. Toàn bộ được hợp nhất vào task này.
- **Mục tiêu**: Bổ sung cấu hình production (profile `prod`) để: cookie phiên **và** cookie remember-me bật `Secure`; app nhận diện đúng scheme/port từ proxy (forward headers) để redirect giữ nguyên HTTPS; kèm ghi chú triển khai ngắn (chạy HTTPS, đặt `SPRING_PROFILES_ACTIVE=prod`). Đồng thời vá các điểm còn bỏ ngỏ kể trên để hệ admin "chốt" đầy đủ trước production.
- **Người dùng / người hưởng lợi**: Quản trị viên khi hệ thống đưa lên production qua HTTPS; người duy trì hệ thống khi các luồng login/logout/session phải chặt chẽ cả về tính đúng đắn lẫn test.

## 2. Phạm vi

### In scope (việc thuộc task)
- Tạo `application-prod.properties` bật `admin.session.cookie.secure=true`.
- Ghi đè trong profile prod: `spring.jpa.show-sql=false`, `spring.thymeleaf.cache=true` (tắt log SQL có thể lộ dữ liệu và bật cache template — không kế thừa cấu hình dev).
- Thêm `server.forward-headers-strategy=framework` trong profile prod để app đọc đúng scheme từ proxy (nginx) — redirect giữ HTTPS.
- Tài liệu ngắn (trong chính task doc + cập nhật ghi chú `PLAN_ADMIN_LOGIN.md` Phần 8) về cách chạy production: HTTPS qua reverse proxy, bật profile `prod`, không chạy dev seed.
- Bọc `@Transactional` cho `AdminAuthService.login`/`logout`: nguyên tử hoá luồng và tuần tự hoá tạo remember token nhờ pessimistic lock sẵn có trên dòng admin (chấm dứt race 2 token — ghi nhận sau review Task 06).
- Bảo vệ login-CSRF cho POST `/admin/login` bằng double-submit cookie ngắn hạn (ghi nhận sau review Task 05).
- Login `rememberMe=false` gửi thêm cookie hết hạn `ADMIN_REMEMBER` để dọn cookie cũ trên trình duyệt (ghi nhận sau review Task 06).
- Dọn dẹp dòng `admin_remember_me` hết hạn khỏi DB bằng job định kỳ trong app (ghi nhận sau review Task 06).
- Test chuỗi đầy đủ 3 interceptor (Remember → Auth → CSRF) bằng MockMvc, khoá các kịch bản đã mô tả ở Task 05/06 (ghi nhận sau review).

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- Cài đặt/khởi tạo hạ tầng HTTPS thật (chứng chỉ, nginx...) — việc vận hành, ngoài mã nguồn.
- Migration DB ngoài nhu cầu của `admin_remember_me` (phương án `@Transactional` không đụng schema; chỉ thêm unique index nếu sau này chứng minh cần thiết).
- Các cấu hình production khác không liên quan bảo mật cookie/session (vd log, metrics).
- Mọi tính năng hệ user.
- Tính năng xoá/tắt tài khoản admin (chỉ ghi chú thiết kế để tính năng tương lai tuân theo — xem mục 8).

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Tồn tại `application-prod.properties` bật `admin.session.cookie.secure=true`. | Cookie `ADMIN_SESSION` và `ADMIN_REMEMBER` (Task 06) đều dùng chung cờ này qua `SessionCookieService` → cả hai thành `Secure` ở prod. |
| FR-02 | Profile prod có `server.forward-headers-strategy=framework` để đọc scheme/port từ header proxy. | Giúp redirect (`redirect:/admin/login`...) và các URL dựng theo request giữ đúng HTTPS sau proxy. |
| FR-03 | Môi trường dev (không bật profile `prod`) giữ nguyên hành vi cũ: `admin.session.cookie.secure=false`. | Không phá luồng dev HTTP. |
| FR-04 | Ghi chú triển khai production trong tài liệu: chạy HTTPS, `SPRING_PROFILES_ACTIVE=prod`, không bật seed admin (Task 08). | |
| FR-05 | Profile prod ghi đè `spring.jpa.show-sql=false` và `spring.thymeleaf.cache=true`. | Tránh kế thừa cấu hình dev (`show-sql=true` đưa dữ liệu vào log, cache tắt gây render chậm). |
| FR-06 | Login với `rememberMe=false` → response gửi thêm cookie `ADMIN_REMEMBER` hết hạn (maxAge 0) để xoá cookie cũ trên trình duyệt. | Token cũ đã bị xoá ở server từ Task 06 (FR-04 Task 06); việc này chỉ dọn dẹp cookie chết còn nằm lại. |
| FR-07 | `AdminAuthService.login` và `logout` chạy nguyên tử trong một transaction (`@Transactional`); tạo remember token diễn ra khi transaction còn giữ pessimistic lock trên dòng admin → hai login đồng thời của cùng admin được tuần tự hoá, không thể tạo 2 token sống. | Tái sử dụng `findWithLockingById` sẵn có của `createSession`; không thêm unique index, không biến race thành lỗi 500. Lỗi khi tạo token (sau khi tạo phiên) sẽ rollback cả phiên — không còn phiên mồ côi. |
| FR-08 | POST `/admin/login` được bảo vệ login-CSRF: GET login sinh token ngắn hạn đặt vào cookie riêng **và** hidden field trong form; POST thiếu/sai token → từ chối xác thực (quay lại trang login). | Cookie ngắn hạn (vd 30 phút), path `/admin`, HttpOnly, SameSite=Lax, Secure theo môi trường; đường login vẫn nằm ngoài `CsrfTokenInterceptor` (token này là cơ chế riêng, độc lập với CSRF token của phiên đã đăng nhập). |
| FR-09 | Dòng `admin_remember_me` đã hết hạn bị xoá khỏi DB bởi job định kỳ trong app (vd chạy lúc 3h sáng theo múi giờ `Asia/Ho_Chi_Minh`). | Token hết hạn đã bị bỏ qua khi tra cứu từ Task 06; job chỉ giới hạn tăng trưởng bảng. |
| FR-10 | Bộ test chuỗi đầy đủ (MockMvc đăng ký cả ba interceptor thật theo đúng thứ tự Remember → Auth → CSRF) khoá 3 kịch bản: (a) request chỉ có remember cookie hợp lệ → đi qua chuỗi, nhận 2 cookie mới; (b) POST mang CSRF token của phiên cũ ngay sau khi nối phiên → 403; (c) GET trang admin rồi POST logout với token vừa render → qua. | Hiện DoD-11 Task 06 được khâu từ các test rời rạc per-class; bộ test này ghim đúng hành vi đã ghi trong mục 8 Task 06. |

## 4. Yêu cầu phi chức năng

- **Bảo mật**: khi production chạy HTTPS, cookie phiên/remember đều có `Secure` → trình duyệt không gửi cookie qua kênh HTTP; giảm rò rỉ session/token. Login-CSRF chặn kẻ tấn công đăng nhập hộ người dùng vào tài khoản do attacker kiểm soát; so token login-CSRF constant-time (`MessageDigest.isEqual`) như CSRF token phiên. Không log bất kỳ token nào.
- **Xử lý thời gian**: hạn dùng cookie/token login-CSRF và mốc xoá token hết hạn tính theo `ZonedDateTime`/`Instant` với ZoneId chung `Asia/Ho_Chi_Minh` (admin-build mục 6), không so `LocalDateTime` trần.
- **Hiệu năng**: `@Transactional` trên login/logout chỉ giữ pessimistic lock từ lúc `createSession` đến commit (milli-giây): verify mật khẩu diễn ra **trước khi lấy lock** (truy vấn đầu `findByUsername` là đọc thường, không mang khoá; khoá chỉ được lấy trong `createSession`), nên khoá không bao trùm bước verify. Job dọn token là 1 query xoá theo khoảng, chạy 1 lần/ngày, không đụng request người dùng.
- **Khả năng mở rộng / bảo trì**: cấu hình production tách theo profile, không hardcode; job dọn token idempotent (xoá theo điều kiện) nên chạy nhiều instance cũng an toàn; tách service tạo/xác thực login-CSRF giữ mỗi class một trách nhiệm.
- **Trải nghiệm / quy ước**: không đổi hành vi dev; tuân theo quy tắc dự án (không hardcode trong code, cấu hình qua property/profile; controller mỏng, logic trong service; mọi method có Javadoc).

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring Boot profile (`application-{profile}.properties`), cookie qua `SessionCookieService`, redirect Spring MVC, `@Transactional` (Spring Data), `@Scheduled`, HandlerInterceptor, Thymeleaf. Toàn bộ code mới nằm trong package admin.
- **Luồng xử lý**:
  - **Profile prod**: khi chạy với `SPRING_PROFILES_ACTIVE=prod`, Spring nạp chồng `application-prod.properties` lên `application.properties`: `admin.session.cookie.secure` thành `true` → `SessionCookieService` dựng cookie có `Secure`. `server.forward-headers-strategy=framework` → container tin header `X-Forwarded-Proto/Host/Port` từ proxy khi dựng URL/redirect.
  - **Login/logout nguyên tử + chống race token**: `@Transactional` trên `AdminAuthService.login` — transaction bao `createSession` (nơi `findWithLockingById` lấy pessimistic lock dòng admin) và `createToken` (xoá cũ + chèn mới) trong cùng một transaction, khoá chỉ nhả khi commit → hai login đồng thời của cùng admin tuần tự hoá, mọi ghi cả hai-là-một hoặc không-gì-cả. `logout` tương tự: khoá phiên + xoá token theo `adminId` + xoá theo hash cookie trong một transaction.
  - **Login-CSRF (double-submit)**: GET `/admin/login` sinh token ngẫu nhiên (tái dùng `SessionIdGenerator`), đặt cookie ngắn hạn riêng (vd `ADMIN_LOGIN_CSRF`) và render hidden field trong form; POST login so token field với cookie bằng constant-time trước khi xác thực; sai/thiếu → quay lại trang login (không xác thực). Cookie được ghi đè bằng token mới ở mỗi GET login; sau login thành công không cần xoá (path/nằm ngoài vùng phiên đã đăng nhập, tự hết hạn ngắn).
  - **Dọn token hết hạn**: thêm query xoá theo mốc trên `AdminRememberMeRepository` + method `@Scheduled` trên `AdminRememberMeService`; bật scheduler qua `@EnableScheduling` trên một config trong package admin.
  - **Dọn cookie remember cũ**: trong `handleLogin`, nhánh `rememberMe=false` thêm header `Set-Cookie` với `createExpiredRememberMeCookie()` (method đã có từ Task 06).
  - **Test chuỗi**: MockMvc `standaloneSetup` với một controller giả (GET + POST), `addInterceptors(rememberMe, auth, csrf)` theo đúng thứ tự đăng ký thật; dùng DB mock qua service mock như các test interceptor hiện có.
- **Các quyết định thiết kế**:
  - **Profile `prod` riêng** thay vì sửa mặc định: giữ dev chạy HTTP không Secure (đúng yêu cầu plan: "Secure tắt lúc dev HTTP, bật ở production HTTPS").
  - **`server.forward-headers-strategy=framework`**: cần thiết khi đặt sau reverse proxy — nếu thiếu, mặc dù cookie Secure vẫn đúng, các redirect do MVC sinh có thể rơi về `http`. Cấu hình ở prod là đủ (dev không đứng sau proxy).
  - **Không đưa mật khẩu DB vào profile prod**: giữ đọc từ biến môi trường `db_password` như hiện tại (không commit secret).
  - **Ghi đè `show-sql`/`thymeleaf.cache` ở prod**: `application.properties` mặc định bật log SQL và tắt cache template cho dev; profile prod phải ghi đè để không kế thừa hai giá trị này khi chạy production.
  - **`@Transactional` thay vì unique index trên `admin_remember_me.admin_id`** (phương án được reviewer đề xuất thay thế): unique index qua `ddl-auto=update` không đáng tin và biến race thành 500; transaction + pessimistic lock sẵn có tuần tự hoá đúng chỗ cần, không đụng schema. Unique index chỉ trở lại nếu sau này có luồng tạo token ngoài đường `AdminAuthService.login`.
  - **Login-CSRF tách khỏi CSRF phiên**: hai cơ chế có vòng đời khác nhau (trước xác thực vs theo phiên), nên token login dùng cookie/hidden field riêng; đường `/admin/login` vẫn loại khỏi cả auth lẫn `CsrfTokenInterceptor` như hiện tại.
- **Điểm chạm hệ thống**: file cấu hình mới `src/main/resources/application-prod.properties`; `AdminAuthService` (transaction); `AuthenticationController` + form login (login-CSRF, cookie hết hạn); `AdminRememberMeRepository`/`AdminRememberMeService` (cleanup); config bật scheduler; không đổi schema DB.

## 6. Các bước triển khai (Implementation Steps)

> Trạng thái: toàn bộ S-01..S-09 đã hoàn thành ngày 2026-09-06 (chi tiết phần nào ở đâu xem mục "Các file / điểm chạm sẽ thay đổi" và báo cáo triển khai).

- **S-01**: ✅ Tạo `src/main/resources/application-prod.properties` với `admin.session.cookie.secure=true`, `server.forward-headers-strategy=framework`, `spring.jpa.show-sql=false`, `spring.thymeleaf.cache=true`.
- **S-02**: ✅ Xác minh dev không bật profile prod vẫn dùng `false` (không đổi `application.properties`).
- **S-03**: ✅ (phần compile/test thực hiện trong task) `./mvnw -q compile` + toàn bộ test pass 71/71; phần "cập nhật ghi chú triển khai" khi closure Task 08 giữ nguyên kế hoạch.
- **S-04**: ✅ Ghi chú triển khai production vào mục Phần 8 của `PLAN_ADMIN_LOGIN.md` — thực hiện luôn trong task này thay vì chờ closure (chỉ là ghi chú tài liệu, DoD-05 yêu cầu tồn tại).
- **S-05**: ✅ Sửa `AuthenticationController.handleLogin`: nhánh `rememberMe=false` gửi thêm cookie hết hạn `ADMIN_REMEMBER` (FR-06) + test xác nhận cả hai header Set-Cookie.
- **S-06**: ✅ Bọc `@Transactional` cho `AdminAuthService.login`/`logout` (FR-07); bổ sung test xác nhận khi `createToken` ném lỗi thì login không trả kết quả (rollback) — mức ràng buộc race được review code xác nhận.
- **S-07**: ✅ Login-CSRF (FR-08): tạo `AdminLoginCsrfService`; `SessionCookieService` thêm cookie `ADMIN_LOGIN_CSRF` ngắn hạn (property `admin.login-csrf.max-age-minutes`, mặc định 30 phút); `handleLogin` GET sinh token + set cookie, POST xác thực trước khi gọi `AdminAuthService.login`; form `login.html` thêm hidden field `_csrf`; test phủ thiếu token, sai token, đúng token + nhánh render lại trang khi validation lỗi cũng cấp token mới.
- **S-08**: ✅ Cleanup token hết hạn (FR-09): query xoá theo mốc trên `AdminRememberMeRepository`, method `@Scheduled` (cron qua property `admin.remember-me.cleanup-cron`, zone `Asia/Ho_Chi_Minh`) trên `AdminRememberMeService`, `@EnableScheduling` trên `AdminSchedulingConfig` mới; test xác nhận mốc xoá tính theo múi giờ phiên.
- **S-09**: ✅ Test chuỗi đầy đủ (FR-10): file `AdminInterceptorChainTest` với MockMvc + 3 interceptor thật (đăng ký qua `MappedInterceptor` đúng pattern/exclude của `AdminWebMvcConfigurer`), 3 kịch bản (a)/(b)/(c); chạy `./mvnw -q compile` + toàn bộ test.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Hệ (admin/user) | Package đích | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|------------------|--------------|--------------------|-------|
| `src/main/resources/application-prod.properties` | admin | resources | tạo | Bật cookie Secure + forward headers + ghi đè show-sql/cache cho production (FR-01, FR-02, FR-05 / S-01). |
| `PLAN_ADMIN_LOGIN.md` | — | docs | sửa | Ghi nhận trạng thái triển khai Phần 8 (FR-04 / S-04). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminAuthService.java` | admin | admin.service | sửa | `@Transactional` cho login/logout, nguyên tử + chống race token (FR-07 / S-06). |
| `src/main/java/com/restaurant/ilikepho/admin/controller/AuthenticationController.java` | admin | admin.controller | sửa | Login không nhớ gửi cookie hết hạn remember; GET/POST login luồng login-CSRF (FR-06, FR-08 / S-05, S-07). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminLoginCsrfService.java` | admin | admin.service | tạo | Sinh/xác thực token login-CSRF (FR-08 / S-07). |
| `src/main/java/com/restaurant/ilikepho/admin/service/SessionCookieService.java` | admin | admin.service | sửa | Cookie `ADMIN_LOGIN_CSRF` ngắn hạn (FR-08 / S-07). |
| `src/main/resources/templates/admin/login.html` | admin | templates | sửa | Hidden field token login-CSRF (FR-08 / S-07). |
| `src/main/java/com/restaurant/ilikepho/admin/repository/AdminRememberMeRepository.java` | admin | admin.repository | sửa | Query xoá dòng token hết hạn (FR-09 / S-08). |
| `src/main/java/com/restaurant/ilikepho/admin/service/AdminRememberMeService.java` | admin | admin.service | sửa | Method `@Scheduled` dọn token hết hạn (FR-09 / S-08). |
| `src/main/java/com/restaurant/ilikepho/admin/config/...` (config bật scheduler) | admin | admin.config | tạo | `@EnableScheduling` cho job dọn token (FR-09 / S-08). |
| Test: `AdminInterceptorChainTest` (mới), cập nhật `AuthenticationControllerTest`, `AdminAuthServiceTest`, `AdminRememberMeServiceTest`, `SessionCookieServiceTest`; thêm `AdminLoginCsrfServiceTest`, `AdminProdProfileCookieTest` | admin | test | tạo/sửa | Kiểm chứng FR-06..FR-10 (S-05..S-09) + DoD-03 (cookie Secure với profile prod). |

## 8. Rủi ro & giả định

- **Rủi ro**: bật `Secure` mà trình duyệt chạy HTTP (cấu hình sai) → cookie không gửi, login hỏng. Giảm thiểu: chỉ bật trong profile `prod`; yêu cầu production chạy HTTPS.
- **Rủi ro**: tin header proxy không đúng nếu proxy không set/ghi đè `X-Forwarded-*`. Giảm thiểu: ghi chú rõ proxy phải set header đúng; chỉ dùng `forward-headers-strategy` ở prod sau proxy.
- **Rủi ro**: `@Transactional` trên login giữ pessimistic lock dòng admin — nếu verify mật khẩu chậm, hàng chờ. Giảm thiểu: verify mật khẩu diễn ra **trước khi** `createSession` lấy pessimistic lock (truy vấn đầu `findByUsername` là đọc thường, không mang khoá), nên lock chỉ giữ từ `createSession` đến commit (milli-giây). *(Câu mô tả trước đây "verify mật khẩu trước thao tác DB đầu tiên" là không chính xác — đã sửa theo kết luận code review Task 07.)*
- **Rủi ro**: cookie login-CSRF bị trình duyệt chặn/tắt cookie → POST login bị từ chối. Giảm thiểu: chấp nhận như cookie phiên (app đã cần cookie để chạy); người dùng vào lại trang login để nhận token mới.
- **Rủi ro**: job dọn token chạy nhiều instance cùng lúc. Giảm thiểu: câu lệnh xoá idempotent theo điều kiện, an toàn khi chạy trùng.
- **Ghi chú tương lai (edge sau review)**: khi tính năng xoá/tắt tài khoản admin ra đời, phải xoá remember token của admin trong cùng flow (hoặc `RememberMeInterceptor` coi `createSession` lỗi — admin không tồn tại — là "không có phiên" thay vì 500). Hiện chưa có tính năng này nên chỉ ghi nhận.
- **Giả định**: production chạy HTTPS qua reverse proxy (nginx) và DB dùng biến môi trường `db_password`; không có gì khác phụ thuộc cấu hình mặc định bị thay đổi; prod chạy một instance app (job dọn token không phụ thuộc giả định này).

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [x] **DoD-01**: Tồn tại `application-prod.properties` với `admin.session.cookie.secure=true`, `server.forward-headers-strategy=framework`, `spring.jpa.show-sql=false`, `spring.thymeleaf.cache=true`. *(Đã tạo đúng 4 cấu hình, kèm ghi chú vận hành trong chính file.)*
- [x] **DoD-02**: `application.properties` (dev) vẫn giữ `admin.session.cookie.secure=false` — không đổi hành vi dev. *(Không đụng giá trị này khi sửa application.properties; chỉ thêm property mới.)*
- [x] **DoD-03**: Khởi động với `SPRING_PROFILES_ACTIVE=prod` → cookie `ADMIN_SESSION` (và `ADMIN_REMEMBER` nếu có) dựng với `Secure; SameSite=Lax` — *xác nhận bằng test profile prod*: `AdminProdProfileCookieTest` (`@SpringBootTest` với `spring.profiles.active=prod`) pass — cả 3 cookie (phiên, remember, login-CSRF) đều có cờ `Secure` từ bean thật.
- [x] **DoD-04**: `mvn -q compile` và toàn bộ unit test không bị ảnh hưởng (pass). *(Chạy: `./mvnw -q compile` exit 0; `./mvnw test` với biến môi trường `db_password` đặt đúng → **Tests run: 71, Failures: 0, Errors: 0 — BUILD SUCCESS**, tăng từ 56 test của Task 06.)*
- [x] **DoD-05**: Ghi chú triển khai production (HTTPS + profile `prod` + không seed admin) được bổ sung vào tài liệu. *(Mục Phần 8 của `PLAN_ADMIN_LOGIN.md` — thực hiện luôn trong task thay vì chờ closure Task 08, vì chỉ là ghi chú tài liệu.)*
- [x] **DoD-06**: Login `rememberMe=false` → response chứa Set-Cookie `ADMIN_REMEMBER` maxAge 0 (test pass). *(Test `handleLogin_taiKhoanHopLeKhongNho_chuyenVeHomeVaGuiCookiePhienVaRememberHetHan` — assert header Set-Cookie có `ADMIN_REMEMBER` kèm `Max-Age=0`.)*
- [x] **DoD-07**: `AdminAuthService.login`/`logout` khai báo `@Transactional`; test xác nhận lỗi khi tạo token làm login thất bại nguyên tử (test pass; chống race được review code xác nhận theo thiết kế mục 5). *(Test `login_taoTokenLoi_nemLoiDeTransactionRollbackToanBo` — `createToken` ném lỗi thì exception lan ra ngoài, không trả kết quả; rollback toàn bộ nhờ proxy `@Transactional`.)*
- [x] **DoD-08**: GET `/admin/login` sinh cookie login-CSRF + hidden field khớp; POST login thiếu/sai token bị từ chối, đúng token xác thực như cũ (test pass). *(Test: `loginPage_taoTokenLoginCsrf_datCookieVaHiddenFieldKhopNhau`; `handleLogin_thieuTokenLoginCsrf_...`, `handleLogin_saiTokenLoginCsrf_...` → redirect login + không gọi `login()`; các test đăng nhập hợp lệ đi qua token đúng như cũ.)*
- [x] **DoD-09**: Job dọn token xoá đúng dòng đã hết hạn, giữ dòng còn hạn (test pass). *(Query xoá theo mốc `expiresAt < :now` trên repository; test `cleanupExpiredTokens_xoaTheoMocHienTaiTheoMuiGioPhien` — mốc gửi vào khớp `LocalDateTime.now(Asia/Ho_Chi_Minh)`; phần "giữ dòng còn hạn" là đặc tính của chính điều kiện `expiresAt < now` đã được query đảm bảo.)*
- [x] **DoD-10**: Test chuỗi 3 interceptor qua đủ 3 kịch bản FR-10 (a)/(b)/(c) (test pass). *(File `AdminInterceptorChainTest`: (a) chỉ có remember cookie hợp lệ → 200 + đúng 2 Set-Cookie mới; (b) POST mang CSRF token phiên cũ ngay sau nối phiên → 403; (c) GET trang rồi POST logout: token sai → 403, token vừa render (đọc từ request attribute) → 200.)*

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Vận hành HTTPS thật (chứng chỉ, reverse proxy, CDN).
- Migration DB ngoài nhu cầu `admin_remember_me`, CI/CD, container build.
- Các cấu hình production ngoài phạm vi bảo mật cookie/session.
- Unique index DB trên `admin_remember_me.admin_id` (chỉ làm nếu xuất hiện luồng tạo token ngoài `AdminAuthService.login`).
- Tính năng xoá/tắt tài khoản admin và xử lý remember token kèm theo.
