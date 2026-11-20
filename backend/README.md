# FoodHub PHP API

Backend PHP online trên **Alwaysdata**, dùng PDO MySQL/TLS để lưu dữ liệu nghiệp vụ trong database **food_delivery_app trên TiDB Cloud** từ 14/11/2026, gồm 32 bảng, 27 cột ID tự tăng.

## Mã nguồn và bản đang chạy

- Mã nguồn: backend/ trong Android project.
- Bản online: https://chinh.alwaysdata.net/food-delivery-api/api/; public /home/chinh/www/food-delivery-api; cấu hình riêng /home/chinh/private/foodhub.
- Router: api/index.php; schema: database/schema.sql; migration: migration/.
- Cấu hình ưu tiên config.local.php, rồi config.runtime.local.php, cuối cùng config.example.php. Backend .gitignore loại các file cấu hình local và service account. Bản source hiện dùng config.runtime.local.php..
- Android project không cần đặt trong htdocs.
- Bản clone mới dùng API online sẵn khi chạy Android. Nếu tự chạy backend, sao chép config.example.php thành config.local.php rồi tự điền TiDB/Firebase/PayOS; service account đặt ngoài repository và trỏ bằng biến môi trường FOODHUB_FIREBASE_SERVICE_ACCOUNT hoặc đường dẫn trong cấu hình local. Mẫu không chứa khóa thật.
- Kết nối TiDB trên hosting dùng helper TLS `database/connection.php`, chứng chỉ `certs/isrgrootx1.pem`, công cụ chỉ đọc `tools/check-database.php`. Xem mục TiDB trong hướng dẫn local. Chỉ đổi khối mysql, giữ cấu hình PayOS/Firebase.

## Tài liệu chính

- [Chạy và cấu hình](../docs/LOCAL_SETUP.md)
- [Triển khai và kiểm tra Alwaysdata](../docs/ALWAYSDATA.md)
- [Endpoint và xác thực](../docs/API.md)
- [Database và quan hệ](../docs/DATABASE.md)
- [Luồng auth, đơn hàng, PayOS, FCM](../docs/WORKFLOWS.md)
- [Trạng thái thực tế và việc cần tiếp tục](../docs/SESSION_HANDOFF.md)

Các route nghiệp vụ yêu cầu Firebase ID token; health check không cần token, webhook PayOS dùng HMAC. Khóa PayOS và service account chỉ đặt phía server.

PayOS đã có checkout/webhook; lỗi PDO::rowCount đã sửa trên bản nguồn và hosting. confirm-webhook đã chấp nhận URL Alwaysdata. Chưa kiểm thử giao dịch tiền thật trong phiên triển khai; không coi xác nhận webhook là kiểm thử toàn bộ thanh toán.
