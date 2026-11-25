# Khảo sát và kế hoạch refactor Android

Cập nhật: 26/11/2026. Đây là kết quả **Bước 0 — khảo sát**, chưa refactor mã nguồn.

## 1. Hiện trạng đã kiểm tra

### Nền tảng và cấu hình

- Android app dùng Kotlin và Jetpack Compose; giao diện ứng dụng nằm trong `app/src/main/java/.../feature`. `AndroidManifest.xml` khai báo `MainApplication` và `MainActivity`.
- Có 208 file Kotlin trong `app/src/main/java` và 10 file test Kotlin trong `app/src/test`/`app/src/androidTest`.
- Gradle có hai product flavor `alwaysdata` và `local`, cùng build type `debug`/`release`; chưa có `dev`, `staging`, `prod` như kế hoạch ban đầu. Alwaysdata URL khai báo trong `app/build.gradle.kts`; local URL đọc từ `backend.local.properties` (file local bị Git bỏ qua) và có URL mẫu trong `backend.local.properties.example`.
- `network_security_config.xml` hiện chặn cleartext toàn app. URL ngrok dùng HTTPS nên không cần bật HTTP cleartext cho luồng đó. Nếu sau này muốn truy cập máy local bằng HTTP/LAN thì cần xem xét cấu hình debug riêng.
- `google-services.json` nằm trong `app/`; Firebase project được chọn bởi file này. SHA-1 cần đăng ký ở Firebase Console, không phải giá trị do Android tự lấy từ server.

### Network và xác thực hiện tại

- `data/remote/FoodHubApi.kt` tạo Retrofit/OkHttp trong object `FoodHubClient`; dùng `BuildConfig.FOODHUB_API_BASE_URL`, Gson và API tổng quát theo resource.
- `FirebaseAuthInterceptor` lấy Firebase ID token qua token provider và gắn `Authorization: Bearer ...`. `FoodHubClient` có cache token trong bộ nhớ; Firebase SDK quản lý phiên đăng nhập. Không thấy `TokenStorage`, DataStore hay EncryptedSharedPreferences.
- Một số API dùng repository, nhưng nhiều ViewModel gọi `FoodHubClient.api` trực tiếp; một số Screen cũng gọi API trực tiếp (ví dụ chi tiết món và nhận đơn shipper). `PaymentApiService` là service riêng.
- Có URL dịch vụ bên thứ ba/tài nguyên giao diện được khai báo tại nơi sử dụng (Cloudinary, bản đồ/tuyến đường, ảnh mẫu). Không nên thay tất cả URL này bằng base URL backend; phải phân biệt URL backend với URL dịch vụ ngoài.
- `okhttp-logging` có trong dependencies nhưng không tìm thấy chỗ gắn `HttpLoggingInterceptor` vào client chính. Giữ nguyên cách xử lý log cho tới khi kiểm tra rủi ro lộ dữ liệu và quyết định cấu hình debug an toàn.

### Dependency và phân tầng

- Không tìm thấy Hilt, Dagger hoặc Koin trong dependencies/source; `di/` hiện không có file.
- Lúc khảo sát ban đầu `domain/` chưa có file; Giai đoạn 2 đã thêm một luồng use case và repository contract. Các feature khác chưa chuyển sang domain.
- `data/mapper/` hiện không có file. Model đang nằm trong `core/model`; API thường dùng `JsonObject`/Gson nên DTO, domain model và UI model chưa được tách nhất quán.
- `data/repository/` có một số repository concrete, nhưng không phải đường truy cập duy nhất. Có chỗ khởi tạo repository thủ công và có chỗ gọi API/Auth trực tiếp từ ViewModel/Screen.
- ViewModel sử dụng `StateFlow` ở nhiều feature. Screen thường thu thập state bằng `collectAsState`; chưa đồng nhất sang `collectAsStateWithLifecycle`. Một số xử lý điều hướng/trạng thái UI đặt trong Screen là hợp lý; request nghiệp vụ trực tiếp từ Screen cần được chuyển dần qua ViewModel.
- `MainActivity` và `AuthViewModel` có dùng `Context` cho phần đăng nhập/khôi phục phiên. Khi refactor cần giữ Credential Manager/luồng hiện tại nhưng tránh lưu Activity/Context trong trường sống lâu của ViewModel.
- `network_security_config.xml` đã tồn tại; `release` chưa bật R8/minify (`isMinifyEnabled = false`). JUnit 4 và AndroidX tests đã có khai báo.

## 2. Luồng hiện tại

```text
Compose Screen
  -> ViewModel (không đồng nhất; đôi khi Screen gọi thẳng API)
  -> Repository hoặc FoodHubClient/ Firebase trực tiếp
  -> PHP API trên Alwaysdata hoặc backend local qua ngrok
  -> TiDB Cloud

Firebase Authentication cấp ID token; client đính kèm token để PHP xác minh.
Một số dịch vụ như FCM, Realtime Database, Cloudinary và bản đồ được client/server dùng trực tiếp theo chức năng.
```

Backend PHP chưa được thay bằng Spring Boot. Khi Spring Boot sẵn sàng, Android chỉ đổi adapter/network contract cần thiết sau khi endpoint và response được xác nhận; không đổi URL đơn thuần nếu hợp đồng API đã thay đổi.

## 3. Kế hoạch refactor đề xuất

Thực hiện tuần tự, mỗi giai đoạn là một commit Conventional Commit riêng. Sau mỗi giai đoạn chạy `assembleDebug` và `testDebugUnitTest`; chỉ tiếp tục nếu cả hai lệnh thành công. Không xóa file chỉ dựa trên tên hoặc vị trí.

1. **Network/config** (`refactor(network): centralize backend configuration`): giữ hai môi trường đang dùng (`local`, `alwaysdata`); gom base URL vào cấu hình BuildConfig, tách khởi tạo Retrofit/OkHttp vào module/factory, bổ sung API nghiệp vụ theo từng nhóm nhưng giữ wrapper cũ trong lúc chuyển để không đổi hành vi. Không thêm dev/staging/prod trước khi có URL và quy trình deploy tương ứng.
2. **Domain tối thiểu** (`refactor(domain): add repository contracts`): thêm domain models/interfaces/use cases cho một luồng có thể kiểm chứng; giữ model cũ và adapter để tránh migration diện rộng.
3. **DTO và mapping** (`refactor(data): separate api dto and domain models`): tách response/request DTO và mapper theo từng endpoint, thay dần `JsonObject`, thêm ErrorMapper mà vẫn hỗ trợ format phản hồi PHP hiện tại.
4. **Dependency injection** (`refactor(di): add hilt dependency graph`): thêm Hilt và migrate một nhóm phụ thuộc mỗi lần; giữ cấu hình singleton/API cũ tới khi mọi consumer đã chuyển.
5. **Feature presentation**: chuyển lần lượt auth → customer → restaurant → shipper → admin → chat sang `presentation/` với UiState/event. Mỗi feature có commit riêng; không gộp toàn bộ 4 vai trò vào một thay đổi.
6. **Authentication** (`refactor(auth): isolate token and identity handling`): trước tiên giữ Firebase Authentication/ID token hiện có. Chỉ thêm JWT access/refresh authenticator nếu hợp đồng Spring Boot thực tế có endpoint và quy tắc refresh; không tự giả định backend sẽ phát JWT.
7. **Dọn dẹp** (`chore(android): consolidate dependencies and release rules`): chuyển dependency về version catalog, rà soát file không dùng bằng tìm kiếm toàn project, cấu hình R8/proguard sau khi kiểm tra Firebase/Retrofit.
8. **Chất lượng** (`test(android): cover use cases repositories and viewmodels`): bổ sung test cho các tầng đã tách, thêm ktlint hoặc detekt và CI nếu phù hợp.

Mỗi giai đoạn cập nhật tài liệu này và `docs/ANDROID_ARCHITECTURE.md` với phạm vi đã hoàn thành/thực tế, không ghi dự kiến như đã xong.

## 4. Rủi ro và điểm cần giữ nguyên

- Không đổi package `com.example.dacs_3_composer` hoặc `applicationId`.
- Không đổi request/response, đăng nhập Google/email, quyền vai trò hoặc luồng đơn hàng trong các bước chỉ refactor.
- TiDB và Firebase là dịch vụ hiện hữu; không reset/import dữ liệu. `users.id` MySQL và Firebase UID là hai định danh khác nhau.
- Không đưa `backend.local.properties`, mật khẩu database, token, PayOS key hoặc service-account private key vào Git, tài liệu hay log.
- Refactor network có nguy cơ ảnh hưởng cả API Firebase/token và các luồng hỗ trợ thời gian phản hồi dài (AI); phải giữ interceptor timeout/auth hiện hữu và chạy build/test sau từng giai đoạn.
- Hiện `domain/` và `di/` rỗng nhưng đây chưa phải bằng chứng mọi code cũ không còn cần thiết; không xóa module/tệp khác nếu chưa tìm consumer.

## 5. Trạng thái bước khảo sát

- Đã đọc cấu trúc Android, Gradle/dependencies, manifest, network client/interceptor, cấu hình token và các call site trực tiếp tiêu biểu.
- Bước khảo sát ban đầu không sửa mã ứng dụng và không chạy build/test.
- Người dùng đã xác nhận kế hoạch; Giai đoạn 1 được ghi riêng bên dưới.

## 6. Giai đoạn 1 — network/config (26/11/2026)

- Đã thêm `data/remote/NetworkModule.kt` làm điểm tạo chung OkHttp/Retrofit cho FoodHub API; giữ nguyên interceptor Firebase, connection pool và timeout hiện tại.
- Đã thêm `AuthApi`, `OrderApi`, `RestaurantApi` trong `ApiServices.kt`. `FoodHubApi` tổng quát vẫn còn để tương thích; code hiện hữu chưa được chuyển hàng loạt sang interface mới.
- Logger chỉ được gắn vào debug; chỉ ghi method, HTTP status/thời gian hoặc loại exception. Không ghi URL/path, query, header, body, token hay nội dung exception.
- Thêm manifest/resource overlay cho debug để cho phép HTTP khi cần backend local trong mạng nội bộ. Release tiếp tục dùng manifest/resource chính, chặn cleartext. Ngrok vẫn dùng HTTPS.
- Giữ các flavor `local` và `alwaysdata`, BuildConfig URL, API contract, auth header, và timeout như trước.
- Kết quả: `assembleDebug` thành công, đóng gói `alwaysdataDebug` và `localDebug`. Hai tác vụ `testAlwaysdataDebugUnitTest` và `testLocalDebugUnitTest` thành công. `testDebugUnitTest` không chạy được riêng vì Gradle báo mơ hồ giữa hai flavor; đã chạy hai tác vụ cụ thể thay thế.
- Còn lại có chủ đích: các ViewModel/Screen chưa được migrate sang service nhóm; sẽ chuyển từng feature ở giai đoạn presentation/repository. Chưa chạy trên thiết bị điện thoại.

## 7. Giai đoạn 2 — domain cho lịch sử tìm kiếm (26/11/2026)

- Thêm domain model thuần Kotlin `SearchQuery`, interface `SearchHistoryRepository`, và `RecordSearchQueryUseCase` cho một luồng nhỏ hiện có.
- `RecommendationRepository` triển khai domain interface nhưng vẫn giữ `recordSearchHistory(String)` để tương thích. `SearchViewModel` dùng use case khi ghi lịch sử; tìm quán/món tiếp tục cùng luồng cũ.
- Thêm `core/common/Result.kt` và `AppError` (`Network`, `Unauthorized`, `Validation`, `Server`, `Unknown`). Use case giữ cancellation, ánh xạ lỗi dự kiến sang `Result`; repository hiện vẫn bắt lỗi mạng và trả `false` như trước.
- Thêm hai unit test cho kết quả thành công và ánh xạ IOException. `assembleDebug`, `testAlwaysdataDebugUnitTest` và `testLocalDebugUnitTest` đều thành công.
- Còn lại có chủ đích: đây mới là một luồng mẫu; các luồng khác sẽ được chuyển dần ở giai đoạn tiếp theo. Chưa chạy trên thiết bị.
