# PHÂN TÍCH CÁC LUỒNG NGHIỆP VỤ HỆ THỐNG (BUSINESS FLOW ANALYSIS)

## 1. Luồng Khởi động và Định tuyến Vai trò (App Startup & Role Routing)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant App as Android Client
    participant Pref as Local Storage
    participant FA as Firebase Auth
    participant API as Backend API
    participant DB as TiDB Cloud

    User->>App: Mở ứng dụng FoodHub
    App->>FA: Kiểm tra phiên đăng nhập (currentUser)
    alt Chưa đăng nhập
        App->>User: Điều hướng màn hình Chào mừng / Đăng nhập
    else Đã có phiên đăng nhập
        App->>FA: Lấy Firebase ID Token hợp lệ
        FA-->>App: Trả ID Token (RS256)
        App->>API: GET /api/users/me (Bearer Token)
        API->>API: Xác thực chữ ký số OpenSSL
        API->>DB: Truy vấn SELECT * FROM users WHERE firebase_uid=?
        DB-->>API: Trả bản ghi thông tin & role
        API-->>App: Trả User Profile (role: user/restaurant/shipper/admin)
        alt role == user
            App->>User: Điều hướng MainRouteContainerCustomer (Trang chủ khách)
        else role == restaurant
            App->>User: Điều hướng MainRouteContainerRestaurant (Quản lý quán)
        else role == shipper
            App->>User: Điều hướng MainRouteContainerShipper (Bảng tin giao hàng)
        else role == admin
            App->>User: Điều hướng MainRouteContainerAdmin (Dashboard Quản trị)
        end
    end
```

---

## 2. Luồng Đặt hàng và Tính toán Giá trị Đơn hàng (Order Placement Flow)

Hệ thống FoodHub áp dụng nguyên tắc **Chốt giá và kiểm tra tính hợp lệ tại Máy chủ (Server-side Price & Rule Enforcement)**:

```mermaid
flowchart TD
    A[Khách hàng thêm món vào giỏ hàng] --> B[Chọn địa chỉ giao hàng và mã Voucher]
    B --> C[Bấm Đặt hàng - POST /api/orders]
    C --> D[Backend mở MySQL Transaction với chế độ Khóa dòng FOR UPDATE]
    D --> E{Kiểm tra trạng thái quán?}
    E -- Quán đóng cửa / Tạm nghỉ --> E1[Rollback Transaction & Trả lỗi 422: Quán không nhận đơn]
    E -- Quán đang mở --> F{Kiểm tra tính hợp lệ của món ăn?}
    F -- Món hết hàng / Sai quán --> F1[Rollback Transaction & Trả lỗi 422: Món không hợp lệ]
    F -- Hợp lệ --> G[Đọc đơn giá thực tế từ bảng dishes & Tính Subtotal]
    G --> H{Có áp dụng Voucher?}
    H -- Có --> I[Khóa bản ghi Voucher FOR UPDATE & Kiểm tra hạn mức, hạn dùng, min_order]
    I -- Không hợp lệ --> I1[Rollback Transaction & Báo lỗi Voucher]
    I -- Hợp lệ --> J[Tính mức giảm Discount Amount & Tăng used_count thêm 1]
    H -- Không --> K[Discount Amount = 0]
    J --> L[Lấy phí ship delivery_fee từ cấu hình máy chủ]
    K --> L
    L --> M["Tổng tiền = max(0, Subtotal + Phí ship - Giảm giá)"]
    M --> N[Tạo bản ghi orders ở trạng thái pending hoặc pending_payment]
    N --> O[Snapshot danh sách món vào bảng order_items]
    O --> P[Tạo bản ghi bảng deliveries & payments]
    P --> Q[Ghi log vào order_status_logs]
    Q --> R[Commit Transaction thành công]
    R --> S[Gửi FCM Push Notification cho Quán ăn: Có đơn hàng mới!]
    S --> T[Trả kết quả Order ID và Tổng tiền chốt về cho Android Client]
```

---

## 3. Luồng Thanh toán Trực tuyến qua Cổng PayOS (Online Payment Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Khách hàng
    participant Android as FoodHub App
    participant API as Backend API
    participant PayOS as Cổng PayOS
    participant Webhook as API /payos/webhook
    participant DB as TiDB Cloud
    participant FCM as Firebase Cloud Messaging

    Customer->>Android: Chọn thanh toán PayOS và bấm Xác nhận
    Android->>API: POST /api/payments/create-link (orderId)
    API->>DB: Truy vấn số tiền thực tế từ orders & payments
    API->>API: Tạo chữ ký số HMAC-SHA256 với checksum_key
    API->>PayOS: Gọi API /v2/payment-requests
    PayOS-->>API: Trả checkoutUrl, paymentLinkId, qrCode
    API->>DB: Cập nhật payment_link_id, checkout_url, status='processing'
    API-->>Android: Trả link thanh toán
    Android->>Customer: Mở Chrome Custom Tabs hiển thị trang thanh toán VietQR
    Customer->>PayOS: Quét mã QR chuyển khoản ngân hàng thành công
    PayOS->>Webhook: Gửi HTTP POST Webhook kèm payload & chữ ký HMAC
    Webhook->>Webhook: Kiểm tra chữ ký HMAC-SHA256
    alt Chữ ký không hợp lệ
        Webhook-->>PayOS: Trả lỗi 401 Unauthorized
    else Hợp lệ
        Webhook->>DB: Bắt đầu Transaction
        Webhook->>DB: Kiểm tra số tiền trùng khớp & Ghi nhận payment_webhook_events
        Webhook->>DB: Cập nhật payments (status='paid', paid_at=NOW())
        Webhook->>DB: Cập nhật orders (payment_status='paid', status='pending')
        Webhook->>DB: Ghi log trạng thái order_status_logs
        Webhook->>DB: Commit Transaction
        Webhook-->>PayOS: Phản hồi HTTP 200 OK
        Webhook->>FCM: Gửi push thông báo thanh toán thành công cho Khách và Quán
        Android->>API: Polling kiểm tra trạng thái đơn hàng
        API-->>Android: Trả trạng thái đơn đã thanh toán (PAID)
        Android->>Customer: Chuyển hướng màn hình Đơn hàng đang xử lý
    end
```

---

## 4. Luồng Vận chuyển và Giao nhận Hàng (Delivery Lifecycle)

```mermaid
stateDiagram-v2
    [*] --> PendingPayment: Đơn hàng thanh toán PayOS
    PendingPayment --> Pending: PayOS Webhook xác nhận đã trả tiền
    [*] --> Pending: Đơn hàng thanh toán tiền mặt COD
    
    Pending --> Processing: Quán ăn bấm "Tiếp nhận đơn"
    Processing --> Accepted: Quán làm xong món, sẵn sàng giao
    
    Accepted --> Assigned: Tài xế khả dụng bấm "Nhận giao đơn" (FOR UPDATE Lock)
    
    Assigned --> Shipping: Tài xế đến quán lấy món (Đã lấy hàng)
    
    Shipping --> Completed: Tài xế giao thành công cho khách hàng
    
    Pending --> Cancelled: Khách hàng hủy đơn (Khi chưa chuẩn bị)
    Processing --> Cancelled: Quán ăn hủy đơn do hết nguyên liệu
    Accepted --> Cancelled: Quán ăn hủy đơn
```

- **Chi tiết cơ chế chống tranh chấp nhận đơn:** Khi tài xế bấm nút "Nhận đơn", Backend khởi tạo một Transaction với câu lệnh:
  `SELECT d.order_id FROM deliveries d JOIN orders o ON o.id=d.order_id WHERE d.order_id=? AND d.driver_id IS NULL AND o.status='accepted' FOR UPDATE`
  Nếu đơn hàng đã bị tài xế khác nhận trước dù chỉ vài phần nghìn giây, câu lệnh sẽ trả về rỗng và hệ thống tự động Rollback, trả về mã lỗi `409 Conflict: Đơn vừa được nhận hoặc không còn khả dụng`.
