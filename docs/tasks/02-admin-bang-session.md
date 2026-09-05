# Tài liệu triển khai Task — Bảng session trong PostgreSQL (Phần 2)

## Thông tin task

- **Số thứ tự**: 02
- **Hệ**: admin
- **Nhiệm vụ**: Bảng session trong PostgreSQL (triển khai Phần 2 trong `PLAN_ADMIN_LOGIN.md`)
- **Ngày tạo**: 2026-08-22

---

## 1. Bối cảnh & Mục tiêu

- **Bối cảnh**: Hệ admin đã có nền tảng tài khoản & hash mật khẩu (Task 01). Để xây dựng luồng đăng nhập session-based (Phần 4–6), cần bảng lưu phiên đăng nhập của admin.
- **Mục tiêu**: Tạo bảng `admin_session` tối giản lưu **hash** của session ID (không lưu chuỗi gốc), kèm thông tin phiên: thuộc admin nào, thời điểm tạo, hoạt động gần nhất, trạng thái (hoạt động / khoá) — phục vụ chính sách 1 admin = 1 phiên hoạt động bằng xoá logic.
- **Người dùng / người hưởng lợi**: Quản trị viên; bảng session là nền tảng cho Phần 4 (login), Phần 5 (middleware) và Phần 6 (logout).

## 2. Phạm vi

### In scope (việc thuộc task)
- Tạo entity `AdminSession` + bảng `admin_session` gồm: `id`, `session_hash` (unique), `admin_id` (khoá ngoại Long), `created_at`, `last_activity_at`, `status` (ACTIVE / LOCKED).
- Tạo `AdminSessionRepository` (JPA) với `findBySessionHash`.
- Chỉ phục vụ **hệ admin** — đặt trong package `...admin...`; hệ user sau này tự làm bảng riêng.

### Out of scope (việc KHÔNG thuộc task — đã loại trừ)
- Không sinh session ID (thuộc Phần 3), không cấu hình cookie (Phần 3).
- Không làm luồng login xác thực & tạo phiên (Phần 4).
- Không làm middleware xác thực mỗi request / sliding expiration (Phần 5).
- Không làm logout (Phần 6).
- **KHÔNG làm method khoá các phiên cũ** (chính sách 1 admin = 1 phiên): theo lựa chọn người dùng, method này sẽ làm ở Phần 4 khi code luồng login.
- Không dùng chung bảng session với hệ user.

## 3. Yêu cầu chức năng (Functional Requirements)

| Mã | Mô tả yêu cầu | Ghi chú |
|----|---------------|---------|
| FR-01 | Entity `AdminSession` ánh xạ bảng `admin_session` gồm: `id` (khóa chính tự tăng), `sessionHash` (duy nhất, không rỗng), `adminId` (không rỗng), `createdAt`, `lastActivityAt`, `status`. | Đặt trong `com.restaurant.ilikepho.admin.entity`; không dùng quan hệ JPA. |
| FR-02 | `sessionHash` là hash của session ID (không lưu chuỗi gốc) — chỉ lưu giá trị hash vào cột. | Đúng yêu cầu "lưu hash, không lưu chuỗi gốc". |
| FR-03 | `adminId` là khoá ngoại dạng `Long` trỏ tới bảng `admin` (không khai quan hệ entity). | Tuân theo admin-build: khoá ngoại Long, nạp thủ công qua repository. |
| FR-04 | `status` kiểu enum `SessionStatus` gồm `ACTIVE` / `LOCKED`, lưu dạng chuỗi trong DB. | Phục vụ xoá logic: LOCKED = phiên bị khoá nhưng giữ dấu vết. |
| FR-05 | `AdminSessionRepository` kế thừa `JpaRepository<AdminSession, Long>`, có method `Optional<AdminSession> findBySessionHash(String sessionHash)`. | Đặt trong `com.restaurant.ilikepho.admin.repository`; phục vụ tra cứu phiên theo hash (dùng ở Phần 5). |

## 4. Yêu cầu phi chức năng

- **Hiệu năng**: cột `session_hash` unique (có index) để tra cứu nhanh theo hash.
- **Bảo mật / quyền hạn**: không lưu chuỗi session ID gốc, chỉ lưu hash — nếu DB bị lộ, kẻ tấn công không tái sử dụng được phiên. Không log session hash.
- **Khả năng mở rộng / bảo trì**: tách biệt hoàn toàn với hệ user (package `admin.entity`, `admin.repository`); mỗi method có Javadoc mô tả chức năng.
- **Trải nghiệm / quy ước**: tuân theo `admin-build` (phân tách package admin/user, khoá ngoại Long, comment Javadoc, không comment rác).

## 5. Thiết kế kỹ thuật

- **Kiến trúc / mô-đun liên quan**: Spring Boot (JPA + Thymeleaf + Web MVC), PostgreSQL, Lombok. Hệ admin có các package `admin.entity`, `admin.repository`, `admin.service`, `admin.controller`, `admin.dto`; Task 02 bổ sung entity & repository session vào package admin tương ứng.
- **Luồng dữ liệu / luồng xử lý**: `AdminSession` (entity) ↔ `AdminSessionRepository` (JPA) ↔ bảng `admin_session`. Khi login (Phần 4) sẽ tạo bản ghi session mới; khi middleware (Phần 5) sẽ tra cứu theo `sessionHash`; khi khoá phiên (Phần 4/6) sẽ đổi `status` sang `LOCKED` (xoá logic, giữ dấu vết).
- **Các quyết định thiết kế**:
  - **Bảng `admin_session` riêng cho hệ admin** (đã được người dùng chọn): không dùng bảng dùng chung với user, tuân theo admin-build.
  - **`adminId` là khoá ngoại dạng `Long`** (không khai quan hệ JPA), nạp dữ liệu admin thủ công qua `AdminRepository` khi cần.
  - **`status` dùng enum `SessionStatus` (ACTIVE/LOCKED)** lưu dạng chuỗi — rõ nghĩa hơn boolean, đúng tinh thần "trạng thái (hoạt động / khoá)" của plan.
  - **Chỉ tạo cấu trúc bảng + repository** (theo lựa chọn người dùng); method khoá phiên cũ (1 admin = 1 phiên) để dành cho Phần 4.
- **Điểm chạm hệ thống**: bảng `admin_session` trong PostgreSQL (JPA `ddl-auto=update` tự tạo); bảng phụ thuộc `admin` (Task 01) qua `admin_id`.

## 6. Các bước triển khai (Implementation Steps)

- **S-01**: Tạo enum `SessionStatus` (ACTIVE / LOCKED) trong `com.restaurant.ilikepho.admin.entity`.
- **S-02**: Tạo entity `AdminSession` trong `com.restaurant.ilikepho.admin.entity`.
- **S-03**: Tạo `AdminSessionRepository` trong `com.restaurant.ilikepho.admin.repository`.
- **S-04**: Biên dịch (`mvn -q compile`) để xác nhận dự án build được.

## 7. Các file / điểm chạm sẽ thay đổi

| File / Thành phần | Loại (tạo/sửa/xoá) | Lý do |
|-------------------|--------------------|-------|
| `src/main/java/com/restaurant/ilikepho/admin/entity/SessionStatus.java` | tạo | Enum trạng thái phiên ACTIVE/LOCKED (FR-04 / S-01). |
| `src/main/java/com/restaurant/ilikepho/admin/entity/AdminSession.java` | tạo | Entity ánh xạ bảng `admin_session` (FR-01, FR-02, FR-03, FR-04 / S-02). |
| `src/main/java/com/restaurant/ilikepho/admin/repository/AdminSessionRepository.java` | tạo | Truy vấn phiên admin (FR-05 / S-03). |

## 8. Rủi ro & giả định

- **Rủi ro**: `ddl-auto=update` tự tạo bảng có thể không đúng cấu trúc nếu khai báo sai; giảm thiểu bằng khai báo `@Column` rõ ràng và kiểm tra khi chạy.
- **Rủi ro**: bảng `admin` (Task 01) chưa có bản ghi thực tế do chưa seed; không ảnh hưởng tới việc tạo cấu trúc bảng session (chỉ ràng buộc khi insert dữ liệu — việc của Phần 4).
- **Giả định**: DB PostgreSQL `ilikepho` chạy được, JPA tự tạo bảng `admin_session` khi khởi động; không cần migration tay.

## 9. Định nghĩa Hoàn thành (Definition of Done / Acceptance Criteria)

- [ ] **DoD-01**: `mvn -q compile` thành công (không phá vỡ build hiện tại).
- [ ] **DoD-02**: Entity `AdminSession` tồn tại trong `com.restaurant.ilikepho.admin.entity`, ánh xạ bảng `admin_session`, có đủ trường theo FR-01, KHÔNG khai quan hệ JPA.
- [ ] **DoD-03**: Cột `session_hash` unique, chỉ lưu hash session ID (không lưu chuỗi gốc).
- [ ] **DoD-04**: `adminId` là khoá ngoại dạng `Long` trỏ tới bảng `admin`.
- [ ] **DoD-05**: Enum `SessionStatus` có `ACTIVE` / `LOCKED`, lưu dạng chuỗi trong DB.
- [ ] **DoD-06**: `AdminSessionRepository.findBySessionHash(String)` tồn tại trong package admin.
- [ ] **DoD-07**: Không có thành phần session nào nằm ngoài package admin (không lẫn vào vùng user/shared).

## 10. Các mục Ngoài phạm vi đã loại trừ (để sau)

- Sinh session ID & cấu hình cookie (Phần 3).
- Luồng login tạo phiên & khoá phiên cũ — gồm cả method khoá phiên ACTIVE cũ của 1 admin (Phần 4).
- Middleware xác thực mỗi request & sliding expiration (Phần 5).
- Logout (Phần 6).
- Bảng session cho hệ user.
