# BẰNG CHỨNG MÃ NGUỒN VÀ TRUY VẾT TRIỂN KHAI (CODE EVIDENCE)

Tài liệu này tổng hợp các bằng chứng đối chiếu trực tiếp giữa các tuyên bố kỹ thuật trong báo cáo và vị trí mã nguồn thực tế đang tồn tại trong dự án FoodHub.

---

## 1. Bằng chứng về Ngăn xếp Công nghệ và Phân hệ

- **Khởi tạo SDK MapLibre và Cloudinary:**
  - Vị trí: `app/src/main/java/com/example/dacs_3_composer/MainApplication.kt`
  - Bằng chứng: Gọi `MapLibre.getInstance(this)` và cấu hình `MediaManager.init(this, config)` khi khởi động ứng dụng.
- **Xác thực Token Firebase phía Android Client:**
  - Vị trí: `app/src/main/java/com/example/dacs_3_composer/data/remote/FirebaseAuthInterceptor.kt`
  - Bằng chứng: Bắt ngoại lệ bất đồng bộ của `Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS)` và đính kèm vào Header `Authorization: Bearer <token>`.
- **Định tuyến vai trò người dùng phía Android:**
  - Vị trí: `app/src/main/java/com/example/dacs_3_composer/MainActivity.kt`
  - Bằng chứng: Lắng nghe trạng thái `AuthViewModel.userRole`, điều hướng tương ứng vào các container:
    - `"user"` -> `MainRouteContainerCustomer`
    - `"restaurant"` -> `MainRouteContainerRestaurant`
    - `"shipper"` -> `MainRouteContainerShipper`
    - `"admin"` -> `MainRouteContainerAdmin`
- **Kết nối CSDL TiDB Cloud qua TLS:**
  - Vị trí: `backend/database/connection.php`
  - Bằng chứng: Thiết lập `PDO::MYSQL_ATTR_SSL_CA` trỏ tới chứng chỉ `certs/isrgrootx1.pem` và cấu hình múi giờ `SET time_zone = '+07:00'`.
- **Xác thực chữ ký Firebase ID Token bằng OpenSSL:**
  - Vị trí: `backend/api/index.php:40-88` (hàm `verifyToken`)
  - Bằng chứng: Tải khóa công khai từ `https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com`, lưu đệm trong cache file và xác thực bằng `openssl_verify($payload, $signature, $key, OPENSSL_ALGO_SHA256)`.
- **Xác thực chữ ký Webhook cổng PayOS:**
  - Vị trí: `backend/api/index.php:132-150`
  - Bằng chứng: Sắp xếp các khóa mảng payload theo thứ tự alphabet và tính băm `hash_hmac('sha256', $signed, $checksum)` để đối chiếu bảo mật.
- **Tư vấn món ăn thông minh bằng Groq / Gemini:**
  - Vị trí: `backend/ai/MealAdvisor.php`, `backend/ai/FoodHubSupport.php`, `backend/ai/meal-advisor-route.php`
  - Bằng chứng: Tiếp nhận yêu cầu tại `POST /api/ai/meal-advisor`, thực hiện phân tích ngân sách, lọc danh sách món ăn từ TiDB và gọi mô hình AI để đưa ra đề xuất có kiểm soát.

---

## 2. Bằng chứng về Xử lý Nghiệp vụ & Khóa CSDL

- **Xử lý xung đột nhận đơn của tài xế (Concurrency Control):**
  - Vị trí: `backend/api/index.php:297-299`
  - Bằng chứng: Sử dụng MySQL `FOR UPDATE` trong Transaction để khóa bản ghi đơn hàng và tài xế:
    `SELECT d.order_id FROM deliveries d JOIN orders o ON o.id=d.order_id WHERE d.order_id=? AND d.driver_id IS NULL AND o.status='accepted' FOR UPDATE`
- **Tự động chốt giá đơn hàng trên máy chủ:**
  - Vị trí: `backend/api/index.php:328-335`
  - Bằng chứng: Lấy đơn giá thực tế từ bảng `dishes`, lấy phí giao hàng từ `config['delivery_fee']`, kiểm tra điều kiện áp dụng voucher trên TiDB và tính tổng tiền cuối cùng, không phụ thuộc vào giá tính của Client.
- **Gửi thông báo đẩy Firebase Cloud Messaging (FCM HTTP v1):**
  - Vị trí: `backend/api/index.php:90-115` (hàm `fcmAccessToken` và `notifyUser`)
  - Bằng chứng: Tự động ký JWT bằng khóa bí mật của file Service Account, lấy OAuth2 Access Token từ Google và gửi thông báo tới các mã FCM Token của người dùng.
