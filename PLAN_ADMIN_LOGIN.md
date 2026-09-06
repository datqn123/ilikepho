# Kế hoạch triển khai chức năng Login (Session-based) cho Admin

> Bản ghi các quyết định đã thảo luận. Không đi sâu chi tiết triển khai.

## Phần 1 — Chuẩn bị tài khoản admin & mật khẩu
- Bảng admin để quản lý tài khoản.
- Mật khẩu lưu bằng hash chậm (bcrypt/argon2) + salt riêng từng user, không lưu plain text.

## Phần 2 — Bảng session trong PostgreSQL
- Tạo bảng session tối giản: hash session ID, user/admin, `created_at`, `last_activity_at`, trạng thái (hoạt động / khoá).
- Lưu **hash** của session ID, không lưu chuỗi gốc.
- Chính sách **1 user = 1 phiên hoạt động**: khi đăng nhập thiết bị khác, khoá phiên cũ bằng **xoá logic** (đánh dấu khoá, giữ dấu vết).

## Phần 3 — Sinh session ID & cấu hình cookie
- Sinh session ID bằng **nguồn ngẫu nhiên an toàn** phía server (128-bit+), chuỗi gốc đặt trong cookie.
- Cookie cấu hình **HttpOnly + Secure + SameSite** (Secure tắt lúc dev HTTP, bật ở production HTTPS).

## Phần 4 — Luồng đăng nhập (login)
- Xác thực mật khẩu (hash + so sánh).
- Tạo phiên mới, **khoá mọi phiên cũ** của user đó (đảm bảo chỉ 1 phiên sống, xử lý nguyên tử khi đăng nhập đồng thời).

## Phần 5 — Xác thực mỗi request (middleware)
- Đọc cookie, tra hash trong DB.
- Nếu phiên **bị khoá/hết hạn** (bất kể lý do) → đưa về trang đăng nhập.
- Cập nhật `last_activity_at` để phục vụ **sliding expiration**.

## Phần 6 — Luồng đăng xuất (logout)
- Đánh dấu phiên khoá trong DB + xoá cookie trên trình duyệt → về trang đăng nhập.

## Phần 7 — Chống CSRF
- Lớp 1: **SameSite** trên cookie.
- Lớp 2: **Synchronizer Token** cho các thao tác nhạy cảm (tạo/sửa/xoá), token vô hiệu khi phiên kết thúc.

## Phần 8 — Bảo mật production
- Bật HTTPS, bật `Secure` trên cookie.

> **Trạng thái triển khai (cập nhật Task 08, 2026-09-06): Phần 1–8 đã hoàn tất.** Chi tiết từng phần và bằng chứng nghiệm thu nằm trong `docs/tasks/01…08` (toàn bộ DoD đã tick kèm bằng chứng).
> - Phần 1 (bảng admin + bcrypt) → Task 01 · Phần 2 (bảng session) → Task 02 · Phần 3 (session ID + cookie) → Task 03 · Phần 4 (login, khoá phiên cũ) → Task 04 · Phần 5 (middleware xác thực + sliding expiration) → Task 04 · Phần 6 (logout) → Task 04, bổ sung dọn remember token ở Task 06/07 · Phần 7 (chống CSRF: SameSite + synchronizer token phiên + login-CSRF double-submit) → Task 03/05/07 · Phần 8 (production) → Task 07, seed dev → Task 08.
>
> **Môi trường chạy test**: PostgreSQL local tại `localhost:5432`, DB `ilikepho` phải tồn tại, đặt biến môi trường `db_password` (mật khẩu Postgres local) trước khi chạy `./mvnw test` — thiếu biến này context test fail với `password authentication failed for user "postgres"`.
>
> **Seed tài khoản admin (chỉ dev/local)**: mặc định tắt. Bật khi cần kiểm chứng:
> `ADMIN_SEED_ENABLED=true ADMIN_SEED_USERNAME=admin ADMIN_SEED_PASSWORD='…' ./mvnw spring-boot:run`
> Seed idempotent (username đã có → bỏ qua, không ghi đè mật khẩu), mật khẩu lưu bcrypt, không bao giờ bật ở production.
>
> **Runbook**: mở 2 tab trang login cùng lúc thì tab submit sau đổi cookie login-CSRF, tab submit trước bị trả về trang login một lần rồi nhận token mới (đặc tính double-submit, chấp nhận được). Remember token hết hạn được job dọn lúc 3h sáng (`admin.remember-me.cleanup-cron`).
>
> **Triển khai production (Task 07, 2026-09-06):** cấu hình production gom vào profile `prod`
> (`application-prod.properties`) — `admin.session.cookie.secure=true` (áp cho cả cookie phiên
> lẫn cookie ghi nhớ), `server.forward-headers-strategy=framework` để đọc scheme từ reverse proxy,
> `show-sql=false`, `thymeleaf.cache=true`. Cách chạy production: đứng sau reverse proxy HTTPS
> (nginx phải set đúng header `X-Forwarded-Proto/Host/Port`), đặt biến môi trường
> `SPRING_PROFILES_ACTIVE=prod`, DB vẫn đọc mật khẩu từ biến môi trường `db_password`
> và **không bật seed tài khoản admin** (tài khoản tạo thủ công — Task 08 chỉ seed cho dev).
