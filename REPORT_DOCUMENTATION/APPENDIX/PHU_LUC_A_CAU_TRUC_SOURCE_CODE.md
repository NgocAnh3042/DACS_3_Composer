# PHỤ LỤC A. CẤU TRÚC CHI TIẾT MÃ NGUỒN DỰ ÁN FOODHUB

Phụ lục này cung cấp sơ đồ cây thư mục chi tiết của toàn bộ mã nguồn dự án FoodHub, bao gồm phân hệ Android Client và Backend Server.

---

## 1. Cây thư mục Phân hệ Android Client (`app/`)

```text
app/src/main/java/com/example/dacs_3_composer/
├── MainActivity.kt                          # Điểm vào chính, điều hướng theo 4 vai trò
├── MainApplication.kt                       # Application class, khởi tạo MapLibre & Cloudinary
│
├── core/
│   ├── designsystem/                        # Hệ thống thiết kế giao diện Material 3
│   │   ├── Color.kt                         # Bảng mã màu chủ đạo (Xanh #2159BC, Trắng #F5F8FC)
│   │   ├── Theme.kt                         # Khai báo MaterialTheme và Dark/Light color schemes
│   │   └── Type.kt                          # Cấu hình kiểu chữ Typography
│   └── model/                               # Data Models dùng chung
│       ├── ActivityLog.kt                   # Model nhật ký hoạt động
│       ├── ChatModels.kt                    # Model hội thoại và tin nhắn
│       ├── Dish.kt                          # Model món ăn chi tiết
│       ├── DishItem.kt                      # Model món ăn tóm tắt
│       ├── NotificationItem.kt              # Model thông báo người dùng
│       ├── Order.kt                         # Model đơn hàng và chi tiết đơn
│       ├── Payment.kt                       # Model thanh toán PayOS
│       ├── Promotion.kt                     # Model mã giảm giá (Voucher)
│       ├── Restaurant.kt                    # Model thông tin nhà hàng/quán ăn
│       ├── RestaurantDetail.kt              # Model chi tiết cửa hàng và thực đơn
│       ├── TopDish.kt                       # Model món ăn bán chạy
│       ├── User.kt                          # Model hồ sơ người dùng và vai trò
│       └── UserAddress.kt                   # Model địa chỉ giao nhận hàng
│
├── data/
│   ├── firebase/                            # Dịch vụ tích hợp Firebase
│   │   ├── ChatMessagingService.kt          # Xử lý thông báo đẩy Firebase Cloud Messaging
│   │   ├── FirebasePushService.kt           # Đồng bộ Device Token lên máy chủ
│   │   └── PresenceManager.kt               # Theo dõi trạng thái Online/Offline qua Realtime DB
│   ├── mapper/                              # Lớp chuyển đổi dữ liệu DTO -> Model
│   ├── remote/                              # Tầng giao tiếp mạng HTTP
│   │   ├── FirebaseAuthInterceptor.kt       # OkHttp Interceptor tự động gắn Firebase Bearer Token
│   │   ├── FoodHubApi.kt                    # Retrofit Interface khai báo các API Endpoints
│   │   ├── ImageUploadService.kt            # Dịch vụ upload ảnh lên Cloudinary
│   │   └── PaymentApiService.kt             # Dịch vụ khởi tạo link thanh toán PayOS
│   └── repository/                          # Tầng Repository điều phối dữ liệu
│       ├── ActivityLogRepository.kt         # Quản lý nhật ký hoạt động
│       ├── ChatRepository.kt                # Quản lý hội thoại và tin nhắn
│       ├── MealAdvisorRepository.kt         # Quản lý truy vấn tư vấn món ăn AI
│       ├── MenuRepository.kt                # Quản lý danh mục và món ăn của quán
│       ├── PaymentRepository.kt             # Quản lý các giao dịch thanh toán
│       ├── RestaurantStoreRepository.kt     # Quản lý thông tin quán ăn
│       └── UserRepository.kt                # Quản lý hồ sơ và sổ địa chỉ
│
└── feature/                                 # Các phân hệ tính năng theo vai trò
    ├── admin/                               # Phân hệ Quản trị viên
    │   ├── MainRouteContainerAdmin.kt       # Container điều hướng Admin
    │   ├── NavigationAdmin.kt               # Định nghĩa các tuyến đường Admin
    │   ├── analytics/                       # Màn hình Dashboard và biểu đồ doanh thu
    │   ├── complaint/                       # Xử lý khiếu nại và giám sát vận hành
    │   ├── customer/                        # Quản lý danh sách tài khoản người dùng
    │   └── voucher/                         # Quản lý mã khuyến mãi toàn sàn
    ├── auth/                                # Phân hệ Đăng nhập & Đăng ký
    │   ├── login/                           # Đăng nhập Email/Password & Google Sign-In
    │   └── register/                        # Đăng ký tài khoản mới và chọn vai trò
    ├── chat/                                # Phân hệ Nhắn tin Real-time
    │   ├── ConversationListScreen.kt        # Danh sách các cuộc trò chuyện
    │   └── ChatDetailScreen.kt              # Khung chat nhắn tin và gửi ảnh
    ├── customer/                            # Phân hệ Khách hàng
    │   ├── MainRouteContainerCustomer.kt    # Container thanh điều hướng đáy (BottomBar)
    │   ├── home/                            # Màn hình Trang chủ, Banner Voucher, Danh sách quán
    │   ├── cart/                            # Quản lý Giỏ hàng và tính tiền
    │   ├── order/                           # Đặt hàng, Thanh toán, Chi tiết đơn hàng
    │   ├── ai/                              # Màn hình Chat tư vấn món ăn "Hôm nay ăn gì?"
    │   └── profile/                         # Quản lý thông tin cá nhân và sổ địa chỉ
    ├── restaurant/                          # Phân hệ Nhà hàng / Quán ăn
    │   ├── MainRouteContainerRestaurant.kt  # Container điều hướng Nhà hàng
    │   ├── store/                           # Quản lý thông tin cửa hàng, giờ hoạt động
    │   ├── menu/                            # Quản lý danh sách món ăn, thêm/sửa món
    │   ├── orders/                          # Tiếp nhận và cập nhật tiến độ đơn hàng
    │   └── profile/                         # Cài đặt quán và xem nhật ký hoạt động
    ├── shared/location/                     # Thành phần dùng chung: Bản đồ & Định vị
    │   ├── AddressLocationPicker.kt         # Thành phần kéo ghim bản đồ chọn tọa độ
    │   ├── AddressSearch.kt                 # Tìm kiếm gợi ý địa chỉ qua Photon
    │   └── DeviceAddressLookup.kt           # Tra cứu vị trí GPS hiện tại qua FusedLocation
    └── shipper/                             # Phân hệ Tài xế giao hàng
        ├── MainRouteContainerShipper.kt     # Container điều hướng Tài xế
        ├── dashboard/                       # Bảng tin nhận đơn, Bản đồ số MapLibre
        ├── orders/                          # Đơn đang giao và lịch sử các chuyến đi
        ├── earnings/                        # Thống kê thu nhập giao hàng theo tuần/tháng
        └── profile/                         # Thông tin tài xế và quản lý phương tiện
```

---

## 2. Cây thư mục Phân hệ Backend API (`backend/`)

```text
backend/
├── .htaccess                                # Định tuyến toàn bộ URL vào api/index.php
├── runtime.php                              # Khởi tạo runtime, hàm cache file an toàn ngoài web root
├── api/
│   └── index.php                            # Router chính: Xác thực Token, phân quyền, xử lý 32 bảng
├── ai/
│   ├── FoodHubSupport.php                   # Module CSKH thông minh: Nhận diện cảm xúc & Guardrails
│   ├── MealAdvisor.php                      # Tư vấn thực đơn, Master System Prompt Groq/Gemini
│   ├── MealChat.php                         # Quản lý ngữ cảnh hội thoại đa lượt và phân trang menu
│   ├── MenuKnowledge.php                    # Trích xuất và lập chỉ mục thực đơn trực tiếp từ TiDB
│   └── meal-advisor-route.php               # Điểm cuối xử lý yêu cầu POST /api/ai/meal-advisor
├── certs/
│   └── isrgrootx1.pem                       # Chứng chỉ số CA xác thực kết nối bảo mật TLS với TiDB Cloud
├── database/
│   ├── connection.php                       # Quản lý kết nối PDO MySQL có cơ chế tái sử dụng (Pooling)
│   └── schema.sql                           # File DDL chuẩn xác 32 bảng cơ sở dữ liệu
├── deploy/                                  # Công cụ tự động hóa triển khai hệ thống
│   ├── alwaysdata.py                        # Script đồng bộ mã nguồn an toàn lên hosting qua SSH/SFTP
│   ├── check-services.php                   # Kiểm tra tình trạng kết nối DB, FCM, PayOS
│   ├── configure-payos.php                  # Thiết lập Webhook và kiểm tra cổng PayOS
│   └── deploy-meal-advisor.py               # Triển khai module AI và đồng bộ API Key riêng
├── migration/                               # Các tập lệnh cập nhật lược đồ CSDL
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
