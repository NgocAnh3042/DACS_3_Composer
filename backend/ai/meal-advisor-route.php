<?php
declare(strict_types=1);
if(!isset($pdo,$uid,$config,$base)){http_response_code(404);exit;}
require_once __DIR__.'/MealAdvisor.php';
require_once __DIR__.'/FoodHubSupport.php';
$started=microtime(true);$providerMs=0.0;$lock=null;
try{
    $me=account($pdo,$uid);
    if($me['status']!=='active'||!in_array($me['role'],['customer','admin','seller','driver'],true))
        throw new MealAdvisorException(403,'Tài khoản chưa được phép dùng tư vấn món.');
    $input=MealAdvisor::validate(body());
    $fee=max(0,(float)($config['delivery_fee']??15000));
    $personal=$me['role']==='customer' && preg_match('/\b(goi y|tu van|chon)\b.*\b(lich su|tung dat|hay dat)\b/',MealAdvisor::folded($input['message']));
    $support=$personal?null:FoodHubSupport::reply($pdo,$me,$input,$fee,static function(string $question,string $previous) use ($base,$uid): string {
        try {
            MealAdvisor::takeQuota(foodhubCacheFile('meal-quota-'.hash('sha256',$uid).'.json'),time());
            return (new MealAdvisor(MealAdvisor::groqSettings($base)))->supportTopic($question,$previous);
        } catch (MealAdvisorException $e) { return 'clarify'; }
    });
    if ($support!==null) out(200,['data'=>['message'=>trim($support),'preferences'=>'','budget'=>$input['budget'],
        'budgetScope'=>$input['budgetScope'],'deliveryFee'=>$fee,'catalogLimited'=>false,'recommendations'=>[],
        'source'=>'support','kind'=>'support','hasMore'=>false]]);
    $intentInput=array_replace($input,['message'=>MenuKnowledge::mealQuestion($input)]);
    $intent=MealAdvisor::localIntent($intentInput);
    if (preg_match('/\b(gom|ca|kem|tinh ca|bao gom) (phi (giao( hang)?|ship)|ship)\b/',MenuKnowledge::text($intentInput['message']))) $input['budgetScope']='total';
    $reserve=$input['budgetScope']==='total'?$fee:0;
    $ceiling=$intent['budget']-$reserve;
    $stores=MenuKnowledge::stores($pdo,$ceiling);
    $mentions=MenuKnowledge::mentionedStores($intentInput['message'],$stores);
    $storeId=count($mentions)===1?(string)$mentions[0]['id']:null;
    $scopedStores=array_values(array_filter($storeId!==null?$mentions:$stores,static fn($s)=>(int)$s['dishes']>0));
    $summary=['dishes'=>array_sum(array_column($scopedStores,'dishes')),'restaurants'=>count($scopedStores),
        'stores'=>array_slice($scopedStores,0,40)];
    $browse=MenuKnowledge::browse($input['message'],$stores);
    $page=null;
    $lastTurn=$input['history']?end($input['history']):[];
    $refs=$lastTurn['dishIds']??[];
    if ($personal) {
        $recent=$pdo->prepare("SELECT i.dish_id FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.customer_id=? AND o.status='completed' AND i.dish_id IS NOT NULL GROUP BY i.dish_id ORDER BY SUM(i.quantity) DESC,MAX(o.placed_at) DESC LIMIT 8");
        $recent->execute([$me['id']]);
        $refs=array_map('strval',$recent->fetchAll(PDO::FETCH_COLUMN));
        $summary['previouslyOrderedDishIds']=$refs;
    }
    $meal=MenuKnowledge::wantsMeal($intentInput['message'])&&!$intent['unsupported'];
    $question=MenuKnowledge::text($input['message']);
    foreach ($mentions as $store) $question=trim(str_replace(' '.MenuKnowledge::text($store['name']).' ',' ',' '.$question.' '));
    $question=trim(preg_replace('/\s+/',' ',$question));
    $comparison=MenuKnowledge::storeComparison($question,$scopedStores);
    $overview=(bool)preg_match('/^(?:(?:foodhub|ung dung|thuc don|menu|nha hang|quan|hien tai) )?(?:(?:hien tai|dang) )?(?:co |gom |tong cong |liet ke )?(?:bao nhieu (?:mon(?: an)?|quan|nha hang)|(?:nhung |cac )?(?:nha hang|quan) nao)(?: dang (?:mo cua|ban)| trong ung dung| tren foodhub| trong thuc don| hien tai)?$/',$question);
    $rankingText=$intent['cheap']&&$intent['canFilter']&&!$intent['filters']?'':$intentInput['message'];
    $fetch=static fn(float $cap)=>MenuKnowledge::candidates($pdo,$cap,$intent['cheap'],$rankingText,$rankingText===''?[]:$refs,$storeId);
    $overview=$overview||$comparison!==null;
    if ($browse!==null) $page=MenuKnowledge::page($pdo,$browse,$input,$stores,$ceiling,$reserve);
    $rows=$page!==null?$page['rows']:($ceiling>=0&&!$intent['unsupported']&&!$overview?$fetch($ceiling):[]);
    $limited=count($rows)>80;
    $menu=array_slice($rows,0,80);
    $dishQuestion=$input['message'];
    foreach ($mentions as $store) $dishQuestion=str_ireplace($store['name'],' ',$dishQuestion);
    $noodles=$page===null && !$overview && MealAdvisor::asksForNoodles($dishQuestion) && MealAdvisor::asksForNoodles($question);
    if ($noodles) $menu=array_values(array_filter($menu,[MealAdvisor::class,'noodleDish']));
    $lookup=$page!==null?['kind'=>'page','rows'=>$menu]:($overview?['kind'=>'overview','rows'=>[]]:
        ($intent['unsupported']||$meal?null:MealChat::lookup($input['message'],$menu,$input['history'])));
    $kind=$lookup['kind']??'suggest';
    // A short refinement may depend on the preceding turn. Let Gemini interpret it with history.
    $lowestOnly=(bool)preg_match('/\b(re nhat|gia thap nhat)\b/',MealAdvisor::folded($input['message']));
    if ($input['history'] && !$lookup && !$lowestOnly && !$noodles)
        $intent['canFilter']=false;
    $eligible=$lookup!==null?$lookup['rows']:MealAdvisor::matchingRows($menu,$intent);
    $source='filters';$cacheHit=false;$plan=null;$providerProblem=false;$selections=[];$fallbackReason=null;
    $direct=$lookup!==null||(!$meal&&$eligible&&$intent['canFilter']);
    if ($lookup!==null) {
        foreach ($eligible as $row) $selections[]=['id'=>(string)$row['id'],'reason'=>'Có trong thực đơn đang bán.'];
    } elseif ($direct) {
        $selections=MealAdvisor::fallback($eligible,$intent);
        if ($lowestOnly) $selections=array_slice($selections,0,1);
    }
    // Empty/unsupported requests never incur a model round trip.
    if($eligible&&!$intent['unsupported']&&!$direct){
        $settings=MealAdvisor::groqSettings($base);
        $advisor=new MealAdvisor($settings);
        $accountKey=hash('sha256',$uid);$modelKey=hash('sha256',$settings['model'].'|'.$settings['api_key']);
        $key=hash('sha256',json_encode(['version'=>6,'model'=>$modelKey,'input'=>$input,'budget'=>$intent['budget'],'summary'=>$summary,
            'fee'=>$fee,'catalog'=>MealAdvisor::catalogFingerprint($eligible)],JSON_UNESCAPED_UNICODE));
        $cacheFile=foodhubCacheFile('meal-result-'.$accountKey.'.json');
        $cooldownFile=foodhubCacheFile('meal-provider-'.$modelKey.'.json');
        $cached=MealAdviceCache::read($cacheFile,$key,time());
        if($cached!==null){
            try{$plan=MealAdvisor::validatePlan($cached,$intent['budget']);$source='cache';$cacheHit=true;}
            catch(MealAdvisorException $ignored){$plan=null;@unlink($cacheFile);}
        }
        $cooldown=MealAdviceCache::read($cooldownFile,$modelKey,time());
        if($plan===null&&$cooldown===null){
            $lock=@fopen(foodhubCacheFile('meal-inflight-'.$accountKey.'.lock'),'c+');
            // Never queue a second slow request for the same account.
            if($lock&&flock($lock,LOCK_EX|LOCK_NB)){
                try{
                    $cached=MealAdviceCache::read($cacheFile,$key,time());
                    if($cached!==null){$plan=MealAdvisor::validatePlan($cached,$intent['budget']);$source='cache';$cacheHit=true;}
                    else{
                        $advisor->ready();
                        MealAdvisor::takeQuota(foodhubCacheFile('meal-quota-'.$accountKey.'.json'),time());
                        $request=$input;$request['budget']=$intent['budget'];$request['summary']=$summary;$request['foodCeiling']=$ceiling;$providerStart=microtime(true);
                        try{$plan=$advisor->advise($request,$eligible);}
                        finally{$providerMs=round((microtime(true)-$providerStart)*1000,1);}
                        $source='groq';MealAdviceCache::write($cacheFile,$key,$plan,time()+300);
                    }
                }catch(MealAdvisorException $error){
                    $providerProblem=true;
                    $fallbackReason=$error->reason?:($error->status===429?'account_quota':'provider_error');
                    if($error->status!==429)MealAdviceCache::write($cooldownFile,$modelKey,['unavailable'=>true,'reason'=>$fallbackReason],time()+30);
                }finally{flock($lock,LOCK_UN);fclose($lock);$lock=null;}
            }else{$providerProblem=true;$fallbackReason='inflight';if(is_resource($lock)){fclose($lock);$lock=null;}}
        }elseif($plan===null){$providerProblem=true;$fallbackReason=$cooldown['reason']??'cooldown';}
        if($plan!==null){
            $kind=$plan['kind'];
            $intent['budget']=$plan['budgetVnd']>0?min($intent['budget'],$plan['budgetVnd']):$intent['budget'];
            $intent['unsupported']=$plan['unsupported'];$ceiling=$intent['budget']-$reserve;
            $selections=$intent['unsupported']||$kind==='clarify'?[]:$plan['selections'];
            // Cache hits used fresh SQL; after a live call, recheck prices and availability.
            if($source==='groq'){
                $allowed=array_fill_keys(array_column($eligible,'id'),true);
                $eligible=array_values(array_filter($fetch($ceiling),static fn($r)=>isset($allowed[$r['id']])));
                $menu=$eligible;
                $eligible=MealAdvisor::matchingRows($eligible,$intent);
            }
        }else{
            $source='filters';$selections=MealAdvisor::fallback($eligible,$intent);
            if (!$selections && !$meal && !$intent['unsupported']) {
                $evidence=MenuKnowledge::evidence($input['message'],$eligible,$refs);
                if ($evidence!==null) {
                    $kind=$evidence['kind'];
                    foreach ($evidence['rows'] as $r) $selections[]=['id'=>(string)$r['id'],'reason'=>'Tra theo tên, nhóm món và mô tả trong thực đơn.'];
                }
            }
        }
    }
    $recommendations=MealAdvisor::reconcile($selections,$eligible,$ceiling,$fee,$page!==null?MenuKnowledge::PAGE_SIZE:3);
    if (($meal||$kind==='meal')&&!$intent['unsupported']) {
        $kind='meal';
        $sameStore=count(array_unique(array_column(array_column($recommendations,'dish'),'restaurantId')))===1;
        $cost=array_sum(array_column(array_column($recommendations,'dish'),'price'));
        if ($source==='filters'||!$sameStore||$cost>$ceiling||count($recommendations)>2||!$recommendations) {
            $parts=MenuKnowledge::meal($menu,$ceiling,MenuKnowledge::mealIntent($intentInput,$stores));
            $partsSelection=array_map(static fn($r)=>['id'=>(string)$r['id'],'reason'=>'Một phần trong bữa ăn cùng quán.'],$parts);
            $recommendations=MealAdvisor::reconcile($partsSelection,$parts,$ceiling,$fee);
            $source='filters';
        }
    }
    if ($intent['unsupported']) $message='Mình có thể tra món, so sánh quán và ghép một bữa ăn cho một người. Với yêu cầu về dị ứng hoặc sức khỏe, bạn hãy xác nhận trực tiếp với quán.';
    elseif ($page!==null && ($recommendations||$browse==='next')) {
        $message=$recommendations?'Trang này có '.count($recommendations).' món đang bán.':'Bạn đã đến cuối thực đơn trong phạm vi đang xem.';
        if ($page['hasMore']) $message.=' Nhắn “xem tiếp menu” để xem trang sau.';
        elseif ($recommendations) $message.=' Đây là trang cuối.';
    } elseif ($overview) {
        $names=array_map(static fn($s)=>$s['name'].' ('.$s['dishes'].' món)',array_slice($scopedStores,0,12));
        $message=$comparison??('Trong ngân sách đang xét có '.$summary['dishes'].' món ở '.$summary['restaurants'].' quán đang mở.'.($names?' '.implode(', ',$names).'.':''));
    } elseif ($kind==='meal' && $recommendations) $message=MenuKnowledge::mealAnswer($recommendations,$fee,$reserve>0);
    elseif ($recommendations || in_array($kind,['greeting','clarify','end','overview'],true)) {
        $message=MealChat::renderReply($plan['reply']??'',$recommendations,$summary,$kind==='clarify')??MealChat::answer($kind,$recommendations);
    }
    elseif (!$menu && $noodles) $message='Mình chưa tìm thấy món mì đang bán trong ngân sách và phạm vi bạn chọn.';
    elseif (!$menu) {
        $lowest=MenuKnowledge::candidates($pdo,10000000,true,'',[],$storeId)[0]??null;
        if (!$lowest) $message='Hiện chưa có món nào có thể đặt từ các quán đang mở. Bạn ghé lại sau nhé.';
        elseif ((float)$lowest['price']>$ceiling)
            $message='Trong ngân sách này chưa có món đang bán. Lựa chọn thấp nhất hiện là '.$lowest['name'].' với '.number_format((float)$lowest['price']+$reserve,0,',','.').'đ'.($reserve?' gồm phí giao.':'.');
        else $message='Thực đơn vừa thay đổi. Bạn nhắn “Xem thực đơn” để tra những món hiện có nhé.';
    } else {
        $examples=implode(', ',array_column(array_slice($menu,0,2),'name'));
        $message=match (true) {
            $fallbackReason==='provider_unconfigured'=>'FoodHub đang chờ khóa Groq để trả lời câu hỏi mở.',
            in_array($fallbackReason,['account_quota','provider_quota'],true)=>'Trợ lý đang giới hạn lượt hỏi.',
            $providerProblem=>'Groq tạm thời chưa trả lời được câu này.',
            default=>'Trong các món đang xét, mình chưa thấy thông tin xác nhận yêu cầu của bạn.'
        };
        $message.=' Thực đơn hiện có '.$examples.'. Bạn muốn xem giá hoặc mô tả món nào?';
    }
    $elapsed=round((microtime(true)-$started)*1000,1);
    header('Server-Timing: meal;dur='.$elapsed.', gemini;dur='.$providerMs);
    out(200,['data'=>['message'=>$message,'preferences'=>$intent['preferences'],'budget'=>$intent['budget'],
        'budgetScope'=>$input['budgetScope'],'deliveryFee'=>$fee,'catalogLimited'=>$limited,'recommendations'=>$recommendations,
        'menuCursor'=>$page['cursor']??null,'hasMore'=>$page['hasMore']??false,'kind'=>$kind,
        'source'=>$source,'cacheHit'=>$cacheHit,'model'=>isset($settings)?$settings['model']:null,'elapsedMs'=>$elapsed,'geminiMs'=>$providerMs,'fallbackReason'=>$fallbackReason]]);
}catch(MealAdvisorException $error){out($error->status,['error'=>$error->getMessage()]);}
catch(Throwable $error){error_log('Meal advisor failed: '.get_class($error));out(503,['error'=>'Tư vấn món tạm thời gián đoạn. Vui lòng thử lại sau.']);}
finally{if(is_resource($lock)){flock($lock,LOCK_UN);fclose($lock);}}
