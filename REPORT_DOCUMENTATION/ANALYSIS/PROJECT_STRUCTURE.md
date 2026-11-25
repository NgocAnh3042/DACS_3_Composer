# TỔNG QUAN CẤU TRÚC DỰ ÁN FOODHUB

## 1. Thông tin chung về dự án
- **Tên dự án:** FoodHub (Mã dự án: `DACS_3_Composer`).
- **Lĩnh vực:** Ứng dụng thương mại điện tử giao đồ ăn trực tuyến (Food Delivery & M-Commerce).
- **Mô hình kiến trúc tổng thể:** Client - Server phân tán (Mobile Client Native Android + Backend RESTful API + Distributed Database Cluster).
- **Các tác nhân chính trong hệ thống:** 
  1. *Khách hàng (Customer / User)*: Tìm kiếm quán, món ăn, đặt hàng, thanh toán trực tuyến PayOS, chat thời gian thực, tư vấn AI thông minh.
  2. *Nhà hàng / Quán ăn (Restaurant / Seller)*: Quản lý cửa hàng, menu món ăn, thiết lập giờ hoạt động, tiếp nhận và chuẩn bị đơn hàng.
  3. *Tài xế giao hàng (Shipper / Driver)*: Nhận đơn giao, bám tuyến thời gian thực trên bản đồ số, theo dõi lịch sử và thống kê doanh thu vận chuyển.
  4. *Quản trị viên (Admin)*: Quản lý người dùng, duyệt hoạt động, quản lý voucher khuyến mãi, giám sát báo cáo doanh thu và xử lý khiếu nại.

---

## 2. Bản đồ cấu trúc thư mục toàn dự án

Dự án được tổ chức rõ ràng thành hai phân hệ độc lập: Ứng dụng di động (`app/`) và Hệ thống máy chủ (`backend/`), kết hợp cùng thư mục tài liệu kiến trúc kỹ thuật (`docs/`):

```text
DACS_3_Composer/
├── app/                                    # Phân hệ Mobile Client (Android Native)
│   ├── build.gradle.kts                    # Cấu hình Gradle module app, dependencies, build flavors
│   ├── google-services.json                # File cấu hình tích hợp dịch vụ Firebase
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Khai báo quyền, Activity, Service nền tảng Android
│       │   ├── java/com/example/dacs_3_composer/
│       │   │   ├── MainActivity.kt         # Entry point điều hướng và khôi phục phiên người dùng
│       │   │   ├── MainApplication.kt      # Application class khởi tạo MapLibre, Cloudinary
│       │   │   ├── core/                   # Các thành phần cốt lõi dùng chung
│       │   │   │   ├── designsystem/       # Hệ thống Design (Theme, Color, Type Material 3)
│       │   │   │   └── model/              # Các Data Models dùng chung toàn ứng dụng
│       │   │   ├── data/                   # Tầng dữ liệu (Data Layer)
│       │   │   │   ├── firebase/           # FCM Push Notification, Realtime Presence, RTDB
│       │   │   │   ├── mapper/             # Ánh xạ Entity/DTO sang Domain Model
│       │   │   │   ├── remote/             # Retrofit API Interfaces, OkHttp Interceptor, Services
│       │   │   │   └── repository/         # Các Repository điều phối dữ liệu nghiệp vụ
│       │   │   └── feature/                # Tầng tính năng theo Module nghiệp vụ (Feature Modules)
│       │   │       ├── admin/              # Phân hệ Quản trị viên (Dashboard, Users, Vouchers, Logs)
│       │   │       ├── auth/               # Phân hệ Xác thực (Login, Register, Role Mapping)
│       │   │       ├── chat/               # Phân hệ Nhắn tin (Conversations, Messages)
│       │   │       ├── customer/           # Phân hệ Khách hàng (Home, Cart, Orders, AI Advisor)
│       │   │       ├── restaurant/         # Phân hệ Nhà hàng (Store Manage, Dishes, Stats)
│       │   │       ├── shared/             # Các thành phần dùng chung (Bản đồ, Geocoding)
│       │   │       └── shipper/            # Phân hệ Tài xế (Active Delivery, Map, Earnings)
│       │   └── res/                        # Tài nguyên Android (Giao diện XML, Drawable, Chuỗi)
│       └── test/                           # Các bài kiểm thử Unit Test (JUnit 4)
│
├── backend/                                # Phân hệ Backend Server (PHP RESTful API)
│   ├── .htaccess                           # Cấu hình URL rewrite điều hướng API
│   ├── runtime.php                         # Khởi tạo runtime môi trường, cache file helper
│   ├── ai/                                 # Module AI thông minh (Gemini & Groq LLM)
│   │   ├── FoodHubSupport.php              # Xử lý hội thoại CSKH, nhận diện cảm xúc và lọc tri thức
│   │   ├── MealAdvisor.php                 # Logic tư vấn bữa ăn, Master System Prompt, Guardrails
│   │   ├── MealChat.php                    # Điều phối hội thoại đa lượt, phân trang món ăn
│   │   ├── MenuKnowledge.php               # Trích xuất và lập chỉ mục thực đơn từ TiDB
│   │   └── meal-advisor-route.php          # Route xử lý API POST /api/ai/meal-advisor
│   ├── api/
│   │   └── index.php                       # Bộ định tuyến API Router chính (348 dòng mã)
│   ├── database/
│   │   ├── connection.php                  # Quản lý kết nối PDO MySQL hỗ trợ TLS & SSL CA
│   │   └── schema.sql                      # Đặc tả DDL chuẩn 32 bảng cơ sở dữ liệu
│   ├── deploy/                             # Script tự động hóa triển khai (Deployment Automation)
│   ├── migration/                          # Các tập lệnh migration cơ sở dữ liệu
│   └── tests/                              # Bộ kiểm thử Unit Test Backend (PHP CLI)
│
└── docs/                                   # Tài liệu kỹ thuật chi tiết của dự án
    ├── ARCHITECTURE.md                     # Tài liệu kiến trúc và công nghệ chi tiết
    ├── WORKFLOWS.md                        # Đặc tả các luồng xử lý nghiệp vụ
    ├── DATABASE.md                         # Đặc tả 32 bảng cơ sở dữ liệu và quan hệ
    ├── API.md                              # Đặc tả toàn bộ API Endpoints
    ├── SESSION_HANDOFF.md                  # Nhật ký tiến độ và bàn giao các phiên làm việc
    └── AI_MEAL_ADVISOR.md                  # Hướng dẫn chi tiết phân hệ AI Advisor
```
