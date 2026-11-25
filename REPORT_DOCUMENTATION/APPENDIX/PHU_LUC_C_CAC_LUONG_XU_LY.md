# PHỤ LỤC C. CÁC LUỒNG XỬ LÝ DỮ LIỆU ĐẶC THÙ (SPECIALIZED PROCESS FLOWS)

Phụ lục này làm rõ các cơ chế kỹ thuật chuyên sâu được hiện thực hóa trong mã nguồn của hệ thống FoodHub, bao gồm cơ chế bảo mật xác thực, cơ chế chống tranh chấp nhận đơn và giải pháp đồng bộ dữ liệu kết hợp.

---

## 1. Cơ chế Xác thực và Cấp quyền Hai Lớp (Hybrid Authentication)

Hệ thống kết hợp sức mạnh bảo mật của **Firebase Authentication** và quyền quản trị chặt chẽ của **Cơ sở dữ liệu Quan hệ TiDB**:

```mermaid
flowchart TD
    Client[Android Client] -->|1. Đăng nhập Email/Pass hoặc Google| Firebase[Google Firebase Auth]
    Firebase -->>Client: 2. Cấp Firebase User & Firebase ID Token (JWT RS256)
    Client -->|3. Gửi HTTP Request kèm Bearer ID Token| PHP[PHP Backend API]
    PHP -->|4. Lấy Google Public Keys có cache| GoogleCert[Google x509 Certs API]
    PHP -->|5. Xác thực chữ ký số bằng OpenSSL| Verify{Hợp lệ?}
    Verify -- Không --> Reject[Trả lỗi 401 Unauthorized]
    Verify -- Hợp lệ --> QueryUser[Truy vấn bảng users theo firebase_uid]
    QueryUser --> CheckRole{Tìm thấy hồ sơ?}
    CheckRole -- Chưa có --> RelinkCheck{Email đã verified?}
    RelinkCheck -- Có --> Relink[Liên kết lại ID cũ trong TiDB]
    RelinkCheck -- Không --> CreateProfile[Tạo hồ sơ mới với role=customer]
    CheckRole -- Đã có --> Authorize[Kiểm tra Role MySQL: customer, seller, driver, admin]
    Authorize --> Business[Thực thi Logic Nghiệp vụ tương ứng]
```

- **Điểm ưu việt:**
  - Mật khẩu người dùng không bao giờ lưu trữ trên CSDL TiDB, triệt tiêu nguy cơ lộ lọt mật khẩu khi bị tấn công dữ liệu.
  - Phân quyền người dùng hoàn toàn do hệ thống backend quyết định dựa trên CSDL tập trung, ngăn chặn triệt để hành vi giả mạo vai trò từ phía ứng dụng khách.

---

## 2. Cơ chế Khóa Giao dịch Chống Tranh chấp Nhận đơn (Concurrency Control in Order Acceptance)

Trong môi trường thực tế, khi một quán ăn chuẩn bị xong món và chuyển trạng thái đơn sang Đã sẵn sàng (`accepted`), có thể có hàng chục tài xế xung quanh cùng nhìn thấy đơn và bấm "Nhận đơn" tại cùng một tích tắc.

Để xử lý bài toán này, FoodHub áp dụng cơ chế **Khóa bi quan (Pessimistic Locking)** ở tầng cơ sở dữ liệu:

```sql
START TRANSACTION;

-- 1. Khóa và kiểm tra tài xế có đang sẵn sàng không
SELECT id, work_status FROM drivers WHERE user_id = :userId FOR UPDATE;

-- 2. Khóa và kiểm tra đơn hàng có còn trống (driver_id IS NULL) và ở trạng thái 'accepted' không
SELECT d.order_id 
FROM deliveries d 
JOIN orders o ON o.id = d.order_id 
WHERE d.order_id = :orderId 
  AND d.driver_id IS NULL 
  AND o.status = 'accepted' 
FOR UPDATE;

-- 3. Gán tài xế vào chuyến giao hàng
UPDATE deliveries 
SET driver_id = :driverId, status = 'assigned', assigned_at = NOW() 
WHERE order_id = :orderId AND driver_id IS NULL;

-- 4. Chuyển trạng thái tài xế sang đang bận
UPDATE drivers SET work_status = 'busy' WHERE id = :driverId;

COMMIT;
```

Nếu một tài xế khác gửi yêu cầu chậm hơn dù chỉ 1 mili-giây, hàng dữ liệu đã bị khóa bởi giao dịch đầu tiên; khi câu truy vấn của tài xế thứ hai được thực thi thì điều kiện `driver_id IS NULL` không còn thỏa mãn, hệ thống lập tức Rollback và trả về mã lỗi HTTP 409 rõ ràng, đảm bảo một đơn hàng chỉ có duy nhất một tài xế nhận giao.

---

## 3. Chiến lược Đồng bộ Dữ liệu Kết hợp (Hybrid Data Synchronization Strategy)

FoodHub không sử dụng một giải pháp đồng bộ đơn lẻ mà phối hợp linh hoạt 3 cơ chế tùy theo tính chất của từng loại dữ liệu:

1. **Cơ chế Polling chu kỳ ngắn có kiểm soát vòng đời (Lifecycle-aware Polling):**
   - *Phạm vi áp dụng:* Danh sách đơn hàng tài xế (4s), tin nhắn chat (2s), thực đơn món ăn (5s), doanh thu tài xế (10s).
   - *Tối ưu hóa:* Chỉ thực hiện polling khi màn hình tương ứng đang ở trạng thái `RESUMED` trong vòng đời Android. Tự động ngắt khi người dùng chuyển tab hoặc thoát ứng dụng nhằm tiết kiệm pin và băng thông.
2. **Cơ chế Lắng nghe Trực tiếp Thời gian thực (Real-time Event Listeners):**
   - *Phạm vi áp dụng:* Tọa độ GPS tài xế di chuyển (`tracking/{orderId}`), trạng thái Online/Offline của người dùng (`presence/{uid}`), trạng thái gõ phím (`typing`).
   - *Nền tảng:* Firebase Realtime Database với cơ chế socket hai chiều độ trễ cực thấp (< 100ms).
3. **Cơ chế Thông báo Đẩy Bất đồng bộ (Push-driven Notifications):**
   - *Phạm vi áp dụng:* Thông báo đơn hàng mới cho chủ quán, thông báo cập nhật trạng thái đơn cho khách hàng, thông báo khi có tin nhắn mới khi ứng dụng đang đóng.
   - *Nền tảng:* Firebase Cloud Messaging (FCM HTTP v1) kết nối trực tiếp từ máy chủ backend.
