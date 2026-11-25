<?php
declare(strict_types=1);

/**
 * Server-owned Conversational Ordering State Machine, Draft Store, Voucher Optimizer,
 * Verifiable Quote Engine (integer VND), and Idempotent Transaction-Safe Order Submitter.
 *
 * Strictly enforces:
 * - Customer ownership & role isolation
 * - Single-restaurant-per-order constraint
 * - Real DB prices, options, addresses, vouchers, distance & ETA
 * - Mandatory final confirmation bound to a non-expired quote + confirmationToken
 * - Idempotency & live price/voucher/stock revalidation inside a DB transaction
 */
final class AiOrderDraftService {
    // Primary Ordering State Machine
    public const STATE_IDLE = 'IDLE';
    public const STATE_INTENT_DETECTED = 'INTENT_DETECTED';
    public const STATE_SEARCHING_FOOD = 'SEARCHING_FOOD';
    public const STATE_FOOD_SELECTION = 'FOOD_SELECTION';
    public const STATE_ITEM_CONFIGURATION = 'ITEM_CONFIGURATION';
    public const STATE_DELIVERY_ADDRESS = 'DELIVERY_ADDRESS';
    public const STATE_VOUCHER_SELECTION = 'VOUCHER_SELECTION';
    public const STATE_PAYMENT_SELECTION = 'PAYMENT_SELECTION';
    public const STATE_CHECKOUT_REVIEW = 'CHECKOUT_REVIEW';
    public const STATE_AWAITING_CONFIRMATION = 'AWAITING_FINAL_CONFIRMATION';
    public const STATE_SUBMITTING_ORDER = 'SUBMITTING_ORDER';
    public const STATE_ORDER_CREATED = 'ORDER_CREATED';

    // Alternate / Exception States
    public const STATE_CANCELLED = 'CANCELLED';
    public const STATE_EXPIRED = 'EXPIRED';
    public const STATE_VALIDATION_FAILED = 'VALIDATION_FAILED';
    public const STATE_PAYMENT_PENDING = 'PAYMENT_PENDING';
    public const STATE_PAYMENT_FAILED = 'PAYMENT_FAILED';
    public const STATE_OUT_OF_STOCK = 'OUT_OF_STOCK';
    public const STATE_NEEDS_CLARIFICATION = 'NEEDS_USER_CLARIFICATION';
    public const STATE_HANDOFF_REQUIRED = 'HANDOFF_REQUIRED';

    public const DRAFT_TTL_SECONDS = 3600; // 60 minutes
    public const QUOTE_TTL_SECONDS = 600;  // 10 minutes
    public const MAX_DELIVERY_KM = 25.0;

    private static ?int $checkedPdoId = null;

    /** Ensure AI draft tables exist in SQLite test DBs; on MySQL/TiDB create safely if permitted. */
    public static function ensureSchema(PDO $pdo): void {
        $pdoId = spl_object_id($pdo);
        if (self::$checkedPdoId === $pdoId) {
            return;
        }
        $driver = (string)$pdo->getAttribute(PDO::ATTR_DRIVER_NAME);
        if ($driver === 'sqlite') {
            $pdo->exec("CREATE TABLE IF NOT EXISTS ai_order_drafts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                draft_uid TEXT NOT NULL UNIQUE,
                user_id INTEGER NOT NULL,
                conversation_id TEXT NOT NULL,
                store_id INTEGER NULL,
                state TEXT NOT NULL DEFAULT 'IDLE',
                items_json TEXT NOT NULL,
                address_id INTEGER NULL,
                voucher_code TEXT NULL,
                voucher_auto INTEGER NOT NULL DEFAULT 1,
                payment_method TEXT NULL,
                customer_note TEXT NULL,
                scheduled_for TEXT NULL,
                reordered_from_order_id INTEGER NULL,
                version INTEGER NOT NULL DEFAULT 1,
                active_quote_id TEXT NULL,
                submitted_order_id INTEGER NULL,
                expires_at TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )");
            $pdo->exec("CREATE TABLE IF NOT EXISTS ai_order_quotes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                quote_uid TEXT NOT NULL UNIQUE,
                draft_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                draft_version INTEGER NOT NULL,
                store_id INTEGER NOT NULL,
                address_id INTEGER NOT NULL,
                voucher_id INTEGER NULL,
                voucher_code TEXT NULL,
                payment_method TEXT NOT NULL,
                scheduled_for TEXT NULL,
                subtotal_vnd INTEGER NOT NULL,
                delivery_fee_vnd INTEGER NOT NULL,
                discount_vnd INTEGER NOT NULL DEFAULT 0,
                delivery_discount_vnd INTEGER NOT NULL DEFAULT 0,
                service_fee_vnd INTEGER NOT NULL DEFAULT 0,
                tax_vnd INTEGER NOT NULL DEFAULT 0,
                total_vnd INTEGER NOT NULL,
                eta_min_minutes INTEGER NULL,
                eta_max_minutes INTEGER NULL,
                quote_payload_json TEXT NOT NULL,
                confirmation_token TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'active',
                expires_at TEXT NOT NULL,
                confirmed_at TEXT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )");
            $pdo->exec("CREATE TABLE IF NOT EXISTS ai_order_idempotency (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                idempotency_key TEXT NOT NULL,
                draft_uid TEXT NOT NULL,
                quote_uid TEXT NOT NULL,
                order_id INTEGER NULL,
                status TEXT NOT NULL DEFAULT 'processing',
                response_json TEXT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, idempotency_key)
            )");
            self::$checkedPdoId = $pdoId;
            return;
        }

        // Check whether ai_order_drafts exists on MySQL/TiDB without altering production unexpectedly.
        try {
            $pdo->query('SELECT scheduled_for, reordered_from_order_id FROM ai_order_drafts LIMIT 1');
            $pdo->query('SELECT scheduled_for FROM ai_order_quotes LIMIT 1');
            self::$checkedPdoId = $pdoId;
        } catch (Throwable) {
            try {
                $pdo->query('SELECT 1 FROM ai_order_drafts LIMIT 1');
                // Table exists from v1 but lacks v2 additive columns: add them non-destructively
                try { $pdo->exec('ALTER TABLE ai_order_drafts ADD COLUMN scheduled_for DATETIME NULL AFTER customer_note'); } catch (Throwable) {}
                try { $pdo->exec('ALTER TABLE ai_order_drafts ADD COLUMN reordered_from_order_id BIGINT UNSIGNED NULL AFTER scheduled_for'); } catch (Throwable) {}
                try { $pdo->exec('ALTER TABLE ai_order_quotes ADD COLUMN scheduled_for DATETIME NULL AFTER payment_method'); } catch (Throwable) {}
            } catch (Throwable) {
                $sqlFile = __DIR__ . '/../database/migrations/20261125_ai_order_drafts.sql';
                if (is_file($sqlFile)) {
                    $raw = (string)file_get_contents($sqlFile);
                    foreach (array_filter(array_map('trim', explode(';', $raw))) as $stmt) {
                        $clean = trim((string)preg_replace('/^--.*$/m', '', $stmt));
                        if ($clean !== '') {
                            $pdo->exec($clean);
                        }
                    }
                }
            }
            self::$checkedPdoId = $pdoId;
        }
    }

    public static function assertCustomer(array $me): int {
        if (($me['status'] ?? '') !== 'active') {
            throw new MealAdvisorException(403, 'Tài khoản đang tạm khóa, chưa thể đặt hàng.');
        }
        if (($me['role'] ?? '') !== 'customer') {
            throw new MealAdvisorException(403, 'Chỉ tài khoản khách hàng mới được tạo đơn đặt món.');
        }
        $userId = (int)($me['id'] ?? 0);
        if ($userId <= 0) {
            throw new MealAdvisorException(401, 'Vui lòng đăng nhập để đặt món.');
        }
        return $userId;
    }

    public static function nowStr(?int $nowTs = null): string {
        return date('Y-m-d H:i:s', $nowTs ?? time());
    }

    public static function moneyVnd(int $amount): string {
        return number_format($amount, 0, ',', '.') . 'đ';
    }

    public static function distanceKm(?float $lat1, ?float $lng1, ?float $lat2, ?float $lng2): ?float {
        if (function_exists('foodhubDistanceKm')) {
            return foodhubDistanceKm($lat1, $lng1, $lat2, $lng2);
        }
        if ($lat1 === null || $lng1 === null || $lat2 === null || $lng2 === null) return null;
        if (!is_finite($lat1) || !is_finite($lng1) || !is_finite($lat2) || !is_finite($lng2)) return null;
        if ((abs($lat1) < 0.0001 && abs($lng1) < 0.0001) || (abs($lat2) < 0.0001 && abs($lng2) < 0.0001)) return null;
        if ($lat1 < -90 || $lat1 > 90 || $lat2 < -90 || $lat2 > 90 || $lng1 < -180 || $lng1 > 180 || $lng2 < -180 || $lng2 > 180) return null;
        $r = 6371.0;
        $dLat = deg2rad($lat2 - $lat1);
        $dLng = deg2rad($lng2 - $lng1);
        $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLng / 2) ** 2;
        $raw = 2 * $r * atan2(sqrt($a), sqrt(max(0.0, 1.0 - $a)));
        return round(max(0.1, $raw), 1);
    }

    public static function shippingFeeVnd(?float $distanceKm, int $baseFeeVnd = 15000): int {
        if ($distanceKm === null || !is_finite($distanceKm) || $distanceKm <= 3.0) {
            return max(0, $baseFeeVnd);
        }
        $extraKm = max(1, (int)ceil(round($distanceKm - 3.0, 4) - 1e-9));
        return max(0, $baseFeeVnd + ($extraKm * 10000));
    }

    /**
     * Grounded ETA estimation breaking down preparation, driver pickup, and travel time.
     * Never fabricates a route ETA if coordinates are missing.
     */
    public static function estimateEta(?float $distanceKm, ?int $storePrepMinutes, array $itemPrepMinutes = []): array {
        $basePrep = ($storePrepMinutes !== null && $storePrepMinutes > 0 && $storePrepMinutes <= 45)
            ? max(8, $storePrepMinutes)
            : 12;
        if ($itemPrepMinutes) {
            $maxDishPrep = max($itemPrepMinutes);
            if ($maxDishPrep > 0 && $maxDishPrep <= 45) {
                $basePrep = max($basePrep, $maxDishPrep);
            }
        }
        $pickupMinutes = 4;
        if ($distanceKm === null || !is_finite($distanceKm)) {
            return [
                'available' => false,
                'min' => null,
                'max' => null,
                'prepMinutes' => $basePrep,
                'pickupMinutes' => $pickupMinutes,
                'travelMinutes' => null,
                'distanceKm' => null,
                'displayText' => null,
                'note' => 'Chưa có đủ tọa độ khoảng cách để ước tính chính xác thời gian di chuyển (thời gian quán chuẩn bị dự kiến khoảng ' . $basePrep . ' phút).',
            ];
        }
        $travelMinutes = (int)ceil($distanceKm * 2.5);
        $min = max(12, $basePrep + $pickupMinutes + $travelMinutes);
        $buffer = $distanceKm > 5.0 ? 8 : 6;
        $max = $min + $buffer;
        return [
            'available' => true,
            'min' => $min,
            'max' => $max,
            'prepMinutes' => $basePrep,
            'pickupMinutes' => $pickupMinutes,
            'travelMinutes' => $travelMinutes,
            'distanceKm' => $distanceKm,
            'displayText' => $min . '–' . $max . ' phút',
            'note' => 'Thời gian dự kiến (gồm ~' . $basePrep . ' phút chuẩn bị và ~' . ($pickupMinutes + $travelMinutes) . ' phút lấy/giao cho ' . number_format($distanceKm, 1, '.', '') . ' km), chưa phải cam kết cố định.',
        ];
    }

    /** Fetch real option groups and options attached to a dish. */
    public static function getDishOptions(PDO $pdo, int $dishId): array {
        try {
            $stmt = $pdo->prepare(
                'SELECT og.id AS group_id, og.name AS group_name, og.min_select, og.max_select,
                        o.id AS option_id, o.name AS option_name, o.extra_price, o.is_available
                 FROM dish_option_groups dog
                 JOIN option_groups og ON og.id = dog.option_group_id
                 LEFT JOIN options o ON o.option_group_id = og.id
                 WHERE dog.dish_id = ?
                 ORDER BY dog.sort_order ASC, og.id ASC, o.sort_order ASC, o.id ASC'
            );
            $stmt->execute([$dishId]);
            $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
        } catch (Throwable) {
            return [];
        }
        if (!$rows) return [];
        $groups = [];
        foreach ($rows as $r) {
            $gid = (int)$r['group_id'];
            if (!isset($groups[$gid])) {
                $groups[$gid] = [
                    'groupId' => (string)$gid,
                    'name' => (string)$r['group_name'],
                    'minSelect' => (int)$r['min_select'],
                    'maxSelect' => (int)$r['max_select'],
                    'required' => (int)$r['min_select'] > 0,
                    'options' => [],
                ];
            }
            if ($r['option_id'] !== null && (bool)$r['is_available']) {
                $groups[$gid]['options'][] = [
                    'optionId' => (string)$r['option_id'],
                    'name' => (string)$r['option_name'],
                    'extraPriceVnd' => (int)round((float)$r['extra_price']),
                ];
            }
        }
        return array_values($groups);
    }

    /** Fetch single verified orderable dish (or return availability diagnostics if out of stock). */
    public static function getDishSnapshot(PDO $pdo, int $dishId): ?array {
        $stmt = $pdo->prepare(
            'SELECT d.id, d.store_id, d.name, d.description, d.price, d.image_url,
                    d.is_available, d.stock_quantity, d.prep_minutes, d.deleted_at,
                    mc.name AS category,
                    s.name AS store_name, s.is_open AS store_open, s.status AS store_status,
                    s.latitude AS store_lat, s.longitude AS store_lng,
                    s.avg_prep_minutes AS store_prep_minutes, s.address_line AS store_address
             FROM dishes d
             JOIN stores s ON s.id = d.store_id
             JOIN merchants m ON m.id = s.merchant_id
             JOIN users owner ON owner.id = m.owner_user_id
             LEFT JOIN menu_categories mc ON mc.id = d.menu_category_id
             WHERE d.id = ? AND d.deleted_at IS NULL AND s.deleted_at IS NULL
               AND m.status = \'active\' AND owner.status = \'active\' AND owner.deleted_at IS NULL
             LIMIT 1'
        );
        $stmt->execute([$dishId]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row) return null;

        $inStock = ($row['stock_quantity'] === null || (int)$row['stock_quantity'] > 0);
        $orderable = (bool)$row['is_available'] && $inStock && (bool)$row['store_open'] && $row['store_status'] === 'active';

        return [
            'id' => (string)$row['id'],
            'dishIdInt' => (int)$row['id'],
            'storeId' => (string)$row['store_id'],
            'storeIdInt' => (int)$row['store_id'],
            'name' => (string)$row['name'],
            'description' => (string)($row['description'] ?? ''),
            'category' => (string)($row['category'] ?? ''),
            'priceVnd' => (int)round((float)$row['price']),
            'imageUrl' => (string)($row['image_url'] ?? ''),
            'isAvailable' => (bool)$row['is_available'] && $inStock,
            'stockQuantity' => $row['stock_quantity'] !== null ? (int)$row['stock_quantity'] : null,
            'prepMinutes' => $row['prep_minutes'] !== null ? (int)$row['prep_minutes'] : null,
            'storeName' => (string)$row['store_name'],
            'storeOpen' => (bool)$row['store_open'] && $row['store_status'] === 'active',
            'storeLat' => $row['store_lat'] !== null ? (float)$row['store_lat'] : null,
            'storeLng' => $row['store_lng'] !== null ? (float)$row['store_lng'] : null,
            'storePrepMinutes' => $row['store_prep_minutes'] !== null ? (int)$row['store_prep_minutes'] : null,
            'storeAddress' => (string)($row['store_address'] ?? ''),
            'orderable' => $orderable,
            'optionGroups' => self::getDishOptions($pdo, (int)$row['id']),
        ];
    }

    /** Fetch verified user addresses ordered by default first. */
    public static function getUserAddresses(PDO $pdo, int $userId): array {
        $stmt = $pdo->prepare(
            'SELECT id, label, receiver_name, receiver_phone, address_line, ward, district, city,
                    latitude, longitude, is_default
             FROM user_addresses
             WHERE user_id = ?
             ORDER BY is_default DESC, id DESC'
        );
        $stmt->execute([$userId]);
        $out = [];
        foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $r) {
            $out[] = [
                'id' => (string)$r['id'],
                'idInt' => (int)$r['id'],
                'label' => (string)($r['label'] ?: 'Địa chỉ'),
                'receiverName' => trim((string)$r['receiver_name']),
                'receiverPhone' => trim((string)$r['receiver_phone']),
                'addressLine' => trim((string)$r['address_line']),
                'ward' => (string)($r['ward'] ?? ''),
                'district' => (string)($r['district'] ?? ''),
                'city' => (string)($r['city'] ?? ''),
                'latitude' => $r['latitude'] !== null ? (float)$r['latitude'] : null,
                'longitude' => $r['longitude'] !== null ? (float)$r['longitude'] : null,
                'isDefault' => (bool)$r['is_default'],
            ];
        }
        return $out;
    }

    /**
     * Evaluate all vouchers for a store + subtotal + delivery fee.
     * Ranks eligible vouchers by ACTUAL VND discount (never naive percentage).
     */
    public static function discoverVouchers(
        PDO $pdo,
        int $storeId,
        int $subtotalVnd,
        int $deliveryFeeVnd,
        ?string $requestedCode = null,
        ?int $nowTs = null
    ): array {
        $now = $nowTs ?? time();
        $stmt = $pdo->query('SELECT * FROM vouchers ORDER BY id DESC');
        $rows = $stmt ? $stmt->fetchAll(PDO::FETCH_ASSOC) : [];

        $eligible = [];
        $ineligible = [];
        $requestedDiagnostic = null;
        $normReq = $requestedCode !== null && $requestedCode !== '' && strtoupper($requestedCode) !== 'NONE'
            ? strtoupper(trim($requestedCode))
            : null;

        foreach ($rows as $v) {
            $code = strtoupper(trim((string)$v['code']));
            $isActive = (bool)$v['is_active'];
            $startsAt = !empty($v['starts_at']) ? strtotime((string)$v['starts_at']) : null;
            $endsAt = !empty($v['ends_at']) ? strtotime((string)$v['ends_at']) : null;
            $vStoreId = $v['store_id'] !== null ? (int)$v['store_id'] : null;
            $minOrderVnd = (int)round((float)$v['min_order_amount']);
            $usageLimit = $v['usage_limit'] !== null ? (int)$v['usage_limit'] : null;
            $usedCount = (int)($v['used_count'] ?? 0);
            $type = (string)$v['discount_type'];
            $val = (float)$v['discount_value'];
            $maxCapVnd = $v['max_discount'] !== null && (float)$v['max_discount'] > 0
                ? (int)round((float)$v['max_discount'])
                : null;

            $reason = null;
            if (!$isActive) {
                $reason = 'Mã giảm giá hiện đang tạm ngưng.';
            } elseif ($startsAt !== null && $startsAt !== false && $startsAt > $now) {
                $reason = 'Mã giảm giá chưa đến thời gian có hiệu lực.';
            } elseif ($endsAt !== null && $endsAt !== false && $endsAt < $now) {
                $reason = 'Mã giảm giá đã hết hạn sử dụng.';
            } elseif ($vStoreId !== null && $vStoreId > 0 && $vStoreId !== $storeId) {
                $reason = 'Mã giảm giá chỉ áp dụng cho nhà hàng khác.';
            } elseif ($usageLimit !== null && $usageLimit > 0 && $usedCount >= $usageLimit) {
                $reason = 'Mã giảm giá đã hết lượt sử dụng.';
            } elseif ($subtotalVnd < $minOrderVnd) {
                $missing = $minOrderVnd - $subtotalVnd;
                $reason = 'Đơn tối thiểu ' . self::moneyVnd($minOrderVnd) . ' (còn thiếu ' . self::moneyVnd($missing) . ').';
            }

            $foodDiscountVnd = 0;
            $shipDiscountVnd = 0;
            if ($reason === null) {
                if ($type === 'percentage') {
                    $raw = (int)floor(($subtotalVnd * $val) / 100.0);
                    $foodDiscountVnd = $maxCapVnd !== null ? min($raw, $maxCapVnd) : $raw;
                    $foodDiscountVnd = min($foodDiscountVnd, $subtotalVnd);
                } elseif ($type === 'fixed') {
                    $raw = (int)round($val);
                    $foodDiscountVnd = min($raw, $subtotalVnd + $deliveryFeeVnd);
                } elseif ($type === 'free_shipping') {
                    $raw = $deliveryFeeVnd;
                    $shipDiscountVnd = $maxCapVnd !== null ? min($raw, $maxCapVnd) : $raw;
                    $shipDiscountVnd = min($shipDiscountVnd, $deliveryFeeVnd);
                }
            }
            $totalDiscountVnd = max(0, min($subtotalVnd + $deliveryFeeVnd, $foodDiscountVnd + $shipDiscountVnd));

            $entry = [
                'id' => (string)$v['id'],
                'idInt' => (int)$v['id'],
                'code' => $code,
                'title' => (string)($v['title'] ?? $code),
                'description' => (string)($v['description'] ?? ''),
                'discountType' => $type,
                'discountValue' => $val,
                'maxDiscountVnd' => $maxCapVnd,
                'minOrderVnd' => $minOrderVnd,
                'foodDiscountVnd' => $foodDiscountVnd,
                'deliveryDiscountVnd' => $shipDiscountVnd,
                'actualDiscountVnd' => $totalDiscountVnd,
                'eligible' => $reason === null && $totalDiscountVnd > 0,
                'ineligibleReason' => $reason,
            ];

            if ($normReq !== null && $code === $normReq) {
                $requestedDiagnostic = $entry;
            }
            if ($entry['eligible']) {
                $eligible[] = $entry;
            } else {
                $ineligible[] = $entry;
            }
        }

        usort($eligible, static function (array $a, array $b): int {
            if ($b['actualDiscountVnd'] !== $a['actualDiscountVnd']) {
                return $b['actualDiscountVnd'] <=> $a['actualDiscountVnd'];
            }
            return $a['minOrderVnd'] <=> $b['minOrderVnd'];
        });

        if ($normReq !== null && $requestedDiagnostic === null) {
            $requestedDiagnostic = [
                'id' => '',
                'idInt' => 0,
                'code' => $normReq,
                'title' => $normReq,
                'eligible' => false,
                'actualDiscountVnd' => 0,
                'ineligibleReason' => 'Mã "' . $normReq . '" không tồn tại trong hệ thống.',
            ];
        }

        return [
            'eligible' => $eligible,
            'best' => $eligible[0] ?? null,
            'ineligible' => array_slice($ineligible, 0, 5),
            'requested' => $requestedDiagnostic,
        ];
    }

    /** Load active (non-cancelled, non-completed) draft for a user + conversationId. */
    public static function getActiveDraft(PDO $pdo, int $userId, string $conversationId, ?int $nowTs = null): ?array {
        self::ensureSchema($pdo);
        $now = $nowTs ?? time();
        $stmt = $pdo->prepare(
            "SELECT * FROM ai_order_drafts
             WHERE user_id = ? AND conversation_id = ?
               AND state NOT IN ('CANCELLED', 'EXPIRED', 'ORDER_CREATED', 'PAYMENT_PENDING')
             ORDER BY id DESC LIMIT 1"
        );
        $stmt->execute([$userId, $conversationId]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row) return null;

        $expTs = strtotime((string)$row['expires_at']);
        if ($expTs !== false && $expTs < $now) {
            $pdo->prepare("UPDATE ai_order_drafts SET state = 'EXPIRED', updated_at = ? WHERE id = ?")
                ->execute([self::nowStr($now), $row['id']]);
            return null;
        }
        return self::hydrateDraftRow($row);
    }

    /** Load draft strictly owned by $userId by its public draftUid. */
    public static function getDraftByUid(PDO $pdo, int $userId, string $draftUid, ?int $nowTs = null): array {
        self::ensureSchema($pdo);
        $now = $nowTs ?? time();
        $stmt = $pdo->prepare('SELECT * FROM ai_order_drafts WHERE draft_uid = ? LIMIT 1');
        $stmt->execute([$draftUid]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row) {
            throw new MealAdvisorException(404, 'Không tìm thấy đơn nháp.');
        }
        if ((int)$row['user_id'] !== $userId) {
            throw new MealAdvisorException(403, 'Bạn không có quyền truy cập đơn nháp này.');
        }
        $expTs = strtotime((string)$row['expires_at']);
        if ($expTs !== false && $expTs < $now && !in_array($row['state'], [self::STATE_ORDER_CREATED, self::STATE_PAYMENT_PENDING, self::STATE_CANCELLED], true)) {
            $pdo->prepare("UPDATE ai_order_drafts SET state = 'EXPIRED', updated_at = ? WHERE id = ?")
                ->execute([self::nowStr($now), $row['id']]);
            $row['state'] = self::STATE_EXPIRED;
        }
        return self::hydrateDraftRow($row);
    }

    private static function hydrateDraftRow(array $row): array {
        $items = json_decode((string)($row['items_json'] ?? '[]'), true);
        return [
            'idInt' => (int)$row['id'],
            'draftId' => (string)$row['draft_uid'],
            'userId' => (int)$row['user_id'],
            'conversationId' => (string)$row['conversation_id'],
            'storeId' => $row['store_id'] !== null ? (string)$row['store_id'] : null,
            'storeIdInt' => $row['store_id'] !== null ? (int)$row['store_id'] : null,
            'state' => (string)$row['state'],
            'items' => is_array($items) ? array_values($items) : [],
            'addressId' => $row['address_id'] !== null ? (string)$row['address_id'] : null,
            'addressIdInt' => $row['address_id'] !== null ? (int)$row['address_id'] : null,
            'voucherCode' => $row['voucher_code'] !== null ? (string)$row['voucher_code'] : null,
            'voucherAuto' => (bool)$row['voucher_auto'],
            'paymentMethod' => $row['payment_method'] !== null ? (string)$row['payment_method'] : null,
            'customerNote' => (string)($row['customer_note'] ?? ''),
            'scheduledFor' => isset($row['scheduled_for']) && $row['scheduled_for'] !== null && $row['scheduled_for'] !== '' ? (string)$row['scheduled_for'] : null,
            'reorderedFromOrderId' => isset($row['reordered_from_order_id']) && $row['reordered_from_order_id'] !== null ? (string)$row['reordered_from_order_id'] : null,
            'version' => (int)$row['version'],
            'activeQuoteId' => $row['active_quote_id'] !== null ? (string)$row['active_quote_id'] : null,
            'submittedOrderId' => $row['submitted_order_id'] !== null ? (string)$row['submitted_order_id'] : null,
            'expiresAt' => (string)$row['expires_at'],
        ];
    }

    /** Create or retrieve active draft for conversation. */
    public static function getOrCreateDraft(PDO $pdo, int $userId, string $conversationId, ?int $nowTs = null): array {
        $existing = self::getActiveDraft($pdo, $userId, $conversationId, $nowTs);
        if ($existing !== null) {
            return $existing;
        }
        $now = $nowTs ?? time();
        $draftUid = 'dr_' . bin2hex(random_bytes(10));
        $addresses = self::getUserAddresses($pdo, $userId);
        $defaultAddressId = isset($addresses[0]) ? $addresses[0]['idInt'] : null;
        $expiresAt = self::nowStr($now + self::DRAFT_TTL_SECONDS);
        $nowStr = self::nowStr($now);

        $stmt = $pdo->prepare(
            'INSERT INTO ai_order_drafts
             (draft_uid, user_id, conversation_id, store_id, state, items_json, address_id, voucher_code, voucher_auto, payment_method, customer_note, version, expires_at, created_at, updated_at)
             VALUES (?, ?, ?, NULL, ?, ?, ?, NULL, 1, \'cash\', \'\', 1, ?, ?, ?)'
        );
        $stmt->execute([$draftUid, $userId, $conversationId, self::STATE_INTENT_DETECTED, '[]', $defaultAddressId, $expiresAt, $nowStr, $nowStr]);
        return self::getDraftByUid($pdo, $userId, $draftUid, $now);
    }

    /** Validate requested option IDs or natural option names against the dish's real optionGroups. */
    public static function resolveItemOptions(array $dishSnapshot, array $requestedOptionIds = [], array $requestedOptionNames = []): array {
        $groups = $dishSnapshot['optionGroups'] ?? [];
        if (!$groups) {
            $unsupported = array_values(array_filter(array_map('trim', $requestedOptionNames), static fn($s) => $s !== ''));
            return [
                'selectedOptions' => [],
                'optionsExtraVnd' => 0,
                'missingRequiredGroups' => [],
                'unsupportedOptions' => $unsupported,
            ];
        }

        $byId = [];
        $byFoldedName = [];
        foreach ($groups as $g) {
            foreach ($g['options'] as $opt) {
                $byId[$opt['optionId']] = $opt + ['groupId' => $g['groupId'], 'groupName' => $g['name'], 'maxSelect' => $g['maxSelect']];
                $byFoldedName[MealAdvisor::folded($opt['name'])] = $byId[$opt['optionId']];
            }
        }

        $chosenById = [];
        $unsupported = [];

        foreach ($requestedOptionIds as $oid) {
            $oidStr = trim((string)$oid);
            if ($oidStr === '') continue;
            if (isset($byId[$oidStr])) {
                $chosenById[$oidStr] = $byId[$oidStr];
            } else {
                $unsupported[] = '#' . $oidStr;
            }
        }

        foreach ($requestedOptionNames as $rawName) {
            $clean = trim((string)$rawName);
            if ($clean === '') continue;
            $folded = MealAdvisor::folded($clean);
            $matched = null;
            if (isset($byFoldedName[$folded])) {
                $matched = $byFoldedName[$folded];
            } else {
                foreach ($byFoldedName as $fKey => $candidate) {
                    if ($fKey !== '' && (str_contains($fKey, $folded) || str_contains($folded, $fKey))) {
                        $matched = $candidate;
                        break;
                    }
                }
            }
            if ($matched !== null) {
                $chosenById[$matched['optionId']] = $matched;
            } else {
                $unsupported[] = $clean;
            }
        }

        // Enforce maxSelect per group and check required groups (minSelect > 0)
        $countByGroup = [];
        $finalOptions = [];
        $extraVnd = 0;
        foreach ($chosenById as $opt) {
            $gid = $opt['groupId'];
            $countByGroup[$gid] = ($countByGroup[$gid] ?? 0) + 1;
            if ($opt['maxSelect'] > 0 && $countByGroup[$gid] > $opt['maxSelect']) {
                continue;
            }
            $finalOptions[] = [
                'optionId' => $opt['optionId'],
                'groupId' => $opt['groupId'],
                'groupName' => $opt['groupName'],
                'name' => $opt['name'],
                'extraPriceVnd' => (int)$opt['extraPriceVnd'],
            ];
            $extraVnd += (int)$opt['extraPriceVnd'];
        }

        $missingGroups = [];
        foreach ($groups as $g) {
            $cnt = $countByGroup[$g['groupId']] ?? 0;
            if ($cnt < $g['minSelect']) {
                $missingGroups[] = $g;
            }
        }

        return [
            'selectedOptions' => $finalOptions,
            'optionsExtraVnd' => $extraVnd,
            'missingRequiredGroups' => $missingGroups,
            'unsupportedOptions' => array_values(array_unique($unsupported)),
        ];
    }

    /** Add or merge an item into the draft, enforcing single-store constraint & stock availability. */
    public static function addOrUpdateItem(
        PDO $pdo,
        int $userId,
        string $draftUid,
        int $dishId,
        int $quantity = 1,
        array $optionIds = [],
        array $optionNames = [],
        string $note = '',
        bool $replaceStore = false,
        bool $setExactQuantity = false,
        ?int $nowTs = null
    ): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        if (in_array($draft['state'], [self::STATE_ORDER_CREATED, self::STATE_PAYMENT_PENDING], true)) {
            throw new MealAdvisorException(409, 'Đơn nháp này đã được tạo thành đơn chính thức.');
        }

        $dish = self::getDishSnapshot($pdo, $dishId);
        if ($dish === null) {
            throw new MealAdvisorException(404, 'Món ăn không tồn tại trong thực đơn.');
        }
        if (!$dish['storeOpen']) {
            return [
                'ok' => false,
                'code' => 'store_closed',
                'message' => 'Quán ' . $dish['storeName'] . ' hiện đang tạm nghỉ nhận đơn.',
                'draft' => $draft,
            ];
        }
        if (!$dish['isAvailable']) {
            $alternatives = MenuKnowledge::candidates($pdo, 10000000, false, $dish['category'] ?: $dish['name'], [], $dish['storeId']);
            return [
                'ok' => false,
                'code' => 'out_of_stock',
                'message' => 'Món ' . $dish['name'] . ' hiện đã tạm hết hàng.',
                'alternatives' => array_slice($alternatives, 0, 3),
                'draft' => self::transitionState($pdo, $draft, self::STATE_OUT_OF_STOCK, $nowTs),
            ];
        }

        $safeQty = max(1, min(50, $quantity));
        if ($dish['stockQuantity'] !== null && $safeQty > $dish['stockQuantity']) {
            return [
                'ok' => false,
                'code' => 'insufficient_stock',
                'message' => 'Món ' . $dish['name'] . ' hiện chỉ còn ' . $dish['stockQuantity'] . ' phần.',
                'availableStock' => $dish['stockQuantity'],
                'draft' => $draft,
            ];
        }

        // Single-restaurant-per-order business constraint
        $items = $draft['items'];
        $currentStoreId = $draft['storeIdInt'];
        if ($currentStoreId !== null && $items && $currentStoreId !== $dish['storeIdInt'] && !$replaceStore) {
            $curStoreName = $items[0]['restaurantName'] ?? 'quán hiện tại';
            return [
                'ok' => false,
                'code' => 'different_store',
                'currentStoreId' => (string)$currentStoreId,
                'currentStoreName' => $curStoreName,
                'targetStoreId' => $dish['storeId'],
                'targetStoreName' => $dish['storeName'],
                'targetDish' => $dish,
                'message' => 'Đơn nháp hiện đang có món của ' . $curStoreName . ', còn ' . $dish['name'] . ' thuộc ' . $dish['storeName'] . '. Mỗi đơn trên FoodHub chỉ đặt từ 1 quán — mình muốn đổi sang ' . $dish['storeName'] . ' hay giữ đơn quán cũ ạ?',
                'draft' => $draft,
            ];
        }

        if ($replaceStore && $currentStoreId !== null && $currentStoreId !== $dish['storeIdInt']) {
            $items = [];
        }

        $optResolution = self::resolveItemOptions($dish, $optionIds, $optionNames);
        if (!empty($optResolution['missingRequiredGroups'])) {
            $draftUpdated = self::transitionState($pdo, $draft, self::STATE_ITEM_CONFIGURATION, $nowTs);
            return [
                'ok' => false,
                'code' => 'missing_required_options',
                'dish' => $dish,
                'missingGroups' => $optResolution['missingRequiredGroups'],
                'unsupportedOptions' => $optResolution['unsupportedOptions'],
                'message' => 'Món ' . $dish['name'] . ' cần chọn tùy chọn bắt buộc trước khi thêm vào đơn.',
                'draft' => $draftUpdated,
            ];
        }

        $cleanNote = mb_substr(trim((string)preg_replace('/\s+/u', ' ', $note)), 0, 250, 'UTF-8');
        $optSig = implode(',', array_column($optResolution['selectedOptions'], 'optionId'));
        $lineKey = $dish['id'] . '|' . $optSig;

        $found = false;
        foreach ($items as $idx => $existing) {
            $existingKey = ($existing['dishId'] ?? '') . '|' . implode(',', array_column($existing['selectedOptions'] ?? [], 'optionId'));
            if ($existingKey === $lineKey) {
                $newQty = $setExactQuantity ? $safeQty : min(50, ((int)$existing['quantity']) + $safeQty);
                if ($dish['stockQuantity'] !== null && $newQty > $dish['stockQuantity']) {
                    $newQty = $dish['stockQuantity'];
                }
                $items[$idx]['quantity'] = $newQty;
                $items[$idx]['unitPriceVnd'] = $dish['priceVnd'];
                $items[$idx]['optionsExtraVnd'] = $optResolution['optionsExtraVnd'];
                $items[$idx]['lineTotalVnd'] = ($dish['priceVnd'] + $optResolution['optionsExtraVnd']) * $newQty;
                if ($cleanNote !== '') {
                    $items[$idx]['note'] = $cleanNote;
                }
                $found = true;
                break;
            }
        }

        if (!$found) {
            $items[] = [
                'lineId' => 'ln_' . bin2hex(random_bytes(4)),
                'dishId' => $dish['id'],
                'restaurantId' => $dish['storeId'],
                'restaurantName' => $dish['storeName'],
                'name' => $dish['name'],
                'imageUrl' => $dish['imageUrl'],
                'category' => $dish['category'],
                'quantity' => $safeQty,
                'unitPriceVnd' => $dish['priceVnd'],
                'selectedOptions' => $optResolution['selectedOptions'],
                'optionsExtraVnd' => $optResolution['optionsExtraVnd'],
                'lineTotalVnd' => ($dish['priceVnd'] + $optResolution['optionsExtraVnd']) * $safeQty,
                'note' => $cleanNote,
            ];
        }

        $updated = self::persistDraftMutation(
            $pdo,
            $draft,
            [
                'store_id' => $dish['storeIdInt'],
                'items' => $items,
                'state' => self::STATE_CHECKOUT_REVIEW,
            ],
            $nowTs
        );

        return [
            'ok' => true,
            'unsupportedOptions' => $optResolution['unsupportedOptions'],
            'dish' => $dish,
            'draft' => $updated,
        ];
    }

    /** Remove item or adjust quantity by dishId / lineId (if quantity <= 0, removes item). */
    public static function modifyItemQuantity(
        PDO $pdo,
        int $userId,
        string $draftUid,
        string $targetDishOrLineId,
        int $newQuantity,
        ?string $newNote = null,
        ?int $nowTs = null
    ): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        $items = $draft['items'];
        $changed = false;
        foreach ($items as $idx => $item) {
            if (($item['dishId'] ?? '') === $targetDishOrLineId || ($item['lineId'] ?? '') === $targetDishOrLineId) {
                if ($newQuantity <= 0) {
                    array_splice($items, $idx, 1);
                } else {
                    $qty = min(50, $newQuantity);
                    $unit = (int)($item['unitPriceVnd'] ?? 0);
                    $extra = (int)($item['optionsExtraVnd'] ?? 0);
                    $items[$idx]['quantity'] = $qty;
                    $items[$idx]['lineTotalVnd'] = ($unit + $extra) * $qty;
                    if ($newNote !== null) {
                        $items[$idx]['note'] = mb_substr(trim($newNote), 0, 250, 'UTF-8');
                    }
                }
                $changed = true;
                break;
            }
        }
        if (!$changed) {
            throw new MealAdvisorException(404, 'Không tìm thấy món này trong đơn nháp.');
        }
        $newStoreId = $items ? $draft['storeIdInt'] : null;
        $newState = $items ? self::STATE_CHECKOUT_REVIEW : self::STATE_FOOD_SELECTION;
        return self::persistDraftMutation($pdo, $draft, [
            'store_id' => $newStoreId,
            'items' => $items,
            'state' => $newState,
        ], $nowTs);
    }

    public static function setDraftAddress(PDO $pdo, int $userId, string $draftUid, int $addressId, ?int $nowTs = null): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        $addresses = self::getUserAddresses($pdo, $userId);
        $matched = null;
        foreach ($addresses as $a) {
            if ($a['idInt'] === $addressId) {
                $matched = $a;
                break;
            }
        }
        if ($matched === null) {
            throw new MealAdvisorException(404, 'Địa chỉ giao hàng không thuộc tài khoản của bạn.');
        }
        return self::persistDraftMutation($pdo, $draft, [
            'address_id' => $addressId,
            'state' => $draft['items'] ? self::STATE_CHECKOUT_REVIEW : self::STATE_FOOD_SELECTION,
        ], $nowTs);
    }

    public static function setDraftVoucher(PDO $pdo, int $userId, string $draftUid, ?string $voucherCode, bool $autoBest = false, ?int $nowTs = null): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        $clean = $voucherCode !== null ? strtoupper(trim($voucherCode)) : null;
        if ($clean === '') $clean = null;
        return self::persistDraftMutation($pdo, $draft, [
            'voucher_code' => $clean,
            'voucher_auto' => $autoBest ? 1 : 0,
            'state' => $draft['items'] ? self::STATE_CHECKOUT_REVIEW : self::STATE_FOOD_SELECTION,
        ], $nowTs);
    }

    public static function setDraftPaymentMethod(PDO $pdo, int $userId, string $draftUid, string $method, ?int $nowTs = null): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        $norm = strtolower(trim($method));
        if (in_array($norm, ['cod', 'cash', 'tien_mat', 'tiền mặt'], true)) {
            $resolved = 'cash';
        } elseif (in_array($norm, ['payos', 'online', 'qr', 'vietqr', 'chuyen_khoan'], true)) {
            $resolved = 'payos';
        } else {
            throw new MealAdvisorException(422, 'FoodHub hiện hỗ trợ thanh toán Tiền mặt (COD) hoặc Chuyển khoản VietQR (PayOS).');
        }
        return self::persistDraftMutation($pdo, $draft, [
            'payment_method' => $resolved,
            'state' => $draft['items'] ? self::STATE_CHECKOUT_REVIEW : self::STATE_PAYMENT_SELECTION,
        ], $nowTs);
    }

    public static function setDraftNote(PDO $pdo, int $userId, string $draftUid, string $note, ?int $nowTs = null): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        $clean = mb_substr(trim($note), 0, 450, 'UTF-8');
        return self::persistDraftMutation($pdo, $draft, ['customer_note' => $clean], $nowTs);
    }

    /** Safe lookup of restaurant open_time .. close_time (compatible with minimal SQLite test schemas). */
    public static function getStoreHours(PDO $pdo, ?int $storeId): array {
        $open = '07:00:00';
        $close = '22:30:00';
        if ($storeId !== null && $storeId > 0) {
            try {
                $stmt = $pdo->prepare('SELECT open_time, close_time FROM stores WHERE id = ? LIMIT 1');
                $stmt->execute([$storeId]);
                $row = $stmt->fetch(PDO::FETCH_ASSOC);
                if ($row) {
                    if (!empty($row['open_time']) && preg_match('/^\d{2}:\d{2}(:\d{2})?$/', trim((string)$row['open_time']))) {
                        $t = trim((string)$row['open_time']);
                        $open = strlen($t) === 5 ? ($t . ':00') : $t;
                    }
                    if (!empty($row['close_time']) && preg_match('/^\d{2}:\d{2}(:\d{2})?$/', trim((string)$row['close_time']))) {
                        $t = trim((string)$row['close_time']);
                        $close = strlen($t) === 5 ? ($t . ':00') : $t;
                    }
                }
            } catch (Throwable) {
                // Keep defaults when open_time / close_time columns are absent in minimal test schemas.
            }
        }
        return [
            'openTime' => $open,
            'closeTime' => $close,
            'openLabel' => substr($open, 0, 5),
            'closeLabel' => substr($close, 0, 5),
            'supportsScheduled' => true,
        ];
    }

    public static function formatScheduledDisplay(?string $scheduledFor, ?int $nowTs = null): ?string {
        if ($scheduledFor === null || trim($scheduledFor) === '') {
            return null;
        }
        $ts = strtotime($scheduledFor);
        if ($ts === false) {
            return null;
        }
        $now = $nowTs ?? time();
        $targetDate = date('Y-m-d', $ts);
        if ($targetDate === date('Y-m-d', $now)) {
            return 'Hôm nay ' . date('H:i', $ts);
        }
        if ($targetDate === date('Y-m-d', $now + 86400)) {
            return 'Ngày mai ' . date('H:i', $ts);
        }
        return date('H:i d/m/Y', $ts);
    }

    private static function isTimeWithinWindow(string $hms, string $openTime, string $closeTime): bool {
        if ($openTime <= $closeTime) {
            return $hms >= $openTime && $hms <= $closeTime;
        }
        // Overnight window (e.g. 18:00:00 -> 02:00:00)
        return $hms >= $openTime || $hms <= $closeTime;
    }

    public static function validateScheduleTime(
        ?string $scheduledFor,
        string $openTime = '07:00:00',
        string $closeTime = '22:30:00',
        ?int $nowTs = null,
        bool $isRevalidation = false
    ): array {
        if ($scheduledFor === null || trim($scheduledFor) === '' || strtoupper(trim($scheduledFor)) === 'NOW') {
            return ['valid' => true, 'normalized' => null, 'display' => null, 'reason' => null];
        }
        $now = $nowTs ?? time();
        $ts = strtotime(trim($scheduledFor));
        if ($ts === false) {
            return ['valid' => false, 'normalized' => null, 'display' => null, 'reason' => 'Định dạng thời gian hẹn giao không hợp lệ.'];
        }
        $minLeadSeconds = $isRevalidation ? (10 * 60) : (20 * 60);
        if ($ts < $now + $minLeadSeconds) {
            return [
                'valid' => false,
                'normalized' => null,
                'display' => null,
                'reason' => 'Giờ hẹn giao (' . date('H:i d/m', $ts) . ') cần cách hiện tại ít nhất 20 phút để quán chuẩn bị món.',
            ];
        }
        if ($ts > $now + (48 * 3600)) {
            return [
                'valid' => false,
                'normalized' => null,
                'display' => null,
                'reason' => 'FoodHub hiện hỗ trợ hẹn giờ giao trước tối đa 48 giờ.',
            ];
        }
        $hms = date('H:i:s', $ts);
        if (!self::isTimeWithinWindow($hms, $openTime, $closeTime)) {
            return [
                'valid' => false,
                'normalized' => null,
                'display' => null,
                'reason' => 'Khung giờ ' . date('H:i', $ts) . ' nằm ngoài giờ mở cửa nhận đơn của quán (' . substr($openTime, 0, 5) . '–' . substr($closeTime, 0, 5) . ').',
            ];
        }
        $norm = date('Y-m-d H:i:s', $ts);
        return [
            'valid' => true,
            'normalized' => $norm,
            'display' => self::formatScheduledDisplay($norm, $now),
            'reason' => null,
        ];
    }

    /** Build up to 4 valid upcoming 30-min rounded delivery schedule slots for quick UI chips. */
    public static function buildAvailableScheduleSlots(string $openTime = '07:00:00', string $closeTime = '22:30:00', ?int $nowTs = null): array {
        $now = $nowTs ?? time();
        $firstCandidate = (int)(ceil(($now + 25 * 60) / 1800) * 1800);
        $slots = [];
        $cursor = $firstCandidate;
        $endLimit = $now + (36 * 3600);
        while ($cursor <= $endLimit && count($slots) < 4) {
            $hms = date('H:i:s', $cursor);
            if (self::isTimeWithinWindow($hms, $openTime, $closeTime)) {
                $iso = date('Y-m-d H:i:s', $cursor);
                $slots[] = [
                    'iso' => $iso,
                    'label' => (string)self::formatScheduledDisplay($iso, $now),
                ];
                // Space out slots nicely: first two 30m apart, next ones 60m apart
                $cursor += count($slots) >= 2 ? 3600 : 1800;
            } else {
                $cursor += 1800;
            }
        }
        return $slots;
    }

    /** Feature 3: Set or clear scheduled_for on an active draft, validated against store opening hours. */
    public static function setDraftSchedule(PDO $pdo, int $userId, string $draftUid, ?string $scheduledFor, ?int $nowTs = null): array {
        $now = $nowTs ?? time();
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $now);
        $hours = self::getStoreHours($pdo, $draft['storeIdInt']);
        $val = self::validateScheduleTime($scheduledFor, $hours['openTime'], $hours['closeTime'], $now, false);
        if (!$val['valid']) {
            return [
                'ok' => false,
                'code' => 'invalid_schedule',
                'message' => (string)$val['reason'],
                'storeOpenHours' => $hours['openLabel'] . '–' . $hours['closeLabel'],
                'availableSlots' => self::buildAvailableScheduleSlots($hours['openTime'], $hours['closeTime'], $now),
                'draft' => $draft,
            ];
        }
        $updated = self::persistDraftMutation($pdo, $draft, [
            'scheduled_for' => $val['normalized'],
            'state' => $draft['items'] ? self::STATE_CHECKOUT_REVIEW : $draft['state'],
        ], $now);
        return [
            'ok' => true,
            'scheduledFor' => $val['normalized'],
            'scheduledForDisplay' => $val['display'],
            'storeOpenHours' => $hours['openLabel'] . '–' . $hours['closeLabel'],
            'draft' => $updated,
        ];
    }

    /**
     * Feature 2: Consent-Based Smart Voucher Upselling.
     * Finds a small side dish / drink from the SAME store when the draft is missing 1.000đ–25.000đ
     * (especially 5.000đ–10.000đ) to hit min_order_amount of a deeper voucher so total becomes cheaper
     * (or near-free). NEVER auto-adds to the draft without explicit user consent!
     */
    public static function findSmartUpsell(
        PDO $pdo,
        int $storeId,
        array $verifiedItems,
        int $subtotalVnd,
        int $deliveryFeeVnd,
        int $currentDiscountVnd,
        ?int $nowTs = null
    ): ?array {
        if ($storeId <= 0 || $subtotalVnd <= 0) {
            return null;
        }
        $now = $nowTs ?? time();
        $currentTotalVnd = max(0, $subtotalVnd + $deliveryFeeVnd - $currentDiscountVnd);

        $stmt = $pdo->query('SELECT * FROM vouchers WHERE is_active = 1 ORDER BY id DESC');
        $vRows = $stmt ? $stmt->fetchAll(PDO::FETCH_ASSOC) : [];
        if (!$vRows) {
            return null;
        }

        $targetVouchers = [];
        foreach ($vRows as $v) {
            $vStoreId = $v['store_id'] !== null ? (int)$v['store_id'] : null;
            if ($vStoreId !== null && $vStoreId > 0 && $vStoreId !== $storeId) {
                continue;
            }
            $startsAt = !empty($v['starts_at']) ? strtotime((string)$v['starts_at']) : null;
            $endsAt = !empty($v['ends_at']) ? strtotime((string)$v['ends_at']) : null;
            if (($startsAt !== null && $startsAt !== false && $startsAt > $now)
                || ($endsAt !== null && $endsAt !== false && $endsAt < $now)
            ) {
                continue;
            }
            $usageLimit = $v['usage_limit'] !== null ? (int)$v['usage_limit'] : null;
            if ($usageLimit !== null && $usageLimit > 0 && (int)($v['used_count'] ?? 0) >= $usageLimit) {
                continue;
            }
            $minOrderVnd = (int)round((float)$v['min_order_amount']);
            $missingVnd = $minOrderVnd - $subtotalVnd;
            if ($missingVnd >= 1000 && $missingVnd <= 25000) {
                $targetVouchers[] = ['row' => $v, 'minOrderVnd' => $minOrderVnd, 'missingVnd' => $missingVnd];
            }
        }
        if (!$targetVouchers) {
            return null;
        }

        // Fetch available dishes from the SAME store priced <= 45,000đ without mandatory option groups
        $dStmt = $pdo->prepare(
            'SELECT d.id, d.name, d.price, d.image_url, mc.name AS category
             FROM dishes d
             LEFT JOIN menu_categories mc ON mc.id = d.menu_category_id
             WHERE d.store_id = ? AND d.deleted_at IS NULL AND d.is_available = 1
               AND (d.stock_quantity IS NULL OR d.stock_quantity > 0)
               AND d.price > 0 AND d.price <= 45000
             ORDER BY d.price ASC, d.id ASC'
        );
        $dStmt->execute([$storeId]);
        $storeDishes = $dStmt->fetchAll(PDO::FETCH_ASSOC);
        if (!$storeDishes) {
            return null;
        }

        $existingDishIds = [];
        foreach ($verifiedItems as $it) {
            $existingDishIds[(string)($it['dishId'] ?? '')] = true;
        }

        $bestCandidate = null;
        foreach ($targetVouchers as $tv) {
            $v = $tv['row'];
            $minOrderVnd = $tv['minOrderVnd'];
            $missingVnd = $tv['missingVnd'];
            $vType = (string)$v['discount_type'];
            $vVal = (float)$v['discount_value'];
            $maxCapVnd = $v['max_discount'] !== null && (float)$v['max_discount'] > 0
                ? (int)round((float)$v['max_discount'])
                : null;
            $vCode = strtoupper(trim((string)$v['code']));
            $vTitle = (string)($v['title'] ?? $vCode);

            foreach ($storeDishes as $d) {
                $addonPriceVnd = (int)round((float)$d['price']);
                if ($addonPriceVnd < $missingVnd) {
                    continue;
                }
                // Skip dishes that require mandatory option selection so 1-tap add never blocks
                $optGroups = self::getDishOptions($pdo, (int)$d['id']);
                $hasRequiredOpts = false;
                foreach ($optGroups as $og) {
                    if (!empty($og['required'])) {
                        $hasRequiredOpts = true;
                        break;
                    }
                }
                if ($hasRequiredOpts) {
                    continue;
                }

                $newSubtotalVnd = $subtotalVnd + $addonPriceVnd;
                $unlockedDiscountVnd = 0;
                if ($vType === 'percentage') {
                    $raw = (int)floor(($newSubtotalVnd * $vVal) / 100.0);
                    $unlockedDiscountVnd = min($newSubtotalVnd, $maxCapVnd !== null ? min($raw, $maxCapVnd) : $raw);
                } elseif ($vType === 'fixed') {
                    $unlockedDiscountVnd = min($newSubtotalVnd + $deliveryFeeVnd, (int)round($vVal));
                } elseif ($vType === 'free_shipping') {
                    $unlockedDiscountVnd = min($deliveryFeeVnd, $maxCapVnd !== null ? min($deliveryFeeVnd, $maxCapVnd) : $deliveryFeeVnd);
                }

                $extraDiscountVnd = $unlockedDiscountVnd - $currentDiscountVnd;
                if ($extraDiscountVnd <= 0) {
                    continue;
                }

                $projectedTotalVnd = max(0, $newSubtotalVnd + $deliveryFeeVnd - $unlockedDiscountVnd);
                $netDeltaVnd = $projectedTotalVnd - $currentTotalVnd;

                // Only suggest when total becomes strictly cheaper/equal OR customer adds <= 5.000đ net for >= 10.000đ extra voucher value
                if ($netDeltaVnd > 5000 || ($netDeltaVnd > 0 && $extraDiscountVnd < 10000)) {
                    continue;
                }

                $isSide = MenuKnowledge::isSideDish(['name' => $d['name'], 'category' => $d['category'] ?? ''])
                    || (bool)preg_match('/\b(nuoc|tra|sua|sam|chanh|cam|pepsi|coca|soda|rau ma|che|banh|nem|cha|quay|canh|sup|goi|khoai)\b/u', MealAdvisor::folded($d['name'] . ' ' . ($d['category'] ?? '')));
                $notAlreadyInCart = !isset($existingDishIds[(string)$d['id']]);
                $isCheaper = $netDeltaVnd < 0;

                // Composite ranking: strictly cheaper first, then side/drink, then not duplicate, then biggest savings (lowest netDeltaVnd)
                $rankScore = ($isCheaper ? 1000000 : 0)
                    + ($netDeltaVnd === 0 ? 500000 : 0)
                    + ($isSide ? 100000 : 0)
                    + ($notAlreadyInCart ? 20000 : 0)
                    - $netDeltaVnd;

                if ($bestCandidate === null || $rankScore > $bestCandidate['_rankScore']) {
                    $savingsVnd = max(0, -$netDeltaVnd);
                    $dishName = (string)$d['name'];
                    if ($netDeltaVnd < 0) {
                        $headline = 'Thêm 1x ' . $dishName . ' (' . self::moneyVnd($addonPriceVnd) . ') — Tổng đơn rẻ hơn ' . self::moneyVnd($savingsVnd) . '!';
                        $reason = 'Đơn còn thiếu ' . self::moneyVnd($missingVnd) . ' để chạm mức ' . self::moneyVnd($minOrderVnd) . ' của mã ' . $vCode . ' (-' . self::moneyVnd($unlockedDiscountVnd) . '). Nếu thêm ' . $dishName . ', tổng tiền giảm từ ' . self::moneyVnd($currentTotalVnd) . ' xuống còn ' . self::moneyVnd($projectedTotalVnd) . '.';
                    } elseif ($netDeltaVnd === 0) {
                        $headline = 'Thêm 1x ' . $dishName . ' (' . self::moneyVnd($addonPriceVnd) . ') hoàn toàn 0đ chênh lệch nhờ mã ' . $vCode . '!';
                        $reason = 'Đơn thiếu ' . self::moneyVnd($missingVnd) . ' để mở mã ' . $vCode . ' (-' . self::moneyVnd($unlockedDiscountVnd) . '). Thêm ' . $dishName . ' mà tổng thanh toán vẫn giữ nguyên ' . self::moneyVnd($projectedTotalVnd) . '.';
                    } else {
                        $headline = 'Chỉ thêm ' . self::moneyVnd($netDeltaVnd) . ' có ngay 1x ' . $dishName . ' (' . self::moneyVnd($addonPriceVnd) . ') nhờ mã ' . $vCode . '!';
                        $reason = 'Đơn thiếu ' . self::moneyVnd($missingVnd) . ' để mở mã ' . $vCode . ' (-' . self::moneyVnd($unlockedDiscountVnd) . '), giúp mua thêm ' . $dishName . ' với tổng đơn ' . self::moneyVnd($projectedTotalVnd) . '.';
                    }

                    $bestCandidate = [
                        '_rankScore' => $rankScore,
                        'dishId' => (string)$d['id'],
                        'dishName' => $dishName,
                        'dishPriceVnd' => $addonPriceVnd,
                        'dishImageUrl' => (string)($d['image_url'] ?? ''),
                        'category' => (string)($d['category'] ?? ''),
                        'isSideOrDrink' => $isSide,
                        'targetVoucherCode' => $vCode,
                        'targetVoucherTitle' => $vTitle,
                        'minOrderVnd' => $minOrderVnd,
                        'missingAmountVnd' => $missingVnd,
                        'unlockedDiscountVnd' => $unlockedDiscountVnd,
                        'currentTotalVnd' => $currentTotalVnd,
                        'projectedTotalVnd' => $projectedTotalVnd,
                        'netDeltaVnd' => $netDeltaVnd,
                        'savingsVsCurrentVnd' => $savingsVnd,
                        'isStrictlyCheaper' => $isCheaper,
                        'headline' => $headline,
                        'reason' => $reason,
                    ];
                }
            }
        }

        if ($bestCandidate !== null) {
            unset($bestCandidate['_rankScore']);
        }
        return $bestCandidate;
    }

    /** Apply customer's explicit consent to add the suggested upsell item + target voucher. */
    public static function acceptUpsell(
        PDO $pdo,
        int $userId,
        string $draftUid,
        int $dishId,
        string $voucherCode,
        ?int $nowTs = null
    ): array {
        $now = $nowTs ?? time();
        $addRes = self::addOrUpdateItem($pdo, $userId, $draftUid, $dishId, 1, [], [], '', false, false, $now);
        if (!$addRes['ok']) {
            return $addRes;
        }
        $cleanVoucher = strtoupper(trim($voucherCode));
        $updated = $cleanVoucher !== ''
            ? self::setDraftVoucher($pdo, $userId, $draftUid, $cleanVoucher, false, $now)
            : $addRes['draft'];
        return [
            'ok' => true,
            'dish' => $addRes['dish'],
            'voucherCode' => $cleanVoucher,
            'draft' => $updated,
        ];
    }

    /**
     * Feature 1: One-Tap Reorder.
     * Loads items & options from the user's most recent completed (or non-cancelled) order into
     * the conversation draft, re-validating store open status, dish stock, and LIVE current prices.
     */
    public static function reorderPastOrder(
        PDO $pdo,
        int $userId,
        string $conversationId,
        ?string $dateHint = null,
        ?int $nowTs = null
    ): array {
        self::ensureSchema($pdo);
        $now = $nowTs ?? time();

        $pastOrder = null;
        $usedFallbackLatest = false;

        if ($dateHint === 'yesterday') {
            $yStart = date('Y-m-d 00:00:00', $now - 86400);
            $yEnd = date('Y-m-d 23:59:59', $now - 86400);
            $yStmt = $pdo->prepare(
                "SELECT * FROM orders
                 WHERE customer_id = ? AND status != 'cancelled'
                   AND (
                       (delivered_at IS NOT NULL AND delivered_at BETWEEN ? AND ?)
                       OR (placed_at IS NOT NULL AND placed_at BETWEEN ? AND ?)
                       OR (created_at BETWEEN ? AND ?)
                   )
                 ORDER BY CASE WHEN status = 'completed' THEN 0 ELSE 1 END ASC, id DESC
                 LIMIT 1"
            );
            $yStmt->execute([$userId, $yStart, $yEnd, $yStart, $yEnd, $yStart, $yEnd]);
            $pastOrder = $yStmt->fetch(PDO::FETCH_ASSOC) ?: null;
        }

        if ($pastOrder === null) {
            $latestStmt = $pdo->prepare(
                "SELECT * FROM orders
                 WHERE customer_id = ? AND status != 'cancelled'
                 ORDER BY CASE WHEN status = 'completed' THEN 0 ELSE 1 END ASC, id DESC
                 LIMIT 1"
            );
            $latestStmt->execute([$userId]);
            $pastOrder = $latestStmt->fetch(PDO::FETCH_ASSOC) ?: null;
            if ($pastOrder !== null && $dateHint === 'yesterday') {
                $usedFallbackLatest = true;
            }
        }

        if ($pastOrder === null) {
            return [
                'ok' => false,
                'code' => 'no_past_orders',
                'message' => 'Tài khoản của mình hiện chưa có đơn hàng cũ nào để đặt lại ạ. Mình muốn ăn món gì nhắn em tìm ngay nhé!',
            ];
        }

        $pastOrderId = (int)$pastOrder['id'];
        $pastCode = (string)$pastOrder['code'];
        $pastStoreId = (int)$pastOrder['store_id'];

        $stStmt = $pdo->prepare('SELECT id, name, is_open, status FROM stores WHERE id = ? AND deleted_at IS NULL LIMIT 1');
        $stStmt->execute([$pastStoreId]);
        $stRow = $stStmt->fetch(PDO::FETCH_ASSOC);
        if (!$stRow || !(bool)$stRow['is_open'] || $stRow['status'] !== 'active') {
            $sName = $stRow['name'] ?? 'nhà hàng trước đó';
            return [
                'ok' => false,
                'code' => 'store_closed',
                'pastOrderCode' => $pastCode,
                'storeName' => $sName,
                'message' => 'Đơn cũ #' . $pastCode . ' thuộc quán ' . $sName . ', nhưng hiện quán đang tạm nghỉ nhận đơn nên chưa đặt lại ngay được ạ.',
            ];
        }
        $storeName = (string)$stRow['name'];

        $oiStmt = $pdo->prepare('SELECT id, dish_id, dish_name, unit_price, quantity, note FROM order_items WHERE order_id = ? ORDER BY id ASC');
        $oiStmt->execute([$pastOrderId]);
        $pastItems = $oiStmt->fetchAll(PDO::FETCH_ASSOC);
        if (!$pastItems) {
            return [
                'ok' => false,
                'code' => 'empty_past_order',
                'pastOrderCode' => $pastCode,
                'message' => 'Đơn cũ #' . $pastCode . ' không còn dữ liệu chi tiết món ăn để nạp lại.',
            ];
        }

        $optStmt = $pdo->prepare('SELECT option_id, option_name FROM order_item_options WHERE order_item_id = ? ORDER BY id ASC');
        $rebuiltItems = [];
        $unavailableItems = [];
        $priceChanges = [];

        foreach ($pastItems as $oi) {
            $dishId = $oi['dish_id'] !== null ? (int)$oi['dish_id'] : 0;
            $dish = $dishId > 0 ? self::getDishSnapshot($pdo, $dishId) : null;
            if ($dish === null || !$dish['isAvailable'] || $dish['storeIdInt'] !== $pastStoreId) {
                $unavailableItems[] = (string)$oi['dish_name'];
                continue;
            }

            $optStmt->execute([(int)$oi['id']]);
            $rawOpts = $optStmt->fetchAll(PDO::FETCH_ASSOC);
            $optIds = [];
            $optNames = [];
            foreach ($rawOpts as $ro) {
                if (!empty($ro['option_id'])) {
                    $optIds[] = (string)$ro['option_id'];
                } elseif (!empty($ro['option_name'])) {
                    $optNames[] = (string)$ro['option_name'];
                }
            }

            $optRes = self::resolveItemOptions($dish, $optIds, $optNames);
            if (!empty($optRes['missingRequiredGroups'])) {
                $unavailableItems[] = (string)$oi['dish_name'];
                continue;
            }

            $qty = max(1, min(50, (int)$oi['quantity']));
            if ($dish['stockQuantity'] !== null && $qty > $dish['stockQuantity']) {
                $qty = $dish['stockQuantity'];
            }

            $histUnitVnd = (int)round((float)$oi['unit_price']);
            $liveUnitTotalVnd = $dish['priceVnd'] + $optRes['optionsExtraVnd'];
            if ($liveUnitTotalVnd !== $histUnitVnd) {
                $priceChanges[] = [
                    'dishName' => $dish['name'],
                    'oldUnitPriceVnd' => $histUnitVnd,
                    'newUnitPriceVnd' => $liveUnitTotalVnd,
                    'deltaVnd' => $liveUnitTotalVnd - $histUnitVnd,
                ];
            }

            $rebuiltItems[] = [
                'lineId' => 'ln_' . bin2hex(random_bytes(4)),
                'dishId' => $dish['id'],
                'restaurantId' => $dish['storeId'],
                'restaurantName' => $dish['storeName'],
                'name' => $dish['name'],
                'imageUrl' => $dish['imageUrl'],
                'category' => $dish['category'],
                'quantity' => $qty,
                'unitPriceVnd' => $dish['priceVnd'],
                'selectedOptions' => $optRes['selectedOptions'],
                'optionsExtraVnd' => $optRes['optionsExtraVnd'],
                'lineTotalVnd' => $liveUnitTotalVnd * $qty,
                'note' => trim((string)($oi['note'] ?? '')),
            ];
        }

        if (!$rebuiltItems) {
            return [
                'ok' => false,
                'code' => 'all_items_unavailable',
                'pastOrderCode' => $pastCode,
                'storeId' => (string)$pastStoreId,
                'storeName' => $storeName,
                'unavailableItems' => $unavailableItems,
                'message' => 'Các món trong đơn cũ #' . $pastCode . ' (' . implode(', ', $unavailableItems) . ') tại ' . $storeName . ' hiện đều tạm hết hàng ạ.',
            ];
        }

        $draft = self::getOrCreateDraft($pdo, $userId, $conversationId, $now);
        $updated = self::persistDraftMutation($pdo, $draft, [
            'store_id' => $pastStoreId,
            'items' => $rebuiltItems,
            'voucher_code' => null,
            'voucher_auto' => 1,
            'scheduled_for' => null,
            'reordered_from_order_id' => $pastOrderId,
            'state' => self::STATE_CHECKOUT_REVIEW,
        ], $now);

        return [
            'ok' => true,
            'pastOrderId' => (string)$pastOrderId,
            'pastOrderCode' => $pastCode,
            'pastOrderDate' => (string)($pastOrder['delivered_at'] ?? $pastOrder['placed_at'] ?? $pastOrder['created_at'] ?? ''),
            'usedFallbackLatest' => $usedFallbackLatest,
            'storeName' => $storeName,
            'priceChanges' => $priceChanges,
            'unavailableItems' => $unavailableItems,
            'draft' => $updated,
        ];
    }

    public static function cancelDraft(PDO $pdo, int $userId, string $draftUid, ?int $nowTs = null): array {
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $nowTs);
        if (in_array($draft['state'], [self::STATE_ORDER_CREATED, self::STATE_PAYMENT_PENDING], true)) {
            throw new MealAdvisorException(409, 'Đơn hàng đã được tạo chính thức, không thể hủy như đơn nháp.');
        }
        $now = $nowTs ?? time();
        $pdo->prepare("UPDATE ai_order_quotes SET status = 'superseded' WHERE draft_id = ? AND status = 'active'")
            ->execute([$draft['idInt']]);
        $pdo->prepare("UPDATE ai_order_drafts SET state = 'CANCELLED', active_quote_id = NULL, version = version + 1, updated_at = ? WHERE id = ?")
            ->execute([self::nowStr($now), $draft['idInt']]);
        return self::getDraftByUid($pdo, $userId, $draftUid, $now);
    }

    private static function transitionState(PDO $pdo, array $draft, string $newState, ?int $nowTs = null): array {
        $now = $nowTs ?? time();
        $pdo->prepare('UPDATE ai_order_drafts SET state = ?, updated_at = ? WHERE id = ?')
            ->execute([$newState, self::nowStr($now), $draft['idInt']]);
        $draft['state'] = $newState;
        return $draft;
    }

    private static function persistDraftMutation(PDO $pdo, array $draft, array $fields, ?int $nowTs = null): array {
        $now = $nowTs ?? time();
        $storeId = array_key_exists('store_id', $fields) ? $fields['store_id'] : $draft['storeIdInt'];
        $items = array_key_exists('items', $fields) ? $fields['items'] : $draft['items'];
        $addressId = array_key_exists('address_id', $fields) ? $fields['address_id'] : $draft['addressIdInt'];
        $voucherCode = array_key_exists('voucher_code', $fields) ? $fields['voucher_code'] : $draft['voucherCode'];
        $voucherAuto = array_key_exists('voucher_auto', $fields) ? (int)$fields['voucher_auto'] : ($draft['voucherAuto'] ? 1 : 0);
        $paymentMethod = array_key_exists('payment_method', $fields) ? $fields['payment_method'] : ($draft['paymentMethod'] ?: 'cash');
        $customerNote = array_key_exists('customer_note', $fields) ? $fields['customer_note'] : $draft['customerNote'];
        $scheduledFor = array_key_exists('scheduled_for', $fields)
            ? $fields['scheduled_for']
            : (($storeId !== null && $storeId === $draft['storeIdInt'] && $items) ? $draft['scheduledFor'] : null);
        $reorderedFromOrderId = array_key_exists('reordered_from_order_id', $fields)
            ? $fields['reordered_from_order_id']
            : (($draft['reorderedFromOrderId'] !== null && $storeId === $draft['storeIdInt'] && $items) ? (int)$draft['reorderedFromOrderId'] : null);
        $state = array_key_exists('state', $fields) ? $fields['state'] : $draft['state'];
        $newVersion = ((int)$draft['version']) + 1;
        $expiresAt = self::nowStr($now + self::DRAFT_TTL_SECONDS);

        // Supersede any previous active quote whenever draft mutates!
        $pdo->prepare("UPDATE ai_order_quotes SET status = 'superseded' WHERE draft_id = ? AND status = 'active'")
            ->execute([$draft['idInt']]);

        $stmt = $pdo->prepare(
            'UPDATE ai_order_drafts
             SET store_id = ?, state = ?, items_json = ?, address_id = ?, voucher_code = ?, voucher_auto = ?,
                 payment_method = ?, customer_note = ?, scheduled_for = ?, reordered_from_order_id = ?,
                 version = ?, active_quote_id = NULL, expires_at = ?, updated_at = ?
             WHERE id = ? AND user_id = ?'
        );
        $stmt->execute([
            $storeId,
            $state,
            json_encode(array_values($items), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            $addressId,
            $voucherCode,
            $voucherAuto,
            $paymentMethod,
            $customerNote,
            $scheduledFor,
            $reorderedFromOrderId,
            $newVersion,
            $expiresAt,
            self::nowStr($now),
            $draft['idInt'],
            $draft['userId'],
        ]);

        return self::getDraftByUid($pdo, $draft['userId'], $draft['draftId'], $now);
    }

    /**
     * Authoritative Server-Side Quote Builder.
     * Re-fetches LIVE prices, store availability, options, user address, distance, ETA, and vouchers.
     * Persists a verifiable quote + confirmationToken when draft is ready for confirmation.
     */
    public static function buildAndPersistQuote(
        PDO $pdo,
        array $config,
        int $userId,
        string $draftUid,
        ?int $nowTs = null
    ): array {
        $now = $nowTs ?? time();
        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $now);
        $addresses = self::getUserAddresses($pdo, $userId);
        $baseFeeVnd = (int)round((float)($config['delivery_fee'] ?? 15000));

        if ($draft['state'] === self::STATE_EXPIRED) {
            return [
                'readyToConfirm' => false,
                'blockingReason' => 'expired_draft',
                'message' => 'Đơn nháp đã hết hạn. Mình chọn lại món giúp em nhé!',
                'draft' => $draft,
                'quote' => null,
                'addresses' => $addresses,
                'vouchers' => ['eligible' => [], 'best' => null],
            ];
        }

        if (!$draft['items'] || $draft['storeIdInt'] === null) {
            return [
                'readyToConfirm' => false,
                'blockingReason' => 'empty_items',
                'message' => 'Đơn nháp chưa có món ăn nào.',
                'draft' => $draft,
                'quote' => null,
                'addresses' => $addresses,
                'vouchers' => ['eligible' => [], 'best' => null],
            ];
        }

        // Re-verify store & every item from live DB
        $verifiedItems = [];
        $subtotalVnd = 0;
        $itemPrepList = [];
        $storeSnapshot = null;
        $warnings = [];

        foreach ($draft['items'] as $item) {
            $dish = self::getDishSnapshot($pdo, (int)$item['dishId']);
            if ($dish === null || !$dish['isAvailable']) {
                $draft = self::transitionState($pdo, $draft, self::STATE_OUT_OF_STOCK, $now);
                return [
                    'readyToConfirm' => false,
                    'blockingReason' => 'out_of_stock',
                    'unavailableDishName' => $item['name'] ?? 'Món đã chọn',
                    'message' => 'Món ' . ($item['name'] ?? '') . ' vừa hết hàng. Mình đổi sang món khác giúp em nhé!',
                    'draft' => $draft,
                    'quote' => null,
                    'addresses' => $addresses,
                    'vouchers' => ['eligible' => [], 'best' => null],
                ];
            }
            if (!$dish['storeOpen']) {
                $draft = self::transitionState($pdo, $draft, self::STATE_VALIDATION_FAILED, $now);
                return [
                    'readyToConfirm' => false,
                    'blockingReason' => 'store_closed',
                    'message' => 'Quán ' . $dish['storeName'] . ' hiện đã đóng cửa.',
                    'draft' => $draft,
                    'quote' => null,
                    'addresses' => $addresses,
                    'vouchers' => ['eligible' => [], 'best' => null],
                ];
            }
            $storeSnapshot = $dish;
            $qty = max(1, min(50, (int)($item['quantity'] ?? 1)));
            if ($dish['stockQuantity'] !== null && $qty > $dish['stockQuantity']) {
                $qty = $dish['stockQuantity'];
                $warnings[] = 'Số lượng món ' . $dish['name'] . ' được điều chỉnh về mức tồn kho còn lại (' . $qty . ' phần).';
            }

            $optIds = array_column($item['selectedOptions'] ?? [], 'optionId');
            $optRes = self::resolveItemOptions($dish, $optIds, []);
            $unitVnd = $dish['priceVnd'];
            $extraVnd = $optRes['optionsExtraVnd'];
            $lineVnd = ($unitVnd + $extraVnd) * $qty;
            if ((int)($item['unitPriceVnd'] ?? $unitVnd) !== $unitVnd) {
                $warnings[] = 'Giá món ' . $dish['name'] . ' vừa cập nhật thành ' . self::moneyVnd($unitVnd) . '.';
            }
            if ($dish['prepMinutes'] !== null) {
                $itemPrepList[] = $dish['prepMinutes'];
            }
            $subtotalVnd += $lineVnd;
            $verifiedItems[] = [
                'lineId' => $item['lineId'] ?? ('ln_' . $dish['id']),
                'dishId' => $dish['id'],
                'restaurantId' => $dish['storeId'],
                'restaurantName' => $dish['storeName'],
                'name' => $dish['name'],
                'imageUrl' => $dish['imageUrl'],
                'category' => $dish['category'],
                'quantity' => $qty,
                'unitPriceVnd' => $unitVnd,
                'selectedOptions' => $optRes['selectedOptions'],
                'optionsExtraVnd' => $extraVnd,
                'lineTotalVnd' => $lineVnd,
                'note' => (string)($item['note'] ?? ''),
            ];
        }

        // Resolve delivery address
        $selectedAddress = null;
        if ($draft['addressIdInt'] !== null) {
            foreach ($addresses as $a) {
                if ($a['idInt'] === $draft['addressIdInt']) {
                    $selectedAddress = $a;
                    break;
                }
            }
        }
        if ($selectedAddress === null && isset($addresses[0])) {
            $selectedAddress = $addresses[0];
            $draft = self::setDraftAddress($pdo, $userId, $draftUid, $selectedAddress['idInt'], $now);
        }

        $distanceKm = null;
        if ($selectedAddress !== null && $storeSnapshot !== null) {
            $distanceKm = self::distanceKm(
                $storeSnapshot['storeLat'],
                $storeSnapshot['storeLng'],
                $selectedAddress['latitude'],
                $selectedAddress['longitude']
            );
            if ($distanceKm !== null && $distanceKm > self::MAX_DELIVERY_KM) {
                $draft = self::transitionState($pdo, $draft, self::STATE_DELIVERY_ADDRESS, $now);
                return [
                    'readyToConfirm' => false,
                    'blockingReason' => 'out_of_delivery_range',
                    'distanceKm' => $distanceKm,
                    'message' => 'Địa chỉ "' . $selectedAddress['addressLine'] . '" cách quán ' . $distanceKm . ' km (vượt phạm vi giao tối đa ' . self::MAX_DELIVERY_KM . ' km). Mình chọn địa chỉ gần hơn nhé!',
                    'draft' => $draft,
                    'quote' => null,
                    'addresses' => $addresses,
                    'vouchers' => ['eligible' => [], 'best' => null],
                ];
            }
        }

        $deliveryFeeVnd = self::shippingFeeVnd($distanceKm, $baseFeeVnd);
        $eta = self::estimateEta($distanceKm, $storeSnapshot['storePrepMinutes'] ?? null, $itemPrepList);

        // Voucher discovery & selection
        $voucherDiscovery = self::discoverVouchers(
            $pdo,
            (int)$storeSnapshot['storeIdInt'],
            $subtotalVnd,
            $deliveryFeeVnd,
            $draft['voucherCode'],
            $now
        );

        $appliedVoucher = null;
        if ($draft['voucherCode'] === 'NONE') {
            $appliedVoucher = null;
        } elseif ($draft['voucherCode'] !== null && $draft['voucherCode'] !== '') {
            if (!empty($voucherDiscovery['requested']['eligible'])) {
                $appliedVoucher = $voucherDiscovery['requested'];
            } else {
                $reason = $voucherDiscovery['requested']['ineligibleReason'] ?? 'không đủ điều kiện áp dụng';
                $warnings[] = 'Mã ' . $draft['voucherCode'] . ' chưa áp dụng được (' . $reason . ').';
                if ($draft['voucherAuto'] && $voucherDiscovery['best'] !== null) {
                    $appliedVoucher = $voucherDiscovery['best'];
                }
            }
        } elseif ($draft['voucherAuto'] && $voucherDiscovery['best'] !== null) {
            $appliedVoucher = $voucherDiscovery['best'];
        }

        $foodDiscountVnd = $appliedVoucher !== null ? (int)$appliedVoucher['foodDiscountVnd'] : 0;
        $deliveryDiscountVnd = $appliedVoucher !== null ? (int)$appliedVoucher['deliveryDiscountVnd'] : 0;
        $totalDiscountVnd = min($subtotalVnd + $deliveryFeeVnd, $foodDiscountVnd + $deliveryDiscountVnd);
        $serviceFeeVnd = 0;
        $taxVnd = 0;
        $totalVnd = max(0, $subtotalVnd + $deliveryFeeVnd + $serviceFeeVnd + $taxVnd - $totalDiscountVnd);
        $paymentMethod = in_array($draft['paymentMethod'], ['cash', 'payos'], true) ? $draft['paymentMethod'] : 'cash';

        // Scheduled delivery validation & upcoming slot options + Smart Consent-Based Voucher Upsell
        $storeHours = self::getStoreHours($pdo, (int)$storeSnapshot['storeIdInt']);
        $availableScheduleSlots = self::buildAvailableScheduleSlots($storeHours['openTime'], $storeHours['closeTime'], $now);
        $validScheduledFor = null;
        $scheduledForDisplay = null;
        if (!empty($draft['scheduledFor'])) {
            $sCheck = self::validateScheduleTime($draft['scheduledFor'], $storeHours['openTime'], $storeHours['closeTime'], $now, true);
            if ($sCheck['valid']) {
                $validScheduledFor = $sCheck['normalized'];
                $scheduledForDisplay = $sCheck['display'];
            } else {
                $warnings[] = 'Giờ hẹn giao trước đó không còn hợp lệ (' . $sCheck['reason'] . ') nên đơn được chuyển về Giao ngay.';
                $pdo->prepare('UPDATE ai_order_drafts SET scheduled_for = NULL WHERE id = ?')->execute([$draft['idInt']]);
                $draft['scheduledFor'] = null;
            }
        }

        $upsellSuggestion = self::findSmartUpsell(
            $pdo,
            (int)$storeSnapshot['storeIdInt'],
            $verifiedItems,
            $subtotalVnd,
            $deliveryFeeVnd,
            $totalDiscountVnd,
            $now
        );

        // Check if address & receiver phone are complete so quote can enter AWAITING_FINAL_CONFIRMATION
        if ($selectedAddress === null || $selectedAddress['addressLine'] === '' || $selectedAddress['receiverPhone'] === '') {
            $draft = self::transitionState($pdo, $draft, self::STATE_DELIVERY_ADDRESS, $now);
            return [
                'readyToConfirm' => false,
                'blockingReason' => 'missing_address',
                'message' => 'Mình chưa có địa chỉ hoặc số điện thoại nhận hàng đã lưu. Mình thêm hoặc chọn địa chỉ nhận hàng trước khi xác nhận đơn nhé!',
                'draft' => $draft,
                'quote' => [
                    'quoteId' => null,
                    'draftId' => $draft['draftId'],
                    'draftVersion' => $draft['version'],
                    'currency' => 'VND',
                    'restaurantId' => $storeSnapshot['storeId'],
                    'restaurantName' => $storeSnapshot['storeName'],
                    'restaurantAddress' => $storeSnapshot['storeAddress'],
                    'storeOpenHours' => $storeHours['openLabel'] . '–' . $storeHours['closeLabel'],
                    'items' => $verifiedItems,
                    'subtotal' => $subtotalVnd,
                    'deliveryFee' => $deliveryFeeVnd,
                    'discount' => $totalDiscountVnd,
                    'foodDiscount' => $foodDiscountVnd,
                    'deliveryDiscount' => $deliveryDiscountVnd,
                    'serviceFee' => $serviceFeeVnd,
                    'tax' => $taxVnd,
                    'total' => $totalVnd,
                    'appliedVoucher' => $appliedVoucher,
                    'address' => null,
                    'paymentMethod' => strtoupper($paymentMethod === 'payos' ? 'PAYOS' : 'COD'),
                    'scheduledFor' => $validScheduledFor,
                    'scheduledForDisplay' => $scheduledForDisplay,
                    'availableScheduleSlots' => $availableScheduleSlots,
                    'upsellSuggestion' => $upsellSuggestion,
                    'estimatedDeliveryMinutes' => $eta,
                    'warnings' => $warnings,
                    'requiresConfirmation' => true,
                    'canConfirmNow' => false,
                ],
                'addresses' => $addresses,
                'vouchers' => $voucherDiscovery,
            ];
        }

        // Reuse existing active quote if draft version & total & expires_at still valid, else mint fresh quote
        $quoteUid = 'qt_' . bin2hex(random_bytes(10));
        $confirmationToken = hash('sha256', $quoteUid . '|' . $userId . '|' . $draft['draftId'] . '|' . $draft['version'] . '|' . $totalVnd . '|' . ($validScheduledFor ?? 'NOW') . '|' . bin2hex(random_bytes(16)));
        $expiresAtStr = self::nowStr($now + self::QUOTE_TTL_SECONDS);

        $quotePayload = [
            'quoteId' => $quoteUid,
            'draftId' => $draft['draftId'],
            'draftVersion' => $draft['version'],
            'currency' => 'VND',
            'restaurantId' => $storeSnapshot['storeId'],
            'restaurantName' => $storeSnapshot['storeName'],
            'restaurantAddress' => $storeSnapshot['storeAddress'],
            'storeOpenHours' => $storeHours['openLabel'] . '–' . $storeHours['closeLabel'],
            'items' => $verifiedItems,
            'subtotal' => $subtotalVnd,
            'deliveryFee' => $deliveryFeeVnd,
            'discount' => $totalDiscountVnd,
            'foodDiscount' => $foodDiscountVnd,
            'deliveryDiscount' => $deliveryDiscountVnd,
            'serviceFee' => $serviceFeeVnd,
            'tax' => $taxVnd,
            'total' => $totalVnd,
            'appliedVoucher' => $appliedVoucher,
            'address' => $selectedAddress,
            'paymentMethod' => strtoupper($paymentMethod === 'payos' ? 'PAYOS' : 'COD'),
            'customerNote' => $draft['customerNote'],
            'scheduledFor' => $validScheduledFor,
            'scheduledForDisplay' => $scheduledForDisplay,
            'availableScheduleSlots' => $availableScheduleSlots,
            'upsellSuggestion' => $upsellSuggestion,
            'reorderedFromOrderId' => $draft['reorderedFromOrderId'],
            'estimatedDeliveryMinutes' => $eta,
            'expiresAt' => $expiresAtStr,
            'expiresInSeconds' => self::QUOTE_TTL_SECONDS,
            'confirmationToken' => $confirmationToken,
            'warnings' => $warnings,
            'requiresConfirmation' => true,
            'canConfirmNow' => true,
        ];

        $pdo->prepare("UPDATE ai_order_quotes SET status = 'superseded' WHERE draft_id = ? AND status = 'active'")
            ->execute([$draft['idInt']]);

        $insQuote = $pdo->prepare(
            'INSERT INTO ai_order_quotes
             (quote_uid, draft_id, user_id, draft_version, store_id, address_id, voucher_id, voucher_code,
              payment_method, scheduled_for, subtotal_vnd, delivery_fee_vnd, discount_vnd, delivery_discount_vnd,
              service_fee_vnd, tax_vnd, total_vnd, eta_min_minutes, eta_max_minutes,
              quote_payload_json, confirmation_token, status, expires_at, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, \'active\', ?, ?)'
        );
        $insQuote->execute([
            $quoteUid,
            $draft['idInt'],
            $userId,
            $draft['version'],
            $storeSnapshot['storeIdInt'],
            $selectedAddress['idInt'],
            $appliedVoucher !== null ? $appliedVoucher['idInt'] : null,
            $appliedVoucher !== null ? $appliedVoucher['code'] : null,
            $paymentMethod,
            $validScheduledFor,
            $subtotalVnd,
            $deliveryFeeVnd,
            $totalDiscountVnd,
            $deliveryDiscountVnd,
            $serviceFeeVnd,
            $taxVnd,
            $totalVnd,
            $eta['min'],
            $eta['max'],
            json_encode($quotePayload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            $confirmationToken,
            $expiresAtStr,
            self::nowStr($now),
        ]);

        $pdo->prepare('UPDATE ai_order_drafts SET state = ?, items_json = ?, active_quote_id = ?, updated_at = ? WHERE id = ?')
            ->execute([
                self::STATE_AWAITING_CONFIRMATION,
                json_encode($verifiedItems, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
                $quoteUid,
                self::nowStr($now),
                $draft['idInt'],
            ]);

        $draft['state'] = self::STATE_AWAITING_CONFIRMATION;
        $draft['activeQuoteId'] = $quoteUid;
        $draft['items'] = $verifiedItems;

        return [
            'readyToConfirm' => true,
            'blockingReason' => null,
            'draft' => $draft,
            'quote' => $quotePayload,
            'addresses' => $addresses,
            'vouchers' => $voucherDiscovery,
        ];
    }

    /**
     * Final Transaction-Safe & Idempotent Order Submission.
     * NEVER creates an order without valid quoteUid + confirmationToken + matching live totalVnd.
     */
    public static function submitConfirmedOrder(
        PDO $pdo,
        array $config,
        array $me,
        string $draftUid,
        string $quoteUid,
        string $confirmationToken,
        string $idempotencyKey,
        ?int $expectedTotalVnd = null,
        ?int $nowTs = null
    ): array {
        self::ensureSchema($pdo);
        $userId = self::assertCustomer($me);
        $now = $nowTs ?? time();
        $cleanIdem = trim($idempotencyKey);
        if ($cleanIdem === '' || strlen($cleanIdem) > 120) {
            throw new MealAdvisorException(422, 'Thiếu hoặc sai khóa chống trùng đơn (idempotencyKey).');
        }
        if ($quoteUid === '' || $confirmationToken === '') {
            throw new MealAdvisorException(422, 'Thiếu mã báo giá hoặc mã xác nhận đơn hàng.');
        }

        // 1. Check Idempotency Table First (Replay Protection)
        $idemCheck = $pdo->prepare('SELECT * FROM ai_order_idempotency WHERE user_id = ? AND idempotency_key = ? LIMIT 1');
        $idemCheck->execute([$userId, $cleanIdem]);
        $existingIdem = $idemCheck->fetch(PDO::FETCH_ASSOC);
        if ($existingIdem) {
            if ($existingIdem['status'] === 'completed' && !empty($existingIdem['response_json'])) {
                $saved = json_decode((string)$existingIdem['response_json'], true);
                if (is_array($saved)) {
                    $saved['idempotentReplay'] = true;
                    return $saved;
                }
            }
            if ($existingIdem['status'] === 'processing') {
                throw new MealAdvisorException(409, 'Yêu cầu đặt hàng đang được xử lý, vui lòng không bấm lặp lại.');
            }
        }

        // Also check if this exact quoteUid was ALREADY confirmed under another idempotencyKey!
        $qStmt = $pdo->prepare('SELECT * FROM ai_order_quotes WHERE quote_uid = ? LIMIT 1');
        $qStmt->execute([$quoteUid]);
        $quoteRow = $qStmt->fetch(PDO::FETCH_ASSOC);
        if (!$quoteRow) {
            throw new MealAdvisorException(404, 'Báo giá không tồn tại hoặc đã bị thay thế.');
        }
        if ((int)$quoteRow['user_id'] !== $userId) {
            throw new MealAdvisorException(403, 'Bạn không có quyền xác nhận báo giá của tài khoản khác.');
        }

        $draft = self::getDraftByUid($pdo, $userId, $draftUid, $now);
        if ((int)$quoteRow['draft_id'] !== $draft['idInt']) {
            throw new MealAdvisorException(422, 'Báo giá không thuộc đơn nháp hiện tại.');
        }

        // If quote was already confirmed, return the already-created order idempotently instead of creating a duplicate!
        if ($quoteRow['status'] === 'confirmed' && $draft['submittedOrderId'] !== null) {
            $ordStmt = $pdo->prepare('SELECT id, code, status, total_amount, payment_method, payment_status, store_id FROM orders WHERE id = ? AND customer_id = ? LIMIT 1');
            $ordStmt->execute([(int)$draft['submittedOrderId'], $userId]);
            $ord = $ordStmt->fetch(PDO::FETCH_ASSOC);
            if ($ord) {
                return [
                    'ok' => true,
                    'idempotentReplay' => true,
                    'order' => [
                        'id' => (string)$ord['id'],
                        'code' => (string)$ord['code'],
                        'status' => strtoupper((string)$ord['status']),
                        'paymentMethod' => strtoupper((string)$ord['payment_method']),
                        'paymentStatus' => strtoupper((string)$ord['payment_status']),
                        'totalVnd' => (int)round((float)$ord['total_amount']),
                        'restaurantId' => (string)$ord['store_id'],
                        'requiresOnlinePayment' => $ord['payment_method'] === 'payos' && $ord['payment_status'] !== 'paid',
                    ],
                ];
            }
        }

        if (!hash_equals((string)$quoteRow['confirmation_token'], $confirmationToken)) {
            throw new MealAdvisorException(403, 'Mã xác nhận đơn hàng không hợp lệ.');
        }
        if ($draft['state'] === self::STATE_CANCELLED) {
            throw new MealAdvisorException(409, 'Đơn nháp đã được hủy trước đó.');
        }
        if ($quoteRow['status'] !== 'active' || (int)$quoteRow['draft_version'] !== $draft['version'] || $draft['activeQuoteId'] !== $quoteUid) {
            $fresh = self::buildAndPersistQuote($pdo, $config, $userId, $draftUid, $now);
            return [
                'ok' => false,
                'code' => 'quote_superseded',
                'message' => 'Thông tin đơn nháp đã thay đổi so với báo giá cũ. Mình kiểm tra và xác nhận lại báo giá mới giúp em nhé!',
                'freshQuote' => $fresh['quote'] ?? null,
                'draft' => $fresh['draft'] ?? $draft,
            ];
        }
        $quoteExp = strtotime((string)$quoteRow['expires_at']);
        if ($quoteExp !== false && $quoteExp < $now) {
            $pdo->prepare("UPDATE ai_order_quotes SET status = 'expired' WHERE id = ?")->execute([$quoteRow['id']]);
            $fresh = self::buildAndPersistQuote($pdo, $config, $userId, $draftUid, $now);
            return [
                'ok' => false,
                'code' => 'quote_expired',
                'message' => 'Báo giá trước đó đã hết hiệu lực. Em vừa cập nhật báo giá mới, mình bấm xác nhận lại nhé!',
                'freshQuote' => $fresh['quote'] ?? null,
                'draft' => $fresh['draft'] ?? $draft,
            ];
        }

        $isSqlite = $pdo->getAttribute(PDO::ATTR_DRIVER_NAME) === 'sqlite';
        $forUpdate = $isSqlite ? '' : ' FOR UPDATE';
        $baseFeeVnd = (int)round((float)($config['delivery_fee'] ?? 15000));

        // Record processing idempotency marker
        if (!$existingIdem) {
            $pdo->prepare(
                "INSERT INTO ai_order_idempotency (user_id, idempotency_key, draft_uid, quote_uid, status, created_at, updated_at)
                 VALUES (?, ?, ?, ?, 'processing', ?, ?)"
            )->execute([$userId, $cleanIdem, $draftUid, $quoteUid, self::nowStr($now), self::nowStr($now)]);
        } else {
            $pdo->prepare("UPDATE ai_order_idempotency SET status = 'processing', updated_at = ? WHERE id = ?")
                ->execute([self::nowStr($now), $existingIdem['id']]);
        }

        $pdo->beginTransaction();
        try {
            $lockDraft = $pdo->prepare('SELECT * FROM ai_order_drafts WHERE id = ? AND user_id = ?' . $forUpdate);
            $lockDraft->execute([$draft['idInt'], $userId]);
            $lockedDraftRow = $lockDraft->fetch(PDO::FETCH_ASSOC);
            if (!$lockedDraftRow || (int)$lockedDraftRow['version'] !== (int)$quoteRow['draft_version']) {
                $pdo->rollBack();
                $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                    ->execute([self::nowStr($now), $userId, $cleanIdem]);
                $fresh = self::buildAndPersistQuote($pdo, $config, $userId, $draftUid, $now);
                return [
                    'ok' => false,
                    'code' => 'quote_superseded',
                    'message' => 'Đơn nháp vừa được cập nhật. Mình xác nhận lại báo giá mới nhé!',
                    'freshQuote' => $fresh['quote'] ?? null,
                    'draft' => $fresh['draft'] ?? $draft,
                ];
            }
            if ($lockedDraftRow['submitted_order_id'] !== null) {
                $pdo->rollBack();
                throw new MealAdvisorException(409, 'Đơn hàng này đã được xác nhận tạo thành công trước đó.');
            }

            $storeId = (int)$quoteRow['store_id'];
            $storeStmt = $pdo->prepare("SELECT id, name, latitude, longitude, is_open, status FROM stores WHERE id = ? AND deleted_at IS NULL" . $forUpdate);
            $storeStmt->execute([$storeId]);
            $storeRow = $storeStmt->fetch(PDO::FETCH_ASSOC);
            if (!$storeRow || !(bool)$storeRow['is_open'] || $storeRow['status'] !== 'active') {
                $pdo->rollBack();
                $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                    ->execute([self::nowStr($now), $userId, $cleanIdem]);
                self::transitionState($pdo, $draft, self::STATE_VALIDATION_FAILED, $now);
                return [
                    'ok' => false,
                    'code' => 'store_closed',
                    'message' => 'Rất tiếc quán vừa đóng cửa nên chưa thể tạo đơn lúc này ạ.',
                ];
            }

            // Revalidate address ownership
            $addrStmt = $pdo->prepare('SELECT * FROM user_addresses WHERE id = ? AND user_id = ? LIMIT 1');
            $addrStmt->execute([(int)$quoteRow['address_id'], $userId]);
            $addrRow = $addrStmt->fetch(PDO::FETCH_ASSOC);
            if (!$addrRow || trim((string)$addrRow['address_line']) === '' || trim((string)$addrRow['receiver_phone']) === '') {
                $pdo->rollBack();
                $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                    ->execute([self::nowStr($now), $userId, $cleanIdem]);
                return [
                    'ok' => false,
                    'code' => 'invalid_address',
                    'message' => 'Địa chỉ giao hàng đã bị xóa hoặc thiếu số điện thoại nhận hàng. Mình chọn lại địa chỉ nhé!',
                ];
            }

            // Revalidate every item & live price & stock
            $draftItems = json_decode((string)$lockedDraftRow['items_json'], true) ?: [];
            $liveSubtotalVnd = 0;
            $verifiedOrderLines = [];
            foreach ($draftItems as $item) {
                $dishId = (int)($item['dishId'] ?? 0);
                $qty = max(1, min(50, (int)($item['quantity'] ?? 1)));
                $dStmt = $pdo->prepare('SELECT id, store_id, name, price, is_available, stock_quantity FROM dishes WHERE id = ? AND deleted_at IS NULL' . $forUpdate);
                $dStmt->execute([$dishId]);
                $dRow = $dStmt->fetch(PDO::FETCH_ASSOC);
                if (!$dRow || (int)$dRow['store_id'] !== $storeId || !(bool)$dRow['is_available']
                    || ($dRow['stock_quantity'] !== null && (int)$dRow['stock_quantity'] < $qty)
                ) {
                    $pdo->rollBack();
                    $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                        ->execute([self::nowStr($now), $userId, $cleanIdem]);
                    self::transitionState($pdo, $draft, self::STATE_OUT_OF_STOCK, $now);
                    $alts = MenuKnowledge::candidates($pdo, 10000000, false, (string)($item['category'] ?? $item['name'] ?? ''), [], (string)$storeId);
                    return [
                        'ok' => false,
                        'code' => 'out_of_stock',
                        'unavailableDishName' => (string)($item['name'] ?? 'Món đã chọn'),
                        'alternatives' => array_slice($alts, 0, 3),
                        'message' => 'Món ' . ($item['name'] ?? '') . ' vừa hết hàng ngay trước lúc tạo đơn. Em chưa tạo đơn để mình chọn món thay thế nhé!',
                    ];
                }

                // Revalidate options if any
                $liveUnitVnd = (int)round((float)$dRow['price']);
                $liveExtraVnd = 0;
                $liveOptions = [];
                foreach ($item['selectedOptions'] ?? [] as $selOpt) {
                    $optId = (int)($selOpt['optionId'] ?? 0);
                    $oStmt = $pdo->prepare('SELECT o.id, o.name, o.extra_price, o.is_available FROM options o JOIN dish_option_groups dog ON dog.option_group_id = o.option_group_id WHERE o.id = ? AND dog.dish_id = ? LIMIT 1');
                    $oStmt->execute([$optId, $dishId]);
                    $oRow = $oStmt->fetch(PDO::FETCH_ASSOC);
                    if (!$oRow || !(bool)$oRow['is_available']) {
                        $pdo->rollBack();
                        $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                            ->execute([self::nowStr($now), $userId, $cleanIdem]);
                        return [
                            'ok' => false,
                            'code' => 'option_unavailable',
                            'message' => 'Tùy chọn "' . ($selOpt['name'] ?? '') . '" vừa tạm hết. Mình kiểm tra lại món nhé!',
                        ];
                    }
                    $ep = (int)round((float)$oRow['extra_price']);
                    $liveExtraVnd += $ep;
                    $liveOptions[] = ['optionId' => $optId, 'name' => (string)$oRow['name'], 'extraPriceVnd' => $ep];
                }

                $lineTotalVnd = ($liveUnitVnd + $liveExtraVnd) * $qty;
                $liveSubtotalVnd += $lineTotalVnd;
                $verifiedOrderLines[] = [
                    'dishId' => (int)$dRow['id'],
                    'dishName' => (string)$dRow['name'],
                    'unitPriceVnd' => $liveUnitVnd + $liveExtraVnd,
                    'quantity' => $qty,
                    'lineTotalVnd' => $lineTotalVnd,
                    'note' => trim((string)($item['note'] ?? '')),
                    'hasStockTracking' => $dRow['stock_quantity'] !== null,
                    'options' => $liveOptions,
                ];
            }

            $distKm = self::distanceKm(
                $storeRow['latitude'] !== null ? (float)$storeRow['latitude'] : null,
                $storeRow['longitude'] !== null ? (float)$storeRow['longitude'] : null,
                $addrRow['latitude'] !== null ? (float)$addrRow['latitude'] : null,
                $addrRow['longitude'] !== null ? (float)$addrRow['longitude'] : null
            );
            $liveDeliveryFeeVnd = self::shippingFeeVnd($distKm, $baseFeeVnd);

            // Revalidate voucher inside transaction with FOR UPDATE
            $quotedVoucherId = $quoteRow['voucher_id'] !== null ? (int)$quoteRow['voucher_id'] : null;
            $liveDiscountVnd = 0;
            if ($quotedVoucherId !== null && $quotedVoucherId > 0) {
                $vStmt = $pdo->prepare('SELECT * FROM vouchers WHERE id = ?' . $forUpdate);
                $vStmt->execute([$quotedVoucherId]);
                $vRow = $vStmt->fetch(PDO::FETCH_ASSOC);
                $vStarts = !empty($vRow['starts_at']) ? strtotime((string)$vRow['starts_at']) : null;
                $vEnds = !empty($vRow['ends_at']) ? strtotime((string)$vRow['ends_at']) : null;
                $vValid = $vRow
                    && (bool)$vRow['is_active']
                    && ($vRow['store_id'] === null || (int)$vRow['store_id'] === 0 || (int)$vRow['store_id'] === $storeId)
                    && ($vStarts === null || $vStarts === false || $vStarts <= $now)
                    && ($vEnds === null || $vEnds === false || $vEnds >= $now)
                    && ($vRow['usage_limit'] === null || (int)$vRow['usage_limit'] === 0 || (int)$vRow['used_count'] < (int)$vRow['usage_limit'])
                    && ($liveSubtotalVnd >= (int)round((float)$vRow['min_order_amount']));

                if (!$vValid) {
                    $pdo->rollBack();
                    $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                        ->execute([self::nowStr($now), $userId, $cleanIdem]);
                    // Clear invalid voucher and build fresh quote so user can review new price!
                    self::setDraftVoucher($pdo, $userId, $draftUid, null, true, $now);
                    $fresh = self::buildAndPersistQuote($pdo, $config, $userId, $draftUid, $now);
                    return [
                        'ok' => false,
                        'code' => 'voucher_expired_or_exhausted',
                        'message' => 'Mã giảm giá trên báo giá vừa hết hạn hoặc hết lượt dùng. Em chưa tạo đơn và đã tính lại báo giá mới để mình xác nhận lại nhé!',
                        'freshQuote' => $fresh['quote'] ?? null,
                        'draft' => $fresh['draft'] ?? $draft,
                    ];
                }

                $vType = (string)$vRow['discount_type'];
                $vVal = (float)$vRow['discount_value'];
                $vCap = $vRow['max_discount'] !== null && (float)$vRow['max_discount'] > 0 ? (int)round((float)$vRow['max_discount']) : null;
                if ($vType === 'percentage') {
                    $raw = (int)floor(($liveSubtotalVnd * $vVal) / 100.0);
                    $liveDiscountVnd = min($liveSubtotalVnd, $vCap !== null ? min($raw, $vCap) : $raw);
                } elseif ($vType === 'fixed') {
                    $liveDiscountVnd = min($liveSubtotalVnd + $liveDeliveryFeeVnd, (int)round($vVal));
                } elseif ($vType === 'free_shipping') {
                    $liveDiscountVnd = min($liveDeliveryFeeVnd, $vCap !== null ? min($liveDeliveryFeeVnd, $vCap) : $liveDeliveryFeeVnd);
                }
            }

            $liveTotalVnd = max(0, $liveSubtotalVnd + $liveDeliveryFeeVnd - $liveDiscountVnd);
            $quotedTotalVnd = (int)$quoteRow['total_vnd'];

            // MANDATORY PRICE CHANGE SAFETY: NEVER submit silently if live total differs from confirmed quote!
            if ($liveTotalVnd !== $quotedTotalVnd || ($expectedTotalVnd !== null && $expectedTotalVnd !== $liveTotalVnd)) {
                $pdo->rollBack();
                $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                    ->execute([self::nowStr($now), $userId, $cleanIdem]);
                $fresh = self::buildAndPersistQuote($pdo, $config, $userId, $draftUid, $now);
                return [
                    'ok' => false,
                    'code' => 'price_changed',
                    'previousTotalVnd' => $quotedTotalVnd,
                    'newTotalVnd' => $liveTotalVnd,
                    'message' => 'Tổng tiền đơn vừa thay đổi từ ' . self::moneyVnd($quotedTotalVnd) . ' sang ' . self::moneyVnd($liveTotalVnd) . '. Em chưa tạo đơn — mình kiểm tra và xác nhận lại mức giá mới nhé!',
                    'freshQuote' => $fresh['quote'] ?? null,
                    'draft' => $fresh['draft'] ?? $draft,
                ];
            }

            // Decrement stock_quantity if tracked
            $decStock = $pdo->prepare('UPDATE dishes SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity IS NOT NULL');
            foreach ($verifiedOrderLines as $line) {
                if ($line['hasStockTracking']) {
                    $decStock->execute([$line['quantity'], $line['dishId']]);
                }
            }

            $paymentMethod = $quoteRow['payment_method'] === 'payos' ? 'payos' : 'cash';
            $initialStatus = $paymentMethod === 'payos' ? 'pending_payment' : 'pending';
            $orderCode = 'FH' . date('ymd', $now) . strtoupper(bin2hex(random_bytes(4)));

            // Revalidate scheduled_for if present on quote/draft and attach clearly to order note & status log
            $quotedScheduledFor = !empty($quoteRow['scheduled_for'])
                ? (string)$quoteRow['scheduled_for']
                : (!empty($lockedDraftRow['scheduled_for']) ? (string)$lockedDraftRow['scheduled_for'] : null);
            $scheduledForDisplay = null;
            $finalCustomerNote = trim((string)($lockedDraftRow['customer_note'] ?? ''));
            $statusLogNote = 'Đặt đơn qua trợ lý FoodHub AI';

            if ($quotedScheduledFor !== null) {
                $storeHours = self::getStoreHours($pdo, $storeId);
                $schedVal = self::validateScheduleTime($quotedScheduledFor, $storeHours['openTime'], $storeHours['closeTime'], $now, true);
                if (!$schedVal['valid']) {
                    $pdo->rollBack();
                    $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                        ->execute([self::nowStr($now), $userId, $cleanIdem]);
                    self::setDraftSchedule($pdo, $userId, $draftUid, null, $now);
                    $fresh = self::buildAndPersistQuote($pdo, $config, $userId, $draftUid, $now);
                    return [
                        'ok' => false,
                        'code' => 'schedule_expired',
                        'message' => 'Khung giờ hẹn giao vừa hết hiệu lực (' . $schedVal['reason'] . '). Em chưa tạo đơn và đã chuyển tạm về Giao ngay — mình chọn lại khung giờ hoặc xác nhận nhé!',
                        'freshQuote' => $fresh['quote'] ?? null,
                        'draft' => $fresh['draft'] ?? $draft,
                    ];
                }
                $quotedScheduledFor = $schedVal['normalized'];
                $scheduledForDisplay = $schedVal['display'];
                $schedTag = '[Hẹn giao: ' . date('H:i d/m/Y', (int)strtotime((string)$quotedScheduledFor)) . ']';
                $finalCustomerNote = $finalCustomerNote !== '' ? ($schedTag . ' ' . $finalCustomerNote) : $schedTag;
                $statusLogNote .= ' ' . $schedTag;
            }

            $insOrder = $pdo->prepare(
                'INSERT INTO orders
                 (code, customer_id, store_id, status, receiver_name, receiver_phone, delivery_address,
                  delivery_lat, delivery_lng, customer_note, subtotal, delivery_fee, discount_amount,
                  total_amount, payment_method, payment_status, placed_at, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, \'pending\', ?, ?, ?)'
            );
            $nowStr = self::nowStr($now);
            $insOrder->execute([
                $orderCode,
                $userId,
                $storeId,
                $initialStatus,
                (string)$addrRow['receiver_name'],
                (string)$addrRow['receiver_phone'],
                (string)$addrRow['address_line'],
                $addrRow['latitude'],
                $addrRow['longitude'],
                $finalCustomerNote !== '' ? $finalCustomerNote : null,
                $liveSubtotalVnd,
                $liveDeliveryFeeVnd,
                $liveDiscountVnd,
                $liveTotalVnd,
                $paymentMethod,
                $nowStr,
                $nowStr,
                $nowStr,
            ]);
            $orderId = (int)$pdo->lastInsertId();

            $insItem = $pdo->prepare(
                'INSERT INTO order_items (order_id, dish_id, dish_name, unit_price, quantity, line_total, note, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
            );
            $insOpt = $pdo->prepare(
                'INSERT INTO order_item_options (order_item_id, option_id, option_name, extra_price)
                 VALUES (?, ?, ?, ?)'
            );
            foreach ($verifiedOrderLines as $line) {
                $insItem->execute([
                    $orderId,
                    $line['dishId'],
                    $line['dishName'],
                    $line['unitPriceVnd'],
                    $line['quantity'],
                    $line['lineTotalVnd'],
                    $line['note'] !== '' ? $line['note'] : null,
                    $nowStr,
                ]);
                $orderItemId = (int)$pdo->lastInsertId();
                foreach ($line['options'] as $opt) {
                    $insOpt->execute([$orderItemId, $opt['optionId'], $opt['name'], $opt['extraPriceVnd']]);
                }
            }

            if ($quotedVoucherId !== null && $quotedVoucherId > 0 && $liveDiscountVnd > 0) {
                $pdo->prepare('INSERT INTO order_vouchers (order_id, voucher_id, discount_amount) VALUES (?, ?, ?)')
                    ->execute([$orderId, $quotedVoucherId, $liveDiscountVnd]);
                $pdo->prepare('UPDATE vouchers SET used_count = used_count + 1 WHERE id = ?')
                    ->execute([$quotedVoucherId]);
            }

            $pdo->prepare('INSERT INTO order_status_logs (order_id, status, note, actor_user_id, created_at) VALUES (?, ?, ?, ?, ?)')
                ->execute([$orderId, $initialStatus, $statusLogNote, $userId, $nowStr]);
            $pdo->prepare('INSERT INTO deliveries (order_id, status, delivery_fee, created_at, updated_at) VALUES (?, \'unassigned\', ?, ?, ?)')
                ->execute([$orderId, $liveDeliveryFeeVnd, $nowStr, $nowStr]);
            $pdo->prepare('INSERT INTO payments (order_id, provider, method, amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, \'pending\', ?, ?)')
                ->execute([$orderId, $paymentMethod, $paymentMethod, $liveTotalVnd, $nowStr, $nowStr]);

            $finalDraftState = $paymentMethod === 'payos' ? self::STATE_PAYMENT_PENDING : self::STATE_ORDER_CREATED;
            $pdo->prepare("UPDATE ai_order_quotes SET status = 'confirmed', confirmed_at = ? WHERE id = ?")
                ->execute([$nowStr, $quoteRow['id']]);
            $pdo->prepare('UPDATE ai_order_drafts SET state = ?, submitted_order_id = ?, updated_at = ? WHERE id = ?')
                ->execute([$finalDraftState, $orderId, $nowStr, $draft['idInt']]);

            $responsePayload = [
                'ok' => true,
                'idempotentReplay' => false,
                'draftState' => $finalDraftState,
                'order' => [
                    'id' => (string)$orderId,
                    'code' => $orderCode,
                    'status' => strtoupper($initialStatus),
                    'paymentMethod' => strtoupper($paymentMethod === 'payos' ? 'PAYOS' : 'COD'),
                    'paymentStatus' => 'PENDING',
                    'subtotalVnd' => $liveSubtotalVnd,
                    'deliveryFeeVnd' => $liveDeliveryFeeVnd,
                    'discountVnd' => $liveDiscountVnd,
                    'totalVnd' => $liveTotalVnd,
                    'restaurantId' => (string)$storeId,
                    'restaurantName' => (string)$storeRow['name'],
                    'deliveryAddress' => (string)$addrRow['address_line'],
                    'receiverName' => (string)$addrRow['receiver_name'],
                    'receiverPhone' => (string)$addrRow['receiver_phone'],
                    'scheduledFor' => $quotedScheduledFor,
                    'scheduledForDisplay' => $scheduledForDisplay,
                    'requiresOnlinePayment' => $paymentMethod === 'payos',
                ],
            ];

            $pdo->prepare("UPDATE ai_order_idempotency SET status = 'completed', order_id = ?, response_json = ?, updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                ->execute([
                    $orderId,
                    json_encode($responsePayload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
                    $nowStr,
                    $userId,
                    $cleanIdem,
                ]);

            $pdo->commit();

            if ($quotedVoucherId !== null && function_exists('foodhubInvalidateJsonCache')) {
                foodhubInvalidateJsonCache('catalog-vouchers.json');
            }
            if (function_exists('notifyUser')) {
                try {
                    $ownerQ = $pdo->prepare('SELECT m.owner_user_id FROM stores s JOIN merchants m ON m.id = s.merchant_id WHERE s.id = ?');
                    $ownerQ->execute([$storeId]);
                    $sellerId = $ownerQ->fetchColumn();
                    if ($sellerId) {
                        notifyUser($pdo, $config, (int)$sellerId, $orderId, 'new_order', 'Có đơn hàng mới', "Bạn vừa nhận đơn #$orderCode");
                    }
                } catch (Throwable) {}
            }

            return $responsePayload;
        } catch (MealAdvisorException $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            throw $e;
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            $pdo->prepare("UPDATE ai_order_idempotency SET status = 'failed', updated_at = ? WHERE user_id = ? AND idempotency_key = ?")
                ->execute([self::nowStr($now), $userId, $cleanIdem]);
            throw new MealAdvisorException(500, 'Chưa thể tạo đơn lúc này: ' . $e->getMessage());
        }
    }
}
