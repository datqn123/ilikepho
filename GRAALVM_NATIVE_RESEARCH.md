# Nghiên cứu GraalVM Native Image cho dự án ilikepho

> **Mục đích:** Đánh giá việc sử dụng GraalVM Native Image để khởi động dự án nhanh hơn — lợi ích là gì, cái giá phải trả là gì, và có đáng làm với dự án hiện tại hay không.
>
> **Ngày:** [ghi ngày tạo]
> **Trạng thái:** Nghiên cứu/phân tích — chưa có thay đổi nào được áp dụng vào code.

---

## 0. Tóm tắt điều hành (Executive summary)

GraalVM Native Image **có thể** khiến ứng dụng Spring Boot của ilikepho khởi động trong vài chục–vài trăm **miligiây** thay vì vài **giây** như JVM thường, đồng thời giảm bộ nhớ runtime và kích thước container — lợi ích lớn nhất đúng như mục tiêu bạn đặt ra (khởi động nhanh).

Tuy nhiên, "cái giá phải trả" là đáng kể và **tập trung vào giai đoạn build & triển khai**, không phải lúc chạy:

- **Build time rất lâu và tốn RAM** — đặc biệt với stack có **Hibernate/JPA** như dự án này.
- **Cần toolchain riêng** (GraalVM JDK / Native Image), CI mới, cấu hình Maven mới.
- **Reflection/classpath scanning** — JPA entity, Lombok, Hibernate proxies, Thymeleaf, Jackson đều nặng về reflection → cần xử lý AOT/hints, dễ vỡ khi dự án lớn dần.
- **Một số thành phần mất tác dụng ở native**: `spring-boot-devtools`, `spring.jpa.hibernate.ddl-auto=update` (schema tự tạo runtime), template Thymeleaf thay đổi lúc chạy, profiling/agent.

**Khuyến nghị cho ilikepho ở giai đoạn hiện tại:** **Chưa nên chuyển sang Native Image ngay.** Dự án còn rất nhỏ (1 entity, 2 controller), đang trong giai đoạn phát triển tích cực, và `spring-boot-devtools` đang được dùng. Có chiến lược an toàn hơn: **giữ JVM build làm mặc định cho phát triển**, chỉ thêm native build **tùy chọn (profile)** khi sắp đưa lên production / cần scale-from-zero. Chi tiết ở [Phần 5](#5-đề-xuất-và-khuyến-nghị).

---

## 1. GraalVM Native Image là gì và vì sao khởi động nhanh

### 1.1. Cơ chế khác biệt so với JVM truyền thống

- **JVM (JIT):** Khi chạy, JVM nạp bytecode, tải class, khởi tạo Spring context (scan bean, đánh giá `@Conditional`, wiring), rồi mới từ từ JIT-compile các hot path. Việc **khởi tạo toàn bộ ứng dụng lúc runtime** chính là lý do khởi động mất vài giây.
- **GraalVM Native Image (AOT):** Tại **thời điểm build**, công cụ phân tích tĩnh toàn bộ ứng dụng (phân tích reachability / "closed-world") — xác định những class, method, field nào thực sự được dùng, tạo ra **executable native riêng biệt**, không cần JVM lúc chạy. Hầu hết công việc khởi tạo được làm trước (build-time initialization), nên lúc chạy chỉ còn việc đọc config và bind port → **khởi động cực nhanh, RAM thấp**.

Spring Boot cung cấp sẵn hỗ trợ native qua **AOT processing** (plugin Maven/Gradle): phân tích beans, các điều kiện, annotations tại build-time và sinh ra các file hint (`reflect-config.json`, `resource-config.json`, ...) để GraalVM biết cách giữ lại reflection cần thiết. Tài liệu chính thức: [Spring Boot Ahead-of-Time Processing](https://docs.spring.io/spring-boot/3.4/maven-plugin/aot.html).

> Lưu ý quan trọng: cách tiếp cận này khác hẳn **Project Leyden** (AOT của JDK chính thống, giữ nguyên JVM runtime, thế giới "open-world"). Xem [GraalVM Native Image vs Project Leyden: Two Answers to the Same Cold-Start Problem](https://www.javacodegeeks.com/2026/04/graalvm-native-image-vs-project-leyden-two-answers-to-the-same-cold-start-problem.html). Điều này nghĩa là chọn Native Image là cam kết theo "closed-world", kéo theo các ràng buộc nêu trong phần 3.

### 1.2. Mức độ hỗ trợ của Spring Boot 4.1 / Java 21

- Dự án này dùng **Spring Boot 4.1.0**, **Java 21** (xem `pom.xml`).
- Native Image được hỗ trợ chính thức trong chuỗi Spring Boot 3.x–4.x; Spring Boot 4 tiếp tục và tinh chỉnh hỗ trợ native/AOT (xem [Spring Boot 4 & Spring Framework 7 – What’s New | Baeldung](https://www.baeldung.com/spring-boot-4-spring-framework-7) và [Spring Boot 4 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)).
- Java 21 là bản LTS được GraalVM hỗ trợ tốt, phù hợp cho native build.

---

## 2. Lợi ích (Benefits) cho ilikepho

| Lợi ích | Chi tiết | Ý nghĩa với ilikepho |
|---|---|---|
| **Khởi động cực nhanh** | Native image khởi động trong vài chục–vài trăm ms thay vì vài giây (JVM). | Đúng mục tiêu của bạn; hữu ích trong CI test, scale-from-zero, khởi động lại nhanh. |
| **Bộ nhớ runtime thấp hơn** | Không cần JVM + heap overhead; footprint nhỏ hơn đáng kể. | Giảm chi phí khi chạy nhiều instance / container. |
| **Container image nhỏ** | Executable tĩnh → image nhẹ hơn. | Deploy nhanh, ít băng thông registry. |
| **Khởi động lại / lăn bánh nhanh** | Rolling deploy, lưu lượng spike (cold start). | Tốt nếu sau này scale theo k8s/serverless. |
| **Không phụ thuộc JVM ở runtime** | Image tự chứa runtime. | Image thuần túy, dễ đóng gói. |

Nguồn tổng quan có số liệu: [GraalVM Native Image with Spring Boot: Faster Startup, Lower Memory](https://katyella.com/blog/graalvm-native-image-spring-boot/).

> **Quan trọng:** Đối với một **monolith MVC nhỏ** như ilikepho (server-side rendering, ít request), lợi ích startup chỉ thực sự phát huy khi bạn **scale nhiều instance, khởi động/tắt thường xuyên, hoặc chạy CI nhiều lần**. Nếu app chỉ chạy 1 instance 24/7, lợi ích startup gần như không thấy — chỉ còn lại cái giá build (phần 3).

---

## 3. Cái giá phải trả (Trade-offs / Costs)

### 3.1. Build time và tài nguyên build (chi phí lớn nhất)

- Native build **lâu và tốn RAM** so với build JVM. Quá trình gồm AOT processing (phân tích Spring context) + `native-image` (phân tích reachability, tạo binary).
- Với stack **JPA/Hibernate**, thời gian build tăng **đáng kể** — có báo cáo cộng đồng ghi nhận mức tăng mạnh khi thêm `spring-boot-starter-jpa` (xem [Spring Boot + GraalVM: Dramatic build time increase with spring-boot-starter-jpa](https://stackoverflow.com/questions/79671225/spring-boot-graalvm-dramatic-build-time-increase-with-spring-boot-starter-jpa#1)).
- Cần máy/CI đủ RAM (thường đề xuất vài GB trở lên dành riêng cho `native-image`).
- Mỗi lần thay đổi code → rebuild native (không có hot-reload như devtools). **Vòng lặp phát triển chậm hơn.**

### 3.2. Toolchain và quy trình build mới

- Cần cài **GraalVM JDK** (bản có Native Image) + chạy `gu install native-image`, hoặc dùng build image của GraalVM.
- Cần thêm cấu hình Maven: `spring-boot-starter-parent` + `native-maven-plugin`, build với profile `native` (goals `process-aot` + `native`).
- **CI pipeline mới** cho native build (môi trường, RAM, cache).
- Đây là thay đổi hạ tầng, không chỉ là sửa code.

### 3.3. Reflection & classpath scanning (điểm dễ vỡ nhất)

Native image phân tích tĩnh nên **không có reflection tự do**. Các thành phần trong stack ilikepho đều dựa vào reflection/số hóa:

- **JPA/Hibernate:** entity (`Category`), proxies lazy loading, nhà sản xuất metadata. Cần AOT xử lý; nếu thiếu hint sẽ lỗi runtime "class not registered for reflection" hoặc `LazyInitializationException` (xem [LazyInitializationException with GraalVM Native Image in Spring Boot 3.3.5](https://github.com/spring-projects/spring-framework/issues/33848#1)). Hibernate 6 có cơ chế riêng hỗ trợ native nhưng vẫn cần cấu hình (xem [Hibernate 6/Native Image](https://discourse.hibernate.org/t/hibernate-6-native-image-from-scratch-no-spring-boot-no-micronaut-no-quarkus/7001/19)).
- **Lombok:** một số annotation (vd `@SuperBuilder`) không tương thích native (xem [issue #39505 spring-boot](https://github.com/spring-projects/spring-boot/issues/39505)). Dự án hiện dùng `@Data`, `@NoArgsConstructor` — ít rủi ro hơn nhưng vẫn cần kiểm chứng.
- **Thymeleaf:** template engine nặng về số hóa cú pháp/fragment lúc runtime; ở native cần AOT xử lý và hạn chế thay đổi template lúc chạy (xem [Thymeleaf layouts với fragment expressions trong native](https://springdoc.cn/thymeleaf-layouts-using-fragment-expressions/)).
- **Jackson / đối tượng DTO:** nếu sau này thêm REST + serialize object thì phải khai báo hint reflection cho các type.

Mỗi khi thêm thư viện/entity mới, **phải kiểm tra lại native build** — đây là gánh nặng bảo trì liên tục.

### 3.4. Những thứ mất tác dụng / hạn chế ở native

| Thành phần trong ilikepho | Trạng thái ở native |
|---|---|
| `spring-boot-devtools` (đang có trong `pom.xml`, scope runtime) | **Không hoạt động** với native (hot-reload chỉ dành cho JVM dev). |
| `spring.jpa.hibernate.ddl-auto=update` | **Cơ chế tự tạo/cập nhật schema lúc runtime của Hibernate gặp hạn chế ở native** — metadata/reflect analysis bị tĩnh hóa. Trong production, ddl-auto=update vốn đã không khuyến khích; với native càng phải dùng migration (Flyway/Liquibase) rõ ràng. Có ghi nhận cộng đồng về việc các giá trị ddl-auto chưa khớp với Hibernate (xem [issue #45336 spring-boot](https://github.com/spring-projects/spring-boot/issues/45336)). |
| Template Thymeleaf thay đổi lúc chạy | Hạn chế: native không hot-reload template; nên compile/pre-build template. |
| Profiling/Java agents (JFR, APM agent, debugger) | Không dùng được như JVM (native không chạy Java agents). |
| Load class/reflection động | Không hỗ trợ (đóng thế giới). |
| Một số library dùng reflection / classpath scanning | Cần hint; có thể không tương thích, phải tìm thay thế. |

### 3.5. Tổng kết "cái giá" theo từng giai đoạn

- **Dev (trả giá nhiều nhất):** build chậm, không hot-reload, vòng lặp chậm.
- **Build/CI:** toolchain mới, RAM, cache, thời gian.
- **Bảo trì:** phải cập nhật hints khi mở rộng entity/reflection.
- **Runtime:** lợi ích lớn (startup/RAM) nhưng có hạn chế schema/template/observability.

---

## 4. Đánh giá cho stack ilikepho cụ thể

Dựa trên inspection dự án:

- **Thành phần nặng reflection:** JPA entity `Category` + JPA auditing, Hibernate, Thymeleaf, Lombok, DTO `UserLoginRequest` (validation). → Đây đều là **các điểm cần AOT/hints**, phức tạp nhất trong native.
- **`@EnableJpaAuditing`** + entity dùng `@GeneratedValue` → cần native AOT cho metadata Hibernate; rủi ro lỗi runtime nếu thiếu hint.
- **`spring.jpa.hibernate.ddl-auto=update`** → cần xử lý (chuyển sang migration) trước khi native.
- **`spring-boot-devtools`** → vô dụng ở native, phải loại khỏi native profile.
- **Ứng dụng MVC server-rendered, nhỏ, đang phát triển** → lợi ích startup hiện chưa thực sự cần thiết, còn cái giá build/bảo trì thì phải gánh ngay.

**Kết luận riêng cho ilikepho:** Với quy mô hiện tại, native là **over-engineering** cho mục tiêu khởi động nhanh. Lợi ích startup chỉ trở nên đáng giá khi có nhu cầu production-scale; ngược lại, cái giá (build time với Hibernate, hints reflection, vô hiệu devtools/ddl-auto, CI mới) phải trả ngay từ hôm nay.

---

## 5. Đề xuất và khuyến nghị

### 5.1. Khuyến nghị chính

**Chưa chuyển native ngay.** Giữ JVM build làm mặc định cho phát triển. Điều kiện nên cân nhắc lại:

- Khi sắp đưa lên **production** cần **scale-from-zero / serverless / nhiều instance** (thì lợi ích startup mới bù được chi phí).
- Khi đã sẵn sàng xử lý: migration DB rõ ràng, CI có RAM, bỏ devtools trong production, cam kết bảo trì hints.

### 5.2. Lộ trình an toàn (nếu sau này muốn làm)

1. **Giữ dev = JVM** (không đổi gì hiện tại).
2. **Chuẩn bị cơ sở:** chuyển `spring.jpa.hibernate.ddl-auto=update` sang **migration** (Flyway/Liquibase) — tốt ngay cả khi không native.
3. **Thêm native build tùy chọn (profile)** trong `pom.xml` (`native-maven-plugin`, profile `native`), build trên CI dùng GraalVM image, không phá vỡ dev flow.
4. **Kiểm thử native:** chạy full test suite trên native image; bổ sung `reflect-config`/hints khi thiếu.
5. **Đo & so sánh:** đo startup time + RAM của native vs JVM cho chính ilikepho trước khi quyết định production.

### 5.3. Checklist rủi ro nếu quyết định làm native

- [ ] Loại `spring-boot-devtools` khỏi native profile (vô dụng).
- [ ] Thay `ddl-auto=update` bằng migration (Flyway/Liquibase).
- [ ] Xác nhận AOT xử lý đúng `Category` entity, JPA auditing, Hibernate metadata.
- [ ] Kiểm chứng Lombok `@Data`/`@NoArgsConstructor` với native (dự kiến OK, nhưng phải test).
- [ ] Xử lý Thymeleaf: pre-compile templates, kiểm fragment expression.
- [ ] Cấu hình CI đủ RAM + cache cho `native-image`.
- [ ] Loại bỏ các Java agents / profiler JVM nếu có.

---

## 6. Nguồn tham khảo

**Tài liệu chính thức**
- [Spring Boot Ahead-of-Time Processing (AOT)](https://docs.spring.io/spring-boot/3.4/maven-plugin/aot.html)
- [Spring Boot 4 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)

**So sánh & tổng quan**
- [GraalVM Native Image vs Project Leyden: Two Answers to the Same Cold-Start Problem](https://www.javacodegeeks.com/2026/04/graalvm-native-image-vs-project-leyden-two-answers-to-the-same-cold-start-problem.html)
- [GraalVM Native Image with Spring Boot: Faster Startup, Lower Memory](https://katyella.com/blog/graalvm-native-image-spring-boot/)
- [Spring Boot 4 & Spring Framework 7 – What’s New | Baeldung](https://www.baeldung.com/spring-boot-4-spring-framework-7)

**Tương thích Hibernate/JPA/Lombok/Thymeleaf**
- [Spring Boot + GraalVM: Dramatic build time increase with spring-boot-starter-jpa](https://stackoverflow.com/questions/79671225/spring-boot-graalvm-dramatic-build-time-increase-with-spring-boot-starter-jpa)
- [Hibernate 6/Native Image discussion](https://discourse.hibernate.org/t/hibernate-6-native-image-from-scratch-no-spring-boot-no-micronaut-no-quarkus/7001/19)
- [LazyInitializationException with GraalVM Native Image in Spring Boot 3.3.5 (spring-framework #33848)](https://github.com/spring-projects/spring-framework/issues/33848)
- [AOT: Lombok @SuperBuilder not compatible with native (spring-boot #39505)](https://github.com/spring-projects/spring-boot/issues/39505)
- [Thymeleaf layouts / fragment expressions trong native](https://springdoc.cn/thymeleaf-layouts-using-fragment-expressions/)
- [ddl-auto values not aligned with Hibernate (spring-boot #45336)](https://github.com/spring-projects/spring-boot/issues/45336)
- [Migration to native causing "Code generation does not support [...]PersistentEntities"](https://stackoverflow.com/questions/76465483/migration-with-spring-boot-3-to-native-image-is-causing-code-generation-does-not)

---

*Báo cáo này là nghiên cứu/đánh giá, không sửa đổi bất kỳ file nguồn hay cấu hình nào của dự án.*
