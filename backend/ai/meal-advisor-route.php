<?php
declare(strict_types=1);
if(!isset($pdo,$uid,$config,$base)){http_response_code(404);exit;}
require_once __DIR__.'/MealAdvisor.php';
require_once __DIR__.'/FoodHubSupport.php';
$started=microtime(true);$providerMs=0.0;$memoryMs=0.0;$lock=null;$memoryLock=null;$lastUsage=null;$handoffCreated=false;$providerProblem=false;
try{
    $me=account($pdo,$uid);
    if($me['status']!=='active'||!in_array($me['role'],['customer','admin','seller','driver'],true))
        throw new MealAdvisorException(403,'Tài khoản chưa được phép dùng tư vấn món.');
    $request=body();
    $input=MealAdvisor::validate($request);
    $accountKey=hash('sha256',$uid);
    $prefsFile=foodhubCacheFile('ai-prefs-'.$accountKey.'.json');
    $mealCacheFile=foodhubCacheFile('meal-result-'.$accountKey.'.json');
    $metricsFile=foodhubCacheFile('ai-metrics-log.json');

    // Phase 4: Consent-aware personalization retrieval & controlled memory extraction
    $memStart=microtime(true);
    $userProfile=UserPersonalization::load($pdo,(int)$me['id'],$prefsFile);
    $approvedPrefs=UserPersonalization::approvedForPrompt($userProfile);
    if (!empty($userProfile['personalizationEnabled'])) {
        $candidates=UserPersonalization::extractCandidateMemories($input['message']);
        $applied=UserPersonalization::applyCandidates($userProfile,$candidates);
        if ($applied['changed']) {
            $userProfile=UserPersonalization::save($pdo,$applied['profile'],$prefsFile,$mealCacheFile);
            $approvedPrefs=UserPersonalization::approvedForPrompt($userProfile);
        }
    }
    $memoryMs=round((microtime(true)-$memStart)*1000,1);

    $chatId=$request['conversationId']??null;
    $chatSummary=[];
    $sessions=[];
    $memoryFile='';
    if ($chatId!==null) {
        if (!is_string($chatId)||!preg_match('/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/D',$chatId))
            throw new MealAdvisorException(422,'Mã hội thoại không hợp lệ.');
        $memoryFile=foodhubCacheFile('support-history-'.hash('sha256',$uid.'|'.$me['role']).'.json');
        $memoryLock=@fopen($memoryFile.'.lock','c');
        if (!$memoryLock || !flock($memoryLock,LOCK_EX|LOCK_NB)) throw new MealAdvisorException(429,'Bạn đợi lượt trước trả lời xong rồi gửi lại nhé.');
        $loaded=ConversationContext::loadSession($memoryFile,$chatId,$input['history'],time());
        $sessions=$loaded['sessions'];
        $input['history']=$loaded['history'];
        $chatSummary=$loaded['summary'];
    }
    $input['summary']=$chatSummary;
    $input['approvedPreferences']=$approvedPrefs;
    $respond=static function(int $status,array $body) use ($chatId,$input,&$sessions,&$memoryFile,&$chatSummary,&$memoryLock,$started,&$providerMs,$memoryMs,&$lastUsage,&$handoffCreated,&$providerProblem,$metricsFile): never {
        $elapsed=round((microtime(true)-$started)*1000,1);
        $data=&$body['data'];
        $data['messageId']='msg_'.bin2hex(random_bytes(8));
        $data['promptVersion']=FoodHubSystemPrompt::PROMPT_VERSION;
        if (is_array($lastUsage)) $data['usage']=$lastUsage;
        if ($chatId!==null && $memoryFile!=='') {
            $updated=ConversationContext::appendTurn(
                $input['history'],
                $chatSummary,
                $input['message'],
                (string)($data['message']??''),
                array_column(array_column($data['recommendations']??[],'dish'),'id'),
                is_array($data['menuCursor']??null)?$data['menuCursor']:null
            );
            $chatSummary=$updated['summary'];
            ConversationContext::saveSession($memoryFile,$sessions,$chatId,$updated['history'],$chatSummary,time());
        }
        AiQualityMonitor::recordMetric($metricsFile,[
            'latencyMs'=>$elapsed,
            'providerMs'=>$providerMs,
            'promptTokens'=>$lastUsage['promptTokens']??0,
            'completionTokens'=>$lastUsage['completionTokens']??0,
            'totalTokens'=>$lastUsage['totalTokens']??0,
            'source'=>$data['source']??'unknown',
            'providerError'=>$providerProblem,
            'handoff'=>$handoffCreated || str_contains((string)($data['message']??''),'Đã chuyển phiếu #'),
            'promptVersion'=>FoodHubSystemPrompt::PROMPT_VERSION,
        ]);
        header('Server-Timing: meal;dur='.$elapsed.', memory;dur='.$memoryMs.', provider;dur='.$providerMs);
        if (is_resource($memoryLock)) { flock($memoryLock,LOCK_UN); fclose($memoryLock); $memoryLock=null; }
        out($status,$body);
    };
    $fee=max(0,(float)($config['delivery_fee']??15000));
    $personal=$me['role']==='customer' && preg_match('/\b(goi y|tu van|chon)\b.*\b(lich su|tung dat|hay dat)\b/',MealAdvisor::folded($input['message']));
    $supportHumanized=false;
    $support=$personal?null:FoodHubSupport::reply($pdo,$me,$input,$fee,static function(string $question,string $previous) use ($base,$uid): string {
        try {
            MealAdvisor::takeQuota(foodhubCacheFile('meal-quota-'.hash('sha256',$uid).'.json'),time());
            return (new MealAdvisor(MealAdvisor::groqSettings($base)))->supportTopic($question,$previous);
        } catch (MealAdvisorException $e) { return 'clarify'; }
    }, static function(string $message, array $history, array $facts, string $fallback) use ($base,$uid,&$supportHumanized,$chatSummary,&$providerMs,&$lastUsage,&$handoffCreated): string {
        if (!empty($facts['handoffCreated'])) $handoffCreated=true;
        try {
            MealAdvisor::takeQuota(foodhubCacheFile('meal-quota-'.hash('sha256',$uid).'.json'),time());
            $facts['olderContextForReferenceOnly']=$chatSummary;
            $advisor=new MealAdvisor(MealAdvisor::groqSettings($base));
            $t0=microtime(true);
            $human=$advisor->humanizeSupport($message,$history,$facts,$fallback);
            $providerMs+=round((microtime(true)-$t0)*1000,1);
            if (is_array($advisor->lastUsage)) $lastUsage=$advisor->lastUsage;
            if ($human!==$fallback) $supportHumanized=true;
            return $human;
        } catch (Throwable $e) { return $fallback; }
    });
    if ($support!==null)  $respond(200,['data'=>['message'=>trim($support),'preferences'=>'','budget'=>$input['budget'],
        'budgetScope'=>$input['budgetScope'],'deliveryFee'=>$fee,'catalogLimited'=>false,'recommendations'=>[],
        'source'=>$supportHumanized?'groq':'support','kind'=>'support','hasMore'=>false]]);
    $intentInput=array_replace($input,['message'=>MenuKnowledge::mealQuestion($input)]);
    $intent=MealAdvisor::localIntent($intentInput);
    if (preg_match('/\b(gom|ca|kem|tinh ca|bao gom) (phi (giao( hang)?|ship)|ship)\b/',MenuKnowledge::text($intentInput['message']))) $input['budgetScope']='total';
    $reserve=$input['budgetScope']==='total'?$fee:0;
    $ceiling=$intent['budget']-$reserve;
    $stores=MenuKnowledge::stores($pdo,$ceiling);
    $mentions=MenuKnowledge::mentionedStores($intentInput['message'],$stores);
    $storeId=count($mentions)===1?(string)$mentions[0]['id']:null;
    $scopedStores=array_values(array_filter($storeId!==null?$mentions:$stores,static fn($s)=>(int)$s['dishes']>0));
    $categories=MenuKnowledge::categories($pdo,$ceiling,$storeId);
    $matchedCats=MenuKnowledge::matchedCategories($intentInput['message'],$categories);
    $summary=['dishes'=>array_sum(array_column($scopedStores,'dishes')),'restaurants'=>count($scopedStores),
        'stores'=>array_slice($scopedStores,0,40),'categories'=>array_slice($categories,0,30)];
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
    $fetch=static fn(float $cap)=>MenuKnowledge::candidates($pdo,$cap,$intent['cheap'],$rankingText,$rankingText===''?[]:$refs,$storeId,$matchedCats);
    $overview=$overview||$comparison!==null;
    if ($browse!==null) $page=MenuKnowledge::page($pdo,$browse,$input,$stores,$ceiling,$reserve);
    $rows=$page!==null?$page['rows']:($ceiling>=0&&!$intent['unsupported']&&!$overview?$fetch($ceiling):[]);
    $limited=count($rows)>80;
    $menu=array_slice($rows,0,80);
    if ($matchedCats && !$meal && $page===null && !$overview && !$intent['unsupported']) {
        $catRows=MenuKnowledge::filterByCategories($menu,$matchedCats);
        if ($catRows) $menu=$catRows;
    }
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
        $key=hash('sha256',json_encode(['version'=>10,'model'=>$modelKey,'input'=>$input,'budget'=>$intent['budget'],'summary'=>$summary,
            'approvedPrefs'=>$approvedPrefs,'fee'=>$fee,'catalog'=>MealAdvisor::catalogFingerprint($eligible)],JSON_UNESCAPED_UNICODE));
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
                        $request=$input;$request['budget']=$intent['budget'];$request['summary']=$summary;$request['chatSummary']=$chatSummary;$request['approvedPreferences']=$approvedPrefs;$request['adaptiveStyle']=UserPersonalization::resolveStyle($input['message'],FoodHubSupport::detectEmotion($input['message']),$approvedPrefs);$request['foodCeiling']=$ceiling;$providerStart=microtime(true);
                        try{$plan=$advisor->advise($request,$eligible);if(is_array($advisor->lastUsage))$lastUsage=$advisor->lastUsage;}
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
            if (!$selections && $matchedCats && !$meal && !$intent['unsupported']) {
                $catEligible=$eligible;
                if ($intent['cheap']) usort($catEligible,static fn($a,$b)=>(float)$a['price']<=>(float)$b['price']);
                foreach (array_slice($catEligible,0,3) as $r) {
                    $catLabel=trim((string)($r['category']??''))?:'thực đơn';
                    $selections[]=['id'=>(string)$r['id'],'reason'=>'Thuộc danh mục '.$catLabel.' tại '.($r['restaurant_name']??'nhà hàng').'.'];
                }
            }
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
        if (count($recommendations)===2 && MenuKnowledge::isSideDish($recommendations[0]['dish']) && MenuKnowledge::isMainDish($recommendations[1]['dish'])) {
            $recommendations=[$recommendations[1],$recommendations[0]];
        }
        $sameStore=count($recommendations)>0 && count(array_unique(array_column(array_column($recommendations,'dish'),'restaurantId')))===1;
        $cost=array_sum(array_column(array_column($recommendations,'dish'),'price'));
        $validMainSide=count($recommendations)===2 && MenuKnowledge::isMainDish($recommendations[0]['dish']) && MenuKnowledge::isSideDish($recommendations[1]['dish']);
        if ($source==='filters'||!$sameStore||$cost>$ceiling||!$validMainSide) {
            $parts=MenuKnowledge::meal($menu,$ceiling,MenuKnowledge::mealIntent($intentInput,$stores),$refs);
            if ($parts) {
                $partsSelection=[];
                foreach ($parts as $idx=>$r) {
                    $partsSelection[]=['id'=>(string)$r['id'],'reason'=>$idx===0?'Món chính trong combo cùng quán.':'Món phụ kèm theo trong combo cùng quán.'];
                }
                $recommendations=MealAdvisor::reconcile($partsSelection,$parts,$ceiling,$fee);
                $source='filters';
            } elseif (!$sameStore||$cost>$ceiling||count($recommendations)>2) {
                $recommendations=[];
            }
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
    } elseif ($kind==='meal' && $recommendations) {
        $renderedMeal=in_array($source,['groq','cache'],true)?MealChat::renderReply($plan['reply']??'',$recommendations,$summary,false):null;
        $message=$renderedMeal??MenuKnowledge::mealAnswer($recommendations,$fee,$reserve>0);
    }
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
     $respond(200,['data'=>['message'=>$message,'preferences'=>$intent['preferences'],'budget'=>$intent['budget'],
        'budgetScope'=>$input['budgetScope'],'deliveryFee'=>$fee,'catalogLimited'=>$limited,'recommendations'=>$recommendations,
        'menuCursor'=>$page['cursor']??null,'hasMore'=>$page['hasMore']??false,'kind'=>$kind,
        'source'=>$source,'cacheHit'=>$cacheHit,'model'=>isset($settings)?$settings['model']:null,'elapsedMs'=>$elapsed,'geminiMs'=>$providerMs,'fallbackReason'=>$fallbackReason]]);
}catch(MealAdvisorException $error){out($error->status,['error'=>$error->getMessage()]);}
catch(Throwable $error){error_log('Meal advisor failed: '.get_class($error));out(503,['error'=>'Tư vấn món tạm thời gián đoạn. Vui lòng thử lại sau.']);}
finally{if(is_resource($lock)){flock($lock,LOCK_UN);fclose($lock);}if(is_resource($memoryLock)){flock($memoryLock,LOCK_UN);fclose($memoryLock);}}
