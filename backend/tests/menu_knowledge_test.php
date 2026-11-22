<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/ai/MealAdvisor.php';
$db=new PDO('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
$db->sqliteCreateFunction('CONCAT_WS',static fn($sep,...$parts)=>implode($sep,$parts));
$db->exec("CREATE TABLE dishes(id INTEGER PRIMARY KEY,store_id INTEGER,name TEXT,description TEXT,price INTEGER,image_url TEXT,menu_category_id INTEGER,deleted_at TEXT,is_available INTEGER,stock_quantity INTEGER,rating_avg REAL);
CREATE TABLE stores(id INTEGER,name TEXT,merchant_id INTEGER,deleted_at TEXT,status TEXT,is_open INTEGER);
CREATE TABLE users(id INTEGER,status TEXT,deleted_at TEXT);
CREATE TABLE merchants(id INTEGER,owner_user_id INTEGER,status TEXT);
CREATE TABLE menu_categories(id INTEGER,name TEXT);
INSERT INTO stores VALUES(1,'Quán A',1,NULL,'active',1),(2,'Quán B',1,NULL,'active',1),(3,'Đã đóng',1,NULL,'active',0);
INSERT INTO users VALUES(1,'active',NULL);
INSERT INTO merchants VALUES(1,1,'active');
INSERT INTO menu_categories VALUES(1,'Món chính'),(2,'Tráng miệng');");
$q=$db->prepare("INSERT INTO dishes VALUES(?,?,?,?,?,'',?,NULL,1,10,?)");
for ($i=1;$i<=105;$i++) {
    $q->execute([$i,1+$i%2,$i===104?'Bánh pancake đặc biệt':"Món $i",$i===104?'vỏ giòn':'',30000+$i,$i%5===0?2:1,105-$i]);
}
$q->execute([106,3,'Quán đóng','',1,1,5]);
$n=0;
function expectMenu(bool $v,string $msg):void {global $n;if (!$v) throw new RuntimeException($msg);$n++;}
$stores=MenuKnowledge::stores($db,100000);
expectMenu(array_sum(array_column($stores,'dishes'))===105,'available dishes across all restaurants');
foreach (['Xem tiếp menu','xem thêm thực đơn','trang tiếp theo','mình muốn xem tiếp menu','còn món nào nữa không?'] as $s)
    expectMenu(MenuKnowledge::browse($s,$stores)==='next',$s);
expectMenu(MenuKnowledge::browse('Xem thực đơn của Quán B',$stores)==='first','named restaurant');
expectMenu(MenuKnowledge::browse('menu món chay',$stores)===null,'do not drop dietary filter');
expectMenu(MenuKnowledge::browse('Cho mình xem menu dưới 80k',$stores)==='first','browse with budget');
$seen=[];$context=[];$i=0;
do {
    $input=['message'=>$i?'xem tiếp menu':'xem thực đơn','history'=>$context];
    $page=MenuKnowledge::page($db,$i?'next':'first',$input,$stores,100000);
    $ids=array_map('strval',array_column($page['rows'],'id'));
    $seen=array_merge($seen,$ids);
    $context=[['question'=>'món đầu tiên giá bao nhiêu','dishIds'=>[$ids[0]],'menuCursor'=>$page['cursor']]];
    $i++;
} while ($page['hasMore'] && $i<20);
expectMenu($seen===array_map('strval',range(1,105)) && $i===14,'all pages past 80 after other questions');
$old=[['question'=>'Xem thực đơn','dishIds'=>['1','2','3']],['question'=>'giá món đầu tiên','dishIds'=>['1']]];
$db->exec('UPDATE dishes SET is_available=0 WHERE id=3');
$page=MenuKnowledge::page($db,'next',['message'=>'Xem tiếp menu','history'=>$old],$stores,100000);
expectMenu((int)$page['rows'][0]['id']===4,'deleted cursor row plus intervening turn');
$scope=MenuKnowledge::page($db,'first',['message'=>'Xem thực đơn của Quán B','history'=>[]],$stores,100000);
expectMenu(array_unique(array_column($scope['rows'],'store_id'))===[2],'same restaurant page');
$next=MenuKnowledge::page($db,'next',['message'=>'Xem tiếp menu','history'=>[['question'=>'giá','dishIds'=>[],'menuCursor'=>$scope['cursor']]]],$stores,100000);
expectMenu(array_unique(array_column($next['rows'],'store_id'))===[2] && $next['rows'][0]['id']>$scope['cursor']['afterId'],'same scope after unrelated turn');
$rows=MenuKnowledge::candidates($db,100000,false,'pancake');
expectMenu((int)$rows[0]['id']===104,'search beyond original catalog slice');
$input=['message'=>'Đề xuất cho tôi 1 khẩu phần ăn trưa dưới 80k','budget'=>100000,'budgetScope'=>'food'];
$intent=MenuKnowledge::mealIntent($input,$stores);
$meal=MenuKnowledge::meal($rows,65000,$intent);
expectMenu($intent['canFilter'] && count($meal)===2,'portion request understood locally');
expectMenu($meal[0]['store_id']===$meal[1]['store_id'] && array_sum(array_column($meal,'price'))<=65000,'one restaurant, within total');
expectMenu(count(MenuKnowledge::meal($rows,40000,$intent))===1,'single main when side exceeds cap');
expectMenu(MenuKnowledge::meal($rows,1000,$intent)===[],'impossible budget');
$restricted=MenuKnowledge::mealIntent(array_replace($input,['message'=>'Khẩu phần ăn trưa không ăn gà']),$stores);
expectMenu(MenuKnowledge::meal($rows,80000,$restricted)===[],'unresolved exclusion never ignored');
expectMenu(MenuKnowledge::evidence('Ở đây nhà hàng nào bán pancake vậy?',$rows,[])['rows'][0]['id']===104,'arbitrary real dish lookup');
expectMenu(MenuKnowledge::evidence('Không ăn pancake',$rows,[])===null,'negation preserved');
$follow=['message'=>'Vậy nếu chỉ có 60k thì sao?','budget'=>1000000,'budgetScope'=>'food',
    'history'=>[['question'=>'Khẩu phần ăn trưa dưới 80k','dishIds'=>['1']]]];
$resolved=MenuKnowledge::mealQuestion($follow);
$refined=MenuKnowledge::mealIntent(array_replace($follow,['message'=>$resolved]),$stores);
expectMenu(MenuKnowledge::wantsMeal($resolved) && $refined['budget']===60000.0 && $refined['canFilter'],'budget-only meal follow-up');
expectMenu(MealChat::renderReply('Có {somon} món ở {soquan} quán.',[],['dishes'=>105,'restaurants'=>2])==='Có 105 món ở 2 quán.','database counts for open Q&A');
$storeSummary=['dishes'=>2,'restaurants'=>1,'stores'=>[['id'=>'12','name'=>'Quán thử','dishes'=>'2','min_price'=>'35000.00','max_price'=>'48000.00']]];
expectMenu(MealChat::renderReply('{tenquan:12} có {demquan:12} món từ {mingia:12} đến {maxgia:12}.',[],$storeSummary)==='Quán thử có 2 món từ 35.000đ đến 48.000đ.','server-bound restaurant facts');
expectMenu(str_contains(MenuKnowledge::storeComparison('quan nao co nhieu mon nhat',$stores),'Quán B'),'restaurant with most items');
expectMenu(str_contains(MenuKnowledge::storeComparison('so sanh gia giua cac quan',$storeSummary['stores']),'48.000đ'),'database price ranges');
expectMenu(MealChat::renderReply('Bạn muốn món chính hay món tráng miệng?',[],[],true)!==null,'open clarification without invented ID');
echo "PASS: $n menu/meal checks\n";
