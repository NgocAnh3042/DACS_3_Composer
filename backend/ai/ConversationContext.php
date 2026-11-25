<?php
declare(strict_types=1);

/**
 * Manages sliding conversation context, rolling summary memory,
 * consistent pronoun resolution, and privacy sanitization per authenticated user.
 */
final class ConversationContext {
    public const DEFAULT_WINDOW = 4;
    public const SESSION_TTL = 86400;
    public const MAX_SESSIONS_PER_USER = 4;

    public static function sanitize(string $text): string {
        if (preg_match('/otp|mật khẩu|mat khau|số thẻ|so the|cvv|password|mã xác thực|ma xac thuc/iu', $text)) {
            return '[Đã ẩn thông tin riêng tư]';
        }
        return (string)preg_replace(
            '/[\w.+-]+@[\w.-]+|(?<!\d)(?:\+?\d[ .-]?){9,16}(?!\d)/u',
            '[đã ẩn]',
            $text
        );
    }

    /**
     * Resolve consistent Vietnamese pronoun for addressing the customer.
     * Priority: explicit request > current self-reference > history > older summary > default "mình".
     */
    public static function detectPronoun(string $message, array $history = [], array $summary = []): string {
        $texts = [$message];
        foreach (array_reverse($history) as $turn) {
            $q = (string)($turn['question'] ?? '');
            if ($q !== '') {
                $texts[] = $q;
            }
        }

        // 1. Explicit addressing preference ("gọi mình là anh", "xưng hô là chị")
        foreach ($texts as $t) {
            $f = MealAdvisor::folded($t);
            if (preg_match('/(?:goi (?:minh|toi|tui|em) la|xung ho la|hay goi la)\s+(anh|chi|ban|minh)\b/u', $f, $m)) {
                return match ($m[1]) {
                    'anh' => 'anh',
                    'chi' => 'chị',
                    'ban' => 'bạn',
                    default => 'mình',
                };
            }
        }

        // 2. Customer self-identifies as "anh" or "chị" (avoid "anh shipper", "món anh đặt" false positives unless clear)
        foreach ($texts as $t) {
            $f = MealAdvisor::folded($t);
            if (preg_match('/(?:^|\b)(?:anh dat|anh hoi|anh muon|anh dang|anh bi|anh cho|anh kiem tra|anh xem|anh can|don cua anh|giup anh|cho anh|cua anh)\b/u', $f)
                && !preg_match('/\banh shipper\b/u', $f)) {
                return 'anh';
            }
            if (preg_match('/(?:^|\b)(?:chi dat|chi hoi|chi muon|chi dang|chi bi|chi cho|chi kiem tra|chi xem|chi can|don cua chi|giup chi|cho chi|cua chi)\b/u', $f)) {
                return 'chị';
            }
        }

        // 3. Previously remembered summary pronoun
        $remembered = $summary['pronoun'] ?? ($summary['address'] ?? '');
        if (in_array($remembered, ['anh', 'chị', 'bạn', 'mình'], true)) {
            return $remembered;
        }

        return 'mình';
    }

    public static function prefersBrief(string $message): bool {
        $f = MealAdvisor::folded($message);
        return (bool)preg_match('/\b(noi ngan thoi|ngan gon thoi|tra loi ngan|ngan thoi|noi nhanh thoi|noi thang)\b/u', $f);
    }

    /**
     * Analyze pragmatic human-chat cues (self-correction, implicit follow-up, hesitation, tone register)
     * so the AI responds to conversational intent rather than surface keywords alone.
     *
     * @return array{act: string, register: string, referencesPriorTurn: bool}
     */
    public static function analyzeDiscourse(string $message, array $history = [], array $summary = []): array {
        $f = MealAdvisor::folded($message);
        $hasHistory = !empty($history) || !empty($summary);

        $act = 'direct';
        if (preg_match('/(?:^|\b)(?:y (?:minh|toi|tui|em|anh|chi) la|khong phai(?: vay)?|a nham|nham roi|hoi cai kia|khong y la)\b/u', $f)) {
            $act = 'self_correction';
        } elseif ($hasHistory && preg_match('/(?:^|\b)(?:phan van|khong biet chon|cai nao ngon hon|nen an gi|nen chon|hay la)\b/u', $f)) {
            $act = 'hesitation_or_comparison';
        } elseif ($hasHistory && preg_match('/^(?:ok|oke|u|uh|vang|da|ung|chot|trien|duoc do|lam luon|gui luon|nho ban|nho em)\b/u', $f)) {
            $act = 'natural_confirmation';
        } elseif (preg_match('/(?:^|\b)(?:thoi bo qua|a ma cho hoi|nhan tien|doi chu de|hoi cai khac)\b/u', $f)) {
            $act = 'topic_shift';
        } elseif ($hasHistory && preg_match('/\b(cai do|cai nay|mon do|mon nay|mon dau|cai dau|mon thu|cai thu|quan do|quan vua nay|don do|vua nay|the con|vay con|con lai)\b/u', $f)) {
            $act = 'follow_up_reference';
        }

        $register = 'neutral';
        if (self::prefersBrief($message)) {
            $register = 'urgent_brief';
        } elseif (preg_match('/\b(tui|ba oi|shop oi|nha|ne|nghen|hen|zay|dzay|hok|hem|xiu)\b/u', $f)) {
            $register = 'casual';
        } elseif (preg_match('/\b(da |vui long|nho em|cam on em|giup anh|giup chi)\b/u', $f)) {
            $register = 'polite';
        }

        return [
            'act' => $act,
            'register' => $register,
            'referencesPriorTurn' => in_array($act, ['self_correction', 'hesitation_or_comparison', 'natural_confirmation', 'follow_up_reference'], true),
        ];
    }

    /**
     * Append a completed turn to the sliding window and fold evicted turns into a compact summary.
     *
     * @return array{history: array, summary: array}
     */
    public static function appendTurn(
        array $history,
        array $summary,
        string $question,
        string $answer,
        array $dishIds = [],
        ?array $menuCursor = null,
        int $windowSize = self::DEFAULT_WINDOW
    ): array {
        $cleanQ = self::sanitize($question);
        $cleanA = mb_substr(self::sanitize($answer), 0, 700);
        $turn = [
            'question' => $cleanQ,
            'answer' => $cleanA,
            'dishIds' => array_values($dishIds),
        ];
        if ($menuCursor !== null) {
            $turn['menuCursor'] = $menuCursor;
        } elseif ($history && isset(end($history)['menuCursor'])) {
            $turn['menuCursor'] = end($history)['menuCursor'];
        }

        $pronoun = self::detectPronoun($question, $history, $summary);
        if ($pronoun !== 'mình' || !empty($summary['pronoun'])) {
            $summary['pronoun'] = $pronoun;
        }

        // Layer C: Task / Support Memory (tracks created support tickets; never authoritative for live order state)
        if (preg_match('/đã chuyển phiếu #(\d+)/iu', $cleanA, $tm)) {
            $orderId = null;
            if (preg_match('/đơn #(\d+)/iu', $cleanA, $om)) $orderId = $om[1];
            elseif (!empty($summary['orderId'])) $orderId = (string)$summary['orderId'];
            $summary['taskMemory'] = [
                'ticketId' => $tm[1],
                'orderId' => $orderId,
                'status' => 'open',
                'pendingAction' => 'awaiting_admin_response',
                'lastVerifiedUpdate' => gmdate('Y-m-d\TH:i:s\Z'),
            ];
        }

        $history[] = $turn;

        while (count($history) > $windowSize) {
            $old = array_shift($history);
            $summary = self::foldTurnIntoSummary($summary, $old);
        }

        return ['history' => array_values($history), 'summary' => $summary];
    }

    private static function foldTurnIntoSummary(array $summary, array $turn): array {
        $q = (string)($turn['question'] ?? '');
        $a = (string)($turn['answer'] ?? '');
        $intent = FoodHubSupport::intent($q) ?? 'meal';
        $summary['topic'] = $intent;

        if (preg_match('/đơn #(\d+)/iu', $a, $m) || preg_match('/(?:#|\bdon\s+)(\d+)\b/iu', MealAdvisor::folded($q), $m)) {
            $summary['orderId'] = $m[1];
        }

        $pronoun = self::detectPronoun($q, [], $summary);
        if ($pronoun !== 'mình') {
            $summary['pronoun'] = $pronoun;
        }

        if (in_array($intent, ['incident', 'refund', 'cancel', 'order'], true)) {
            $summary['issue'] = mb_substr($q, 0, 120);
        }

        $summary['awaitingTicketConfirmation'] = str_contains($a, 'Gửi phiếu hỗ trợ');
        $summary['awaitingCancellationConfirmation'] = str_contains($a, 'Xác nhận hủy đơn');

        if (preg_match('/đã được hủy|đã chuyển phiếu #(\d+)/iu', $a)) {
            $confirmed = is_array($summary['confirmedActions'] ?? null) ? $summary['confirmedActions'] : [];
            $confirmed[] = mb_substr($a, 0, 100);
            $summary['confirmedActions'] = array_slice($confirmed, -3);
            $summary['unresolvedQuestion'] = null;
        } elseif (str_ends_with(trim($a), '?')) {
            $summary['unresolvedQuestion'] = mb_substr(trim($a), 0, 160);
        }

        return $summary;
    }

    /**
     * Load user-scoped sessions from private cache file.
     *
     * @return array{sessions: array, history: array, summary: array}
     */
    public static function loadSession(string $memoryFile, string $chatId, array $clientHistory, int $now): array {
        $raw = @file_get_contents($memoryFile);
        $sessions = is_string($raw) ? (json_decode($raw, true) ?: []) : [];
        if (!is_array($sessions)) {
            $sessions = [];
        }
        $sessions = array_filter($sessions, static fn($v) => is_array($v) && ($v['time'] ?? 0) > $now - self::SESSION_TTL);
        if (isset($sessions[$chatId]) && is_array($sessions[$chatId])) {
            return [
                'sessions' => $sessions,
                'history' => is_array($sessions[$chatId]['history'] ?? null) ? $sessions[$chatId]['history'] : [],
                'summary' => is_array($sessions[$chatId]['summary'] ?? null) ? $sessions[$chatId]['summary'] : [],
            ];
        }
        return [
            'sessions' => $sessions,
            'history' => $clientHistory,
            'summary' => [],
        ];
    }

    public static function saveSession(string $memoryFile, array $sessions, string $chatId, array $history, array $summary, int $now): void {
        unset($sessions[$chatId]);
        $sessions[$chatId] = [
            'time' => $now,
            'history' => $history,
            'summary' => $summary,
        ];
        $bounded = array_slice($sessions, -self::MAX_SESSIONS_PER_USER, null, true);
        $tmp = $memoryFile . '.tmp-' . bin2hex(random_bytes(4));
        try {
            $encoded = json_encode($bounded, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            if (@file_put_contents($tmp, $encoded, LOCK_EX) === false || !@rename($tmp, $memoryFile)) {
                error_log('Support history write failed');
            }
        } finally {
            if (is_file($tmp)) {
                @unlink($tmp);
            }
        }
    }
}
