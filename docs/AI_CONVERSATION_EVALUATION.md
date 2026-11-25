# Báo cáo Nâng cấp & Đánh giá FoodHub AI Human-Like Conversation

Cập nhật: **22/11/2026**

## 1. Kiến trúc hệ thống AI sau nâng cấp

Luồng xử lý vẫn giữ nguyên tương thích 100% với Android client (`POST /api/ai/meal-advisor`) và cơ sở dữ liệu TiDB Cloud:

```
User Message (+ conversationId, history)
  -> backenddd/api/index.php (Firebase ID token auth -> MySQL account & role)
  -> backenddd/ai/meal-advisor-route.php
     -> ConversationContext (sliding window 4 lượt + rolling summary + PII redaction)
     -> FoodHubSupport (intent, emotion, dynamic pronoun, brevity, role-scoped SQL)
     -> FoodHubSystemPrompt (official 15-section System Prompt + multi-turn PromptBuilder)
     -> MealAdvisor::humanizeSupport() -> Groq (openai/gpt-oss-120b, temperature=0.6, strict json_schema)
     -> 6 Runtime Guardrails (Identity, Secret leak, ActionRequired, Refund, ETA, Angry emoji strip)
     -> ConversationContext::saveSession() (atomic per-user/role persistence)
```

### Danh sách file đã tạo / chỉnh sửa
- **Tạo mới**:
  - `backenddd/ai/FoodHubSystemPrompt.php`: Tách riêng System Prompt chính thức (FoodHub AI — minh bạch là trợ lý AI, 70% thân thiện / 30% chuyên nghiệp, 15 nguyên tắc + ví dụ phong cách) và `buildMessages()` dựng hội thoại multi-turn.
  - `backenddd/ai/ConversationContext.php`: Quản lý sliding context window, tóm tắt hội thoại cũ (`orderId`, `topic`, `issue`, `pronoun`, `confirmedActions`, `unresolvedQuestion`, `awaitingTicketConfirmation`, `awaitingCancellationConfirmation`), nhận diện xưng hô nhất quán (`anh`/`chị`/`bạn`/`mình`), nhận diện yêu cầu ngắn gọn (`preferBrief`), và ẩn dữ liệu nhạy cảm (OTP, mật khẩu, email, SĐT, số thẻ).
  - `backenddd/tests/foodhub_conversation_120_test.php`: Bộ kiểm thử tự động 120 trường hợp (6 nhóm × 20 tình huống) qua SQLite in-memory + pipeline `FoodHubSupport` / `ConversationContext` / `FoodHubSystemPrompt` / `MealAdvisor`.
- **Chỉnh sửa**:
  - `backenddd/ai/MealAdvisor.php`: Rút gọn `humanizeSupport()` gọi `FoodHubSystemPrompt::buildMessages()`, sửa 2 lỗi false-positive ở guardrail ETA (cho phép nhắc lại thời gian khách đã đợi, vẫn chặn bịa ETA tương lai) và guardrail hoàn tiền (cho phép câu phủ định trung thực "chưa xác nhận đã hoàn tiền"), bổ sung guardrail chống tự nhận người thật, chống lộ key/prompt, lọc emoji vui khi khách tức giận, thêm `temperature: 0.6`.
  - `backenddd/ai/FoodHubSupport.php`: Mở rộng `normalize()` (tiếng lóng, viết tắt `dc`, `mk/mik`, `tks/thx`, `ib`, `rep/trl`, `lun`, `dzay`), mở rộng `intent()` và `detectEmotion()` (8 trạng thái, sửa lỗi `hài long` có dấu trong chuỗi folded), sửa bug `(bool)preg_match` cho `$accountIncident`, cho `identity` đi qua `dispatch($humanize, ...)`, bổ sung ngữ cảnh đa lượt ("ừ đúng rồi", "vậy còn bao lâu", "thế hủy đi", "nếu lâu quá thì sao") và đọc `orderId` từ `summary`.
  - `backenddd/ai/meal-advisor-route.php`: Tích hợp `ConversationContext`, giữ lịch sử client khi phiên mới chưa có trên server, giải phóng `$memoryLock` trong `finally`.
  - `backenddd/deploy/deploy-meal-advisor.py`: Bổ sung `ai/FoodHubSystemPrompt.php` và `ai/ConversationContext.php` vào danh sách triển khai.

## 2. Kết quả kiểm thử (Trước vs Sau khi tinh chỉnh)

Lệnh chạy:
```powershell
C:\php82\php.exe -d extension_dir=C:\php82\ext -d extension=mbstring -d extension=pdo_sqlite backenddd/tests/foodhub_conversation_120_test.php
```

### So sánh kết quả 120 trường hợp

| Nhóm kiểm thử | Lần chạy đầu (Baseline) | Sau khi sửa lỗi & tinh chỉnh |
|---|---:|---:|
| G1 — Chào hỏi & trò chuyện tự nhiên | 14/20 | **20/20** |
| G2 — Tiếng lóng, viết tắt & không dấu | 15/20 | **20/20** |
| G3 — Nhận biết cảm xúc | 19/20 | **20/20** |
| G4 — Hội thoại nhiều lượt | 20/20 | **20/20** |
| G5 — Khách tức giận / khó tính | 15/20 | **20/20** |
| G6 — Thiếu dữ liệu, mâu thuẫn & giới hạn quyền | 16/20 | **20/20** |
| **Tổng cộng** | **99/120 (82.5%)** | **120/120 (100%)** |

### Kết quả theo 8 tiêu chí đánh giá (sau nâng cấp)

| Tiêu chí | Đạt / Tổng |
|---|---:|
| Naturalness | 60/60 |
| Empathy | 47/47 |
| Context awareness | 19/19 |
| Pronoun consistency | 8/8 |
| Relevance | 21/21 |
| Conciseness | 10/10 |
| Factual correctness | 34/34 |
| Safety | 48/48 |

### Toàn bộ các bộ kiểm thử hồi quy PHP khác
- `foodhub_support_test.php`: PASS
- `foodhub_human_ai_test.php`: 6/6 PASS
- `foodhub_conversation_style_test.php`: PASS
- `meal_chat_test.php`: 26 checks + Groq validation PASS
- `meal_advisor_fast_test.php`: 61 checks PASS
- `menu_knowledge_test.php`: 27 checks PASS
- `php -l` trên toàn bộ 5 file AI: Không có lỗi cú pháp.

## 3. Giới hạn hiện tại & Hướng phát triển tiếp theo

- **Phạm vi kiểm thử**: Bộ 120 tình huống kiểm thử đầy đủ pipeline nghiệp vụ, intent, emotion, pronoun, context summary và 6 lớp guardrail bằng mock model transport (không tiêu tốn quota Groq và có tính tái lập 100%). Chưa chạy live evaluation với API Groq thật cho toàn bộ 120 câu trong môi trường sandbox (không có mạng).
- **Hướng phát triển tiếp theo**:
  1. Bổ sung script đánh giá định kỳ 20–30 mẫu ngẫu nhiên trực tiếp trên Groq (`openai/gpt-oss-120b`) để chấm điểm định tính độ tự nhiên và độ trễ thực tế.
  2. Khi hệ thống bổ sung trường `estimated_delivery_at` từ quán/shipper, kết nối trực tiếp vào `facts.hasEstimatedTime` và `facts.estimatedMinutes` để AI thông báo ETA thật.
  3. Theo dõi các phản hồi "Chưa giải quyết" trong hộp thư hỗ trợ để định kỳ cập nhật từ điển tiếng lóng và kịch bản mới (có nhân viên kiểm duyệt trước khi áp dụng).
