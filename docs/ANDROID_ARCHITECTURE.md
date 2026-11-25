# Kiến trúc Android — trạng thái khảo sát

Cập nhật: 26/11/2026. Tài liệu mô tả hiện trạng và đích refactor; phần đích không có nghĩa là đã triển khai.

## Hiện trạng

```mermaid
flowchart TD
    User[Người dùng] --> Screen[Jetpack Compose Screen]
    Screen --> VM[ViewModel / StateFlow]
    VM --> Repo[Repository, nếu feature dùng]
    VM -. một số nơi gọi trực tiếp .-> API[FoodHubClient / Retrofit]
    Screen -. một số call site cũ gọi trực tiếp .-> API
    Repo --> API
    API -->|Firebase ID token + HTTPS| PHP[PHP API: Alwaysdata hoặc local qua ngrok]
    PHP --> DB[(TiDB Cloud)]
    VM --> Firebase[Firebase Auth / Realtime Database / FCM]
    Repo --> External[Cloudinary / API dịch vụ ngoài]
```

Hiện trạng là kiến trúc phân lớp một phần, chưa phải Clean Architecture hoàn chỉnh: `domain/`, `di/` và `data/mapper/` đang rỗng; không có Hilt; Screen/ViewModel/Repository chưa tuân theo một đường truy cập dữ liệu duy nhất.

## Đích sau refactor (chưa triển khai)

```mermaid
flowchart TD
    User[Người dùng] --> Screen[Feature Screen]
    Screen --> VM[ViewModel: UiState + UI events]
    VM --> UseCase[Domain UseCase]
    UseCase --> Contract[Domain Repository interface]
    Contract -. implement .-> Impl[Data Repository implementation]
    Impl --> Mapper[DTO mapper]
    Mapper --> Retrofit[Remote API / Firebase adapter]
    Retrofit --> Server[Backend API: PHP hiện tại, Spring Boot nếu chuyển server]
    Server --> DB[(TiDB Cloud)]
    DI[Hilt] -. cung cấp phụ thuộc .-> VM
    DI -. cung cấp phụ thuộc .-> Impl
```

Quy tắc đích: Screen chỉ hiển thị và chuyển thao tác; ViewModel giữ state và gọi use case; domain không phụ thuộc Android/Retrofit/Firebase; data chuyển đổi DTO/domain và thực hiện truy cập nguồn dữ liệu. Backend xác thực và thực thi quy tắc bảo mật/nghiệp vụ; client không được xem là ranh giới bảo mật.

## Liên kết

- Kết quả khảo sát và lộ trình: [REFACTOR_NOTES.md](REFACTOR_NOTES.md)
- Cấu trúc/tổng quan dự án: [README.md](../README.md)
- Luồng nghiệp vụ: [WORKFLOWS.md](WORKFLOWS.md)
