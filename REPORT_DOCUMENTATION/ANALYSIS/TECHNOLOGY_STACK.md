# PHÂN TÍCH NGĂN XẾP CÔNG NGHỆ (TECHNOLOGY STACK)

## 1. Phân hệ Ứng dụng Di động (Mobile Client - Android)

| Thành phần / Thư viện | Phiên bản / Cấu hình | Mục đích & Vai trò trong hệ thống | Bằng chứng mã nguồn |
| :--- | :--- | :--- | :--- |
| **Ngôn ngữ lập trình** | Kotlin 2.0.21 | Ngôn ngữ phát triển Native Android, hỗ trợ Null-safety và Coroutines bất đồng bộ | `app/build.gradle.kts` |
| **Giao diện người dùng (UI)** | Jetpack Compose & Material 3 (BOM 2024.09.00) | Xây dựng giao diện khai báo (Declarative UI) hiện đại, thích ứng đa kích thước màn hình | `app/build.gradle.kts:117-122` |
| **Kiến trúc ứng dụng** | MVVM (Model - View - ViewModel) | Tách biệt giao diện (Compose), logic trạng thái (ViewModel/StateFlow) và dữ liệu (Repository) | `core/model`, `feature/*` |
| **Quản lý bất đồng bộ** | Kotlin Coroutines & StateFlow | Xử lý các tác vụ nền, tải dữ liệu mạng và phát luồng dữ liệu thời gian thực cho UI | `ShipperViewModel.kt`, `CartViewModel.kt` |
| **Điều hướng ứng dụng** | Navigation Compose 2.8.5 | Quản lý ngăn xếp điều hướng, định tuyến theo 4 vai trò người dùng | `MainActivity.kt`, `NavigationAdmin.kt` |
| **Mạng & REST Client** | Retrofit 2.9.0 & OkHttp 4.12.0 | Giao tiếp mạng HTTP/RESTful API với Backend, mã hóa chuỗi JSON qua Gson Converter | `data/remote/FoodHubApi.kt` |
| **Xác thực bảo mật** | Firebase Auth & Credential Manager | Xác thực email/mật khẩu và Google ID Token, bảo vệ phiên đăng nhập | `feature/auth/login/LoginViewModel.kt` |
| **Bảo mật API Client** | FirebaseAuthInterceptor | Tự động lấy Firebase ID Token (RS256) gắn vào `Authorization: Bearer <token>` | `data/remote/FirebaseAuthInterceptor.kt` |
| **Dịch vụ Đẩy tin (Push)** | Firebase Cloud Messaging (FCM) | Nhận thông báo tức thời về trạng thái đơn hàng, tin nhắn mới từ máy chủ | `data/firebase/ChatMessagingService.kt` |
| **Dữ liệu Thời gian thực** | Firebase Realtime Database (RTDB) | Quản lý trạng thái hiện diện (Presence), tọa độ GPS tài xế (`tracking/{orderId}`) | `data/firebase/PresenceManager.kt` |
| **Quản lý Đa phương tiện** | Cloudinary Android SDK 3.0.2 & Coil 2.6.0 | Upload ảnh món ăn/quán lên Cloudinary và tải/hiển thị ảnh bất đồng bộ kèm bộ nhớ đệm | `data/remote/ImageUploadService.kt` |
| **Bản đồ số & Tọa độ** | MapLibre Native 13.7.1 & OpenFreeMap | Hiển thị bản đồ vector mã nguồn mở chất lượng cao không yêu cầu phí bản quyền | `feature/shipper/dashboard/components/ShipperMapView.kt` |
| **Định vị & Geocoding** | Play Services Location 21.2.0, Photon & Android Geocoder | Thu nhận GPS thiết bị, tìm kiếm gợi ý địa chỉ và đảo ngược tọa độ thành địa chỉ số nhà | `feature/shared/location/AddressSearch.kt` |
| **Tính toán lộ trình** | OSRM (Open Source Routing Machine) | Tính toán tuyến đường di chuyển tối ưu giữa quán ăn và điểm giao của khách hàng | `ShipperViewModel.fetchOSRMRoute` |

---

## 2. Phân hệ Máy chủ Dịch vụ (Backend Server)

| Thành phần / Công nghệ | Phiên bản / Môi trường | Mục đích & Vai trò trong hệ thống | Bằng chứng mã nguồn |
| :--- | :--- | :--- | :--- |
| **Ngôn ngữ & Môi trường** | PHP 8.2 (Local CLI) / PHP 8.4.27 (Alwaysdata) | Xử lý toàn bộ logic nghiệp vụ, xác thực danh tính, điều phối cơ sở dữ liệu | `backend/api/index.php`, `backend/runtime.php` |
| **Cơ chế định tuyến API** | Custom Lightweight Front-Controller Router | Xử lý request HTTP (GET, POST, PUT, PATCH, DELETE) và trả dữ liệu JSON chuẩn hóa | `backend/api/index.php:125-155` |
| **Xác thực Token Backend** | OpenSSL Verification (RSA RS256) | Xác thực tính hợp lệ của Firebase ID Token qua khóa công khai của Google có bộ nhớ đệm | `backend/api/index.php:40-88` |
| **Hệ quản trị CSDL** | TiDB Cloud 8.0.11-v8.5.3 (MySQL Compatible) | Cụm cơ sở dữ liệu phân tán chuẩn Serverless, tự động mở rộng, mã hóa TLS với ISRG Root X1 | `backend/database/connection.php` |
| **Giao tiếp CSDL** | PDO MySQL với Connection Pooling | Quản lý kết nối an toàn, chống SQL Injection bằng Prepared Statements, session `+07:00` | `backend/database/connection.php:38-75` |
| **Cổng thanh toán** | PayOS Payment Gateway API | Khởi tạo giao dịch thanh toán trực tuyến qua QR chuyển khoản ngân hàng và Webhook HMAC-SHA256 | `backend/api/index.php:280-285`, `backend/deploy/configure-payos.php` |
| **Tư vấn AI thông minh** | Groq API (`openai/gpt-oss-120b`) & Gemini 4 Hydrogen | Tiếp nhận câu hỏi tự do, tư vấn món ăn theo ngân sách, hỗ trợ CSKH tự động | `backend/ai/MealAdvisor.php`, `backend/ai/FoodHubSupport.php` |
| **Gửi thông báo Đẩy** | Google FCM HTTP v1 API | Tự động ký JWT Service Account để lấy Google OAuth2 token và gửi tin push tới thiết bị | `backend/api/index.php:90-115` |
