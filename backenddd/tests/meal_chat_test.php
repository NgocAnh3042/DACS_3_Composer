<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/ai/MealAdvisor.php';

$rows=[
    ['id'=>7,'name'=>'Bánh xèo','price'=>60000,'restaurant_name'=>'Quán thử','store_id'=>2,'description'=>'Bánh giòn'],
    ['id'=>9,'name'=>'Salad khai vị','price'=>65000,'restaurant_name'=>'Quán thử','store_id'=>2,'description'=>''],
];
$history=[['question'=>'Xem thực đơn','dishIds'=>['7','9']]];
$count=0;
function verify($ok,$label) { global $count; if (!$ok) throw new RuntimeException($label); $count++; }
foreach ([
    ['Xem thực đơn','menu',[7,9]],['Giá bánh xèo bao nhiêu?','price',[7]],
    ['Món thứ hai ở quán nào?','store',[9]],['Mô tả món đầu tiên','detail',[7]],
    ['Món đó giá bao nhiêu?','clarify',[]],['Xin chào!','greeting',[]],
    ['So sánh bánh xèo và salad khai vị','compare',[9,7]]
] as [$question,$kind,$ids]) {
    $answer=MealChat::lookup($question,$rows,$history);
    verify($answer['kind']===$kind && array_column($answer['rows'],'id')===$ids,$question);
}
foreach (['Không ăn bánh xèo','Bánh xèo không cay','Cháo','Salad khai vị nhiều protein'] as $question)
    verify(MealChat::lookup($question,$rows,$history)===null,'do not guess: '.$question);
verify(MealChat::lookup('Món thứ hai giá bao nhiêu?',[$rows[0]],$history)['kind']==='clarify','removed dish');
verify(MealChat::history($history)===$history,'history order');
$categories=$rows;
$categories[0]['category']='Món chính';
$categories[1]['category']='Khai vị';
verify(MealChat::lookup('Mình phân vân giữa các món khai vị, bạn chọn giúp món nào cho bữa tối?',$categories,[])['rows']===[$categories[1]],'real category without provider');
verify(MealChat::lookup('Món khai vị không cay',$categories,[])===null,'category does not discard taste constraint');
verify(MenuKnowledge::browse('Xem thêm thực đơn',[])==='next','route to SQL pagination');
verify(MenuKnowledge::browse('Xem thực đơn',[])==='first','start a new menu');
foreach ([array_fill(0,5,$history[0]),[['question'=>'x','dishIds'=>['bad']]],'bogus'] as $bad) {
    try { MealChat::history($bad); throw new RuntimeException('bad history accepted'); }
    catch (MealAdvisorException $e) { verify($e->status===422,'reject history'); }
}
$items=MealAdvisor::reconcile([['id'=>'7','reason'=>'ok'],['id'=>'9','reason'=>'ok']],$rows,100000,15000);
verify(str_contains(MealChat::answer('detail',$items),'Quán chưa bổ sung mô tả cho Salad khai vị'),'missing description');
verify(MealChat::renderReply('Thử {mon:7} giá {gia:7} tại {quan:7}.',$items)==='Thử Bánh xèo giá 60.000đ tại Quán thử.','DB substitutions');
verify(MealChat::renderReply('Thử {mon:8}.',$items)===null,'unknown or stale ID');
verify(MealChat::renderReply('Thử {mon:7} giá 1đ.',$items)===null,'reject model price');
$advisor=new MealAdvisor(['api_key'=>'test'],static function($payload) use ($history) {
    $input=json_decode($payload['contents'][0]['parts'][0]['text'],true);
    verify($input['history']===$history && $input['catalog'][0]['store']==='Quán thử','real menu and context sent together');
    return ['ids'=>['7'],'budgetVnd'=>0,'unsupported'=>false,'kind'=>'price','reply'=>'Món {mon:7} có giá {gia:7}.'];
});
$plan=$advisor->advise(['message'=>'Vậy giá thế nào?','budget'=>100000,'budgetScope'=>'food','history'=>$history],$rows);
verify($plan['kind']==='price' && str_contains(MealChat::renderReply($plan['reply'],$items),'60.000đ'),'conversational model reply');
echo "PASS: $count meal chat checks\n";

$groq=new MealAdvisor(['model'=>MealAdvisor::GROQ_MODEL,'api_key'=>'test'],static function($request) {
    verify($request['model']==='openai/gpt-oss-120b' && $request['messages'][0]['role']==='system','Groq chat request');
    $format=$request['response_format']['json_schema'];
    verify($format['strict'] && $format['schema']['additionalProperties']===false,'Groq strict schema');
    verify($format['schema']['properties']['ids']['items']['enum']===['7','9'],'IDs bound to real menu');
    return ['ids'=>['7'],'kind'=>'price','reply'=>'{mon:7} giá {gia:7}.','unsupported'=>false,'budgetVnd'=>0];
});
$plan=$groq->advise(['message'=>'Giá món đầu tiên?','budget'=>100000,'budgetScope'=>'food','history'=>$history],$rows);
verify(MealChat::renderReply($plan['reply'],$items)==='Bánh xèo giá 60.000đ.','Groq reply grounded in DB');
echo "PASS: Groq request and menu validation\n";
