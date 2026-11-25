<?php
declare(strict_types=1);

/**
 * Phase 5: Feedback Collection, Token & Latency Telemetry,
 * Quality Scorecard, and Admin Quality Monitoring.
 */
final class AiQualityMonitor {
    public const ALLOWED_RATINGS = ['positive', 'negative'];
    public const ALLOWED_REASONS = [
        'did_not_understand',
        'incorrect_information',
        'too_long',
        'unnatural_response',
        'did_not_solve',
        'other',
    ];
    public const MAX_EVENTS = 500;
    public const MAX_FEEDBACK_PER_HOUR = 30;

    public const SCORECARD_WEIGHTS = [
        'contextCorrectness' => 0.25,
        'factualGrounding' => 0.25,
        'naturalness' => 0.15,
        'relevance' => 0.15,
        'emotionalAppropriateness' => 0.10,
        'personalizationCorrectness' => 0.10,
    ];

    /**
     * Validate and persist user feedback for an AI message.
     * Enforces ownership, allowlisted reasons, rate-limiting, and one vote per message.
     */
    public static function submitFeedback(
        ?PDO $db,
        int $userId,
        array $input,
        string $promptVersion,
        ?string $fallbackFile = null,
        ?int $now = null
    ): array {
        $now ??= time();
        $conversationId = trim((string)($input['conversationId'] ?? ''));
        $messageId = trim((string)($input['messageId'] ?? ''));
        $rating = trim((string)($input['rating'] ?? ''));
        $reason = isset($input['reason']) && $input['reason'] !== null ? trim((string)$input['reason']) : null;

        if ($conversationId === '' || mb_strlen($conversationId) > 64 || !preg_match('/^[a-zA-Z0-9._-]+$/D', $conversationId)) {
            throw new MealAdvisorException(422, 'Mã hội thoại không hợp lệ.');
        }
        if ($messageId === '' || mb_strlen($messageId) > 64 || !preg_match('/^[a-zA-Z0-9._-]+$/D', $messageId)) {
            throw new MealAdvisorException(422, 'Mã tin nhắn không hợp lệ.');
        }
        if (!in_array($rating, self::ALLOWED_RATINGS, true)) {
            throw new MealAdvisorException(422, 'Đánh giá chỉ nhận positive hoặc negative.');
        }
        if ($reason !== null && $reason !== '') {
            if (!in_array($reason, self::ALLOWED_REASONS, true)) {
                throw new MealAdvisorException(422, 'Lý do phản hồi không hợp lệ.');
            }
        } else {
            $reason = null;
        }
        if ($rating === 'positive') {
            $reason = null;
        }

        $record = [
            'userId' => $userId,
            'conversationId' => $conversationId,
            'messageId' => $messageId,
            'rating' => $rating,
            'reason' => $reason,
            'promptVersion' => mb_substr($promptVersion, 0, 32),
            'createdAt' => gmdate('Y-m-d\TH:i:s\Z', $now),
            'ts' => $now,
        ];

        if ($db !== null && self::hasFeedbackTable($db)) {
            $cutoff = gmdate('Y-m-d H:i:s', $now - 3600);
            $cntStmt = $db->prepare('SELECT COUNT(*) FROM ai_feedback WHERE user_id = ? AND created_at >= ?');
            $cntStmt->execute([$userId, $cutoff]);
            if ((int)$cntStmt->fetchColumn() >= self::MAX_FEEDBACK_PER_HOUR) {
                throw new MealAdvisorException(429, 'Bạn đã gửi quá nhiều phản hồi trong giờ qua. Vui lòng thử lại sau.');
            }

            $dupStmt = $db->prepare('SELECT id FROM ai_feedback WHERE user_id = ? AND conversation_id = ? AND message_id = ? LIMIT 1');
            $dupStmt->execute([$userId, $conversationId, $messageId]);
            if ($dupStmt->fetchColumn()) {
                throw new MealAdvisorException(409, 'Bạn đã đánh giá tin nhắn này rồi.');
            }

            $ins = $db->prepare('INSERT INTO ai_feedback (user_id, conversation_id, message_id, rating, reason, prompt_version, created_at) VALUES (?,?,?,?,?,?,?)');
            $ins->execute([$userId, $conversationId, $messageId, $rating, $reason, $record['promptVersion'], gmdate('Y-m-d H:i:s', $now)]);
            $record['feedbackId'] = (string)$db->lastInsertId();
            unset($record['ts']);
            return $record;
        }

        if ($fallbackFile !== null) {
            $rows = self::readJsonArray($fallbackFile);
            $recentCount = 0;
            foreach ($rows as $existing) {
                if ((int)($existing['userId'] ?? 0) === $userId) {
                    if ((int)($existing['ts'] ?? 0) >= $now - 3600) $recentCount++;
                    if (($existing['conversationId'] ?? '') === $conversationId && ($existing['messageId'] ?? '') === $messageId) {
                        throw new MealAdvisorException(409, 'Bạn đã đánh giá tin nhắn này rồi.');
                    }
                }
            }
            if ($recentCount >= self::MAX_FEEDBACK_PER_HOUR) {
                throw new MealAdvisorException(429, 'Bạn đã gửi quá nhiều phản hồi trong giờ qua. Vui lòng thử lại sau.');
            }
            $record['feedbackId'] = 'fb_' . bin2hex(random_bytes(6));
            $rows[] = $record;
            $rows = array_slice($rows, -self::MAX_EVENTS);
            self::writeJsonArray($fallbackFile, $rows);
            unset($record['ts']);
            return $record;
        }

        $record['feedbackId'] = 'fb_' . bin2hex(random_bytes(6));
        unset($record['ts']);
        return $record;
    }

    /** Record non-PII latency, token, error, and handoff telemetry. */
    public static function recordMetric(string $metricsFile, array $event, ?int $now = null): void {
        $now ??= time();
        $rows = self::readJsonArray($metricsFile);
        $rows[] = [
            'ts' => $now,
            'latencyMs' => round(max(0.0, (float)($event['latencyMs'] ?? 0.0)), 1),
            'providerMs' => round(max(0.0, (float)($event['providerMs'] ?? 0.0)), 1),
            'promptTokens' => max(0, (int)($event['promptTokens'] ?? 0)),
            'completionTokens' => max(0, (int)($event['completionTokens'] ?? 0)),
            'totalTokens' => max(0, (int)($event['totalTokens'] ?? 0)),
            'source' => (string)($event['source'] ?? 'unknown'),
            'providerError' => !empty($event['providerError']),
            'handoff' => !empty($event['handoff']),
            'promptVersion' => (string)($event['promptVersion'] ?? FoodHubSystemPrompt::PROMPT_VERSION),
        ];
        self::writeJsonArray($metricsFile, array_slice($rows, -self::MAX_EVENTS));
    }

    /** Compute P50/P95 latency, token averages, error rate, handoff count, and feedback summary. */
    public static function report(?PDO $db, string $metricsFile, ?string $feedbackFallbackFile = null, ?int $sinceTs = null): array {
        $events = self::readJsonArray($metricsFile);
        if ($sinceTs !== null) {
            $events = array_values(array_filter($events, static fn($e) => (int)($e['ts'] ?? 0) >= $sinceTs));
        }

        $count = count($events);
        $latencies = array_map(static fn($e) => (float)($e['latencyMs'] ?? 0.0), $events);
        sort($latencies);

        $totalTokens = 0;
        $tokenSamples = 0;
        $providerErrors = 0;
        $handoffs = 0;
        foreach ($events as $e) {
            $t = (int)($e['totalTokens'] ?? 0);
            if ($t > 0) { $totalTokens += $t; $tokenSamples++; }
            if (!empty($e['providerError'])) $providerErrors++;
            if (!empty($e['handoff'])) $handoffs++;
        }

        $feedback = self::feedbackSummary($db, $feedbackFallbackFile, $sinceTs);

        return [
            'promptVersion' => FoodHubSystemPrompt::PROMPT_VERSION,
            'model' => MealAdvisor::GROQ_MODEL,
            'sampleSize' => $count,
            'avgLatencyMs' => $count > 0 ? round(array_sum($latencies) / $count, 1) : null,
            'p50LatencyMs' => self::percentile($latencies, 50),
            'p95LatencyMs' => self::percentile($latencies, 95),
            'tokenSampleSize' => $tokenSamples,
            'avgTokens' => $tokenSamples > 0 ? round($totalTokens / $tokenSamples, 1) : null,
            'totalTokens' => $totalTokens,
            'providerErrorCount' => $providerErrors,
            'providerErrorRate' => $count > 0 ? round($providerErrors / $count * 100, 2) : null,
            'handoffCount' => $handoffs,
            'feedback' => $feedback,
        ];
    }

    public static function feedbackSummary(?PDO $db, ?string $fallbackFile = null, ?int $sinceTs = null): array {
        $rows = [];
        if ($db !== null && self::hasFeedbackTable($db)) {
            if ($sinceTs !== null) {
                $stmt = $db->prepare('SELECT rating, reason, prompt_version FROM ai_feedback WHERE created_at >= ?');
                $stmt->execute([gmdate('Y-m-d H:i:s', $sinceTs)]);
            } else {
                $stmt = $db->query('SELECT rating, reason, prompt_version FROM ai_feedback');
            }
            $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
        } elseif ($fallbackFile !== null) {
            $rows = self::readJsonArray($fallbackFile);
            if ($sinceTs !== null) {
                $rows = array_values(array_filter($rows, static fn($r) => (int)($r['ts'] ?? 0) >= $sinceTs));
            }
        }

        $total = count($rows);
        $pos = 0;
        $neg = 0;
        $byReason = [];
        foreach ($rows as $r) {
            if (($r['rating'] ?? '') === 'positive') {
                $pos++;
            } elseif (($r['rating'] ?? '') === 'negative') {
                $neg++;
                $reason = (string)($r['reason'] ?? 'unspecified');
                if ($reason === '') $reason = 'unspecified';
                $byReason[$reason] = ($byReason[$reason] ?? 0) + 1;
            }
        }

        return [
            'total' => $total,
            'positive' => $pos,
            'negative' => $neg,
            'positiveRate' => $total > 0 ? round($pos / $total * 100, 2) : null,
            'negativeRate' => $total > 0 ? round($neg / $total * 100, 2) : null,
            'byReason' => $byReason,
        ];
    }

    /**
     * Weighted quality scorecard with independent Safety & Privacy blocking gates.
     * Scores per dimension are 0.0..100.0.
     */
    public static function scorecard(
        array $dimensionScores,
        int $safetyFailures = 0,
        int $privacyFailures = 0,
        array $weights = self::SCORECARD_WEIGHTS,
        float $passThreshold = 85.0
    ): array {
        $weighted = 0.0;
        $weightSum = 0.0;
        $normalized = [];
        foreach ($weights as $dim => $w) {
            $score = max(0.0, min(100.0, (float)($dimensionScores[$dim] ?? 0.0)));
            $normalized[$dim] = round($score, 2);
            $weighted += $score * $w;
            $weightSum += $w;
        }
        $overall = $weightSum > 0 ? round($weighted / $weightSum, 2) : 0.0;

        $blockReasons = [];
        if ($safetyFailures > 0) $blockReasons[] = "safety_failures={$safetyFailures}";
        if ($privacyFailures > 0) $blockReasons[] = "privacy_failures={$privacyFailures}";
        $blocked = !empty($blockReasons);

        return [
            'overallScore' => $overall,
            'dimensions' => $normalized,
            'weights' => $weights,
            'safetyFailures' => $safetyFailures,
            'privacyFailures' => $privacyFailures,
            'blocked' => $blocked,
            'blockReasons' => $blockReasons,
            'passed' => !$blocked && $overall >= $passThreshold,
        ];
    }

    public static function percentile(array $sortedValues, float $p): ?float {
        $n = count($sortedValues);
        if ($n === 0) return null;
        $idx = (int)ceil(($p / 100.0) * $n) - 1;
        $idx = max(0, min($n - 1, $idx));
        return round((float)$sortedValues[$idx], 1);
    }

    private static function hasFeedbackTable(PDO $db): bool {
        try {
            $driver = (string)$db->getAttribute(PDO::ATTR_DRIVER_NAME);
            if ($driver === 'sqlite') {
                return (bool)$db->query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='ai_feedback'")->fetchColumn();
            }
            return (bool)$db->query("SHOW TABLES LIKE 'ai_feedback'")->fetchColumn();
        } catch (Throwable) {
            return false;
        }
    }

    private static function readJsonArray(string $file): array {
        if (!is_file($file)) return [];
        $raw = @file_get_contents($file);
        $decoded = is_string($raw) ? json_decode($raw, true) : null;
        return is_array($decoded) ? $decoded : [];
    }

    private static function writeJsonArray(string $file, array $rows): void {
        $tmp = $file . '.tmp-' . bin2hex(random_bytes(4));
        try {
            $encoded = json_encode(array_values($rows), JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            if (@file_put_contents($tmp, $encoded, LOCK_EX) !== false) {
                @chmod($tmp, 0600);
                @rename($tmp, $file);
            }
        } finally {
            if (is_file($tmp)) @unlink($tmp);
        }
    }
}
