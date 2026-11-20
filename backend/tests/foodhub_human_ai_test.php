<?php
declare(strict_types=1);
require_once __DIR__.'/../ai/MealAdvisor.php';
require_once __DIR__.'/../ai/FoodHubSupport.php';

function check(bool $condition, string $msg): void {
    if (!$condition) throw new RuntimeException("Assertion failed: $msg");
}

echo "Running FoodHub Human AI Support Tests...\n";

// 1. Test emotion detection
check(FoodHubSupport::detectEmotion('Tôi đợi gần một tiếng rồi, làm ăn kiểu gì vậy?') === 'angry', 'detect angry emotion');
check(FoodHubSupport::detectEmotion('Sao mãi chưa thấy shipper tới vậy shop?') === 'anxious', 'detect anxious emotion');
check(FoodHubSupport::detectEmotion('Đồ ăn giao tới bị đổ hết rồi!') === 'frustrated', 'detect frustrated emotion');
check(FoodHubSupport::detectEmotion('Giao nhanh giúp mình gấp nhé') === 'urgent', 'detect urgent emotion');
check(FoodHubSupport::detectEmotion('Cảm ơn shop nhiều, món ngon lắm') === 'satisfied', 'detect satisfied emotion');
check(FoodHubSupport::detectEmotion('Cho mình xem thực đơn') === 'neutral', 'detect neutral emotion');
echo "PASS: Emotion detection heuristics\n";

// 2. Test Groq humanizeSupport mock
$mockGroqResponses = [];
$advisor = new MealAdvisor(['model' => MealAdvisor::GROQ_MODEL, 'api_key' => 'gsk_mock_key'], function(array $payload) use (&$mockGroqResponses) {
    $msg = $payload['messages'][1]['content'];
    $data = json_decode($msg, true);
    $intent = $data['verifiedFacts']['intent'] ?? '';

    if (isset($mockGroqResponses[$intent])) {
        return $mockGroqResponses[$intent];
    }
    return [
        'emotion' => 'neutral',
        'reply' => 'Dạ em chào anh/chị, em có thể hỗ trợ gì cho mình ạ?'
    ];
});

// Test Factor 1 & 2: Natural Vietnamese + Order lookup without ETA hallucination
$mockGroqResponses['order'] = [
    'emotion' => 'anxious',
    'reply' => 'Dạ em chào anh/chị! Em kiểm tra thấy đơn #101 tại Quán thử đang được chuẩn bị. Shipper phụ trách là anh D. Hiện hệ thống chưa có thời gian giao dự kiến chính xác, em sẽ theo dõi sát và cập nhật ngay khi quán giao đồ nhé!'
];
$orderFacts = [
    'intent' => 'order',
    'hasEstimatedTime' => false,
    'order' => ['id' => 101, 'storeName' => 'Quán thử', 'statusText' => 'đang chờ quán xác nhận', 'shipperName' => 'D']
];
$reply = $advisor->humanizeSupport('Shop ơi, đơn tui đặt hồi nãy sao chưa thấy giao vậy?', [], $orderFacts, 'Fallback text');
check(str_contains($reply, 'đơn #101') && str_contains($reply, 'Quán thử') && str_contains($reply, 'chưa có thời gian giao dự kiến'), 'humanized order reply');
echo "PASS: Humanized order inquiry without ETA hallucination\n";

// Test Anti-hallucination Guardrail: Fake ETA should be blocked
$mockGroqResponses['order_fake_eta'] = [
    'emotion' => 'neutral',
    'reply' => 'Dạ đơn của bạn sẽ tới trong 15 phút nữa nhé!'
];
$fakeEtaFacts = ['intent' => 'order_fake_eta', 'hasEstimatedTime' => false];
$blockedReply = $advisor->humanizeSupport('Bao lâu nữa tới?', [], $fakeEtaFacts, 'Hệ thống chưa có giờ giao dự kiến.');
check($blockedReply === 'Hệ thống chưa có giờ giao dự kiến.', 'blocked fake ETA hallucination');
echo "PASS: Guardrail blocks fake ETA hallucination\n";

// Test Factor 5: Refund Guardrail - Do not claim refund success unless confirmed
$mockGroqResponses['refund_unconfirmed'] = [
    'emotion' => 'neutral',
    'reply' => 'Dạ em đã hoàn tiền thành công vào ví của anh rồi ạ!'
];
$unconfirmedFacts = ['intent' => 'refund_unconfirmed', 'paymentStatus' => 'pending'];
$refundFallback = 'Mình chưa thấy giao dịch được xác nhận hoàn tiền. Nhắn “Gửi phiếu hỗ trợ” để nhân viên kiểm tra.';
$safeReply = $advisor->humanizeSupport('Hoàn tiền cho tôi chưa?', [], $unconfirmedFacts, $refundFallback);
check($safeReply === $refundFallback, 'blocked unconfirmed refund claim');
echo "PASS: Guardrail blocks false refund claims\n";

// Test Factor 4 & 9: Incident resolution & Action required preservation
$mockGroqResponses['incident'] = [
    'emotion' => 'frustrated',
    'reply' => 'Dạ em thật sự xin lỗi mình vì sự cố đồ ăn bị đổ ạ! Anh/chị chụp ảnh lại phần bị đổ giúp em, rồi nhắn "Gửi phiếu hỗ trợ" để em chuyển ngay cho Admin giải quyết nhé!'
];
$incidentFacts = ['intent' => 'incident', 'actionRequired' => 'Gửi phiếu hỗ trợ'];
$incidentReply = $advisor->humanizeSupport('Đồ ăn giao tới bị đổ hết rồi!', [], $incidentFacts, 'Fallback incident');
check(str_contains($incidentReply, 'Gửi phiếu hỗ trợ') && str_contains($incidentReply, 'xin lỗi'), 'incident action preserved');
echo "PASS: Incident response preserves actionRequired trigger\n";

// Test missing actionRequired should be blocked by guardrail
$mockGroqResponses['incident_missing_action'] = [
    'emotion' => 'frustrated',
    'reply' => 'Dạ em xin lỗi nhé, đồ ăn bị đổ thì xui quá.'
];
$blockedIncident = $advisor->humanizeSupport('Đồ ăn bị đổ!', [], ['actionRequired' => 'Gửi phiếu hỗ trợ'], 'Fallback prompt');
check($blockedIncident === 'Fallback prompt', 'guardrail enforces actionRequired presence');
echo "PASS: Guardrail enforces presence of required action commands\n";

echo "ALL FoodHub Human AI Support Tests PASSED successfully!\n";
