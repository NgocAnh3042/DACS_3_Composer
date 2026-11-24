<?php
declare(strict_types=1);

/** Conversation references are untrusted IDs. Resolve facts from this request's available menu. */
final class MealChat {
    public const KINDS = ['suggest','menu','price','store','detail','compare','clarify','meal','overview'];

    public static function history(mixed $history): array {
        if (!is_array($history) || !array_is_list($history) || count($history)>4)
            throw new MealAdvisorException(422,'Lịch sử trò chuyện chưa hợp lệ.');
        $valid=[];
        foreach ($history as $turn) {
            $question=$turn['question']??null;
            $ids=$turn['dishIds']??null;
            if (!is_string($question) || mb_strlen($question)>500 || !is_array($ids) || !array_is_list($ids) || count($ids)>MenuKnowledge::PAGE_SIZE)
                throw new MealAdvisorException(422,'Lượt trò chuyện chưa hợp lệ.');
            foreach ($ids as $id)
                if (!is_string($id) || !preg_match('/^[0-9]{1,20}$/D',$id))
                    throw new MealAdvisorException(422,'Mã món trong cuộc trò chuyện chưa hợp lệ.');
            $entry=['question'=>$question,'dishIds'=>$ids];
            if (isset($turn['menuCursor'])) $entry['menuCursor']=MenuKnowledge::pageState($turn['menuCursor']);
            if (isset($turn['answer']) && is_string($turn['answer'])) $entry['answer']=mb_substr($turn['answer'],0,700);
            $valid[]=$entry;
        }
        return $valid;
    }

    /** Understand common factual questions locally, even during a provider outage. */
    public static function lookup(string $message,array $rows,array $history): ?array {
        $expanded=class_exists('FoodHubKnowledgeBase')?FoodHubKnowledgeBase::expandVietnamese($message):$message;
        $text=trim(preg_replace('/[^a-z0-9 ]+/',' ',MealAdvisor::folded($expanded)));
        $text=trim(preg_replace('/\s+/',' ',$text));
        if (preg_match('/^(xin chao|chao ban|hello|hi|cam on|cam on ban)$/',$text) || mb_strtolower(trim($message," !?."))==='chào')
            return ['kind'=>'greeting','rows'=>[]];
        if (preg_match('/^(?:cho (?:minh|toi) |minh muon |toi muon )?(?:xem )?(thuc don|menu|hom nay an gi|hom nay co mon gi|co mon gi|co nhung mon gi|quan co mon gi|mon nao dang ban|mon dang ban)(?: dang ban| hom nay| nao| nhe| a)?$/',$text))
            return ['kind'=>'menu','rows'=>array_slice($rows,0,3)];

        // Offer a real menu category only when the remaining words add no new condition.
        foreach (array_unique(array_column($rows,'category')) as $category) {
            $group=MealAdvisor::folded((string)$category);
            if ($group==='' || !preg_match('/\b'.preg_quote($group,'/').'\b/',$text)) continue;
            $rest=preg_replace('/\b'.preg_quote($group,'/').'\b/',' ',$text);
            $rest=preg_replace('/\b(minh phan van giua|ban chon giup|goi y|bua toi|bua trua|xem|cac|nhom|mon|nao|cho|minh|toi|muon|an|co|gi|hom nay|nhe)\b/',' ',$rest);
            if (trim($rest)==='' && !$history) {
                $matches=array_filter($rows,static fn($row)=>($row['category']??null)===$category);
                return ['kind'=>'menu','rows'=>array_slice(array_values($matches),0,3)];
            }
        }

        // Consume the entire question. A leftover condition must go to Gemini.
        $kind='suggest'; $rest=$text;
        foreach (['price'=>'gia bao nhieu|bao nhieu tien|bao nhieu vay|gia sao|bao nhieu|gia cua|gia',
                  'store'=>'ban o quan nao|cua quan nao|o quan nao|quan nao ban|o dau ban',
                  'detail'=>'mo ta|co gi ben trong|co gi trong|gom nhung gi|co nhung gi|thanh phan|gom gi',
                  'compare'=>'so sanh'] as $action=>$pattern) {
            if (preg_match('/\b('.$pattern.')\b/',$rest)) {
                $rest=preg_replace('/\b('.$pattern.')\b/',' ',$rest);
                $kind=$action; break;
            }
        }
        $last=end($history);
        $prior=$last['dishIds']??[];
        $byId=array_column($rows,null,'id');
        $selected=[];
        $candidates=$rows;
        usort($candidates,static fn($a,$b)=>mb_strlen($b['name'])<=>mb_strlen($a['name']));
        foreach ($candidates as $row) {
            $name=trim(MealAdvisor::folded($row['name']));
            if ($name==='') continue;
            $pattern='/\b'.preg_quote($name,'/').'\b/';
            if (preg_match($pattern,$rest)) {
                $selected[]=$row;
                $rest=preg_replace($pattern,' ',$rest);
            }
        }
        $ambiguous=false;
        foreach (['(?:mon|cai|phan|ly|to) dau tien|(?:mon|cai|phan|ly|to) thu nhat|(?:mon|cai|phan|ly|to) dau|(?:mon|cai|phan|ly|to) thu 1'=>0,
                  '(?:mon|cai|phan|ly|to) thu hai|(?:mon|cai|phan|ly|to) thu 2'=>1,
                  '(?:mon|cai|phan|ly|to) thu ba|(?:mon|cai|phan|ly|to) thu 3'=>2,
                  '(?:mon|cai|phan|ly|to) thu tu|(?:mon|cai|phan|ly|to) thu bon|(?:mon|cai|phan|ly|to) thu 4'=>3,
                  '(?:mon|cai|phan|ly|to) thu nam|(?:mon|cai|phan|ly|to) thu 5'=>4,
                  '(?:mon|cai|phan|ly|to) thu sau|(?:mon|cai|phan|ly|to) thu 6'=>5,
                  '(?:mon|cai|phan|ly|to) thu bay|(?:mon|cai|phan|ly|to) thu 7'=>6,
                  '(?:mon|cai|phan|ly|to) thu tam|(?:mon|cai|phan|ly|to) thu 8'=>7,
                  '(?:mon|cai|phan|ly|to) cuoi cung|(?:mon|cai|phan|ly|to) cuoi'=>count($prior)-1,
                  '(?:mon|cai|phan|ly|to) (?:nay|do|ay|vua nay)'=>count($prior)===1?0:-1] as $reference=>$index) {
            if (!preg_match('/\b('.$reference.')\b/',$rest)) continue;
            $rest=preg_replace('/\b('.$reference.')\b/',' ',$rest);
            if (isset($prior[$index],$byId[$prior[$index]])) $selected[]=$byId[$prior[$index]];
            else $ambiguous=true;
        }
        $left=preg_replace('/\b(cho minh|cho toi|goi y|toi muon|minh muon|muon an|toi|minh|mon|an|voi|va|co|khong a|khong|la|nhe|nha|vay|the|con|ua|shop|oi|em|a)\b/',' ',$rest);
        // Never turn a negation ("không ăn ...") into a positive match.
        $assertion=preg_replace('/\s+khong(?: a)?$/','',$text);
        if (trim($left)!=='' || preg_match('/\b(khong|tranh|kieng|tru)\b/',$assertion)) return null;
        if ($ambiguous) return ['kind'=>'clarify','rows'=>[]];
        if (!$selected) return null;
        return ['kind'=>$kind,'rows'=>array_slice($selected,0,3)];
    }

    public static function answer(string $kind,array $items): string {
        if ($kind==='greeting') return 'Chào mình nha! Mình muốn xem thực đơn, tìm món theo gu & ngân sách hay hỏi về món cụ thể nào ạ?';
        if ($kind==='end') return 'Mình đã xem hết các món trong phần thực đơn này rồi ạ. Mình có muốn tìm theo món cụ thể hay đổi ngân sách không nha?';
        if ($kind==='clarify' || !$items) return 'Mình muốn hỏi về món nào ạ? Mình có thể nhắn tên món hoặc nói “món đầu tiên” sau khi xem gợi ý nha.';
        $facts=[];
        foreach ($items as $item) {
            $dish=$item['dish'];
            $price=number_format($dish['price'],0,',','.').'đ';
            $facts[]=match ($kind) {
                'detail'=>trim($dish['description'])!=='' ? $dish['name'].' ('.$price.', '.$item['restaurantName'].') — quán mô tả: '.mb_substr(trim($dish['description']),0,200) : 'Quán chưa bổ sung mô tả cho '.$dish['name'].'.',
                'store'=>$dish['name'].' hiện đang bán tại '.$item['restaurantName'].' với giá '.$price.'.',
                default=>$dish['name'].' ('.$price.')'
            };
        }
        if (in_array($kind,['detail','store'],true)) return implode("\n",$facts);
        $prefix=match ($kind) {
            'price'=>'Dạ giá hiện tại trên thực đơn là ',
            'menu'=>'Dạ thực đơn đang bán có ',
            'evidence'=>'Dạ em tìm thấy mấy món khớp yêu cầu của mình nè: ',
            'compare'=>'Để mình dễ so sánh giá nhé: ',
            default=>'Dạ em gợi ý mình thử '
        };
        return $prefix.implode(', ',$facts).'.';
    }

    /** Resolve model references only after the final price/availability check. */
    public static function renderReply(string $reply,array $items,array $summary=[],bool $clarify=false): ?string {
        if ($reply==='' || mb_strlen($reply)>700) return null;
        $tokens=$summary?['{somon}'=>(string)$summary['dishes'],'{soquan}'=>(string)$summary['restaurants']]:[];
        foreach ($summary['stores']??[] as $store) {
            $id=$store['id'];
            $tokens['{tenquan:'.$id.'}']=$store['name'];
            $tokens['{demquan:'.$id.'}']=(string)$store['dishes'];
            $tokens['{mingia:'.$id.'}']=MenuKnowledge::money((float)$store['min_price']);
            $tokens['{maxgia:'.$id.'}']=MenuKnowledge::money((float)$store['max_price']);
        }
        foreach ($items as $item) {
            $dish=$item['dish']; $id=$dish['id'];
            $tokens['{mon:'.$id.'}']=$dish['name'];
            $tokens['{gia:'.$id.'}']=number_format($dish['price'],0,',','.').'đ';
            $tokens['{quan:'.$id.'}']=$item['restaurantName'];
            if (!isset($tokens['{tenquan:'.$id.'}'])) $tokens['{tenquan:'.$id.'}']=$item['restaurantName'];
        }
        $literal=str_replace(array_keys($tokens),'',$reply,$references);
        if (preg_match('/[{}<>]|https?:|www\./ui',$literal)) return null;
        $foldedLiteral=MealAdvisor::folded($literal);
        // Never allow model-invented currency/price amounts outside {gia:ID}/{mingia:ID}/{maxgia:ID}
        if (preg_match('/\d+(?:[.,]\d+)*\s*(?:d\b|dong|vnd|k\b|nghin|ngan|trieu|tr\b)/ui',$foldedLiteral)) return null;
        // Allow small portion/item counts (e.g., "1 phan", "2 mieng", "1 nuoc ngot", "1 nguoi") from dish descriptions
        $withoutCounts=preg_replace('/\b[1-9]\s*(?:phan|mieng|mon|nguoi|ly|coc|to|bat|dia|goi|nuoc|tuong|khoai|chai|hop|cai|suat|com|mi|ga)\b/ui','',$foldedLiteral);
        if (preg_match('/\d/',$withoutCounts)) return null;
        if (!$references && !($clarify && str_ends_with($reply,'?'))) return null;
        return strtr($reply,$tokens);
    }
}
