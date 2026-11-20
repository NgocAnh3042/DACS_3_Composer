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
        $text=trim(preg_replace('/[^a-z0-9 ]+/',' ',MealAdvisor::folded($message)));
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
        foreach (['price'=>'gia bao nhieu|bao nhieu tien|bao nhieu|gia cua|gia',
                  'store'=>'ban o quan nao|cua quan nao|o quan nao|quan nao ban',
                  'detail'=>'mo ta|co gi trong|gom nhung gi|co nhung gi|thanh phan',
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
        foreach (['mon dau tien|mon thu nhat|mon dau|mon thu 1'=>0,'mon thu hai|mon thu 2'=>1,'mon thu ba|mon thu 3'=>2,
                  'mon thu tu|mon thu bon|mon thu 4'=>3,'mon thu nam|mon thu 5'=>4,'mon thu sau|mon thu 6'=>5,
                  'mon thu bay|mon thu 7'=>6,'mon thu tam|mon thu 8'=>7,'mon cuoi cung|mon cuoi'=>count($prior)-1,
                  'mon nay|mon do|mon ay'=>count($prior)===1?0:-1] as $reference=>$index) {
            if (!preg_match('/\b('.$reference.')\b/',$rest)) continue;
            $rest=preg_replace('/\b('.$reference.')\b/',' ',$rest);
            if (isset($prior[$index],$byId[$prior[$index]])) $selected[]=$byId[$prior[$index]];
            else $ambiguous=true;
        }
        $left=preg_replace('/\b(cho minh|cho toi|goi y|toi muon|minh muon|muon an|toi|minh|mon|an|voi|va|co|khong a|khong|la|nhe|a)\b/',' ',$rest);
        // Never turn a negation ("không ăn ...") into a positive match.
        $assertion=preg_replace('/\s+khong(?: a)?$/','',$text);
        if (trim($left)!=='' || preg_match('/\b(khong|tranh|kieng|tru)\b/',$assertion)) return null;
        if ($ambiguous) return ['kind'=>'clarify','rows'=>[]];
        if (!$selected) return null;
        return ['kind'=>$kind,'rows'=>array_slice($selected,0,3)];
    }

    public static function answer(string $kind,array $items): string {
        if ($kind==='greeting') return 'Chào bạn! Bạn muốn xem thực đơn, tìm món theo giá hay hỏi về một món cụ thể?';
        if ($kind==='end') return 'Bạn đã xem hết các món trong phần thực đơn này.';
        if ($kind==='clarify' || !$items) return 'Bạn muốn hỏi về món nào? Bạn có thể nhắn tên món hoặc nói “món đầu tiên” sau khi xem gợi ý.';
        $facts=[];
        foreach ($items as $item) {
            $dish=$item['dish'];
            $price=number_format($dish['price'],0,',','.').'đ';
            $facts[]=match ($kind) {
                'detail'=>trim($dish['description'])!=='' ? $dish['name'].' — quán mô tả: '.mb_substr(trim($dish['description']),0,200) : 'Quán chưa bổ sung mô tả cho '.$dish['name'].'.',
                'store'=>$dish['name'].' đang bán tại '.$item['restaurantName'].'.',
                default=>$dish['name'].' ('.$price.')'
            };
        }
        if (in_array($kind,['detail','store'],true)) return implode("\n",$facts);
        $prefix=match ($kind) {
            'price'=>'Giá trên thực đơn là ',
            'menu'=>'Trong thực đơn đang bán có ',
            'evidence'=>'Mình tìm thấy những món sau theo thông tin quán đã đăng: ',
            'compare'=>'Để bạn so sánh giá món: ',
            default=>'Mình gợi ý bạn thử '
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
        }
        $literal=str_replace(array_keys($tokens),'',$reply,$references);
        if (preg_match('/[{}\d<>]|https?:|www\./ui',$literal)) return null;
        if (!$references && !($clarify && str_ends_with($reply,'?'))) return null;
        return strtr($reply,$tokens);
    }
}
