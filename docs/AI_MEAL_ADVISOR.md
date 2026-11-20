# Hôm nay ăn gì? — FoodHub AI

Backend hiện gọi Groq với model `openai/gpt-oss-120b`. Các mục về Gemini bên dưới ghi lại phiên bản trước. Groq nhận yêu cầu cùng các ứng viên từ TiDB, trả JSON theo schema và ứng dụng đối chiếu ID, quán, giá trước khi trả kết quả. Khóa thuộc máy chủ. Các lượt tra cứu thực đơn trực tiếp vẫn dùng được khi chưa cấu hình khóa.

## Duyệt thực đơn, hỏi về quán và ghép bữa trưa

Menu truy vấn trực tiếp TiDB bằng điều kiện ID lớn hơn ID cuối của trang trước. Mỗi trang đọc thêm một dòng để xác định còn trang sau, độc lập với danh sách rút gọn gửi Gemini. Người dùng có thể duyệt theo quán và mức tiền, hỏi xen về giá rồi tiếp tục. Nút “Xem tiếp menu” chỉ xuất hiện khi phản hồi còn trang.

Khi hỏi tự do, máy chủ xếp hạng món theo nội dung câu hỏi trên toàn bộ tập đang bán trước khi lấy tối đa 80 ứng viên, bổ sung các ID vừa nhắc cùng số món và khoảng giá từ truy vấn thống kê. Gemini 4 Hydrogen nhận câu hỏi, câu trả lời gần đây, thông tin món và quán. Giá, tên món, số lượng và tên quán trong các tham chiếu của câu trả lời được điền từ dữ liệu thực. Khi Google từ chối, FoodHub còn có thể tra món theo mô tả, tra quán bán món, so sánh khoảng giá và chọn một bữa từ món chính, món phụ cùng quán theo ngân sách. Tin trả về có nguồn để phân biệt. Một phần ở đây tính theo đơn vị món mà quán đăng bán.

## Hội thoại FoodHub trên Android, 19/11/2026

Thẻ ở trang chủ mở màn hình chat. Trong cùng màn hình, người dùng có thể xem thực đơn, hỏi món rẻ nhất, tra giá, quán và mô tả, rồi mở quán từ thẻ món. Ô ngân sách mặc định trống. API vẫn áp dụng mức tối đa kỹ thuật 10 triệu đồng. Người dùng có thể nhập hạn mức trong tin nhắn hoặc đặt mức riêng, có tùy chọn tính phí giao.

Mỗi yêu cầu lấy lại thực đơn có thể đặt từ TiDB. Các câu tra cứu đơn giản trả lời bằng dữ liệu FoodHub. Với câu phức tạp, Gemini 4 Hydrogen nhận tối đa bốn câu hỏi trước cùng ID món đã hiển thị, và danh sách món của lượt này. Gemini trả loại câu trả lời, ID và một câu tiếng Việt ngắn có vị trí điền tên, giá, quán. Máy chủ điền dữ liệu sau khi kiểm tra lại món và bỏ câu có tham chiếu không hợp lệ. Cache v5 có cả ngữ cảnh hội thoại trong khóa băm. Lịch sử trên màn hình thuộc phiên đang mở.

## Tối ưu hiệu năng ngày 16/11/2026

- Người dùng chọn **giữ nguyên Gemini 4 Hydrogen**, không tự chuyển sang Flash/Lite.
- Luồng HTTP dùng advise() một lần để nhận ngân sách, cờ unsupported và tối đa ba ID trong enum thực đơn. Model không viết mô tả/lý do dài; server lấy category/giá/quán/ảnh thật. Giới hạn output 768 token bao gồm phần suy luận, thinkingLevel LOW; không gửi temperature.
- Bộ lọc đã hiểu đầy đủ (cơm, món nước, phở/bún/mì, chay/không cay có bằng chứng, thành phần đơn giản, giá rẻ), một ứng viên duy nhất, ngân sách không đủ hoặc yêu cầu ngoài phạm vi được xử lý không chờ Google. Điều kiện phủ định chưa hiểu không bị bỏ qua. Bộ lọc tránh nhầm bánh mì thành món nước, cà phê thành cá, bơ thành bò.
- Cache model 5 phút riêng từng tài khoản và fingerprint thực đơn; kết quả quá hạn bị bỏ qua. Google chậm/quota/lỗi: nghỉ gọi 30 giây và trả bộ lọc/nhắc làm rõ. Thông báo phân biệt quota với phản hồi chậm. Không có món mẫu giả hoặc tự đổi model.
- Đo HTTP từ hosting: bộ lọc/nhắc ngoài phạm vi khoảng 1,35–1,9 giây; một lượt chạm timeout model mất khoảng 7,07 giây tổng (5,503 giây Google + kết nối/xác thực/SQL). Đây là mẫu đo, không phải SLA. Luồng cũ đo trực tiếp trên server 7,47 giây gồm hai lượt Google (2,136 + 4,061 giây).
- Đã có lượt bản một-call trước khi rút đầu ra xác nhận source=gemini và lặp lại source=cache, geminiMs=0. Các lượt cuối sau khi rút đầu ra gặp 429/timeout Google; không tuyên bố đã đo thành công câu trả lời Gemini mới trong mọi trường hợp. Unit test xác minh schema enum/plan/cache; HTTP xác minh bộ lọc thật, giá DB, trường hợp không gọi Google và yêu cầu đồng thời.
- Chỉ thay backend, không yêu cầu APK mới. 55 kiểm tra nhanh + 28 kiểm tra cũ đạt; PHP lint/checksum hosting đạt. Không tạo đơn hoặc ghi dữ liệu nghiệp vụ trong kiểm tra.

## Chức năng

Trang chủ khách hàng → **Hôm nay ăn gì?** → nhập sở thích và ngân sách → **Gợi ý món cho tôi**.
Trả tối đa ba lựa chọn thay thế nhau cho **một món, một người**. Bấm **Xem thực đơn và chọn món** để mở quán và đặt hàng qua luồng hiện có; AI không tự thêm giỏ hoặc tạo đơn.

- Ngân sách mặc định 70.000đ, cho sửa từ 1.000đ đến 10.000.000đ. Bật **Tính cả phí giao hàng** để server trừ phí giao trước khi lọc món.
- Server nhận diện nhanh các mức như 60k/60.000đ/0,05 triệu và lấy mức thấp hơn với biểu mẫu; Gemini bổ sung phân tích khi cần. Phạm vi tiền món/tổng tiền lấy theo công tắc của biểu mẫu.
- Chỉ xét món còn bán/còn tồn (hoặc không quản lý tồn), quán đang bật mở và active, chưa xóa, merchant và chủ quán active. Theo cờ `is_open` hiện có, chưa tự suy ra giờ mở từ lịch.
- Giá, ảnh, tên món, quán và phí giao lấy từ backend; ID Gemini trả được đối chiếu và dữ liệu được đọc lại sau xếp hạng.
- Không có món đủ thông tin thì thông báo thật, không thay bằng món mẫu. Yêu cầu nhiều người/combo/dị ứng/an toàn thực phẩm được hướng dẫn trao đổi với quán. Khẩu vị dựa trên mô tả, không phải chứng nhận thành phần.
- Xét tối đa 80 ứng viên ưu tiên rating/ID trong ngân sách; UI thông báo khi danh mục bị giới hạn. Chưa lọc khoảng cách, cá nhân hóa, voucher, dinh dưỡng hoặc tìm vector.
- Mỗi câu hỏi mới dùng tối đa **một lần gọi Gemini**, gộp hiểu yêu cầu và chọn ID. Catalog gửi tên/giá/nhóm/quán và tối đa 160 ký tự mô tả, kết quả giới hạn 768 token, vẫn dùng Hydrogen LOW. Các hàm interpret/rank cũ chỉ còn để tương thích công cụ kiểm tra cũ, không nằm trong route HTTP.
- Không gọi Gemini khi không có món phù hợp, yêu cầu nhiều người/dị ứng/y tế đã nhận diện, hoặc chỉ có một món và bộ lọc đã hiểu đầy đủ yêu cầu.
- Cache riêng từng tài khoản trong 5 phút; khóa phụ thuộc nội dung yêu cầu, ngân sách, phí giao, model và toàn bộ dữ liệu ứng viên. Mỗi lần hỏi vẫn đọc SQL trước, nên thay giá/tên/mô tả/tình trạng sẽ làm cache mất hiệu lực. Giá/quán/ảnh luôn lấy từ database, không lấy từ cache AI.
- Khi Gemini chậm/lỗi/hết hạn mức, bộ lọc bảo thủ có thể trả món thật theo điều kiện đã hiểu. Điều kiện chưa hiểu hoặc phủ định chưa hỗ trợ sẽ không được bỏ qua để chọn đại món; yêu cầu không cay/chay cần bằng chứng trong thông tin món. Message hiển thị rõ kết quả do FoodHub lọc; không giả làm câu trả lời Gemini.
- Giới hạn 4 lượt/phút và 20 lượt/giờ/tài khoản áp dụng cho lần gọi model mới; cache/bộ lọc không tiêu thụ lượt model. Khi model vừa lỗi, tạm tránh gọi tiếp trong 30 giây. Yêu cầu đang xử lý cùng tài khoản dùng khóa không chờ để tránh xếp hàng nhiều lượt model.

## Khóa Gemini

Tệp riêng trên máy: `backend/config.gemini.local.php`, đã được Git bỏ qua và Apache chặn HTTP. Điền khóa vào chuỗi `api_key`, lưu tệp, không gửi khóa vào chat. Mẫu công khai: `backend/config.gemini.example.php`.

```php
<?php
return [
    'api_key' => 'DIEN_KHOA_CUA_BAN_O_DAY',
    'model' => 'gemini-4-hydrogen',
];
```

Không đưa khóa vào Android/BuildConfig. Có thể dùng `FOODHUB_GEMINI_API_KEY` và `FOODHUB_GEMINI_MODEL` trên server, ưu tiên hơn tệp. Model mặc định hiện là Gemini 4 Hydrogen theo cấu hình người dùng: generateContent, JSON Schema, thinkingLevel LOW, không gửi temperature. Vẫn hỗ trợ cấu hình cũ 2.5 Flash với thinkingBudget 0. Kiểm tra khả năng hỗ trợ khi đổi model.

Mặc định giới hạn **5.500ms cho một lượt Gemini**, không retry đồng bộ. Có thể cấu hình timeout_ms trong private Gemini config, chỉ nhận 1.000–6.000ms. Khi chạm giới hạn, trả bộ lọc/nhắc diễn đạt lại thay vì chờ đến timeout Android. Thời gian toàn API còn gồm TLS, Firebase, SQL và mạng; không hứa SLA 5,5 giây cho toàn request. Android vẫn tương thích với JSON cũ, không cần APK mới cho tối ưu này. Không tự đổi model; người dùng đã chọn giữ Gemini 4 Hydrogen.

Hosting đích: `/home/chinh/www/food-delivery-api`; khóa thực ở `/home/chinh/private/foodhub/config.gemini.local.php`, public chỉ giữ wrapper require. Không thay cấu hình MySQL/Firebase/PayOS.

## API

`POST /api/ai/meal-advisor`, Firebase Bearer token, chỉ customer/admin active.

```json
{"message":"Tôi muốn ăn cơm dưới 60k","budget":70000,"budgetScope":"food"}
```

`budgetScope`: food hoặc total. Kết quả trong data giữ message, preferences, budget, budgetScope, deliveryFee, catalogLimited, recommendations; thêm source (gemini/cache/filters), cacheHit, model, elapsedMs, geminiMs, fallbackReason để chẩn đoán. Header Server-Timing có thời gian xử lý route và Gemini. elapsedMs bắt đầu sau bước kết nối/xác thực chung, không phải toàn thời gian mạng. Mỗi recommendation chứa dish tương thích Android, restaurantName, reason, estimatedTotal. Tổng dự tính cho một món chưa voucher; server chốt lại lúc tạo đơn.

Lỗi HTTP: 401 token, 403 quyền/trạng thái, 422 đầu vào, 503 khi dịch vụ/DB không xử lý được. Lỗi/quota/timeout riêng Gemini được chuyển sang kết quả filters với message rõ và HTTP 200. fallbackReason chỉ chứa mã phân loại an toàn, không trả lỗi gốc/khóa Google.

Yêu cầu và thông tin món được gửi tới Google khi cần gọi model. Không gửi hồ sơ, lịch sử đơn, địa chỉ hoặc Firebase token tới Gemini. Cache ngoài web root, quyền file 0600, riêng từng UID; giữ tối đa kết quả model gần nhất/tài khoản trong 5 phút để sử dụng, không lưu raw prompt/token. File cache hết hạn không được dùng, được thay khi có kết quả mới; không phải lịch sử chat.

## Triển khai

Chỉ chạy sau khi người dùng đồng ý cập nhật hosting. Xem ALWAYSDATA.md về fingerprint và credential riêng.

```powershell
python backend/deploy/deploy-meal-advisor.py
# Sau khi điền khóa, đồng bộ cấu hình riêng:
python backend/deploy/deploy-meal-advisor.py --sync-key
```

Dùng SSH fingerprint hiện có, so router hosting với Git HEAD/source, sao lưu ngoài web, lint candidate, upload helper trước router và kiểm tra checksum. Mặc định giữ khóa hosting đã có; --sync-key mới thay bằng tệp local. Không thay database/thanh toán.

prepare-alwaysdata.php cũng đóng gói helper mới; chỉ kèm cấu hình Gemini khi local có khóa, tránh ghi đè khóa hosting bằng mẫu rỗng.

## Kiểm tra

```powershell
php backend/tests/meal_advisor_test.php
php backend/tests/meal_advisor_fast_test.php
php backend/tools/check-meal-advisor.php --repo-ca
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --no-daemon
```

--repo-ca chỉ chọn CA công khai trong repo cho công cụ đọc DB, không sửa runtime hoặc tắt TLS.

Sau triển khai và có khóa, thử thật: món cơm, món nước, yêu cầu không có dữ liệu, ngân sách gồm phí giao, nhiều người, giới hạn lượt; xác nhận mở đúng quán. Unit test dùng transport giả không chứng minh Gemini thật đã hoạt động.

Tài liệu Google: [Structured outputs](https://ai.google.dev/gemini-api/docs/structured-output), [generateContent](https://ai.google.dev/api/generate-content), [Vòng đời model](https://ai.google.dev/gemini-api/docs/deprecations).
