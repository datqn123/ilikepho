# Tài liệu triển khai Task — Seed tài khoản admin (dev) & kiểm tra tổng thể + hoàn tất tài liệu

## Thông tin task

- **Số thứ tự**: 08
- **Hệ**: admin
- **Nhiệm vụ**: Seed tài khoản admin (chỉ dev) để kiểm chứng end-to-end; xanh lại test môi trường; kiểm tra tổng thể và tick DoD + hoàn tất tài liệu
- **Ngày tạo**: 2026-09-05
- **Ngày cập nhật**: 2026-09-06 — tiếp nhận các điểm Minor từ code review Task 07 (mục 11) để xử lý cùng đợt kiểm tra tổng thể.

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Toàn bộ tính năng admin login đã có (Task 01–07) nhưng **chưa có cách tạo tài khoản admin đầu tiên** (Task 01/04 cố ý loại trừ seed) nên **không thể kiểm chứng end-to-end login qua UI** bằng tài khoản thật. Ngoài ra `IlikephoApplicationTests.contextLoads` đang **fail** do DB: `password authentication failed for user "postgres"` — thiếu biến môi trường `db_password` / PostgreSQL local chưa đúng — khiến `mvn test` chưa xanh. Các checkbox DoD của `docs/tasks/01–04` vẫn bỏ trống dù code đã có.
- **Mục tiêu**:
  1. Seed tài khoản admin **chỉ ở môi trường dev** khi được bật (property `admin.seed.enabled`) — không bao giờ tự chạy ở production.
  2. Ghi rõ yêu cầu môi trường chạy test (PostgreSQL local + `db_password`) để `mvn test` xanh.
  3. Kiểm tra tổng thể (compile + toàn bộ test + kiểm chứng UI end-to-end các luồng login/logout/remember-me/CSRF).
  4. Hoàn tất tài liệu: **tick toàn bộ DoD** `docs/tasks/01–08`, đánh dấu trạng thái `PLAN_ADMIN_LOGIN.md` (Phần 1–8) và các tài liệu liên quan.
- **Người dùng / người hưởng lợi**: Quản trị viên phát triển — có tài khoản để chạy thử local; đội dự án — tài liệu/DoD phản ánh đúng trạng thái thực tế.

## 2. Phạm vi

### In scope (việc thuộc task)
- Tạo bộ seed tài khoản admin dành riêng dev (`AdminDataSeeder`) chạy khi `admin.seed.enabled=true`: nếu chưa có admin theo username cấu hình thì tạo bằng `PasswordService.hash`.
- Property `admin.seed.enabled` (mặc định `false`), `admin.seed.username`, `admin.seed.password` (mặc định rỗng) trong `application.properties`.
- Ghi chú môi trường chạy test (PostgreSQL local, DB `ilikepho`, biến môi trường `db_password`) — cập nhật vào tài liệu.
- Chạy kiểm tra tổng thể: `mvn -q compile`, `mvn test` toàn bộ, kiểm chứng UI end-to-end.
- Xử lý các điểm Minor từ code review Task 07 (chi tiết tại mục 11): hằng số pattern interceptor dùng chung, hằng số múi giờ dùng chung, `Cache-Control: no-store` cho trang đăng nhập, integration test nguyên tử login (tùy chọn).
- Hoàn tất tài liệu: tick DoD cho `docs/tasks/01` → `08` và cập nhật trạng thái `PLAN_ADMIN_LOGIN.md`.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- Seed chạy ở production / tự bật mặc định (phải chủ động bật + profile dev/local).
- Tạo trang quản lý tài khoản admin trên UI (đổi mật khẩu, thêm user) — để task nghiệp vụ sau.
- Khắc phục hạ tầng PostgreSQL của người dùng (chỉ hướng dẫn cấu hình biến môi trường).
- Mọi tính năng hệ user / native image.

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Có class seed `AdminDataSeeder` (chạy một lần lúc khởi động) chỉ active khi property `admin.seed.enabled=true`. | Khi seed tắt → không chạy, không ảnh hưởng startup. |
| FR-02 | Khi bật: nếu chưa tồn tại admin có `username` = `admin.seed.username` → tạo bản ghi `admin` với `password_hash` = `PasswordService.hash(admin.seed.password)`; nếu đã tồn tại → bỏ qua (không tạo trùng, không ghi đè mật khẩu). | |
| FR-03 | Property `admin.seed.enabled=false` (mặc định), `admin.seed.username` (mặc định `admin`), `admin.seed.password` (mặc định rỗng → nếu để rỗng thì không seed). | Không để mật khẩu mặc định cứng có giá trị trong file. |
| FR-04 | Tài liệu ghi rõ cách chạy test local: PostgreSQL chạy tại `localhost:5432`, DB `ilikepho` tồn tại, biến môi trường `db_password` đặt đúng → `mvn test` xanh. | Giải thích nguyên nhân fail trước đây. |

## 4. Yêu cầu phi chức năng

- **Bảo mật / quyền hạn**: seed **không bao giờ tự chạy ở production** (property tắt mặc định; chỉ bật ở dev/local); mật khẩu lưu bằng bcrypt qua `PasswordService` — không plain text; không commit mật khẩu mặc định.
- **Khả năng mở rộng / bảo trì**: seed nằm trong package admin, chạy idempotent (kiểm tra tồn tại trước khi tạo).
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (package admin, Javadoc đủ cho method); kiểm tra DoD bằng cách chạy thử thật, không khai báo suông.

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring Boot (CommandLineRunner/ApplicationRunner + `@ConditionalOnProperty`), JPA (`AdminRepository`), `PasswordService`. Package admin: `admin.config` hoặc `admin.bootstrap`.
- **Luồng xử lý**: Khởi động → nếu `admin.seed.enabled=true` → đọc username/password từ property → `AdminRepository.findByUsername` → rỗng thì tạo `Admin` (hash mật khẩu, set `createdAt`/`updatedAt`) và `save`.
- **Các quyết định thiết kế**:
  - **Bật qua property `admin.seed.enabled`, mặc định `false`**: an toàn tuyệt đối với production — không cần profile phức tạp, người chạy local chủ động bật (vd biến môi trường `ADMIN_SEED_ENABLED=true` hoặc `-Dadmin.seed.enabled=true`). Không dùng `@Profile("dev")` để tránh phụ thuộc profile đặt sẵn.
  - **Kiểm tra tồn tại trước khi tạo** (idempotent): chạy lại nhiều lần không tạo trùng, không reset mật khẩu đã đổi.
  - **Không có mật khẩu mặc định có giá trị trong file**: tránh commit credential; người dùng phải cung cấp khi bật seed.
  - **Đặt trong `admin.config`** (hoặc `admin.bootstrap`): gọn, cùng nhóm cấu hình khởi động admin.
- **Điểm chạm hệ thống**: `application.properties` (3 property mới); bảng `admin` (đã có Task 01).

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Thêm property vào `application.properties`: `admin.seed.enabled=false`, `admin.seed.username=admin`, `admin.seed.password=` (rỗng).
- **S-02**: Tạo `AdminDataSeeder` trong package admin (bật theo property, seed idempotent qua `AdminRepository` + `PasswordService`).
- **S-03**: `mvn -q compile`; viết test (nếu cần, dùng mock `AdminRepository`/`PasswordService` để xác nhận tạo/không tạo trùng/không chạy khi tắt).
- **S-04**: Chuẩn bị môi trường: PostgreSQL local chạy, DB `ilikepho`, set biến môi trường `db_password`; chạy `mvn test` toàn bộ cho xanh.
- **S-05**: Bật seed (`admin.seed.enabled=true` + username/password) chạy app dev → kiểm chứng UI end-to-end: login đúng/sai, redirect home, logout, CSRF (thiếu token → 403), remember-me (nối lại phiên sau khi xoá session cookie).
- **S-06**: Tick toàn bộ DoD `docs/tasks/01` → `08` (chỉ tick mục đã kiểm chứng); cập nhật trạng thái triển khai vào `PLAN_ADMIN_LOGIN.md` (Phần 1–8) và ghi chú cấu hình môi trường vào tài liệu.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Hệ (admin/user) | Package đích | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|------------------|--------------|--------------------|-------|
| `src/main/resources/application.properties` | admin | resources | sửa | Thêm property seed (FR-03 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/config/AdminDataSeeder.java` | admin | `admin.config` | tạo | Seed admin chỉ khi bật (FR-01, FR-02 / S-02). |
| `src/test/java/.../admin/.../AdminDataSeederTest.java` (nếu có) | admin | test | tạo | Kiểm chứng seed idempotent/không chạy khi tắt (S-03). |
| `docs/tasks/01-admin-chuan-bi-tai-khoan-mat-khau.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/02-admin-bang-session.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/03-admin-sinh-session-id-cau-hinh-cookie.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/04-admin-login-session-xac-thuc-logout.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/05-admin-chong-csrf-token.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/06-admin-remember-me.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/07-admin-bao-mat-production.md` | — | docs | sửa | Tick DoD sau kiểm chứng (S-06). |
| `docs/tasks/08-admin-seed-tai-khoan-va-kiem-tra-tong-the.md` | — | docs | sửa | Tick DoD (S-06). |
| `PLAN_ADMIN_LOGIN.md` | — | docs | sửa | Đánh dấu trạng thái Phần 1–8 + ghi chú triển khai (S-06). |

## 8. Rủi ro & giả định

- **Rủi ro**: quên tắt `admin.seed.enabled` khi deploy production → tạo tài khoản admin ngầm. Giảm thiểu: mặc định `false`; ghi rõ trong doc & checklist vận hành; seed chỉ tạo khi bảng `admin` chưa có username đó.
- **Rủi ro**: `mvn test` vẫn fail nếu môi trường Postgres chưa đúng — không thuộc quyền sửa code; cần người dùng cấu hình `db_password` đúng theo tài liệu.
- **Giả định**: người dùng chạy được PostgreSQL local `ilikepho`; tài khoản dev dùng để kiểm chứng là tài khoản seed do người dùng đặt (không commit mật khẩu).

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [x] **DoD-01**: `mvn -q compile` thành công; `mvn test` toàn bộ **xanh** với môi trường Postgres local + `db_password` đúng. *(Task 08: `./mvnw test` với biến môi trường `db_password` đặt đúng → Tests run: 76, Failures: 0, Errors: 0 — BUILD SUCCESS; 76 sau khi bổ sung 1 test username seed rỗng từ round code review.)*
- [x] **DoD-02**: Khi `admin.seed.enabled=false` (mặc định), khởi động **không** tạo tài khoản nào. *(Chạy live không đặt biến seed: Started sạch, bảng `admin` giữ nguyên dữ liệu cũ.)*
- [x] **DoD-03**: Khi bật seed với username chưa tồn tại → tạo admin (mật khẩu lưu bcrypt); chạy lại lần 2 → không tạo trùng/không đổi mật khẩu. *(Chạy live: lần 1 tạo đúng 1 dòng admin; restart lần 2 cùng biến môi trường → vẫn đúng 1 dòng, đăng nhập bằng mật khẩu đã seed vẫn thành công (hash bcrypt không bị ghi đè). Unit: `AdminDataSeederTest` 4 ca.)*
- [x] **DoD-04**: UI end-to-end kiểm chứng được: login đúng → `/admin/home`; login sai → `?error`; logout → về login và phiên/token remember bị xoá. *(Trình duyệt thực tế Task 08: sai mật khẩu hiện alert "Tài khoản hoặc mật khẩu không đúng" tại `?error`; đúng tài khoản seed vào `/admin/home` "Đăng nhập thành công"; logout từ dropdown topbar về `/admin/login`, vào lại `/admin/home` bị trả về login (phiên và remember đã xoá).)*
- [x] **DoD-05**: CSRF: POST logout thiếu/sai token → 403; đúng token → logout thành công (Task 05 kiểm chứng bằng tay). *(curl Task 08: thiếu token 403, sai token 403, đúng token 302; trình duyệt logout thành công.)*
- [x] **DoD-06**: Remember-me: đăng nhập có tick nhớ → xoá session cookie rồi truy cập lại → tự nối phiên (Task 06 kiểm chứng bằng tay). *(curl Task 08: giữ duy nhất cookie `ADMIN_REMEMBER` → GET `/admin/home` 200 tự nối phiên, nhận 2 Set-Cookie mới (phiên mới + remember xoay mới).)*
- [x] **DoD-07**: Toàn bộ checkbox DoD trong `docs/tasks/01` → `08` được **tick đúng theo kết quả kiểm chứng thực tế**. *(Đợt tick Task 08: docs 01→04 tick kèm bằng chứng unit + live; doc 05 tick DoD-08 sau khi chạy UI; docs 06/07 đã tick, bổ sung ghi chú kiểm chứng bằng tay; đồng thời xoá mật khẩu DB thật khỏi bằng chứng trong docs 06/07.)*
- [x] **DoD-08**: `PLAN_ADMIN_LOGIN.md` phản ánh trạng thái: Phần 1–8 đã triển khai + ghi chú môi trường/test/seed. *(Bổ sung khối "Trạng thái triển khai" tổng hợp Phần 1–8, môi trường test, seed dev và runbook.)*

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Quản lý tài khoản admin trên UI (đổi mật khẩu, thêm/sửa/xoá admin).
- Tự động hoá CI/CD hoặc migration DB.
- Seed/tính năng cho hệ user; native image.

## 11. Mục chuyển tiếp từ code review Task 07 (2026-09-06)

Code review Task 07 kết luận **Ready to merge** (0 Critical, 0 Important, 5 Minor); các Minor còn lại dưới đây chuyển vào task này để xử lý cùng đợt kiểm tra tổng thể:

- **Tách hằng số pattern interceptor dùng chung**: các pattern `/admin/**`, `/admin/login`, `/admin/logout` đang được gõ lại trong `AdminInterceptorChainTest` thay vì tham chiếu từ `AdminWebMvcConfigurer` — tách hằng số public ở configurer để test tự ghim đúng cấu hình thật (chống drift cấu hình bảo mật khi sau này sửa config).
- **Hằng số chuỗi múi giờ dùng chung**: chuỗi `"Asia/Ho_Chi_Minh"` lặp tại `@Scheduled(zone = ...)` của `AdminRememberMeService` (annotation cần hằng số compile-time, không dùng được `ZoneId`) — thêm `public static final String SESSION_ZONE_ID` cạnh `AdminSessionService.SESSION_ZONE` để hai chỗ không lệch nhau.
- **`Cache-Control: no-store` cho response trang đăng nhập**: hidden field login-CSRF nằm trong HTML trả về; `no-store` là phòng thủ bổ sung (double-submit vẫn an toàn vì cookie đi kèm không bị cache).
- **(Tùy chọn) Integration test nguyên tử login**: `@SpringBootTest` chứng minh rollback thật khi tạo remember token lỗi và tuần tự hoá 2 login đồng thời của cùng admin (hiện được chứng minh bằng review code + unit test lan truyền exception — xem Task 07 mục 5/DoD-07).
- **Backlog production-readiness (ghi nhận, có thể tách task riêng)**: prod đang kế thừa `spring.jpa.hibernate.ddl-auto=update`; chưa có rate-limit/lockout cho luồng login; bổ sung ghi chú runbook về trường hợp 2 tab đăng nhập mở cùng lúc (tab submit sau đổi cookie login-CSRF → tab submit trước bị trả về trang login một lần — đặc tính của double-submit, chấp nhận được).
