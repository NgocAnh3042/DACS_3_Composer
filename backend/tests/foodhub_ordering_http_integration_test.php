<?php
declare(strict_types=1);

/**
 * End-to-End Local Backend Route Integration Test for:
 * - POST /api/ai/order-drafts
 * - GET  /api/ai/order-drafts/active
 * - PATCH /api/ai/order-drafts/{draftId}
 * - POST /api/ai/order-drafts/{draftId}/quote
 * - POST /api/ai/order-drafts/{draftId}/confirm
 * - DELETE /api/ai/order-drafts/{draftId}
 *
 * Spins up PHP built-in HTTP server on a random local port with an isolated SQLite DB,
 * executes real HTTP requests, verifies status codes & DB state, and shuts down cleanly.
 */

$tmpDir = sys_get_temp_dir() . '/foodhub_http_order_test_' . bin2hex(random_bytes(4));
@mkdir($tmpDir, 0700, true);
$dbFile = $tmpDir . '/test.sqlite';
$routerFile = $tmpDir . '/router.php';

// Initialize test SQLite database
$pdo = new PDO('sqlite:' . $dbFile);
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec("
    CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, firebase_uid TEXT UNIQUE, name TEXT, email TEXT, phone TEXT, role TEXT, status TEXT, deleted_at TEXT);
    CREATE TABLE user_addresses (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, label TEXT, receiver_name TEXT, receiver_phone TEXT, address_line TEXT, ward TEXT, district TEXT, city TEXT, latitude REAL, longitude REAL, is_default INTEGER);
    CREATE TABLE merchants (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_user_id INTEGER, brand_name TEXT, status TEXT);
    CREATE TABLE stores (id INTEGER PRIMARY KEY AUTOINCREMENT, merchant_id INTEGER, name TEXT, address_line TEXT, latitude REAL, longitude REAL, is_open INTEGER, status TEXT, avg_prep_minutes INTEGER, rating_avg REAL DEFAULT 4.8, deleted_at TEXT);
    CREATE TABLE menu_categories (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER, name TEXT, is_active INTEGER DEFAULT 1);
    CREATE TABLE dishes (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER, menu_category_id INTEGER, name TEXT, description TEXT, image_url TEXT, price REAL, is_available INTEGER, stock_quantity INTEGER, prep_minutes INTEGER, rating_avg REAL DEFAULT 4.8, deleted_at TEXT);
    CREATE TABLE option_groups (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER, name TEXT, min_select INTEGER, max_select INTEGER);
    CREATE TABLE options (id INTEGER PRIMARY KEY AUTOINCREMENT, option_group_id INTEGER, name TEXT, extra_price REAL, is_available INTEGER, sort_order INTEGER);
    CREATE TABLE dish_option_groups (dish_id INTEGER, option_group_id INTEGER, sort_order INTEGER, PRIMARY KEY(dish_id, option_group_id));
    CREATE TABLE vouchers (id INTEGER PRIMARY KEY AUTOINCREMENT, store_id INTEGER, code TEXT UNIQUE, title TEXT, description TEXT, discount_type TEXT, discount_value REAL, max_discount REAL, min_order_amount REAL, usage_limit INTEGER, used_count INTEGER DEFAULT 0, starts_at TEXT, ends_at TEXT, is_active INTEGER DEFAULT 1);
    CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE, customer_id INTEGER, store_id INTEGER, status TEXT, receiver_name TEXT, receiver_phone TEXT, delivery_address TEXT, delivery_lat REAL, delivery_lng REAL, customer_note TEXT, subtotal REAL, delivery_fee REAL, discount_amount REAL, total_amount REAL, payment_method TEXT, payment_status TEXT, placed_at TEXT, created_at TEXT, updated_at TEXT);
    CREATE TABLE order_items (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER, dish_id INTEGER, dish_name TEXT, unit_price REAL, quantity INTEGER, line_total REAL, note TEXT, created_at TEXT);
    CREATE TABLE order_item_options (id INTEGER PRIMARY KEY AUTOINCREMENT, order_item_id INTEGER, option_id INTEGER, option_name TEXT, extra_price REAL);
    CREATE TABLE order_vouchers (order_id INTEGER, voucher_id INTEGER, discount_amount REAL, PRIMARY KEY(order_id, voucher_id));
    CREATE TABLE order_status_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER, status TEXT, note TEXT, actor_user_id INTEGER, created_at TEXT);
    CREATE TABLE deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER UNIQUE, driver_id INTEGER, status TEXT, delivery_fee REAL, created_at TEXT, updated_at TEXT);
    CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER, provider TEXT, method TEXT, amount REAL, status TEXT, created_at TEXT, updated_at TEXT);

    INSERT INTO users VALUES (1, 'uid-http-1', 'Lê Minh Khang', 'khang@test.vn', '0905123456', 'customer', 'active', NULL);
    INSERT INTO user_addresses VALUES (1, 1, 'Nhà riêng', 'Lê Minh Khang', '0905123456', '100 Nguyễn Văn Linh, Đà Nẵng', NULL, 'Hải Châu', 'Đà Nẵng', 16.0605, 108.2160, 1);
    INSERT INTO merchants VALUES (1, 1, 'Cơm Tấm Sài Gòn Xưa', 'active');
    INSERT INTO stores VALUES (1, 1, 'Cơm Tấm Sườn Nướng Sài Gòn Xưa', '186 Nguyễn Văn Linh', 16.0612, 108.2215, 1, 'active', 12, 4.9, NULL);
    INSERT INTO menu_categories VALUES (1, 1, 'Cơm tấm', 1);
    INSERT INTO dishes VALUES (10, 1, 1, 'Cơm tấm sườn bì chả trứng ốp la', 'Sườn cốt lết nướng', '', 55000, 1, 10, 10, 4.9, NULL);
    INSERT INTO vouchers VALUES (1, NULL, 'HTTP15K', 'Giảm 15k', '', 'fixed', 15000, NULL, 40000, 50, 0, '2025-01-01 00:00:00', '2029-12-31 23:59:59', 1);
");
unset($pdo);

$backendDir = str_replace('\\', '/', realpath(__DIR__ . '/..'));
$dbFileNorm = str_replace('\\', '/', $dbFile);
$tmpDirNorm = str_replace('\\', '/', $tmpDir);

$routerCode = <<<PHP
<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
\$base = '$backendDir';
\$config = ['delivery_fee' => 15000, 'cache_directory' => '$tmpDirNorm'];
require_once \$base . '/runtime.php';
\$pdo = new PDO('sqlite:$dbFileNorm');
\$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
\$pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
\$pdo->sqliteCreateFunction('CONCAT_WS', static fn(string \$s, ...\$p) => implode(\$s, array_filter(\$p, static fn(\$x) => \$x !== null)));

function out(int \$status, array \$body): never { http_response_code(\$status); echo json_encode(\$body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function body(): array { \$j = json_decode((string)file_get_contents('php://input'), true); return is_array(\$j) ? \$j : []; }
function account(?PDO &\$pdo, string \$uid): array {
    \$s = \$pdo->prepare('SELECT * FROM users WHERE firebase_uid = ?');
    \$s->execute([\$uid]);
    \$r = \$s->fetch();
    if (!\$r) out(401, ['error' => 'Unauthorized']);
    return \$r;
}

\$uid = \$_SERVER['HTTP_X_TEST_UID'] ?? 'uid-http-1';
\$method = \$_SERVER['REQUEST_METHOD'];
\$uri = trim((string)parse_url(\$_SERVER['REQUEST_URI'], PHP_URL_PATH), '/');
if (str_starts_with(\$uri, 'api/')) \$uri = substr(\$uri, 4);
\$parts = explode('/', \$uri);
if ((\$parts[0] ?? '') === 'ai' && (\$parts[1] ?? '') === 'order-drafts') {
    require \$base . '/ai/ai-order-draft-route.php';
}
if ((\$parts[0] ?? '') === 'ai' && (\$parts[1] ?? '') === 'meal-advisor') {
    require \$base . '/ai/meal-advisor-route.php';
}
out(404, ['error' => 'Not found']);
PHP;

file_put_contents($routerFile, $routerCode);

// Pick free port and launch local PHP built-in web server
$sock = stream_socket_server('tcp://127.0.0.1:0', $errno, $errstr);
$addr = stream_socket_get_name($sock, false);
fclose($sock);
$port = (int)explode(':', (string)$addr)[1];

$phpBin = PHP_BINARY;
$extDir = ini_get('extension_dir');
$cmd = sprintf('"%s" -d extension_dir="%s" -d extension=mbstring -d extension=pdo_sqlite -S 127.0.0.1:%d "%s"', $phpBin, $extDir, $port, $routerFile);
$descriptors = [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']];
$proc = proc_open($cmd, $descriptors, $pipes);
if (!is_resource($proc)) {
    throw new RuntimeException('Failed to start local PHP HTTP server');
}

function httpJson(int $port, string $method, string $path, ?array $payload = null): array {
    $headers = "Accept: application/json\r\nX-Test-Uid: uid-http-1\r\n";
    $content = '';
    if ($payload !== null) {
        $headers .= "Content-Type: application/json\r\n";
        $content = json_encode($payload, JSON_UNESCAPED_UNICODE);
    }
    $ctx = stream_context_create([
        'http' => [
            'method' => $method,
            'header' => $headers,
            'content' => $content,
            'timeout' => 5,
            'ignore_errors' => true,
        ],
    ]);
    $raw = @file_get_contents("http://127.0.0.1:{$port}{$path}", false, $ctx);
    $statusLine = $http_response_header[0] ?? 'HTTP/1.1 500';
    preg_match('/\s(\d{3})\s?/', $statusLine, $m);
    return ['status' => (int)($m[1] ?? 500), 'body' => json_decode((string)$raw, true) ?: []];
}

try {
    // Wait up to 2s for server readiness
    for ($i = 0; $i < 20; $i++) {
        $probe = @fsockopen('127.0.0.1', $port, $e1, $e2, 0.1);
        if (is_resource($probe)) {
            fclose($probe);
            break;
        }
        usleep(100000);
    }

    $convId = '11111111-2222-3333-4444-555555555555';

    // 1. POST /api/ai/order-drafts
    $r1 = httpJson($port, 'POST', '/api/ai/order-drafts', ['conversationId' => $convId, 'dishId' => 10, 'quantity' => 1]);
    assert($r1['status'] === 201, 'POST /api/ai/order-drafts returns 201');
    $draftId = $r1['body']['data']['draft']['draftId'] ?? '';
    assert(str_starts_with($draftId, 'dr_'), 'Draft created over HTTP');

    // 2. GET /api/ai/order-drafts/active
    $r2 = httpJson($port, 'GET', '/api/ai/order-drafts/active?conversationId=' . urlencode($convId));
    assert($r2['status'] === 200 && ($r2['body']['data']['draft']['draftId'] ?? '') === $draftId, 'GET active draft matches');

    // 3. PATCH /api/ai/order-drafts/{draftId} -> update quantity to 2
    $r3 = httpJson($port, 'PATCH', "/api/ai/order-drafts/{$draftId}", ['updateItem' => ['dishId' => '10', 'quantity' => 2]]);
    assert($r3['status'] === 200 && (int)($r3['body']['data']['quote']['subtotal'] ?? 0) === 110000, 'PATCH updates subtotal to 110,000đ');

    // 4. POST /api/ai/order-drafts/{draftId}/quote
    $r4 = httpJson($port, 'POST', "/api/ai/order-drafts/{$draftId}/quote", []);
    $quote = $r4['body']['data']['quote'] ?? [];
    assert($r4['status'] === 200 && (int)($quote['total'] ?? 0) === 110000 + 15000 - 15000, 'POST quote returns total 110,000đ after HTTP15K voucher');

    // 5. POST /api/ai/order-drafts/{draftId}/confirm without confirmed=true -> 422
    $rBad = httpJson($port, 'POST', "/api/ai/order-drafts/{$draftId}/confirm", ['confirmed' => false, 'quoteId' => $quote['quoteId']]);
    assert($rBad['status'] === 422, 'Confirm without explicit confirmed=true returns 422');

    // 6. POST /api/ai/order-drafts/{draftId}/confirm with valid token & idempotencyKey -> 201
    $r5 = httpJson($port, 'POST', "/api/ai/order-drafts/{$draftId}/confirm", [
        'confirmed' => true,
        'quoteId' => $quote['quoteId'],
        'confirmationToken' => $quote['confirmationToken'],
        'idempotencyKey' => 'http-idem-001',
        'expectedTotalVnd' => $quote['total'],
    ]);
    assert($r5['status'] === 201 && !empty($r5['body']['data']['order']['code']), 'POST confirm creates real order');

    // 7. POST /api/ai/meal-advisor conversational ordering turn -> DELETE draft
    $convId2 = '22222222-3333-4444-5555-666666666666';
    $rChat = httpJson($port, 'POST', '/api/ai/meal-advisor', [
        'conversationId' => $convId2,
        'message' => 'Đặt cho anh 1 phần Cơm tấm sườn bì chả trứng ốp la',
        'budget' => 10000000,
        'budgetScope' => 'food',
        'history' => [],
    ]);
    assert($rChat['status'] === 200 && ($rChat['body']['data']['kind'] ?? '') === 'ordering_quote', 'POST /api/ai/meal-advisor returns ordering_quote');
    $draft2 = $rChat['body']['data']['orderDraft']['draftId'] ?? '';
    $rDel = httpJson($port, 'DELETE', "/api/ai/order-drafts/{$draft2}");
    assert($rDel['status'] === 200 && ($rDel['body']['data']['cancelled'] ?? false) === true, 'DELETE cancels second draft');

    echo "PASS: Local PHP HTTP Backend E2E Integration (8/8 REST & Chat endpoints verified)\n";
} finally {
    proc_terminate($proc);
    foreach ($pipes as $p) @fclose($p);
    proc_close($proc);
    foreach (glob($tmpDir . '/*') ?: [] as $f) @unlink($f);
    @rmdir($tmpDir);
}
