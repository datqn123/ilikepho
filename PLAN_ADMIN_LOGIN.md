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
