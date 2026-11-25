<?php
declare(strict_types=1);
if (!isset($pdo, $uid, $config, $base, $parts, $method)) {
    http_response_code(404);
    exit;
}
require_once __DIR__ . '/MealAdvisor.php';
require_once __DIR__ . '/FoodHubSupport.php';
require_once __DIR__ . '/AiOrderDraftService.php';
require_once __DIR__ . '/AiOrderingOrchestrator.php';

try {
    $me = account($pdo, $uid);
    $userId = AiOrderDraftService::assertCustomer($me);
    AiOrderDraftService::ensureSchema($pdo);

    // Sub-routing under /api/ai/order-drafts[/...]
    // $parts[0] = 'ai', $parts[1] = 'order-drafts', $parts[2] = draftId | 'active', $parts[3] = 'quote' | 'confirm'
    $subId = $parts[2] ?? null;
    $action = $parts[3] ?? null;

    // 1. GET /api/ai/order-drafts/active?conversationId=...
    if ($method === 'GET' && $subId === 'active') {
        $convId = trim((string)($_GET['conversationId'] ?? ''));
        if ($convId === '' || strlen($convId) > 64) {
            throw new MealAdvisorException(422, 'Thiếu hoặc sai conversationId.');
        }
        $draft = AiOrderDraftService::getActiveDraft($pdo, $userId, $convId);
        if ($draft === null) {
            out(200, ['data' => ['draft' => null, 'quote' => null]]);
        }
        $quoteBundle = $draft['items']
            ? AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'])
            : null;
        out(200, [
            'data' => [
                'draft' => $quoteBundle['draft'] ?? $draft,
                'quote' => $quoteBundle['quote'] ?? null,
                'availableAddresses' => $quoteBundle['addresses'] ?? AiOrderDraftService::getUserAddresses($pdo, $userId),
                'availableVouchers' => $quoteBundle['vouchers']['eligible'] ?? [],
                'bestVoucher' => $quoteBundle['vouchers']['best'] ?? null,
                'readyToConfirm' => (bool)($quoteBundle['readyToConfirm'] ?? false),
            ],
        ]);
    }

    // 2. POST /api/ai/order-drafts (create or initialize draft with optional first item)
    if ($method === 'POST' && $subId === null) {
        $in = body();
        $convId = trim((string)($in['conversationId'] ?? ('conv-' . $userId . '-' . bin2hex(random_bytes(4)))));
        if (strlen($convId) > 64) {
            throw new MealAdvisorException(422, 'conversationId quá dài.');
        }
        $draft = AiOrderDraftService::getOrCreateDraft($pdo, $userId, $convId);
        $reorderMeta = null;
        if (!empty($in['reorderLast'])) {
            $dateHint = isset($in['dateHint']) ? (string)$in['dateHint'] : null;
            $rRes = AiOrderDraftService::reorderPastOrder($pdo, $userId, $convId, $dateHint);
            if (!$rRes['ok']) {
                out(409, ['error' => $rRes['message'] ?? 'Chưa thể đặt lại đơn cũ.', 'data' => $rRes]);
            }
            $draft = $rRes['draft'];
            $reorderMeta = $rRes;
        }
        if (!empty($in['dishId'])) {
            $addRes = AiOrderDraftService::addOrUpdateItem(
                $pdo,
                $userId,
                $draft['draftId'],
                (int)$in['dishId'],
                max(1, (int)($in['quantity'] ?? 1)),
                is_array($in['optionIds'] ?? null) ? $in['optionIds'] : [],
                is_array($in['optionNames'] ?? null) ? $in['optionNames'] : [],
                (string)($in['note'] ?? ''),
                (bool)($in['replaceStore'] ?? false)
            );
            if (!$addRes['ok']) {
                out(409, ['error' => $addRes['message'] ?? 'Chưa thể thêm món vào đơn nháp.', 'data' => $addRes]);
            }
            $draft = $addRes['draft'];
        }
        if (array_key_exists('scheduledFor', $in)) {
            $sVal = $in['scheduledFor'] !== null ? (string)$in['scheduledFor'] : null;
            $sRes = AiOrderDraftService::setDraftSchedule($pdo, $userId, $draft['draftId'], $sVal);
            if (!$sRes['ok']) {
                out(422, ['error' => $sRes['message'] ?? 'Khung giờ hẹn giao không hợp lệ.', 'data' => $sRes]);
            }
            $draft = $sRes['draft'];
        }
        $quoteBundle = $draft['items']
            ? AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $draft['draftId'])
            : null;
        out(201, [
            'data' => [
                'draft' => $quoteBundle['draft'] ?? $draft,
                'quote' => $quoteBundle['quote'] ?? null,
                'reorderResult' => $reorderMeta,
                'availableAddresses' => $quoteBundle['addresses'] ?? AiOrderDraftService::getUserAddresses($pdo, $userId),
                'availableVouchers' => $quoteBundle['vouchers']['eligible'] ?? [],
                'bestVoucher' => $quoteBundle['vouchers']['best'] ?? null,
                'readyToConfirm' => (bool)($quoteBundle['readyToConfirm'] ?? false),
            ],
        ]);
    }

    // Require valid $subId for draft-specific routes
    if ($subId === null || !preg_match('/^dr_[0-9a-f]{16,40}$/D', $subId)) {
        throw new MealAdvisorException(404, 'Đường dẫn đơn nháp không hợp lệ.');
    }

    // 3. PATCH /api/ai/order-drafts/{draftId}
    if ($method === 'PATCH' && $action === null) {
        $in = body();
        $draft = AiOrderDraftService::getDraftByUid($pdo, $userId, $subId);

        if (!empty($in['addItem']) && is_array($in['addItem'])) {
            $it = $in['addItem'];
            $addRes = AiOrderDraftService::addOrUpdateItem(
                $pdo,
                $userId,
                $subId,
                (int)($it['dishId'] ?? 0),
                max(1, (int)($it['quantity'] ?? 1)),
                is_array($it['optionIds'] ?? null) ? $it['optionIds'] : [],
                is_array($it['optionNames'] ?? null) ? $it['optionNames'] : [],
                (string)($it['note'] ?? ''),
                (bool)($it['replaceStore'] ?? false),
                (bool)($it['setExactQuantity'] ?? false)
            );
            if (!$addRes['ok']) {
                out(409, ['error' => $addRes['message'] ?? 'Chưa thể thêm món.', 'data' => $addRes]);
            }
            $draft = $addRes['draft'];
        }

        if (!empty($in['updateItem']) && is_array($in['updateItem'])) {
            $uIt = $in['updateItem'];
            $draft = AiOrderDraftService::modifyItemQuantity(
                $pdo,
                $userId,
                $subId,
                (string)($uIt['lineId'] ?? $uIt['dishId'] ?? ''),
                (int)($uIt['quantity'] ?? 1),
                isset($uIt['note']) ? (string)$uIt['note'] : null
            );
        }

        if (!empty($in['removeItem'])) {
            $draft = AiOrderDraftService::modifyItemQuantity($pdo, $userId, $subId, (string)$in['removeItem'], 0);
        }

        if (isset($in['addressId'])) {
            $draft = AiOrderDraftService::setDraftAddress($pdo, $userId, $subId, (int)$in['addressId']);
        }

        if (array_key_exists('voucherCode', $in) || isset($in['autoBestVoucher'])) {
            $vCode = array_key_exists('voucherCode', $in) && $in['voucherCode'] !== null ? (string)$in['voucherCode'] : null;
            $draft = AiOrderDraftService::setDraftVoucher($pdo, $userId, $subId, $vCode, (bool)($in['autoBestVoucher'] ?? false));
        }

        if (isset($in['paymentMethod'])) {
            $draft = AiOrderDraftService::setDraftPaymentMethod($pdo, $userId, $subId, (string)$in['paymentMethod']);
        }

        if (isset($in['customerNote'])) {
            $draft = AiOrderDraftService::setDraftNote($pdo, $userId, $subId, (string)$in['customerNote']);
        }

        if (!empty($in['acceptUpsell']) && is_array($in['acceptUpsell'])) {
            $up = $in['acceptUpsell'];
            $upRes = AiOrderDraftService::acceptUpsell(
                $pdo,
                $userId,
                $subId,
                (int)($up['dishId'] ?? 0),
                (string)($up['voucherCode'] ?? '')
            );
            if (!$upRes['ok']) {
                out(409, ['error' => $upRes['message'] ?? 'Chưa thể thêm món kèm gợi ý.', 'data' => $upRes]);
            }
            $draft = $upRes['draft'];
        }

        if (array_key_exists('scheduledFor', $in)) {
            $sVal = $in['scheduledFor'] !== null ? (string)$in['scheduledFor'] : null;
            $sRes = AiOrderDraftService::setDraftSchedule($pdo, $userId, $subId, $sVal);
            if (!$sRes['ok']) {
                out(422, ['error' => $sRes['message'] ?? 'Khung giờ hẹn giao không hợp lệ.', 'data' => $sRes]);
            }
            $draft = $sRes['draft'];
        }

        $quoteBundle = $draft['items']
            ? AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $subId)
            : null;
        out(200, [
            'data' => [
                'draft' => $quoteBundle['draft'] ?? $draft,
                'quote' => $quoteBundle['quote'] ?? null,
                'availableAddresses' => $quoteBundle['addresses'] ?? AiOrderDraftService::getUserAddresses($pdo, $userId),
                'availableVouchers' => $quoteBundle['vouchers']['eligible'] ?? [],
                'bestVoucher' => $quoteBundle['vouchers']['best'] ?? null,
                'readyToConfirm' => (bool)($quoteBundle['readyToConfirm'] ?? false),
            ],
        ]);
    }

    // 4. POST /api/ai/order-drafts/{draftId}/quote
    if ($method === 'POST' && $action === 'quote') {
        $quoteBundle = AiOrderDraftService::buildAndPersistQuote($pdo, $config, $userId, $subId);
        out(200, [
            'data' => [
                'draft' => $quoteBundle['draft'],
                'quote' => $quoteBundle['quote'],
                'availableAddresses' => $quoteBundle['addresses'],
                'availableVouchers' => $quoteBundle['vouchers']['eligible'] ?? [],
                'bestVoucher' => $quoteBundle['vouchers']['best'] ?? null,
                'readyToConfirm' => $quoteBundle['readyToConfirm'],
                'blockingReason' => $quoteBundle['blockingReason'],
            ],
        ]);
    }

    // 5. POST /api/ai/order-drafts/{draftId}/confirm
    if ($method === 'POST' && $action === 'confirm') {
        $in = body();
        if (empty($in['confirmed'])) {
            throw new MealAdvisorException(422, 'Cần tín hiệu xác nhận rõ ràng từ người dùng (confirmed=true).');
        }
        $quoteId = trim((string)($in['quoteId'] ?? ''));
        $token = trim((string)($in['confirmationToken'] ?? ''));
        $idemKey = trim((string)($in['idempotencyKey'] ?? ''));
        $expectedTotal = isset($in['expectedTotalVnd']) && is_numeric($in['expectedTotalVnd'])
            ? (int)$in['expectedTotalVnd']
            : null;

        $res = AiOrderDraftService::submitConfirmedOrder(
            $pdo,
            $config,
            $me,
            $subId,
            $quoteId,
            $token,
            $idemKey,
            $expectedTotal
        );

        if (empty($res['ok'])) {
            out(409, ['error' => $res['message'] ?? 'Báo giá đã thay đổi, vui lòng xác nhận lại.', 'data' => $res]);
        }
        out(201, ['data' => $res]);
    }

    // 6. DELETE /api/ai/order-drafts/{draftId}
    if ($method === 'DELETE' && $action === null) {
        $cancelled = AiOrderDraftService::cancelDraft($pdo, $userId, $subId);
        out(200, ['data' => ['draft' => $cancelled, 'cancelled' => true]]);
    }

    out(404, ['error' => 'Endpoint đơn nháp không tồn tại.']);
} catch (MealAdvisorException $e) {
    out($e->status, ['error' => $e->getMessage()]);
} catch (Throwable $e) {
    error_log('AI Order Draft route failed: ' . $e->getMessage());
    out(503, ['error' => 'Dịch vụ đơn nháp AI tạm thời gián đoạn.']);
}
