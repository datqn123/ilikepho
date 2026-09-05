# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Chủ quán / người quản lý Tiệm Phở cô Dự** — đăng nhập hệ admin hằng ngày tại quán, trên desktop/laptop, để quản lý dữ liệu của quán. Cần vào nhanh, yên tâm về bảo mật.
- Khách ghé thăm trang giới thiệu (hệ user) — xem giới thiệu, thực đơn, liên hệ. Không phải mục tiêu của task hiện tại.

## Product Purpose

Hệ thống web của Tiệm Phở cô Dự gồm hai phần tách biệt: trang giới thiệu công khai cho khách, và hệ quản trị (admin) có đăng nhập bằng session. Task hiện tại làm mới giao diện trang đăng nhập admin để chủ quán đăng nhập an toàn, dễ chịu mỗi ngày.

## Positioning

Là phần mềm vận hành cho một quán phở Hà Nội thật, hơn 30 năm, có cá tính thương hiệu riêng ("Phở ngon, chuẩn vị Hà Nội") — không phải khung phần mềm chung chung.

## Operating Context

- Spring Boot 4.1 + Thymeleaf + Spring Data JPA + PostgreSQL, Java 21, chạy bằng `./mvnw`; DB PostgreSQL tên `ilikepho`.
- Giao diện tiếng Việt. Login admin tại `/admin/login`; form gồm `username`, `password`, tuỳ chọn "Ghi nhớ đăng nhập"; lỗi đăng nhập qua `?error` (xác thực bcrypt + session tự xây, cookie HttpOnly).
- Luồng sau đăng nhập: trang home admin (`/admin/home`), logout đưa về trang đăng nhập.

## Capabilities and Constraints

- Đã có: đăng nhập/logout admin theo session (bcrypt, 1 phiên sống/admin, cookie HttpOnly+Secure+SameSite), admin home, khung admin (sidebar/topbar), trang public gồm home + các section neo giới thiệu/thực đơn/liên hệ.
- Chưa có (đừng bịa): địa chỉ quán, số điện thoại, hình ảnh thật, chức năng quản lý nghiệp vụ (thực đơn/đơn hàng…) chưa xuất hiện trong code.
- Hệ admin và hệ user tách package hoàn toàn; entity JPA chỉ dùng khoá ngoại Long, không quan hệ JPA (quy ước admin-build).
- Không hardcode màu/kiểu rời rạc — token dùng chung từ `theme.css`.

## Brand Commitments

- Tên: **Tiệm Phở cô Dự**; tagline "Hà Nội · 30 năm hương vị"; câu "Phở ngon, chuẩn vị Hà Nội".
- Người dùng yêu cầu giữ nguyên phong cách thị giác hiện tại của dự án khi làm lại trang login (thế giới "quán phở cổ điển hiện đại": cam phở, gỗ ấm, Playfair Display + Be Vietnam Pro).

## Evidence on Hand

- Copy thương hiệu và nội dung public đã có sẵn trong code (`index.html`, `theme.css`, `admin/login.html`): tên quán, tagline, "Hơn 30 năm gìn giữ hương vị truyền thống giữa lòng thành phố", "nước dùng hầm 12 tiếng", giờ mở cửa 6:00–22:00, giá món (65k/70k). Đây là nội dung site hiện hữu cần giữ, chưa được xác minh độc lập.
- Không có testimonial, ảnh chụp, địa chỉ/số điện thoại thật — không được bịa thêm.

## Product Principles

- **Xác thực thương hiệu**: dù là trang đăng nhập admin, vẫn phải cảm nhận được đây là quán phở Hà Nội cụ thể này, không phải template.
- **Người vận hành được ưu tiên**: chủ quán dùng mỗi ngày; login phải nhanh, chắc chắn, đáng tin, không cản trở việc vào hệ thống.
- **Nhiệm vụ rõ trước trang trí**: bản chất là màn hình Operate — sự biểu cảm không được che khuất form, trạng thái hay thao tác quen thuộc.
- **Một sản phẩm, một hệ**: admin và user đọc liền mạch như một gia đình (dùng chung token/component), kể cả khi bố cục riêng.
- **Trung thực nội dung**: giữ đúng copy thương hiệu đã có, không thêm tuyên bố chưa được xác minh.

## Accessibility & Inclusion

Giao diện tiếng Việt. Duy trì các chuẩn đang có: form có label, focus-visible rõ, `prefers-reduced-motion`, tương phản đủ. Form đăng nhập phải dùng được bằng bàn phím, trạng thái lỗi thông báo rõ.
