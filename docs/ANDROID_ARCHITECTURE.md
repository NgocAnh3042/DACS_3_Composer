# Kiến trúc Android — trạng thái khảo sát

Cập nhật: 26/11/2026. Giai đoạn 1 network, Giai đoạn 2 domain, Giai đoạn 3 DTO/error và Giai đoạn 4 Hilt cho luồng lịch sử tìm kiếm đã qua build/unit test; các luồng khác bên dưới vẫn chưa chuyển.

## Hiện trạng

```mermaid
flowchart TD
    User[Người dùng] --> Screen[Jetpack Compose Screen]
    Screen --> VM[ViewModel / StateFlow]
    VM --> Repo[Repository, nếu feature dùng]
    VM -. một số nơi gọi trực tiếp .-> Client[FoodHubClient]
    Screen -. một số call site cũ gọi trực tiếp .-> Client
    Repo --> Client
    VM --> UC[RecordSearchQueryUseCase: lịch sử tìm kiếm]
    UC --> Contract[SearchHistoryRepository interface]
    Contract -. implement .-> Impl[RecommendationRepository]
    Impl --> Client
    Client --> Network[FoodHubClient + RemoteNetworkFactory]
    Network --> Groups[AuthApi / OrderApi / RestaurantApi; FoodHubApi cũ vẫn giữ]
    Groups -->|Firebase ID token + URL theo flavor| PHP[PHP API: Alwaysdata hoặc local qua ngrok]
    PHP --> Groups
    Groups --> Mapping[SearchHistory DTO + mapper/ErrorMapper]
    Mapping --> Impl
    PHP --> DB[(TiDB Cloud)]
    VM --> Firebase[Firebase Auth / Realtime Database / FCM]
    Repo --> External[Cloudinary / API dịch vụ ngoài]
```

Hiện trạng là kiến trúc phân lớp một phần, chưa phải Clean Architecture hoàn chỉnh: domain hiện có `SearchQuery`, `SearchHistoryRepository` và `RecordSearchQueryUseCase` cho một luồng; `di/` cung cấp Hilt cho lát tìm kiếm này, còn Screen/ViewModel/Repository chưa tuân theo một đường truy cập dữ liệu duy nhất.

Network đã có một điểm tạo Retrofit/OkHttp chung. Logger an toàn chỉ bật ở debug. Manifest chính chặn cleartext; source set debug có overlay cho phép cleartext nếu backend local dùng HTTP. Các API service nhóm đã được khai báo, còn `FoodHubApi` tổng quát được giữ để chuyển caller dần mà không đổi hành vi. DTO/mapper/ErrorMapper hiện được dùng cho riêng luồng lịch sử tìm kiếm; các API cũ chưa được chuyển. Hilt tạo `SearchViewModel` và cấp use case/repository; Retrofit vẫn được lấy từ `FoodHubClient` singleton trong giai đoạn chuyển tiếp.

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
    DI[Hilt] -. tạo SearchViewModel .-> VM
    DI -. cung cấp use case / repository .-> UC
    DI -. bind SearchHistoryRepository .-> Impl
    DI -. cung cấp FirebaseAuth / SearchHistoryApi .-> Impl
```

Quy tắc đích: Screen chỉ hiển thị và chuyển thao tác; ViewModel giữ state và gọi use case; domain không phụ thuộc Android/Retrofit/Firebase; data chuyển đổi DTO/domain và thực hiện truy cập nguồn dữ liệu. Backend xác thực và thực thi quy tắc bảo mật/nghiệp vụ; client không được xem là ranh giới bảo mật.

## Liên kết

- Kết quả khảo sát và lộ trình: [REFACTOR_NOTES.md](REFACTOR_NOTES.md)
- Cấu trúc/tổng quan dự án: [README.md](../README.md)
- Luồng nghiệp vụ: [WORKFLOWS.md](WORKFLOWS.md)
