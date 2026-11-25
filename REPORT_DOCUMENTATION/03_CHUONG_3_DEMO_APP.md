# CHƯƠNG 3. DEMO APP VÀ KẾT QUẢ THỰC NGHIỆM

## 3.1. Cấu trúc cây folder dự án

Dự án FoodHub được tổ chức theo cấu trúc module hóa phân tầng rõ rệt, phân tách độc lập giữa phân hệ ứng dụng khách (Frontend Android) và phân hệ máy chủ dịch vụ (Backend Server).

### 3.1.1. Cấu trúc cây thư mục Phân hệ Android Client (`app/`)

```text
app/src/main/java/com/example/dacs_3_composer/
├── MainActivity.kt                          # Điểm vào chính, điều hướng động theo 4 vai trò
├── MainApplication.kt                       # Application class, khởi tạo MapLibre & Cloudinary
│
├── core/
│   ├── designsystem/                        # Hệ thống thiết kế Material 3
│   │   ├── Color.kt                         # Bảng mã màu chủ đạo: Xanh #2159BC, Nền #F5F8FC
│   │   ├── Theme.kt                         # Cấu hình giao diện MaterialTheme
│   │   └── Type.kt                          # Kiểu chữ Typography
│   └── model/                               # Data Models dùng chung toàn ứng dụng
│       ├── ActivityLog.kt                   # Model nhật ký hoạt động của quán
│       ├── ChatModels.kt                    # Model hội thoại, tin nhắn text và hình ảnh
│       ├── Dish.kt                          # Model chi tiết món ăn
│       ├── DishItem.kt                      # Model món ăn rút gọn trong đơn hàng
│       ├── NotificationItem.kt              # Model thông báo người dùng
│       ├── Order.kt                         # Model đơn hàng và chi tiết các dòng món
│       ├── Payment.kt                       # Model phiên thanh toán PayOS
│       ├── Promotion.kt                     # Model mã voucher khuyến mãi
│       ├── Restaurant.kt                    # Model thông tin nhà hàng/quán ăn
│       ├── RestaurantDetail.kt              # Model chi tiết quán ăn và menu danh mục
│       ├── TopDish.kt                       # Model món ăn bán chạy
│       ├── User.kt                          # Model hồ sơ người dùng và quyền hạn
│       └── UserAddress.kt                   # Model sổ địa chỉ giao nhận hàng
│
├── data/
│   ├── firebase/                            # Dịch vụ tích hợp đám mây Firebase
│   │   ├── ChatMessagingService.kt          # Tiếp nhận và hiển thị thông báo đẩy FCM
│   │   ├── FirebasePushService.kt           # Đăng ký và đồng bộ FCM Device Token
│   │   └── PresenceManager.kt               # Đồng bộ trạng thái trực tuyến Realtime DB
│   ├── mapper/                              # Lớp chuyển đổi DTO sang Domain Model
│   ├── remote/                              # Tầng giao tiếp mạng RESTful API
│   │   ├── FirebaseAuthInterceptor.kt       # OkHttp Interceptor tự động gắn Bearer Token
│   │   ├── FoodHubApi.kt                    # Retrofit Interface khai báo các API Endpoints
│   │   ├── ImageUploadService.kt            # Tải ảnh trực tiếp lên Cloudinary SDK
│   │   └── PaymentApiService.kt             # Khởi tạo giao dịch thanh toán PayOS
│   └── repository/                          # Tầng Repository điều phối dữ liệu
│       ├── ActivityLogRepository.kt         # Quản lý nhật ký hoạt động
│       ├── ChatRepository.kt                # Quản lý hội thoại và tin nhắn
│       ├── MealAdvisorRepository.kt         # Quản lý tương tác tư vấn ẩm thực AI
│       ├── MenuRepository.kt                # Quản lý thực đơn và danh mục món
│       ├── PaymentRepository.kt             # Quản lý dữ liệu thanh toán
│       ├── RestaurantStoreRepository.kt     # Quản lý thông tin quán ăn
│       └── UserRepository.kt                # Quản lý hồ sơ người dùng và sổ địa chỉ
│
└── feature/                                 # Các phân hệ chức năng theo vai trò
    ├── admin/                               # Phân hệ Quản trị viên
    │   ├── MainRouteContainerAdmin.kt       # Container điều hướng Admin
    │   ├── NavigationAdmin.kt               # Định nghĩa các route màn hình Admin
    │   ├── analytics/                       # Bảng điều khiển Dashboard và biểu đồ doanh thu
    │   ├── complaint/                       # Quản lý khiếu nại và giám sát hệ thống
    │   ├── customer/                        # Quản lý tài khoản người dùng và phân quyền
    │   └── voucher/                         # Quản lý mã khuyến mãi toàn hệ thống
    ├── auth/                                # Phân hệ Xác thực
    │   ├── login/                           # Màn hình đăng nhập Email & Google Sign-In
    │   └── register/                        # Màn hình đăng ký tài khoản mới và chọn Role
    ├── chat/                                # Phân hệ Nhắn tin Trực tuyến
    │   ├── ConversationListScreen.kt        # Danh sách các cuộc trò chuyện
    │   └── ChatDetailScreen.kt              # Khung chat nhắn tin và chia sẻ hình ảnh
    ├── customer/                            # Phân hệ Khách hàng
    │   ├── MainRouteContainerCustomer.kt    # Khung điều hướng thanh đáy (BottomBar)
    │   ├── home/                            # Màn hình Trang chủ, Banner Voucher, Danh sách quán
    │   ├── cart/                            # Màn hình Giỏ hàng và chỉnh sửa số lượng
    │   ├── order/                           # Màn hình Thanh toán Checkout và Chi tiết đơn
    │   ├── ai/                              # Màn hình Trợ lý AI "Hôm nay ăn gì?"
    │   └── profile/                         # Quản lý thông tin cá nhân và sổ địa chỉ
    ├── restaurant/                          # Phân hệ Nhà hàng / Quán ăn
    │   ├── MainRouteContainerRestaurant.kt  # Khung điều hướng phân hệ Nhà hàng
    │   ├── store/                           # Quản lý thông tin cửa hàng, giờ mở/đóng cửa
    │   ├── menu/                            # Quản lý thực đơn, Thêm/Sửa/Xóa món ăn
    │   ├── orders/                          # Tiếp nhận và cập nhật tiến độ chế biến đơn
    │   └── profile/                         # Cài đặt quán và xem nhật ký hoạt động
    ├── shared/location/                     # Thành phần dùng chung: Bản đồ & Vị trí
    │   ├── AddressLocationPicker.kt         # Thành phần kéo ghim bản đồ chọn tọa độ
    │   ├── AddressSearch.kt                 # Tìm kiếm địa chỉ qua máy chủ Photon
    │   └── DeviceAddressLookup.kt           # Lấy vị trí GPS thiết bị qua FusedLocation
    └── shipper/                             # Phân hệ Tài xế giao hàng
        ├── MainRouteContainerShipper.kt     # Khung điều hướng phân hệ Tài xế
        ├── dashboard/                       # Bảng tin nhận đơn, Bản đồ số MapLibre
        ├── orders/                          # Danh sách đơn đang giao và lịch sử chuyến đi
        ├── earnings/                        # Thống kê thu nhập giao hàng theo tuần/tháng
        └── profile/                         # Quản lý phương tiện và thông tin tài xế
```

*Hình 3.1: Cấu trúc cây thư mục phân hệ Android Client*

---

### 3.1.2. Cấu trúc cây thư mục Phân hệ Backend Server (`backend/`)

```text
backend/
├── .htaccess                                # Tự động chuyển hướng toàn bộ request vào api/index.php
├── runtime.php                              # Khởi tạo runtime và thư mục cache an toàn ngoài web root
├── api/
│   └── index.php                            # Bộ định tuyến Front Controller xử lý toàn bộ 32 bảng
├── ai/
│   ├── FoodHubSupport.php                   # CSKH thông minh: Nhận diện cảm xúc & Guardrails
│   ├── MealAdvisor.php                      # Master System Prompt và logic tư vấn món ăn
│   ├── MealChat.php                         # Điều phối hội thoại đa lượt và phân trang thực đơn
│   ├── MenuKnowledge.php                    # Trích xuất và lập chỉ mục thực đơn trực tiếp từ TiDB
│   └── meal-advisor-route.php               # Điểm cuối xử lý API POST /api/ai/meal-advisor
├── certs/
│   └── isrgrootx1.pem                       # Chứng chỉ số CA xác thực kết nối bảo mật TLS với TiDB Cloud
├── database/
│   ├── connection.php                       # Quản lý kết nối PDO MySQL có cơ chế Pooling
│   └── schema.sql                           # File DDL chuẩn xác 32 bảng cơ sở dữ liệu
├── deploy/                                  # Công cụ tự động hóa triển khai hệ thống
│   ├── alwaysdata.py                        # Triển khai mã nguồn qua giao thức SFTP/SSH an toàn
│   ├── check-services.php                   # Kiểm tra trạng thái TiDB, FCM, PayOS
│   ├── configure-payos.php                  # Thiết lập và xác thực Webhook PayOS
│   └── deploy-meal-advisor.py               # Triển khai module AI và đồng bộ API Key riêng
├── migration/                               # Các tập lệnh cập nhật cấu trúc cơ sở dữ liệu
│   ├── 2026_11_12_shipper_profile.sql       # Bổ sung cột address_line vào bảng users
│   ├── 2026_11_20_home_vouchers.sql         # Cập nhật mã voucher hiển thị trang chủ
│   ├── 2026_11_20_voucher_hero_images.sql   # Cập nhật ảnh banner khuyến mãi hero
│   └── 2026_11_20_voucher_image.sql         # Bổ sung cột image_url cho bảng vouchers
└── tests/                                   # Bộ kiểm thử Unit Test Backend
    ├── database_connection_test.php         # Kiểm tra kết nối TLS và timezone CSDL
    ├── foodhub_human_ai_test.php            # Kiểm tra khả năng xử lý cảm xúc và guardrails
    ├── foodhub_support_test.php             # Kiểm tra luồng hủy đơn và bàn giao nhân viên thật
    ├── meal_advisor_fast_test.php           # Kiểm thử hiệu năng xử lý bộ lọc thực đơn
    ├── meal_advisor_test.php                # Kiểm thử toàn diện module tư vấn bữa ăn
    ├── meal_chat_test.php                   # Kiểm thử phân trang 8 món và nhớ ngữ cảnh
    └── menu_knowledge_test.php              # Kiểm thử trích xuất kiến thức thực đơn
```

*Hình 3.2: Cấu trúc cây thư mục phân hệ Backend Server*

---

## 3.2. Giao diện và các luồng tương tác của ứng dụng

Ứng dụng FoodHub sở hữu giao diện người dùng hiện đại, đồng bộ màu sắc thương hiệu Trắng - Xanh dương (`#2159BC`), bố cục trực quan và được tối ưu hóa cho từng vai trò người dùng cụ thể.

### 3.2.1. Phân hệ Khách hàng (Customer Experience)

#### 1. Màn hình Chào mừng, Đăng nhập và Đăng ký
- **Mô tả giao diện:**
  - Thiết kế tối giản, hiện đại với logo thương hiệu FoodHub nổi bật trên nền trắng.
  - Biểu mẫu đăng nhập cung cấp ô nhập Email, Mật khẩu có nút ẩn/hiện mật khẩu, nút "Đăng nhập" màu xanh dương nổi bật và nút "Đăng nhập với Google" tiện lợi.
  - Biểu mẫu đăng ký cho phép người dùng chọn vai trò ban đầu (Khách hàng, Quán ăn hoặc Tài xế), kiểm tra mật khẩu trùng khớp và điều khoản dịch vụ trước khi tạo tài khoản.
- **Hoạt động nghiệp vụ:** Sau khi người dùng xác thực thành công qua Firebase Authentication, ứng dụng tự động đồng bộ tài khoản với CSDL TiDB Cloud qua API `POST /api/users/me` và điều hướng người dùng vào giao diện tương ứng.

#### 2. Màn hình Trang chủ Ẩm thực (Home Screen)
- **Mô tả giao diện:**
  - Thanh trên cùng hiển thị địa chỉ giao hàng hiện tại của khách hàng kèm icon định vị, nút chuông thông báo và thanh tìm kiếm món ăn nhanh.
  - Khu vực Banner Khuyến mãi (Voucher Hero Banner): Thiết kế dạng thẻ bo góc lớn (chiều cao 240dp), màu nền gradient bắt mắt, hiển thị hình ảnh món ăn đặc trưng xoay nghiêng 12 độ nổi bật (Pizza, Bánh mì, Cơm tấm, Trà sữa, Bún bò) cùng mã voucher và hạn dùng. Người dùng có thể vuốt ngang mượt mà để duyệt các mã giảm giá.
  - Thẻ tiện ích AI "Hôm nay ăn gì?": Nằm ngay dưới banner, mời gọi người dùng trò chuyện với Trợ lý ảo để nhận gợi ý món ăn theo ngân sách.
  - Danh mục món ăn nhanh: Các biểu tượng tròn trực quan (Cơm, Món nước, Trà sữa, Ăn vặt).
  - Danh sách Quán ăn đang hoạt động: Hiển thị dạng thẻ gồm ảnh đại diện, tên quán, điểm đánh giá sao, thời gian chuẩn bị dự kiến và khoảng cách.
  - Danh sách Món ăn gợi ý: Hiển thị dạng lưới 2 cột gồm ảnh món, tên món, đơn giá niêm yết và nút thêm nhanh vào giỏ hàng.

#### 3. Màn hình Trợ lý Ẩm thực AI ("Hôm nay ăn gì?")
- **Mô tả giao diện:**
  - Thiết kế theo phong cách khung chat trò chuyện hiện đại.
  - Hàng gợi ý nhanh phía trên: Các chip chọn nhanh sở thích (Món nước, Cơm no bụng, Món chay, Không cay).
  - Thanh nhập ngân sách: Cho phép người dùng tùy chỉnh hạn mức chi tiêu (mặc định 70.000đ) kèm công tắc "Tính cả phí giao hàng".
  - Khung nội dung hội thoại: Hiển thị các bong bóng chat tự nhiên của Trợ lý AI. Khi AI gợi ý món ăn, kết quả được hiển thị dưới dạng tối đa 3 thẻ món ăn thực tế (gồm ảnh món thật từ Cloudinary, tên món, tên quán, đơn giá và tổng chi phí dự kiến kèm phí ship).
  - Tương tác: Người dùng có thể bấm trực tiếp vào nút "Xem thực đơn" trên thẻ món để chuyển thẳng đến quán ăn và thêm món vào giỏ hàng.

#### 4. Màn hình Giỏ hàng và Màn hình Chốt đơn (Cart & Checkout)
- **Mô tả giao diện:**
  - *Màn hình Giỏ hàng:* Hiển thị danh sách các món ăn đã chọn, hình ảnh thu nhỏ, đơn giá, nút tăng/giảm số lượng (+/-) và nút xóa món. Phía dưới hiển thị tổng tiền tạm tính và nút "Mua hàng".
  - *Màn hình Chốt đơn (Checkout):* Hiển thị khối thông tin người nhận (Họ tên, SĐT, Địa chỉ có thể bấm để thay đổi); khối danh sách món ăn kèm đơn giá; khối chọn mã Voucher giảm giá; khối chọn phương thức thanh toán (Tiền mặt COD hoặc Chuyển khoản VietQR PayOS); khối tổng kết chi phí (Tiền món + Phí giao hàng - Giảm giá = Tổng thanh toán).

#### 5. Màn hình Thanh toán VietQR qua PayOS
- **Mô tả giao diện:**
  - Khi khách hàng chọn thanh toán PayOS và bấm "Đặt hàng", ứng dụng tự động mở Chrome Custom Tabs hiển thị trang thanh toán chuẩn của cổng PayOS.
  - Trang hiển thị mã VietQR động, logo ngân hàng, số tiền chính xác từng đồng và nội dung chuyển khoản đặc thù (ví dụ: `FH261120A1B2`).
  - Khách hàng chỉ cần chụp màn hình hoặc dùng app ngân hàng quét mã để thanh toán tức thì mà không cần nhập số tài khoản thủ công.

#### 6. Màn hình Theo dõi Đơn hàng và Bản đồ Lộ trình (Order Tracking Screen)
- **Mô tả giao diện:**
  - Nửa trên màn hình tích hợp bản đồ số MapLibre Native, hiển thị vị trí quán ăn, vị trí khách hàng và biểu tượng xe máy của tài xế đang di chuyển.
  - Nửa dưới hiển thị thanh tiến trình trực quan với các mốc trạng thái: Đã đặt hàng -> Quán đang chuẩn bị -> Tài xế đã nhận -> Đang giao hàng -> Đã hoàn tất.
  - Hiển thị thông tin tài xế giao hàng (Tên, Biển số xe) và nút gọi điện thoại hoặc nhắn tin chat trực tiếp với tài xế.

---

### 3.2.2. Phân hệ Nhà hàng (Restaurant Store Management)

#### 1. Màn hình Quản lý Cửa hàng (Store Management)
- **Mô tả giao diện:**
  - Hiển thị thông tin tổng quan của quán: Tên thương hiệu, ảnh bìa, ảnh logo, địa chỉ và số hotline.
  - Công tắc Trạng thái mở cửa (`isOpen`): Cho phép chủ quán chủ động bật/tắt nhận đơn chỉ với một chạm.
  - Cài đặt thời gian hoạt động: Thiết lập giờ mở cửa (`open_time`) và giờ đóng cửa (`close_time`).
  - Lối tắt nhanh vào các mục: Quản lý thực đơn, Xem đơn hàng hiện tại, Lịch sử hoạt động và Cài đặt thông báo.

#### 2. Màn hình Quản lý Thực đơn Món ăn (Menu Management)
- **Mô tả giao diện:**
  - Danh sách toàn bộ các món ăn của quán được phân loại theo từng danh mục.
  - Mỗi dòng món ăn hiển thị: Ảnh chụp thực tế, tên món, đơn giá, trạng thái Còn hàng / Tạm hết hàng, nút Sửa và nút Xóa.
  - Hộp thoại Thêm/Sửa món ăn: Cho phép nhập tên món, mô tả, chọn danh mục, nhập đơn giá, chọn ảnh từ thư viện thiết bị (hệ thống tự động tải lên Cloudinary và lấy URL) và bấm "Lưu món ăn".

#### 3. Màn hình Tiếp nhận và Xử lý Đơn hàng của Quán
- **Mô tả giao diện:**
  - Phân loại đơn hàng theo các tab: Đơn mới (`pending`), Đang chuẩn bị (`processing`), Đã sẵn sàng (`accepted`) và Đã hoàn tất (`completed`).
  - Khi có đơn mới phát sinh, ứng dụng phát âm thanh thông báo và hiển thị thẻ đơn hàng nổi bật kèm chi tiết các món khách đặt và ghi chú.
  - Chủ quán nhấn nút "Xác nhận & Nấu" để chuyển sang `processing`; khi nấu xong, nhấn nút "Sẵn sàng giao" (`accepted`) để hệ thống điều phối tài xế đến nhận món.

---

### 3.2.3. Phân hệ Tài xế (Shipper Delivery & Route Map)

#### 1. Màn hình Bảng tin Nhận đơn (Shipper Dashboard)
- **Mô tả giao diện:**
  - Thanh trạng thái trên cùng: Công tắc chuyển đổi "Sẵn sàng nhận đơn" (`available`) hoặc "Tạm nghỉ" (`offline`).
  - Khối tóm tắt nhanh: Thu nhập hôm nay, số cuốc xe đã hoàn thành.
  - Danh sách Đơn hàng khả dụng (Available Orders Pool): Hiển thị các đơn hàng đang ở trạng thái `accepted` trong khu vực, thông tin tên quán, địa chỉ lấy món, địa chỉ giao hàng và phí ship nhận được.
  - Nút "Nhận đơn": Kích hoạt cơ chế khóa CSDL để nhận chuyến giao.

#### 2. Màn hình Lộ trình Di chuyển Chi tiết (Shipper Navigation Screen)
- **Mô tả giao diện:**
  - Bản đồ số MapLibre Native toàn màn hình, hiển thị tuyến đường vẽ bằng đường kẻ polyline màu xanh từ động cơ OSRM.
  - Chặng 1: Dẫn đường từ vị trí hiện tại của tài xế đến Quán ăn -> Tài xế đến nơi bấm "Đã lấy món" (chuyển đơn sang `shipping`).
  - Chặng 2: Dẫn đường từ Quán ăn đến Địa chỉ khách hàng -> Tài xế đến nơi giao thức ăn và bấm "Hoàn tất giao hàng" (chuyển đơn sang `completed`).
  - Tọa độ GPS của tài xế được phát liên tục lên Firebase Realtime Database trong suốt hành trình.

#### 3. Màn hình Thống kê Thu nhập Tài xế (Shipper Earnings Screen)
- **Mô tả giao diện:**
  - Bộ chọn thời gian linh hoạt: Xem theo Tuần (từ thứ Hai đến Chủ nhật) hoặc theo Tháng.
  - Thẻ tổng quan tài chính: Tổng thu nhập thực nhận trong kỳ (tổng `shippingFee` của các đơn `completed`), tổng số đơn đã giao, thu nhập trung bình mỗi đơn, ngày có doanh thu cao nhất.
  - Biểu đồ cột trực quan: Trực quan hóa thu nhập từng ngày trong tuần/tháng; tài xế có thể chạm vào từng cột để xem danh sách chi tiết các đơn hàng hoàn tất trong ngày đó.

---

### 3.2.4. Phân hệ Quản trị viên (Admin Analytics Dashboard)

#### 1. Màn hình Bảng điều khiển Doanh thu (Admin Analytics Dashboard)
- **Mô tả giao diện:**
  - Bộ lọc thời gian chuẩn lịch thực: Theo Tuần hoặc Theo Tháng.
  - Thẻ chỉ số tổng quan (KPI Cards): Tổng doanh thu toàn sàn (tổng giá trị đơn hoàn tất), tổng số đơn đặt, số đơn hoàn thành, số đơn bị hủy.
  - Biểu đồ Doanh thu (Revenue Chart): Biểu đồ đường lũy kế thể hiện xu hướng tăng trưởng doanh thu so với cùng kỳ trước đó.
  - Danh sách đơn hàng mới nhất: Hiển thị 5 đơn hàng phát sinh gần nhất, chạm vào để xem đầy đủ chi tiết đơn hàng thực tế.

#### 2. Màn hình Quản lý Người dùng (User Management)
- **Mô tả giao diện:**
  - Danh sách toàn bộ tài khoản trong hệ thống kèm thông tin Họ tên, Email, Số điện thoại, Vai trò hiện tại (User, Restaurant, Shipper, Admin) và Trạng thái (Hoạt động / Tạm khóa).
  - Thanh tìm kiếm tài khoản theo tên hoặc email.
  - Thao tác quản trị: Cho phép sửa vai trò tài khoản, tạm khóa tài khoản (`suspended`) khi phát hiện vi phạm và kích hoạt lại tài khoản.

#### 3. Màn hình Quản lý Voucher Khuyến mãi (Voucher Management)
- **Mô tả giao diện:**
  - Danh sách toàn bộ các mã voucher đang hoạt động hoặc đã hết hạn trên hệ thống.
  - Nút "Thêm Voucher mới": Mở biểu mẫu tạo mã gồm Mã code, Tiêu đề, Mô tả, Loại giảm (Phần trăm, Tiền mặt, Miễn phí ship), Giá trị giảm, Giảm tối đa, Giá trị đơn tối thiểu, Giới hạn lượt dùng và Ngày hiệu lực.
  - Cho phép bật/tắt kích hoạt voucher hoặc xóa voucher khỏi hệ thống.
