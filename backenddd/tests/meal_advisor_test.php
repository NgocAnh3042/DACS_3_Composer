<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/ai/MealAdvisor.php';
$checks = 0;
function check(bool $condition, string $name): void {
    global $checks; if (!$condition) throw new RuntimeException('FAILED: '.$name); $checks++;
}
function rejects(callable $callback, int $status): void {
    try { $callback(); } catch (MealAdvisorException $e) { check($e->status === $status, 'error status'); return; }
    throw new RuntimeException('Expected rejection');
}
$request = MealAdvisor::validate(['message'=>'Món nước dưới 60k','budget'=>70000,'budgetScope'=>'total']);
check($request['budgetScope'] === 'total', 'scope');
foreach ([[], ['message'=>[], 'budget'=>70000], ['message'=>' ', 'budget'=>70000], ['message'=>str_repeat('a',501),'budget'=>70000],
    ['message'=>'Cơm','budget'=>-1], ['message'=>'Cơm','budget'=>'70000'], ['message'=>'Cơm','budget'=>INF],
    ['message'=>'Cơm','budget'=>70000,'budgetScope'=>'other']] as $invalid) rejects(fn()=>MealAdvisor::validate($invalid),422);
rejects(fn()=>(new MealAdvisor([]))->ready(),503);
$advisor = new MealAdvisor(['api_key'=>'test'], static fn($p)=>['budgetVnd'=>60000,'preferences'=>'Món nước','unsupported'=>false]);
check($advisor->interpret($request)['budget'] === 60000.0, 'natural language lowers cap');
$advisor = new MealAdvisor(['api_key'=>'test'], static fn($p)=>['budgetVnd'=>90000,'preferences'=>'Cơm','unsupported'=>false]);
check($advisor->interpret($request)['budget'] === 70000.0, 'model cannot raise cap');
$advisor = new MealAdvisor(['api_key'=>'test'], static fn($p)=>['budgetVnd'=>0,'preferences'=>'Cơm','unsupported'=>false]);
check($advisor->interpret($request)['budget'] === 70000.0, 'missing budget preserves form');
$advisor = new MealAdvisor(['api_key'=>'test'], static fn($p)=>['budgetVnd'=>0,'preferences'=>'Dị ứng','unsupported'=>true]);
check($advisor->interpret($request)['unsupported'], 'unsupported requirements');
$advisor = new MealAdvisor(['api_key'=>'test'], static fn($p)=>['budgetVnd'=>-1,'preferences'=>'','unsupported'=>false]);
rejects(fn()=>$advisor->interpret($request),502);
$hydrogen = new MealAdvisor(['api_key'=>'test','model'=>'gemini-4-hydrogen'], static function($payload) {
    check(($payload['generationConfig']['thinkingConfig']['thinkingLevel']??'') === 'LOW', 'Hydrogen low thinking');
    check(!isset($payload['generationConfig']['thinkingConfig']['thinkingBudget']), 'Hydrogen has no legacy thinking budget');
    check(!isset($payload['generationConfig']['temperature']), 'Hydrogen ignores sampling controls');
    return ['budgetVnd'=>0,'preferences'=>'Cơm','unsupported'=>false];
});
$hydrogen->interpret($request);
$legacy = new MealAdvisor(['api_key'=>'test','model'=>'gemini-2.5-flash'], static function($payload) {
    check(($payload['generationConfig']['thinkingConfig']['thinkingBudget']??null) === 0, 'legacy thinking configuration');
    return ['budgetVnd'=>0,'preferences'=>'Cơm','unsupported'=>false];
});
$legacy->interpret($request);
$rows = [];
foreach ([1=>40000,2=>65000,3=>30000,4=>20000,5=>10000] as $id=>$price) $rows[] = ['id'=>$id,'store_id'=>9,
    'name'=>'Món '.$id,'price'=>$price,'restaurant_name'=>'Quán thật','image_url'=>'real.jpg'];
$selected = [['id'=>'999','reason'=>'Bịa món'],['id'=>'1','reason'=>'Món thật','price'=>1,'restaurantName'=>'Bịa quán'],
    ['id'=>'1','reason'=>'Trùng'],['id'=>'2','reason'=>'Vượt tiền'],['id'=>'3','reason'=>'Có thật'],
    ['id'=>'4','reason'=>'Có thật'],['id'=>'5','reason'=>'Có thật']];
$actual = MealAdvisor::reconcile($selected,$rows,45000,15000);
check(count($actual) === 3, 'at most three unique affordable known IDs');
check($actual[0]['dish']['price'] === 40000.0 && $actual[0]['restaurantName'] === 'Quán thật', 'DB owns prices/store');
check($actual[0]['estimatedTotal'] === 55000.0, 'delivery fee arithmetic');
check(count(MealAdvisor::reconcile([['id'=>'1','reason'=>'ok']],[],45000,15000)) === 0, 'unavailable after ranking removed');
check(count(MealAdvisor::reconcile([['id'=>'1','reason'=>[]]],$rows,45000,15000)) === 0, 'invalid reason removed');
$file = tempnam(sys_get_temp_dir(),'meal-quota-');
try {
    for ($i=0;$i<4;$i++) MealAdvisor::takeQuota($file,10000);
    rejects(fn()=>MealAdvisor::takeQuota($file,10000),429);
    MealAdvisor::takeQuota($file,10061);
    check(count(json_decode(file_get_contents($file),true)) === 5, 'minute limit expires');
    for ($i=0;$i<15;$i++) MealAdvisor::takeQuota($file,10122+$i*61);
    rejects(fn()=>MealAdvisor::takeQuota($file,11100),429);
    MealAdvisor::takeQuota($file,15000);
    check(count(json_decode(file_get_contents($file),true)) === 1, 'hour limit expires');
} finally { unlink($file); }
echo "PASS: $checks meal advisor checks\n";
