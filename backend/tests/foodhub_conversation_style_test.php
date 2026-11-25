<?php
declare(strict_types=1);
require_once __DIR__.'/../ai/MealAdvisor.php';
require_once __DIR__.'/../ai/FoodHubSupport.php';
$cases=[
    ['Ê shop, ship lâu z tr, đói xỉu lun.','order'],
    ['don cua tui dau roi shop','order'],
    ['Shop dễ thương ghê á.','thanks'],
    ['Tui mong chờ món này cả ngày mà giờ giao sai, buồn ghê.','incident'],
    ['À mà FoodHub có thanh toán tiền mặt không?','pay'],
    ['Nói ngắn thôi, đơn bao giờ tới?','order'],
    ['tks','thanks'],['Bạn là người thật à?','identity']
];
foreach($cases as [$text,$expected]) {
    if(FoodHubSupport::intent($text)!==$expected) throw new RuntimeException($text);
}
if(FoodHubSupport::detectEmotion('buồn ghê')!=='sad'||FoodHubSupport::detectEmotion('tui chưa hiểu')!=='confused') throw new RuntimeException('Emotion mismatch');
echo "PASS: Vietnamese slang, identity, topic changes, brevity requests and emotions.\n";
