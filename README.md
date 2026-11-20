# FoodHub — ứng dụng giao đồ ăn Android

FoodHub là dự án Android đang phát triển, gồm bốn vai trò: khách hàng, nhà hàng, shipper và admin. Giao diện dùng Kotlin, Jetpack Compose và Material 3, giữ tông trắng/xanh với nội dung tiếng Việt.

Dữ liệu nghiệp vụ đi qua **API PHP trên Alwaysdata** để lưu trong database **`food_delivery_app` trên TiDB Cloud** qua kết nối MySQL có TLS. Firebase Authentication xử lý danh tính đăng nhập; Firebase Cloud Messaging gửi thông báo; Realtime Database giữ dữ liệu phiên và tọa độ trực tiếp; Cloudinary lưu ảnh.

> Ngày **14/11/2026**, database chuyển sang TiDB Cloud (32 bảng, 27 cột ID tự tăng) và API đã online trên **Alwaysdata**. Mã Android đã đổi URL; app cũ trên điện thoại cần build/cài bản mới. Các database cũ vẫn giữ để đối chiếu.

## Đọc tài liệu

| Tài liệu | Nội dung |
| --- | --- |
| [Bàn giao phiên làm việc](docs/SESSION_HANDOFF.md) | Đọc đầu tiên ở phiên sau; trạng thái, kiểm tra đã làm, phần còn thiếu |
| [Kiến trúc và công nghệ](docs/ARCHITECTURE.md) | Các thành phần, thư viện, mã nguồn và vai trò dịch vụ |
| [Luồng xử lý](docs/WORKFLOWS.md) | Đăng nhập, đặt hàng, thanh toán, giao hàng, địa chỉ, ảnh và chat |
| [Database](docs/DATABASE.md) | 32 bảng, quan hệ, quy ước ID và các trường nghiệp vụ quan trọng |
| [API](docs/API.md) | Endpoint hiện có, dữ liệu trả về, xác thực và phạm vi quyền |
| [Chạy local và xử lý lỗi](docs/LOCAL_SETUP.md) | Android Studio, Alwaysdata, TiDB, Firebase, PayOS, kiểm tra |
| [Triển khai Alwaysdata](docs/ALWAYSDATA.md) | Hosting đang dùng, cách triển khai, cấu hình và kiểm tra |
| [Tư vấn món bằng Gemini](docs/AI_MEAL_ADVISOR.md) | Giai đoạn 1, cấu hình khóa riêng, API và triển khai |
| [Hướng dẫn cho coding agent](AGENTS.md) | Quyết định đã thống nhất và đường dẫn cần đọc |

## Cấu trúc chính

```text
app/src/main/java/com/example/dacs_3_composer/
  MainApplication.kt       Khởi tạo MapLibre, Cloudinary
  MainActivity.kt          Khôi phục đăng nhập và điều hướng theo vai trò
  core/model/              Model dùng chung
  data/remote/             FoodHub API client, các dịch vụ upload/thanh toán
  data/repository/         Truy cập và điều phối dữ liệu
  data/firebase/           FCM, presence; có cả file cũ hoặc khung rỗng
  feature/auth/            Email/password, Google và đồng bộ hồ sơ
  feature/customer/        Khách hàng
  feature/restaurant/      Quản lý nhà hàng
  feature/shipper/         Giao hàng, bản đồ, hồ sơ, thu nhập
  feature/admin/           Thống kê, tài khoản, voucher, hồ sơ admin
  feature/chat/            Danh sách hội thoại và chat
  feature/shared/location/ Tìm địa chỉ, GPS và bản đồ chọn vị trí
backend/
  api/index.php            Router và xử lý API PHP
  database/schema.sql      Định nghĩa 32 bảng
  migration/               Thay đổi schema cho DB đã tồn tại
docs/                      Tài liệu dự án
```

## Bắt đầu nhanh

1. Mở **thư mục gốc dự án** bằng Android Studio.
2. Kiểm tra [API online](https://chinh.alwaysdata.net/food-delivery-api/api/), cần nhận `database: connected`.
3. `FoodHubClient.BASE_URL` hiện trỏ Alwaysdata..
4. Build/cài bản app mới để dùng URL mới; ứng dụng cũ chưa tự đổi endpoint.
5. Tài khoản admin thử là `admin@gmail.com`, sử dụng mật khẩu đã được cung cấp trong cuộc trao đổi tạo tài khoản.

Chi tiết cấu hình nằm trong [LOCAL_SETUP.md](docs/LOCAL_SETUP.md).
