<?php
declare(strict_types=1);
if (!isset($pdo, $uid, $config, $base, $method, $parts)) { http_response_code(404); exit; }

require_once __DIR__ . '/MealAdvisor.php';
require_once __DIR__ . '/FoodHubSupport.php';

try {
    $me = account($pdo, $uid);
    if ($me['status'] !== 'active' || !in_array($me['role'], ['customer', 'admin', 'seller', 'driver'], true)) {
        throw new MealAdvisorException(403, 'Tài khoản chưa được phép sử dụng tính năng AI.');
    }

    $userId = (int)$me['id'];
    $accountKey = hash('sha256', $uid);
    $prefsFile = foodhubCacheFile('ai-prefs-' . $accountKey . '.json');
    $mealCacheFile = foodhubCacheFile('meal-result-' . $accountKey . '.json');
    $feedbackFile = foodhubCacheFile('ai-feedback-log.json');
    $metricsFile = foodhubCacheFile('ai-metrics-log.json');

    $sub = strtolower((string)($parts[1] ?? ''));
    $sub2 = (string)($parts[2] ?? '');

    if ($sub === 'preferences') {
        if ($method === 'GET' && $sub2 === '') {
            $profile = UserPersonalization::load($pdo, $userId, $prefsFile);
            out(200, ['data' => $profile]);
        }
        if ($method === 'PUT' && $sub2 === '') {
            $updated = UserPersonalization::updatePreferences($pdo, $userId, body(), $prefsFile, $mealCacheFile);
            out(200, ['data' => $updated]);
        }
        if ($method === 'PUT' && strtolower($sub2) === 'consent') {
            $updated = UserPersonalization::updateConsent($pdo, $userId, body(), $prefsFile, $mealCacheFile);
            out(200, ['data' => $updated]);
        }
        if ($method === 'DELETE' && $sub2 === '') {
            $cleared = UserPersonalization::clear($pdo, $userId, $prefsFile, $mealCacheFile);
            out(200, ['data' => $cleared]);
        }
        if ($method === 'DELETE' && $sub2 !== '') {
            $updated = UserPersonalization::deleteField($pdo, $userId, $sub2, $prefsFile, $mealCacheFile);
            out(200, ['data' => $updated]);
        }
        out(405, ['error' => 'Phương thức không được hỗ trợ cho ai/preferences.']);
    }

    if ($sub === 'feedback' && $method === 'POST' && $sub2 === '') {
        $saved = AiQualityMonitor::submitFeedback($pdo, $userId, body(), FoodHubSystemPrompt::PROMPT_VERSION, $feedbackFile);
        out(201, ['data' => $saved]);
    }

    if ($sub === 'metrics' && $method === 'GET' && $sub2 === '') {
        if ($me['role'] !== 'admin') {
            throw new MealAdvisorException(403, 'Chỉ tài khoản quản trị mới được xem báo cáo chất lượng AI.');
        }
        $days = isset($_GET['days']) && is_numeric($_GET['days']) ? max(1, min(90, (int)$_GET['days'])) : null;
        $since = $days !== null ? time() - ($days * 86400) : null;
        $report = AiQualityMonitor::report($pdo, $metricsFile, $feedbackFile, $since);
        out(200, ['data' => $report]);
    }

    out(404, ['error' => 'Không tìm thấy endpoint AI.']);
} catch (MealAdvisorException $error) {
    out($error->status, ['error' => $error->getMessage()]);
} catch (Throwable $error) {
    error_log('AI personalization/quality route failed: ' . get_class($error));
    out(503, ['error' => 'Dịch vụ tùy chọn AI tạm thời gián đoạn. Vui lòng thử lại sau.']);
}
