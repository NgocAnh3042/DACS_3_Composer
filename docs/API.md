# API hiện tại

`POST ai/meal-advisor` hỗ trợ duyệt toàn bộ thực đơn đang bán bằng các trang 8 món sắp theo ID tăng dần. `data.kind=page`, `hasMore` và `menuCursor` mô tả vị trí tiếp theo theo quán, giới hạn tiền món và ID cuối. Khi tiếp tục, Android gửi lại cursor trong phần tử cuối của `history`; mỗi lượt nhận tối đa 8 ID. Có thể gửi `answer` (tối đa 700 ký tự) bên cạnh câu hỏi và ID để giữ ngữ cảnh bốn lượt. Client cũ vẫn gửi câu hỏi và ID để khôi phục trang từ lịch sử gần nhất. Các đề xuất riêng lẻ giữ tối đa 3 thẻ. `kind=meal` là một bữa cùng quán, tổng tiền được tính trên giá hiện tại và phí giao chỉ tính một lần.

Từ 19/11/2026, `POST ai/meal-advisor` nhận thêm `history` tùy chọn, tối đa bốn phần tử theo thứ tự cũ tới mới. Mỗi phần tử có `question` (tối đa 500 ký tự) và `dishIds` (tối đa ba ID chuỗi theo thứ tự hiển thị). Tin nhắn tiếp theo dùng các ID này để tra lại giá/quán/mô tả hiện hành. Trường `data.message` chứa câu trả lời và `data.recommendations` chứa các thẻ món mở được. Ứng dụng cũ có thể bỏ qua lịch sử.

**Tối ưu Alwaysdata 16/11/2026:** `POST ai/meal-advisor` cho customer/admin active, Firebase Bearer token. Giữ payload `message`, `budget`, `budgetScope` (`food`/`total`) và tối đa ba thẻ món thật. Giữ Gemini 4 Hydrogen theo người dùng; một lượt model chọn ID, bộ lọc đơn giản trả ngay, cache riêng tài khoản 5 phút, ngắt chờ Google sau 5,5 giây và dùng kết quả lọc có thông báo rõ. Thêm source/cacheHit/geminiMs/elapsedMs/fallbackReason, tương thích APK hiện tại. Google còn có lúc trả 429/timeout; không coi bộ lọc là câu trả lời Gemini. Xem [AI_MEAL_ADVISOR.md](AI_MEAL_ADVISOR.md).

Nguồn: `backend/api/index.php`, đối chiếu ngày 12/11/2026. Đây là danh mục implementation hiện có, không phải đặc tả của toàn bộ PRD.

Cập nhật ngày 14/11/2026: endpoint/JSON giữ nguyên, **host đã chuyển sang Alwaysdata**, backend dùng `food_delivery_app` trên TiDB Cloud qua PDO MySQL/TLS. PayOS confirm-webhook đã xác nhận URL mới; mã Android đã đổi BASE_URL. App đã cài trước đó cần build/cài lại. Xem ALWAYSDATA và SESSION_HANDOFF.

## 1. Địa chỉ và xác thực

- Android hiện cấu hình: `https://chinh.alwaysdata.net/food-delivery-api/api/`.
- Thay đổi base URL tại `FoodHubClient.BASE_URL`, không sửa rải rác trong từng màn hình.
- Mọi route nghiệp vụ đi qua xác minh Firebase ID token: chữ ký RS256, `kid`, `aud`, `iss`, `sub`, `exp`, `iat`.
- Header: `Authorization: Bearer <Firebase ID token>` và `Accept: application/json`.
- `GET /` là health check không cần đăng nhập. `OPTIONS` xử lý preflight. `POST /payos/webhook` dùng chữ ký PayOS thay Bearer token.

Không gửi token hoặc các khóa dịch vụ vào nội dung tài liệu, issue hay log chia sẻ.

## 2. Dạng phản hồi

| Tình huống | Dạng thường gặp |
| --- | --- |
| Danh sách | `{"data": [...]}` |
| Một đối tượng | `{"data": {...}}` |
| Tạo mới | `{"id": "123"}`; một số route có thêm `data` |
| Cập nhật/xóa | `{"ok": true}` |
| Lỗi | `{"error": "Nội dung lỗi"}` kèm HTTP status |
| Tạo checkout | Trả `checkoutUrl`, `paymentLinkId`, `qrCode` trực tiếp |

`FoodHubClient.list/get/create/update/delete` chuyển các cấu trúc này sang model. Tạo checkout dùng response riêng, không giả định mọi POST đều có cùng dạng.

## 3. Endpoint

Các đường dẫn dưới đây nối sau base `/api/`. Quyền chi tiết nằm ở từng nhánh router; kiểm tra mã nếu thay đổi hành vi.

| Method | Route | Công dụng / phạm vi hiện có |
| --- | --- | --- |
| GET | `/` | Health check và kết nối DB |
| GET | `users/me` | Hồ sơ tài khoản đang đăng nhập |
| POST/PUT/PATCH | `users/me` | Tạo/đồng bộ hồ sơ; khi tạo chỉ nhận role đăng ký thường |
| GET | `users` | Danh sách tài khoản, yêu cầu admin |
| GET | `users/{firebaseUid}` | Admin hoặc người có quan hệ hội thoại theo kiểm tra trong router |
| PATCH | `users/{firebaseUid}` | Admin sửa hồ sơ, vai trò hoặc trạng thái |
| DELETE | `users/{firebaseUid}` | Admin xóa mềm / đình chỉ hồ sơ MySQL |
| GET | `addresses` | Địa chỉ của tài khoản hiện tại |
| POST | `addresses` | Thêm địa chỉ |
| PUT/PATCH | `addresses/{id}` | Sửa địa chỉ thuộc tài khoản |
| DELETE | `addresses/{id}` | Xóa địa chỉ thuộc tài khoản |
| GET | `stores` | Danh sách quán đang hoạt động |
| GET | `stores/{id}` | Chi tiết quán |
| GET | `stores/mine` | Quán của chủ hiện tại |
| POST | `stores/mine` | Tạo merchant/quán khi chưa có; xem kiểm tra role trong router |
| PUT/PATCH | `stores/mine` | Cập nhật quán sở hữu, ảnh, liên hệ, giờ và tọa độ |
| GET | `dishes` | Danh sách món; filter `restaurantId`, `category` |
| GET | `dishes?restaurantId={id}&includeUnavailable=1` | Chủ quán/admin xem cả món không bán |
| POST | `dishes` | Chủ quán/admin thêm món vào quán được phép |
| PUT/PATCH | `dishes/{id}` | Chủ quán/admin sửa món |
| DELETE | `dishes/{id}` | Xóa mềm món của quán được phép |
| GET | `promotions` hoặc `vouchers` | Admin xem toàn bộ; người khác xem voucher đang hiệu lực |
| POST | `promotions` hoặc `vouchers` | Admin tạo voucher |
| PATCH | `promotions/{id}` hoặc `vouchers/{id}` | Hiện xử lý bật/tắt bằng `status` |
| DELETE | `promotions/{id}` hoặc `vouchers/{id}` | Admin xóa voucher; chịu ràng buộc FK |
| GET | `orders` | Lọc theo tài khoản: khách sở hữu, quán sở hữu, driver nhận; admin xem toàn bộ |
| GET | `orders/{id}` | Một đơn trong phạm vi quyền |
| GET | `orders/available` | Driver xem đơn `accepted` chưa có người nhận |
| POST | `orders` | Customer tạo đơn, server tính lại tiền |
| PATCH | `orders/{id}` | Đổi trạng thái, kiểm tra role/sở hữu; chưa kiểm soát đầy đủ mọi cạnh trạng thái |
| GET/PATCH | `drivers/me` | Hồ sơ hoạt động và phương tiện của shipper |
| POST | `deliveries/{orderId}/accept` | Driver available nhận đơn trong transaction |
| POST | `payments/create-link` | Checkout PayOS cho đơn online của khách hiện tại |
| GET | `payments` | Lịch sử thanh toán các đơn của tài khoản hiện tại; chưa phải endpoint payout/admin toàn hệ thống |
| POST | `payments` | Nhánh lưu payment cũ; đọc kỹ implementation trước khi dùng cho một luồng mới |
| POST | `payos/webhook` | Callback gateway, kiểm tra HMAC và số tiền; xem lỗi đã ghi trong bàn giao |
| GET/POST | `conversations` | Hội thoại và tạo hội thoại; admin có nhánh xem hội thoại hỗ trợ |
| GET | `conversations/{id}/messages` | Tin trong hội thoại, hiện giới hạn 50 bản ghi gần nhất |
| POST | `conversations/{id}/messages` | Gửi tin; payload `action=read` dùng đánh dấu đã đọc |
| GET | `notifications` | Hộp thư thông báo của người dùng |
| PATCH | `notifications/{id}` | Đọc một thông báo thuộc người dùng |
| PATCH | `notifications/read-all` | Đọc tất cả thông báo của người dùng |
| POST | `device-tokens` | Ghi/cập nhật token FCM của tài khoản |
| GET/POST | `activity_logs` | Nhật ký; phạm vi truy vấn tùy role/quán/actor |

Không có endpoint CRUD riêng cho mọi bảng trong schema. Đặc biệt chưa có router `carts`, `favorites`, `option_groups` hay `store_staff` hoàn chỉnh.

## 4. Các payload cần nhớ

### Địa chỉ

```json
{
  "name": "Tên người nhận",
  "phone": "Số điện thoại",
  "address": "Số nhà, tên đường, khu vực đã chọn",
  "latitude": 16.0264674,
  "longitude": 108.24489,
  "isDefault": true
}
```

API còn nhận một số alias `receiverName/receiverPhone`. Địa chỉ và ghi chú trong các form hiện được ghép vào `address_line`; không giả định `addressDetail` có cột lưu riêng.

### Tạo đơn

Các trường chính: `restaurantId`, `items[{dishId,quantity}]`, `customerName`, `customerPhone`, `customerAddress`, `customerLat`, `customerLng`, `paymentMethod`, `appliedPromotionId` nếu có.

Server dùng DB/cấu hình để tính lại đơn giá, tổng món, phí giao và mức giảm. Client không có quyền xác nhận đã trả tiền chỉ bằng một trường boolean.

### Trạng thái

```json
{"status": "SHIPPING"}
```

Server chuẩn hóa về chữ thường. Khách chỉ có nhánh hủy; quán, driver và admin có kiểm tra quyền tương ứng, nhưng chưa có state machine chặt cho tất cả trạng thái.

## 5. Chẩn đoán mã lỗi

| HTTP | Thường cần kiểm tra |
| --- | --- |
| 401 | Bearer header, Firebase ID token, hết hạn, chữ ký, đồng hồ máy, sai Firebase project |
| 403 | Role MySQL và quyền sở hữu |
| 404 | Route/ID hoặc chưa có hồ sơ MySQL/quán; phân biệt với lỗi kết nối |
| 409 | Xung đột nhận đơn, trạng thái/phiên thanh toán |
| 422 | Dữ liệu đầu vào, quán/món/voucher không hợp lệ |
| 503 | MySQL không kết nối hoặc PHP không tải được chứng chỉ Firebase/cấu hình dịch vụ thiếu |
| 500 | Xem PHP/Apache error log; không chỉ dựa vào log WindowManager của Android |

## 6. Phạm vi kiểm tra đã xác nhận

Ngày 12/11/2026, dùng Firebase ID token đăng nhập thật của tài khoản thử để gọi `users/me`: trả `role=admin`; gọi endpoint admin `GET users`: thành công. Token không được lưu trong các tài liệu này.
