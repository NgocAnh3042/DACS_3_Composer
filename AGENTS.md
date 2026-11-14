# FoodHub: ngữ cảnh cho phiên làm việc tiếp theo

## Đọc trước khi sửa

1. `docs/SESSION_HANDOFF.md`: trạng thái mới nhất, thay đổi gần đây và việc chưa hoàn tất.
2. `README.md`: bản đồ tài liệu.
3. `docs/ARCHITECTURE.md`, `docs/WORKFLOWS.md`, `docs/DATABASE.md` và `docs/API.md` theo phần cần sửa.
4. `docs/LOCAL_SETUP.md` khi chạy ứng dụng, API hoặc kiểm tra lỗi kết nối.

## Các quyết định đã được người dùng chấp nhận

- Dự án Android: `D:\AndroidProjects\DACS_3_Composer`.
- Backend PHP nguồn: `backend/`; **bản online chính trên Alwaysdata**: `https://chinh.alwaysdata.net/food-delivery-api/api/`, public `/home/chinh/www/food-delivery-api`, cấu hình/khóa `/home/chinh/private/foodhub`. XAMPP `C:\xampp\htdocs\food-delivery-api` giữ làm bản phát triển cũ; không tự đồng bộ đè bản hosting.
- Từ 14/11/2026, API đang chạy dùng **`food_delivery_app` trên TiDB Cloud (MySQL-compatible)** qua PDO MySQL/TLS xác minh CA, session timezone `+07:00`. Bản người dùng import `food_delivery` thiếu AUTO_INCREMENT ở 27 bảng, được giữ lại để đối chiếu; không đổi API về bản đó. MariaDB 10.4.32 / food_delivery trên XAMPP cũng được giữ làm bản cũ, không còn là nguồn ghi chính.
- Firebase Authentication giữ đăng nhập email/mật khẩu và Google. MySQL giữ hồ sơ và quyền.
- Firebase Cloud Messaging gửi thông báo; Realtime Database giữ tracking, presence, typing.
- Cloudinary giữ ảnh. Không chuyển ảnh hoặc nghiệp vụ trở lại Firestore.
- MapLibre Native + OpenFreeMap là bản đồ hiện tại; không tự đổi sang dịch vụ bản đồ cần thanh toán.
- Giữ tông trắng/xanh và nhãn tiếng Việt. Stitch project: `17203895360811157884`, “Food Delivery PRD (Vietnamese)”.

## Các điểm dễ nhầm

- Tên file/hàm có chữ `Firebase` có thể đã gọi MySQL API. Đọc implementation trước khi kết luận.
- `users.id` là khóa số MySQL; `users.firebase_uid` là định danh Firebase. Đừng dùng thay thế lẫn nhau.
- Quyền MySQL `customer/seller/driver/admin` được API chuyển thành `user/restaurant/shipper/admin` cho Android.
- `users/me` khi đăng ký không cho tự tạo quyền admin. Admin thử đã được cấp trực tiếp trong MySQL, gắn với Firebase UID thật.
- Tài khoản thử: `admin@gmail.com`. Mật khẩu không lưu trong tài liệu hay mã nguồn theo dõi bằng Git.
- Số thu nhập shipper hiện là tổng phí giao hàng của đơn hoàn tất theo `completedAt`; không phải tiền thu hộ, số dư ví hay tiền đã đối soát.
- Có 32 bảng trong schema, nhưng một số bảng chưa có luồng UI/API. Xem phần trạng thái trong `docs/DATABASE.md`.

## Khi thay đổi và xác nhận kết quả

- Sửa backend trong repo chưa làm thay đổi bản Apache đang chạy. Đối chiếu và đồng bộ có chủ đích, giữ cấu hình local; không ghi đè toàn bộ bản chạy nếu có thay đổi khác.
- Cấu hình online: `/home/chinh/private/foodhub/config.local.php`; public config.local chỉ là wrapper. Checkout hiện dùng `backend/config.runtime.local.php` (Git bỏ qua), chỉ để chuẩn bị bundle; không dùng đường dẫn Windows trên hosting. API ưu tiên config.local, rồi config.runtime.local, cuối cùng config.example. SFTP xác minh fingerprint, mật khẩu/fingerprint trong backend/deploy/*.local.txt đều Git bỏ qua; không đưa khóa vào web root. Xem docs/ALWAYSDATA.md.
- Không đưa khóa PayOS, service-account JSON, token đăng nhập hoặc mật khẩu vào Markdown, log hay commit.
- Không reset/import/xóa database để sửa một lỗi nhỏ. `schema.sql` không phải hệ thống migration tự động.
- Kiểm tra phần thay đổi phù hợp: Kotlin/Compose, JUnit, `php -l`, truy vấn DB hoặc thao tác trên thiết bị.
- Gradle từng bị chặn bởi `Unable to establish loopback connection` trong môi trường công cụ. Phân biệt biên dịch riêng tệp, chạy unit test và build APK đầy đủ; không tuyên bố đã test điện thoại nếu chưa làm.
- Cập nhật `docs/SESSION_HANDOFF.md` sau thay đổi đáng kể. Ghi trạng thái thực tế, không ghi chức năng dự kiến như đã xong.
