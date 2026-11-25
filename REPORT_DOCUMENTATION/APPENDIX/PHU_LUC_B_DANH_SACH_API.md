# PHỤ LỤC B. DANH SÁCH CHI TIẾT CÁC ĐIỂM CUỐI API (API ENDPOINTS)

Hệ thống cung cấp giao diện RESTful API chuẩn hóa, phản hồi dữ liệu định dạng JSON với mã hóa UTF-8. Base URL hoạt động trên môi trường sản xuất: `https://chinh.alwaysdata.net/food-delivery-api/api/`.

---

## 1. Xác thực và Phân quyền API
- **Header bắt buộc đối với các yêu cầu bảo mật:**
  - `Authorization: Bearer <Firebase_ID_Token>`
  - `Accept: application/json`
  - `Content-Type: application/json`
- **Xác thực:** Backend giải mã chữ ký RS256 qua chứng chỉ Google x509, trích xuất `sub` (Firebase UID) và tra cứu hồ sơ tương ứng trong bảng `users` trên TiDB Cloud.
- **Phân quyền truy cập:** Dựa trên cột `users.role` (`customer`, `seller`, `driver`, `admin`).

---

## 2. Bảng Danh mục API Endpoints Đầy đủ

| Nhóm chức năng | Phương thức | Đường dẫn API | Quyền hạn yêu cầu | Mô tả chi tiết chức năng |
| :--- | :---: | :--- | :--- | :--- |
| **Hệ thống** | `GET` | `/` | Công khai | Kiểm tra tình trạng dịch vụ (Health check) và kết nối CSDL. |
| **Xác thực & Hồ sơ** | `GET` | `/users/me` | Đã đăng nhập | Lấy thông tin hồ sơ của tài khoản hiện tại. |
| | `POST/PATCH`| `/users/me` | Đã đăng nhập | Khởi tạo hoặc cập nhật hồ sơ (Họ tên, SĐT, Địa chỉ, Avatar). |
| | `GET` | `/users` | `admin` | Lấy danh sách toàn bộ người dùng hệ thống. |
| | `GET` | `/users/{uid}` | `admin` / Người chat | Xem thông tin chi tiết một người dùng cụ thể. |
| | `PATCH` | `/users/{uid}` | `admin` | Cập nhật vai trò, trạng thái khóa/kích hoạt tài khoản. |
| | `DELETE`| `/users/{uid}` | `admin` | Xóa mềm tài khoản người dùng (`deleted_at=NOW()`). |
| **Sổ địa chỉ** | `GET` | `/addresses` | Khách hàng | Lấy danh sách sổ địa chỉ nhận hàng của người dùng. |
| | `POST` | `/addresses` | Khách hàng | Thêm địa chỉ mới (kèm tọa độ GPS và nhãn). |
| | `PUT/PATCH`| `/addresses/{id}` | Khách hàng | Chỉnh sửa địa chỉ thuộc sở hữu của người dùng. |
| | `DELETE`| `/addresses/{id}` | Khách hàng | Xóa một địa chỉ khỏi sổ địa chỉ. |
| **Quán ăn** | `GET` | `/stores` | Công khai | Lấy danh sách các quán ăn đang hoạt động. |
| | `GET` | `/stores/{id}` | Công khai | Xem thông tin chi tiết quán ăn (tọa độ, giờ mở cửa). |
| | `GET` | `/stores/mine` | `seller` / `admin` | Lấy thông tin quán ăn thuộc sở hữu của tài khoản hiện tại. |
| | `POST` | `/stores/mine` | `seller` / `admin` | Đăng ký tạo quán ăn mới khi chưa có quán. |
| | `PATCH` | `/stores/mine` | `seller` / `admin` | Cập nhật thông tin quán, giờ mở cửa, ảnh bìa, tọa độ. |
| **Thực đơn Món ăn** | `GET` | `/dishes` | Công khai | Xem danh sách món ăn (có thể lọc theo quán, danh mục). |
| | `GET` | `/dishes?includeUnavailable=1` | `seller` / `admin` | Xem toàn bộ thực đơn bao gồm cả món đã hết hàng. |
| | `POST` | `/dishes` | `seller` / `admin` | Thêm món ăn mới vào quán sở hữu. |
| | `PUT/PATCH`| `/dishes/{id}` | `seller` / `admin` | Chỉnh sửa thông tin món ăn, đơn giá, ảnh, trạng thái bán. |
| | `DELETE`| `/dishes/{id}` | `seller` / `admin` | Xóa mềm món ăn khỏi thực đơn quán. |
| **Voucher Khuyến mãi**| `GET` | `/vouchers` | Đã đăng nhập | Xem danh sách voucher khả dụng (Admin xem toàn bộ). |
| | `POST` | `/vouchers` | `admin` | Tạo mới mã khuyến mãi toàn sàn hoặc theo quán. |
| | `PATCH` | `/vouchers/{id}` | `admin` | Bật/tắt trạng thái kích hoạt của voucher. |
| | `DELETE`| `/vouchers/{id}` | `admin` | Xóa voucher khỏi hệ thống. |
| **Đơn hàng** | `GET` | `/orders` | Đã đăng nhập | Lấy danh sách đơn hàng theo phạm vi sở hữu của từng vai trò. |
| | `GET` | `/orders/{id}` | Đã đăng nhập | Xem chi tiết một đơn hàng kèm snapshot món ăn. |
| | `GET` | `/orders/available` | `driver` | Tài xế xem danh sách các đơn hàng sẵn sàng chờ nhận giao. |
| | `POST` | `/orders` | `customer` | Khách hàng tạo đơn hàng mới (Server chốt giá & voucher). |
| | `PATCH` | `/orders/{id}` | Theo vai trò | Cập nhật trạng thái đơn (`processing`, `shipping`, `completed`...).|
| **Vận chuyển Shipper**| `GET/PATCH`| `/drivers/me` | `driver` | Xem hoặc cập nhật trạng thái làm việc (offline/available/busy). |
| | `POST` | `/deliveries/{orderId}/accept` | `driver` | Tài xế nhận giao đơn hàng (Xử lý khóa chống tranh chấp). |
| **Thanh toán PayOS** | `POST` | `/payments/create-link` | `customer` | Tạo liên kết thanh toán VietQR cho đơn hàng online. |
| | `GET` | `/payments` | `customer` | Xem lịch sử các phiên thanh toán của người dùng. |
| | `POST` | `/payos/webhook` | Cổng PayOS | Webhook nhận thông báo thanh toán thành công (Xác thực HMAC). |
| **Hội thoại & Chat** | `GET` | `/conversations` | Đã đăng nhập | Lấy danh sách các cuộc hội thoại mà người dùng tham gia. |
| | `POST` | `/conversations` | Đã đăng nhập | Khởi tạo cuộc hội thoại mới giữa khách - quán - shipper - admin. |
| | `GET` | `/conversations/{id}/messages` | Thành viên chat | Lấy danh sách 50 tin nhắn gần nhất trong cuộc trò chuyện. |
| | `POST` | `/conversations/{id}/messages` | Thành viên chat | Gửi tin nhắn mới (text/ảnh) hoặc đánh dấu đã đọc. |
| **Thông báo & FCM** | `GET` | `/notifications` | Đã đăng nhập | Lấy danh sách thông báo cá nhân trong hộp thư. |
| | `PATCH` | `/notifications/{id}` | Đã đăng nhập | Đánh dấu một thông báo đã đọc. |
| | `PATCH` | `/notifications/read-all` | Đã đăng nhập | Đánh dấu tất cả thông báo trong hộp thư đã đọc. |
| | `POST` | `/device-tokens` | Đã đăng nhập | Đăng ký hoặc làm mới mã FCM Device Token phục vụ Push. |
| **Tư vấn AI** | `POST` | `/ai/meal-advisor` | `customer` / `admin` | Gửi yêu cầu tư vấn món ăn theo ngân sách và ngữ cảnh chat. |
| **Nhật ký Vận hành** | `GET/POST`| `/activity_logs` | `seller` / `admin` | Ghi nhận hoặc truy vấn nhật ký thao tác quan trọng. |
