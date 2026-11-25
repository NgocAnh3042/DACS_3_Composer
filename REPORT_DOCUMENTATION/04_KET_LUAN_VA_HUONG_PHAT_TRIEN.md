# KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN

## 1. Kết quả đạt được

Đề tài **"Xây dựng ứng dụng đặt và giao đồ ăn trực tuyến FoodHub"** đã hoàn thành toàn bộ các mục tiêu nghiên cứu và triển khai đề ra. Hệ thống không chỉ giải quyết trọn vẹn bài toán thương mại điện tử giao đồ ăn bốn bên (Khách hàng, Quán ăn, Tài xế, Quản trị viên) mà còn tạo dựng một nền tảng dịch vụ kỹ thuật số hiện đại, an toàn và có tính ứng dụng thực tiễn cao.

Trải qua quá trình khảo sát nghiệp vụ, phân tích thiết kế kiến trúc hệ thống bằng mô hình hóa UML, thiết kế cơ sở dữ liệu quan hệ chuẩn hóa và lập trình thực nghiệm, nhóm sinh viên đã đạt được những kết quả nổi bật sau:

### 1.1. Về mặt Chức năng nghiệp vụ
- **Hiện thực hóa trọn vẹn chu trình đặt và giao hàng:** Hệ thống vận hành mượt mà toàn bộ quy trình từ khâu duyệt món, tìm kiếm quán ăn, quản lý giỏ hàng, đặt hàng, tiếp nhận đơn tại nhà hàng, phân phối đơn cho tài xế, bám sát lộ trình giao hàng trên bản đồ số cho đến khi hoàn tất đơn hàng và thống kê tài chính.
- **Tích hợp các tính năng công nghệ hiện đại:**
  - *Thanh toán trực tuyến VietQR qua PayOS:* Cho phép khách hàng chuyển khoản ngân hàng nhanh chóng, tự động hóa hoàn toàn quy trình đối soát và xác nhận thanh toán thông qua Webhook có chữ ký mật mã HMAC-SHA256.
  - *Bản đồ số và Định tuyến thông minh:* Ứng dụng MapLibre Native kết hợp cùng dữ liệu gạch bản đồ OpenFreeMap và động cơ OSRM giúp tài xế xem lộ trình di chuyển tối ưu mà không phụ thuộc vào các dịch vụ bản đồ thương mại đắt đỏ.
  - *Cơ chế khóa giao dịch CSDL chống tranh chấp:* Áp dụng kỹ thuật khóa bi quan (`FOR UPDATE`) ở tầng cơ sở dữ liệu để giải quyết triệt để bài toán xung đột khi nhiều tài xế cùng bấm nhận một đơn hàng tại cùng thời điểm.
  - *Trợ lý ẩm thực thông minh AI ("Hôm nay ăn gì?"):* Ứng dụng mô hình ngôn ngữ lớn (Groq LLM / Gemini 4 Hydrogen) kết hợp cùng dữ liệu thực đơn thực tế từ TiDB Cloud để tư vấn món ăn chuẩn xác theo ngân sách và khẩu vị của người dùng.
  - *Giao tiếp đa bên thời gian thực:* Cho phép khách hàng nhắn tin trao đổi trực tiếp với quán ăn hoặc tài xế giao hàng; tích hợp thông báo đẩy Firebase Cloud Messaging (FCM) tức thời.

### 1.2. Về mặt Công nghệ và Kiến trúc
- **Ứng dụng di động Native hiệu năng cao:** Xây dựng hoàn toàn bằng ngôn ngữ Kotlin 2.0 và Jetpack Compose kết hợp Material 3, áp dụng kiến trúc MVVM và luồng dữ liệu một chiều (UDF) với StateFlow và Kotlin Coroutines, đảm bảo ứng dụng chạy mượt mà, phản hồi tức thì và không bị giật lag khung hình.
- **Hệ thống máy chủ dịch vụ chuẩn RESTful:** Triển khai bộ định tuyến API gọn nhẹ, không trạng thái (Stateless) bằng ngôn ngữ PHP trên môi trường Alwaysdata, kiểm soát nghiêm ngặt quyền sở hữu dữ liệu của từng vai trò người dùng.
- **Cơ sở dữ liệu phân tán TiDB Cloud:** Vận hành 32 bảng dữ liệu có cấu trúc với các ràng buộc toàn vẹn khóa ngoại, mã hóa kết nối bắt buộc qua giao thức TLS với chứng chỉ CA `ISRG Root X1`, đảm bảo an toàn tuyệt đối cho dữ liệu doanh nghiệp.
- **Bảo mật danh tính hai lớp:** Kết hợp Firebase Authentication và CSDL TiDB Cloud. Mật khẩu người dùng được bảo vệ bởi hạ tầng bảo mật của Google; các truy vấn mạng được xác thực thông qua Firebase ID Token có chữ ký số RSA (RS256).

### 1.3. Về mặt Trải nghiệm người dùng (UI/UX)
- Giao diện người dùng được thiết kế đồng bộ theo tông màu Trắng - Xanh dương (`#2159BC`), mang lại cảm giác tin cậy, sạch sẽ và hiện đại.
- 100% các nhãn điều hướng, nút bấm, thông báo trạng thái và form nhập liệu đều được Việt hóa tự nhiên, rõ ràng, phù hợp với văn hóa và thói quen của người tiêu dùng Việt Nam.
- Thiết kế thẻ Voucher Hero Banner với góc nghiêng 12 độ bắt mắt và thao tác vuốt ngang mượt mà tạo điểm nhấn ấn tượng cho màn hình trang chủ.

### 1.4. Về mặt Kỹ năng cá nhân
- Củng cố và nâng cao tư duy phân tích, thiết kế hệ thống phần mềm quy mô lớn theo chuẩn công nghiệp.
- Thành thạo kỹ năng thiết kế cơ sở dữ liệu quan hệ, làm việc với các mức cô lập giao dịch và giải quyết các bài toán xung đột đồng thời (Concurrency Control).
- Rèn luyện kỹ năng làm việc nhóm, quản lý phiên bản mã nguồn bằng Git và năng lực viết tài liệu kỹ thuật học thuật chuyên nghiệp.

---

## 2. Hạn chế còn tồn đọng

Mặc dù hệ thống đã đáp ứng đầy đủ các yêu cầu cốt lõi của đề tài, song do giới hạn về mặt thời gian và nguồn lực nghiên cứu, dự án vẫn còn một số điểm hạn chế nhất định cần được hoàn thiện:

1. **Khả năng xử lý tùy chọn món ăn chuyên sâu (Toppings & Options):** Mặc dù cơ sở dữ liệu đã thiết kế sẵn các bảng `option_groups`, `options`, `dish_option_groups` và `order_item_options`, tuy nhiên giao diện đặt món hiện tại mới chỉ hỗ trợ chọn món và điều chỉnh số lượng đơn giản, chưa hoàn thiện luồng chọn size, mức đường, mức đá hoặc món ăn kèm phức tạp trên ứng dụng khách.
2. **Cơ chế quản lý ví và đối soát dòng tiền tự động:** Báo cáo thu nhập của tài xế hiện đang được tổng hợp dựa trên tổng phí giao hàng (`shippingFee`) của các đơn hàng hoàn tất; hệ thống chưa tích hợp phân hệ Ví điện tử nội bộ (In-app Wallet) để hỗ trợ nạp tiền, rút tiền tự động (Payouts) hoặc tính toán tỷ lệ chiết khấu hoa hồng thực tế của nhà hàng.
3. **Mức độ tương tác thời gian thực của một số màn hình:** Một số danh sách dữ liệu (như danh sách đơn hàng của quán, lịch sử tin nhắn chat) hiện vẫn đang dựa vào cơ chế Polling theo chu kỳ ngắn có kiểm soát vòng đời (Lifecycle-aware Polling) thay vì kết nối Socket hai chiều liên tục (như WebSocket toàn phần).
4. **Phân hệ AI chưa hỗ trợ nhận diện thị giác máy tính:** Trợ lý ảo AI hiện mới chỉ tiếp nhận câu hỏi dưới dạng văn bản (Text-based), chưa được tích hợp mô hình thị giác máy tính (Computer Vision) để nhận diện món ăn hoặc đọc hóa đơn qua hình ảnh chụp từ camera.

---

## 3. Hướng nghiên cứu và phát triển

Dựa trên những kết quả đã đạt được và các mặt hạn chế nêu trên, nhóm nghiên cứu đề xuất các định hướng phát triển và mở rộng hệ thống trong tương lai như sau:

1. **Mở rộng Hệ thống Thanh toán và Ví điện tử Đa kênh:**
   - Tích hợp thêm các cổng thanh toán điện tử phổ biến tại Việt Nam như VNPay, MoMo, ZaloPay và thẻ thanh toán quốc tế (Visa/Mastercard).
   - Xây dựng phân hệ Ví tài xế và Ví quán ăn, hỗ trợ luồng nạp tiền ký quỹ, tự động khấu trừ hoa hồng theo từng cuốc giao và kết nối API chi hộ (Payout API) của ngân hàng để chuyển tiền về tài khoản cá nhân.
2. **Nâng cấp Hệ thống Thời gian thực (Full Realtime Infrastructure):**
   - Thay thế toàn bộ cơ chế Polling chu kỳ bằng giao thức WebSocket hai chiều (sử dụng Socket.IO hoặc Ktor WebSocket Server) để cập nhật tức thì trạng thái đơn hàng và tin nhắn chat với độ trễ dưới 50ms.
   - Ứng dụng công nghệ bộ nhớ đệm phân tán Redis (Redis In-Memory Cache) trên máy chủ để lưu trữ thực đơn món ăn và phiên làm việc, giảm tải tối đa cho cụm CSDL TiDB Cloud.
3. **Ứng dụng Trí tuệ Nhân tạo và Học máy chuyên sâu (Deep AI Integration):**
   - Xây dựng hệ thống khuyến nghị món ăn cá nhân hóa (Personalized Recommendation Engine) dựa trên lịch sử đặt món, khoảng cách địa lý và hành vi duyệt món của từng khách hàng.
   - Huấn luyện mô hình Computer Vision nhận diện món ăn từ hình ảnh khách hàng tải lên để tự động tìm kiếm các quán ăn đang bán món tương tự trong khu vực.
   - Nâng cấp Trợ lý CSKH ảo với khả năng phân tích cảm xúc (Sentiment Analysis) để tự động ưu tiên hỗ trợ các trường hợp khách hàng không hài lòng.
4. **Tích hợp Mạng lưới Vận chuyển Đa bên (Logistics Aggregation):**
   - Kết nối API với các đơn vị vận chuyển chuyên nghiệp (Ahamove, Lalamove, Giao Hàng Nhanh) để tự động điều phối tài xế bên ngoài khi hệ thống thiếu hụt tài xế nội bộ vào các khung giờ cao điểm.
5. **Nâng cấp Phân hệ Báo cáo và Quản trị Doanh nghiệp:**
   - Bổ sung các công cụ dự báo doanh thu và xu hướng ẩm thực cho chủ quán dựa trên dữ liệu lịch sử.
   - Xây dựng tính năng quản lý chuỗi nhiều chi nhánh (Multi-store Franchises) dưới cùng một thương hiệu.
