# Bàn giao phiên làm việc — FoodHub

Cập nhật **26/11/2026**, múi giờ làm việc Asia/Bangkok. Đọc trước khi tiếp tục dự án.

## Refactor Android — Giai đoạn 1 network/config — 26/11/2026

- Tạo `app/src/main/java/com/example/dacs_3_composer/data/remote/NetworkModule.kt` để tạo OkHttp/Retrofit thống nhất; logger debug chỉ ghi method/status/thời gian hoặc loại exception, không ghi URL/path, query, body hay token.
- Thêm `AuthApi`, `OrderApi`, `RestaurantApi`; giữ `FoodHubApi` generic để caller cũ chưa phải đổi ngay.
- Debug manifest/resource cho phép cleartext phục vụ backend HTTP local; release tiếp tục chặn cleartext. Ngrok vẫn nên dùng HTTPS.
- Giữ nguyên flavor `local`/`alwaysdata`, URL BuildConfig, Firebase auth header và API contract.
- `assembleDebug`: thành công cho cả hai flavor. `testAlwaysdataDebugUnitTest` + `testLocalDebugUnitTest`: thành công. Task `testDebugUnitTest` riêng mơ hồ do hai flavor.
- Chưa chạy trên điện thoại. Caller chưa được chuyển sang API nhóm; sẽ làm theo từng feature ở giai đoạn sau.

## Refactor Android — Giai đoạn 2 domain — 26/11/2026

- Thêm `SearchQuery`, `SearchHistoryRepository`, `RecordSearchQueryUseCase`, `core/common/Result.kt` và `AppError` cho luồng ghi lịch sử tìm kiếm.
- `RecommendationRepository` implement interface domain; giữ hàm cũ và kết quả Boolean để không đổi luồng. `SearchViewModel` gọi use case cho thao tác này; phần tìm/lọc quán và món chưa đổi.
- Thêm hai unit test cho thành công và map IOException. `assembleDebug`, `testAlwaysdataDebugUnitTest` và `testLocalDebugUnitTest` đều thành công; chưa thử trên điện thoại.

## Đồng bộ hồ sơ Firebase và ngrok local — 25/11/2026

- Theo yêu cầu chuyển về AnChinGo: `app/google-services.json` dùng project `dacs3-anchin-go` (948126651845); `backend/config.local.php` trên máy cũng xác minh token từ `dacs3-anchin-go`, vẫn dùng TiDB `food_delivery_app`. Cấu hình private này bị Git bỏ qua.
- Đã thay `app/google-services.json` bằng `Downloads/google-services (2).json`. File xác nhận project `dacs3-anchin-go`, package đúng và có Android OAuth fingerprints cả `b3...` lẫn `aa...`; cấu hình backend local cũng là `dacs3-anchin-go`.
- Alwaysdata chưa được cấu hình/xác minh chuyển sang AnChinGo. Đổi app JSON hoặc config local không tự cập nhật server hosting.
- Backend local nối UID Firebase mới với hồ sơ TiDB cũ khi email trong token đã được Firebase xác minh và trùng email hồ sơ. Mỗi hàng người dùng vẫn giữ ID, quyền và quan hệ nghiệp vụ.
- `localDebug` dùng URL trong `backend.local.properties`; `alwaysdataDebug` vẫn gọi Alwaysdata.
- Local URL cần path `/food-delivery-api/api/` để API parser nhận đúng route. Đã thêm lại `backend/router.php` vì PHP built-in server không đọc `.htaccess`.
- Kiểm tra localhost `/food-delivery-api/api/` trả HTTP 200 và `database=connected` trên TiDB Cloud. Chưa thử đăng nhập Google hoặc đồng bộ hồ sơ thật.
- Tunnel chưa online trong môi trường công cụ: ngrok báo không kết nối được `connect.ngrok-agent.com:443` vì kết nối proxy `127.0.0.1:9` bị từ chối; local ngrok API không có tunnel. Cần khởi động ngrok trong PowerShell có kết nối Internet.
## Sửa lỗi Crash `NoSuchMethodError: RevenueCard(...)` khi chuyển sang Tài khoản Restaurant — 25/11/2026

- **Nguyên nhân thực tế từ Crash Logcat (`11:11:34.913`)**:
  - `java.lang.NoSuchMethodError: No static method RevenueCard(Ljava/lang/String;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;II)V in class ...RevenueCardKt (...base.apk!classes15.dex)` tại `RestaurantScreen.kt:41`.
  - Do `versionCode` trong `app/build.gradle.kts` vẫn giữ `1`, trình triển khai nhanh của Android Studio trên thiết bị Samsung (`SM-A235F`, Android 12) giữ nguyên thư mục cài đặt cũ `/data/app/~~GVpCUFoqFY6Gia8pBfNuLw==/base.apk` và chỉ đẩy một phần `.overlay` DEX (`RestaurantScreenKt` được cập nhật chữ ký mới có `onClick`, trong khi `classes15.dex` vẫn giữ `RevenueCard` chữ ký cũ).
- **Khắc phục triệt để**:
  - `app/build.gradle.kts`: Tăng `versionCode = 2` (`versionName = "1.1"`) để ép Android `PackageManager` xóa sạch bộ đệm `code_cache/.overlay/` cũ và cài mới hoàn toàn toàn bộ `.dex` ngay khi nhấn **Run ▶**.
  - `RevenueCard.kt`, `WeeklyRevenueCard.kt`, `CategoryTabs.kt`, `RestaurantScreen.kt`, `RestaurantMenuScreen.kt`: Giữ nguyên các hàm chữ ký gốc (`RevenueCard`, `WeeklyRevenueCard`, `CategoryTabs`) tương thích nhị phân 100%, tách các hàm mở rộng thành `InteractiveRevenueCard`, `InteractiveWeeklyRevenueCard`, `DynamicCategoryTabs`, đồng thời lọc trùng khóa không phân biệt hoa/thường cho `LazyRow` danh mục thực đơn.
  - Đã chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.

## Nâng cấp AI Ordering Agent v2 (Đặt lại đơn cũ 1 chạm • Upsell Mở Voucher • Hẹn giờ giao) & Đồng bộ Live Alwaysdata/TiDB — 25/11/2026

- **Khắc phục triệt để lỗi chưa đặt được món qua AI trên điện thoại**:
  - Nguyên nhân: Bản APK mới trên máy gửi `"Chọn đặt 1x Mâm khai vị"` lên `https://chinh.alwaysdata.net/food-delivery-api/api/ai/meal-advisor`, nhưng trước đó Alwaysdata chưa được đồng bộ bộ mã Ordering Agent & bảng nháp trên TiDB Cloud.
  - Đã chạy migration cộng thêm `backend/database/migrations/20261125_ai_order_drafts.sql` trên TiDB Cloud (`food_delivery_app`), bổ sung `INSERT IGNORE` 3 voucher sàn (`FHGIAM15K`, `FHDEAL30K`, `FHFREESHIP` — tổng 8 voucher active), và đồng bộ toàn bộ 20 tệp PHP lên Alwaysdata (`checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261125-033019`).
  - Bổ sung nhận diện cả văn bản thuần `"Chọn đặt 1x ..."` lẫn `orderingAction` trong `AiOrderingOrchestrator.php`. Kiểm tra smoke live trên Alwaysdata với câu `"Chọn đặt 1x Mâm khai vị"` trả về `kind = ordering_quote`, tự tối ưu mã `FHDEAL30K (-30.000đ)` và dọn sạch bản nháp thử nghiệm ngay sau khi kiểm tra (`0` đơn hàng giả tạo trên production).
- **Bổ sung 3 chức năng nâng cao cho AI Ordering Agent (End-to-End Backend + DB + Android Compose)**:
  1. **Đặt lại đơn cũ 1 chạm (`One-Tap Reorder`)**:
     - `AiOrderDraftService::reorderPastOrder()` & `AiOrderingOrchestrator` (`reorder_last` + câu tự nhiên *"Đặt lại y hệt đơn hôm qua"*, *"Đặt lại đơn cũ"*): nạp món + tùy chọn từ đơn hoàn tất gần nhất (`reordered_from_order_id`), kiểm tra trạng thái mở cửa quán, tự lược món hết hàng và **đối chiếu giá niêm yết hiện hành** so với giá lịch sử (báo rõ chênh lệch `giá cũ → giá mới`).
     - Giao diện Android: Chip 1 chạm **`Đặt lại đơn cũ`** ngay đầu màn hình `MealAdvisorScreen.kt` + nhãn `Đặt lại đơn cũ · Giá hiện hành` trên `CheckoutSummaryCard`.
  2. **Gợi ý món kèm có sự đồng ý (`Consent-Based Smart Voucher Upselling`)**:
     - `AiOrderDraftService::findSmartUpsell()` & `acceptUpsell()`: khi tổng món đang thiếu `1.000đ–25.000đ` (đặc biệt `5.000đ–10.000đ`) để chạm `min_order_amount` của voucher giảm sâu cùng quán/toàn sàn, tự động tìm món phụ/nước giải khát cùng quán không vướng tùy chọn bắt buộc sao cho tổng thanh toán sau giảm **rẻ hơn cả khi chưa thêm** (`isStrictlyCheaper = true`). Tuyệt đối **không tự thêm vào giỏ** khi chưa có sự đồng ý; hiển thị thẻ `ai_upsell_suggestion_card` kèm nút 1 chạm **`+ Thêm <Món> & Áp mã <CODE>`** (hoặc nhắn *"Ừ thêm món kèm đó đi"*).
  3. **Hẹn giờ giao hàng trước (`Scheduled Delivery — scheduled_for`)**:
     - Thêm cột `scheduled_for DATETIME NULL` vào `ai_order_drafts` và `ai_order_quotes`; kiểm tra chặt chẽ giờ mở cửa thực tế của nhà hàng (`stores.open_time` .. `stores.close_time`) và thời gian chuẩn bị tối thiểu (`>= 20 phút`, `<= 48 giờ`).
     - Hỗ trợ cả ngôn ngữ tự nhiên (*"giao lúc 18h30 tối nay"*, *"hẹn giao 8h30 sáng mai"*, *"đổi lại giao ngay"*) lẫn dải `FilterChip` chọn khung giờ hợp lệ ngay trên `CheckoutSummaryCard`; khi xác nhận tạo đơn, lưu `[Hẹn giao: HH:MM DD/MM/YYYY]` vào `orders.customer_note` và `order_status_logs.note` để Nhà hàng & Shipper thấy ngay.
- **Kiểm thử đã xác nhận**:
  - `backend/tests/foodhub_ordering_agent_150_test.php`: mở rộng lên **175/175 PASS (100%)** (thêm Group H 25 kịch bản cho Reorder, Smart Upsell, Scheduled Delivery & `"Chọn đặt 1x ..."`).
  - Android Gradle `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`: **BUILD SUCCESSFUL** (cần nhấn **Run ▶** cài lại bản `app-debug.apk` mới lên điện thoại để hiển thị 3 cụm UI mới trên app; phần đặt món AI phía server Alwaysdata đã hoạt động ngay).

## Nâng cấp FoodHub AI thành AI Food Ordering Agent (Đặt món qua Hội thoại) — 25/11/2026

- **Database Migration cộng thêm** (`backend/database/migrations/20261125_ai_order_drafts.sql`):
  - Khai báo 3 bảng `ai_order_drafts`, `ai_order_quotes`, `ai_order_idempotency` (chưa tự ý chạy trên TiDB Cloud production theo đúng quy tắc không sửa DB production khi chưa được phép).
- **Backend Ordering Engine & REST Endpoints**:
  - Tạo mới `backend/ai/AiOrderDraftService.php`: State machine đặt món đầy đủ (`IDLE` → `AWAITING_FINAL_CONFIRMATION` → `ORDER_CREATED` / `PAYMENT_PENDING` + nhánh ngoại lệ), kiểm tra quyền `customer`, ràng buộc 1 nhà hàng/đơn, kiểm chứng tùy chọn món (`option_groups`/`options`), tối ưu voucher theo mức giảm VND thực tế, tính phí ship theo km + ETA có cơ sở, sinh báo giá VND nguyên kèm `confirmationToken` (`TTL = 10 phút`) và tạo đơn an toàn trong giao dịch SQL chống trùng đơn (`idempotencyKey` + `SELECT ... FOR UPDATE`).
  - Tạo mới `backend/ai/AiOrderingOrchestrator.php` & `backend/ai/ai-order-draft-route.php`; mở rộng `FoodHubSystemPrompt.php` (`v3.0.0`), `meal-advisor-route.php` và `backend/api/index.php` (`/api/ai/order-drafts`).
- **Android Jetpack Compose AI Ordering UI**:
  - Mở rộng `MealAdvisorRepository.kt`, `MealAdvisorViewModel.kt`, `MealAdvisorScreen.kt`, `MainRouteContainerUser.kt`, `FirebaseAuthInterceptor.kt`: nút **Chọn đặt** trên thẻ món, xử lý xung đột khác cửa hàng (`StoreConflictCard`), bảng **Tóm tắt đơn đặt món (`CheckoutSummaryCard`)** chỉnh số lượng/địa chỉ/voucher tối ưu/COD-PayOS, nút **`Xác nhận đặt hàng — Xđ`** kèm khóa `idempotencyKey` cố định mỗi báo giá, và thẻ hoàn tất **`OrderCreatedSuccessCard`** điều hướng thẳng sang Theo dõi đơn / Thanh toán QR PayOS.
- **Kiểm thử đã xác nhận**:
  - `backend/tests/foodhub_ordering_agent_150_test.php`: **150/150 PASS (100%)** trên đủ 7 nhóm A–G.
  - `backend/tests/foodhub_ordering_http_integration_test.php`: **8/8 PASS (100%)** qua máy chủ HTTP PHP local.
  - Toàn bộ bộ test hồi quy PHP (`foodhub_conversation_120_test.php` 120/120, `foodhub_phase5_quality_120_test.php` 120/120, `foodhub_meal_quality_test.php` 82/82, `foodhub_vietnamese_context_test.php` 85/85, `recommendation_system_test.php` 31/31): **100% PASS**.
  - Android Gradle `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest` (gồm `OrderingAgentViewModelTest`, `MealAdvisorScreenTest`, `HomeFeedCompositionTest`, `DeliveryEstimateTest`): **BUILD SUCCESSFUL**.
  - **Chưa đồng bộ lên Alwaysdata / TiDB production**: Cần sự cho phép của người dùng trước khi chạy migration `20261125_ai_order_drafts.sql` và đồng bộ tệp PHP lên Alwaysdata.

## Sửa lỗi Crash `NoSuchMethodError: cacheTtlMs$default` khi mở Ứng dụng — 25/11/2026

- **Nguyên nhân**: Các hàm `suspend inline fun <reified T> list/get/create/update` trong `FoodHubApi.kt` trước đó gọi trực tiếp các hàm `@PublishedApi internal` (`cacheTtlMs` có tham số mặc định `filters = emptyMap()`), khiến trình biên dịch nhúng lời gọi cầu nối tĩnh `FoodHubClient.cacheTtlMs$default(...)` vào từng ViewModel và gây lỗi `NoSuchMethodError` khi cài đặt tăng dần / Apply Changes trên thiết bị thật (`SM-A235F`).
- **Khắc phục**:
  - `app/src/main/java/com/example/dacs_3_composer/data/remote/FoodHubApi.kt`: Loại bỏ hoàn toàn `@PublishedApi internal` và chuyển toàn bộ logic bộ nhớ đệm + mạng vào các phương thức chuẩn không `inline` (`listTyped`, `getTyped`, `createObject`, `updateObject`); giữ `inline fun <reified T>` làm lớp bọc mỏng 1 dòng truyền `T::class.java`, tương thích nhị phân 100% với mọi bản build tăng dần.
  - Chạy `.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest`: **BUILD SUCCESSFUL** (45/45 tasks), sinh bản `app-debug.apk` mới hoàn toàn không còn `.dex` cũ.

## Phát triển Danh mục Món ăn Động & Phân loại Trực tiếp trên Trang chủ Khách hàng — 24/11/2026

- **Chuyển đổi Danh mục món ăn trên Trang chủ từ 5 ảnh tĩnh cố định sang hệ thống phân loại động theo dữ liệu thực tế TiDB**:
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/home/HomeViewModel.kt`: Bổ sung `HomeCategory(name, dishCount, imageUrl)`, `categories: StateFlow`, `selectedCategory: StateFlow`, `toggleCategory()`, `setCategoryFilter()` cùng các hàm thuần `buildCategories()`, `filterDishesByCategory()`, `filterRestaurantsByCategory()` — tự động tổng hợp toàn bộ 17+ danh mục món ăn đang mở bán từ TiDB (gộp không phân biệt hoa/thường, lấy ảnh thật đại diện trên Cloudinary và sắp xếp theo số lượng món giảm dần).
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/home/components/CategorySection.kt`: Nâng cấp giao diện tông trắng/xanh `#2159BC` gồm thẻ **"Tất cả"** + hàng cuộn ngang toàn bộ danh mục động kèm số lượng món (`"40 món"`, `"15 món"`, `"6 món"`...) và trạng thái viền xanh khi đang chọn; bấm **"Xem tất cả"** mở `AllCategoriesBottomSheet` dạng lưới 3 cột hiển thị trực quan tất cả nhóm món kèm nút mở nhanh toàn bộ thực đơn.
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/home/HomeScreen.kt`: Khi chạm vào một danh mục, lọc tức thì (`0 ms`) cả khu vực món ăn (**`"Danh mục: <Tên> (Có X món ăn...)"`** kèm chip **"Bỏ lọc"**) lẫn danh sách cửa hàng bên dưới (**`"Quán có món <Tên> (Y)"`**); chạm lại danh mục đang chọn hoặc **"Tất cả"** sẽ khôi phục toàn bộ bảng tin cá nhân hóa.
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/home/components/DeliciousDishSection.kt` (`AllDishesScreen`) & `MainRouteContainerUser.kt`: Đồng bộ danh mục đang chọn sang màn hình **Tất cả món ăn**, bổ sung thanh tìm kiếm nhanh trong danh mục và dải `FilterChip` chuyển đổi linh hoạt giữa các nhóm món.
- **Kiểm thử đã xác nhận**:
  - Bổ sung unit test cho `buildCategories`, `filterDishesByCategory` và `filterRestaurantsByCategory` trong `HomeFeedCompositionTest.kt`.
  - Android Gradle `:app:compileDebugKotlin` & `:app:testDebugUnitTest`: **BUILD SUCCESSFUL** (cần build/cài APK mới lên điện thoại để trải nghiệm giao diện Danh mục món ăn động mới).

## Mở rộng Đợt 2: Thêm 5 Nhà hàng + 50 Món ăn (TiDB/Cloudinary) & Nâng cấp 300 Mẫu Hội thoại Huấn luyện AI — 24/11/2026

- **Thêm 5 nhà hàng đặc trưng mới + 50 món ăn mới trên TiDB Cloud (`food_delivery_app`) & Cloudinary (`dhscw17vq`)**:
  1. `Cơm Tấm Sườn Nướng Sài Gòn Xưa` (`store_id = 90002`, Hải Châu, Đà Nẵng) — 10 món (`Cơm tấm`, `Món chính`, `Món phụ`, `Khai vị`, `Nước giải khát`, `Tráng miệng`).
  2. `Bếp Hàn Seoul — Tokbokki & Gà Rán` (`store_id = 90003`, Sơn Trà, Đà Nẵng) — 10 món (`Món Hàn`, `Món chính`, `Khai vị`, `Ăn vặt`, `Món phụ`, `Nước giải khát`).
  3. `Hải Sản & Ốc Ngon Biển Mỹ Khê` (`store_id = 90004`, Sơn Trà, Đà Nẵng) — 10 món (`Hải sản`, `Món chính`, `Đồ nướng`, `Món phụ`, `Nước giải khát`).
  4. `Góc Nhật Bản — Sushi, Ramen & Bento` (`store_id = 90005`, Hải Châu, Đà Nẵng) — 10 món (`Món Nhật`, `Món chính`, `Ăn vặt`, `Khai vị`, `Món phụ`, `Nước giải khát`, `Tráng miệng`).
  5. `Tiệm Chè, Sinh Tố & Trái Cây Nhiệt Đới` (`store_id = 90006`, Thanh Khê, Đà Nẵng) — 10 món (`Chè`, `Sinh tố`, `Tráng miệng`, `Nước giải khát`, `Ăn vặt`).
  - Đã tải **60 ảnh thật** (10 ảnh bìa/logo quán + 50 ảnh món ăn) lên Cloudinary (`foodhub_catalog/stores`, `foodhub_catalog/dishes`) và tự động xóa cache danh mục trên Alwaysdata (`foodhubInvalidateJsonCache('stores', 'dishes', 'categories')`).
  - **Tổng quy mô thực đơn mở bán trên TiDB Cloud**: **15 nhà hàng đang mở**, **129 món ăn** thuộc **17 danh mục** (`Món chính` 40, `Nước giải khát` 15, `Ăn vặt` 11, `Tráng miệng` 10, `Khai vị` 10, `Hải sản` 6, `Món phụ` 6, `Món chay` 5, `Món Hàn` 4, `Cơm tấm` 4, `Món Nhật` 4, `Sinh tố` 3, `Chè` 3, `Trà sữa` 3, `Cà phê` 2, `Đồ nướng` 2, `Bánh ngọt` 1).
- **Bổ sung thêm 100 mẫu hội thoại JSONL mới (`docs/AI_TRAINING_DATASET.jsonl`, dòng `201–300`, nâng tổng lên `300` mẫu)**:
  - Bao phủ 10 nhóm ngữ cảnh chuyên sâu (0 dòng trùng lặp, 0 câu mở đầu trùng lặp): 5 nhóm tư vấn món & ghép combo 1 chính + 1 phụ theo đúng thực đơn/giá của 5 nhà hàng mới (`Cơm Tấm Sài Gòn Xưa`, `Bếp Hàn Seoul`, `Hải Sản Biển Mỹ Khê`, `Góc Nhật Bản`, `Tiệm Chè & Sinh Tố Nhiệt Đới`), cộng 5 nhóm so sánh liên quán toàn sàn, phương ngữ Bắc–Trung–Nam/teencode đa lượt, xử lý sự cố thực tế, bài toán phí ship theo km/voucher và quản trị đa vai trò/bảo mật SQL/độ tuổi.
  - Mở rộng `backend/ai/MenuKnowledge.php` & `backend/ai/FoodHubSupport.php` hỗ trợ cầu nối danh mục động, từ khóa tìm kiếm và phân loại món chính/phụ cho 6 danh mục mới (`Cơm tấm`, `Món Hàn`, `Hải sản`, `Món Nhật`, `Chè`, `Sinh tố`) — bản sửa PHP AI đang ở repo local, sẵn sàng đồng bộ lên Alwaysdata khi người dùng yêu cầu.
- **Kiểm thử đã xác nhận**:
  - `backend/tests/foodhub_vietnamese_context_test.php`: **85/85 PASS** (xác minh đủ **300 mẫu JSONL hợp lệ**, 0 trùng lặp, tuân thủ hàng rào an toàn & phân loại đúng danh mục mới).
  - `backend/tests/foodhub_meal_quality_test.php` (**82/82 PASS**), `backend/tests/menu_knowledge_test.php` (**27/27 PASS**), `php -l` sạch 100%.

## Mở rộng 200 Mẫu Hội thoại Huấn luyện AI & Mở Trang Profile Món ăn từ Gợi ý AI — 24/11/2026

- **Bổ sung thêm 70 mẫu hội thoại JSONL mới (`docs/AI_TRAINING_DATASET.jsonl`, dòng `131–200`, tổng `200` mẫu)**:
  - Bao phủ 7 nhóm chuyên sâu không trùng lặp: (1) Trải nghiệm mở trang Profile món ăn từ gợi ý AI, giỏ hàng 1 quán/đổi nhà hàng, cá nhân hóa lịch sử tìm kiếm & giờ mở cửa quán; (2) Ghép combo theo thời tiết, tập gym, ăn xế, chay ít dầu mỡ, trẻ em không ăn cay & ngân sách gồm phí ship; (3) Ngôn ngữ đời thường nâng cao (ẩn ý rát họng/chạy deadline/ngày rằm, không dấu, câu hỏi gộp 2 ý, tự đổi ngân sách giữa chừng); (4) Thanh toán PayOS, COD, điều kiện áp mã giảm giá & đối soát minh bạch; (5) Theo dõi trạng thái đơn, bản đồ tài xế, đặt nhầm địa chỉ & xử lý sự cố thực tế; (6) Dị ứng tôm cua/lactose/đậu phộng, chế độ thuần chay (vegan), kiêng heo/bò & ranh giới y khoa; (7) Nghiệp vụ Nhà hàng/Shipper/Admin & chống Prompt Injection / bảo vệ dữ liệu riêng tư.
  - Bổ sung thêm 3 chủ đề FAQ runtime trong `backend/ai/FoodHubKnowledgeBase.php` & `backend/ai/FoodHubSupport.php` (`dish_profile_navigation`, `seller_revenue_and_categories`, `driver_vehicle_breakdown_or_incident`) và cập nhật `how_to_order` (chưa đồng bộ bản sửa 2 file PHP này lên Alwaysdata vì người dùng chưa yêu cầu).
- **Nhấn thẻ món ăn do AI gợi ý mở trực tiếp Trang Profile Món ăn (`DishDetailScreen`)**:
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/ai/MealAdvisorScreen.kt`: Đổi callback `onOpenRestaurant` sang `onOpenDish(item.dish.id)` kèm `onSelectDish(item.dish)` trong `DishCard` để mở trang Chi tiết món ăn thay vì trang thực đơn nhà hàng.
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/MainRouteContainerUser.kt`: Điều hướng `meal_advisor` sang `dish_detail/{dishId}` kèm truyền sẵn `selectedAiDish` vào `initialDish` để hiển thị tức thì ảnh, giá, mô tả và nút Thêm vào giỏ mà không phải chờ tải lại; cập nhật tương ứng ở `MainRouteContainerAdmin.kt`, `MainRouteContainerShipper.kt`, `MainRouteContainerRestaurant.kt` và test UI `MealAdvisorScreenTest.kt`.
- **Kiểm thử đã xác nhận**:
  - PHP: `backend/tests/foodhub_vietnamese_context_test.php` (**79/79 PASS**, xác minh đủ 200 mẫu JSONL hợp lệ, 0 dòng trùng lặp, tuân thủ hàng rào an toàn), `foodhub_conversation_120_test.php` (**120/120 PASS**), `foodhub_phase5_quality_120_test.php` (**120/120 PASS**), `foodhub_meal_quality_test.php` (**82/82 PASS**), `foodhub_support_test.php` (**PASS**).
  - Android Gradle `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`: **BUILD SUCCESSFUL** (cần build/cài APK mới lên điện thoại để trải nghiệm thao tác chạm mở trang Profile món từ khung gợi ý AI).

## Tối ưu Tốc độ Truyền Dữ liệu DB/API & Huấn luyện AI Đọc hiểu Hội thoại Tự nhiên — 24/11/2026

- **Nguyên nhân độ trễ 3–4 giây trước đây** (đã đo thực tế trên Alwaysdata Paris ↔ TiDB Cloud Singapore `ap-southeast-1`):
  - Mỗi kết nối lạnh tốn **`1.301,7 ms`** (gồm bắt tay TLS + `SET SESSION time_zone` + truy vấn thừa `SHOW SESSION STATUS LIKE 'Ssl_cipher'` `~160 ms` trên mọi request) cộng thêm `160 ms` tra `account($uid)` và `170–195 ms` truy vấn chính; khi mở Trang chủ bắn 6 request song song tạo tranh chấp kết nối lên đến 3–4s, cộng thêm lỗi N+1 truy vấn ở `GET /api/conversations`.
- **Tối ưu hóa tốc độ Backend & Android**:
  - `backend/database/connection.php`: Bật `PDO::ATTR_PERSISTENT` dùng lại kết nối ấm kèm tự kết nối lại khi hết hạn, bật `PDO::ATTR_EMULATE_PREPARES => true` (giảm 1 vòng RTT mỗi câu SQL) và chỉ kiểm tra `Ssl_cipher` 1 lần đầu trên mỗi worker (vẫn xác minh nghiêm ngặt CA + chứng chỉ TLS).
  - `backend/runtime.php` & `backend/api/index.php`: Nén phản hồi Gzip (`ob_gzhandler`), khởi tạo `$pdo` dạng **lazy** (chỉ mở kết nối TiDB khi cache miss hoặc ghi dữ liệu), bổ sung cache JSON riêng tư ngoài web root cho `account($uid)` (120s), `stores` (90s), `dishes` (90s), `categories` (90s), `vouchers` (60s), `addresses` (120s) kèm **cơ chế tự xóa cache tức thì (write-through invalidation)** ngay khi thêm/sửa/xóa món, quán, địa chỉ, voucher hoặc đặt đơn; gộp truy vấn `GET /api/conversations` từ `4×N+1` xuống **3 câu bulk `IN (...)`**.
  - `app/src/main/java/com/example/dacs_3_composer/data/remote/FoodHubApi.kt`: Bổ sung bộ nhớ đệm Firebase ID Token trên RAM (tránh nghẽn luồng `Tasks.await` khi gọi song song), `OkHttp ConnectionPool(8, 5m)` giữ ấm kết nối HTTP/2, và bộ nhớ đệm phản hồi ngắn hạn (60s) tự hủy ngay khi `create`/`update`/`delete` để chuyển tab hiển thị tức thì `0 ms`.
- **Nâng cấp AI đọc hiểu đoạn chat đời thường & đa lượt**:
  - `backend/ai/FoodHubKnowledgeBase.php`: Mở rộng viết tắt đời thường (`k` → `không` nhưng giữ nguyên `4 km`, `nc` → `nước`, `cf/cafe` → `cà phê`, `ts` → `trà sữa`, `bh/bjo` → `bây giờ`), nhận diện ngữ cảnh ẩn ý (`bụng kêu`, `tụt huyết áp`, `buồn miệng`, `khát nước`, `khô cổ`) và thêm 2 chủ đề mới (`change_or_modify_items` khi bấm nhầm món, `payos_pending_sync` khi chuyển khoản xong vẫn báo chờ thanh toán).
  - `backend/ai/ConversationContext.php`, `FoodHubSupport.php`, `MealChat.php`, `FoodHubSystemPrompt.php`, `MealAdvisor.php`: Thêm `analyzeDiscourse()` nhận diện hành động hội thoại (`self_correction` *"ý mình là..."*, `hesitation_or_comparison` *"phân vân..."*, `natural_confirmation` *"ok gửi đi"*, `follow_up_reference`), chấp nhận xác nhận tạo phiếu hỗ trợ tự nhiên (*"ok gửi đi"*, *"ok tạo phiếu hỗ trợ giúp mình nha"*) và mở rộng tham chiếu món ẩn ý (*"cái thứ 2 giá sao zậy"*, *"ly đầu tiên bán ở quán nào z"*).
  - `docs/AI_TRAINING_DATASET.jsonl`: Bổ sung thêm 12 hội thoại đa lượt thực tế và mở rộng thêm **50 mẫu hội thoại JSONL mới** theo 6 nhóm ngữ cảnh (Combo 1 chính + 1 phụ cùng quán trên 5 quán mới, Danh mục động TiDB, Ngôn ngữ đời thường/viết tắt/phương ngữ Bắc–Trung–Nam/tự đính chính, Khoảng cách & phí ship lũy tiến theo km, Trí tuệ cảm xúc & xử lý sự cố 2 bước/dị ứng, Hỗ trợ đa vai trò Restaurant/Shipper/Admin & bảo mật OTP/đơn người khác) → tổng **130 mẫu JSONL hợp lệ**, không trùng lặp.
- **Kiểm thử & Kết quả đo thực tế**:
  - `backend/tests/foodhub_vietnamese_context_test.php`: **76/76 PASS** (xác minh toàn bộ 130 mẫu JSONL hợp lệ chuẩn OpenAI/Groq chat format, không trùng lặp, không tự nhận là người thật, không bịa hành động); toàn bộ **12/12 bộ test PHP** & `php -l` đạt **100%**.
  - Android Gradle `:app:compileDebugKotlin` & `:app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
  - ✅ **Đã đồng bộ toàn bộ 17 file lên Alwaysdata** (`checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261124-034507`).
  - **Số liệu tốc độ đo thực tế trên Alwaysdata**: đọc 79 món + 10 quán từ cache server giảm từ **`~1.660 ms` xuống `7,51 ms`** (`0` vòng RTT sang Singapore), kết nối ấm TiDB giảm từ **`1.301,7 ms` xuống `157,99 ms`**, dung lượng JSON 79 món nén Gzip giảm **`87,5%`** (`71.681 bytes` → `8.943 bytes`).

## Khoảng cách Thực tế, Ước tính Thời gian Giao & Quy luật Tính phí Ship theo Km — 24/11/2026

- **Tính khoảng cách thực tế, thời gian giao dự kiến & hiển thị trên UI Khách hàng**:
  - Tạo mới `app/src/main/java/com/example/dacs_3_composer/feature/shared/location/DeliveryEstimate.kt`: Tính khoảng cách Haversine làm tròn 1 chữ số thập phân từ tọa độ quán `(latitude, longitude)` tới vị trí thực tế của người dùng (`rememberUserCoordinates`: ưu tiên GPS thiết bị tại Việt Nam, dự phòng sang địa chỉ giao hàng mặc định đã lưu để tránh lỗi GPS mặc định ở Mỹ trên máy ảo), ước tính thời gian giao (`prepMinutes + ceil(km × 2.5) + 3 phút`) và phí ship theo km.
  - `HomeScreen.kt`, `SearchScreen.kt`, `RestaurantItem.kt`, `RestaurantDetailScreen.kt`, `RestaurantInfoUserCard.kt`, `RestaurantDetail.kt`: Hiển thị đầy đủ **⭐ Đánh giá · ⏱️ Thời gian giao dự kiến · 📍 Khoảng cách (km) · 🚚 Ship dự kiến** ngay dưới banner và thông tin quán; sắp xếp mục **"Quán ngon gợi ý"** ưu tiên quán gần người dùng trước.
- **Quy luật tính tiền ship theo số km (đồng nhất Android + Backend PHP + AI CSKH)**:
  - Quy luật: **`0 – 3.0 km`: `15.000đ`**; **trên `3.0 km` cứ tăng mỗi `1 km` (làm tròn lên theo bậc km) cộng thêm `10.000đ`** (`3.1–4.0 km` = `25.000đ`, `4.1–5.0 km` = `35.000đ`, ...).
  - `CartScreen.kt`: Tự động tính lại khoảng cách, thời gian giao và phí ship khi khách đổi địa chỉ nhận hàng, hiển thị rõ công thức ngay tại dòng Phí giao hàng và cập nhật đúng mức giảm cho mã `free_shipping`.
  - `backend/api/index.php`: Bổ sung `foodhubDistanceKm()`, `foodhubShippingFee()`, `foodhubDeliveryEstimate()`; áp dụng trực tiếp vào `POST /api/orders` (`orders.delivery_fee`, `orders.total_amount`, `deliveries.delivery_fee` thu nhập shipper, voucher `free_shipping`, PayOS) và hỗ trợ `?lat=&lng=` trên `GET /api/stores`.
  - `backend/ai/FoodHubSupport.php`: Cập nhật câu trả lời phí giao hàng của FoodHub AI theo đúng quy luật km mới.
  - Tạo mới unit test `app/src/test/java/com/example/dacs_3_composer/feature/shared/location/DeliveryEstimateTest.kt`.
- **Kiểm thử & Triển khai**:
  - Android Gradle `:app:compileDebugKotlin` & `:app:testDebugUnitTest` (`DeliveryEstimateTest`): **BUILD SUCCESSFUL** (cần cài APK mới lên điện thoại để thấy giao diện khoảng cách/thời gian/phí ship mới).
  - PHP lint & 4 bộ test hỗ trợ (`foodhub_conversation_120_test.php`, `foodhub_support_test.php`, `foodhub_phase5_quality_120_test.php`, `foodhub_vietnamese_context_test.php`) đạt **100%**.
  - ✅ Đã đồng bộ lên Alwaysdata (`checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261124-025944`).

## Biểu đồ Doanh thu Chi tiết Nhà hàng, Ảnh thật Món bán chạy & Nút Robot Trợ lý FoodHub — 24/11/2026

- **Biểu đồ doanh thu Nhà hàng xem chi tiết giống Admin**:
  - `app/src/main/java/com/example/dacs_3_composer/feature/restaurant/home/components/WeeklyRevenueCard.kt`: Tái sử dụng `AdminRevenueBars` + `adminRevenueSummary` cho phép chạm trực tiếp từng cột (`T2–CN`) trên trang Tổng quan để xem ngày, số đơn và doanh thu của ngày đó; nối nút `"Chi tiết"` (và thẻ `RevenueCard`) mở màn hình chi tiết.
  - Tạo mới `app/src/main/java/com/example/dacs_3_composer/feature/restaurant/home/RestaurantRevenueDetailScreen.kt` (định tuyến `restaurant_revenue_detail` trong `MainRouteContainerRestaurant.kt`): chuyển đổi `Tuần` / `Tháng`, điều hướng kỳ trước / kỳ sau, tổng doanh thu đơn hoàn tất & `%` tăng trưởng so với kỳ trước, 4 thẻ chỉ số (`Đơn tạo trong kỳ`, `Giao thành công`, `Giá trị đơn trung bình`, `Tỷ lệ hủy`), biểu đồ cột tương tác theo ngày, biểu đồ đường tăng trưởng lũy kế (`AdminRevenueLine`), danh sách đơn hoàn tất của ngày đang chọn & đơn mới trong kỳ kèm hộp thoại xem chi tiết từng đơn.
- **Hiển thị đúng ảnh thật của món trong danh sách "Món ăn bán chạy"**:
  - `backend/api/index.php` (`GET /api/orders`): `LEFT JOIN dishes d ON d.id = oi.dish_id` trả thêm trường `imageUrl` cho từng `order_items`.
  - `Order.kt`, `RestaurantHomeViewModel.kt`, `TopSellingDishItem.kt`: Bổ sung `OrderItem.imageUrl`, đối chiếu ảnh thật từ cả `OrderItem.imageUrl` lẫn danh sách món của quán theo `dishId` và tên món (giữ nguyên thứ tự xếp hạng `#1, #2, ...`), load qua Coil `ImageRequest` có `crossfade` thay vì chỉ hiện 1 ảnh cố định `R.drawable.images1`.
- **Thu gọn nút Trợ lý FoodHub của Nhà hàng thành icon Robot**:
  - `MainRouteContainerRestaurant.kt`: Thay `ExtendedFloatingActionButton("Trợ lý FoodHub")` cồng kềnh bằng nút tròn `FloatingActionButton` (`54.dp`, màu `#2159BC`) mang biểu tượng Robot `Icons.Filled.SmartToy` kèm chấm xanh trạng thái gọn mắt.
- **Kiểm thử & Triển khai**:
  - Android Gradle `:app:compileDebugKotlin` & `:app:testDebugUnitTest`: **BUILD SUCCESSFUL** (chưa cài APK trực tiếp lên điện thoại trong phiên này — cần build/cài bản APK mới để thấy toàn bộ thay đổi UI Android).
  - ✅ Đã đồng bộ `backend/api/index.php` lên Alwaysdata (`checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261124-022847`).

## Mở rộng 5 Cửa hàng + 50 Món ăn (TiDB/Cloudinary), Danh mục linh hoạt & AI học Danh mục động — 24/11/2026

- **Mở rộng dữ liệu trên TiDB Cloud (`food_delivery_app`) & ảnh trên Cloudinary (`dhscw17vq`)**:
  - Tạo **5 cửa hàng mới** (`Phở & Bún Bò Cố Đô` #60002, `Trà Sữa & Cà Phê Mây House` #60003, `Bánh Mì & Xôi Nóng 3 Miền` #60004, `Bếp Chay An Lạc` #60005, `Pizza & Burger Street Kitchen` #60006) kèm **50 món ăn mới** (10 món/quán) và **60 ảnh thật** (ảnh bìa/logo quán + ảnh món ăn) upload vĩnh viễn lên Cloudinary (`foodhub_catalog/...`).
  - Tổng danh mục đang mở bán trong TiDB tăng lên **79 món ăn** thuộc **11 danh mục**: `Món chính` (35), `Nước giải khát` (10), `Ăn vặt` (8), `Khai vị` (7), `Tráng miệng` (6), `Món chay` (5), `Trà sữa` (3), `Cà phê` (2), `Món phụ` (1), `Đồ nướng` (1), `Bánh ngọt` (1).
- **Mở rộng danh mục tự do cho Nhà hàng (Android + Backend)**:
  - `app/src/main/java/com/example/dacs_3_composer/feature/restaurant/menu/components/CategoryTabs.kt`: Đổi `Row` cố định 2 mục (`Món chính`, `Khai vị`) sang `LazyRow` cuộn ngang hiển thị động tất cả danh mục thật của quán.
  - `app/src/main/java/com/example/dacs_3_composer/feature/restaurant/menu/RestaurantMenuScreen.kt` & `RestaurantViewModel.kt`: Nâng cấp `DishDialog` cho phép chọn nhanh từ danh sách gợi ý (`Món chính`, `Khai vị`, `Ăn vặt`, `Nước giải khát`, `Tráng miệng`, `Trà sữa`, `Cà phê`, `Đồ uống`, `Món phụ`, `Món chay`, `Đồ nướng`, `Bánh ngọt`, `Combo` + danh mục hiện có của quán) **hoặc tự nhập bất kỳ danh mục mới nào**, hiển thị danh mục trên từng thẻ món và cập nhật UI ngay khi sửa món.
  - `backend/api/index.php`: Chuẩn hóa khoảng trắng & so khớp không phân biệt hoa/thường khi tự tạo `menu_categories` trong `POST/PUT /api/dishes`, hỗ trợ lọc `GET /api/dishes?category=<tên danh mục>` và thêm `GET /api/categories`.
- **AI học danh mục món động trực tiếp từ TiDB (`menu_categories.name`)**:
  - `backend/ai/MenuKnowledge.php`: Thêm `categories()` đọc trực tiếp danh mục đang bán từ TiDB, `matchedCategories()` khớp thông minh theo tên danh mục đầy đủ / cụm cốt lõi (`"món giải khát"` → `"Nước giải khát"`, hỗ trợ cả danh mục hoàn toàn mới nhà hàng tự tạo như `"Sinh tố tươi"`) / cầu nối ngữ nghĩa tiếng Việt, `filterByCategories()`, ưu tiên SQL trong `candidates()`, và mở rộng `isSideDish()` / `isMainDish()` cho đồ uống/trà sữa/cà phê/bánh ngọt/món chay/đồ nướng.
  - `backend/ai/meal-advisor-route.php`, `backend/ai/FoodHubSupport.php`, `backend/ai/FoodHubSystemPrompt.php`, `backend/ai/MealAdvisor.php`: Truyền `menuSummary.categories` cho Groq, lọc chặt món đúng danh mục khi khách hỏi theo thể loại, tăng cache version lên `10`, bổ sung từ khóa đồ uống/thể loại vào bộ định tuyến nhanh.
  - `docs/AI_TRAINING_DATASET.jsonl`: Bổ sung 3 mẫu huấn luyện theo danh mục đồ uống/chay (tổng **68 mẫu JSONL hợp lệ**).
- **Kiểm thử & Triển khai**:
  - `backend/tests/foodhub_meal_quality_test.php`: **82/82 PASS** (gồm test học danh mục động `Nước giải khát` → Coca-Cola và danh mục tùy biến mới `Sinh tố tươi`).
  - Toàn bộ **12/12 bộ test PHP** & `php -l` đạt **100%**.
  - Android Gradle `:app:compileDebugKotlin`: **BUILD SUCCESSFUL** (chưa cài APK lên điện thoại trong phiên này — cần cài bản build mới trên điện thoại để thấy UI danh mục mở rộng ở màn hình Nhà hàng).
  - ✅ **Đã đồng bộ lên Alwaysdata (24/11/2026)**: 15 file (`checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261124-015904`, Groq `openai/gpt-oss-120b`). Kiểm thử live 4/4 truy vấn danh mục động (`Nước giải khát`, `Trà sữa`/`Cà phê`, `Món chay`, `Combo trưa`) trên TiDB 79 món + Groq đều trả về đúng danh mục và đúng cửa hàng.

## Huấn luyện AI gợi ý Combo 1 Món chính + 1 Món phụ cùng quán — 23/11/2026

- **Nguyên nhân lỗi trước đó** (khi khách hỏi *"hãy gợi ý combo món ăn trưa nay cho tôi"* hoặc *"ghép giúp em 1 set đồ ăn trưa gồm 1 món chính và 1 món phụ dưới 100k"*):
  1. `MenuKnowledge::mealIntent()` chưa loại bỏ từ chỉ thời gian/đệm (`nay`, `trua nay`, `toi nay`, `em/anh/chi`, `tiet kiem nhat`), làm `canFilter = false` khiến bộ ghép combo trả về rỗng.
  2. `MenuKnowledge::wantsMeal()` chưa bao phủ các cách hỏi combo/bữa tối/sáng/set món/món chính + món phụ.
  3. `meal-advisor-route.php` chưa kiểm tra hậu kiểm bắt buộc combo phải có đúng **1 Món chính + 1 Món phụ cùng quán**.
  4. `FoodHubKnowledgeBase.php` có mẫu regex `do an.*oi` thiếu ranh giới từ `\b`, bắt nhầm `"đồ ăn ... dưới 100k"` (`duoi` chứa `oi`) thành sự cố chất lượng món.
- **Thay đổi đã thực hiện**:
  - `backend/ai/MenuKnowledge.php`: Mở rộng `wantsMeal()`, `mealQuestion()` (hỗ trợ lượt tiếp theo `"đổi combo khác"`), `mealIntent()`; thêm `isSideDish()` / `isMainDish()` và nâng cấp `meal()` luôn ưu tiên ghép **1 Món chính + 1 Món phụ cùng quán** trong ngân sách (kèm xoay vòng combo theo `$excludeIds`), cập nhật `mealAnswer()` nêu rõ món chính & món phụ.
  - `backend/ai/FoodHubSystemPrompt.php`: Bổ sung quy tắc bắt buộc cho `kind=meal` = `1 Món chính (ids[0]) + 1 Món phụ (ids[1]) cùng quán` (vẫn giữ độ dài prompt < 4500 ký tự).
  - `backend/ai/meal-advisor-route.php`: Tăng cache version lên `9`, tự sắp thứ tự `[Món chính, Món phụ]`, hậu kiểm chặt cùng quán + đúng ngân sách + đủ 1 chính & 1 phụ, nếu sai tự fallback sang `MenuKnowledge::meal()`, và hỗ trợ render câu trả lời tự nhiên cho cả `groq` và `cache`.
  - `backend/ai/FoodHubKnowledgeBase.php`, `backend/ai/FoodHubSupport.php`, `backend/ai/MealAdvisor.php`: Sửa regex `food_quality_issue` có `\b` tránh bắt nhầm `"đồ ăn dưới..."`, thêm `combo|set|suat|khau phan|bua` vào bộ lọc chuyển thẳng sang `MealAdvisor` và bổ sung `tiet kiem nhat`.
  - `docs/AI_TRAINING_DATASET.jsonl`: Bổ sung 5 mẫu huấn luyện combo (tổng **65 mẫu JSONL hợp lệ**).
- **Kiểm thử**:
  - `backend/tests/foodhub_meal_quality_test.php`: **72/72 checks PASS** (gồm 8 biến thể combo, xoay vòng `"đổi combo khác"`, nêu rõ món chính/món phụ).
  - `backend/tests/foodhub_vietnamese_context_test.php`: **63/63 checks PASS** (gồm 3 test hồi quy chống bắt nhầm combo/set đồ ăn).
  - Toàn bộ **12/12 bộ test PHP** và `php -l` đạt **100%**.
  - ✅ **Đã đồng bộ lên Alwaysdata (23/11/2026)**: 15 file (`checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261123-085222`, Groq `openai/gpt-oss-120b`). Kiểm thử live 4/4 truy vấn combo trên TiDB + Groq đều trả về đúng **1 Món chính + 1 Món phụ cùng quán** trong ngân sách.

## Mở rộng Ngữ cảnh Tiếng Việt, Knowledge Base & Bộ dữ liệu Training (v2.2.0) — 23/11/2026

- **Kho kiến thức tiếng Việt FoodHub (`backend/ai/FoodHubKnowledgeBase.php`)**:
  - Bổ sung 19 chủ đề thực tế cho cả 3 vai trò (Khách hàng, Shipper, Nhà hàng): đổi địa chỉ/SĐT, đặt 2 quán cùng giỏ, hướng dẫn đặt món, ghi chú cho quán, liên hệ quán/shipper, báo đã giao nhưng chưa nhận / lỡ cuộc gọi, chất lượng đồ ăn (nguội/sống/dị vật/ôi thiu/sai ghi chú), lỗi không áp được voucher, mã freeship, quán tự hủy đơn PayOS, quên mật khẩu/đăng nhập, đăng ký đối tác Quán/Shipper, giờ mở cửa, theo dõi bản đồ, đánh giá quán, hóa đơn VAT, sự cố Shipper tại quán/khách không nghe máy, vận hành thực đơn & tạm đóng quán của Seller.
  - Chuẩn hóa tiếng Việt đời thường, teencode và vùng miền Bắc–Trung–Nam (`dchi`, `sdt`, `ck`, `cod`, `fs`, `ntn`, `tx`, `hok`, `hem`, `ib`, `bùn` ≠ `bún`).
  - Nhận diện ngữ cảnh thời tiết/tâm trạng/đói bụng (`trời mưa lạnh`, `cuối tháng hết tiền`, `đói quá`, `đêm khuya`, `ăn nhẹ eat clean`) để chuyển thẳng sang tư vấn món thật từ TiDB thay vì rơi vào `clarify`.
- **Cập nhật AI Engine**:
  - `FoodHubSupport.php`, `MealAdvisor.php`, `FoodHubSystemPrompt.php` (v`2.2.0`), `deploy-meal-advisor.py`: Tích hợp RAG nhẹ từ `FoodHubKnowledgeBase`, thêm intent `faq` và `farewell`, sửa lỗi regex `"hóa đơn đỏ"` bị bắt nhầm thành `"đơn đó"`, mở rộng `supportTopic()` và phân loại ticket `Chất lượng món` / `Chưa nhận được hàng`.
- **Bộ dữ liệu huấn luyện (`docs/AI_TRAINING_DATASET.jsonl`)**:
  - Làm sạch và mở rộng từ 25 mẫu cũ lên **60 mẫu hội thoại tiếng Việt chuẩn OpenAI/Groq JSONL** (bao gồm hội thoại đa lượt, 4 vai trò, an toàn chống ảo giác & prompt injection, loại bỏ hoàn toàn câu tự nhận người thật hay bịa hành động).
- **Kiểm thử**:
  - Mới tạo `backend/tests/foodhub_vietnamese_context_test.php`: **60/60 checks PASS**.
  - Toàn bộ **12/12 bộ test PHP** và `php -l` đạt **100%**.
  - ✅ **Đã đồng bộ lên TiDB Cloud & Alwaysdata (23/11/2026)**:
    - TiDB migration `20261123_user_search_history.sql` thành công (`user_search_history = true`, `user_recommendation_preferences = true`).
    - Alwaysdata deploy thành công toàn bộ **15 file** (AI v2.2.0 + Recommendation + `GET /api/dishes/{id}`, `checksums_verified: true`, `php_lint: true`, backup `/home/chinh/private/foodhub/backups/meal-advisor-20261123-082417`).
    - Lưu ý cho điện thoại: các nâng cấp AI v2.2.0 chạy ngay lập tức trên app hiện tại; để thấy giao diện Android mới (mục "Món ngon cho bạn" trộn ngẫu nhiên + ưu tiên gợi ý tìm kiếm và màn hình Profile món ăn `DishDetailScreen`), cần build/cài bản APK mới lên điện thoại.

## Hệ thống Đề xuất Món ăn Cá nhân hóa theo Lịch sử Tìm kiếm — 23/11/2026

- **Database Migration (chưa chạy lên TiDB production — chờ kiểm tra/xác nhận)**:
  - `backend/database/migrations/20261123_user_search_history.sql`: Bảng `user_search_history` (`id`, `user_id`, `search_query`, `normalized_query`, `searched_at`, index `(user_id, searched_at)` & `(user_id, normalized_query)`) và `user_recommendation_preferences` (`user_id`, `search_personalization_enabled`, `retention_days`, `updated_at`).
- **PHP Backend**:
  - `backend/recommendation/SearchHistoryService.php`: Chuẩn hóa truy vấn tiếng Việt UTF-8, chặn truy vấn nhạy cảm (OTP/email/SĐT/token), deduplication trong cửa sổ 60s, tôn trọng tùy chọn bật/tắt cá nhân hóa và hạn lưu trữ `retentionDays`, hỗ trợ PDO và private per-user JSON fallback.
  - `backend/recommendation/RecommendationEngine.php`: Thuật toán Content-Based (`0.40 × TextSimilarity + 0.25 × CategorySimilarity + 0.20 × exp(-0.08 × daysSince) + 0.15 × log(1+freq)/log(11)`), từ điển đồng nghĩa tiếng Việt có chọn lọc, giới hạn đa dạng (tối đa 1 biến thể/tên gốc & 3 món/quán lượt đầu), lọc chặt món hợp lệ (chưa xóa, đang bán, còn kho, giá > 0, quán đang mở/active), cold-start fallback theo số lượt đặt thật (`order_items`, loại đơn `cancelled`) + `rating_avg`, per-user cache TTL 180s.
  - `backend/recommendation/recommendation-route.php` & `backend/api/index.php`: Các endpoint xác thực Firebase: `POST/GET/DELETE /api/search/history`, `GET /api/recommendations/personalized?limit=10`, `GET/PATCH /api/recommendations/preferences`.
- **Android Kotlin / Jetpack Compose**:
  - `app/src/main/java/com/example/dacs_3_composer/data/repository/RecommendationRepository.kt`: Tái sử dụng `FoodHubClient.api`, thêm `RecommendationRefreshBus` để làm mới đúng 1 lần khi quay về trang chủ.
  - `SearchViewModel.kt`: Ghi nhận tìm kiếm đã xác nhận bất đồng bộ (chống trùng do recomposition, lỗi mạng không chặn kết quả tìm kiếm).
  - `HomeViewModel.kt`, `HomeScreen.kt`, `DeliciousDishItem.kt`: **Gộp trực tiếp** gợi ý cá nhân hóa + xáo trộn ngẫu nhiên vào chính mục **"Món ngon cho bạn"** (không tạo mục riêng "Dành riêng cho bạn"). Quy luật: ưu tiên các món liên quan tìm kiếm gần đây ở đầu (xáo trộn nhẹ từng cụm 3 món, gắn nhãn tinh tế `"Gợi ý"`), phần món còn lại xáo trộn ngẫu nhiên theo phiên (thay vì hiển thị cứng theo `created_at DESC`), kèm nút làm mới ngay tại tiêu đề mục.
  - `app/src/main/java/com/example/dacs_3_composer/feature/customer/dish/DishDetailScreen.kt` + `MainRouteContainerUser.kt` + `SearchScreen.kt` + `CartViewModel.kt` + `GET /api/dishes/{id}` trong `backend/api/index.php`: Tạo màn hình **Profile chi tiết món ăn** (`dish_detail/{dishId}`) hiển thị ảnh lớn, danh mục, trạng thái, giá, mô tả, thẻ nhà hàng ("Xem quán"), các món khác cùng quán, chọn số lượng và thêm ngay vào giỏ hàng (kèm hộp thoại xác nhận nếu giỏ đang có món của quán khác). Nhấn món từ Trang chủ, Tất cả món hoặc Tìm kiếm đều mở thẳng Profile món ăn.
  - `app/src/test/java/com/example/dacs_3_composer/feature/customer/home/HomeFeedCompositionTest.kt`: Unit test kiểm chứng quy luật ưu tiên món liên quan tìm kiếm + trộn ngẫu nhiên.
- **Kiểm thử**:
  - `backend/tests/recommendation_system_test.php`: **31/31 checks PASS** (`P50 = 4.04ms`, `P95 = 5.45ms` trên SQLite in-memory).
  - Toàn bộ 11/11 bộ test PHP (bao gồm hồi quy AI Phase 4 & 5) và `php -l` đạt 100%.
  - Android Gradle `:app:compileDebugKotlin` + `:app:testDebugUnitTest` (`HomeFeedCompositionTest`): **BUILD SUCCESSFUL** (chưa cài APK/test trực tiếp trên điện thoại thật trong phiên này).
  - **Chưa deploy lên Alwaysdata và chưa chạy migration lên TiDB production** (đúng theo yêu cầu).

## Phase 4 (Personalization & Adaptive Intelligence) & Phase 5 (Quality & Performance Optimization) — 22/11/2026

- **Phase 4 — Cá nhân hóa có kiểm soát & Bộ nhớ 3 tầng**:
  - `backend/ai/UserPersonalization.php`: Lưu trữ sở thích dài hạn (Layer B) theo `userId` đã xác thực (hỗ trợ cả bảng `user_ai_preferences` trên PDO lẫn private per-user JSON fallback), quản lý consent rõ ràng (`personalizationEnabled` mặc định `false`, `useFoodPreferences`, `rememberCommunication`, `consentVersion`, `consentUpdatedAt`), trích xuất ký ức có kiểm soát (`extractCandidateMemories`: chỉ nhận tuyên bố bền vững tường minh, loại trừ yêu cầu tạm thời `"hôm nay"`, câu hỏi, giả định, người thứ 3, prompt injection, và không tự lưu chi tiết dị ứng/y tế), tự hủy cache món ăn khi sửa/xóa sở thích, và Adaptive Communication Engine (`resolveStyle`: 5 phong cách `friendly`/`professional`/`concise`/`empathetic`/`formal`, yêu cầu hiện tại luôn ưu tiên hơn bộ nhớ dài hạn).
  - `backend/ai/ConversationContext.php` & `backend/ai/FoodHubSupport.php`: Bổ sung Layer C Task/Support Memory (`taskMemory` lưu `ticketId`, `orderId`, `status`, `pendingAction`, `lastVerifiedUpdate`), chuẩn hóa tóm tắt chuyển tiếp nhân viên thật (Human Handoff) có lọc PII trước khi ghi vào `conversations`/`messages`.
  - `backend/ai/FoodHubSystemPrompt.php`: Bổ sung Prompt Versioning (`foodhub-support` v`2.1.0`, `foodhub-meal-advisor` v`2.1.0`) và phần mở rộng Personalization (chỉ dùng sở thích đã duyệt khi hữu ích, yêu cầu hiện tại ưu tiên cao hơn).
- **Phase 5 — Đánh giá chất lượng, Phản hồi & Hiệu năng**:
  - `backend/ai/AiQualityMonitor.php`: Thu thập phản hồi 👍/👎 + 6 lý do chuẩn hóa (chống trùng lặp vote theo user+conversation+message, rate-limit 30/giờ), đo lường latency (Avg/P50/P95), token usage (`promptTokens`/`completionTokens`/`totalTokens` từ Groq), tỷ lệ lỗi provider, số lượt handoff, và Quality Scorecard có trọng số kèm chốt chặn độc lập cho lỗi Safety/Privacy.
  - `backend/ai/ai-personalization-route.php` & `backend/api/index.php`: Thêm các REST endpoint xác thực Firebase: `GET/PUT/DELETE /api/ai/preferences`, `PUT /api/ai/preferences/consent`, `DELETE /api/ai/preferences/{field}`, `POST /api/ai/feedback`, và `GET /api/ai/metrics` (chỉ Admin — trả 403 cho role khác).
  - `backend/ai/MealAdvisor.php` & `backend/ai/meal-advisor-route.php`: Bổ sung `$lastUsage` đo token Groq, `Server-Timing` tách `meal`/`memory`/`provider`, trả thêm `messageId`, `promptVersion`, `usage` (giữ tương thích ngược Android), siết thêm guardrail ETA (`sẽ tới trong X phút`) và lọc toàn bộ emoji khi `emojiPreference=none`.
  - `backend/database/migrations/20261122_ai_personalization_and_feedback.sql`: Migration an toàn (`CREATE TABLE IF NOT EXISTS`) cho `user_ai_preferences` và `ai_feedback`.
- **Kiểm thử**:
  - `backend/tests/foodhub_phase4_personalization_120_test.php`: **120/120 PASS** (baseline 107/120 → mở rộng nhận diện xưng hô `anh/chị kiểm tra/xem/cần` → 120/120).
  - `backend/tests/foodhub_phase5_quality_120_test.php`: **120/120 PASS** (baseline 117/120 → bổ sung guardrail `sẽ tới trong X phút` → 120/120).
  - Toàn bộ 10/10 bộ test PHP và `php -l` trên 11/11 file đều đạt 100%.
- **Triển khai thực tế**:
  - ✅ Đã chạy SQL migration `20261122_ai_personalization_and_feedback.sql` lên TiDB Cloud (`food_delivery_app`): xác nhận 2 bảng `user_ai_preferences` và `ai_feedback` đã sẵn sàng.
  - ✅ Đã deploy thành công toàn bộ 11 file Phase 4 & 5 lên Alwaysdata (`checksums_verified: true`, `php_lint: true`, backup: `/home/chinh/private/foodhub/backups/meal-advisor-20261122-154307`, Groq `openai/gpt-oss-120b` đã cấu hình).
- **Việc tiếp theo tùy chọn**:
  - Gắn nút UI 👍/👎 (`POST /api/ai/feedback`) và màn hình cài đặt AI Personalization (`GET/PUT/DELETE /api/ai/preferences`, `PUT /api/ai/preferences/consent`) trên ứng dụng Android Compose.

## Nâng cấp FoodHub AI Human-Like Conversation & Bộ test 120 tình huống — 22/11/2026

- **Kiến trúc gọn & dễ bảo trì**:
  - Tách System Prompt chính thức sang `backend/ai/FoodHubSystemPrompt.php` (FoodHub AI minh bạch là trợ lý AI, 15 nguyên tắc giao tiếp + ví dụ phong cách + `buildMessages()` multi-turn kèm chỉ dẫn động về xưng hô, độ ngắn gọn, tiếp nối hội thoại, cảm xúc tức giận).
  - Tách quản lý ngữ cảnh hội thoại sang `backend/ai/ConversationContext.php` (sliding window 4 lượt, rolling summary bảo toàn `orderId`/`topic`/`issue`/`pronoun`/`confirmedActions`/`unresolvedQuestion`, nhận diện xưng hô nhất quán `anh`/`chị`/`bạn`/`mình`, phát hiện yêu cầu ngắn gọn `preferBrief`, ẩn dữ liệu nhạy cảm OTP/mật khẩu/email/SĐT).
  - Cập nhật `backend/ai/MealAdvisor.php`: dùng `FoodHubSystemPrompt::buildMessages()`, thêm `temperature: 0.6` cho Groq multi-turn, sửa lỗi false-positive ở guardrail ETA (cho phép nhắc thời gian khách đã đợi, vẫn chặn bịa ETA tương lai) và guardrail hoàn tiền (cho phép câu phủ định trung thực "chưa xác nhận đã hoàn tiền"), bổ sung guardrail chống tự nhận người thật, chống lộ API key/prompt và lọc emoji vui khi khách tức giận.
  - Cập nhật `backend/ai/FoodHubSupport.php`: mở rộng chuẩn hóa tiếng lóng/viết tắt (`dc`, `mk/mik`, `tks/thx`, `ib`, `rep/trl`, `lun`, `dzay`), mở rộng `intent()` & `detectEmotion()` (8 trạng thái, sửa lỗi `hài long` có dấu), sửa bug `(bool)preg_match` cho `$accountIncident`, cho `identity` đi qua `dispatch($humanize, ...)`, bổ sung ngữ cảnh đa lượt ("ừ đúng rồi", "vậy còn bao lâu", "thế hủy đi", "nếu lâu quá thì sao") và đọc `orderId` từ `summary`.
  - Cập nhật `backend/ai/meal-advisor-route.php`: dùng `ConversationContext`, giữ `history` client gửi khi phiên mới chưa có trên server, giải phóng `$memoryLock` trong `finally`.
  - Cập nhật `backend/deploy/deploy-meal-advisor.py`: bổ sung `ai/FoodHubSystemPrompt.php` và `ai/ConversationContext.php`.
- **Kiểm thử**:
  - Tạo `backend/tests/foodhub_conversation_120_test.php` (6 nhóm × 20 tình huống = 120 cases). Baseline lần chạy đầu đạt 99/120 (82.5%) — phát hiện và sửa các lỗi thật trong `FoodHubSupport.php` — chạy lại đạt **120/120 (100%)**.
  - Toàn bộ 6 bộ test PHP hồi quy còn lại (`foodhub_support_test.php`, `foodhub_human_ai_test.php`, `foodhub_conversation_style_test.php`, `meal_chat_test.php`, `meal_advisor_fast_test.php`, `menu_knowledge_test.php`) và `php -l` đều đạt.
  - Báo cáo đánh giá lưu tại `docs/AI_CONVERSATION_EVALUATION.md`.
- **Nâng cấp tư vấn món ăn dựa trên 29 món thật trên TiDB Cloud (`Nhà hàng AnChin`, `Jolllibee`, `Mì cay Seoul`, `Việt Nam Food`)**:
  - `MenuKnowledge.php`: Bổ sung khớp tên quán linh hoạt (`Jollibee`/`Jolibee` ↔ `Jolllibee`, `An Chin` ↔ `Nhà hàng AnChin`, `Seoul` ↔ `Mì cay Seoul`), mở rộng từ khóa đồng nghĩa theo thực đơn thật (cay/kim chi/gochujang, ăn vặt/tokbokki/chuối, tráng miệng/kem/sundae, món Hàn, gà rán, món nước), hỗ trợ nhóm `Ăn vặt` trong ghép bữa 1 người cùng quán, và làm tự nhiên `mealAnswer()`.
  - `MealChat.php`: Sửa lỗi `renderReply()` trước đây vứt bỏ toàn bộ câu trả lời tự nhiên của AI khi có số phần nhỏ (`1 phần`, `2 miếng`, `1 nước ngọt`, `1 người`), trong khi vẫn chặn tuyệt đối mọi giá tiền tự bịa ngoài `{gia:ID}`. Nâng cấp giọng điệu `answer()`.
  - `FoodHubSystemPrompt.php` + `MealAdvisor.php`: Tách `FoodHubSystemPrompt::mealAdvisor()` đồng nhất giọng FoodHub AI, hỗ trợ `reasons` hấp dẫn theo từng món dựa trên mô tả thật, dùng `temperature: 0.5` cho Groq, truyền `pronounPreference` & `isContinuation`, tăng cache version lên `7`.
  - Tạo bộ test mới `backend/tests/foodhub_meal_quality_test.php` (22 checks trên bản sao 29 món thật từ TiDB) — toàn bộ 8/8 bộ test PHP và `php -l` đạt.
  - ✅ Đã deploy thành công lên Alwaysdata (`backup: meal-advisor-20261122-152004`, checksums verified, php_lint passed) và kiểm thử live trực tiếp với TiDB + Groq (`openai/gpt-oss-120b`): các câu *"80k cay cay no bụng"* và *"Jollibee có món gì ngon dưới 70k"* đều đạt `rendered_ok=YES` với phản hồi tự nhiên và lý do từng món bám sát mô tả thật.

## Hội thoại tiếng Việt ngày 22/11/2026

POST ai/meal-advisor dùng Firebase, account TiDB và Groq gpt-oss-120b. MealAdvisor.php áp dụng phần IV master prompt cho câu trả lời hỗ trợ. Mười kịch bản ở docs/AI_CONVERSATION_SCENARIOS.md và docs/AI_TRAINING_DATASET.jsonl là mô phỏng. Android tạo UUID theo instance repository của màn chat. Backend sử dụng cache riêng theo UID và vai trò, giữ tối đa bốn phiên, bốn lượt gần nhất và tóm tắt có cấu trúc. Phiên quá 24 giờ không được nạp lại, được dọn khi tài khoản ghi tiếp. Thiết bị chạy bản cũ vẫn gửi cửa sổ lịch sử như trước. Không có tác vụ cập nhật trọng số. Kiểm thử route với hai tài khoản, hai UUID và sáu lượt xác nhận cách ly, cửa sổ và tóm tắt.
## Voucher theo mẫu HTML

Banner Compose dùng gradient, chiều cao tối thiểu 240dp, bo góc 24dp, chữ phía trái và ảnh lớn xoay 12 độ, tràn góc dưới phải. Tạo mới đúng bốn ảnh nền trong suốt bánh mì, cơm tấm, trà sữa, bún bò. Ảnh thứ năm dùng nguyên ảnh pizza trong HTML người dùng cung cấp. Tệp gốc nằm ở backend/assets/vouchers. Ảnh dùng cho mã miễn phí giao FHPHOSHIP, đổi tiêu đề thành Giao ngon tận nhà. Mức giảm, hạn dùng và lượt sử dụng giữ theo các voucher đã tạo. APK build/deliverables/FoodHub-Voucher-Hero-debug.apk đã build và unit test đạt. Chưa cài lên điện thoại vì ADB không có thiết bị kết nối trong lần kiểm tra này.

## Nâng cấp AI CSKH — Phiên 2: Phản hồi tự nhiên hơn — 20/11/2026 (tối)

**Vấn đề được giải quyết:** AI trả lời còn cứng nhắc, không hiểu ngữ cảnh, không hiểu ý truyền đạt.

### Thay đổi đã thực hiện

**`backend/ai/MealAdvisor.php`**:
- **Rewrite hoàn toàn `humanizeSupport()`**: System prompt mới viết theo kiểu nhân vật ("Em là Linh — nhân viên CSKH FoodHub") thay vì danh sách quy tắc đánh số. Bao gồm 4 cặp ví dụ trước/sau (❌ máy móc / ✅ người thật) nhúng trực tiếp vào prompt. Hướng dẫn đa dạng câu mở, tránh "Dạ" ở đầu mỗi câu, dùng "nè/nha/nhé/thôi" tự nhiên.
- **Thêm method `generateMultiTurn()`**: Gọi Groq trực tiếp với messages array multi-turn thực sự (role: user/assistant cho từng lượt lịch sử, facts được inject dưới dạng system message cuối cùng thay vì JSON blob trong user message). Tối đa 4 lượt lịch sử.
- **Bỏ `reasoning_effort: 'low'`** khỏi path humanize. `max_completion_tokens` tăng lên 1024.
- Timeout cURL tăng lên 8000ms cho path multi-turn.

**`backend/ai/FoodHubSupport.php`**:
- **Mở rộng `detectEmotion()`**: Thêm pattern tiếng Việt tự nhiên hơn:
  - angry: `troi oi`, `oi troi`, `vo ly`, `nuc cuoi`, `khong chap nhan`, `that vong qua`, v.v.
  - anxious: `sao.*chua thay` (bắt "sao mãi chưa thấy shipper"), `doi.*lau lam`, `doi ca tieng`
  - frustrated: `chan qua`, `khong hieu sao`, `da noi roi ma`, v.v.
  - urgent: `gap lam`, `voi len`, `can ngay`
  - satisfied: `tot lam`, `tuyet voi`, `oke nha`
- **Mở rộng `intent()`**: Thêm pattern bị bỏ sót:
  - `order`: `dau roi`, `o dau roi`, `sao lau vay`, `sao chua thay`, `bao lau nua`, `con cach bao lau`
  - `voucher`: `ma giam gia`, `co ma giam`, `dung ma`
  - `incident`: `tien bi tru 2 lan`, `mat tien`, `thanh toan 2 lan`
  - `role`/greeting: `hey`, `alo`, `chao foodhub`, `lam gi duoc`
  - `thanks`: `ok nhe`, `oke nha`

**`backend/tests/foodhub_human_ai_test.php`**:
- Cập nhật mock transport để parse facts từ system message cuối (DỮ LIỆU ĐÃ XÁC THỰC) thay vì `messages[1].content`.

### Kết quả kiểm thử
```
foodhub_human_ai_test.php → 6/6 PASSED
foodhub_support_test.php → PASSED
meal_chat_test.php → 26 checks + Groq validation PASSED
meal_advisor_fast_test.php → 61 checks PASSED
php -l tất cả 3 file AI → No syntax errors
```

### Trạng thái Deploy
- ✅ **Deploy thành công** lúc 20/11/2026 14:20 UTC — `deploy-meal-advisor.py` xác nhận checksums và php_lint.
- Backup tự động: `/home/chinh/private/foodhub/backups/meal-advisor-20261120-142048`
- **Bước tiếp theo**: Test trực tiếp trên thiết bị:
  - "Sao mãi chưa thấy shipper tới?" → AI phải phản hồi đồng cảm, tự nhiên, không bịa ETA
  - "Đồ ăn bị đổ hết rồi!" → AI đồng cảm 1 câu rồi hướng dẫn tạo phiếu
  - "Có mã giảm giá không?" / "hey shop ơi" / "oke nha cảm ơn"



- **Hoàn thiện kiến trúc 10 yếu tố cho Groq AI Assistant**:
  1. Giao tiếp tự nhiên tiếng Việt đời thường, xưng hô em - anh/chị, tự thích ứng cách xưng hô, hiểu viết tắt (tui, shp, đh, ko, nãy, 80k), bỏ qua lỗi telex/chính tả.
  2. Tra cứu đơn hàng trên TiDB, giải thích minh bạch khi chưa có ETA chính xác, chống ảo giác (anti-hallucination) không bịa số phút đến nơi.
  3. Tư vấn món ăn thông minh theo ngân sách, sở thích cay/no bụng/món nước/chay và an toàn dị ứng thực phẩm.
  4. Tiếp nhận khiếu nại (đổ thức ăn, sai món, thiếu món), hỏi ảnh bằng chứng và hướng dẫn tạo phiếu hỗ trợ.
  5. Quy trình thanh toán, voucher, phí vận chuyển và hoàn tiền; chốt an toàn nghiêm ngặt: chỉ thông báo hoàn tiền thành công khi hệ thống ghi nhận `payments.status === 'refunded'`.
  6. Phát hiện cảm xúc khách hàng (angry, anxious, frustrated, urgent, satisfied, neutral); kỹ năng xoa dịu (de-escalation) đồng cảm 1 câu ngắn gọn, không lặp lại lời xin lỗi, tập trung ngay vào giải pháp.
  7. Phân quyền dữ liệu theo vai trò (Customer chỉ xem đơn của mình, Seller xem quán mình, Shipper xem đơn được gán, Admin xem toàn hệ thống).
  8. Ghi nhớ ngữ cảnh đa lượt: tự động trích xuất mã đơn và món ăn từ các lượt chat trước (ví dụ "Vậy hủy giúp mình đi").
  9. Chuyển tiếp nhân viên thật (Human Handoff): tạo ticket trong `conversations` (type='support'), tóm tắt nội dung cho Admin xử lý qua Tin nhắn.
  10. Thu thập đánh giá (Hữu ích / Chưa giải quyết), đưa vào hàng đợi kiểm duyệt, tuyệt đối không tự học dữ liệu chưa duyệt.
- **Backend**:
  - `MealAdvisor.php`: Bổ sung hàm `humanizeSupport()` tích hợp Master System Prompt CSKH và chốt an toàn (actionRequired, refund guardrail, ETA guardrail).
  - `FoodHubSupport.php`: Bổ sung `detectEmotion()` và cơ chế `dispatch()` truyền gói sự thật (facts) đã xác thực vào Groq.
  - `meal-advisor-route.php`: Kết nối luồng humanize vào API route với hạn mức quota và fallback an toàn.
- **Tài liệu & Dữ liệu huấn luyện**:
  - `docs/AI_TRAINING_DATASET.jsonl`: Bộ dữ liệu mẫu chuẩn OpenAI/Groq Chat format cho 10 kịch bản thực tế.
  - `docs/AI_ASSISTANT_TRAINING.md`: Bản hướng dẫn chi tiết kiến trúc, Master System Prompt và quy trình Fine-tuning LLaMA qua Unsloth/Hugging Face.
- **Kiểm thử**: Toàn bộ kiểm thử PHP đạt (`foodhub_support_test.php`, `foodhub_human_ai_test.php`, `meal_chat_test.php`, `meal_advisor_fast_test.php`, `menu_knowledge_test.php`).

## Voucher có ảnh trên trang chủ

Đã tạo và kiểm tra trên TiDB năm mã FHBANHMI10, FHCOM15, FHTRASUA12, FHBUN20 và FHPHOSHIP. Mỗi mã áp dụng toàn hệ thống trong 30 ngày từ thời điểm tạo với 100 lượt. TiDB lưu URL Cloudinary trong vouchers.image_url, API Alwaysdata trả imageUrl. Android tải ảnh qua Coil. Hai tệp migration ngày 20/11 ghi cấu trúc và dữ liệu mẫu. Chạy lại seed không gia hạn hoặc đặt lại lượt dùng.

## Trang chủ khách hàng và khung chat ngày 20/11/2026

Banner đọc promotions đã có trên Alwaysdata, lọc mã còn lượt và hiển thị theo trang vuốt ngang. Trang chủ giới hạn 20 món theo hai cột. Route all_dishes dùng danh sách đầy đủ trong HomeViewModel và giữ điều hướng tới nhà hàng. Route meal_advisor ẩn thanh tab khách hàng, khung tin nhắn giới hạn theo bề rộng màn hình. APK build/deliverables/FoodHub-Home-debug.apk đã build, unit test đạt và đã cài đè trên Samsung A23. Kiểm tra tương tác trực tiếp chưa hoàn tất vì điện thoại chuyển sang ứng dụng khác.

## Hiệu năng truy vấn ngày 20/11/2026

Đã cập nhật API và Samsung A23. GET tái sử dụng kết nối PDO theo worker, xác minh TLS và dùng pool riêng với các lượt ghi. Bảy đơn trên TiDB cho kết quả món giống nhau, trung vị ba lần 1145 ms trước và 163 ms sau khi gom truy vấn. Health API trên cùng kết nối HTTPS trả byte đầu ở 1,27–1,35 s trước và 0,50–0,53 s sau khi làm nóng, lần đầu sau triển khai 1,80 s. Đây là số đo riêng từng bước, chưa đo trọn thời gian vẽ màn hình.

API orders kèm restaurantCoverImage. Android ngừng tải ảnh quán bằng từng API riêng và chỉ thăm dò đơn trong vòng đời màn hình. Trang chủ tải hồ sơ, địa chỉ, món và quán song song, hiển thị các phần ngay khi có dữ liệu. Ứng dụng dùng HTTPS. Bản APK nằm ở build/deliverables/FoodHub-Performance-debug.apk.

## Trợ lý hỗ trợ khách hàng và vận hành

Android dùng lại màn hình chat ở trang chủ khách hàng và thêm lối vào tại tổng quan nhà hàng, shipper và Admin. APK thử nghiệm ở `build/deliverables/FoodHub-Support-debug.apk`.

Backend nhận quyền từ tài khoản đã xác thực, giới hạn truy vấn theo người đặt, chủ quán hoặc shipper phụ trách. Groq phân loại câu hỏi mở, PHP truy vấn trạng thái thực và xử lý thao tác. Chat gửi tối đa bốn lượt gần nhất. Chỉ giao dịch có trạng thái refunded mới được báo đã hoàn tiền. Hủy qua chat hiện áp dụng cho đơn tiền mặt đang chờ quán xác nhận, sau khi khách xác nhận đúng mã. Khiếu nại và đánh giá đi vào hộp thư hỗ trợ, không tự cập nhật tri thức. Người nhận có thể bổ sung ảnh trong Tin nhắn. Báo cáo vận hành là tổng tất cả đơn hoàn tất trong phạm vi tài khoản, không phải doanh thu ròng theo kỳ.

Đã triển khai Alwaysdata và đối chiếu checksum (backup meal-advisor-20261119-164013). Đã kiểm tra trực tiếp truy vấn phạm vi khách/nhà hàng/shipper, voucher, mô tả dị ứng, thống kê phản hồi Admin và phân loại câu hỏi qua Groq.

Kiểm tra quyền, hủy hai lượt, gửi lặp, bàn giao Admin và phản hồi bằng `backend/tests/foodhub_support_test.php` với PDO SQLite và mbstring. Kiểm tra chỉ đọc trên hosting dùng tài khoản chẩn đoán, không ghi lên đơn thật. Build APK và 24 Android unit test đạt. Trên máy ảo API 36.1, AndroidJUnitRunner dừng hai lần lúc khởi động tiến trình, chưa xác nhận kiểm thử giao diện. Chưa huấn luyện lại trọng số mô hình hoặc xây kho kiến thức tự xuất bản.

## Chuyển nhà cung cấp tư vấn sang Groq

Cập nhật sau khi người dùng lưu khóa: đã đồng bộ vào thư mục riêng của Alwaysdata và xác minh route thực tế với TiDB. Lượt hỏi tiếp “Trong số đó, món nào rẻ nhất?” trả source=groq, model=openai/gpt-oss-120b, fallbackReason=null và chọn đúng hai món đồng giá 45.000đ trong ba món mì vừa hiển thị. Backup triển khai meal-advisor-20261119-093822.

Route Alwaysdata đang chọn `openai/gpt-oss-120b` trên Groq Chat Completions. Backup tại `/home/chinh/private/foodhub/backups/meal-advisor-20261119-092841`. Lần triển khai đầu xác nhận chưa có khóa trên hosting. Cấu hình qua biến môi trường `GROQ_API_KEY` của PHP hoặc tệp riêng `/home/chinh/private/foodhub/groq.apikey.local.txt`. Sau khi người dùng điền tệp local `backend/deploy/groq.apikey.local.txt`, dùng `python backend/deploy/deploy-meal-advisor.py --sync-key` và kiểm tra câu hỏi mở có source=groq. Không in khóa. Test transport dùng dữ liệu giả, cần kiểm tra một phản hồi Groq thật sau khi có khóa.

## Nhận diện mì và kiểm tra lựa chọn model

Bộ lọc hiểu cách hỏi số nhiều, phân biệt mì/mỳ với bánh mì và bò Mỹ. Với yêu cầu về mì, ứng viên được kiểm tra theo tên món/danh mục trước cả tra cứu và gọi model. Tên quán và mô tả món ăn kèm từng gây khớp sai ở nhánh dự phòng. Bổ sung ví dụ phân biệt loại món với tên quán vào chỉ dẫn cho Gemini. 142 kiểm tra PHP đạt. Thử cấu hình Gemini 3.5 Flash-Lite trong một yêu cầu riêng từ Alwaysdata trả HTTP 400 FAILED_PRECONDITION do vị trí; giữ cấu hình production. Chưa fine-tune trọng số. Log Android đính kèm có 35 dòng và không có bản ghi Gemini hoặc endpoint tư vấn.

## Phân trang menu và tư vấn khẩu phần, 19/11/2026

- Bản mới truy vấn menu bằng ID tăng dần, 8 món/trang; Android nhớ cursor độc lập với câu hỏi xen giữa và hiển thị nút xem tiếp. APK `build/deliverables/FoodHub-Menu-Chat-debug.apk` đã đối chiếu checksum với bản build. Bốn lượt gần nhất có câu trả lời, ID món và vị trí trang để xử lý câu hỏi nối tiếp.
- Các truy vấn danh mục, thống kê theo quán và xếp hạng ứng viên dùng chung điều kiện món có thể đặt. SQL tìm ứng viên trên mọi món hợp lệ trước khi rút gọn ngữ cảnh model. FoodHub tính tổng một khẩu phần cùng quán, kiểm tra ngân sách và tính phí giao một lần.
- Đã cập nhật Alwaysdata, giữ nguyên cấu hình Gemini 4 Hydrogen. Backup lần cuối `/home/chinh/private/foodhub/backups/meal-advisor-20261119-085512`. Kiểm tra trực tiếp route đã triển khai sau bước xác thực bằng phiên chẩn đoán riêng: 29 món/4 quán, đi hết các trang 8+8+8+5, ID không trùng; món bún bò thuộc Việt Nam Food; một bữa trưa 78.000đ cộng 20.000đ phí giao. Google đã trả `provider_location` khi thử câu mở; các câu trả lời trên đến từ truy vấn và logic của FoodHub.
- 136 kiểm tra PHP, có dữ liệu thử 105 món, đi qua giới hạn 80, xóa món dùng làm mốc, xem theo quán, đổi ngân sách cho bữa trưa, so sánh số món/giá. Build Android, 24 JUnit và 2 kiểm thử giao diện trên máy ảo Android 36.1 đạt, gồm bấm xem trang tiếp và hỏi tiếp theo món. Lượt khởi động thử đầu bị BIND APPLICATION ANR, lượt chạy lại đạt cả hai bài.

## Chat “Hôm nay ăn gì?” — 19/11/2026

- Stitch project FoodHub đã có hai màn hình mới: trang chủ `9b4c3301e23a40d282d0f744ff928017` và chat `7094891ee3a440139c84ad6dd495ad31`. Compose triển khai thẻ mở chat, lịch sử trong phiên, nút gửi, gửi lại, ngân sách tùy chọn, thẻ món mở quán. Các chi tiết được hiển thị theo dữ liệu FoodHub đang có.
- TiDB có sáu món đặt được khi kiểm tra. Trần mặc định cũ 70.000đ chỉ còn hai ứng viên. Không có món khớp ba preset cũ (cơm/món nước/chay). Bản chat dùng “Xem thực đơn” và “Món nào rẻ nhất?”. API kiểm tra lại thực đơn và dùng tối đa bốn lượt gần nhất làm ngữ cảnh. `source=filters` là câu tra cứu FoodHub, `gemini/cache` là câu được lập từ kết quả Gemini. Google vẫn là Gemini 4 Hydrogen.
- Đã triển khai chọn lọc lên Alwaysdata, lint và checksum đạt. Backup cuối `/home/chinh/private/foodhub/backups/meal-advisor-20261119-031011`. Kiểm tra trực tiếp route PHP trên hosting sau bước xác thực bằng phiên chẩn đoán, menu/giá câu tiếp theo/món rẻ/mức tiền quá thấp/nhóm khai vị đều đúng với TiDB. Google trả HTTP 400 FAILED_PRECONDITION “User location is not supported for the API use” khi gọi từ môi trường SSH hosting. Backend phân loại `provider_location` và giải thích rõ. Chưa xác nhận nhà cung cấp chấp nhận một câu hội thoại tự do từ hosting trong phiên này. Không đổi khóa/model/DB.
- 109 kiểm tra PHP đạt (28 + 55 + 26), 24 JUnit và một kiểm thử giao diện trên máy ảo Android 36.1 đạt. Kiểm thử giao diện dùng dữ liệu thử, gửi hai lượt, đối chiếu ID món của lượt trước và thao tác mở quán. Máy ảo cần biên dịch trước APK vì ANR lúc khởi động hệ thống. APK đã đối chiếu SHA-256 với bản build tại `build/deliverables/FoodHub-AI-Chat-debug.apk`.

## Tối ưu API tư vấn AI — 16/11/2026

- Người dùng xác nhận hết crash/DB đã kết nối, yêu cầu tối ưu Gemini và **chọn giữ nguyên Gemini 4 Hydrogen**. Không tự đổi sang model nhẹ. Log gửi kèm còn chứa lỗi DB/503 cũ; health và các thử API hiện tại đã qua xác thực/SQL.
- Đã triển khai backend mới lên Alwaysdata: route gọi advise() **tối đa một lần** thay interpret()+rank() hai lượt; Gemini chỉ trả budget/unsupported và tối đa ba ID enum thực đơn, không sinh đoạn giải thích dài. Thông tin món và lý do ngắn theo category lấy từ DB. Thinking LOW, output cap 768 (có headroom suy luận), không temperature, mỗi call tối đa 5.500ms mặc định và không retry đồng bộ.
- Bộ lọc server nhận diện ngân sách 60k/60.000đ/triệu; các yêu cầu đơn giản hiểu đầy đủ, một ứng viên, không đủ tiền hoặc ngoài phạm vi trả ngay không gọi model. Khẩu vị/chay/không cay cần bằng chứng; phủ định/điều kiện không hiểu không được bỏ qua. Đã kiểm tra trường hợp nhầm đồng âm do bỏ dấu (cá/cà phê, bò/bơ), bánh mì/món nước và mô tả chưa chắc không cay.
- Cache một kết quả/tài khoản trong 5 phút, cache key version=4 gồm model/key fingerprint, yêu cầu/ngân sách/phí và fingerprint ứng viên. Luôn đọc catalog hiện tại trước cache; sinh mới thì kiểm tra lại SQL sau call. Giá, quán, ảnh không lấy từ AI/cache. Cache ngoài web, 0600, không lưu raw prompt/token. Khóa không chờ tránh nhiều call cùng account; provider lỗi nghỉ gọi 30 giây; quota 4/phút/20/giờ chỉ áp dụng call mới. Source gemini/cache/filters và fallbackReason hiển thị rõ dữ liệu lọc khi Google chưa đáp ứng.
- Đo baseline cũ trực tiếp hosting: tổng 7,47s (interpret 2,136s, rank 4,061s, phần còn lại SQL/kết nối). HTTP mới: bộ lọc/ngoài phạm vi/không đủ ngân sách khoảng 1,35–1,9s. Một mẫu cold chạm giới hạn Google: tổng 7,07s, Gemini=5,503s. Không coi giới hạn call 5,5s là SLA toàn API hoặc thời gian trên điện thoại.
- Bản one-call đầu đã có gợi ý Gemini thật và cache-hit không gọi model; các lượt cuối (sau chuyển sang output ID-only) gặp **Google 429** và timeout. Không xác định được loại quota theo phút/ngày vì probe tiếp theo timeout; không đoán thời điểm reset. Không nói tất cả các câu hỏi hiện đã được Gemini trả lời. Bộ lọc HTTP 200, thông báo quota/slow rõ, không bịa câu trả lời AI. Không nâng quota/thanh toán tự động.
- **Kiểm tra đạt:** 55 fast checks + 28 kiểm tra cũ; PHP lint, checksum triển khai; HTTP health, thiếu token 401, khóa riêng 403, existing Firebase customer (isNewUser=false), gợi ý/giá DB thật, cache ở đợt đầu, các preset, ít tiền, ngoài phạm vi, một ứng viên, hai request HTTP song song. Không tạo tài khoản/đơn/thanh toán thử. Chỉ sửa backend, không cần APK mới.
- Backup bản triển khai cuối: `/home/chinh/private/foodhub/backups/meal-advisor-20261116-082338`; giữ nguyên private config DB đã khôi phục password và Gemini key/model. `backend/tests/meal_advisor_fast_test.php` là test mới; `build/alwaysdata-deploy/*` chứa công cụ đo và smoke (Git bỏ qua). Cache source params/field cũ giữ tương thích app. Xem docs/AI_MEAL_ADVISOR.md và API.md cho contract hiện tại.

## TiDB từ chối đăng nhập backend — 16/11/2026

- **ĐÃ KHÔI PHỤC sau khi người dùng lưu mật khẩu mới.** Thử credential mới thành công trước khi ghi; chỉ thay mysql.password trong private config Alwaysdata, đối chiếu toàn bộ giá trị khác giữ nguyên, sao lưu tại `/home/chinh/private/foodhub/backups/password-20261116-064356`. Không đưa mật khẩu vào lệnh/log/Git, không reset mật khẩu trên TiDB hoặc sửa dữ liệu nghiệp vụ.
- Xác minh live: API health trả 200 `database=connected`; đúng food_delivery_app, TLS_AES_128_GCM_SHA256, timezone +07:00, 32 bảng. Đọc trực tiếp sau kết nối: users=5, stores=1, dishes=6, orders=7. HTTPS bằng Firebase khách hàng hiện có (lookup trước đăng nhập, isNewUser=false) đọc users/me, stores, dishes, orders, addresses đều 200. Thiếu token 401, wrapper khóa Gemini 403. Không cần build/cài lại APK cho thay đổi mật khẩu server.
- Đã đồng bộ password literal trong `backend/config.tidb.local.php` đang tồn tại, PHP lint đạt, sao lưu local tại `build/private-config-backups/20261116-064710`. **config.local.php và config.runtime.local.php hiện KHÔNG còn trong checkout**; không tự tái tạo file người dùng có thể đã xóa. Candidate config.tidb vẫn kế thừa một trong hai file này, nên không coi local CLI đã chạy được; cần khôi phục cấu hình riêng thích hợp trước khi dùng local/prepare full bundle. Không dùng bundle cũ để ghi đè private config hosting mới; không đồng bộ ngược đường dẫn Linux vào Windows. App online dùng private config hosting đã kiểm tra thành công.

- Người dùng xác nhận lỗi truy cập DB xảy ra trong FoodHub. HTTPS API Alwaysdata trả **503**, thông báo không kết nối database. DNS Alwaysdata phân giải được khi kiểm tra với quyền mạng phù hợp; lần công cụ giới hạn báo DNS lỗi không phải bằng chứng hosting mất DNS.
- Kiểm tra chỉ đọc từ máy local bằng backend/config.runtime.local.php: TiDB trả mã driver **1045**, không phải timeout. Kiểm tra trực tiếp trên Alwaysdata cũng trả **1045 Access denied for user ... (using password: YES)**.
- Hosting phân giải được host TiDB, kết nối TCP được, CA đọc được, TLS required bật, mật khẩu có cấu hình, username có tiền tố cluster, database food_delivery_app. IP nguồn TiDB thấy từ hosting: **185.31.41.84**. Chưa có bằng chứng database bị xóa hay mất dữ liệu vì xác thực bị từ chối trước truy vấn.
- File cấu hình chính hosting có mtime **2026-11-14 04:45:12 UTC**, chưa đổi bởi các lần triển khai AI/Android sau đó. Chẩn đoán chắc chắn: thất bại ở bước đăng nhập/quyền tài khoản DB. Chưa thể kết luận riêng mật khẩu, username/cluster hay hạn chế truy cập nếu chưa đối chiếu TiDB Cloud hiện tại. Nếu mới sửa IP rules, kiểm tra IP nguồn hosting nêu trên; không tự mở toàn bộ IP.
- **Người dùng đã xác nhận đổi mật khẩu TiDB và đã lưu mật khẩu mới** vào tệp riêng `backend/deploy/tidb.password.local.txt` (Git bỏ qua), không gửi qua chat. Đã xử lý và kiểm tra thành công theo các dòng trạng thái ở đầu mục này; giữ nguyên TiDB/schema/Firebase/PayOS/Gemini.
- Công cụ chẩn đoán chỉ đọc ở build/alwaysdata-deploy/db-diagnostic.php và run-db-diagnostic.py (Git bỏ qua), che thông tin đăng nhập khi trả thông báo. Đối chiếu fingerprint local/server chưa thu được kết quả nên không coi cấu hình hai phía đã được xác minh giống nhau.

## Crash mở màn hình AI / Compose ABI — 15/11/2026

- Log mới 22:00 ngày 15/11 xác định crash hiện tại là **NoSuchMethodError FlowLayoutKt.FlowRow**, từ MealAdvisorScreen.kt:76. Dòng AconfigStorageReadException ở đầu log không phải stack lỗi dựng màn hình. Bản sửa Firebase bên dưới là lỗi riêng trong log cũ, chưa giải quyết lỗi Compose mới này.
- Dependency insight xác nhận debugCompileClasspath chọn foundation-layout **1.7.2**, debugRuntimeClasspath chọn **1.10.0**. Các dependency runtime/debug tooling đẩy phiên bản lên trong khi compiler vẫn thấy ABI cũ.
- app/build.gradle.kts dùng Android Components API để compileConfiguration.shouldResolveConsistentlyWith(runtimeConfiguration) cho mọi variant và nested component (unit/device tests), tránh biên dịch/đóng gói hai phiên bản khác nhau. Không ép hạ runtime hoặc nâng toàn bộ BOM.
- MealAdvisorScreen bỏ FlowRow/ExperimentalLayoutApi, dùng Row cuộn ngang cho các lựa chọn sở thích. Giữ nguyên chức năng chọn gợi ý, không gọi mạng khi mới mở màn hình.
- Đã clean build thành công `assembleDebug`, `testDebugUnitTest` (24 test), `assembleDebugAndroidTest`. Kiểm tra dependency graph sau sửa xác nhận 44 module Compose dùng chung có cùng phiên bản compile/runtime; foundation-layout=1.10.0 ở cả hai bên. Công cụ đối chiếu ở build/verify-compose.gradle (Git bỏ qua).
- Thêm MealAdvisorScreenTest trong androidTest: **đã chạy đạt trên máy ảo API 36.1**, mở màn hình, chọn Món nước, xác nhận ô nhập cập nhật và nút Gợi ý hiển thị/enabled; không gọi Gemini. Kết quả AndroidJUnitRunner `OK (1 test)`, 7,883 giây. Đây là kiểm tra UI trong APK cài thật, bổ sung cho 24 JUnit.
- Các lượt đầu máy ảo bị BIND APPLICATION ANR trước hoặc ngay lúc runner bắt đầu; trace nằm ở ART DexFileVerifier, không phải FlowRow. Đã tăng RAM/CPU, dùng GPU host, chạy package compile cho app/test, rồi chạy lại khi cache đã sẵn sàng với --no-window-animation; lượt cuối đạt. Không đổi code nghiệp vụ để né kiểm tra. Máy ảo do agent mở đã được đóng sau kiểm thử, không wipe data/snapshot.
- APK mới: `build/deliverables/FoodHub-fix-ai-screen-v2.apk` (148.733.636 byte). Cài bản v2 này thay bản FoodHub-fix-crash.apk của lần chỉ sửa Firebase. Không cần cập nhật backend cho thay đổi này.

## Sửa crash lấy Firebase token — 15/11/2026

- Người dùng báo bấm **Hôm nay ăn gì?** bị văng; log đính kèm có timestamp 13/11 cho thấy FATAL trên OkHttp Dispatcher: ExecutionException bọc FirebaseNetworkException tại Tasks.await trong FoodHubApi.kt. Đây là lỗi lấy Firebase ID token chưa được đổi thành IOException; ngoại lệ không kiểm tra thoát khỏi worker, không được catch ở ViewModel.
- Tách `data/remote/FirebaseAuthInterceptor.kt` để chuyển lỗi token ExecutionException/TimeoutException/CancellationException và lỗi auth khác thành IOException cho Retrofit xử lý; ngắt chờ dùng InterruptedIOException, giữ interrupt flag. Không gửi request API khi lấy token thất bại và không tự xóa phiên đăng nhập.
- FoodHubApi.kt giới hạn Tasks.await token 15 giây. Giữ Bearer/Accept header và read timeout riêng AI 60 giây, các API khác 30 giây.
- Thêm 5 `FirebaseAuthInterceptorTest`: callback bất đồng bộ không có uncaught exception theo lỗi log, timeout rồi thử lại thành công, giữ interrupt flag, hủy/lỗi auth không kiểm tra, header và thời gian chờ các route.
- Build đầy đủ `assembleDebug` + `testDebugUnitTest` **đạt**, gồm 5 test mới và 19 test cũ (24 JUnit). Bản cài sửa lỗi: `build/deliverables/FoodHub-fix-crash.apk`, cùng bản build gốc `app/build/outputs/apk/debug/app-debug.apk`. ADB đã kiểm tra với quyền phù hợp, danh sách thiết bị trống nên chưa test UI trên điện thoại. Không cần triển khai lại PHP/Alwaysdata cho bản sửa Android này.

## Tư vấn món Gemini — 15/11/2026

- Đã triển khai mã giai đoạn 1: trang chủ khách có lối vào **Hôm nay ăn gì?**, form sở thích/ngân sách/phạm vi tiền món hoặc gồm phí giao, trạng thái loading/lỗi/rỗng, tối đa ba thẻ món thật và mở thực đơn quán. Route `meal_advisor`, repository và ViewModel riêng, giữ trắng/xanh và Material 3.
- API mới `POST ai/meal-advisor`: Firebase auth, customer/admin active, giới hạn 4 lượt/phút và 20 lượt/giờ. Gemini phân tích yêu cầu rồi xếp hạng ứng viên; server chốt ngân sách, phí giao, giá/ID/quán từ DB và đọc lại sau AI. Món/quán/merchant/chủ quán phải đủ điều kiện bán. Không thay schema.
- Người dùng đã điền khóa riêng `backend/config.gemini.local.php`, đổi model sang `gemini-4-hydrogen` và xác nhận triển khai. Đã đồng bộ khóa/model vào private `/home/chinh/private/foodhub/config.gemini.local.php`, public wrapper được Apache chặn. Không in/commit khóa. Xem `docs/AI_MEAL_ADVISOR.md`.
- Đã tạo `backend/deploy/deploy-meal-advisor.py` để deploy riêng 3 file PHP (2 helper và router), backup, lint, checksum, kiểm tra router hosting chưa bị sửa khác; tùy chọn `--sync-key` đồng bộ tệp riêng. prepare-alwaysdata.php đã biết đóng gói helper; không đóng gói khóa local rỗng đè hosting.
- **ĐÃ triển khai hosting sau xác nhận người dùng**: đợt từ chối auto-review trước đã được giải quyết bằng sự đồng ý rõ ràng. Ba file PHP runtime + wrapper và khóa riêng đã lên đúng Alwaysdata, PHP lint/checksum đạt. Backup cuối phiên `/home/chinh/private/foodhub/backups/meal-advisor-20261115-143608`, các lần trước cùng tiền tố được giữ. Không sửa DB/cấu hình thanh toán.
- Kiểm tra đã đạt: `assembleDebug` + `testDebugUnitTest` đầy đủ bằng JBR Android Studio, 19 JUnit không lỗi; 28 kiểm tra PHP cho ngân sách/đầu vào/ID giả/giá DB/quota và tương thích cấu hình Hydrogen/2.5 Flash; PHP lint helper/router/bundler; Python syntax; git diff --check. APK `app/build/outputs/apk/debug/app-debug.apk` khoảng 149MB.
- Đã chạy `backend/tools/check-meal-advisor.php --repo-ca` chỉ đọc TiDB với quyền công cụ phù hợp: mức 60.000đ có 1 ứng viên; 45.000đ và 0đ có 0. Đây là kiểm tra SQL thật, không phải Gemini thật. --repo-ca chỉ chọn CA công khai cho công cụ, không sửa runtime/tắt TLS. Lần chạy giới hạn quyền không đọc/kết nối được, lần có quyền đã đạt.
- Đã gọi Gemini thật từ hosting: GET model trả 200 đúng `models/gemini-4-hydrogen`; generateContent trả 503 UNAVAILABLE do high demand, một lượt đầy đủ timeout sau 22 giây. Chưa xác nhận gợi ý thật thành công. HTTP hosting health, thiếu token 401, khóa riêng 403, đăng nhập khách Firebase hiện có (isNewUser=false), budget sai 422 đều đạt. Không tạo tài khoản/đơn/thanh toán thử; token chỉ nằm trong bộ nhớ kiểm tra, không ghi log. Chưa test UI điện thoại; ADB chưa khởi động được trong công cụ.
- Model mặc định chuyển Hydrogen theo người dùng, thinkingLevel LOW, bỏ temperature; không gửi thinkingBudget cũ cho Hydrogen. Lỗi Google 5xx thử lại một lần (20 giây lần đầu, 5 giây lần sau); timeout/hết quota không retry, trả lỗi 503 rõ ràng. Android chỉ route AI dùng read timeout 60 giây, route khác giữ 30 giây. Không tự fallback sang model khác.
- Lượt kiểm tra HTTPS cuối sau bản sửa trả đúng 503 **Gemini phản hồi quá lâu** sau giới hạn chờ; các bước health/auth/khóa riêng/budget vẫn đạt. Chưa thể đánh dấu Gemini trả món thật thành công do phía Google đang quá tải/chậm. Người dùng không cần cung cấp thêm khóa hoặc mật khẩu; cần thử lại khi model ổn định, hoặc chỉ đổi model nếu họ yêu cầu.
- Phạm vi: một món/một người, không combo/cá nhân hóa/khoảng cách/voucher/dị ứng; tối đa 80 ứng viên, có thông báo giới hạn. Không tự tạo đơn/thêm giỏ.

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
