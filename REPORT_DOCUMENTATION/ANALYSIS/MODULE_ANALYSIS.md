# PHÂN TÍCH CHI TIẾT CÁC MODULE CHỨC NĂNG (MODULE ANALYSIS)

Dự án FoodHub được thiết kế theo hướng module hóa cao độ dựa trên 4 vai trò tác nhân chính và các dịch vụ dùng chung.

---

## 1. Module Xác thực và Hồ sơ (Authentication & Profile Module)
- **Vị trí trong mã nguồn:** `feature/auth/`, `data/remote/FirebaseAuthInterceptor.kt`, `data/repository/UserRepository.kt`, `backend/api/index.php (route users/me)`.
- **Trách nhiệm:** 
  - Xác thực người dùng qua Email/Password hoặc Google Sign-In qua Android Credential Manager.
  - Đồng bộ tài khoản Firebase Authentication sang bản ghi người dùng trong CSDL TiDB (`users`).
  - Phân loại và ánh xạ vai trò: MySQL `customer` -> Android `user`, `seller` -> `restaurant`, `driver` -> `shipper`, `admin` -> `admin`.
  - Quản lý thông tin cá nhân: Cập nhật họ tên, số điện thoại, địa chỉ mặc định, ảnh đại diện tải lên Cloudinary.

---

## 2. Module Khách hàng (Customer Experience Module)
- **Vị trí trong mã nguồn:** `feature/customer/`, `feature/customer/home/`, `feature/customer/cart/`, `feature/customer/order/`, `feature/customer/ai/`.
- **Trách nhiệm:**
  - *Khám phá ẩm thực (Home & Search)*: Hiển thị danh sách quán ăn đang hoạt động, danh sách món ăn đề xuất, banner khuyến mãi vuốt ngang, tìm kiếm món ăn theo từ khóa và danh mục.
  - *Giỏ hàng (Cart)*: Lưu trữ danh sách món ăn đã chọn trong phiên làm việc, tự động kiểm tra điều kiện cùng quán ăn, tính toán tạm tính đơn giá món.
  - *Đặt hàng & Thanh toán (Checkout & Order)*: Chọn địa chỉ giao hàng (sử dụng GPS hoặc tìm kiếm Photon), chọn voucher giảm giá khả dụng, chọn phương thức thanh toán (Tiền mặt COD hoặc Ví PayOS).
  - *Theo dõi đơn hàng (Order Tracking)*: Theo dõi tiến trình đơn hàng (Chờ xác nhận -> Quán chuẩn bị -> Sẵn sàng giao -> Đang giao -> Hoàn tất), hiển thị bản đồ MapLibre bám theo tọa độ di chuyển của Shipper.
  - *Trợ lý ẩm thực AI (Meal Advisor)*: Chat thông minh với trợ lý AI (Groq/Gemini), tự động phân tích ngân sách, lọc món nước/món khô/cơm/chay và đưa ra thẻ món gợi ý bấm để đặt trực tiếp.

---

## 3. Module Nhà hàng (Restaurant Management Module)
- **Vị trí trong mã nguồn:** `feature/restaurant/`, `feature/restaurant/store/`, `feature/restaurant/menu/`, `feature/restaurant/orders/`, `feature/restaurant/profile/`.
- **Trách nhiệm:**
  - *Hồ sơ quán ăn*: Quản lý thương hiệu (`merchants`), tên quán, mô tả, giờ mở cửa/đóng cửa, định vị tọa độ lấy hàng trên bản đồ, tải ảnh bìa và logo.
  - *Quản lý thực đơn (Dish CRUD)*: Thêm mới món ăn, chỉnh sửa giá bán, tải ảnh món ăn lên Cloudinary, bật/tắt trạng thái món còn hàng hoặc tạm ngừng bán, phân loại món theo danh mục.
  - *Tiếp nhận và chuẩn bị đơn*: Nhận thông báo tức thời khi có đơn hàng mới phát sinh, chuyển trạng thái đơn sang Đang chuẩn bị (`processing`) và Đã sẵn sàng (`accepted`) để tài xế đến nhận.
  - *Thống kê hoạt động*: Theo dõi doanh số theo ngày, số lượng đơn hoàn tất, nhật ký hoạt động của quán (`activity_logs`).

---

## 4. Module Tài xế (Shipper Delivery Module)
- **Vị trí trong mã nguồn:** `feature/shipper/`, `feature/shipper/dashboard/`, `feature/shipper/orders/`, `feature/shipper/earnings/`, `feature/shipper/profile/`.
- **Trách nhiệm:**
  - *Bật/Tắt trạng thái hoạt động*: Chuyển đổi trạng thái làm việc giữa Ngoại tuyến (`offline`), Sẵn sàng nhận đơn (`available`) và Đang bận (`busy`).
  - *Tiếp nhận đơn hàng (Order Acceptance)*: Xem danh sách các đơn hàng ở trạng thái `accepted` chưa có người nhận trong khu vực; thao tác nhận đơn sử dụng khóa giao dịch (Transaction Lock) trên TiDB để tránh xung đột hai tài xế cùng nhận một đơn.
  - *Điều hướng & Định vị lộ trình*: Hiển thị bản đồ MapLibre Native kết hợp tuyến đường OSRM. Tự động vẽ lộ trình từ vị trí hiện tại đến Quán lấy món, sau đó từ Quán đến Điểm giao khách hàng. Tọa độ GPS được phát lên Firebase Realtime Database (`tracking/{orderId}`).
  - *Báo cáo thu nhập (Earnings Summary)*: Tổng hợp thu nhập từ phí giao hàng (`shippingFee`) của các đơn hàng đã hoàn thành (`COMPLETED`) dựa trên mốc thời gian thực tế `completedAt`, trực quan hóa biểu đồ theo tuần và theo tháng.

---

## 5. Module Quản trị viên (Admin Management Module)
- **Vị trí trong mã nguồn:** `feature/admin/`, `feature/admin/analytics/`, `feature/admin/customer/`, `feature/admin/voucher/`, `feature/admin/complaint/`.
- **Trách nhiệm:**
  - *Bảng điều khiển & Thống kê doanh thu (Analytics Dashboard)*: Báo cáo tổng doanh thu hệ thống từ các đơn hàng hoàn tất, thống kê tổng số đơn theo tuần/tháng, tỷ lệ hủy đơn, biểu đồ lũy kế so sánh cùng kỳ.
  - *Quản lý người dùng (User Management)*: Duyệt danh sách toàn bộ tài khoản, tìm kiếm, chỉnh sửa vai trò (Role), tạm khóa hoặc kích hoạt tài khoản vi phạm chính sách.
  - *Quản lý Voucher Khuyến mãi (Voucher Management)*: Tạo mới các mã voucher giảm giá (theo phần trăm, số tiền cố định, miễn phí vận chuyển), cài đặt ngày bắt đầu/kết thúc, giới hạn lượt dùng và mức chi tiêu tối thiểu.
  - *Giám sát hệ thống & Xử lý khiếu nại*: Tiếp nhận các cuộc trò chuyện hỗ trợ (`conversations.type='support'`), xem nhật ký vận hành hệ thống.

---

## 6. Module Nhắn tin & Chăm sóc khách hàng (Chat & Communication Module)
- **Vị trí trong mã nguồn:** `feature/chat/`, `backend/api/index.php (resource conversations)`, `backend/ai/FoodHubSupport.php`.
- **Trách nhiệm:**
  - Thiết lập phòng trò chuyện giữa Khách hàng - Quán ăn, Khách hàng - Tài xế hoặc Khách hàng - Bộ phận hỗ trợ.
  - Gửi và nhận tin nhắn văn bản kèm hình ảnh qua Cloudinary.
  - Tự động cập nhật trạng thái đã xem (`is_seen`) và đã phát (`is_delivered`).
  - Tích hợp phát hiện trạng thái gõ phím (Typing) qua Firebase Realtime Database và thông báo tin nhắn mới qua FCM Push Notification.
