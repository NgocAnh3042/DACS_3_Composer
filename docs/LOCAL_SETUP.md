# Chạy local và kiểm tra lỗi

## API đang dùng trên hosting

Từ 14/11/2026, API chính là **https://chinh.alwaysdata.net/food-delivery-api/api/** trên Alwaysdata, kết nối TiDB food_delivery_app. Không cần bật XAMPP/ngrok/máy phát triển để dùng API này. Mã Android đã đổi BASE_URL; app cũ trên điện thoại phải build/cài lại để nhận URL mới. Xem [ALWAYSDATA.md](ALWAYSDATA.md) cho triển khai và kiểm tra hosting.

Các hướng dẫn XAMPP/ngrok dưới đây là môi trường phát triển/dự phòng trước khi chuyển hosting, không phải yêu cầu chạy app online hiện tại.

## 1. Môi trường local/dự phòng đã đối chiếu ngày 14/11/2026

| Thành phần | Đường dẫn / giá trị |
| --- | --- |
| Android và mã nguồn backend | `D:\AndroidProjects\DACS_3_Composer` |
| Backend nguồn | `D:\AndroidProjects\DACS_3_Composer\backend` |
| API online chính | `https://chinh.alwaysdata.net/food-delivery-api/api/` |
| Cấu hình hosting | `/home/chinh/private/foodhub/config.local.php` (ngoài web root) |
| Backend Apache đang chạy | `C:\xampp\htdocs\food-delivery-api` |
| PHP | `C:\xampp\php\php.exe` |
| MySQL CLI | `C:\xampp\mysql\bin\mysql.exe` |
| Database đang dùng | `food_delivery_app` trên TiDB Cloud, 32 bảng, đủ 27 cột ID tự tăng |
| Engine đang dùng | `8.0.11-TiDB-v8.5.3-serverless`, PDO MySQL/TLS |
| DB cũ để đối chiếu | `food_delivery` trên MariaDB 10.4.32 local; bản import TiDB `food_delivery` cũng giữ nguyên |
| User DB local cũ | `root`, mật khẩu trống theo thiết lập XAMPP của người dùng |
| Cấu hình local | `C:\xampp\htdocs\food-delivery-api\config.local.php` |
| Cấu hình checkout | `backend/config.runtime.local.php`, bị Git bỏ qua; API ưu tiên config.local nếu file đó tồn tại |
| Service account | `C:\xampp\private\foodhub-service-account.json` |
| Firebase project | `dacs3-anchin-go` |
| Android package | `com.example.dacs_3_composer` |

Android project không cần chuyển vào `htdocs`; Apache chỉ cần phục vụ backend PHP.

## 2. API và database

1. Bật Apache trong XAMPP và có Internet tới TiDB. Không cần bật MySQL local để chạy API hiện tại. Môi trường đã có database và tài khoản thử, không cần tạo lại.
2. Với máy mới và DB trống, dùng `backend/database/schema.sql` qua công cụ quản trị DB. Với DB cũ, so sánh cột trước khi chạy migration; `users.address_line` đã có ở máy hiện tại.
3. API ưu tiên `config.local.php`, rồi `config.runtime.local.php`, sau đó `config.example.php`. Bản XAMPP dùng config.local; checkout dùng config.runtime.local do đường dẫn config.local nguồn không ghi được trong phiên chuyển DB. Các file local giữ host/port/database/username/password, TLS CA, Firebase project, phí giao hàng và PayOS; không commit hoặc in nội dung các file này.
4. Giữ `.htaccess`: root bảo vệ file local; `api/.htaccess` rewrite route và chuyển tiếp Authorization header. Apache cần cho phép rule hoạt động.
5. Health check: `http://localhost/food-delivery-api/api/`, cần nhận `ok=true`, `service=FoodHub API`, `database=connected`.

Kiểm tra chỉ đọc bằng PowerShell:

```powershell
Set-Location 'D:\AndroidProjects\DACS_3_Composer'
& 'C:\xampp\php\php.exe' -l .\backend\api\index.php
& 'C:\xampp\php\php.exe' .\backend\tools\check-database.php 'C:\xampp\htdocs\food-delivery-api\config.local.php'
Invoke-RestMethod 'http://localhost/food-delivery-api/api/'
```

Repo và bản trong `htdocs` có thể khác nhau. Khi triển khai, xem diff, sao lưu bản chạy ngoài web root, đồng bộ tệp cần thiết và giữ khóa local. Không copy `config.example.php` đè cấu hình đã điền khóa. Import lại schema không tự thêm cột cho bảng đã tồn tại.

### TiDB Cloud đã kích hoạt (14/11/2026)

- **Database đang dùng là `food_delivery_app`**, không phải bản import `food_delivery`. Bản import có 32 bảng nhưng thiếu AUTO_INCREMENT ở 27 bảng; đã tạo database chuẩn từ DDL MariaDB và chép dữ liệu giữ nguyên ID/Firebase UID. Không xóa bản import hoặc database local.
- Trước khi chuyển đã bổ sung dữ liệu phát sinh sau lần dump (gồm đơn thứ 6 và các bản ghi liên quan), đối chiếu khóa/dấu băm toàn bộ 32 bảng. Tại thời điểm chuyển có 5 users, 1 store, 4 dishes, 6 orders.
- Source API dùng `backend/database/connection.php`, hỗ trợ PDO MySQL local và TLS bắt buộc có xác minh chứng chỉ cho TiDB. Cấu hình TiDB đặt session timezone `+07:00`.
- Chứng chỉ công khai `backend/certs/isrgrootx1.pem` tải từ [Let's Encrypt](https://letsencrypt.org/certs/isrgrootx1.pem). SHA-256: `96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6`.
- File riêng `backend/config.tidb.local.php` đã có thông số người dùng cung cấp, mật khẩu do người dùng tự điền; database đã đổi sang `food_delivery_app`. File kế thừa config.local nếu có, nếu thiếu thì config.runtime.local. Không đổi tên file này đè cấu hình đang dùng. Các file local bị Git bỏ qua và Apache chặn truy cập trực tiếp.

Kiểm tra chỉ đọc, không tạo/import/xóa dữ liệu:

```powershell
& 'C:\xampp\php\php.exe' 'D:\AndroidProjects\DACS_3_Composer\backend\tools\check-database.php'
```

Kết quả cần có database `food_delivery_app`, 32 bảng, TLS cipher khác rỗng, timezone `+07:00`, `missing_auto_increment=[]`. Công cụ in số dòng users/stores/dishes/orders, không in hồ sơ hoặc mật khẩu. Kiểm tra schema giúp phát hiện trường hợp đủ bảng nhưng không tạo được dữ liệu mới.

Đã sao lưu ngoài web root và đồng bộ có chọn lọc router/helper/chứng chỉ, **chỉ đổi khối `mysql`** của cấu hình đang chạy; kiểm tra các cấu hình khác (Firebase/PayOS/phí giao hàng) bằng nhau trước/sau. Thư mục sao lưu:

- `C:\xampp\private\foodhub-backups\tidb-20261114-032718`: cấu hình MariaDB trước chuyển và dữ liệu hai phía trước đồng bộ.
- `C:\xampp\private\foodhub-backups\tidb-schema-20261114-033741`: snapshot dữ liệu TiDB trước sửa schema, DDL chuẩn và cấu hình trước khi kích hoạt database mới.

Backup có dữ liệu/khóa, giữ ngoài htdocs/Git. Khôi phục cấu hình về MariaDB không tự chép các thay đổi phát sinh trên TiDB về local; phải đối chiếu dữ liệu trước khi rollback. Không reset hoặc import lại DB chỉ để thử kết nối.

Đã xác nhận: health HTTP local, đăng nhập admin email/password và các vai trò hiện có qua Firebase, API hồ sơ/quyền/quán/món/đơn/địa chỉ/shipper, cập nhật rồi khôi phục hồ sơ admin, INSERT/lastInsertId/SELECT FOR UPDATE/rollback trên TiDB. Public ngrok health và API admin/6 orders bằng token thật đã đạt sau khi người dùng xử lý AVG. Chưa chạy toàn bộ thao tác UI điện thoại hoặc thanh toán thật; các giới hạn PayOS cũ vẫn ở SESSION_HANDOFF.

Đó là trạng thái lúc chuyển database trước khi đưa hosting. Hiện API chính đã lên Alwaysdata, URL Android và PayOS webhook đã đổi sang chinh.alwaysdata.net; không cần Apache/ngrok local cho endpoint mới.

## 3. Ngrok dự phòng và điện thoại

```powershell
ngrok http 80 --url=https://plasma-avenue-scrubber.ngrok-free.dev
```

Giá trị hiện ghi trong `FoodHubClient.BASE_URL`:

```text
https://chinh.alwaysdata.net/food-delivery-api/api/
```

Chỉ đổi về ngrok nếu chủ động thử backend local; không thay URL chính/webhook PayOS khi chạy app online bình thường. Giữ đủ đường dẫn `food-delivery-api/api/`.

- Điện thoại thật dùng ngrok HTTPS hoặc IP LAN của máy Apache khi mạng/firewall cho phép.
- Emulator có thể dùng `http://10.0.2.2/food-delivery-api/api/`.
- `localhost` trên điện thoại/emulator không phải máy tính chạy XAMPP.
- HTTP 401 từ ngrok cần kiểm tra token/phản hồi API; không tự coi là DB tắt hoặc sai thư mục Android.
- Phiên chuyển TiDB từng gặp lỗi ngrok `x509: certificate signed by unknown authority`; chuỗi chứng chỉ thực tế bị AVG Web/Mail Shield thay bằng `Untrusted Root`. Sau khi người dùng xử lý AVG và mở lại ngrok, public API đã đạt kiểm tra. Không tắt xác minh TLS hoặc cài tin cậy chứng chỉ mang tên Untrusted Root. Cấu hình ngrok gốc không bị sửa; overlay thử nghiệm trong build không dùng để chạy chính thức.
- Ngrok từ Microsoft Store có thể báo đường dẫn config `AppData/Local/ngrok/ngrok.yml` nhưng file thật nằm trong `AppData/Local/Packages/ngrok.ngrok_1g87z0zv29zzc/LocalCache/Local/ngrok/ngrok.yml`. Không in authtoken khi chẩn đoán.

## 4. Android Studio và build

Mở thư mục có `settings.gradle.kts`. `local.properties` trỏ SDK của máy và không commit. `app/google-services.json` phải thuộc đúng project/package.

Compile target Java 11 không có nghĩa nên chạy Gradle 9 bằng JDK 11. Ví dụ dùng JBR đi kèm Android Studio trong cửa sổ PowerShell:

```powershell
Set-Location 'D:\AndroidProjects\DACS_3_Composer'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Môi trường công cụ từng gặp `java.io.IOException: Unable to establish loopback connection` trước bước biên dịch. Đối chiếu bằng build trong Android Studio và log đúng môi trường; không tự sửa mã app/dependency để chữa lỗi kết nối nội bộ đó.

Các phiên trước đã biên dịch riêng tệp Kotlin/Compose và chạy JUnit trực tiếp khi Gradle bị chặn. `build/address-search-check` và `build/shipper-check` là công cụ tạm của máy, không phải hệ thống build chính được theo dõi trong Git.

## 5. Firebase và Cloudinary

### Authentication

- Bật Email/Password và Google trong Firebase Authentication.
- Google cần `default_web_client_id`, package và cấu hình signing/SHA phù hợp bản app đang chạy.
- API cần đúng Firebase project, truy cập HTTPS tới chứng chỉ Firebase và thời gian máy PHP chính xác.
- Tài khoản thử `admin@gmail.com` đã có `role=admin`, `status=active`. Mật khẩu được chỉ định trong cuộc trao đổi tạo tài khoản, không lưu trong Markdown.
- Đăng xuất tài khoản hiện tại rồi đăng nhập email/password để test admin. Đây là tài khoản trong ứng dụng, không phải tạo hộp thư Gmail mới.

### FCM / Realtime Database

- Service-account JSON ở ngoài `htdocs`, được trỏ bằng `firebase_service_account_file` trong cấu hình local.
- App đăng ký token qua `device-tokens` sau đăng nhập; server dùng OAuth và FCM HTTP v1 gửi thông báo. Cho phép thông báo trên điện thoại nếu hệ điều hành yêu cầu.
- RTDB đang dùng `tracking`, `presence`, `chat/.../typing`. Rules triển khai không có bản đầy đủ trong repo, cần đối chiếu Firebase Console nếu sửa quyền.
- Không in service account, FCM token hoặc ID token vào log chia sẻ.

### Cloudinary

`MainApplication` khởi tạo MediaManager; các luồng dùng unsigned upload preset `ml_default`. Đối chiếu preset/cloud account ở môi trường đang dùng. Lưu `secure_url` vào MySQL; không đặt API secret Cloudinary trong Android/tài liệu.

## 6. PayOS

- Điền Client ID, API Key, Checksum Key trong `config.local.php` phía server.
- Webhook hiện tại, đã được PayOS confirm-webhook chấp nhận: `https://chinh.alwaysdata.net/food-delivery-api/api/payos/webhook`.
- Callback hiện chạy trên hosting, không cần XAMPP/ngrok. PDOStatement rowCount đã sửa trên source và hosting; chưa kiểm thử giao dịch tiền thật trong phiên triển khai.
- Không tạo giao dịch thật hay đánh dấu paid thủ công chỉ để thử khi chưa có yêu cầu tương ứng.

## 7. Các lỗi đã gặp

| Biểu hiện | Phần cần kiểm tra |
| --- | --- |
| Email đã tồn tại nhưng MySQL trống | Firebase Auth và hồ sơ MySQL riêng nhau; đăng nhập rồi đồng bộ `users/me` |
| 401/token hết hạn | Loại ID token, Bearer header, project, chữ ký, giờ máy PHP |
| 503 tải khóa Firebase | HTTPS/cURL/OpenSSL, CA và quyền đọc cache của tài khoản Apache |
| Notice PHP kèm JSON | PHP error log; Notice có thể làm client không đọc được JSON |
| Món/quán không lưu | HTTP response, role/owner, restaurantId và đúng bản backend trong htdocs |
| Map đen/trống | MapLibre init/lifecycle, style OpenFreeMap, Internet/DNS, error overlay |
| Không có gợi ý địa chỉ | Photon có phản hồi không; `lang=vi` từng bị từ chối, hiện dùng `default` |
| GPS chỉ có tọa độ | Android Geocoder, Photon reverse, kết nối và dữ liệu số nhà |
| Vuốt map kéo cả trang | Native touch guard, khung Compose, `userScrollEnabled` của LazyColumn |
| Log WindowManager/destroySurfaces | Cần exception HTTP/API của app; đây không tự chứng minh lỗi DB |
| PHP OpenSSL nạp hai lần | Cấu hình extension PHP local; đã quan sát lúc chạy CLI |

## 8. Kiểm tra hiện có

- `AddressSearchTest`: địa chỉ/số nhà, tọa độ, kết quả trùng/không hợp lệ, không chọn số nhà ở xa.
- `EarningsSummaryTest`: ngày hoàn tất, đúng shipper, trạng thái, qua năm, tháng nhuận, dữ liệu thiếu ngày.
- `AdminRevenueSummaryTest`: tổng tiền đơn hoàn tất, phạm vi ngày, so sánh cùng số ngày hai kỳ, tháng khác độ dài, tỷ lệ hủy và không vẽ dữ liệu tương lai.
- `php -l` cho repo và bản Apache; SQL thu nhập đã EXPLAIN thành công trên local DB.
- Admin đã qua sign-in Firebase thật, `users/me` và endpoint admin `users`.
- Những kiểm tra này không thay thế chạy APK mới trên thiết bị để xác nhận giao diện và thao tác.
