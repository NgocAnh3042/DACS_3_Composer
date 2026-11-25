<?php
declare(strict_types=1);

require_once __DIR__ . '/../ai/MealAdvisor.php';
require_once __DIR__ . '/../ai/FoodHubSupport.php';

/**
 * Vietnamese Real-World Context Expansion Test Suite (60 checks):
 * Tests all 19 FoodHub Knowledge Base scenarios across Customer/Driver/Seller,
 * teencode/regional Vietnamese normalization, weather/mood meal routing,
 * food quality & delivered-not-received incidents, farewells, and JSONL dataset integrity.
 */

$db = new PDO('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$db->sqliteCreateFunction('NOW', static fn(): string => '2026-11-23 12:00:00', 0);
$db->exec("
    CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, role TEXT, status TEXT, deleted_at TEXT);
    CREATE TABLE merchants (id INTEGER PRIMARY KEY, owner_user_id INTEGER, name TEXT, status TEXT);
    CREATE TABLE stores (id INTEGER PRIMARY KEY, merchant_id INTEGER, name TEXT, is_open INTEGER, status TEXT, deleted_at TEXT);
    CREATE TABLE drivers (id INTEGER PRIMARY KEY, user_id INTEGER);
    CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, store_id INTEGER, status TEXT, total_amount REAL, delivery_fee REAL, payment_method TEXT, payment_status TEXT, placed_at TEXT, cancelled_at TEXT, cancel_reason TEXT);
    CREATE TABLE deliveries (id INTEGER PRIMARY KEY, order_id INTEGER, driver_id INTEGER, status TEXT);
    CREATE TABLE payments (id INTEGER PRIMARY KEY, order_id INTEGER, status TEXT);
    CREATE TABLE vouchers (id INTEGER PRIMARY KEY, code TEXT, store_id INTEGER, min_order_amount REAL, is_active INTEGER, starts_at TEXT, ends_at TEXT, usage_limit INTEGER, used_count INTEGER);
    CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER, type TEXT, status TEXT DEFAULT 'open', created_by INTEGER);
    CREATE TABLE conversation_participants (conversation_id INTEGER, user_id INTEGER);
    CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id INTEGER, sender_user_id INTEGER, message_type TEXT, content TEXT);

    INSERT INTO users VALUES (1,'Khách A','customer','active',NULL),(2,'Quán B','seller','active',NULL),(3,'Tài xế C','driver','active',NULL),(4,'Admin D','admin','active',NULL);
    INSERT INTO merchants VALUES (1,2,'Quán B','active');
    INSERT INTO stores VALUES (1,1,'Quán B',1,'active',NULL);
    INSERT INTO drivers VALUES (1,3);
    INSERT INTO orders VALUES (101,1,1,'shipping',75000,15000,'cash','pending','2026-11-23 10:00:00',NULL,NULL);
    INSERT INTO deliveries VALUES (1,101,1,'delivering');
    INSERT INTO vouchers VALUES (1,'FHPHOSHIP',NULL,50000,1,NULL,NULL,100,2);
");

$checks = 0;
function okCtx(bool $cond, string $msg): void {
    global $checks;
    if (!$cond) throw new RuntimeException("FAIL: {$msg}");
    $checks++;
}

function askRole(PDO $db, string $msg, string $role = 'customer', int $id = 1): ?string {
    return FoodHubSupport::reply(
        $db,
        ['id' => $id, 'role' => $role, 'status' => 'active'],
        ['message' => $msg, 'budget' => 10000000, 'budgetScope' => 'food', 'history' => []],
        15000.0
    );
}

// 1. Vietnamese teencode & regional normalization (10 checks)
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('dchi giao hang sai rui'), 'dia chi'), 'dchi -> dia chi');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('doi sdt dc ko shp'), 'so dien thoai duoc khong shop'), 'sdt dc ko shp');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('ck hay cod vay bn'), 'chuyen khoan hay tien mat vay bao nhieu'), 'ck cod bn');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('co ma fs ko'), 'mien phi giao hang'), 'fs -> mien phi giao hang');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('dat mon ntn vay'), 'nhu the nao'), 'ntn -> nhu the nao');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('tx toi dau r'), 'tai xe'), 'tx -> tai xe');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('bùn quá đi'), 'buon'), 'bùn -> buon');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('bún bò ngon'), 'bun bo'), 'bún stays bun (not buon)');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('hok bit dat hang sao lun'), 'khong biet'), 'hok bit -> khong biet');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('ib cho quan dc hem'), 'nhan tin'), 'ib hem -> nhan tin khong');

// 2. Customer FAQ & real-world contexts (22 checks)
$customerFaqCases = [
    ['Shop ơi mình nhập nhầm địa chỉ giao hàng rồi, đổi sao vậy?', 'Địa chỉ giao hàng'],
    ['Thêm địa chỉ mới ở đâu vậy bạn?', 'Địa chỉ giao hàng'],
    ['Đổi số điện thoại người nhận ở đâu?', 'Địa chỉ giao hàng'],
    ['Sao mình không thêm được món của 2 quán vào cùng 1 giỏ?', '1 quán'],
    ['Đặt gộp 2 quán chung 1 đơn được không?', '2 đơn riêng'],
    ['Làm sao để đặt món trên FoodHub vậy?', 'Thêm vào giỏ'],
    ['Đặt trước giờ giao cho tối nay được không shop?', 'giao ngay'],
    ['Mình muốn dặn quán không bỏ hành thì ghi chú ở đâu?', 'Tin nhắn'],
    ['Cho mình số điện thoại shipper giao đơn này với', 'Tin nhắn'],
    ['Làm sao chat với quán vậy shop?', 'Tin nhắn'],
    ['App báo đã giao rồi mà mình chưa nhận được đồ ăn!', 'Gửi phiếu hỗ trợ'],
    ['Shipper gọi mà mình bận họp lỡ cuộc gọi thì sao?', 'Tin nhắn'],
    ['Đồ ăn giao tới nguội ngắt rồi shop ơi!', 'Gửi phiếu hỗ trợ'],
    ['Có tóc trong tô bún luôn, kinh quá!', 'Gửi phiếu hỗ trợ'],
    ['Mình dặn không hành mà quán vẫn bỏ đầy hành!', 'Gửi phiếu hỗ trợ'],
    ['Sao mình nhập mã giảm giá mà không áp được vậy?', 'giá trị tối thiểu'],
    ['Voucher báo hết lượt hoặc không hợp lệ là sao?', 'hết lượt'],
    ['Có mã freeship không shop?', 'banner Trang chủ'],
    ['Tại sao đơn của mình bị quán hủy mà đã thanh toán PayOS rồi?', 'Gửi phiếu hỗ trợ'],
    ['Mình quên mật khẩu đăng nhập rồi làm sao lấy lại?', 'Quên mật khẩu'],
    ['Mình muốn đăng ký mở quán bán hàng trên FoodHub', 'Đăng ký'],
    ['Quán có xuất hóa đơn đỏ VAT không?', 'VAT'],
];
foreach ($customerFaqCases as [$q, $needle]) {
    $ans = askRole($db, $q, 'customer', 1);
    okCtx($ans !== null && str_contains($ans, $needle), "Customer FAQ '{$q}' missing '{$needle}', got: " . var_export($ans, true));
}

// 3. Additional Customer contexts: store hours, delivery tracking, review, farewell (6 checks)
okCtx(str_contains((string)askRole($db, 'FoodHub giao tới mấy giờ vậy?'), 'từng quán'), 'Store hours FAQ');
okCtx(str_contains((string)askRole($db, 'Giao hàng thường mất bao lâu vậy?'), 'bản đồ'), 'Delivery time & map tracking FAQ');
okCtx(str_contains((string)askRole($db, 'Đánh giá quán ở đâu vậy bạn?'), 'Đơn hàng'), 'Review rating FAQ');
okCtx(str_contains((string)askRole($db, 'Đăng ký chạy shipper FoodHub làm sao?'), 'Shipper'), 'Driver registration FAQ');
okCtx(str_contains((string)askRole($db, 'Tạm biệt shop nhé!'), 'tạm biệt'), 'Farewell natural response');
okCtx(str_contains((string)askRole($db, 'Bye bye shop nha'), 'tạm biệt'), 'English-Vietnamese bye response');

// 4. Role-specific Shipper & Seller operational contexts (8 checks)
$driverCases = [
    ['Tôi đến quán nhưng quán đóng cửa rồi thì xử lý sao?', 'Gửi phiếu hỗ trợ'],
    ['Quán báo hết món từ chối làm đơn rồi', 'Gửi phiếu hỗ trợ'],
    ['Quán làm món quá lâu bắt chờ hơn 30 phút', 'Gửi phiếu hỗ trợ'],
    ['Gọi khách không nghe máy, không ra nhận hàng thì sao?', 'Gửi phiếu hỗ trợ'],
];
foreach ($driverCases as [$q, $needle]) {
    $ans = askRole($db, $q, 'driver', 3);
    okCtx($ans !== null && str_contains($ans, $needle), "Driver FAQ '{$q}' missing '{$needle}'");
}

$sellerCases = [
    ['Làm sao để sửa giá món hoặc thêm món mới?', 'Thực đơn'],
    ['Quán đang quá tải muốn tạm đóng cửa nghỉ nhận đơn thì sao?', 'Đang mở cửa'],
    ['Món này hết hàng muốn tạm tắt món ở đâu?', 'Thực đơn'],
    ['Sao app quán không nhận được đơn mới thông báo?', 'Thông báo'],
];
foreach ($sellerCases as [$q, $needle]) {
    $ans = askRole($db, $q, 'seller', 2);
    okCtx($ans !== null && str_contains($ans, $needle), "Seller FAQ '{$q}' missing '{$needle}'");
}

// 5. Vietnamese weather / mood / hunger meal cues route cleanly to TiDB MealAdvisor (returns null from support) (8 checks)
foreach ([
    'Trời mưa lạnh ăn gì hợp ta?',
    'Cuối tháng hết tiền rồi, ăn gì rẻ mà no?',
    'Tui đói quá trời luôn',
    'Trưa nay chưa biết ăn gì hết',
    'Nắng nóng quá muốn uống gì mát',
    'Đêm khuya đói bụng quá có gì lót dạ không?',
    'Tối nay ở một mình ăn gì ngon nhỉ',
    'Hôm nay muốn ăn nhẹ eat clean',
    'hãy gợi ý combo món ăn trưa nay cho tôi',
    'ghép giúp em 1 set đồ ăn trưa gồm 1 món chính và 1 món phụ dưới 100k',
    'combo trưa nay',
] as $mealMoodQ) {
    $res = FoodHubSupport::reply(
        $db,
        ['id' => 1, 'role' => 'customer', 'status' => 'active'],
        ['message' => $mealMoodQ, 'budget' => 10000000, 'budgetScope' => 'food', 'history' => []],
        15000.0,
        static fn(): string => 'clarify'
    );
    okCtx($res === null, "Meal/combo query '{$mealMoodQ}' must route to TiDB MealAdvisor (null), got: " . var_export($res, true));
}

// 6. Verify 300-sample Vietnamese JSONL Training Dataset integrity & safety (7 checks)
$datasetPath = __DIR__ . '/../../docs/AI_TRAINING_DATASET.jsonl';
okCtx(is_file($datasetPath), 'AI_TRAINING_DATASET.jsonl exists');
$lines = array_values(array_filter(file($datasetPath, FILE_IGNORE_NEW_LINES) ?: [], static fn($l) => trim($l) !== ''));
okCtx(count($lines) >= 300, 'Dataset has at least 300 curated Vietnamese samples, got ' . count($lines));
okCtx(count($lines) === count(array_unique($lines)), 'Dataset has zero duplicate JSONL conversation rows');
$allValidJson = true;
$noHumanClaim = true;
$noFakeAction = true;
$hasMultiTurn = false;
foreach ($lines as $idx => $line) {
    $row = json_decode($line, true);
    if (!is_array($row) || !is_array($row['messages'] ?? null) || count($row['messages']) < 3) {
        $allValidJson = false;
        break;
    }
    if (count($row['messages']) >= 5) $hasMultiTurn = true;
    foreach ($row['messages'] as $m) {
        if (($m['role'] ?? '') === 'assistant') {
            $folded = MealAdvisor::folded((string)($m['content'] ?? ''));
            if (preg_match('/em la nguoi that|nhan vien that/', $folded)) $noHumanClaim = false;
            if (preg_match('/da gan co uu tien|goi giuc tai xe/', $folded)) $noFakeAction = false;
        }
    }
}
okCtx($allValidJson, 'Every JSONL line is valid OpenAI/Groq chat format');
okCtx($noHumanClaim, 'Dataset assistant replies never claim to be human');
okCtx($noFakeAction, 'Dataset assistant replies never fabricate unperformed actions');
okCtx($hasMultiTurn, 'Dataset includes multi-turn Vietnamese conversations');

// 7. Deep Human-Chat Comprehension: Slang ("k", "nc", "cf", "ts", "4 km"), Discourse Acts, Natural Ticket Submit & Implicit Dish Refs (12 checks)
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('co ts hay cf k shp'), 'co tra sua hay ca phe khong shop'), 'ts cf k shp slang expansion');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('goi y nc giai khat'), 'goi y nuoc giai khat'), 'nc -> nuoc expansion');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('quan cach 4 km phi ship bn'), '4 km'), '4 km distance preserved (not turned into khuyen mai)');
okCtx(str_contains(FoodHubKnowledgeBase::expandVietnamese('co ma km k'), 'khuyen mai khong'), 'standalone km still expands to khuyen mai');

okCtx(str_contains((string)askRole($db, 'Tui lỡ bấm nhầm món rồi đổi sang món khác đc k?'), 'Chờ xác nhận'), 'Modify/wrong item ordered FAQ');
okCtx(str_contains((string)askRole($db, 'Mình ck PayOS trừ tiền rồi mà vẫn báo chờ thanh toán'), 'Gửi phiếu hỗ trợ'), 'PayOS pending sync FAQ');

// Natural multi-turn ticket confirmation ("ok gửi đi" after prompt)
$ticketFollowUp = FoodHubSupport::reply(
    $db,
    ['id' => 1, 'role' => 'customer', 'status' => 'active'],
    [
        'message' => 'ok gửi đi',
        'budget' => 10000000,
        'budgetScope' => 'food',
        'history' => [
            ['question' => 'Giao thiếu món rồi', 'dishIds' => [], 'answer' => 'Mình nhắn “Gửi phiếu hỗ trợ” để nhân viên xử lý nhé!']
        ]
    ],
    15000.0
);
okCtx($ticketFollowUp !== null && str_contains($ticketFollowUp, 'Đã chuyển phiếu #'), 'Natural "ok gửi đi" submits support ticket in multi-turn chat');

$naturalCreateTicket = FoodHubSupport::reply(
    $db,
    ['id' => 1, 'role' => 'customer', 'status' => 'active'],
    [
        'message' => 'ok tạo phiếu hỗ trợ giúp mình nha',
        'budget' => 10000000,
        'budgetScope' => 'food',
        'history' => [
            ['question' => 'Đơn bị đổ hết nước ra ngoài', 'dishIds' => [], 'answer' => 'Nhắn “Gửi phiếu hỗ trợ” để chuyển nội dung cho nhân viên.']
        ]
    ],
    15000.0
);
okCtx($naturalCreateTicket !== null && str_contains($naturalCreateTicket, 'Đã chuyển phiếu #'), 'Natural "ok tạo phiếu hỗ trợ giúp mình nha" submits ticket');

// Discourse act detection
$discCorrection = ConversationContext::analyzeDiscourse('không phải, ý mình là nước giải khát cơ', [['question' => 'gợi ý món cay', 'dishIds' => []]]);
okCtx($discCorrection['act'] === 'self_correction', 'Detects self_correction discourse act');
$discHesitate = ConversationContext::analyzeDiscourse('phân vân quá cái nào ngon hơn nhỉ', [['question' => 'phở hay bún', 'dishIds' => []]]);
okCtx($discHesitate['act'] === 'hesitation_or_comparison', 'Detects hesitation_or_comparison discourse act');

// Implicit casual dish reference ("cái thứ 2 giá sao zậy", "ly đầu tiên ở quán nào z")
$sampleRows = [
    ['id' => '11', 'name' => 'Trà đào cam sả', 'price' => 35000, 'category' => 'Nước giải khát', 'description' => 'Trà đào cam sả mát lạnh', 'restaurant_name' => 'Mây House'],
    ['id' => '12', 'name' => 'Cà phê muối Huế', 'price' => 29000, 'category' => 'Cà phê', 'description' => 'Cà phê kem muối', 'restaurant_name' => 'Mây House'],
];
$refLookup1 = MealChat::lookup('cái thứ 2 giá sao zậy', $sampleRows, [['question' => 'gợi ý đồ uống', 'dishIds' => ['11', '12']]]);
okCtx($refLookup1 !== null && $refLookup1['kind'] === 'price' && $refLookup1['rows'][0]['id'] === '12', 'Implicit "cái thứ 2 giá sao zậy" resolves to dish #12 price');
$refLookup2 = MealChat::lookup('ly đầu tiên bán ở quán nào z', $sampleRows, [['question' => 'gợi ý đồ uống', 'dishIds' => ['11', '12']]]);
okCtx($refLookup2 !== null && $refLookup2['kind'] === 'store' && $refLookup2['rows'][0]['id'] === '11', 'Implicit "ly đầu tiên bán ở quán nào z" resolves to dish #11 store');

// 8. Dish profile tap FAQ & extended Seller/Shipper operational FAQs (3 checks)
okCtx(str_contains((string)askRole($db, 'Khi nhấn vào thẻ món ăn AI gợi ý thì mở ra trang gì vậy?'), 'Chi tiết món ăn'), 'Dish profile tap FAQ');
okCtx(str_contains((string)askRole($db, 'Làm sao xem chi tiết biểu đồ doanh thu tuần và tháng vậy em?', 'seller', 2), 'Chi tiết'), 'Seller detailed revenue chart FAQ');
okCtx(str_contains((string)askRole($db, 'Anh đang giao đơn thì xe bị bể bánh giữa đường xử lý sao?', 'driver', 3), 'Gửi phiếu hỗ trợ'), 'Driver vehicle breakdown FAQ');

// 9. New signature restaurant categories dynamic matching & combo classification (6 checks)
$newCats = ['Cơm tấm', 'Món Hàn', 'Hải sản', 'Món Nhật', 'Chè', 'Sinh tố'];
okCtx(in_array('Hải sản', MenuKnowledge::matchedCategories('Gợi ý giúp mình vài món hải sản tươi ngon', $newCats), true), 'Dynamic category matches Hải sản');
okCtx(in_array('Món Nhật', MenuKnowledge::matchedCategories('Tối nay muốn ăn đồ Nhật', $newCats), true), 'Dynamic category matches Món Nhật');
okCtx(in_array('Món Hàn', MenuKnowledge::matchedCategories('Có quán món Hàn nào ngon ko', $newCats), true), 'Dynamic category matches Món Hàn');
okCtx(in_array('Sinh tố', MenuKnowledge::matchedCategories('Uống sinh tố trái cây mát lạnh', $newCats), true), 'Dynamic category matches Sinh tố');
okCtx(MenuKnowledge::isMainDish(['name' => 'Mì Ramen Tonkotsu', 'category' => 'Món Nhật']), 'Ramen Món Nhật classified as main dish');
okCtx(MenuKnowledge::isSideDish(['name' => 'Chè khúc bạch hạnh nhân', 'category' => 'Chè']), 'Chè classified as side/dessert dish');

echo "PASS: {$checks} Vietnamese context & dataset checks\n";
