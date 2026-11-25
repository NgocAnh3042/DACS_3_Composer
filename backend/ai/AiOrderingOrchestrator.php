<?php
declare(strict_types=1);

/**
 * FoodHub AI Ordering Agent — Intent & Entity Extractor + Tool Orchestrator.
 *
 * Bridges natural multi-turn Vietnamese chat AND structured UI actions to
 * AiOrderDraftService without ever letting LLM text fabricate IDs, prices,
 * vouchers, addresses, ETAs, or bypass final user confirmation.
 */
final class AiOrderingOrchestrator {
    public const INTENTS = [
        'SEARCH_FOOD',
        'SELECT_FOOD',
        'MODIFY_QUANTITY',
        'ADD_ITEM',
        'REMOVE_ITEM',
        'CONFIGURE_ITEM',
        'SELECT_ADDRESS',
        'CHANGE_ADDRESS',
        'CHECK_VOUCHERS',
        'APPLY_VOUCHER',
        'CHANGE_PAYMENT',
        'REQUEST_ETA',
        'REQUEST_ORDER_SUMMARY',
        'CONFIRM_ORDER',
        'CANCEL_DRAFT',
        'REORDER_PAST_ORDER',
        'ACCEPT_UPSELL',
        'SCHEDULE_DELIVERY',
        'TRACK_ORDER',
        'ASK_HUMAN_SUPPORT',
    ];

    /** Detect prompt injection attempts trying to bypass confirmation, alter prices, or spoof identity. */
    public static function hasPromptInjection(string $message): bool {
        $q = MealAdvisor::folded($message);
        return (bool)preg_match(
            '/\b(bo qua.*(?:quy tac|xac nhan|kiem tra|chi dan)|ignore.*(?:previous|rules|instructions)|sua gia|doi gia.*thanh|set price|tu dong thanh toan|danh dau da thanh toan|mark.*paid|don cua nguoi khac|user_id\s*=|select\s+\*\s+from)\b/u',
            $q
        );
    }

    /** Detect whether user tries to force skipping mandatory final confirmation. */
    public static function triesToSkipConfirmation(string $message): bool {
        $q = MealAdvisor::folded($message);
        return (bool)preg_match(
            '/\b(dung hoi nua|khong can hoi|bo qua buoc xac nhan|cu tu dat|tu dat va tu thanh toan|dat thang luon khong can xac nhan)\b/u',
            $q
        );
    }

    /** Parse Vietnamese cardinal numbers ("một/1", "hai/2", ..., "chục/10", "2x"). */
    public static function extractQuantity(string $message): ?int {
        $q = FoodHubKnowledgeBase::expandVietnamese($message);
        // Multiplier shorthand like "1x Mâm khai vị", "2x Trà sữa"
        if (preg_match('/\b(\d{1,2})\s*x\b/u', $q, $mx)) {
            $nx = (int)$mx[1];
            if ($nx >= 1 && $nx <= 50) return $nx;
        }
        // Explicit digits before classifiers
        if (preg_match('/\b(\d{1,2})\s*(?:phan|suat|ly|coc|to|bat|dia|o|cai|hop|chiec|mieng|xien|chai|lon|khay|chen)\b/u', $q, $m)) {
            $n = (int)$m[1];
            if ($n >= 1 && $n <= 50) return $n;
        }
        if (preg_match('/\b(?:them|lay|dat|mua|cho\s+(?:anh|chi|em|minh|toi))\s+(\d{1,2})\b(?!\s*(?:k|nghin|ngan|d|dong|vnd|km|phut|sao))/u', $q, $m)) {
            $n = (int)$m[1];
            if ($n >= 1 && $n <= 50) return $n;
        }
        $wordMap = [
            'mot' => 1, 'hai' => 2, 'ba' => 3, 'bon' => 4, 'tu' => 4,
            'nam' => 5, 'sau' => 6, 'bay' => 7, 'tam' => 8, 'chin' => 9,
            'muoi' => 10, 'chuc' => 10,
        ];
        foreach ($wordMap as $word => $val) {
            if (preg_match('/\b' . $word . '\s+(?:phan|suat|ly|coc|to|bat|dia|o|cai|hop|chiec|mieng|xien|chai|lon|khay|chen)\b/u', $q)) {
                return $val;
            }
        }
        return null;
    }

    /** Extract option/size/topping & kitchen note entities from Vietnamese text. */
    public static function extractItemCustomization(string $message): array {
        $q = MealAdvisor::folded($message);
        $optionNames = [];
        $notes = [];

        if (preg_match('/\bsize\s*([sml]|nho|vua|lon)\b/u', $q, $m)) {
            $sz = strtoupper($m[1]);
            $map = ['NHO' => 'Size S', 'VUA' => 'Size M', 'LON' => 'Size L'];
            $optionNames[] = $map[$sz] ?? ('Size ' . $sz);
        }
        if (preg_match_all('/\bthem\s+(tran chau(?:\s+\w+)?|pho mai|trung(?:\s+\w+)?|kem\s+pho\s+mai|thach(?:\s+\w+)?|quay)\b/u', $q, $matches)) {
            foreach ($matches[1] as $top) {
                $optionNames[] = 'Thêm ' . trim($top);
            }
        }

        $notePatterns = [
            '/\b(it da|khong da|nhieu da|da rieng)\b/u' => ['it da' => 'ít đá', 'khong da' => 'không đá', 'nhieu da' => 'nhiều đá', 'da rieng' => 'để đá riêng'],
            '/\b(it duong|khong duong|ngot vua|it ngot)\b/u' => ['it duong' => 'ít đường', 'khong duong' => 'không đường', 'ngot vua' => 'ngọt vừa', 'it ngot' => 'ít ngọt'],
            '/\b(khong hanh|bo hanh|nhieu hanh|khong ngo)\b/u' => ['khong hanh' => 'không hành', 'bo hanh' => 'bỏ hành', 'nhieu hanh' => 'nhiều hành', 'khong ngo' => 'không ngò'],
            '/\b(it cay|khong cay|khong ot|cay vua|them ot)\b/u' => ['it cay' => 'ít cay', 'khong cay' => 'không cay', 'khong ot' => 'không ớt', 'cay vua' => 'cay vừa', 'them ot' => 'thêm ớt'],
            '/\b(de rieng nuoc(?:\s+dung|\s+leo|\s+mam|\s+cham)?|xin them dua|khong lay dua|nhieu rau)\b/u' => [],
        ];
        foreach ($notePatterns as $regex => $canonMap) {
            if (preg_match_all($regex, $q, $nm)) {
                foreach ($nm[1] as $hit) {
                    $notes[] = $canonMap[$hit] ?? $hit;
                }
            }
        }

        return [
            'optionNames' => array_values(array_unique($optionNames)),
            'note' => implode(', ', array_values(array_unique($notes))),
        ];
    }

    /** Extract payment method cue; returns ['method' => 'cash'|'payos'|'unsupported', 'raw' => string]|null */
    public static function extractPaymentCue(string $message): ?array {
        $q = FoodHubKnowledgeBase::expandVietnamese($message);
        if (preg_match('/\b(momo|zalopay|zalo pay|vnpay|shopeepay|apple pay|the tin dung|visa|mastercard)\b/u', $q, $m)) {
            return ['method' => 'unsupported', 'raw' => strtoupper($m[1])];
        }
        if (preg_match('/\b(payos|vietqr|quet\s*qr|quet\s*ma|chuyen khoan|thanh toan online)\b/u', $q)) {
            return ['method' => 'payos', 'raw' => 'PayOS'];
        }
        if (preg_match('/\b(tien mat|cod|tra khi nhan|thanh toan khi nhan)\b/u', $q)) {
            return ['method' => 'cash', 'raw' => 'COD'];
        }
        return null;
    }

    /** Extract voucher cue from natural text. */
    public static function extractVoucherCue(string $message): ?array {
        $q = MealAdvisor::folded($message);
        if (preg_match('/\b(khong dung ma|khong ap ma|giu lai ma|bo ma giam|go voucher|khong dung voucher)\b/u', $q)) {
            return ['mode' => 'none', 'code' => 'NONE'];
        }
        if (preg_match('/\b(?:dung|ap|ap dung|nhap|thu)\s+(?:ma|code|voucher)\s+([a-z0-9_-]{3,32})\b/u', $q, $m)) {
            $candidate = strtoupper($m[1]);
            if (!in_array($candidate, ['GIAM', 'TOT', 'NAY', 'NAO', 'GIUP', 'LUON', 'CHO', 'CUA'], true)) {
                return ['mode' => 'code', 'code' => $candidate];
            }
        }
        if (preg_match('/\b(ap ma.*tot nhat|chon ma.*loi nhat|co code giam.*quat luon|ap voucher tot nhat|dung ma giam tot nhat|toi uu voucher)\b/u', $q)) {
            return ['mode' => 'best', 'code' => null];
        }
        if (preg_match('/\b(kiem tra voucher|xem voucher|co ma giam.*khong|co voucher.*khong|uu dai nao ap dung)\b/u', $q)) {
            return ['mode' => 'check', 'code' => null];
        }
        return null;
    }

    /** Extract address selection cue ("nhà", "công ty", "văn phòng", "địa chỉ cũ", "mặc định"). */
    public static function extractAddressCue(string $message): ?string {
        $q = FoodHubKnowledgeBase::expandVietnamese($message);
        if (preg_match('/\b(?:giao|chuyen|doi)\s*(?:ve|qua|den|toi|sang)?\s*(nha(?:\s+rieng)?|cong ty|van phong|truong|ky tuc xa|dia chi cu|dia chi mac dinh|cho cu)\b/u', $q, $m)) {
            return trim($m[1]);
        }
        if (preg_match('/\b(?:dia chi)\s+(nha(?:\s+rieng)?|cong ty|van phong)\b/u', $q, $m)) {
            return trim($m[1]);
        }
        return null;
    }

    /** Resolve an address cue string against verified user addresses. */
    public static function matchSavedAddress(array $addresses, string $cue): ?array {
        if (!$addresses) return null;
        $f = MealAdvisor::folded($cue);
        if (preg_match('/dia chi cu|mac dinh|cho cu/', $f)) {
            return $addresses[0];
        }
        foreach ($addresses as $addr) {
            $hay = MealAdvisor::folded($addr['label'] . ' ' . $addr['addressLine']);
            if (str_contains($f, 'cong ty') || str_contains($f, 'van phong')) {
                if (preg_match('/cong ty|van phong|office|toa nha/', $hay)) return $addr;
            } elseif (str_contains($f, 'nha')) {
                if (preg_match('/nha|home|rieng/', $hay)) return $addr;
            } elseif ($f !== '' && str_contains($hay, $f)) {
                return $addr;
            }
        }
        // If user says "về nhà" and has a default address (and no office label conflict), return default if labeled nhà or single address
        if (str_contains($f, 'nha') && count($addresses) === 1) {
            return $addresses[0];
        }
        return null;
    }

    /** Detect One-Tap Reorder natural Vietnamese phrases ("Đặt lại y hệt đơn hôm qua", "Đặt lại đơn cũ"). */
    public static function isReorderIntent(string $foldedExpanded): bool {
        return (bool)preg_match(
            '/\b(dat\s+lai(?:\s+y\s+het|\s+y\s+chang|\s+giong(?:\s+het)?)?\s+don|mua\s+lai\s+don|goi\s+lai\s+don|len\s+lai\s+don|y\s+het\s+don\s+(?:hom\s+qua|cu|gan\s+nhat|truoc)|don\s+(?:hom\s+qua|cu|gan\s+nhat|truoc\s+do).*dat\s+lai)\b/u',
            $foldedExpanded
        );
    }

    /** Detect explicit customer consent to add the AI-suggested voucher-unlocking side dish/drink. */
    public static function isAcceptUpsellIntent(string $foldedExpanded): bool {
        return (bool)preg_match(
            '/\b(them\s+mon\s+(?:kem|phu|goi\s+y)|lay\s+them\s+mon\s+(?:kem|phu|goi\s+y)|them\s+(?:nuoc|mon)\s+do(?:\s+luon|\s+di|\s+nha|\s+nhe)?|dong\s+y\s+them\s+mon|ok\s+them\s+mon.*(?:ma|voucher)|chot\s+them\s+mon\s+goi\s+y)\b/u',
            $foldedExpanded
        );
    }

    /**
     * Parse natural Vietnamese scheduled delivery cues ("giao lúc 12h15", "hẹn 18:30 tối nay",
     * "giao 7h30 sáng mai", "giao sau 45 phút", "đổi lại giao ngay").
     */
    public static function extractScheduleCue(string $message, ?int $nowTs = null): ?array {
        $now = $nowTs ?? time();
        $q = FoodHubKnowledgeBase::expandVietnamese($message);

        if (preg_match('/\b(doi\s+(?:lai|sang|ve)\s+giao\s+ngay|giao\s+ngay\s+(?:luon|di|bay\s+gio)|bo\s+hen\s+gio|huy\s+hen\s+gio|khong\s+hen\s+gio\s+nua)\b/u', $q)) {
            return ['mode' => 'now', 'iso' => null, 'raw' => 'giao ngay'];
        }

        if (preg_match('/\b(?:giao\s+sau|hen\s+sau)\s+(\d{1,3})\s*phut\b/u', $q, $mMin)
            || preg_match('/\b(\d{1,3})\s*phut\s+nua\s+giao\b/u', $q, $mMin)
        ) {
            $mins = (int)$mMin[1];
            return [
                'mode' => 'scheduled',
                'iso' => date('Y-m-d H:i:00', $now + ($mins * 60)),
                'raw' => $mMin[0],
            ];
        }

        if (preg_match('/\b(?:giao\s+sau|hen\s+sau)\s+(\d{1,2})(?:\s*(?:tieng|gio)\s*(ruoi|\d{1,2}\s*phut)?)?\b/u', $q, $mHr) && str_contains($mHr[0], 'tieng')) {
            $hrs = (int)$mHr[1];
            $extraMins = !empty($mHr[2]) && str_contains($mHr[2], 'ruoi') ? 30 : 0;
            return [
                'mode' => 'scheduled',
                'iso' => date('Y-m-d H:i:00', $now + ($hrs * 3600) + ($extraMins * 60)),
                'raw' => $mHr[0],
            ];
        }

        // Explicit clock time accompanied by delivery/schedule context: e.g. "giao lúc 12h15", "hẹn 18:30 tối nay", "hẹn giao 7h30 sáng mai"
        if (preg_match(
            '/\b(?:hen(?:\s+gio)?(?:\s+giao(?:\s+hang)?)?|giao(?:\s+hang)?(?:\s+truoc)?(?:\s+vao)?(?:\s+luc|\s+tam|\s+khoang)?|dat\s+truoc(?:\s+luc)?|nhan\s+hang\s+luc)\s*(?:luc\s+|vao\s+|tam\s+)?(\d{1,2})\s*(?:h|:|gio)\s*(ruoi|\d{1,2})?\s*(sang|trua|chieu|toi|dem)?(?:\s*(hom\s+nay|nay|ngay\s+mai|mai))?\b/u',
            $q,
            $mClock
        )) {
            $hour = (int)$mClock[1];
            $minRaw = $mClock[2] ?? '';
            $minute = $minRaw === 'ruoi' ? 30 : ($minRaw !== '' ? (int)$minRaw : 0);
            $meridiem = $mClock[3] ?? '';
            $dayHint = $mClock[4] ?? '';

            if ($hour > 23 || $minute > 59) {
                return null;
            }
            if (in_array($meridiem, ['chieu', 'toi', 'dem'], true) && $hour >= 1 && $hour <= 11) {
                $hour += 12;
            } elseif ($meridiem === 'trua' && $hour >= 1 && $hour <= 3) {
                $hour += 12;
            }

            $dayOffset = str_contains($dayHint, 'mai') ? 86400 : 0;
            $baseDate = date('Y-m-d', $now + $dayOffset);
            $targetTs = strtotime(sprintf('%s %02d:%02d:00', $baseDate, $hour, $minute));
            if ($targetTs === false) {
                return null;
            }

            // If user wrote "giao lúc 6h" without AM/PM during daytime and 06:00 has passed while 18:00 is upcoming today
            if ($dayOffset === 0 && $meridiem === '' && $hour >= 1 && $hour <= 10 && $targetTs < $now) {
                $pmTs = strtotime(sprintf('%s %02d:%02d:00', $baseDate, $hour + 12, $minute));
                if ($pmTs !== false && $pmTs > $now) {
                    $targetTs = $pmTs;
                }
            }

            return [
                'mode' => 'scheduled',
                'iso' => date('Y-m-d H:i:00', $targetTs),
                'raw' => $mClock[0],
            ];
        }

        return null;
    }

    /**
     * Determine if this customer turn belongs to the Ordering Agent flow
     * rather than general FAQ/support or read-only menu browsing.
     */
    public static function shouldHandle(array $me, array $input, ?array $activeDraft, ?array $actionPayload = null): bool {
        if (($me['role'] ?? '') !== 'customer') {
            return false;
        }
        if (is_array($actionPayload) && !empty($actionPayload['type'])) {
            return true;
        }

        $text = (string)($input['message'] ?? '');
        $q = FoodHubKnowledgeBase::expandVietnamese($text);

        // Do NOT hijack feedback buttons, support ticket submissions, or explicit #order_id tracking/cancellation of ALREADY PLACED orders!
        if (preg_match('/^(?:danh gia\s*:|gui phieu ho tro|xac nhan huy don\s*#?\d+)/u', $q)) {
            return false;
        }
        if (preg_match('/(?:#\d+|\bdon\s+#?\d+)\b/u', $q) && !preg_match('/\bdon nhap\b/u', $q)) {
            return false;
        }
        if ($activeDraft === null && preg_match('/\b(don vua dat|don cua minh dau|shipper dau|tai xe dau|giao cham|giao sai|giao thieu|hoan tien)\b/u', $q)) {
            return false;
        }

        // One-Tap Reorder trigger ("Đặt lại y hệt đơn hôm qua", "Đặt lại đơn cũ")
        if (self::isReorderIntent($q)) {
            return true;
        }

        // Explicit ordering trigger verbs (including "Chọn đặt ..." from recommendation cards!)
        if (preg_match('/\b(chon dat|dat cho|dat giup|dat dum|dat luon|dat ngay|dat mon|muon dat|len don|chot don|mua cho|mua giup|lay cho\s+(?:anh|chi|em|minh|toi|tui)|goi cho\s+(?:anh|chi|em|minh|toi|tui)|dat\s+(?:tui|minh|anh|chi|em|toi)\s+(?:\d+|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi)|dat\s+(?:mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi|\d+)\s*(?:x|phan|suat|ly|coc|to|bat|dia|o|cai))\b/u', $q)) {
            return true;
        }
        if (preg_match('/\b(dat giup duoc khong|co dat duoc khong|them vao don|bo vao gio nhap)\b/u', $q)) {
            return true;
        }
        // Selecting a dish from the previous AI recommendation turn to order
        $lastTurn = !empty($input['history']) ? end($input['history']) : null;
        $hasRecentDishRefs = is_array($lastTurn) && !empty($lastTurn['dishIds']);
        if ($hasRecentDishRefs && preg_match('/\b(lay mon|chon mon|chot mon|lay cai|lay phan|lay ly|lay to)\s*(?:hoi nay|vua nay|dau tien|thu nhat|thu hai|thu 2|so 1|so 2|do|nay)\b/u', $q)) {
            return true;
        }

        // If an active draft already exists in this conversation, route draft follow-ups here!
        if ($activeDraft !== null) {
            if (self::isAcceptUpsellIntent($q) || self::extractScheduleCue($text) !== null) {
                return true;
            }
            if (preg_match('/\b(them|bot|tang|giam|doi sang|khong an.*nua|bo mon|xoa mon|bo hanh|it da|khong cay|giao ve|giao qua|dia chi|voucher|ma giam|code giam|tien mat|cod|payos|chuyen khoan|momo|hen gio|hen giao|giao luc|giao ngay|tong bao nhieu|het bao nhieu|bao lau co do|bao lau toi|xac nhan|chot|dat di|ok dat|u dat|thoi huy|khong dat nua|huy don nhap|xem lai don)\b/u', $q)) {
                return true;
            }
            if ($activeDraft['state'] === AiOrderDraftService::STATE_FOOD_SELECTION) {
                return true;
            }
            if ($activeDraft['state'] === AiOrderDraftService::STATE_AWAITING_CONFIRMATION
                && preg_match('/^(?:u|uh|ok|oke|vang|da|duoc|chot|dat di|dat luon|dong y)[.! nha nhe a]*$/u', trim($q))
            ) {
                return true;
            }
        }

        return false;
    }

    /** Extract clean food search query from an ordering sentence (preserving Vietnamese diacritics when present). */
    public static function cleanFoodQuery(string $message, bool $preserveDiacritics = false): string {
        $base = $preserveDiacritics ? mb_strtolower($message, 'UTF-8') : MealAdvisor::folded($message);
        if ($preserveDiacritics) {
            $base = preg_replace('/\b(shop ơi|em ơi|bạn ơi|ai ơi|cho anh|cho chị|cho em|cho mình|cho tôi|cho tui|giúp anh|giúp chị|giúp em|giúp mình|giúp tui|chọn đặt|đặt giúp|đặt cho|đặt dùm|mua cho|mua giúp|lấy cho|gọi cho|đặt tui|muốn ăn|muốn đặt|lên đơn|đặt luôn|đặt ngay|đặt|mua|lấy|gọi|thêm|cho|giúp|anh|chị|em|mình|tôi|tui|được không|nha|nhé|với|đi|luôn|dùm|ạ)\b/u', ' ', (string)$base);
            $base = preg_replace('/\b\d{1,2}\s*x\b/u', ' ', (string)$base);
            $base = preg_replace('/\b(?:\d{1,2}|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|chục)\s*(?:phần|suất|ly|cốc|tô|bát|đĩa|ổ|cái|hộp|chiếc|miếng|khay|chén)\b/u', ' ', (string)$base);
            $base = preg_replace('/\bsize\s*(?:[sml]|nhỏ|vừa|lớn)\b/u', ' ', (string)$base);
            $base = preg_replace('/\b(hẹn giờ.*|hẹn giao.*|hẹn\s+\d{1,2}.*|giao lúc.*|giao vào.*|giao trước.*|giao sau.*|ít đá|không đá|ít đường|không đường|không hành|bỏ hành|ít cay|không cay|giao về.*|giao qua.*|tiền mặt|cod|payos|chuyển khoản|momo|có code.*|áp mã.*|kiểm tra voucher.*|thôi.*không ăn.*nữa|đổi sang|đổi qua|chuyển sang)\b/u', ' ', (string)$base);
            return trim((string)preg_replace('/[^\p{L}\p{N}]+/u', ' ', (string)$base));
        }
        $q = $base;
        $q = preg_replace('/\b(shop oi|em oi|ban oi|ai oi|cho anh|cho chi|cho em|cho minh|cho toi|cho tui|giup anh|giup chi|giup em|giup minh|giup tui|chon dat|dat giup|dat cho|dat dum|mua cho|mua giup|lay cho|goi cho|dat tui|muon an|muon dat|len don|dat luon|dat ngay|dat|mua|lay|goi|them|cho|giup|anh|chi|em|minh|toi|tui|duoc khong|nha|nhe|voi|di|luon|dum|a)\b/u', ' ', $q);
        $q = preg_replace('/\b\d{1,2}\s*x\b/u', ' ', $q);
        $q = preg_replace('/\b(?:\d{1,2}|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi|chuc)\s*(?:phan|suat|ly|coc|to|bat|dia|o|cai|hop|chiec|mieng|khay|chen)\b/u', ' ', $q);
        $q = preg_replace('/\bsize\s*(?:[sml]|nho|vua|lon)\b/u', ' ', $q);
        $q = preg_replace('/\b(hen gio.*|hen giao.*|hen\s+\d{1,2}.*|giao luc.*|giao vao.*|giao truoc.*|giao sau.*|it da|khong da|it duong|khong duong|khong hanh|bo hanh|it cay|khong cay|giao ve.*|giao qua.*|tien mat|cod|payos|chuyen khoan|momo|co code.*|ap ma.*|kiem tra voucher.*)\b/u', ' ', $q);
        $q = preg_replace('/\b(thoi.*khong an.*nua|doi sang|doi qua|chuyen sang)\b/u', ' ', $q);
        return trim((string)preg_replace('/\s+/u', ' ', $q));
    }

    /** Search real orderable dishes matching a food query, scored by diacritic & token overlap. */
    public static function searchOrderableFoods(PDO $pdo, string $foodQuery, ?string $storeId = null, float $ceiling = 10000000.0, string $rawMessage = ''): array {
        $clean = trim($foodQuery);
        if ($clean === '') return [];
        $candidates = MenuKnowledge::candidates($pdo, $ceiling, false, $rawMessage !== '' ? $rawMessage : $clean, [], $storeId);
        if (!$candidates) return [];

        $tokens = array_values(array_filter(
            explode(' ', MenuKnowledge::text($clean)),
            static fn($w) => strlen($w) >= 2 && !in_array($w, ['mon', 'an', 'do', 'quan', 'nha', 'hang', 'tiem'], true)
        ));
        if (!$tokens) return [];

        $rawDishQuery = $rawMessage !== '' ? self::cleanFoodQuery($rawMessage, true) : '';
        $userTypedDiacritics = $rawDishQuery !== '' && $rawDishQuery !== MealAdvisor::folded($rawDishQuery);
        $rawTokens = $userTypedDiacritics
            ? array_values(array_filter(explode(' ', $rawDishQuery), static fn($w) => mb_strlen($w, 'UTF-8') >= 2))
            : [];

        $scored = [];
        $phrase = implode(' ', $tokens);
        $minRequiredTokens = count($tokens) >= 2 ? (int)ceil(count($tokens) * 0.5) : 1;

        $msgLower = ' ' . trim((string)preg_replace('/[^\p{L}\p{N}]+/u', ' ', mb_strtolower($rawMessage !== '' ? $rawMessage : $clean, 'UTF-8'))) . ' ';
        $msgFolded = ' ' . MenuKnowledge::text($rawMessage !== '' ? $rawMessage : $clean) . ' ';

        foreach ($candidates as $row) {
            $dishRawClean = trim((string)preg_replace('/[^\p{L}\p{N}]+/u', ' ', mb_strtolower((string)$row['name'], 'UTF-8')));
            $dishFoldedClean = MenuKnowledge::text((string)$row['name']);
            $nameLower = ' ' . $dishRawClean . ' ';
            $nameNorm = ' ' . $dishFoldedClean . ' ';
            $catNorm = ' ' . MenuKnowledge::text((string)($row['category'] ?? '')) . ' ';
            $score = 0;

            // Full dish name mentioned verbatim inside a longer conversational sentence!
            $fullDishMentioned = ($userTypedDiacritics && $dishRawClean !== '' && str_contains($msgLower, ' ' . $dishRawClean . ' '))
                || (!$userTypedDiacritics && $dishFoldedClean !== '' && str_contains($msgFolded, ' ' . $dishFoldedClean . ' '));
            if ($fullDishMentioned) {
                $score += 25;
            }

            if ($userTypedDiacritics && $rawDishQuery !== '' && str_contains($nameLower, ' ' . $rawDishQuery . ' ')) {
                $score += 18;
            } elseif ($phrase !== '' && str_contains($nameNorm, ' ' . $phrase . ' ')) {
                if (!$userTypedDiacritics || !$rawTokens || array_reduce($rawTokens, static fn($carry, $rt) => $carry || str_contains($nameLower, ' ' . $rt . ' '), false)) {
                    $score += 12;
                }
            }

            $matchedTokens = 0;
            foreach ($tokens as $idx => $tk) {
                $rawTk = $rawTokens[$idx] ?? null;
                if ($userTypedDiacritics && $rawTk !== null) {
                    if (str_contains($nameLower, ' ' . $rawTk . ' ')) {
                        $score += 5;
                        $matchedTokens++;
                    }
                } else {
                    if (str_contains($nameNorm, ' ' . $tk . ' ')) {
                        $score += 4;
                        $matchedTokens++;
                    } elseif (str_contains($catNorm, ' ' . $tk . ' ')) {
                        $score += 2;
                        $matchedTokens++;
                    }
                }
            }

            if (!$fullDishMentioned && $matchedTokens < $minRequiredTokens) {
                continue;
            }
            if ($matchedTokens === count($tokens)) {
                $score += 6;
            }
            if ($score > 0) {
                $row['_matchScore'] = $score;
                $scored[] = $row;
            }
        }

        usort($scored, static function (array $a, array $b): int {
            if ($b['_matchScore'] !== $a['_matchScore']) {
                return $b['_matchScore'] <=> $a['_matchScore'];
            }
            return (float)$a['price'] <=> (float)$b['price'];
        });

        return array_slice($scored, 0, 6);
    }

    /** Resolve pronoun ("anh", "chị", "mình") consistently across turns. */
    private static function pronoun(string $message, array $history, array $summary = []): string {
        $detected = ConversationContext::detectPronoun($message, $history, $summary);
        return in_array($detected, ['anh', 'chị', 'bạn'], true) ? $detected : 'mình';
    }

    /** Format natural checkout summary message grounded 100% in Backend Quote. */
    public static function formatQuoteMessage(array $quoteBundle, string $pronoun = 'mình', array $extraNotices = []): string {
        $quote = $quoteBundle['quote'] ?? null;
        if ($quote === null) {
            return (string)($quoteBundle['message'] ?? 'Đơn nháp đang được chuẩn bị.');
        }

        $lines = [];
        foreach ($extraNotices as $notice) {
            if ($notice !== '') $lines[] = $notice;
        }
        foreach ($quote['warnings'] ?? [] as $w) {
            $lines[] = 'Lưu ý: ' . $w;
        }

        $itemParts = [];
        foreach ($quote['items'] as $it) {
            $desc = $it['quantity'] . 'x ' . $it['name'];
            $opts = array_column($it['selectedOptions'] ?? [], 'name');
            if ($opts) $desc .= ' (' . implode(', ', $opts) . ')';
            if (!empty($it['note'])) $desc .= ' [ghi chú: ' . $it['note'] . ']';
            $desc .= ' — ' . AiOrderDraftService::moneyVnd((int)$it['lineTotalVnd']);
            $itemParts[] = $desc;
        }

        $payLabel = ($quote['paymentMethod'] ?? 'COD') === 'PAYOS'
            ? 'Chuyển khoản QR PayOS'
            : 'Tiền mặt khi nhận (COD)';

        if (!$quoteBundle['readyToConfirm']) {
            $lines[] = 'Dạ em đã cập nhật đơn nháp tại ' . $quote['restaurantName'] . ': ' . implode('; ', $itemParts)
                . ' (tạm tính món ' . AiOrderDraftService::moneyVnd((int)$quote['subtotal']) . ').';
            $lines[] = (string)($quoteBundle['message'] ?? 'Mình bổ sung thông tin còn thiếu để chốt đơn nhé!');
            return implode(' ', $lines);
        }

        $voucherText = !empty($quote['appliedVoucher'])
            ? ('áp mã ' . $quote['appliedVoucher']['code'] . ' (-' . AiOrderDraftService::moneyVnd((int)$quote['discount']) . ')')
            : 'chưa áp voucher';

        $eta = $quote['estimatedDeliveryMinutes'] ?? [];
        if (!empty($quote['scheduledForDisplay'])) {
            $etaSentence = 'Lịch hẹn nhận hàng: ' . $quote['scheduledForDisplay'] . ' (trong khung mở cửa ' . ($quote['storeOpenHours'] ?? 'của quán') . ').';
        } else {
            $etaSentence = !empty($eta['available']) && !empty($eta['displayText'])
                ? ('Dự kiến giao ngay trong khoảng ' . $eta['displayText'] . ' (thời gian dự kiến, có thể thay đổi theo thực tế).')
                : 'Hiện chưa có đủ tọa độ để ước tính phút giao chính xác.';
        }

        $addr = $quote['address'];
        $addrLabel = ($addr['label'] ?? 'Địa chỉ') . ': ' . ($addr['addressLine'] ?? '');
        $totalFormatted = AiOrderDraftService::moneyVnd((int)$quote['total']);

        $lines[] = 'Dạ em lên báo giá đơn tại ' . $quote['restaurantName'] . ' cho ' . $pronoun . ' nha: '
            . implode('; ', $itemParts) . '. '
            . 'Tiền món ' . AiOrderDraftService::moneyVnd((int)$quote['subtotal'])
            . ', phí giao ' . AiOrderDraftService::moneyVnd((int)$quote['deliveryFee'])
            . ', ' . $voucherText
            . ' → Tổng thanh toán: ' . $totalFormatted . ' (' . $payLabel . '). '
            . 'Giao tới ' . $addrLabel . '. ' . $etaSentence;

        if (!empty($quote['upsellSuggestion']) && is_array($quote['upsellSuggestion'])) {
            $up = $quote['upsellSuggestion'];
            $lines[] = '💡 Mẹo tiết kiệm (tùy chọn): ' . ($up['reason'] ?? $up['headline'] ?? '')
                . ' ' . ucfirst($pronoun) . ' có thể bấm nút “+ Thêm ' . ($up['dishName'] ?? 'món kèm') . '” bên dưới nếu đồng ý thêm nha!';
        }

        $lines[] = ucfirst($pronoun) . ' bấm nút “Xác nhận đặt hàng — ' . $totalFormatted . '” (hoặc nhắn xác nhận) để em tạo đơn chính thức nhé!';

        return implode(' ', $lines);
    }

    /**
     * Main Orchestration Entry Point for both Conversational Turns and Structured UI Actions.
     */
    public static function handle(
        PDO $pdo,
        array $config,
        array $me,
        array $input,
        ?array $actionPayload = null,
        ?int $nowTs = null
    ): array {
        $now = $nowTs ?? time();
        $userId = AiOrderDraftService::assertCustomer($me);
        $convId = (string)($input['conversationId'] ?? ('conv-' . $userId));
        $message = trim((string)($input['message'] ?? ''));
        $history = is_array($input['history'] ?? null) ? $input['history'] : [];
        $summary = is_array($input['summary'] ?? null) ? $input['summary'] : [];
        $pronoun = self::pronoun($message, $history, $summary);
        $baseFee = (float)($config['delivery_fee'] ?? 15000);

        $existingDraft = AiOrderDraftService::getActiveDraft($pdo, $userId, $convId, $now);
        $hadPriorActiveQuote = $existingDraft !== null
            && $existingDraft['state'] === AiOrderDraftService::STATE_AWAITING_CONFIRMATION
            && !empty($existingDraft['activeQuoteId']);

        // 1. Structured UI Action Dispatch (Buttons / Quantity +/- / Chips / Confirm Button)
        if (is_array($actionPayload) && !empty($actionPayload['type'])) {
            return self::handleStructuredAction($pdo, $config, $me, $convId, $actionPayload, $pronoun, $baseFee, $now);
        }

        $notices = [];
        if (self::hasPromptInjection($message)) {
            $notices[] = 'Em luôn giữ đúng giá niêm yết của hệ thống và chỉ tạo đơn sau khi ' . $pronoun . ' xác nhận báo giá cuối cùng ạ.';
        }
        if (self::triesToSkipConfirmation($message)) {
            $notices[] = 'Để bảo vệ quyền lợi của ' . $pronoun . ', em chuẩn bị sẵn trọn bộ báo giá rồi ' . $pronoun . ' xác nhận bước cuối giúp em nhé!';
        }

        $q = FoodHubKnowledgeBase::expandVietnamese($message);

        // 2. Cancel Draft Intent (strictly separated from cancelling an already placed order!)
        if (preg_match('/\b(thoi khong dat nua|khong dat nua|thoi huy di|huy don nhap|bo don nhap|xoa don nhap|huy gio nhap)\b/u', $q)) {
            if ($existingDraft === null) {
                return self::buildResponse(
                    'Dạ hiện chưa có đơn nháp nào đang mở. Khi nào ' . $pronoun . ' muốn chọn món mới cứ nhắn em nha!',
                    'ordering',
                    null,
                    null,
                    [],
                    $baseFee
                );
            }
            $cancelled = AiOrderDraftService::cancelDraft($pdo, $userId, $existingDraft['draftId'], $now);
            return self::buildResponse(
                'Dạ em đã hủy đơn nháp đang chuẩn bị cho ' . $pronoun . ' rồi ạ (các đơn chính thức đã đặt trước đó hoàn toàn không bị ảnh hưởng). Khi nào muốn đặt món khác ' . $pronoun . ' cứ nhắn em nhé!',
                'ordering',
                $cancelled,
                null,
                [],
                $baseFee
            );
        }

        // 2b. One-Tap Reorder Intent ("Đặt lại y hệt đơn hôm qua", "Đặt lại đơn cũ")
        if (self::isReorderIntent($q)) {
            $dateHint = str_contains($q, 'hom qua') ? 'yesterday' : null;
            $reorderRes = AiOrderDraftService::reorderPastOrder($pdo, $userId, $convId, $dateHint, $now);
            if (!$reorderRes['ok']) {
                return self::buildResponse(
                    (string)$reorderRes['message'],
                    'ordering',
                    $existingDraft,
                    null,
                    [],
                    $baseFee,
                    ['reorderResult' => $reorderRes]
                );
            }
            $rDraft = $reorderRes['draft'];
            $scheduleCueOnReorder = self::extractScheduleCue($message, $now);
            if ($scheduleCueOnReorder !== null) {
                $sRes = AiOrderDraftService::setDraftSchedule($pdo, $userId, $rDraft['draftId'], $scheduleCueOnReorder['iso'], $now);
                if ($sRes['ok']) {
                    $rDraft = $sRes['draft'];
                    $notices[] = $sRes['scheduledForDisplay'] !== null
                        ? ('Đã đặt lịch hẹn giao: ' . $sRes['scheduledForDisplay'] . '.')
                        : 'Đã chuyển về Giao ngay.';
                } else {
                    $notices[] = (string)$sRes['message'];
                }
            }
            $reorderNotices = self::buildReorderNotices($reorderRes, $pronoun);
            $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $rDraft['draftId'], $now);
            return self::buildResponse(
                self::formatQuoteMessage($qb, $pronoun, array_merge($reorderNotices, $notices)),
                'ordering_quote',
                $qb['draft'],
                $qb,
                [],
                $baseFee,
                ['reorderResult' => $reorderRes]
            );
        }

        // 2c. Consent-Based Upsell Acceptance via Chat ("Thêm món kèm đó đi", "Ok thêm món gợi ý mở mã")
        if ($existingDraft !== null && !empty($existingDraft['activeQuoteId']) && self::isAcceptUpsellIntent($q)) {
            $uStmt = $pdo->prepare('SELECT quote_payload_json FROM ai_order_quotes WHERE quote_uid = ? AND user_id = ? LIMIT 1');
            $uStmt->execute([$existingDraft['activeQuoteId'], $userId]);
            $uJson = $uStmt->fetchColumn();
            $uPayload = is_string($uJson) ? json_decode($uJson, true) : null;
            $upsell = is_array($uPayload['upsellSuggestion'] ?? null) ? $uPayload['upsellSuggestion'] : null;
            if ($upsell !== null && !empty($upsell['dishId'])) {
                $upRes = AiOrderDraftService::acceptUpsell(
                    $pdo,
                    $userId,
                    $existingDraft['draftId'],
                    (int)$upsell['dishId'],
                    (string)($upsell['targetVoucherCode'] ?? ''),
                    $now
                );
                if ($upRes['ok']) {
                    $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $upRes['draft']['draftId'], $now);
                    $upNotice = 'Đã thêm 1x ' . ($upsell['dishName'] ?? 'món kèm') . ' và áp mã ' . ($upsell['targetVoucherCode'] ?? '') . ' theo sự đồng ý của ' . $pronoun . '!';
                    return self::buildResponse(
                        self::formatQuoteMessage($qb, $pronoun, array_merge($notices, [$upNotice])),
                        'ordering_quote',
                        $qb['draft'],
                        $qb,
                        [],
                        $baseFee
                    );
                }
            }
        }

        // 3. Final Order Confirmation via Chat Text (ONLY if a prior active quote already existed AND no modifications in this turn!)
        $scheduleCue = self::extractScheduleCue($message, $now);
        $isModifyingTurn = $scheduleCue !== null || (bool)preg_match('/\b(them|bot|doi sang|doi qua|giao ve|giao qua|ap ma|dung ma|bo ma|khong an|size|hen gio|giao luc)\b/u', $q);
        $isConfirmPhrase = (bool)preg_match(
            '/^(?:u|uh|ok|oke|vang|da|duoc|dong y)?\s*(?:xac nhan(?:\s+dat(?:\s+don|\s+hang)?)?|chot\s+don|chot\s+dat(?:\s+don)?|dat\s+don\s+nay|dat\s+di(?:\s+em)?|ok\s+dat(?:\s+di|\s+nha)?|u\s+dat\s+di)(?:\s+\d+(?:[.,]\d+)?\s*(?:k|nghin|ngan|d|dong|vnd))?[.! nha nhe a em oi]*$/u',
            trim($q)
        );

        if ($isConfirmPhrase && !$isModifyingTurn && !self::hasPromptInjection($message)) {
            if (!$hadPriorActiveQuote || $existingDraft === null || empty($existingDraft['activeQuoteId'])) {
                return self::buildResponse(
                    'Dạ em cần lên báo giá chi tiết gồm món ăn, địa chỉ và tổng tiền trước rồi mới xác nhận đặt đơn được ạ. ' . ucfirst($pronoun) . ' muốn đặt món gì nhắn em tìm ngay nha!',
                    'ordering',
                    $existingDraft,
                    null,
                    [],
                    $baseFee
                );
            }

            // Load active quote token from DB for this user's conversation
            $qRowStmt = $pdo->prepare('SELECT quote_uid, confirmation_token, total_vnd FROM ai_order_quotes WHERE quote_uid = ? AND user_id = ? LIMIT 1');
            $qRowStmt->execute([$existingDraft['activeQuoteId'], $userId]);
            $qRow = $qRowStmt->fetch(PDO::FETCH_ASSOC);
            if (!$qRow) {
                $fresh = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $existingDraft['draftId'], $now);
                return self::buildResponse(
                    self::formatQuoteMessage($fresh, $pronoun, ['Báo giá vừa được làm mới, ' . $pronoun . ' xác nhận lại giúp em nhé!']),
                    'ordering_quote',
                    $fresh['draft'],
                    $fresh,
                    [],
                    $baseFee
                );
            }

            $expectedTotal = null;
            if (preg_match('/(\d+(?:[.,]\d+)?)\s*(k|nghin|ngan|d|dong|vnd)\b/u', $q, $amtMatch)) {
                $num = (float)str_replace(',', '.', $amtMatch[1]);
                $unit = $amtMatch[2];
                $expectedTotal = in_array($unit, ['k', 'nghin', 'ngan'], true)
                    ? (int)round($num * 1000)
                    : (int)round($num);
            }

            $idemKey = 'chat-confirm-' . $userId . '-' . $qRow['quote_uid'];
            $submitRes = AiOrderDraftService::submitConfirmedOrder(
                $pdo,
                $config,
                $me,
                $existingDraft['draftId'],
                (string)$qRow['quote_uid'],
                (string)$qRow['confirmation_token'],
                $idemKey,
                $expectedTotal,
                $now
            );

            return self::formatSubmitOutcome($submitRes, $pronoun, $baseFee);
        }

        // Ensure we have an active draft if we are about to configure/add items
        $draft = $existingDraft ?? AiOrderDraftService::getOrCreateDraft($pdo, $userId, $convId, $now);

        // Apply side-entities present in the user's message (payment, voucher, address, notes)
        $paymentCue = self::extractPaymentCue($message);
        if ($paymentCue !== null) {
            if ($paymentCue['method'] === 'unsupported') {
                $notices[] = 'Hiện tại FoodHub chưa tích hợp ví ' . $paymentCue['raw'] . ' trực tiếp mà hỗ trợ Tiền mặt (COD) hoặc Chuyển khoản VietQR qua PayOS — em tạm để COD, nếu ' . $pronoun . ' muốn quét QR PayOS thì bấm chọn PayOS nhé!';
            } else {
                $draft = AiOrderDraftService::setDraftPaymentMethod($pdo, $userId, $draft['draftId'], $paymentCue['method'], $now);
            }
        }

        $voucherCue = self::extractVoucherCue($message);
        if ($voucherCue !== null) {
            if ($voucherCue['mode'] === 'none') {
                $draft = AiOrderDraftService::setDraftVoucher($pdo, $userId, $draft['draftId'], 'NONE', false, $now);
                $notices[] = 'Dạ em đã gỡ mã giảm giá để giữ lại voucher theo ý ' . $pronoun . ' ạ.';
            } elseif ($voucherCue['mode'] === 'code') {
                $draft = AiOrderDraftService::setDraftVoucher($pdo, $userId, $draft['draftId'], $voucherCue['code'], false, $now);
            } elseif ($voucherCue['mode'] === 'best' || $voucherCue['mode'] === 'check') {
                $draft = AiOrderDraftService::setDraftVoucher($pdo, $userId, $draft['draftId'], null, true, $now);
            }
        }

        $addressCue = self::extractAddressCue($message);
        if ($addressCue !== null) {
            $userAddrs = AiOrderDraftService::getUserAddresses($pdo, $userId);
            $matchedAddr = self::matchSavedAddress($userAddrs, $addressCue);
            if ($matchedAddr !== null) {
                $draft = AiOrderDraftService::setDraftAddress($pdo, $userId, $draft['draftId'], $matchedAddr['idInt'], $now);
                $notices[] = 'Đã chọn địa chỉ "' . $matchedAddr['label'] . '" (' . $matchedAddr['addressLine'] . ').';
            } else {
                $notices[] = 'Em chưa tìm thấy địa chỉ đã lưu khớp với "' . $addressCue . '", ' . $pronoun . ' chọn trong danh sách địa chỉ bên dưới giúp em nhé!';
            }
        }

        if ($scheduleCue !== null && $draft['items']) {
            $schedRes = AiOrderDraftService::setDraftSchedule($pdo, $userId, $draft['draftId'], $scheduleCue['iso'], $now);
            if ($schedRes['ok']) {
                $draft = $schedRes['draft'];
                $notices[] = $schedRes['scheduledForDisplay'] !== null
                    ? ('Đã hẹn lịch giao hàng: ' . $schedRes['scheduledForDisplay'] . '.')
                    : 'Đã chuyển đơn về chế độ Giao ngay.';
            } else {
                $notices[] = (string)$schedRes['message'];
            }
        }

        $customization = self::extractItemCustomization($message);
        $extractedQty = self::extractQuantity($message);

        // 4. Quantity / Note modification on existing draft item ("cho anh thêm 1 phần nữa", "bỏ hành giúp anh")
        if ($draft['items']) {
            $lastItem = end($draft['items']);
            if (preg_match('/\b(?:cho\s+(?:anh|chi|em|minh|tui)\s+)?them\s+(\d+|mot|hai|ba)\s*(?:phan|suat|ly|coc|to|bat|dia|o|cai)?\s*nua\b/u', $q)) {
                $delta = $extractedQty ?? 1;
                $newQty = ((int)$lastItem['quantity']) + $delta;
                $draft = AiOrderDraftService::modifyItemQuantity($pdo, $userId, $draft['draftId'], (string)$lastItem['lineId'], $newQty, $customization['note'] !== '' ? $customization['note'] : null, $now);
                $quoteBundle = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'], $now);
                return self::buildResponse(
                    self::formatQuoteMessage($quoteBundle, $pronoun, array_merge($notices, ['Đã tăng ' . $lastItem['name'] . ' lên ' . $newQty . ' phần.'])),
                    'ordering_quote',
                    $quoteBundle['draft'],
                    $quoteBundle,
                    [],
                    $baseFee
                );
            }

            if (preg_match('/\b(?:giam|bot)\s+(\d+|mot|hai)?\s*(?:phan|suat|ly|to)?\b/u', $q) && !preg_match('/ma giam|giam gia/', $q)) {
                $delta = $extractedQty ?? 1;
                $newQty = max(0, ((int)$lastItem['quantity']) - $delta);
                $draft = AiOrderDraftService::modifyItemQuantity($pdo, $userId, $draft['draftId'], (string)$lastItem['lineId'], $newQty, null, $now);
                $quoteBundle = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'], $now);
                return self::buildResponse(
                    self::formatQuoteMessage($quoteBundle, $pronoun, $notices),
                    'ordering_quote',
                    $quoteBundle['draft'],
                    $quoteBundle,
                    [],
                    $baseFee
                );
            }

            // Pure note / payment / address / voucher / schedule / ETA / summary update on existing draft without changing dish
            $isPureDraftQueryOrUpdate = (
                $paymentCue !== null || $voucherCue !== null || $addressCue !== null || $scheduleCue !== null
                || ($customization['note'] !== '' && !preg_match('/\b(dat|lay|chon|doi sang|mua)\b/u', $q))
                || preg_match('/\b(tong bao nhieu|vay tong|het bao nhieu|bao lau co do|bao lau toi|neu dat thi bao lau|xem lai don)\b/u', $q)
            ) && !preg_match('/\b(doi sang|khong an.*nua|dat them|goi them)\b/u', $q);

            if ($isPureDraftQueryOrUpdate) {
                if ($customization['note'] !== '') {
                    $mergedNote = trim($lastItem['note'] !== '' ? ($lastItem['note'] . ', ' . $customization['note']) : $customization['note']);
                    $draft = AiOrderDraftService::modifyItemQuantity($pdo, $userId, $draft['draftId'], (string)$lastItem['lineId'], (int)$lastItem['quantity'], $mergedNote, $now);
                    $notices[] = 'Em đã ghi chú "' . $customization['note'] . '" vào món ' . $lastItem['name'] . ' (việc điều chỉnh cụ thể sẽ tùy bếp nhà hàng hỗ trợ ạ).';
                }
                $quoteBundle = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'], $now);

                if ($voucherCue !== null && $voucherCue['mode'] === 'check') {
                    $bestV = $quoteBundle['vouchers']['best'] ?? null;
                    if ($bestV !== null) {
                        $notices[] = 'Ưu đãi có lợi nhất cho đơn này là mã ' . $bestV['code'] . ' (giảm thực tế ' . AiOrderDraftService::moneyVnd((int)$bestV['actualDiscountVnd']) . ').';
                    } else {
                        $notices[] = 'Dạ hiện chưa có voucher nào đủ điều kiện áp dụng cho đơn này ạ.';
                    }
                }
                return self::buildResponse(
                    self::formatQuoteMessage($quoteBundle, $pronoun, $notices),
                    'ordering_quote',
                    $quoteBundle['draft'],
                    $quoteBundle,
                    [],
                    $baseFee
                );
            }
        }

        // 5. Resolve Dish Reference from History ("lấy món hồi nãy", "món đầu tiên", "món thứ 2")
        $replaceExistingItems = (bool)preg_match('/\b(thoi.*khong an.*nua|doi sang|doi qua|thay bang)\b/u', $q);
        $lastTurn = !empty($history) ? end($history) : null;
        $priorDishIds = is_array($lastTurn) ? array_values($lastTurn['dishIds'] ?? []) : [];

        $referencedDishId = null;
        if ($priorDishIds) {
            if (preg_match('/\b(mon dau tien|mon thu nhat|mon so 1|cai dau tien|phan dau)\b/u', $q)) {
                $referencedDishId = (int)$priorDishIds[0];
            } elseif (isset($priorDishIds[1]) && preg_match('/\b(mon thu hai|mon thu 2|mon so 2|cai thu 2)\b/u', $q)) {
                $referencedDishId = (int)$priorDishIds[1];
            } elseif (isset($priorDishIds[2]) && preg_match('/\b(mon thu ba|mon thu 3|mon so 3|cai thu 3)\b/u', $q)) {
                $referencedDishId = (int)$priorDishIds[2];
            } elseif (count($priorDishIds) === 1 && preg_match('/\b(mon hoi nay|mon vua nay|mon do|mon nay|lay luon)\b/u', $q)) {
                $referencedDishId = (int)$priorDishIds[0];
            }
        }

        if ($referencedDishId !== null && $referencedDishId > 0) {
            return self::addResolvedDishAndRespond(
                $pdo,
                $config,
                $userId,
                $draft,
                $referencedDishId,
                $extractedQty ?? 1,
                $customization + ['scheduledFor' => $scheduleCue['iso'] ?? null, 'hasScheduleCue' => $scheduleCue !== null],
                $replaceExistingItems,
                $pronoun,
                $notices,
                $baseFee,
                $now
            );
        }

        // 6. Food Search & Disambiguation from Natural Text
        $stores = MenuKnowledge::stores($pdo, 10000000);
        $mentionedStores = MenuKnowledge::mentionedStores($message, $stores);
        $scopedStoreId = count($mentionedStores) === 1 ? (string)$mentionedStores[0]['id'] : null;

        $foodQuery = self::cleanFoodQuery($message);
        foreach ($mentionedStores as $st) {
            $foodQuery = trim(str_ireplace(MealAdvisor::folded($st['name']), ' ', $foodQuery));
        }

        // Check if the user explicitly requested a dish that is currently out of stock!
        $oosExact = self::findUnavailableDishByName($pdo, $foodQuery);
        if ($oosExact !== null) {
            $alts = MenuKnowledge::candidates($pdo, 10000000, false, $oosExact['category'] ?: $foodQuery, [], (string)$oosExact['store_id']);
            $recs = self::toRecommendations(array_slice($alts, 0, 3), $baseFee);
            return self::buildResponse(
                'Dạ món ' . $oosExact['name'] . ' tại ' . $oosExact['store_name'] . ' hiện đang tạm hết hàng nên em chưa tạo đơn được ạ. ' . ucfirst($pronoun) . ' tham khảo các món thay thế đang có sẵn bên dưới nha!',
                'ordering_out_of_stock',
                $draft,
                null,
                $recs,
                $baseFee
            );
        }

        $matches = self::searchOrderableFoods($pdo, $foodQuery, $scopedStoreId, 10000000.0, $message);

        if (!$matches) {
            // Check if the user asked for an out-of-stock dish by name!
            $oosCheck = self::findUnavailableDishByName($pdo, $foodQuery);
            if ($oosCheck !== null) {
                $alts = MenuKnowledge::candidates($pdo, 10000000, false, $oosCheck['category'] ?: $foodQuery, [], (string)$oosCheck['store_id']);
                $recs = self::toRecommendations(array_slice($alts, 0, 3), $baseFee);
                return self::buildResponse(
                    'Dạ món ' . $oosCheck['name'] . ' tại ' . $oosCheck['store_name'] . ' hiện đang tạm hết hàng nên em chưa tạo đơn được ạ. ' . ucfirst($pronoun) . ' tham khảo các món thay thế đang có sẵn bên dưới nha!',
                    'ordering_out_of_stock',
                    $draft,
                    null,
                    $recs,
                    $baseFee
                );
            }

            $similar = MenuKnowledge::candidates($pdo, 10000000, false, $message, [], $scopedStoreId);
            $recs = self::toRecommendations(array_slice($similar, 0, 3), $baseFee);
            return self::buildResponse(
                'Dạ hiện em chưa tìm thấy đúng món "' . ($foodQuery ?: $message) . '" trong danh sách đang mở bán. ' . ucfirst($pronoun) . ' muốn chọn một trong các món gần giống bên dưới không ạ?',
                'ordering_selection',
                $draft,
                null,
                $recs,
                $baseFee
            );
        }

        // Check if top match is an exact unambiguous hit (either 1 result, or exact full dish name match!)
        $exactMatch = null;
        $normQuery = MenuKnowledge::text($foodQuery);
        if (count($matches) === 1) {
            $exactMatch = $matches[0];
        } else {
            foreach ($matches as $cand) {
                if (MenuKnowledge::text((string)$cand['name']) === $normQuery) {
                    $exactMatch = $cand;
                    break;
                }
            }
            // Also treat as unambiguous if top match score strictly dominates #2 by >= 8 points
            if ($exactMatch === null && (($matches[0]['_matchScore'] ?? 0) - ($matches[1]['_matchScore'] ?? 0) >= 8)) {
                $exactMatch = $matches[0];
            }
        }

        if ($exactMatch !== null) {
            return self::addResolvedDishAndRespond(
                $pdo,
                $config,
                $userId,
                $draft,
                (int)$exactMatch['id'],
                $extractedQty ?? 1,
                $customization + ['scheduledFor' => $scheduleCue['iso'] ?? null, 'hasScheduleCue' => $scheduleCue !== null],
                $replaceExistingItems,
                $pronoun,
                $notices,
                $baseFee,
                $now
            );
        }

        // Multiple plausible dishes -> Ask user to pick (never force a single dish when ambiguous!)
        $topChoices = array_slice($matches, 0, 3);
        $pdo->prepare("UPDATE ai_order_drafts SET state = ?, updated_at = ? WHERE id = ?")
            ->execute([AiOrderDraftService::STATE_FOOD_SELECTION, AiOrderDraftService::nowStr($now), $draft['idInt']]);
        $draft['state'] = AiOrderDraftService::STATE_FOOD_SELECTION;

        $choiceLabels = array_map(
            static fn(array $r) => $r['name'] . ' (' . AiOrderDraftService::moneyVnd((int)round((float)$r['price'])) . ' — ' . $r['restaurant_name'] . ')',
            $topChoices
        );
        $qtyHint = $extractedQty !== null && $extractedQty > 1 ? (' (' . $extractedQty . ' phần)') : '';
        $recs = self::toRecommendations($topChoices, $baseFee);

        return self::buildResponse(
            'Dạ FoodHub đang có mấy món phù hợp cho ' . $pronoun . ' lựa chọn' . $qtyHint . ' nè: '
            . implode('; ', $choiceLabels) . '. '
            . ucfirst($pronoun) . ' bấm nút “Chọn đặt” ở món ưng ý (hoặc nhắn tên món / món số 1, 2, 3) để em lên báo giá nhé!',
            'ordering_selection',
            $draft,
            null,
            $recs,
            $baseFee
        );
    }

    private static function addResolvedDishAndRespond(
        PDO $pdo,
        array $config,
        int $userId,
        array $draft,
        int $dishId,
        int $quantity,
        array $customization,
        bool $replaceExistingItems,
        string $pronoun,
        array $notices,
        float $baseFee,
        int $now
    ): array {
        if ($replaceExistingItems && $draft['items']) {
            foreach ($draft['items'] as $oldItem) {
                $draft = AiOrderDraftService::modifyItemQuantity($pdo, $userId, $draft['draftId'], (string)$oldItem['lineId'], 0, null, $now);
            }
            $notices[] = 'Đã bỏ món cũ và đổi sang món mới theo ý ' . $pronoun . '.';
        }

        $addRes = AiOrderDraftService::addOrUpdateItem(
            $pdo,
            $userId,
            $draft['draftId'],
            $dishId,
            $quantity,
            [],
            $customization['optionNames'] ?? [],
            $customization['note'] ?? '',
            $replaceExistingItems,
            false,
            $now
        );

        if (!$addRes['ok']) {
            if (($addRes['code'] ?? '') === 'different_store') {
                $quoteBundle = $draft['items']
                    ? AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'], $now)
                    : null;
                $targetRec = isset($addRes['targetDish']) ? [[
                    'dish' => [
                        'id' => $addRes['targetDish']['id'],
                        'name' => $addRes['targetDish']['name'],
                        'price' => (float)$addRes['targetDish']['priceVnd'],
                        'imageUrl' => $addRes['targetDish']['imageUrl'],
                        'description' => $addRes['targetDish']['description'],
                        'category' => $addRes['targetDish']['category'],
                        'available' => true,
                        'restaurantId' => $addRes['targetDish']['storeId'],
                    ],
                    'restaurantName' => $addRes['targetDish']['storeName'],
                    'reason' => 'Khác cửa hàng với đơn nháp hiện tại — bấm Đổi quán nếu muốn tạo đơn mới tại đây.',
                    'estimatedTotal' => (float)$addRes['targetDish']['priceVnd'] + $baseFee,
                ]] : [];
                return self::buildResponse(
                    (string)$addRes['message'],
                    'ordering_store_conflict',
                    $addRes['draft'],
                    $quoteBundle,
                    $targetRec,
                    $baseFee,
                    ['storeConflict' => [
                        'currentStoreName' => $addRes['currentStoreName'],
                        'targetStoreName' => $addRes['targetStoreName'],
                        'targetDishId' => $addRes['targetDish']['id'] ?? '',
                        'quantity' => $quantity,
                    ]]
                );
            }

            if (($addRes['code'] ?? '') === 'missing_required_options') {
                return self::buildResponse(
                    (string)$addRes['message'],
                    'ordering_options_required',
                    $addRes['draft'],
                    null,
                    [],
                    $baseFee,
                    ['requiredOptionGroups' => $addRes['missingGroups'] ?? [], 'pendingDish' => $addRes['dish'] ?? null]
                );
            }

            $alts = self::toRecommendations($addRes['alternatives'] ?? [], $baseFee);
            return self::buildResponse((string)$addRes['message'], 'ordering_out_of_stock', $addRes['draft'], null, $alts, $baseFee);
        }

        if (!empty($addRes['unsupportedOptions'])) {
            $notices[] = 'Món này trên thực đơn hiện chưa có tùy chọn "' . implode(', ', $addRes['unsupportedOptions']) . '" riêng nên em giữ cỡ tiêu chuẩn của quán nha.';
        }
        if (($customization['note'] ?? '') !== '') {
            $notices[] = 'Đã ghi chú "' . $customization['note'] . '" cho nhà hàng.';
        }

        $targetDraftId = $addRes['draft']['draftId'];
        if (!empty($customization['hasScheduleCue'])) {
            $sRes = AiOrderDraftService::setDraftSchedule($pdo, $userId, $targetDraftId, $customization['scheduledFor'] ?? null, $now);
            if ($sRes['ok']) {
                $notices[] = $sRes['scheduledForDisplay'] !== null
                    ? ('Đã hẹn lịch giao hàng: ' . $sRes['scheduledForDisplay'] . '.')
                    : 'Đã chuyển về Giao ngay.';
            } else {
                $notices[] = (string)$sRes['message'];
            }
        }

        $quoteBundle = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $targetDraftId, $now);
        return self::buildResponse(
            self::formatQuoteMessage($quoteBundle, $pronoun, $notices),
            'ordering_quote',
            $quoteBundle['draft'],
            $quoteBundle,
            [],
            $baseFee
        );
    }

    /** Build concise Vietnamese explanation of live price changes & skipped items during One-Tap Reorder. */
    private static function buildReorderNotices(array $reorderRes, string $pronoun): array {
        $out = [];
        $code = $reorderRes['pastOrderCode'] ?? '';
        if (!empty($reorderRes['usedFallbackLatest'])) {
            $out[] = 'Hôm qua ' . $pronoun . ' chưa có đơn hoàn tất nên em nạp nhanh các món từ đơn gần nhất #' . $code . ' tại ' . ($reorderRes['storeName'] ?? '') . ' ạ.';
        } else {
            $out[] = 'Em đã nạp lại các món từ đơn #' . $code . ' tại ' . ($reorderRes['storeName'] ?? '') . ' vào đơn nháp cho ' . $pronoun . '.';
        }
        if (!empty($reorderRes['priceChanges']) && is_array($reorderRes['priceChanges'])) {
            $diffs = [];
            foreach ($reorderRes['priceChanges'] as $pc) {
                $diffs[] = $pc['dishName'] . ': ' . AiOrderDraftService::moneyVnd((int)$pc['oldUnitPriceVnd']) . ' → ' . AiOrderDraftService::moneyVnd((int)$pc['newUnitPriceVnd']);
            }
            $out[] = 'Cập nhật giá hiện hành so với lúc đặt cũ: ' . implode('; ', $diffs) . '.';
        } else {
            $out[] = 'Giá hiện hành của các món giữ nguyên như đơn cũ.';
        }
        if (!empty($reorderRes['unavailableItems']) && is_array($reorderRes['unavailableItems'])) {
            $out[] = 'Lưu ý món tạm hết hàng đã được lược ra: ' . implode(', ', $reorderRes['unavailableItems']) . '.';
        }
        return $out;
    }

    /** Handle explicit UI actions sent from Android Compose controls. */
    private static function handleStructuredAction(
        PDO $pdo,
        array $config,
        array $me,
        string $convId,
        array $action,
        string $pronoun,
        float $baseFee,
        int $now
    ): array {
        $userId = (int)$me['id'];
        $type = (string)$action['type'];
        $draft = AiOrderDraftService::getOrCreateDraft($pdo, $userId, $convId, $now);

        return match ($type) {
            'add_item', 'replace_store_with_item' => self::addResolvedDishAndRespond(
                $pdo,
                $config,
                $userId,
                $draft,
                (int)($action['dishId'] ?? 0),
                max(1, (int)($action['quantity'] ?? 1)),
                [
                    'optionNames' => is_array($action['optionNames'] ?? null) ? $action['optionNames'] : [],
                    'note' => (string)($action['note'] ?? ''),
                ],
                $type === 'replace_store_with_item',
                $pronoun,
                [],
                $baseFee,
                $now
            ),
            'update_quantity' => (static function () use ($pdo, $config, $userId, $draft, $action, $pronoun, $baseFee, $now): array {
                $updated = AiOrderDraftService::modifyItemQuantity(
                    $pdo,
                    $userId,
                    $draft['draftId'],
                    (string)($action['lineId'] ?? $action['dishId'] ?? ''),
                    (int)($action['quantity'] ?? 1),
                    isset($action['note']) ? (string)$action['note'] : null,
                    $now
                );
                if (!$updated['items']) {
                    return self::buildResponse('Dạ đơn nháp hiện đã trống. ' . ucfirst($pronoun) . ' chọn món mới giúp em nhé!', 'ordering', $updated, null, [], $baseFee);
                }
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $updated['draftId'], $now);
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun), 'ordering_quote', $qb['draft'], $qb, [], $baseFee);
            })(),
            'select_address' => (static function () use ($pdo, $config, $userId, $draft, $action, $pronoun, $baseFee, $now): array {
                $updated = AiOrderDraftService::setDraftAddress($pdo, $userId, $draft['draftId'], (int)($action['addressId'] ?? 0), $now);
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $updated['draftId'], $now);
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun, ['Đã cập nhật địa chỉ giao hàng và tính lại phí giao + ETA.']), 'ordering_quote', $qb['draft'], $qb, [], $baseFee);
            })(),
            'select_voucher' => (static function () use ($pdo, $config, $userId, $draft, $action, $pronoun, $baseFee, $now): array {
                $code = isset($action['voucherCode']) ? (string)$action['voucherCode'] : null;
                $auto = (bool)($action['autoBest'] ?? false);
                $updated = AiOrderDraftService::setDraftVoucher($pdo, $userId, $draft['draftId'], $code, $auto, $now);
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $updated['draftId'], $now);
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun), 'ordering_quote', $qb['draft'], $qb, [], $baseFee);
            })(),
            'select_payment' => (static function () use ($pdo, $config, $userId, $draft, $action, $pronoun, $baseFee, $now): array {
                $updated = AiOrderDraftService::setDraftPaymentMethod($pdo, $userId, $draft['draftId'], (string)($action['paymentMethod'] ?? 'cash'), $now);
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $updated['draftId'], $now);
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun), 'ordering_quote', $qb['draft'], $qb, [], $baseFee);
            })(),
            'reorder_last' => (static function () use ($pdo, $config, $userId, $convId, $action, $draft, $pronoun, $baseFee, $now): array {
                $dateHint = isset($action['dateHint']) ? (string)$action['dateHint'] : null;
                $rRes = AiOrderDraftService::reorderPastOrder($pdo, $userId, $convId, $dateHint, $now);
                if (!$rRes['ok']) {
                    return self::buildResponse((string)$rRes['message'], 'ordering', $draft['items'] ? $draft : null, null, [], $baseFee, ['reorderResult' => $rRes]);
                }
                $rNotices = self::buildReorderNotices($rRes, $pronoun);
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $rRes['draft']['draftId'], $now);
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun, $rNotices), 'ordering_quote', $qb['draft'], $qb, [], $baseFee, ['reorderResult' => $rRes]);
            })(),
            'accept_upsell' => (static function () use ($pdo, $config, $userId, $draft, $action, $pronoun, $baseFee, $now): array {
                $dishId = (int)($action['dishId'] ?? 0);
                $vCode = (string)($action['voucherCode'] ?? '');
                $upRes = AiOrderDraftService::acceptUpsell($pdo, $userId, $draft['draftId'], $dishId, $vCode, $now);
                if (!$upRes['ok']) {
                    $qb = $draft['items'] ? AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'], $now) : null;
                    return self::buildResponse((string)($upRes['message'] ?? 'Chưa thể thêm món kèm lúc này.'), $qb ? 'ordering_quote' : 'ordering_out_of_stock', $upRes['draft'] ?? $draft, $qb, [], $baseFee);
                }
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $upRes['draft']['draftId'], $now);
                $dName = $upRes['dish']['name'] ?? 'món kèm';
                $notice = 'Đã thêm 1x ' . $dName . ($vCode !== '' ? (' và áp mã ' . strtoupper($vCode)) : '') . ' theo lựa chọn của ' . $pronoun . '!';
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun, [$notice]), 'ordering_quote', $qb['draft'], $qb, [], $baseFee);
            })(),
            'set_schedule' => (static function () use ($pdo, $config, $userId, $draft, $action, $pronoun, $baseFee, $now): array {
                $schedIso = array_key_exists('scheduledFor', $action) && $action['scheduledFor'] !== null
                    ? (string)$action['scheduledFor']
                    : null;
                $sRes = AiOrderDraftService::setDraftSchedule($pdo, $userId, $draft['draftId'], $schedIso, $now);
                $targetDraftId = ($sRes['draft']['draftId'] ?? $draft['draftId']);
                $qb = $draft['items'] ? AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $targetDraftId, $now) : null;
                $notice = $sRes['ok']
                    ? ($sRes['scheduledForDisplay'] !== null ? ('Đã cập nhật lịch hẹn giao: ' . $sRes['scheduledForDisplay'] . '.') : 'Đã chuyển đơn về chế độ Giao ngay.')
                    : (string)$sRes['message'];
                return self::buildResponse(
                    $qb !== null ? self::formatQuoteMessage($qb, $pronoun, [$notice]) : $notice,
                    $qb !== null ? 'ordering_quote' : 'ordering',
                    $qb['draft'] ?? $sRes['draft'] ?? $draft,
                    $qb,
                    [],
                    $baseFee
                );
            })(),
            'refresh_quote' => (static function () use ($pdo, $config, $userId, $draft, $pronoun, $baseFee, $now): array {
                $qb = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'], $now);
                return self::buildResponse(self::formatQuoteMessage($qb, $pronoun), 'ordering_quote', $qb['draft'], $qb, [], $baseFee);
            })(),
            'cancel_draft' => (static function () use ($pdo, $userId, $draft, $pronoun, $baseFee, $now): array {
                $cancelled = AiOrderDraftService::cancelDraft($pdo, $userId, $draft['draftId'], $now);
                return self::buildResponse('Dạ em đã hủy đơn nháp theo yêu cầu của ' . $pronoun . ' rồi ạ.', 'ordering', $cancelled, null, [], $baseFee);
            })(),
            'confirm_order' => (static function () use ($pdo, $config, $me, $action, $draft, $pronoun, $baseFee, $now): array {
                $draftUid = (string)($action['draftId'] ?? $draft['draftId']);
                $quoteUid = (string)($action['quoteId'] ?? '');
                $token = (string)($action['confirmationToken'] ?? '');
                $idem = (string)($action['idempotencyKey'] ?? '');
                $expectedTotal = isset($action['expectedTotalVnd']) && is_numeric($action['expectedTotalVnd'])
                    ? (int)$action['expectedTotalVnd']
                    : null;
                $res = AiOrderDraftService::submitConfirmedOrder($pdo, $config, $me, $draftUid, $quoteUid, $token, $idem, $expectedTotal, $now);
                return self::formatSubmitOutcome($res, $pronoun, $baseFee);
            })(),
            default => throw new MealAdvisorException(422, 'Thao tác đặt hàng không hợp lệ.'),
        };
    }

    private static function formatSubmitOutcome(array $res, string $pronoun, float $baseFee): array {
        if (!empty($res['ok']) && !empty($res['order'])) {
            $ord = $res['order'];
            $isPayos = !empty($ord['requiresOnlinePayment']);
            $schedSuffix = !empty($ord['scheduledForDisplay']) ? (', hẹn giao ' . $ord['scheduledForDisplay']) : '';
            $msg = $isPayos
                ? ('Dạ đơn hàng #' . $ord['code'] . ' (' . AiOrderDraftService::moneyVnd((int)$ord['totalVnd']) . $schedSuffix . ') đã được tạo ở trạng thái Chờ thanh toán PayOS! 💙 ' . ucfirst($pronoun) . ' bấm nút “Thanh toán QR PayOS ngay” bên dưới để hoàn tất thanh toán nhé.')
                : ('Dạ đơn hàng của ' . $pronoun . ' đã được tạo thành công rồi ạ! 💙 Mã đơn là #' . $ord['code'] . ' (tổng ' . AiOrderDraftService::moneyVnd((int)$ord['totalVnd']) . ', thanh toán tiền mặt khi nhận' . $schedSuffix . '). ' . ucfirst($pronoun) . ' có thể bấm Theo dõi đơn hàng ngay bên dưới nha!');

            return [
                'message' => $msg,
                'kind' => 'order_created',
                'source' => 'ordering_agent',
                'deliveryFee' => $baseFee,
                'recommendations' => [],
                'orderDraft' => null,
                'orderQuote' => null,
                'createdOrder' => $ord,
                'idempotentReplay' => (bool)($res['idempotentReplay'] ?? false),
            ];
        }

        // Price changed / quote expired / voucher exhausted / out of stock -> return updated state without creating order!
        $freshQuote = $res['freshQuote'] ?? null;
        $draft = $res['draft'] ?? null;
        $alts = self::toRecommendations($res['alternatives'] ?? [], $baseFee);
        return self::buildResponse(
            (string)($res['message'] ?? 'Chưa thể xác nhận đơn hàng, vui lòng kiểm tra lại báo giá.'),
            $freshQuote !== null ? 'ordering_quote' : 'ordering_error',
            $draft,
            $freshQuote !== null ? ['readyToConfirm' => true, 'quote' => $freshQuote, 'addresses' => [], 'vouchers' => []] : null,
            $alts,
            $baseFee,
            ['submitErrorCode' => $res['code'] ?? 'validation_failed']
        );
    }

    private static function findUnavailableDishByName(PDO $pdo, string $foodQuery): ?array {
        $normQuery = MenuKnowledge::text($foodQuery);
        if ($normQuery === '') return null;
        $stmt = $pdo->query(
            'SELECT d.id, d.store_id, d.name, d.is_available, d.stock_quantity, mc.name AS category, s.name AS store_name
             FROM dishes d
             JOIN stores s ON s.id = d.store_id
             LEFT JOIN menu_categories mc ON mc.id = d.menu_category_id
             WHERE d.deleted_at IS NULL
               AND (d.is_available = 0 OR (d.stock_quantity IS NOT NULL AND d.stock_quantity <= 0))
             LIMIT 200'
        );
        if (!$stmt) return null;
        foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $normName = MenuKnowledge::text((string)$row['name']);
            if ($normName !== '' && (str_contains($normName, $normQuery) || str_contains($normQuery, $normName))) {
                return $row;
            }
        }
        return null;
    }

    private static function toRecommendations(array $rows, float $fee): array {
        if (!$rows) return [];
        $selections = [];
        foreach ($rows as $r) {
            $selections[] = [
                'id' => (string)$r['id'],
                'reason' => 'Đang mở bán tại ' . ($r['restaurant_name'] ?? 'nhà hàng') . ' — có thể chọn đặt ngay.',
            ];
        }
        return MealAdvisor::reconcile($selections, $rows, 10000000.0, $fee, 3);
    }

    private static function buildResponse(
        string $message,
        string $kind,
        ?array $draft,
        ?array $quoteBundle,
        array $recommendations,
        float $baseFee,
        array $extra = []
    ): array {
        return array_merge([
            'message' => $message,
            'kind' => $kind,
            'source' => 'ordering_agent',
            'deliveryFee' => $quoteBundle['quote']['deliveryFee'] ?? $baseFee,
            'recommendations' => $recommendations,
            'orderDraft' => $draft,
            'orderQuote' => $quoteBundle['quote'] ?? null,
            'availableAddresses' => $quoteBundle['addresses'] ?? [],
            'availableVouchers' => $quoteBundle['vouchers']['eligible'] ?? [],
            'bestVoucher' => $quoteBundle['vouchers']['best'] ?? null,
            'readyToConfirm' => (bool)($quoteBundle['readyToConfirm'] ?? false),
            'blockingReason' => $quoteBundle['blockingReason'] ?? null,
        ], $extra);
    }
}
