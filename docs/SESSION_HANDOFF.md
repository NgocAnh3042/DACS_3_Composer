# Bàn giao phiên làm việc — FoodHub

Cập nhật **14/11/2026**, múi giờ làm việc Asia/Bangkok. Đọc trước khi tiếp tục dự án.

## 1. Quyết định của người dùng

- App đang phát triển, chưa vận hành thật. Từ 14/11/2026 dùng **API PHP trên Alwaysdata + TiDB Cloud / food_delivery_app**, PDO MySQL có TLS. API chính https://chinh.alwaysdata.net/food-delivery-api/api/; XAMPP và DB cũ giữ làm bản dự phòng. Mã Android đã đổi URL; app đang cài cần build/cài lại.
- Giữ Firebase Authentication, FCM, Realtime Database và Cloudinary đúng vai trò trong `ARCHITECTURE.md`. Không phát triển CRUD nghiệp vụ mới trên Firestore.
- Giữ tiếng Việt, trắng/xanh; Material 3/Compose và tham chiếu Stitch khi được yêu cầu.
- Bản đồ đã được người dùng chấp nhận là MapLibre Native; không tự đổi sang Google Maps cần cấu hình thanh toán.

## 2. Tài khoản admin vừa xử lý

- Email đăng nhập ứng dụng: `admin@gmail.com`.
- Firebase đã có email này: **giữ UID hiện có**, cập nhật mật khẩu người dùng yêu cầu và bật tài khoản.
- Hồ sơ MySQL: `role=admin`, `status=active`, `deleted_at=NULL`.
- Mật khẩu không lưu vào bảng users hoặc Markdown/mã nguồn theo dõi bằng Git.
- Kiểm tra đã đạt: email/password sign-in Firebase thật; `GET users/me` trả `admin`; `GET users` của admin thành công.
- Chưa test toàn bộ giao diện admin trên điện thoại. Quyền đăng nhập không có nghĩa mọi màn hình admin đã hoàn thiện.
- Script tạm của phiên này ở `build/`, không phải endpoint công khai hoặc công cụ provisioning chính thức.

## 3. Thay đổi gần đây

### Người dùng đã kiểm tra ứng dụng, chuẩn bị đưa Git review

- Ngày 14/11/2026 người dùng xác nhận app chạy và lưu dữ liệu thành công khi không bật XAMPP/ngrok. Đây là xác nhận từ người dùng sau cài/chạy bản mới; không thay thế việc agent tự kiểm thử toàn bộ UI hoặc giao dịch tiền thật.
- Người dùng yêu cầu đẩy toàn bộ phần cập nhật lên Git để bạn sở hữu repo duyệt. Dùng nhánh review riêng trên origin (fork chinh21112006), upstream là NgocAnh3042/DACS_3_Composer; không tự merge main.
- Đã rà file làm việc khớp khóa server hiện tại và mẫu private-key/token trước stage; các cấu hình local, SSH password, service account và bundle/build không được đưa vào commit. Khôi phục config.example.php bằng placeholder cho bản clone mới; không dùng mẫu này đè cấu hình hosting.

### Alwaysdata đã triển khai và chuyển endpoint

- Account `chinh`, SSH ssh-chinh.alwaysdata.net, home /home/chinh. Đã xác minh fingerprint và đăng nhập sau khi người dùng sửa mật khẩu SSH trong file local. Không tạo SSH key mới, không gửi mật khẩu vào chat.
- **API chính https://chinh.alwaysdata.net/food-delivery-api/api/**, public /home/chinh/www/food-delivery-api; private /home/chinh/private/foodhub. Upload/checksum 10 tệp, PHP lint, CLI PHP 8.4.27, TiDB đúng database/32 bảng/auto IDs/TLS, HTTPS Firebase thành công. Không thay đổi/import lại database.
- prepare-alwaysdata.php/alwaysdata.py và docs/ALWAYSDATA.md mô tả triển khai. Bundle và dependencies ở build; credential/fingerprint ở backend/deploy/*.local.txt đều Git bỏ qua. Config TiDB/PayOS/service account/CA/cache ngoài web, private folder 0700/file 0600. Public config.local.php chỉ wrapper và bị HTTP chặn.
- runtime.php chọn cache riêng ngoài web; PHP timezone Asia/Ho_Chi_Minh khớp DB +07:00. Sửa PDOStatement rowCount trong webhook. XAMPP vẫn giữ bản cũ, không tự đồng bộ source đè nó.
- HTTPS hosting đã qua: admin Firebase email/password; user/restaurant/shipper bằng danh tính sẵn có qua custom token (isNewUser=false), đúng role và đọc API hồ sơ/quán/món/đơn/địa chỉ/payments/shipper. PATCH/GET tên admin rồi khôi phục thành công. Khi test users=5, stores=1, dishes=4, orders=6. Thiếu Bearer và HMAC PayOS sai trả 401; config/.user.ini/service-account trả 403, schema SQL trả 404.
- OAuth FCM lấy được ngay trên hosting, không gửi thông báo thử. **PayOS confirm-webhook confirmed=true cho https://chinh.alwaysdata.net/food-delivery-api/api/payos/webhook**; không tạo giao dịch tiền thật hoặc đánh dấu paid giả.
- FoodHubApi.kt đổi BASE_URL sang Alwaysdata, bỏ header riêng ngrok. Không còn ngrok/localhost trong app/src/main. App cũ cần build/cài bản mới. Gradle trong công cụ vẫn lỗi loopback trước compile, cả cách chạy nền không daemon; không tạo được APK mới. ADB không có thiết bị được cấp quyền. Cần build/Run qua Android Studio ngoài môi trường công cụ, chưa xác nhận UI/Google Sign-In trên điện thoại.
- Không dừng XAMPP/ngrok do người dùng mở vì có thể có tác vụ khác; endpoint hosting hoạt động độc lập máy local. Backup app trên remote nếu có tệp trước deploy: /home/chinh/private/foodhub/backups/20261114-044507.

### TiDB đã kích hoạt ngày 14/11/2026

- **Database đang dùng: `food_delivery_app`**, TiDB `8.0.11-TiDB-v8.5.3-serverless`, 32 bảng, 27 cột AUTO_INCREMENT. Không nhầm với `food_delivery` trên local hoặc bản import đầu tiên ở TiDB.
- Người dùng tự điền mật khẩu trên máy. Đã xác minh TLS `TLS_AES_128_GCM_SHA256`, chứng chỉ ISRG Root X1, session timezone `+07:00`. Helper PDO `backend/database/connection.php` dùng cho API và CLI, bắt buộc CA/kiểm tra TLS khi cấu hình required; vẫn hỗ trợ MySQL local không TLS.
- Bản import ban đầu thiếu đơn thứ 6 và các bản ghi liên quan: đã sao lưu rồi bổ sung 25 bản ghi, cập nhật 4 bản ghi trạng thái (conversation_participants/drivers/messages/user_device_tokens), giữ nguyên ID/Firebase UID và đối chiếu dấu băm toàn bộ 32 bảng.
- Phát hiện tiếp bản import mất AUTO_INCREMENT ở cả 27 bảng có primary id: đọc/sửa được nhưng INSERT không truyền id lỗi 1364. Vì TiDB không cho bổ sung thuộc tính này bằng ALTER, đã tạo **food_delivery_app** bằng SHOW CREATE TABLE từ MariaDB, chép dữ liệu TiDB đã bổ sung, xác minh đầy đủ khóa/dấu băm và đủ 27 cột tự tăng. Không xóa database local hoặc bản import TiDB.
- Cấu hình live `C:\xampp\htdocs\food-delivery-api\config.local.php` đã đổi mysql sang database mới; các cấu hình Firebase/PayOS/phí giao hàng đã kiểm tra giữ nguyên. Router/helper/CA/.htaccess được đồng bộ có chọn lọc. URL Android và webhook giữ nguyên.
- File config.local.php và config.example.php ở checkout không còn truy cập được trong phiên, không thể ghi lại config.local.php. Đã dùng file **backend/config.runtime.local.php** riêng (Git bỏ qua, chứa cấu hình TiDB đầy đủ). API ưu tiên config.local, rồi runtime, cuối cùng example. Không tạo lại config.local trỏ MariaDB rồi vô tình ghi đè ưu tiên runtime. Candidate **config.tidb.local.php** kế thừa local hoặc runtime; đã cập nhật database food_delivery_app, không đổi tên file đè cấu hình gốc.
- Công cụ `backend/tools/check-database.php` chỉ chạy CLI, đọc phiên bản/timezone/TLS/số bảng/số dòng và phát hiện primary id thiếu AUTO_INCREMENT. Test `backend/tests/database_connection_test.php` đạt.
- Bản sao lưu ngoài web root: `C:\xampp\private\foodhub-backups\tidb-20261114-032718` (cấu hình MariaDB + snapshot trước đồng bộ), `tidb-schema-20261114-033741` (TiDB trước sửa schema, DDL chuẩn, cấu hình trước activation). Chứa dữ liệu/khóa, không copy vào Git/htdocs. Rollback cấu hình không tự chép dữ liệu mới từ TiDB về local.
- Kiểm tra đạt: admin email/password Firebase thật; các danh tính user/restaurant/shipper hiện có sign-in bằng custom token để kiểm tra API, `isNewUser=false`, đúng vai trò; đọc hồ sơ/quán/món/đơn/địa chỉ/payments/shipper. Cập nhật tên admin, đọc lại rồi khôi phục; TiDB SELECT FOR UPDATE/INSERT/lastInsertId/rollback thành công, không để lại bản ghi thử. Counts tại thời điểm chuyển users=5, stores=1, dishes=4, orders=6.
- Public HTTPS ngrok health, users/me admin và orders=6 bằng Firebase token thật đạt sau khi người dùng xử lý AVG. Lỗi trước đó là AVG Web/Mail Shield thay certificate connect.ngrok-agent.com thành **AVG ... Untrusted Root**. Không tắt TLS hoặc tin cậy root này. Thử overlay CA host không sửa được và không dùng làm cấu hình chính; cấu hình ngrok gốc giữ nguyên. Người dùng mở tunnel của họ (PID 23988 trong phiên); không dừng tunnel người dùng.
- Apache và MySQL local ban đầu tắt; phiên này bật nền để so sánh, dữ liệu MariaDB giữ nguyên. Sau chuyển chỉ cần Apache + Internet/ngrok, không cần MySQL local cho API. Chưa chạy toàn bộ UI/đăng nhập Google trực tiếp trên thiết bị hoặc thanh toán thật trong phiên chuyển DB. Các vấn đề PayOS bên dưới chưa tự sửa.

### Địa chỉ khách hàng và nhà hàng

- Bộ dùng chung `feature/shared/location/AddressLocationPicker.kt` đã vào hồ sơ địa chỉ, giỏ hàng, hồ sơ quán và quản lý quán.
- Gõ số nhà/đường có gợi ý Photon; chọn kết quả lấy luôn tọa độ, không bắt buộc kéo map.
- Đã sửa `lang=vi` bị dịch vụ từ chối sang `lang=default`.
- GPS reverse ưu tiên `DeviceAddressLookup` / Android Geocoder, có Photon dự phòng.
- Giữ tọa độ được chọn, chỉ tra tên gần ghim; thiếu số nhà thì nhắc bổ sung.
- Hủy yêu cầu cũ, cache giới hạn, tra sau khi dừng pan; không tự đưa zoom về mức cố định sau mỗi lần kéo.

### Shipper map

- `ShipperMapView.kt`: MapLibre + OpenFreeMap, RTDB vị trí và OSRM tuyến đường.
- Chạm trong map khóa cuộn trang; ngoài khung cuộn trang bình thường. Có guard native và khung Compose, xử lý multi-touch/thả/cancel/dispose.
- Chạm map ngừng camera tự bám GPS; nút định vị bật lại.
- Truyền trạng thái touch tới LazyColumn của cả tổng quan và chi tiết đơn.

### Thu nhập shipper

- Mẫu Stitch “Thu nhập giao hàng (Shipper)”, screen `3aeab867043b4da399712cc64071cbfa`, project `17203895360811157884`.
- Chạm icon ví/ô Thu nhập hôm nay mở `shipper_earnings`.
- `feature/shipper/earnings/`: tuần/tháng, biểu đồ cột chọn ngày, chi tiết đơn, số đơn, trung bình, ngày cao nhất, so kỳ trước.
- Tính tổng phí giao hàng của đơn `COMPLETED` đúng shipper, theo `Order.completedAt`.
- Backend lấy ngày hoàn tất từ orders/deliveries/log; đã cập nhật repo và bản XAMPP.
- Không có dữ liệu rút tiền, số dư ví, thưởng, khấu trừ hoặc payout trong báo cáo này.

### Tổng quan và cá nhân admin

- Stitch mới: “Tổng quan quản trị (Material 3 Mobile)” screen `f0b537457bad4398951c7743d38aa1cc`; “Cá nhân quản trị (Material 3 Admin)” screen `9c22edc97c034bf2b335c2ec146e24af`.
- Dashboard đã thay bố cục cũ: tuần/tháng và chuyển kỳ hoạt động thật; cột doanh thu từng ngày và đường doanh thu lũy kế kỳ này/kỳ trước.
- Doanh thu admin dùng `totalPrice` của đơn `COMPLETED`, theo `completedAt`; không phải phí giao hàng shipper hay lợi nhuận. So sánh cùng số ngày đầu hai kỳ, có xử lý tháng dài/ngắn khác nhau và không vẽ dữ liệu tương lai.
- Có KPI đơn tạo, đơn giao thành công, tỷ lệ hủy theo đơn tạo trong kỳ, giá trị đơn trung bình; tổng tài khoản/quán/shipper hiện tại, 5 đơn mới trong kỳ và hộp thoại chi tiết đơn thật.
- Hồ sơ mới cho đổi avatar Cloudinary, sửa tên/số điện thoại qua payload PATCH riêng để tránh xung đột alias `fullName/name`, `phoneNumber/phone`. Email đăng nhập chỉ đọc; bỏ chỉ số uptime giả và các mục xe/payout chưa có.
- Menu đáy: Tổng quan, Người dùng, Khuyến mãi, Cá nhân. Các lối tắt đã nối; header/hồ sơ mở được hộp thư hỗ trợ và chat qua route `admin_messages`, `admin_chat/{conversationId}`.
- 6 kiểm tra `AdminRevenueSummaryTest` đạt; các tệp thay đổi qua kiểm tra biên dịch Kotlin/Compose riêng. API hồ sơ/tài khoản/đơn/quán được gọi thật bằng admin và thành công. Chưa chạy UI mới trên điện thoại.

## 4. Kiểm tra và giới hạn xác nhận

| Phần | Kết quả |
| --- | --- |
| Địa chỉ | 7 JUnit đã đạt; có biên dịch riêng tệp liên quan |
| Shipper/thu nhập | 5 JUnit đã đạt; các tệp thay đổi qua biên dịch Kotlin/Compose riêng |
| Admin dashboard mới | 6 JUnit đã đạt; biên dịch riêng và kiểm tra API đọc bằng admin đạt |
| Completion date API | PHP lint repo/bản Apache và SQL EXPLAIN đạt |
| DB local | MariaDB 10.4.32, database food_delivery, 32 bảng |
| DB đang dùng | TiDB Cloud food_delivery_app, 32 bảng, 27 cột ID tự tăng, TLS + timezone +07:00 |
| Chuyển TiDB | Đối chiếu dữ liệu 32 bảng, bốn vai trò, hồ sơ ghi/đọc/khôi phục, INSERT/lastInsertId/rollback đạt; public ngrok + token thật đạt |
| Admin | Sign-in Firebase thật và API quyền admin đạt |
| Alwaysdata | HTTPS bốn vai trò, hồ sơ ghi/đọc/khôi phục, FCM OAuth, chặn file riêng, PayOS confirm-webhook đạt; URL Android đã đổi |
| Full build APK | Gradle trong công cụ từng bị lỗi loopback trước biên dịch; chưa xác nhận full APK mới |
| Thiết bị | Chưa kiểm chứng độ mượt map và toàn bộ giao diện thu nhập/admin mới trên điện thoại |
| Photon | Gợi ý đã trả được tên tiếng Việt; reverse public server cũng từng timeout, đã thêm lựa chọn Android Geocoder |

## 5. Những phần cần biết trước khi test/mở rộng

Các quan sát dưới đây từ code hiện tại, chưa được tự động sửa trong phiên viết tài liệu.

1. **PayOS rowCount đã sửa trên source/hosting.** UPDATE payment lấy rowCount từ PDOStatement; bản XAMPP cũ chưa đồng bộ có thể vẫn còn lỗi cũ. Alwaysdata qua kiểm tra HMAC sai (401) và PayOS confirm-webhook hợp lệ; chưa thử giao dịch tiền thật hoặc toàn bộ nhánh cập nhật paid/webhook lặp trong phiên triển khai, không coi là PayOS end-to-end.
2. **AdminCustomerViewModel tạo tài khoản bằng Firebase client auth chính.** `createUserWithEmailAndPassword` có thể chuyển phiên admin sang tài khoản mới. Cần luồng server/Admin SDK hoặc FirebaseAuth phụ khi hoàn thiện chức năng này. Tài khoản admin vừa xử lý không đi qua luồng đó.
3. **Admin còn một số phần mẫu ngoài dashboard/profile mới.** Tổng quan thật dùng `AdminAnalyticsScreen`, không phải class `AdminOverviewScreen` rỗng. `AdminPaymentScreen` là khung; màn khiếu nại có dữ liệu/nút mẫu, mục đơn hàng đang comment khỏi bottom navigation. Các component analytics/profile cũ còn trong cây nguồn không có nghĩa chúng vẫn là giao diện đang dùng.
4. **Khóa tài khoản chưa được chặn toàn API.** `account()` hiện lọc `deleted_at`, chưa bắt buộc `status=active` ở mọi route. Không coi thao tác đổi suspended là đã vô hiệu hóa mọi token/API.
5. **Trạng thái chưa là state machine đầy đủ.** Có quyền/sở hữu và tập trạng thái, chưa chặn đủ chuyển ngược/nhảy bước. PATCH completed lặp có thể tăng `completed_trips` lại; biểu đồ đếm đơn, không dựa hoàn toàn vào counter.
6. **Giỏ server/tùy chọn món chưa nối đủ.** Có bảng carts/options, nhưng giỏ hiện ở state CartViewModel và chưa có CRUD giỏ trong router.
7. **Phí ship client/server cần đồng bộ khi sửa.** Server chốt bằng `config['delivery_fee']`, không dùng trực tiếp lựa chọn phí UI.
8. **Lượt voucher tăng lúc tạo đơn.** Chưa thấy xử lý trả lượt khi hủy đơn chờ thanh toán; cần xác định chính sách nếu mở rộng.
9. **Phân biệt doanh thu admin và thu nhập shipper.** Dashboard admin mới đã chuyển sang ngày hoàn tất; admin tổng hợp `totalPrice`, shipper tổng hợp `shippingFee`. Các card analytics cũ chưa dùng trong dashboard mới không phải nguồn dữ liệu hiện hành.
10. **Rules RTDB/secrets nằm ngoài repo.** Không suy ra rules đang triển khai từ code client.

## 6. Bản đồ mã nguồn

| Việc cần sửa | Đọc đầu tiên |
| --- | --- |
| Login/role | AuthViewModel, MainActivity, FoodHubClient, verifyToken/account/externalUser trong PHP |
| Món/quán không lưu | RestaurantStoreRepository, MenuRepository, ViewModel nhà hàng, nhánh stores/dishes |
| Địa chỉ/GPS | AddressLocationPicker, AddressSearch, DeviceAddressLookup, CustomerAddressForm, form quán |
| Map shipper rung/scroll | ShipperMapView, ShipperDashboardScreen, HeatMapBanner, ActiveDeliveryCard, MapRouteSection, ShipperOrderDetailScreen |
| Thu nhập | EarningsSummary, ShipperEarningsScreen, ShipperViewModel, Order.completedAt, GET orders |
| PayOS | PaymentRepository, PaymentViewModel/Screen, MainActivity deep link, payments/create-link, payos/webhook |
| Chat/FCM | ChatRepository, PresenceManager, FirebasePushService, conversations/messages/device-tokens/notifyUser |
| Schema | schema.sql, migration, SHOW CREATE TABLE của DB đang chạy |

## 7. Cập nhật tài liệu cho phiên sau

Sau thay đổi đáng kể, cập nhật ngày, chức năng/file/route liên quan, dữ liệu đã đổi và kiểm tra thực sự đã chạy. Nếu tài liệu khác code, kiểm tra implementation và bản Apache rồi sửa mô tả; không ép code khớp một ghi chú cũ bằng cách xóa dữ liệu hoặc bỏ xác thực.
