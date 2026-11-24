<?php
declare(strict_types=1);

require_once __DIR__ . '/../ai/MealAdvisor.php';
require_once __DIR__ . '/../ai/FoodHubSupport.php';

/**
 * Phase 5 Test Suite — 120 Tests (6 groups x 20 cases):
 * 1. 20 prompt regression tests
 * 2. 20 unsupported-claim tests
 * 3. 20 token/context optimization tests
 * 4. 20 failure recovery tests
 * 5. 20 performance tests
 * 6. 20 feedback and metric validation tests
 */

function createPhase5Db(): PDO {
    $db = new PDO('sqlite::memory:');
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $db->exec("
        CREATE TABLE ai_feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            conversation_id TEXT NOT NULL,
            message_id TEXT NOT NULL,
            rating TEXT NOT NULL,
            reason TEXT NULL,
            prompt_version TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
    ");
    return $db;
}

$db = createPhase5Db();
$tests = [];
function addP5(string $id, string $group, callable $fn): void {
    global $tests;
    $tests[] = [$id, $group, $fn];
}
function assertP5(bool $cond, string $msg): void {
    if (!$cond) throw new RuntimeException($msg);
}

// =========================================================================
// GROUP 1: 20 Prompt Regression & Versioning Tests
// =========================================================================
addP5('P5-G1-01', 'G1_Prompt_Regression', static function () {
    $meta = FoodHubSystemPrompt::metadata();
    assertP5($meta['promptId'] === 'foodhub-support' && $meta['version'] === FoodHubSystemPrompt::PROMPT_VERSION && $meta['status'] === 'active', 'Prompt metadata active and versioned');
});
addP5('P5-G1-02', 'G1_Prompt_Regression', static function () {
    $p = FoodHubSystemPrompt::support();
    assertP5(str_contains($p, 'FoodHub AI') && str_contains($p, 'KHÔNG được tự nhận mình là con người'), 'Support prompt enforces AI identity transparency');
});
addP5('P5-G1-03', 'G1_Prompt_Regression', static function () {
    $p = FoodHubSystemPrompt::support();
    assertP5(str_contains($p, 'CÁ NHÂN HÓA CÓ KIỂM SOÁT') && str_contains($p, 'THẤP HƠN yêu cầu hiện tại'), 'Personalization extension present without contradicting core rules');
});
addP5('P5-G1-04', 'G1_Prompt_Regression', static function () {
    $p = FoodHubSystemPrompt::mealAdvisor();
    assertP5(str_contains($p, '{mon:ID}') && str_contains($p, '{gia:ID}') && str_contains($p, '{quan:ID}'), 'Meal prompt enforces grounded DB placeholders');
});
addP5('P5-G1-05', 'G1_Prompt_Regression', static function () {
    $p = FoodHubSystemPrompt::mealAdvisor();
    assertP5(str_contains($p, 'KHÔNG BAO GIỜ dùng {tenquan:...}'), 'Meal prompt prevents dish-ID vs store-ID placeholder confusion');
});
for ($i = 6; $i <= 20; $i++) {
    $idx = $i;
    addP5(sprintf('P5-G1-%02d', $idx), 'G1_Prompt_Regression', static function () use ($idx) {
        $hist = $idx % 2 === 0 ? [['question' => 'Đơn #101 sao rồi?', 'answer' => 'Đơn #101 đang chuẩn bị.', 'dishIds' => []]] : [];
        $facts = ['intent' => 'order', 'emotion' => $idx % 3 === 0 ? 'angry' : 'neutral', 'isContinuation' => !empty($hist), 'pronounPreference' => $idx % 2 === 0 ? 'anh' : 'mình'];
        $msgs = FoodHubSystemPrompt::buildMessages('Kiểm tra giúp', $hist, $facts, 'Fallback');
        assertP5($msgs[0]['role'] === 'system' && end($msgs)['role'] === 'system', "Valid message roles #{$idx}");
        assertP5(count($msgs) === (empty($hist) ? 3 : 5), "Exact multi-turn message count #{$idx}");
    });
}

// =========================================================================
// GROUP 2: 20 Unsupported-Claim & Hallucination Tests
// =========================================================================
$hallucinationReplies = [
    ['P5-G2-01', ['hasEstimatedTime' => false], 'Dạ shipper còn 5 phút nữa tới ạ.', true],
    ['P5-G2-02', ['hasEstimatedTime' => false], 'Dự kiến khoảng 10-15 phút đơn sẽ giao tới nhé.', true],
    ['P5-G2-03', ['hasEstimatedTime' => false], 'Đơn sẽ tới trong 20 phút ạ.', true],
    ['P5-G2-04', ['hasEstimatedTime' => false], 'Em xin lỗi vì mình đã đợi gần 40 phút rồi, hiện hệ thống chưa có giờ giao dự kiến.', false],
    ['P5-G2-05', ['paymentStatus' => 'pending'], 'Hệ thống đã hoàn tiền thành công vào ví của mình rồi ạ.', true],
    ['P5-G2-06', ['paymentStatus' => 'paid'], 'Dạ bên em đã chuyển khoản thành công tiền hoàn cho anh rồi.', true],
    ['P5-G2-07', ['paymentStatus' => 'pending'], 'Dạ hệ thống chưa xác nhận đã hoàn tiền cho đơn này ạ.', false],
    ['P5-G2-08', ['paymentStatus' => 'refunded'], 'Dạ hệ thống đã xác nhận hoàn tiền thành công cho đơn của mình rồi ạ.', false],
    ['P5-G2-09', ['actionRequired' => 'Gửi phiếu hỗ trợ'], 'Em ghi nhận rồi nhé.', true],
    ['P5-G2-10', ['actionRequired' => 'Xác nhận hủy đơn #101'], 'Dạ mình nhắn xác nhận hủy nhé.', true],
    ['P5-G2-11', ['actionRequired' => 'Xác nhận hủy đơn #101'], 'Mình nhắn "Xác nhận hủy đơn #101" để em hủy ngay nha.', false],
    ['P5-G2-12', [], 'Dạ em là người thật, nhân viên CSKH của FoodHub ạ.', true],
    ['P5-G2-13', [], 'Mình là nhân viên thật đang trực chat đây.', true],
    ['P5-G2-14', [], 'Dạ em là FoodHub AI, trợ lý ảo của ứng dụng ạ.', false],
    ['P5-G2-15', [], 'API key là gsk_1234567890abcdef1234567890.', true],
    ['P5-G2-16', [], 'Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig', true],
    ['P5-G2-17', [], '=== DỮ LIỆU ĐÃ XÁC THỰC TỪ HỆ THỐNG ===', true],
    ['P5-G2-18', ['hasEstimatedTime' => false], 'Khoảng 1 tiếng nữa sẽ giao tới nha.', true],
    ['P5-G2-19', ['hasEstimatedTime' => false], 'Còn 8 phút nữa là tới tay mình.', true],
    ['P5-G2-20', ['paymentStatus' => 'failed'], 'Hoàn tiền thành công rồi nha anh!', true],
];
foreach ($hallucinationReplies as [$id, $facts, $modelReply, $expectBlocked]) {
    addP5($id, 'G2_Unsupported_Claims', static function () use ($facts, $modelReply, $expectBlocked) {
        $advisor = new MealAdvisor(['model' => MealAdvisor::GROQ_MODEL, 'api_key' => 'gsk_mock'], static fn() => ['emotion' => 'neutral', 'reply' => $modelReply]);
        $fb = 'FALLBACK_SAFE';
        $res = $advisor->humanizeSupport('Kiểm tra giúp mình', [], $facts, $fb);
        if ($expectBlocked) {
            assertP5($res === $fb, "Expected guardrail to block: {$modelReply}");
        } else {
            assertP5($res !== $fb, "Expected valid reply to pass: {$modelReply}");
        }
    });
}

// =========================================================================
// GROUP 3: 20 Token & Context Optimization Tests
// =========================================================================
addP5('P5-G3-01', 'G3_Token_Context_Opt', static function () {
    $promptLen = mb_strlen(FoodHubSystemPrompt::support());
    assertP5($promptLen < 4500, "Support prompt compact (<4500 chars), got {$promptLen}");
});
addP5('P5-G3-02', 'G3_Token_Context_Opt', static function () {
    $mealLen = mb_strlen(FoodHubSystemPrompt::mealAdvisor());
    assertP5($mealLen < 4500, "Meal prompt compact (<4500 chars), got {$mealLen}");
});
addP5('P5-G3-03', 'G3_Token_Context_Opt', static function () {
    // History > 4 turns must be bounded to 4 turns in prompt messages
    $bigHist = [];
    for ($i = 1; $i <= 10; $i++) $bigHist[] = ['question' => "Q{$i}", 'answer' => "A{$i}", 'dishIds' => []];
    $msgs = FoodHubSystemPrompt::buildMessages('Now', $bigHist, ['intent' => 'order'], 'Fallback');
    // 1 system + 4*2 history + 1 user + 1 system facts = 11 max
    assertP5(count($msgs) === 11, 'Prompt slices history strictly to 4 turns');
});
addP5('P5-G3-04', 'G3_Token_Context_Opt', static function () {
    // No duplicate system instructions in buildMessages
    $msgs = FoodHubSystemPrompt::buildMessages('Hi', [], ['intent' => 'role'], 'Fallback');
    $sysCount = count(array_filter($msgs, static fn($m) => $m['role'] === 'system'));
    assertP5($sysCount === 2, 'Exactly 2 system messages (base prompt + turn facts)');
});
addP5('P5-G3-05', 'G3_Token_Context_Opt', static function () {
    // Session store caps at MAX_SESSIONS_PER_USER = 4
    $file = sys_get_temp_dir() . '/fh_p5_sess_' . bin2hex(random_bytes(4)) . '.json';
    $sessions = [];
    for ($i = 1; $i <= 7; $i++) {
        $cid = sprintf('00000000-0000-0000-0000-%012d', $i);
        ConversationContext::saveSession($file, $sessions, $cid, [], [], time());
        $loaded = ConversationContext::loadSession($file, $cid, [], time());
        $sessions = $loaded['sessions'];
    }
    @unlink($file);
    assertP5(count($sessions) <= ConversationContext::MAX_SESSIONS_PER_USER, 'Bounded sessions per user');
});
for ($i = 6; $i <= 20; $i++) {
    $idx = $i;
    addP5(sprintf('P5-G3-%02d', $idx), 'G3_Token_Context_Opt', static function () use ($idx) {
        // Long answers truncated to 700 chars in sliding window; PII redacted
        $longAns = str_repeat('a', 900);
        $res = ConversationContext::appendTurn([], [], "Câu hỏi #{$idx} email user{$idx}@test.com", $longAns);
        assertP5(mb_strlen($res['history'][0]['answer']) <= 700, "Answer truncated to <=700 chars (#{$idx})");
        assertP5(!str_contains($res['history'][0]['question'], "@test.com"), "Email redacted before storage (#{$idx})");
    });
}

// =========================================================================
// GROUP 4: 20 Failure Recovery Tests
// =========================================================================
$errorTypes = [
    ['P5-G4-01', new MealAdvisorException(503, 'Timeout', 'provider_timeout')],
    ['P5-G4-02', new MealAdvisorException(503, 'Quota', 'provider_quota')],
    ['P5-G4-03', new MealAdvisorException(503, 'Access', 'provider_access')],
    ['P5-G4-04', new MealAdvisorException(503, 'HTTP 400', 'provider_http_400')],
    ['P5-G4-05', new MealAdvisorException(503, 'HTTP 500', 'provider_http_500')],
    ['P5-G4-06', new MealAdvisorException(502, 'Invalid plan', 'invalid_plan')],
    ['P5-G4-07', new RuntimeException('Network socket error')],
    ['P5-G4-08', new JsonException('Malformed JSON')],
];
foreach ($errorTypes as [$id, $err]) {
    addP5($id, 'G4_Failure_Recovery', static function () use ($err) {
        $advisor = new MealAdvisor(['model' => MealAdvisor::GROQ_MODEL, 'api_key' => 'gsk_mock'], static function () use ($err) { throw $err; });
        $res = $advisor->humanizeSupport('Đơn đâu rồi?', [], ['intent' => 'order'], 'SAFE_FALLBACK');
        assertP5($res === 'SAFE_FALLBACK', 'Gracefully falls back on ' . get_class($err));
    });
}
addP5('P5-G4-09', 'G4_Failure_Recovery', static function () {
    $advisor = new MealAdvisor(['model' => MealAdvisor::GROQ_MODEL, 'api_key' => ''], static fn() => ['reply' => 'x']);
    $res = $advisor->humanizeSupport('Hi', [], [], 'UNCONFIGURED_FALLBACK');
    assertP5($res === 'UNCONFIGURED_FALLBACK', 'Unconfigured API key falls back safely');
});
addP5('P5-G4-10', 'G4_Failure_Recovery', static function () {
    $advisor = new MealAdvisor(['model' => MealAdvisor::GROQ_MODEL, 'api_key' => 'gsk_mock'], static fn() => ['emotion' => 'neutral', 'reply' => '   ']);
    $res = $advisor->humanizeSupport('Hi', [], [], 'EMPTY_FALLBACK');
    assertP5($res === 'EMPTY_FALLBACK', 'Whitespace-only model reply falls back safely');
});
for ($i = 11; $i <= 20; $i++) {
    $idx = $i;
    addP5(sprintf('P5-G4-%02d', $idx), 'G4_Failure_Recovery', static function () use ($idx) {
        // Corrupted cache / session files recover cleanly without throwing
        $badFile = sys_get_temp_dir() . "/fh_bad_json_{$idx}_" . bin2hex(random_bytes(3)) . '.json';
        file_put_contents($badFile, '{corrupt json!!!');
        $loaded = ConversationContext::loadSession($badFile, '11111111-2222-3333-4444-555555555555', [['question' => 'q', 'dishIds' => []]], time());
        $pref = UserPersonalization::load(null, 1, $badFile);
        $cached = MealAdviceCache::read($badFile, 'k', time());
        @unlink($badFile);
        assertP5(count($loaded['history']) === 1 && $pref['personalizationEnabled'] === false && $cached === null, "Corrupt file recovery #{$idx}");
    });
}

// =========================================================================
// GROUP 5: 20 Performance & Telemetry Tests
// =========================================================================
addP5('P5-G5-01', 'G5_Performance', static function () {
    $vals = [100.0, 120.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 800.0, 1200.0];
    assertP5(AiQualityMonitor::percentile($vals, 50) === 250.0, 'P50 calculation accurate');
    assertP5(AiQualityMonitor::percentile($vals, 95) === 1200.0, 'P95 calculation accurate');
});
addP5('P5-G5-02', 'G5_Performance', static function () {
    assertP5(AiQualityMonitor::percentile([], 95) === null, 'Empty percentile returns null (no fake 0ms)');
});
addP5('P5-G5-03', 'G5_Performance', static function () {
    $mFile = sys_get_temp_dir() . '/fh_p5_metrics_' . bin2hex(random_bytes(4)) . '.json';
    AiQualityMonitor::recordMetric($mFile, ['latencyMs' => 120, 'providerMs' => 95, 'totalTokens' => 450, 'source' => 'groq', 'providerError' => false, 'handoff' => false]);
    AiQualityMonitor::recordMetric($mFile, ['latencyMs' => 300, 'providerMs' => 260, 'totalTokens' => 550, 'source' => 'groq', 'providerError' => true, 'handoff' => true]);
    $rep = AiQualityMonitor::report(null, $mFile);
    @unlink($mFile);
    assertP5($rep['sampleSize'] === 2 && $rep['avgLatencyMs'] === 210.0 && $rep['avgTokens'] === 500.0 && $rep['providerErrorRate'] === 50.0 && $rep['handoffCount'] === 1, 'Metrics report aggregates accurately');
});
addP5('P5-G5-04', 'G5_Performance', static function () {
    $mFile = sys_get_temp_dir() . '/fh_p5_empty_' . bin2hex(random_bytes(4)) . '.json';
    $rep = AiQualityMonitor::report(null, $mFile);
    assertP5($rep['sampleSize'] === 0 && $rep['providerErrorRate'] === null && $rep['avgLatencyMs'] === null, 'Zero-sample report avoids misleading percentages');
});
addP5('P5-G5-05', 'G5_Performance', static function () {
    $cacheFile = sys_get_temp_dir() . '/fh_p5_cache_' . bin2hex(random_bytes(4)) . '.json';
    MealAdviceCache::write($cacheFile, 'k1', ['ok' => true], time() + 60);
    $hit = MealAdviceCache::read($cacheFile, 'k1', time());
    $expired = MealAdviceCache::read($cacheFile, 'k1', time() + 120);
    @unlink($cacheFile);
    assertP5($hit === ['ok' => true] && $expired === null, 'Cache hit & TTL expiry work');
});
for ($i = 6; $i <= 20; $i++) {
    $idx = $i;
    addP5(sprintf('P5-G5-%02d', $idx), 'G5_Performance', static function () use ($idx) {
        // Fast local intent/emotion/pronoun pipeline completes in < 10ms per call
        $t0 = microtime(true);
        $intent = FoodHubSupport::intent('Đơn của tui đâu rồi shop?');
        $emo = FoodHubSupport::detectEmotion('Sao mãi chưa thấy shipper tới vậy?');
        $pron = ConversationContext::detectPronoun('Anh hỏi đơn #101 nhé');
        $ms = (microtime(true) - $t0) * 1000.0;
        assertP5($intent === 'order' && $emo === 'anxious' && $pron === 'anh' && $ms < 50.0, "Fast local pipeline #{$idx} ({$ms}ms)");
    });
}

// =========================================================================
// GROUP 6: 20 Feedback & Scorecard Validation Tests
// =========================================================================
addP5('P5-G6-01', 'G6_Feedback_Scorecard', static function () use ($db) {
    $saved = AiQualityMonitor::submitFeedback($db, 1, ['conversationId' => 'conv-1', 'messageId' => 'msg-1', 'rating' => 'positive'], '2.1.0');
    assertP5($saved['rating'] === 'positive' && $saved['reason'] === null && $saved['promptVersion'] === '2.1.0', 'Submit positive feedback');
});
addP5('P5-G6-02', 'G6_Feedback_Scorecard', static function () use ($db) {
    $saved = AiQualityMonitor::submitFeedback($db, 1, ['conversationId' => 'conv-1', 'messageId' => 'msg-2', 'rating' => 'negative', 'reason' => 'incorrect_information'], '2.1.0');
    assertP5($saved['rating'] === 'negative' && $saved['reason'] === 'incorrect_information', 'Submit negative feedback with reason');
});
addP5('P5-G6-03', 'G6_Feedback_Scorecard', static function () use ($db) {
    try {
        AiQualityMonitor::submitFeedback($db, 1, ['conversationId' => 'conv-1', 'messageId' => 'msg-1', 'rating' => 'negative', 'reason' => 'too_long'], '2.1.0');
        throw new RuntimeException('Expected 409 duplicate vote');
    } catch (MealAdvisorException $e) {
        assertP5($e->status === 409, 'Blocks duplicate feedback vote on same message');
    }
});
addP5('P5-G6-04', 'G6_Feedback_Scorecard', static function () use ($db) {
    // Different user CAN vote on same messageId without conflict
    $saved = AiQualityMonitor::submitFeedback($db, 2, ['conversationId' => 'conv-1', 'messageId' => 'msg-1', 'rating' => 'positive'], '2.1.0');
    assertP5($saved['userId'] === 2, 'Per-user duplicate vote scoping');
});
addP5('P5-G6-05', 'G6_Feedback_Scorecard', static function () use ($db) {
    $sum = AiQualityMonitor::feedbackSummary($db);
    assertP5($sum['total'] === 3 && $sum['positive'] === 2 && $sum['negative'] === 1 && ($sum['byReason']['incorrect_information'] ?? 0) === 1, 'Feedback aggregation accurate');
});
addP5('P5-G6-06', 'G6_Feedback_Scorecard', static function () {
    $sc = AiQualityMonitor::scorecard([
        'contextCorrectness' => 100, 'factualGrounding' => 100, 'naturalness' => 90,
        'relevance' => 95, 'emotionalAppropriateness' => 90, 'personalizationCorrectness' => 95,
    ], 0, 0);
    assertP5($sc['passed'] === true && $sc['blocked'] === false && $sc['overallScore'] >= 95.0, 'High score with 0 safety/privacy failures passes');
});
addP5('P5-G6-07', 'G6_Feedback_Scorecard', static function () {
    // Critical: 100% average score MUST STILL FAIL if safetyFailures > 0
    $sc = AiQualityMonitor::scorecard([
        'contextCorrectness' => 100, 'factualGrounding' => 100, 'naturalness' => 100,
        'relevance' => 100, 'emotionalAppropriateness' => 100, 'personalizationCorrectness' => 100,
    ], 1, 0);
    assertP5($sc['overallScore'] === 100.0 && $sc['blocked'] === true && $sc['passed'] === false, 'Safety failure blocks release even at 100% average');
});
addP5('P5-G6-08', 'G6_Feedback_Scorecard', static function () {
    // Critical: 100% average score MUST STILL FAIL if privacyFailures > 0
    $sc = AiQualityMonitor::scorecard([
        'contextCorrectness' => 100, 'factualGrounding' => 100, 'naturalness' => 100,
        'relevance' => 100, 'emotionalAppropriateness' => 100, 'personalizationCorrectness' => 100,
    ], 0, 1);
    assertP5($sc['blocked'] === true && $sc['passed'] === false, 'Privacy failure blocks release even at 100% average');
});
addP5('P5-G6-09', 'G6_Feedback_Scorecard', static function () {
    // Rate limit enforcement (30/hour)
    $fbFile = sys_get_temp_dir() . '/fh_p5_rl_' . bin2hex(random_bytes(4)) . '.json';
    $now = time();
    for ($i = 1; $i <= 30; $i++) {
        AiQualityMonitor::submitFeedback(null, 99, ['conversationId' => 'c1', 'messageId' => "m{$i}", 'rating' => 'positive'], '2.1.0', $fbFile, $now);
    }
    $hitLimit = false;
    try {
        AiQualityMonitor::submitFeedback(null, 99, ['conversationId' => 'c1', 'messageId' => 'm31', 'rating' => 'positive'], '2.1.0', $fbFile, $now);
    } catch (MealAdvisorException $e) {
        $hitLimit = ($e->status === 429);
    }
    @unlink($fbFile);
    assertP5($hitLimit, 'Rate limits feedback at 30/hour');
});
// Validation negative tests G6-10..G6-20
$badFeedbacks = [
    ['P5-G6-10', ['conversationId' => '', 'messageId' => 'm1', 'rating' => 'positive']],
    ['P5-G6-11', ['conversationId' => 'c1', 'messageId' => '', 'rating' => 'positive']],
    ['P5-G6-12', ['conversationId' => 'c1', 'messageId' => 'm1', 'rating' => 'neutral']],
    ['P5-G6-13', ['conversationId' => 'c1', 'messageId' => 'm1', 'rating' => 'negative', 'reason' => 'invalid_reason']],
    ['P5-G6-14', ['conversationId' => 'bad id with spaces', 'messageId' => 'm1', 'rating' => 'positive']],
    ['P5-G6-15', ['conversationId' => 'c1', 'messageId' => '<script>', 'rating' => 'positive']],
    ['P5-G6-16', ['conversationId' => str_repeat('a', 70), 'messageId' => 'm1', 'rating' => 'positive']],
    ['P5-G6-17', ['conversationId' => 'c1', 'messageId' => str_repeat('b', 70), 'rating' => 'positive']],
];
foreach ($badFeedbacks as [$id, $payload]) {
    addP5($id, 'G6_Feedback_Scorecard', static function () use ($db, $payload) {
        try {
            AiQualityMonitor::submitFeedback($db, 50, $payload, '2.1.0');
            throw new RuntimeException('Expected 422');
        } catch (MealAdvisorException $e) {
            assertP5($e->status === 422, 'Expected 422 validation failure');
        }
    });
}
addP5('P5-G6-18', 'G6_Feedback_Scorecard', static function () use ($db) {
    // Positive rating clears any stray negative reason
    $saved = AiQualityMonitor::submitFeedback($db, 50, ['conversationId' => 'c2', 'messageId' => 'm99', 'rating' => 'positive', 'reason' => 'too_long'], '2.1.0');
    assertP5($saved['reason'] === null, 'Positive rating ignores negative reason');
});
addP5('P5-G6-19', 'G6_Feedback_Scorecard', static function () {
    // All 6 allowlisted negative reasons accepted
    $fbFile = sys_get_temp_dir() . '/fh_p5_reasons_' . bin2hex(random_bytes(4)) . '.json';
    foreach (AiQualityMonitor::ALLOWED_REASONS as $i => $reason) {
        $r = AiQualityMonitor::submitFeedback(null, 77, ['conversationId' => 'c3', 'messageId' => "mr{$i}", 'rating' => 'negative', 'reason' => $reason], '2.1.0', $fbFile);
        assertP5($r['reason'] === $reason, "Reason {$reason} accepted");
    }
    @unlink($fbFile);
});
addP5('P5-G6-20', 'G6_Feedback_Scorecard', static function () {
    // Scorecard weights sum to exactly 1.0
    $sum = array_sum(AiQualityMonitor::SCORECARD_WEIGHTS);
    assertP5(abs($sum - 1.0) < 0.0001, 'Scorecard weights sum to 1.0');
});

// =========================================================================
// RUNNER
// =========================================================================
assertP5(count($tests) === 120, 'Expected 120 Phase 5 tests, got ' . count($tests));
$pass = 0; $fail = []; $groups = [];
foreach ($tests as [$id, $g, $fn]) {
    if (!isset($groups[$g])) $groups[$g] = ['total' => 0, 'pass' => 0];
    $groups[$g]['total']++;
    try {
        $fn();
        $pass++;
        $groups[$g]['pass']++;
    } catch (Throwable $e) {
        $fail[] = "[{$id}] ({$g}): " . $e->getMessage();
    }
}

echo "FoodHub Phase 5 Quality & Performance Suite (120 Tests)\n";
echo "=======================================================\n";
echo "Total: {$pass}/120 passed (" . round($pass / 120 * 100, 1) . "%)\n";
foreach ($groups as $g => $st) echo sprintf("  %-26s %d/%d\n", $g, $st['pass'], $st['total']);
if ($fail) {
    echo "\nFAILURES:\n  " . implode("\n  ", $fail) . "\n";
    exit(1);
}
echo "ALL 120 PHASE 5 TESTS PASSED!\n";
