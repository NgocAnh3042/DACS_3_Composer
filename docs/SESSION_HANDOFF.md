# Bàn giao phiên làm việc — FoodHub

Cập nhật **19/11/2026**, múi giờ làm việc Asia/Bangkok. Đọc trước khi tiếp tục dự án.

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
