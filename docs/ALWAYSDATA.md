# Triển khai API lên Alwaysdata

**Khôi phục kết nối 16/11/2026:** sau khi người dùng đổi mật khẩu TiDB, backend bị 1045/health 503. Đã kiểm tra mật khẩu mới và chỉ cập nhật mysql.password trong cấu hình private, giữ các giá trị khác; backup `/home/chinh/private/foodhub/backups/password-20261116-064356`. Health và các API đọc có Firebase token đều đạt. Cấu hình local/runtime trong checkout hiện thiếu, cần chuẩn bị lại cấu hình riêng trước khi tạo full bundle; không triển khai bundle cũ đè mật khẩu hosting mới. Xem SESSION_HANDOFF.

**Bổ sung 15/11/2026:** tính năng tư vấn món đã được người dùng cho phép và triển khai có chọn lọc (router + hai helper AI), giữ nguyên DB/PayOS/Firebase. Khóa Gemini riêng ở private/config.gemini.local.php, model `gemini-4-hydrogen`, HTTP chặn wrapper 403. Backup cuối `/home/chinh/private/foodhub/backups/meal-advisor-20261115-143608`; lint/checksum đạt. Google nhận khóa/model nhưng generateContent đang gặp high demand/timeout; chưa xác nhận lượt tư vấn thật thành công. Xem AI_MEAL_ADVISOR và SESSION_HANDOFF.

Trạng thái ngày 14/11/2026: **đã triển khai API, xác nhận PayOS webhook và đổi URL trong mã Android sang Alwaysdata**. Người dùng đã sửa mật khẩu SSH sau lần từ chối đầu; kết nối xác minh đúng fingerprint và đăng nhập thành công. App cũ trên điện thoại cần build/cài bản mới để dùng URL mới.

## Thông tin triển khai

| Thành phần | Đường dẫn |
| --- | --- |
| SSH | `chinh@ssh-chinh.alwaysdata.net:22` |
| Home đã xác minh | `/home/chinh` |
| API chính | `https://chinh.alwaysdata.net/food-delivery-api/api/` |
| Webhook PayOS đã xác nhận | `https://chinh.alwaysdata.net/food-delivery-api/api/payos/webhook` |
| Web root đã kiểm tra | `/home/chinh/www` |
| Backend public | `/home/chinh/www/food-delivery-api` |
| Config/CA/service account/cache riêng | `/home/chinh/private/foodhub` |
| Database | Giữ TiDB Cloud `food_delivery_app`, không import/reset DB khi đổi hosting |

Gói Free chỉ dành học tập/demo phi thương mại. Không tự nâng cấp gói hoặc thay đổi thông tin thanh toán. Backend cần PHP >=8.1, PDO MySQL, OpenSSL, HTTPS streams/cURL; test thực tế PHP web và CLI sau tải lên.

## Kết quả đã kiểm tra

- CLI PHP 8.4.27; TiDB đúng food_delivery_app, 32 bảng, không thiếu ID tự tăng, TLS xác minh CA. Checksum 10 tệp triển khai và PHP lint đạt.
- HTTPS health 200; Firebase admin sign-in email/password, ba vai trò còn lại dùng danh tính hiện có qua custom token, isNewUser=false; đúng role và đọc được hồ sơ/quán/món/đơn/địa chỉ/payments/shipper. Counts khi kiểm tra: users=5, stores=1, dishes=4, orders=6.
- PATCH tên admin rồi GET xác nhận và khôi phục tên ban đầu thành công. Không tạo tài khoản hoặc đơn hàng thử; không tạo giao dịch tiền thật.
- API từ chối thiếu Bearer token và chữ ký PayOS không hợp lệ (401). Config.local.php, .user.ini, service-account.json bị chặn HTTP (403); schema.sql không có trong public (404).
- Script CLI ngoài web root lấy OAuth FCM thành công, không gửi thông báo thử. HTTPS Firebase certificates thành công. PayOS API confirm-webhook trả confirmed=true với URL Alwaysdata.
- Public/private cấu hình đã tách; Windows paths không đưa lên Linux. Bản sao lưu nếu có app trước đó ở `/home/chinh/private/foodhub/backups/20261114-044507`; database không reset hay chuyển lần nữa.
- Build APK bằng Gradle trong công cụ gặp lại lỗi loopback trước compile, cả cách chạy nền không daemon. Chưa tạo được APK mới, cần build/Run qua Android Studio bên ngoài môi trường công cụ. ADB không có thiết bị được cấp quyền; chưa xác nhận UI trên điện thoại.

## Công cụ và khóa

- `backenddd/deploy/prepare-alwaysdata.php`: đọc cấu hình TiDB hiện có, tạo bundle trong `build/alwaysdata-deploy/bundle` (Git bỏ qua). Chỉ chép whitelist tệp runtime, không chép Android project, SQL data, tài liệu hoặc toàn bộ XAMPP.
- Bundle private giữ mật khẩu DB, khóa PayOS, Firebase service account và CA. Các tệp này chỉ đưa vào `/home/chinh/private/foodhub`, quyền folder 0700/file 0600. Public config.local.php chỉ là wrapper tới file riêng, HTTP bị .htaccess chặn.
- `backenddd/deploy/alwaysdata.py`: Paramiko SFTP có xác minh fingerprint SHA256 trước xác thực mật khẩu; hỗ trợ probe/deploy/check. Nếu dashboard hiển thị RSA thay ED25519, thử các thuật toán nhưng vẫn bắt buộc khớp fingerprint đã được người dùng cung cấp; không tự tin cậy key không khớp.
- Mật khẩu trong `backenddd/deploy/alwaysdata.password.local.txt`, chỉ nội dung mật khẩu SSH, không thêm nhãn hoặc dấu ngoặc. Fingerprint từ **Remote access > SSH/SFTP > Display fingerprints** trong `alwaysdata.hostkey.local.txt`. Hai file được Git bỏ qua, không in nội dung ra log.
- Python dependency cài riêng trong `build/alwaysdata-deploy/python-libs`, không thay Python hệ thống. Công cụ không lưu mật khẩu vào tham số dòng lệnh.
- `backenddd/runtime.php` chọn cache directory ngoài web trên hosting. API đặt timezone từ config, mặc định Asia/Ho_Chi_Minh để khớp session TiDB +07:00. Cache cũ local vẫn dùng thư mục temp nếu chưa cấu hình riêng.
- Đã sửa lỗi webhook cũ: lấy rowCount từ PDOStatement sau UPDATE payment, không gọi PDO::rowCount. Chưa coi thanh toán thật đã được kiểm thử chỉ vì code này đã sửa.

## Thứ tự triển khai

```powershell
& 'C:\xampp\php\php.exe' .\backenddd\deploy\prepare-alwaysdata.php
python .\backenddd\deploy\alwaysdata.py probe
python .\backenddd\deploy\alwaysdata.py deploy
```

1. Xác minh home, PHP, web root và fingerprint. Không thử lại mật khẩu sai liên tục.
2. Deploy sao lưu có chọn lọc các tệp app hiện có vào private/backups, tải các tệp private/dependency trước và entrypoint cuối; kiểm tra checksum/cú pháp. Không sửa website khác hoặc xóa database.
3. Preflight CLI: TiDB TLS, đúng database, 32 bảng, đủ auto-increment, service account đọc được, HTTPS Firebase. Kiểm tra HTTP health qua domain mới, header Bearer, bốn vai trò, đọc/ghi/khôi phục hồ sơ; kiểm tra config/service account không đọc được qua HTTP.
4. Kiểm tra FCM OAuth phía server, HMAC webhook và PayOS confirm-webhook bằng khóa hiện có. Không tạo giao dịch tiền thật để thử.
5. Chỉ sau khi endpoint mới đạt kiểm tra, đổi FoodHubClient.BASE_URL sang Alwaysdata và bỏ header riêng ngrok. Đổi webhook PayOS bằng confirm-webhook API nếu thành công; báo rõ nếu provider từ chối.
6. Build app mới/cài lại trên thiết bị để dùng URL mới. Không tuyên bố điện thoại đã chạy bản mới chỉ từ HTTP test hoặc biên dịch riêng Kotlin.
7. Cập nhật trạng thái thực tế trong SESSION_HANDOFF/README/LOCAL_SETUP. Không dừng XAMPP/ngrok trước khi hoàn tất chuyển đổi hoặc nếu chúng đang phục vụ tác vụ khác của người dùng.

CLI PHP chỉ dùng chuẩn bị bundle trên máy; sau triển khai, hosting tự chạy API, không cần mở XAMPP/ngrok/máy tính. Firebase Authentication, FCM, RTDB và Cloudinary giữ nguyên chức năng.
