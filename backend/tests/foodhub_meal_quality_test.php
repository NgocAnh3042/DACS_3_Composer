<?php
declare(strict_types=1);

require_once __DIR__ . '/../ai/MealAdvisor.php';
require_once __DIR__ . '/../ai/FoodHubSupport.php';

/**
 * Tests meal advisory quality against the real 29-dish TiDB catalog snapshot
 * (Nhà hàng AnChin, Jolllibee, Mì cay Seoul, Việt Nam Food).
 */

$db = new PDO('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$db->sqliteCreateFunction('CONCAT_WS', static fn(string $sep, ...$parts): string => implode($sep, array_filter($parts, static fn($v) => $v !== null && $v !== '')));

$db->exec("
    CREATE TABLE users (id INTEGER PRIMARY KEY, status TEXT, deleted_at TEXT);
    CREATE TABLE merchants (id INTEGER PRIMARY KEY, owner_user_id INTEGER, status TEXT);
    CREATE TABLE stores (id INTEGER PRIMARY KEY, merchant_id INTEGER, name TEXT, status TEXT, is_open INTEGER, deleted_at TEXT);
    CREATE TABLE menu_categories (id INTEGER PRIMARY KEY, name TEXT);
    CREATE TABLE dishes (id INTEGER PRIMARY KEY, store_id INTEGER, menu_category_id INTEGER, name TEXT, description TEXT, price REAL, image_url TEXT, is_available INTEGER, stock_quantity INTEGER, rating_avg REAL, deleted_at TEXT);

    INSERT INTO users VALUES (1,'active',NULL);
    INSERT INTO merchants VALUES (1,1,'active');
    INSERT INTO stores VALUES
        (1,1,'Nhà hàng AnChin','active',1,NULL),
        (30002,1,'Jolllibee','active',1,NULL),
        (30003,1,'Mì cay Seoul','active',1,NULL),
        (30005,1,'Việt Nam Food','active',1,NULL);
    INSERT INTO menu_categories VALUES (1,'Món chính'),(2,'Tráng miệng'),(3,'Ăn vặt'),(4,'Khai vị');
");

$realDishes = [
    [60027, 30005, 3, 'Chuối nếp nướng', 'Chuối nếp nướng', 15000],
    [60026, 30005, 1, 'Bún riêu cua', 'Bún riêu cua', 45000],
    [60025, 30005, 1, 'Gỏi cuốn', 'Gỏi cuốn', 75000],
    [60024, 30005, 1, 'Bún bò', 'Bún bò', 50000],
    [60023, 30005, 1, 'Nem rán', 'Nem rán', 65000],
    [60022, 30005, 1, 'Bánh mì kẹp thịt', 'Bánh mì kẹp thịt', 35000],
    [60021, 30005, 1, 'Bún chả', 'Bún chả', 55000],
    [60020, 30003, 3, 'Tokbokki Lắc Phô Mai', 'Bánh gạo chiên vàng giòn bên ngoài, mềm dẻo bên trong, phủ lớp bột phô mai mặn ngọt béo ngậy.', 35000],
    [60019, 30003, 3, 'Bibimbap', 'Cơm trắng dẻo thơm trộn cùng thịt băm, trứng ốp la, rau củ xào và tương ớt Gochujang cay ngọt đậm đà.', 45000],
    [60018, 30003, 1, 'Lẩu hải sản', 'Hội tụ tinh túy hương vị biển khơi với tôm tươi, mực ống, bạch tuộc chọn lọc hòa quyện cùng nước lẩu thanh ngọt tự nhiên', 145000],
    [60017, 30003, 1, 'Lẩu bò', 'Thịt bò tươi ngon nhúng nước cốt lẩu xương hầm thanh ngọt, ăn kèm các loại rau nấm theo mùa và mì dai SEOUL chuẩn Hàn.', 145000],
    [60016, 30003, 1, 'Mì kim chi đùi gà', 'Thịt đùi gà chắc ngọt, da giòn sớ thịt mềm, ngập trong làn nước súp kim chi chua thanh kích thích vị giác.', 45000],
    [60015, 30003, 1, 'Mì kim chi Bò', 'Thịt bò mềm mọng, giữ trọn vị ngọt tự nhiên hòa cùng vị chua thanh đặc trưng của nước súp kim chi Hàn Quốc truyền thống.', 50000],
    [60014, 30003, 1, 'Mì kim chi cá', 'Thịt cá basa phi lê tươi ngọt, thịt mềm thơm được ninh chín tới giúp thấm đẫm vị súp kim chi Hàn Quốc', 45000],
    [60013, 30003, 1, 'Mì kim chi xúc xích trứng', 'Sợi mì dai ngon quyện nước súp kim chi chua thanh chuẩn vị, kết hợp xúc xích, trứng béo ngậy hoặc cá viên cực cuốn.', 35000],
    [60012, 30002, 2, 'Kem Sữa Tươi (Cúp)', 'Kem Sữa Tươi (Cúp)', 5000],
    [60011, 30002, 2, 'Kem Sôcôla (Cúp)', 'Kem Sôcôla (Cúp)', 7000],
    [60010, 30002, 2, 'Tropical Sundae', 'Tropical Sundae', 20000],
    [60009, 30002, 2, 'Bánh Xoài Đào', 'Bánh Xoài Đào', 15000],
    [60008, 30002, 1, 'Cơm Gà Giòn Vui Vẻ', '1 Cơm Gà Giòn Vui Vẻ + 1 Nước ngọt + 1 Tương Chua Ngọt', 58000],
    [60007, 30002, 1, '2 miếng Gà Giòn Vui Vẻ', '2 miếng Gà Giòn Vui Vẻ + 2 Tương Chua Ngọt', 66000],
    [60006, 30002, 1, 'Cặp Đôi Ăn Ý', '2 Gà Giòn Vui Vẻ + 2 Mì Ý Jolly vừa + 1 Khoai tây', 145000],
    [60005, 30002, 1, 'MỘT MÌNH HÍT HÀ', '1 Mì ý sốt cay vừa + 1 Gà Sốt cay + 1 Nước ngọt', 80000],
    [30006, 1, 1, 'bánh xèo', 'ngon lắm', 60000],
    [30005, 1, 4, 'Mâm khai vị', 'khai vị trước bữa tiệc', 99000],
    [4, 1, 4, 'Salad khai vị', 'món ăn khai vị', 65000],
    [3, 1, 1, 'vịt quay', 'vịt ngon', 199000],
    [2, 1, 1, 'nộm bò', 'nộm bò ngon', 140000],
    [1, 1, 1, 'sườn nướng', 'sườn ngon', 99000],
];

$ins = $db->prepare("INSERT INTO dishes VALUES (?,?,?,?,?,?,'',1,20,4.8,NULL)");
foreach ($realDishes as $d) $ins->execute($d);

$checks = 0;
function ok(bool $cond, string $msg): void {
    global $checks;
    if (!$cond) throw new RuntimeException("FAIL: {$msg}");
    $checks++;
}

$stores = MenuKnowledge::stores($db, 10000000);
ok(count($stores) === 4, '4 active open stores with dishes');

// 1. Fuzzy store matching against real DB names
$m1 = MenuKnowledge::mentionedStores('Jollibee có món gì ngon?', $stores);
ok(count($m1) === 1 && (int)$m1[0]['id'] === 30002, 'Jollibee (2 l) matches Jolllibee (3 l)');

$m2 = MenuKnowledge::mentionedStores('Jolibee có bán kem không?', $stores);
ok(count($m2) === 1 && (int)$m2[0]['id'] === 30002, 'Jolibee (1 l) matches Jolllibee');

$m3 = MenuKnowledge::mentionedStores('An Chin có món gì?', $stores);
ok(count($m3) === 1 && (int)$m3[0]['id'] === 1, 'An Chin matches Nhà hàng AnChin');

$m4 = MenuKnowledge::mentionedStores('Quán Seoul có gì cay không?', $stores);
ok(count($m4) === 1 && (int)$m4[0]['id'] === 30003, 'Seoul matches Mì cay Seoul');

// 2. Semantic keyword expansion on real TiDB dishes
$spicy = MenuKnowledge::candidates($db, 80000, false, 'Em có 80k, muốn ăn gì cay cay mà no bụng');
$topSpicyNames = array_column(array_slice($spicy, 0, 6), 'name');
ok(in_array('MỘT MÌNH HÍT HÀ', $topSpicyNames, true) || in_array('Bibimbap', $topSpicyNames, true), 'Spicy 80k surfaces MỘT MÌNH HÍT HÀ or Bibimbap in top 6');

$snacks = MenuKnowledge::candidates($db, 50000, false, 'Có gì ăn vặt chiều nay không?');
$topSnackNames = array_column(array_slice($snacks, 0, 5), 'name');
ok(in_array('Tokbokki Lắc Phô Mai', $topSnackNames, true) && in_array('Chuối nếp nướng', $topSnackNames, true), 'Ăn vặt surfaces Tokbokki and Chuối nếp nướng');

$desserts = MenuKnowledge::candidates($db, 30000, false, 'Có món tráng miệng ngọt nào không?');
$topDessertNames = array_column(array_slice($desserts, 0, 4), 'name');
ok(in_array('Kem Sữa Tươi (Cúp)', $topDessertNames, true) && in_array('Tropical Sundae', $topDessertNames, true), 'Tráng miệng surfaces Kem and Sundae');

// 3. One-person meal composition on real TiDB catalog (main + side at same store)
$allRows = MenuKnowledge::candidates($db, 80000, false, '');
$mealIntent = MenuKnowledge::mealIntent(['message' => 'Gợi ý 1 khẩu phần ăn trưa dưới 80k', 'budget' => 80000, 'budgetScope' => 'food'], $stores);
$combo = MenuKnowledge::meal($allRows, 80000, $mealIntent);
ok(count($combo) === 2 && $combo[0]['store_id'] === $combo[1]['store_id'], 'Composes 2-item meal from same real store');
ok(($combo[0]['price'] + $combo[1]['price']) <= 80000, 'Composed meal within 80k ceiling');

// 4. MealChat::renderReply allows natural portion counts but still blocks fabricated prices
$sampleItems = MealAdvisor::reconcile(
    [['id' => '60005', 'reason' => 'Combo cay vừa và no bụng'], ['id' => '60007', 'reason' => '2 miếng gà giòn kèm tương chua ngọt']],
    $allRows,
    100000,
    15000
);
$naturalReply = MealChat::renderReply(
    'Dạ em gợi ý {mon:60005} ({gia:60005}) tại {quan:60005} gồm 1 mì ý sốt cay vừa, 1 gà sốt cay và 1 nước ngọt, cực hợp cho 1 người ăn no ạ!',
    $sampleItems
);
ok($naturalReply !== null && str_contains($naturalReply, 'MỘT MÌNH HÍT HÀ') && str_contains($naturalReply, '80.000đ'), 'renderReply preserves natural reply with portion counts');

$naturalReply2 = MealChat::renderReply(
    'Mình có thể thử {mon:60007} ({gia:60007}) gồm 2 miếng gà giòn và 2 tương chua ngọt nha!',
    $sampleItems
);
ok($naturalReply2 !== null && str_contains($naturalReply2, '2 miếng gà giòn'), 'renderReply preserves 2 miếng count from description');

ok(MealChat::renderReply('Thử {mon:60005} giá 75k tại {quan:60005}.', $sampleItems) === null, 'renderReply still blocks fabricated k price');
ok(MealChat::renderReply('Thử {mon:60005} giá 75.000đ tại {quan:60005}.', $sampleItems) === null, 'renderReply still blocks fabricated đ price');

// 5. MealAdvisor::advise uses FoodHubSystemPrompt::mealAdvisor() and supports custom dish reasons
$advisor = new MealAdvisor(
    ['model' => MealAdvisor::GROQ_MODEL, 'api_key' => 'gsk_mock'],
    static function (array $payload): array {
        $sys = $payload['messages'][0]['content'];
        if (!str_contains($sys, 'FoodHub AI')) throw new RuntimeException('Missing FoodHub AI persona in mealAdvisor prompt');
        if (($payload['temperature'] ?? null) !== 0.5) throw new RuntimeException('Expected temperature 0.5 for meal advise');
        return [
            'budgetVnd' => 80000,
            'unsupported' => false,
            'kind' => 'suggest',
            'ids' => ['60005', '60019'],
            'reasons' => [
                'Gồm 1 mì ý sốt cay vừa, 1 gà sốt cay và 1 nước ngọt, no bụng đúng gu cay.',
                'Cơm trộn thịt băm, trứng ốp la với tương ớt Gochujang cay ngọt đậm đà.'
            ],
            'reply' => 'Dạ gu cay mà no bụng thì em gợi ý {mon:60005} ({gia:60005}) tại {quan:60005} có đủ 1 mì ý, 1 gà sốt cay và 1 nước ngọt, hoặc {mon:60019} ({gia:60019}) tại {quan:60019} với tương ớt Gochujang đậm đà nha!'
        ];
    }
);
$plan = $advisor->advise(
    ['message' => 'Em có 80k, muốn ăn gì cay cay mà no bụng', 'budget' => 80000, 'budgetScope' => 'food', 'history' => []],
    $spicy
);
ok(count($plan['selections']) === 2, 'Advisor returned 2 selections');
ok(str_contains($plan['selections'][0]['reason'], 'gà sốt cay'), 'Custom dish reason preserved');
$rendered = MealChat::renderReply($plan['reply'], MealAdvisor::reconcile($plan['selections'], $spicy, 80000, 15000));
ok($rendered !== null && str_contains($rendered, '80.000đ') && str_contains($rendered, '45.000đ'), 'Rendered reply grounded in real TiDB prices');

// 6. Meal queries are NOT hijacked by FoodHubSupport::intent()
foreach ([
    'Em có 80k, muốn ăn gì cay cay mà no bụng',
    'Jollibee có bán gì ngon không?',
    'Có gì ăn vặt chiều nay không?',
    'Trời lạnh muốn ăn gì nóng nóng có nước',
    'Món tráng miệng nào rẻ rẻ không?'
] as $mealQ) {
    ok(FoodHubSupport::intent($mealQ) === null, "Meal query not hijacked by support intent: {$mealQ}");
}

// 7. Combo meal queries (1 main + 1 side at same store) — including user's exact query!
foreach ([
    'hãy gợi ý combo món ăn trưa nay cho tôi',
    'gợi ý combo tối nay cho mình nhé',
    'ghép giúp em 1 set đồ ăn trưa nay',
    'combo 1 món chính 1 món phụ dưới 100k',
    'gợi ý combo ăn sáng dưới 80k',
    'combo tiết kiệm nhất trưa nay',
    'ghép giúp em 1 set đồ ăn trưa gồm 1 món chính và 1 món phụ dưới 100k',
    'combo trưa nay',
] as $comboQ) {
    ok(FoodHubSupport::intent($comboQ) === null, "Combo query not hijacked by support: {$comboQ}");
    ok(MenuKnowledge::wantsMeal($comboQ), "wantsMeal detects: {$comboQ}");
    $ci = MenuKnowledge::mealIntent(['message' => $comboQ, 'budget' => 100000, 'budgetScope' => 'food'], $stores);
    ok($ci['canFilter'] === true, "mealIntent canFilter=true for: {$comboQ}");
    $pair = MenuKnowledge::meal($allRows, $ci['budget'], $ci);
    ok(count($pair) === 2, "Returns 1 main + 1 side for: {$comboQ}");
    ok($pair[0]['store_id'] === $pair[1]['store_id'], "Same store for: {$comboQ}");
    ok(MenuKnowledge::isMainDish($pair[0]) && MenuKnowledge::isSideDish($pair[1]), "First is main, second is side for: {$comboQ}");
}

// Follow-up "đổi combo khác đi" rotates to a different combo
$firstCombo = MenuKnowledge::meal($allRows, 100000, MenuKnowledge::mealIntent(['message' => 'hãy gợi ý combo món ăn trưa nay cho tôi', 'budget' => 100000, 'budgetScope' => 'food'], $stores));
$prevIds = array_map(static fn($r) => (string)$r['id'], $firstCombo);
$secondCombo = MenuKnowledge::meal($allRows, 100000, MenuKnowledge::mealIntent(['message' => 'hãy gợi ý combo món ăn trưa nay cho tôi', 'budget' => 100000, 'budgetScope' => 'food'], $stores), $prevIds);
ok(count($secondCombo) === 2 && ($secondCombo[0]['id'] !== $firstCombo[0]['id'] || $secondCombo[1]['id'] !== $firstCombo[1]['id']), 'Follow-up combo rotates to different dishes');

$comboText = MenuKnowledge::mealAnswer(
    MealAdvisor::reconcile(
        [['id' => (string)$firstCombo[0]['id'], 'reason' => 'Món chính'], ['id' => (string)$firstCombo[1]['id'], 'reason' => 'Món phụ']],
        $firstCombo,
        100000,
        15000
    ),
    15000,
    false
);
ok(str_contains($comboText, 'món chính') && str_contains($comboText, 'món phụ'), 'mealAnswer clearly states món chính and món phụ');

// 8. Dynamic Category Learning from restaurant-defined menu_categories (including Nước giải khát -> Coca & brand-new custom categories!)
$db->exec("
    INSERT INTO menu_categories (id, name) VALUES (10, 'Nước giải khát'), (11, 'Trà sữa'), (12, 'Sinh tố tươi'), (13, 'Món chay');
    INSERT INTO dishes (id, store_id, menu_category_id, name, description, price, image_url, is_available, stock_quantity, rating_avg, deleted_at) VALUES
        (70001, 30005, 10, 'Coca-Cola lon ướp lạnh', 'Nước ngọt có ga mát lạnh giải khát.', 15000, '', 1, 50, 4.9, NULL),
        (70002, 30005, 10, 'Trà đào cam sả thanh mát', 'Trà trái cây đào cam sả mát lạnh.', 35000, '', 1, 50, 4.8, NULL),
        (70003, 30002, 11, 'Trà sữa trân châu đường đen', 'Sữa tươi trân châu đường đen béo thơm.', 38000, '', 1, 50, 4.9, NULL),
        (70004, 30005, 12, 'Sinh tố bơ sáp Đắk Lắk', 'Sinh tố bơ sáp dẻo mịn tươi mát.', 32000, '', 1, 50, 4.9, NULL),
        (70005, 30005, 13, 'Cơm tấm bì chả chay', 'Cơm tấm thuần chay thanh đạm.', 42000, '', 1, 50, 4.9, NULL);
");

$liveCats = MenuKnowledge::categories($db, 100000);
$catNames = array_column($liveCats, 'name');
ok(in_array('Nước giải khát', $catNames, true), 'MenuKnowledge::categories loads Nước giải khát from DB');
ok(in_array('Sinh tố tươi', $catNames, true), 'MenuKnowledge::categories loads brand-new custom category Sinh tố tươi from DB');

$matchedDrink = MenuKnowledge::matchedCategories('gợi ý món giải khát cho mình', $liveCats);
ok(in_array('Nước giải khát', $matchedDrink, true), 'Matches "món giải khát" -> Nước giải khát');
$drinkCandidates = MenuKnowledge::candidates($db, 100000, false, 'gợi ý món giải khát', [], null, $matchedDrink);
$drinkFiltered = MenuKnowledge::filterByCategories($drinkCandidates, $matchedDrink);
$drinkNames = array_column($drinkFiltered, 'name');
ok(count($drinkFiltered) === 2 && in_array('Coca-Cola lon ướp lạnh', $drinkNames, true), 'Dynamic category filter recommends Coca-Cola for "món giải khát"');

$matchedCustom = MenuKnowledge::matchedCategories('có sinh tố tươi nào không?', $liveCats);
ok($matchedCustom === ['Sinh tố tươi'], 'Dynamically learns brand-new seller category "Sinh tố tươi" without hardcoding');
$customFiltered = MenuKnowledge::filterByCategories(MenuKnowledge::candidates($db, 100000, false, 'có sinh tố tươi nào không?', [], null, $matchedCustom), $matchedCustom);
ok(count($customFiltered) === 1 && $customFiltered[0]['name'] === 'Sinh tố bơ sáp Đắk Lắk', 'Recommends dish from brand-new custom category');

$matchedVeg = MenuKnowledge::matchedCategories('gợi ý món chay đi shop', $liveCats);
ok(in_array('Món chay', $matchedVeg, true), 'Matches Món chay from DB categories');

ok(MenuKnowledge::isSideDish(['category' => 'Nước giải khát', 'name' => 'Coca-Cola lon ướp lạnh']), 'Nước giải khát recognized as valid side item for combos');
ok(MenuKnowledge::isSideDish(['category' => 'Trà sữa', 'name' => 'Trà sữa trân châu đường đen']), 'Trà sữa recognized as valid side item for combos');
ok(MenuKnowledge::isMainDish(['category' => 'Món chay', 'name' => 'Cơm tấm bì chả chay']), 'Món chay recognized as valid main dish for combos');

echo "PASS: {$checks} real-TiDB meal quality checks\n";
