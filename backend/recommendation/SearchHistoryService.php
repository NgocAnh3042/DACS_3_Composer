<?php
declare(strict_types=1);

/**
 * Manages authenticated user food search history and search personalization preferences.
 * Supports MySQL/TiDB & SQLite via PDO, with private per-user JSON fallback when migration is not yet applied.
 */
final class SearchHistoryService {
    public const DEFAULT_DEDUP_SECONDS = 60;
    public const DEFAULT_RETENTION_DAYS = 90;
    public const MIN_RETENTION_DAYS = 7;
    public const MAX_RETENTION_DAYS = 365;
    public const MAX_QUERY_LENGTH = 120;
    public const MAX_HISTORY_ITEMS = 200;

    /** UTF-8 safe Vietnamese normalization: trim, lowercase, remove diacritics, collapse spaces. */
    public static function normalizeQuery(string $query): string {
        $text = trim($query);
        if ($text === '') return '';
        $text = mb_strtolower($text, 'UTF-8');
        $map = [
            'àáạảãâầấậẩẫăằắặẳẵ' => 'a',
            'èéẹẻẽêềếệểễ' => 'e',
            'ìíịỉĩ' => 'i',
            'òóọỏõôồốộổỗơờớợởỡ' => 'o',
            'ùúụủũưừứựửữ' => 'u',
            'ỳýỵỷỹ' => 'y',
            'đ' => 'd',
        ];
        foreach ($map as $chars => $ascii) {
            $letters = preg_split('//u', $chars, -1, PREG_SPLIT_NO_EMPTY);
            if ($letters !== false) {
                $text = str_replace($letters, $ascii, $text);
            }
        }
        $text = (string)preg_replace('/[^\p{L}\p{N}\s]+/u', ' ', $text);
        return trim((string)preg_replace('/\s+/u', ' ', $text));
    }

    /** Reject obvious credentials, tokens, emails, phone numbers, or card-like strings. */
    public static function isSensitiveQuery(string $query): bool {
        $folded = self::normalizeQuery($query);
        if (preg_match('/\b(otp|password|mat khau|so the|cvv|cccd|cmnd|token|bearer)\b/u', $folded)) {
            return true;
        }
        if (preg_match('/gsk_[a-zA-Z0-9]{10,}/', $query)) {
            return true;
        }
        if (preg_match('/[\w.+-]+@[\w.-]+\.[a-z]{2,}/i', $query)) {
            return true;
        }
        if (preg_match('/(?<!\d)(?:\+?\d[ .-]?){9,16}(?!\d)/', $query)) {
            return true;
        }
        return false;
    }

    public static function getPreferences(?PDO $db, int $userId, ?string $prefsFile = null): array {
        $default = [
            'userId' => $userId,
            'searchPersonalizationEnabled' => true,
            'retentionDays' => self::DEFAULT_RETENTION_DAYS,
            'updatedAt' => null,
        ];
        if ($db !== null && self::hasTable($db, 'user_recommendation_preferences')) {
            $stmt = $db->prepare('SELECT search_personalization_enabled, retention_days, updated_at FROM user_recommendation_preferences WHERE user_id = ? LIMIT 1');
            $stmt->execute([$userId]);
            $row = $stmt->fetch(PDO::FETCH_ASSOC);
            if (is_array($row)) {
                return [
                    'userId' => $userId,
                    'searchPersonalizationEnabled' => (bool)$row['search_personalization_enabled'],
                    'retentionDays' => max(self::MIN_RETENTION_DAYS, min(self::MAX_RETENTION_DAYS, (int)$row['retention_days'])),
                    'updatedAt' => $row['updated_at'] ?? null,
                ];
            }
            return $default;
        }
        if ($prefsFile !== null && is_file($prefsFile)) {
            $decoded = json_decode((string)@file_get_contents($prefsFile), true);
            if (is_array($decoded) && (int)($decoded['userId'] ?? -1) === $userId) {
                return [
                    'userId' => $userId,
                    'searchPersonalizationEnabled' => (bool)($decoded['searchPersonalizationEnabled'] ?? true),
                    'retentionDays' => max(self::MIN_RETENTION_DAYS, min(self::MAX_RETENTION_DAYS, (int)($decoded['retentionDays'] ?? self::DEFAULT_RETENTION_DAYS))),
                    'updatedAt' => $decoded['updatedAt'] ?? null,
                ];
            }
        }
        return $default;
    }

    public static function updatePreferences(
        ?PDO $db,
        int $userId,
        array $patch,
        ?string $prefsFile = null,
        ?string $recCacheFile = null
    ): array {
        $prefs = self::getPreferences($db, $userId, $prefsFile);
        if (array_key_exists('searchPersonalizationEnabled', $patch)) {
            if (!is_bool($patch['searchPersonalizationEnabled'])) {
                throw new InvalidArgumentException('searchPersonalizationEnabled phải là kiểu boolean.');
            }
            $prefs['searchPersonalizationEnabled'] = $patch['searchPersonalizationEnabled'];
        }
        if (array_key_exists('retentionDays', $patch)) {
            if (!is_int($patch['retentionDays']) || $patch['retentionDays'] < self::MIN_RETENTION_DAYS || $patch['retentionDays'] > self::MAX_RETENTION_DAYS) {
                throw new InvalidArgumentException('retentionDays phải từ ' . self::MIN_RETENTION_DAYS . ' đến ' . self::MAX_RETENTION_DAYS . ' ngày.');
            }
            $prefs['retentionDays'] = $patch['retentionDays'];
        }
        $nowSql = gmdate('Y-m-d H:i:s');
        $prefs['updatedAt'] = gmdate('Y-m-d\TH:i:s\Z');

        if ($db !== null && self::hasTable($db, 'user_recommendation_preferences')) {
            $driver = (string)$db->getAttribute(PDO::ATTR_DRIVER_NAME);
            if ($driver === 'sqlite') {
                $db->prepare('INSERT OR REPLACE INTO user_recommendation_preferences (user_id, search_personalization_enabled, retention_days, updated_at) VALUES (?,?,?,?)')
                    ->execute([$userId, $prefs['searchPersonalizationEnabled'] ? 1 : 0, $prefs['retentionDays'], $nowSql]);
            } else {
                $db->prepare('INSERT INTO user_recommendation_preferences (user_id, search_personalization_enabled, retention_days, updated_at) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE search_personalization_enabled=VALUES(search_personalization_enabled), retention_days=VALUES(retention_days), updated_at=VALUES(updated_at)')
                    ->execute([$userId, $prefs['searchPersonalizationEnabled'] ? 1 : 0, $prefs['retentionDays'], $nowSql]);
            }
        } elseif ($prefsFile !== null) {
            self::writeJson($prefsFile, $prefs);
        }

        self::invalidateCache($recCacheFile);
        return $prefs;
    }

    /**
     * Record a confirmed search query for the authenticated user.
     *
     * @return array{recorded: bool, reason: string, normalizedQuery?: string, id?: string}
     */
    public static function record(
        ?PDO $db,
        int $userId,
        string $rawQuery,
        ?string $historyFile = null,
        ?string $prefsFile = null,
        ?string $recCacheFile = null,
        int $dedupSeconds = self::DEFAULT_DEDUP_SECONDS,
        ?int $now = null
    ): array {
        $now ??= time();
        $cleanQuery = trim((string)preg_replace('/\s+/u', ' ', $rawQuery));
        if ($cleanQuery === '' || mb_strlen($cleanQuery, 'UTF-8') < 2) {
            throw new InvalidArgumentException('Từ khóa tìm kiếm phải có ít nhất 2 ký tự.');
        }
        if (mb_strlen($cleanQuery, 'UTF-8') > self::MAX_QUERY_LENGTH) {
            throw new InvalidArgumentException('Từ khóa tìm kiếm tối đa ' . self::MAX_QUERY_LENGTH . ' ký tự.');
        }
        if (self::isSensitiveQuery($cleanQuery)) {
            return ['recorded' => false, 'reason' => 'sensitive_query_skipped'];
        }

        $normalized = self::normalizeQuery($cleanQuery);
        if ($normalized === '' || mb_strlen($normalized, 'UTF-8') < 2) {
            return ['recorded' => false, 'reason' => 'empty_normalized_query'];
        }

        $prefs = self::getPreferences($db, $userId, $prefsFile);
        if (!$prefs['searchPersonalizationEnabled']) {
            return ['recorded' => false, 'reason' => 'personalization_disabled'];
        }

        $cutoffSql = gmdate('Y-m-d H:i:s', $now - max(0, $dedupSeconds));
        $nowSql = gmdate('Y-m-d H:i:s', $now);

        if ($db !== null && self::hasTable($db, 'user_search_history')) {
            if ($dedupSeconds > 0) {
                $dup = $db->prepare('SELECT id FROM user_search_history WHERE user_id = ? AND normalized_query = ? AND searched_at >= ? LIMIT 1');
                $dup->execute([$userId, $normalized, $cutoffSql]);
                if ($dup->fetchColumn()) {
                    return ['recorded' => false, 'reason' => 'deduplicated', 'normalizedQuery' => $normalized];
                }
            }
            $ins = $db->prepare('INSERT INTO user_search_history (user_id, search_query, normalized_query, searched_at) VALUES (?,?,?,?)');
            $ins->execute([$userId, $cleanQuery, $normalized, $nowSql]);
            $id = (string)$db->lastInsertId();
            self::invalidateCache($recCacheFile);
            return ['recorded' => true, 'reason' => 'saved', 'id' => $id, 'normalizedQuery' => $normalized];
        }

        if ($historyFile !== null) {
            $rows = self::readJsonList($historyFile);
            if ($dedupSeconds > 0) {
                foreach (array_reverse($rows) as $existing) {
                    if ((int)($existing['userId'] ?? 0) === $userId
                        && ($existing['normalizedQuery'] ?? '') === $normalized
                        && (int)($existing['ts'] ?? 0) >= ($now - $dedupSeconds)) {
                        return ['recorded' => false, 'reason' => 'deduplicated', 'normalizedQuery' => $normalized];
                    }
                }
            }
            $id = 'sh_' . bin2hex(random_bytes(6));
            $rows[] = [
                'id' => $id,
                'userId' => $userId,
                'searchQuery' => $cleanQuery,
                'normalizedQuery' => $normalized,
                'searchedAt' => $nowSql,
                'ts' => $now,
            ];
            self::writeJson($historyFile, array_slice($rows, -self::MAX_HISTORY_ITEMS));
            self::invalidateCache($recCacheFile);
            return ['recorded' => true, 'reason' => 'saved', 'id' => $id, 'normalizedQuery' => $normalized];
        }

        return ['recorded' => false, 'reason' => 'no_storage'];
    }

    /** Return recent search history strictly scoped to $userId within retention window. */
    public static function list(
        ?PDO $db,
        int $userId,
        int $limit = 30,
        ?string $historyFile = null,
        ?string $prefsFile = null,
        ?int $now = null
    ): array {
        $now ??= time();
        $limit = max(1, min(100, $limit));
        $prefs = self::getPreferences($db, $userId, $prefsFile);
        $cutoffTs = $now - ($prefs['retentionDays'] * 86400);
        $cutoffSql = gmdate('Y-m-d H:i:s', $cutoffTs);

        if ($db !== null && self::hasTable($db, 'user_search_history')) {
            $stmt = $db->prepare('SELECT id, search_query, normalized_query, searched_at FROM user_search_history WHERE user_id = ? AND searched_at >= ? ORDER BY searched_at DESC, id DESC LIMIT ' . $limit);
            $stmt->execute([$userId, $cutoffSql]);
            $out = [];
            foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $r) {
                $out[] = [
                    'id' => (string)$r['id'],
                    'query' => (string)$r['search_query'],
                    'normalizedQuery' => (string)$r['normalized_query'],
                    'searchedAt' => (string)$r['searched_at'],
                ];
            }
            return $out;
        }

        if ($historyFile !== null && is_file($historyFile)) {
            $rows = self::readJsonList($historyFile);
            $filtered = array_values(array_filter($rows, static fn($r) => (int)($r['userId'] ?? 0) === $userId && (int)($r['ts'] ?? 0) >= $cutoffTs));
            usort($filtered, static fn($a, $b) => ((int)($b['ts'] ?? 0)) <=> ((int)($a['ts'] ?? 0)));
            $out = [];
            foreach (array_slice($filtered, 0, $limit) as $r) {
                $out[] = [
                    'id' => (string)($r['id'] ?? ''),
                    'query' => (string)($r['searchQuery'] ?? ''),
                    'normalizedQuery' => (string)($r['normalizedQuery'] ?? ''),
                    'searchedAt' => (string)($r['searchedAt'] ?? ''),
                ];
            }
            return $out;
        }

        return [];
    }

    public static function clear(?PDO $db, int $userId, ?string $historyFile = null, ?string $recCacheFile = null): int {
        $deleted = 0;
        if ($db !== null && self::hasTable($db, 'user_search_history')) {
            $stmt = $db->prepare('DELETE FROM user_search_history WHERE user_id = ?');
            $stmt->execute([$userId]);
            $deleted = $stmt->rowCount();
        }
        if ($historyFile !== null && is_file($historyFile)) {
            $rows = self::readJsonList($historyFile);
            $kept = array_values(array_filter($rows, static fn($r) => (int)($r['userId'] ?? 0) !== $userId));
            $deleted += max(0, count($rows) - count($kept));
            self::writeJson($historyFile, $kept);
        }
        self::invalidateCache($recCacheFile);
        return $deleted;
    }

    public static function invalidateCache(?string $recCacheFile): void {
        if ($recCacheFile !== null && is_file($recCacheFile)) {
            @unlink($recCacheFile);
        }
    }

    private static function hasTable(PDO $db, string $table): bool {
        try {
            $driver = (string)$db->getAttribute(PDO::ATTR_DRIVER_NAME);
            if ($driver === 'sqlite') {
                $stmt = $db->prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?");
                $stmt->execute([$table]);
                return (bool)$stmt->fetchColumn();
            }
            $stmt = $db->prepare('SHOW TABLES LIKE ?');
            $stmt->execute([$table]);
            return (bool)$stmt->fetchColumn();
        } catch (Throwable) {
            return false;
        }
    }

    private static function readJsonList(string $file): array {
        if (!is_file($file)) return [];
        $decoded = json_decode((string)@file_get_contents($file), true);
        return is_array($decoded) ? $decoded : [];
    }

    private static function writeJson(string $file, array $data): void {
        $tmp = $file . '.tmp-' . bin2hex(random_bytes(4));
        try {
            $encoded = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            if (@file_put_contents($tmp, $encoded, LOCK_EX) !== false) {
                @chmod($tmp, 0600);
                @rename($tmp, $file);
            }
        } finally {
            if (is_file($tmp)) @unlink($tmp);
        }
    }
}
