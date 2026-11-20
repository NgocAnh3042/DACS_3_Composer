# Chạy FoodHub và kiểm tra kết nối

Android gọi API trên Alwaysdata tại https://chinh.alwaysdata.net/food-delivery-api/api/. Đường dẫn này trả trạng thái kết nối TiDB. Cấu hình máy chủ và triển khai được mô tả trong [ALWAYSDATA.md](ALWAYSDATA.md).

## Android Studio và build

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

## Firebase và Cloudinary

### Authentication

- Bật Email/Password và Google trong Firebase Authentication.
- Google cần `default_web_client_id`, package và cấu hình signing/SHA phù hợp bản app đang chạy.
- API cần đúng Firebase project, truy cập HTTPS tới chứng chỉ Firebase và thời gian máy PHP chính xác.
- Tài khoản thử `admin@gmail.com` đã có `role=admin`, `status=active`. Mật khẩu được chỉ định trong cuộc trao đổi tạo tài khoản, không lưu trong Markdown.
- Đăng xuất tài khoản hiện tại rồi đăng nhập email/password để test admin. Đây là tài khoản trong ứng dụng, không phải tạo hộp thư Gmail mới.

### FCM / Realtime Database

- Service-account JSON ở ngoài thư mục web, được trỏ bằng `firebase_service_account_file` trong cấu hình local.
- App đăng ký token qua `device-tokens` sau đăng nhập; server dùng OAuth và FCM HTTP v1 gửi thông báo. Cho phép thông báo trên điện thoại nếu hệ điều hành yêu cầu.
- RTDB đang dùng `tracking`, `presence`, `chat/.../typing`. Rules triển khai không có bản đầy đủ trong repo, cần đối chiếu Firebase Console nếu sửa quyền.
- Không in service account, FCM token hoặc ID token vào log chia sẻ.

### Cloudinary

`MainApplication` khởi tạo MediaManager; các luồng dùng unsigned upload preset `ml_default`. Đối chiếu preset/cloud account ở môi trường đang dùng. Lưu `secure_url` vào MySQL; không đặt API secret Cloudinary trong Android/tài liệu.

## PayOS

- Điền Client ID, API Key, Checksum Key trong `config.local.php` phía server.
- Webhook hiện tại, đã được PayOS confirm-webhook chấp nhận: `https://chinh.alwaysdata.net/food-delivery-api/api/payos/webhook`.
- Callback hiện chạy trên hosting. PDOStatement rowCount đã sửa trên source và hosting; chưa kiểm thử giao dịch tiền thật trong phiên triển khai.
- Không tạo giao dịch thật hay đánh dấu paid thủ công chỉ để thử khi chưa có yêu cầu tương ứng.

## Các lỗi đã gặp

| Biểu hiện | Phần cần kiểm tra |
| --- | --- |
| Email đã tồn tại nhưng MySQL trống | Firebase Auth và hồ sơ MySQL riêng nhau; đăng nhập rồi đồng bộ `users/me` |
| 401/token hết hạn | Loại ID token, Bearer header, project, chữ ký, giờ máy PHP |
| 503 tải khóa Firebase | HTTPS/cURL/OpenSSL, CA và quyền đọc cache của tài khoản Apache |
| Notice PHP kèm JSON | PHP error log; Notice có thể làm client không đọc được JSON |
| Món/quán không lưu | HTTP response, role/owner, restaurantId và đúng bản backend trên Alwaysdata |
| Map đen/trống | MapLibre init/lifecycle, style OpenFreeMap, Internet/DNS, error overlay |
| Không có gợi ý địa chỉ | Photon có phản hồi không; `lang=vi` từng bị từ chối, hiện dùng `default` |
| GPS chỉ có tọa độ | Android Geocoder, Photon reverse, kết nối và dữ liệu số nhà |
| Vuốt map kéo cả trang | Native touch guard, khung Compose, `userScrollEnabled` của LazyColumn |
| Log WindowManager/destroySurfaces | Cần exception HTTP/API của app; đây không tự chứng minh lỗi DB |
| PHP OpenSSL nạp hai lần | Cấu hình extension PHP local; đã quan sát lúc chạy CLI |

## Kiểm tra hiện có

- `AddressSearchTest`: địa chỉ/số nhà, tọa độ, kết quả trùng/không hợp lệ, không chọn số nhà ở xa.
- `EarningsSummaryTest`: ngày hoàn tất, đúng shipper, trạng thái, qua năm, tháng nhuận, dữ liệu thiếu ngày.
- `AdminRevenueSummaryTest`: tổng tiền đơn hoàn tất, phạm vi ngày, so sánh cùng số ngày hai kỳ, tháng khác độ dài, tỷ lệ hủy và không vẽ dữ liệu tương lai.
- `php -l` cho repo và bản Apache; SQL thu nhập đã EXPLAIN thành công trên local DB.
- Admin đã qua sign-in Firebase thật, `users/me` và endpoint admin `users`.
- Những kiểm tra này không thay thế chạy APK mới trên thiết bị để xác nhận giao diện và thao tác.
