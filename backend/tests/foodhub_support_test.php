<?php
declare(strict_types=1);
require __DIR__.'/../ai/MealAdvisor.php';
require __DIR__.'/../ai/FoodHubSupport.php';
$db=new PDO('sqlite::memory:',null,null,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$db->sqliteCreateFunction('NOW',static fn()=>'2026-11-19');
$db->exec("CREATE TABLE orders(id INTEGER PRIMARY KEY, status,customer_id INTEGER,store_id INTEGER,placed_at,total_amount,delivery_fee,payment_method,payment_status,cancelled_at,cancel_reason);
CREATE TABLE stores(id INTEGER,merchant_id INTEGER,name); CREATE TABLE merchants(id INTEGER,owner_user_id INTEGER);
CREATE TABLE deliveries(order_id INTEGER,driver_id INTEGER,status); CREATE TABLE drivers(id INTEGER,user_id INTEGER);
CREATE TABLE users(id INTEGER,role,status,deleted_at,name); CREATE TABLE payments(id INTEGER,order_id INTEGER,status);
CREATE TABLE order_status_logs(order_id,status,actor_user_id,note);
CREATE TABLE conversations(id INTEGER PRIMARY KEY,order_id INTEGER,type,created_by INTEGER,status DEFAULT 'open');
CREATE TABLE conversation_participants(conversation_id,user_id); CREATE TABLE messages(id INTEGER PRIMARY KEY,conversation_id INTEGER,sender_user_id,message_type,content);
INSERT INTO users VALUES(1,'customer','active',NULL,'A'),(2,'customer','active',NULL,'B'),(3,'admin','active',NULL,'C'),(4,'driver','active',NULL,'D');
INSERT INTO stores VALUES(1,1,'Quán thử'); INSERT INTO merchants VALUES(1,5);
INSERT INTO drivers VALUES(1,4); INSERT INTO deliveries VALUES(101,1,'assigned');
INSERT INTO orders VALUES(101,'pending',1,1,1,70000,15000,'cash','pending',NULL,NULL),(102,'completed',2,1,2,80000,15000,'payos','paid',NULL,NULL);
INSERT INTO payments VALUES(1,102,'paid');");
function ask(string $message,array $history=[],string $role='customer',int $id=1):string {
    global $db;
    return FoodHubSupport::reply($db,compact('role','id'),compact('message','history'),15000)??'';
}
function check(bool $ok,string $name):void {if(!$ok) throw new RuntimeException($name);}
function turn(string $question,string $answer):array {return compact('question','answer');}
$q='Shop ơi, đơn tui đặt hồi nãy sao chưa thấy giao vậy?'; $a=ask($q);
check(str_contains($a,'Đơn #101') && str_contains($a,'Shipper'),'customer order');
check(str_contains(ask('Đơn #102'),'không tìm thấy'),'foreign order');
check(str_contains(ask('đơn #101',[],'driver',4),'Đơn #101'),'assigned driver');
check(str_contains(ask('đơn #102',[],'driver',4),'không tìm thấy'),'other driver');
check(str_contains(ask('đơn #101',[],'seller',5),'Đơn #101'),'seller scope');
check(str_contains(ask('đơn #101',[],'seller',6),'không tìm thấy'),'other seller');
check(str_contains(ask('Hoàn tiền đơn #102',[],'customer',2),'chưa thấy'),'refund unconfirmed');
$db->exec("UPDATE payments SET status='refunded'");
check(str_contains(ask('Hoàn tiền đơn #102',[],'customer',2),'ghi nhận hoàn tiền'),'confirmed refund');
$cancel='Vậy hủy giúp mình đi';$prompt=ask($cancel,[turn($q,$a)]);
check(str_contains($prompt,'Xác nhận hủy đơn #101'),'confirmation');
$yes='Xác nhận hủy đơn #101';check(str_contains(ask($yes,[turn($cancel,$prompt)]),'đã được hủy'),'cancel');
ask($yes,[turn($cancel,$prompt)]);
check((int)$db->query('SELECT COUNT(*) FROM order_status_logs')->fetchColumn()===1,'cancel once');
$spill='Đồ ăn giao tới bị đổ hết rồi!';$history=[turn($spill,ask($spill))];
check(str_contains(ask('Gửi phiếu hỗ trợ',$history),'phiếu #1'),'create ticket');
ask('Gửi phiếu hỗ trợ',$history);
check((int)$db->query('SELECT COUNT(*) FROM conversations')->fetchColumn()===1,'ticket once');
check((int)$db->query('SELECT COUNT(*) FROM conversation_participants WHERE user_id=3')->fetchColumn()===1,'admin inbox');
ask('Đánh giá: Chưa giải quyết',$history);
check(str_contains(ask('phản hồi chưa giải quyết',[],'admin',3),'Phản hồi chưa giải quyết (1)'),'review queue');
check(FoodHubSupport::intent('đơn của tui thanh toán lỗi')==='pay','payment precedence');
check(FoodHubSupport::intent('thực đơn của quán')===null,'menu precedence');
echo "FoodHub support checks passed\n";
