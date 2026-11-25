<?php
declare(strict_types=1);

require_once __DIR__ . '/../recommendation/SearchHistoryService.php';
require_once __DIR__ . '/../recommendation/RecommendationEngine.php';

/**
 * Comprehensive test suite for FoodHub Personalized Food Recommendation System.
 * Covers: Search Tracking, Recommendation Relevance, Security & Privacy,
 * Data Integrity, and Latency Performance (P50/P95).
 */

function createRecTestDb(): PDO {
    $db = new PDO('sqlite::memory:');
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $db->exec("
        CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, role TEXT, status TEXT, deleted_at TEXT);
        CREATE TABLE merchants (id INTEGER PRIMARY KEY, owner_user_id INTEGER, name TEXT, status TEXT);
        CREATE TABLE stores (id INTEGER PRIMARY KEY, merchant_id INTEGER, name TEXT, is_open INTEGER, status TEXT, deleted_at TEXT);
        CREATE TABLE menu_categories (id INTEGER PRIMARY KEY, store_id INTEGER, name TEXT);
        CREATE TABLE platform_categories (id INTEGER PRIMARY KEY, name TEXT);
        CREATE TABLE dishes (
            id INTEGER PRIMARY KEY, store_id INTEGER, menu_category_id INTEGER, platform_category_id INTEGER,
            name TEXT, description TEXT, price REAL, image_url TEXT, is_available INTEGER,
            stock_quantity INTEGER, rating_avg REAL, deleted_at TEXT
        );
        CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, store_id INTEGER, status TEXT);
        CREATE TABLE order_items (id INTEGER PRIMARY KEY, order_id INTEGER, dish_id INTEGER, quantity INTEGER);
        CREATE TABLE user_search_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            search_query TEXT NOT NULL,
            normalized_query TEXT NOT NULL,
            searched_at TEXT NOT NULL
        );
        CREATE TABLE user_recommendation_preferences (
            user_id INTEGER PRIMARY KEY,
            search_personalization_enabled INTEGER NOT NULL DEFAULT 1,
            retention_days INTEGER NOT NULL DEFAULT 90,
            updated_at TEXT NOT NULL
        );

        INSERT INTO users VALUES (1,'User A','customer','active',NULL),(2,'User B','customer','active',NULL),(3,'User C','customer','active',NULL),(10,'Owner','seller','active',NULL);
        INSERT INTO merchants VALUES (1,10,'M1','active'),(2,10,'M2','suspended');
        INSERT INTO stores VALUES
            (1,1,'Gà Rán Vui Vẻ',1,'active',NULL),
            (2,1,'Trà Sữa Mây',1,'active',NULL),
            (3,1,'Bếp Việt & Pizza',1,'active',NULL),
            (4,1,'Quán Đóng Cửa',0,'active',NULL),
            (5,2,'Quán Ngưng Hoạt Động',1,'active',NULL);
        INSERT INTO menu_categories VALUES
            (1,1,'Gà rán'),(2,2,'Trà sữa'),(3,3,'Cơm'),(4,3,'Pizza'),(5,2,'Đồ uống');
        INSERT INTO platform_categories VALUES (1,'Gà rán'),(2,'Đồ uống'),(3,'Cơm'),(4,'Pizza');

        -- Valid dishes
        INSERT INTO dishes VALUES
            (101,1,1,1,'Gà rán giòn','Gà chiên giòn rụm nóng hổi',45000,'https://img/101.jpg',1,20,4.8,NULL),
            (102,1,1,1,'Gà sốt Hàn Quốc','Gà sốt cay ngọt chuẩn vị Hàn',55000,'https://img/102.jpg',1,15,4.9,NULL),
            (103,1,1,1,'Gà chiên nước mắm','Gà chiên đậm đà mùi tỏi',52000,'https://img/103.jpg',1,10,4.7,NULL),
            (104,3,3,3,'Cơm gà chiên giòn','Cơm trắng kèm gà chiên giòn',58000,'https://img/104.jpg',1,20,4.6,NULL),
            (105,1,1,1,'Gà rán size S','Biến thể size nhỏ',35000,'https://img/105.jpg',1,10,4.2,NULL),
            (106,1,1,1,'Gà rán size M','Biến thể size vừa',45000,'https://img/106.jpg',1,10,4.2,NULL),
            (107,1,1,1,'Gà rán size L','Biến thể size lớn',55000,'https://img/107.jpg',1,10,4.2,NULL),
            (201,2,2,2,'Trà sữa trân châu','Trà sữa thơm béo trân châu đen',32000,'https://img/201.jpg',1,30,4.9,NULL),
            (202,2,2,2,'Matcha latte','Matcha Nhật Bản hòa sữa tươi',38000,'https://img/202.jpg',1,25,4.8,NULL),
            (203,2,5,2,'Trà đào cam sả','Trà đào thanh mát',35000,'https://img/203.jpg',1,25,4.7,NULL),
            (301,3,3,3,'Cơm tấm sườn bì chả','Cơm tấm sườn nướng than',50000,'https://img/301.jpg',1,20,4.8,NULL),
            (401,3,4,4,'Pizza hải sản','Bánh pizza phô mai tôm mực',125000,'https://img/401.jpg',1,10,4.7,NULL),
            -- Invalid / ineligible dishes (MUST NEVER appear)
            (901,1,1,1,'Gà rán đã xóa','Đã xóa mềm',45000,'',1,10,5.0,'2026-11-01 00:00:00'),
            (902,1,1,1,'Gà rán bị ẩn','Ngừng bán',45000,'',0,10,5.0,NULL),
            (903,1,1,1,'Gà rán hết hàng','Hết kho',45000,'',1,0,5.0,NULL),
            (904,4,1,1,'Gà rán quán đóng','Quán đóng cửa',45000,'',1,10,5.0,NULL),
            (905,5,1,1,'Gà rán quán khóa','Merchant suspended',45000,'',1,10,5.0,NULL),
            (906,1,1,1,'Gà rán giá lỗi','Giá 0đ',0,'',1,10,5.0,NULL);

        -- Real order counts for cold-start popularity (Trà sữa trân châu #201 is most ordered)
        INSERT INTO orders VALUES (1,1,2,'completed'),(2,2,3,'completed'),(3,1,1,'cancelled');
        INSERT INTO order_items VALUES (1,1,201,12),(2,2,301,5),(3,3,101,99);
    ");
    return $db;
}

$checks = 0;
function okRec(bool $cond, string $msg): void {
    global $checks;
    if (!$cond) throw new RuntimeException("FAIL: {$msg}");
    $checks++;
}

$db = createRecTestDb();
$now = strtotime('2026-11-23 12:00:00 UTC');

// =========================================================================
// 1. SEARCH TRACKING
// =========================================================================
okRec(SearchHistoryService::normalizeQuery('  GÀ   RÁN  ') === 'ga ran', 'Normalize uppercase, spaces & diacritics');
okRec(SearchHistoryService::normalizeQuery('Trà Sữa Trân Châu!') === 'tra sua tran chau', 'Normalize Vietnamese punctuation');
okRec(SearchHistoryService::normalizeQuery('ga ran') === 'ga ran', 'Normalize unaccented query');

$r1 = SearchHistoryService::record($db, 1, '  GÀ RÁN  ', null, null, null, 60, $now);
okRec($r1['recorded'] === true && $r1['normalizedQuery'] === 'ga ran', 'Records valid confirmed search');

// Duplicate within 60s window is deduplicated
$r2 = SearchHistoryService::record($db, 1, 'gà rán', null, null, null, 60, $now + 10);
$r3 = SearchHistoryService::record($db, 1, 'GA RAN', null, null, null, 60, $now + 30);
okRec($r2['recorded'] === false && $r2['reason'] === 'deduplicated' && $r3['recorded'] === false, 'Deduplicates rapid repeated searches within 60s');

// Same query after dedup window (>60s) IS recorded to preserve real frequency
$r4 = SearchHistoryService::record($db, 1, 'gà rán', null, null, null, 60, $now + 120);
okRec($r4['recorded'] === true, 'Records repeat search outside dedup window');

// Sensitive queries skipped
okRec(SearchHistoryService::record($db, 1, 'OTP 123456', null, null, null, 60, $now)['recorded'] === false, 'Skips OTP sensitive query');
okRec(SearchHistoryService::record($db, 1, 'test@example.com', null, null, null, 60, $now)['recorded'] === false, 'Skips email sensitive query');
okRec(SearchHistoryService::record($db, 1, '0912345678', null, null, null, 60, $now)['recorded'] === false, 'Skips phone sensitive query');

// Too short query rejected with InvalidArgumentException
$rejectedShort = false;
try { SearchHistoryService::record($db, 1, 'g', null, null, null, 60, $now); }
catch (InvalidArgumentException) { $rejectedShort = true; }
okRec($rejectedShort, 'Rejects 1-character search-as-you-type fragment');

// =========================================================================
// 2. RECOMMENDATION RELEVANCE & DIVERSITY & COLD START
// =========================================================================
// User 1 searched "gà rán" + "gà sốt cay"
SearchHistoryService::record($db, 1, 'gà sốt cay', null, null, null, 60, $now + 180);
$recA = RecommendationEngine::recommend($db, 1, 6, null, null, null, $now + 200);
okRec($recA['success'] === true && $recA['type'] === 'personalized' && $recA['title'] === 'Dành riêng cho bạn', 'User A gets personalized section');
$idsA = array_column($recA['data'], 'foodId');
okRec(in_array(101, $idsA, true) && in_array(102, $idsA, true), 'User A gets Gà rán giòn & Gà sốt Hàn Quốc');
okRec(in_array(104, $idsA, true), 'User A includes Cơm gà chiên giòn from another store for diversity');

// Diversity check: all 3 identical-root variants (105, 106, 107) should not crowd out distinct dishes
$top4A = array_slice($idsA, 0, 4);
$variantCountInTop4 = count(array_intersect($top4A, [105, 106, 107]));
okRec($variantCountInTop4 <= 1, 'Diversity prevents size S/M/L variants from dominating top 4');

// User 2 searches trà sữa / matcha / trà đào
SearchHistoryService::record($db, 2, 'trà sữa', null, null, null, 60, $now);
SearchHistoryService::record($db, 2, 'matcha', null, null, null, 60, $now + 100);
SearchHistoryService::record($db, 2, 'trà đào', null, null, null, 60, $now + 200);
$recB = RecommendationEngine::recommend($db, 2, 5, null, null, null, $now + 250);
$idsB = array_column($recB['data'], 'foodId');
okRec($recB['type'] === 'personalized' && array_slice($idsB, 0, 3) === [201, 202, 203] || count(array_intersect(array_slice($idsB, 0, 3), [201, 202, 203])) === 3, 'User B top 3 are Trà sữa, Matcha latte, Trà đào cam sả');

// Cold-start User 3 (no search history) -> popular fallback ranked by real non-cancelled order_items
$recC = RecommendationEngine::recommend($db, 3, 5, null, null, null, $now);
okRec($recC['type'] === 'popular' && $recC['title'] === 'Món phổ biến hôm nay', 'Cold-start user gets popular fallback');
okRec($recC['data'][0]['foodId'] === 201 && $recC['data'][1]['foodId'] === 301, 'Cold-start respects real non-cancelled order counts (ignores cancelled order #3)');

// Recency weighting: User 3 searched "pizza" 25 days ago, searched "cơm tấm" today -> Cơm tấm ranks above Pizza
SearchHistoryService::record($db, 3, 'pizza', null, null, null, 60, $now - (25 * 86400));
SearchHistoryService::record($db, 3, 'cơm tấm', null, null, null, 60, $now);
$recC2 = RecommendationEngine::recommend($db, 3, 5, null, null, null, $now + 10);
okRec($recC2['type'] === 'personalized' && $recC2['data'][0]['foodId'] === 301, 'Recent search (cơm tấm today) outranks old search (pizza 25d ago)');

// Non-existent dish query -> honest popular fallback
SearchHistoryService::clear($db, 3);
SearchHistoryService::record($db, 3, 'bánh mì sao hỏa xyz', null, null, null, 60, $now);
$recUnknown = RecommendationEngine::recommend($db, 3, 5, null, null, null, $now + 10);
okRec($recUnknown['type'] === 'popular' && count($recUnknown['data']) > 0, 'Unmatched search falls back to real popular dishes');

// =========================================================================
// 3. DATA INTEGRITY (Ineligible dishes NEVER returned)
// =========================================================================
$allReturnedIds = array_merge($idsA, $idsB, array_column($recC['data'], 'foodId'));
$ineligible = [901, 902, 903, 904, 905, 906];
okRec(count(array_intersect($allReturnedIds, $ineligible)) === 0, 'Deleted, hidden, out-of-stock, closed-store, suspended-merchant, and 0-price dishes excluded');
okRec(count($idsA) === count(array_unique($idsA)), 'No duplicate foodId returned');

// =========================================================================
// 4. SECURITY & PRIVACY ISOLATION
// =========================================================================
$histA = SearchHistoryService::list($db, 1, 20, null, null, $now + 300);
$histB = SearchHistoryService::list($db, 2, 20, null, null, $now + 300);
okRec(!in_array('matcha', array_column($histA, 'normalizedQuery'), true), 'User A cannot see User B search history');
okRec(!in_array('ga ran', array_column($histB, 'normalizedQuery'), true), 'User B cannot see User A search history');

// SQL injection attempt is safely parameterized
$sqli = SearchHistoryService::record($db, 1, "gà'; DROP TABLE user_search_history; --", null, null, null, 60, $now + 400);
$tableStillExists = (bool)$db->query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='user_search_history'")->fetchColumn();
okRec($tableStillExists, 'Prepared statements prevent SQL injection');

// Clearing User 2 history does NOT affect User 1
SearchHistoryService::clear($db, 2);
okRec(count(SearchHistoryService::list($db, 2, 20, null, null, $now + 500)) === 0, 'User 2 history cleared');
okRec(count(SearchHistoryService::list($db, 1, 20, null, null, $now + 500)) >= 2, 'User 1 history untouched when User 2 clears');

// Disabling personalization stops recording AND stops returning personalized label + invalidates cache
$cacheA = sys_get_temp_dir() . '/fh_rec_cache_a_' . bin2hex(random_bytes(4)) . '.json';
$cacheB = sys_get_temp_dir() . '/fh_rec_cache_b_' . bin2hex(random_bytes(4)) . '.json';
RecommendationEngine::recommend($db, 1, 5, null, null, $cacheA, $now + 500);
okRec(is_file($cacheA), 'Per-user cache created for User A');

SearchHistoryService::updatePreferences($db, 1, ['searchPersonalizationEnabled' => false], null, $cacheA);
okRec(!is_file($cacheA), 'Disabling personalization invalidates User A cache');

$blockedRec = SearchHistoryService::record($db, 1, 'phở bò', null, null, $cacheA, 60, $now + 600);
okRec($blockedRec['recorded'] === false && $blockedRec['reason'] === 'personalization_disabled', 'Disabled personalization blocks new search recording');

$disabledResp = RecommendationEngine::recommend($db, 1, 5, null, null, $cacheA, $now + 600);
okRec($disabledResp['type'] === 'popular' && $disabledResp['title'] !== 'Dành riêng cho bạn', 'Disabled personalization never shows Dành riêng cho bạn');

@unlink($cacheA);
@unlink($cacheB);

// =========================================================================
// 5. PERFORMANCE (Measure P50 & P95 across 50 runs)
// =========================================================================
SearchHistoryService::updatePreferences($db, 1, ['searchPersonalizationEnabled' => true]);
$latencies = [];
for ($i = 0; $i < 50; $i++) {
    $t0 = microtime(true);
    RecommendationEngine::recommend($db, 1, 10, null, null, null, $now + 700);
    $latencies[] = (microtime(true) - $t0) * 1000.0;
}
sort($latencies);
$p50 = round($latencies[24], 2);
$p95 = round($latencies[47], 2);
okRec($p95 < 500.0, "P95 latency under 500ms (actual P50={$p50}ms, P95={$p95}ms)");

echo "PASS: {$checks} recommendation system checks (P50={$p50}ms, P95={$p95}ms)\n";
