<?php
declare(strict_types=1);
if (!isset($pdo, $uid, $config, $base, $method, $parts, $resource)) { http_response_code(404); exit; }

require_once __DIR__ . '/SearchHistoryService.php';
require_once __DIR__ . '/RecommendationEngine.php';

$started = microtime(true);
try {
    $me = account($pdo, $uid);
    if ($me['status'] !== 'active') {
        out(403, ['error' => 'Tài khoản không hoạt động.']);
    }

    $userId = (int)$me['id'];
    $userHash = hash('sha256', $uid);
    $historyFile = foodhubCacheFile('search-history-' . $userHash . '.json');
    $prefsFile = foodhubCacheFile('rec-prefs-' . $userHash . '.json');
    $cacheFile = foodhubCacheFile('rec-cache-' . $userHash . '.json');

    $sub = strtolower((string)($parts[1] ?? ''));

    // /api/search/history
    if ($resource === 'search' && $sub === 'history' && count($parts) === 2) {
        if ($method === 'POST') {
            $payload = body();
            $query = $payload['query'] ?? null;
            if (!is_string($query)) {
                out(422, ['error' => 'Thiếu trường query hợp lệ.']);
            }
            $res = SearchHistoryService::record($pdo, $userId, $query, $historyFile, $prefsFile, $cacheFile);
            out($res['recorded'] ? 201 : 200, ['success' => true, 'data' => $res]);
        }
        if ($method === 'GET') {
            $limit = isset($_GET['limit']) && is_numeric($_GET['limit']) ? (int)$_GET['limit'] : 30;
            $items = SearchHistoryService::list($pdo, $userId, $limit, $historyFile, $prefsFile);
            out(200, ['success' => true, 'data' => $items]);
        }
        if ($method === 'DELETE') {
            $deleted = SearchHistoryService::clear($pdo, $userId, $historyFile, $cacheFile);
            out(200, ['success' => true, 'deleted' => $deleted]);
        }
        out(405, ['error' => 'Phương thức không được hỗ trợ cho search/history.']);
    }

    // /api/recommendations/personalized
    if ($resource === 'recommendations' && $sub === 'personalized' && count($parts) === 2) {
        if ($method !== 'GET') {
            out(405, ['error' => 'Chỉ hỗ trợ GET cho recommendations/personalized.']);
        }
        $limit = isset($_GET['limit']) && is_numeric($_GET['limit']) ? (int)$_GET['limit'] : 10;
        $result = RecommendationEngine::recommend($pdo, $userId, $limit, $historyFile, $prefsFile, $cacheFile);
        $elapsed = round((microtime(true) - $started) * 1000, 1);
        header('Server-Timing: rec;dur=' . $elapsed);
        out(200, $result);
    }

    // /api/recommendations/preferences
    if ($resource === 'recommendations' && $sub === 'preferences' && count($parts) === 2) {
        if ($method === 'GET') {
            $prefs = SearchHistoryService::getPreferences($pdo, $userId, $prefsFile);
            out(200, ['success' => true, 'data' => $prefs]);
        }
        if ($method === 'PATCH' || $method === 'PUT') {
            $updated = SearchHistoryService::updatePreferences($pdo, $userId, body(), $prefsFile, $cacheFile);
            out(200, ['success' => true, 'data' => $updated]);
        }
        out(405, ['error' => 'Phương thức không được hỗ trợ cho recommendations/preferences.']);
    }

    out(404, ['error' => 'Không tìm thấy endpoint đề xuất.']);
} catch (InvalidArgumentException $e) {
    out(422, ['error' => $e->getMessage()]);
} catch (Throwable $e) {
    error_log('Recommendation route failed: ' . get_class($e));
    out(503, ['error' => 'Dịch vụ đề xuất món ăn tạm thời gián đoạn.']);
}
