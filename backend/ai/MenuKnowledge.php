<?php
declare(strict_types=1);

/** Read-only queries share the orderability rules used by the meal advisor. */
final class MenuKnowledge {
    public const PAGE_SIZE=8;
    private const FIELDS='SELECT d.id,d.store_id,d.name,d.description,d.price,d.image_url,s.name AS restaurant_name,mc.name AS category ';
    private const AVAILABLE='FROM dishes d JOIN stores s ON s.id=d.store_id JOIN merchants m ON m.id=s.merchant_id
        JOIN users owner ON owner.id=m.owner_user_id LEFT JOIN menu_categories mc ON mc.id=d.menu_category_id
        WHERE d.deleted_at IS NULL AND d.is_available=1 AND (d.stock_quantity IS NULL OR d.stock_quantity>0)
        AND s.deleted_at IS NULL AND s.status=\'active\' AND s.is_open=1 AND m.status=\'active\'
        AND owner.status=\'active\' AND owner.deleted_at IS NULL AND d.price<=?';

    public static function stores(PDO $db,float $ceiling): array {
        $q=$db->prepare('SELECT s.id,s.name,SUM(CASE WHEN d.price<=? THEN 1 ELSE 0 END) AS dishes,
            MIN(d.price) AS min_price,MAX(CASE WHEN d.price<=? THEN d.price END) AS max_price '.self::AVAILABLE.' GROUP BY s.id,s.name ORDER BY s.name,s.id');
        $q->execute([$ceiling,$ceiling,10000000]);
        return $q->fetchAll(PDO::FETCH_ASSOC);
    }

    public static function text(string $message): string {
        return trim(preg_replace('/[^a-z0-9]+/',' ',MealAdvisor::folded($message)));
    }

    public static function mentionedStores(string $message,array $stores): array {
        $text=' '.self::text($message).' ';
        return array_values(array_filter($stores,static fn($s)=>str_contains($text,' '.self::text($s['name']).' ')));
    }

    /** Match a browse request only if the other words add no unresolved condition. */
    public static function browse(string $message,array $stores): ?string {
        $base=preg_replace('/\b(?:duoi|toi da|ngan sach)\s+\d+(?:[.,]\d+)*\s*(?:k|nghin|ngan|d|dong|vnd|trieu|tr)\b/u',' ',MealAdvisor::folded($message));
        $text=self::text($base);
        foreach (self::mentionedStores($message,$stores) as $store)
            $text=trim(str_replace(' '.self::text($store['name']).' ',' ',' '.$text.' '));
        $text=trim(preg_replace('/\b(cho (toi|minh)|toi muon|minh muon|vui long|tat ca|toan bo|day du|cua|nha hang|quan|tai|nhe|voi|duoc khong)\b/',' ',$text));
        $text=preg_replace('/\s+(?:nua di|nua nhe|di)$/','',$text);
        $text=trim(preg_replace('/\s+/',' ',$text));
        if (preg_match('/^(?:xem|liet ke|hien thi) (?:cac |nhung )?mon(?: an)?(?: dang ban)?$/',$text)) return 'first';
        if (preg_match('/^(?:xem |cho xem )?(?:(?:them|tiep) (?:cac )?mon(?: an)?(?: khac| con lai)|(?:cac )?mon con lai)(?: trong (?:menu|thuc don))?$/',$text)) return 'next';
        if (preg_match('/^(?:(?:hien tai|hom nay) )?(?:dang ban |ban |co )(?:nhung |cac )?mon (?:gi|nao)(?: dang ban| hom nay)?$/',$text)) return 'first';
        if (preg_match('/^(?:(?:minh |toi )?(?:muon )?)?(?:(?:xem|hien thi|liet ke|mo|cho xem) )?(?:menu|thuc don|danh sach mon(?: an)?)(?: dang ban| hom nay)?$/',$text)) return 'first';
        if (preg_match('/^(?:(?:minh |toi )?(?:muon )?)?(?:(?:xem|cho xem|hien thi|liet ke) )?(?:(?:them|tiep|tiep tuc)(?: (?:menu|thuc don|danh sach mon|cac mon|mon an|mon))?|(?:menu|thuc don) (?:tiep|tiep theo)|trang (?:ke|sau|tiep)(?: tiep| theo)?|con (?:nhung |cac )?mon (?:nao|gi)(?: khac| nua)?(?: khong)?)$/',$text)) return 'next';
        return null;
    }

    public static function pageState(mixed $page): ?array {
        if ($page===null) return null;
        if (!is_array($page) || !is_string($page['afterId']??null) || !preg_match('/^\d{1,20}$/D',$page['afterId'])
            || (isset($page['storeId']) && (!is_string($page['storeId']) || !preg_match('/^\d{1,20}$/D',$page['storeId'])))
            || !is_numeric($page['ceiling']??null) || $page['ceiling']<0 || $page['ceiling']>10000000)
            throw new MealAdvisorException(422,'Vị trí trong thực đơn chưa hợp lệ.');
        return ['afterId'=>$page['afterId'],'storeId'=>$page['storeId']??null,'ceiling'=>(float)$page['ceiling']];
    }

    public static function page(PDO $db,string $mode,array $input,array $stores,float $ceiling,float $reserve=0): array {
        $mentions=self::mentionedStores($input['message'],$stores);
        $storeId=isset($mentions[0])?(string)$mentions[0]['id']:null;
        $after='0';
        if ($mode==='next') {
            $history=array_reverse($input['history']);
            $last=$history[0]['menuCursor']??null;
            if ($last===null) {
                // Older Android clients send only questions and displayed IDs, including intervening turns.
                foreach ($history as $turn) {
                    if (self::browse($turn['question'],$stores)===null) continue;
                    $ids=$turn['dishIds'];
                    if ($after==='0' && $ids) $after=end($ids);
                    $prior=self::mentionedStores($turn['question'],$stores);
                    if ($storeId===null && $prior) $storeId=(string)$prior[0]['id'];
                    if (self::browse($turn['question'],$stores)==='first') {
                        $priorInput=array_replace($input,['message'=>$turn['question'],'budget'=>$input['budget']??10000000]);
                        $ceiling=min($ceiling,MealAdvisor::localIntent($priorInput)['budget']-$reserve);
                        break;
                    }
                }
            } elseif ($storeId===null || $storeId===$last['storeId']) {
                $after=$last['afterId']; $storeId=$last['storeId']; $ceiling=min($ceiling,$last['ceiling']);
            }
        }
        $params=[$ceiling,$after];
        $sql=self::FIELDS.self::AVAILABLE.' AND d.id>?';
        if ($storeId!==null) { $sql.=' AND d.store_id=?'; $params[]=$storeId; }
        $q=$db->prepare($sql.' ORDER BY d.id ASC LIMIT '.(self::PAGE_SIZE+1));
        $q->execute($params);
        $rows=$q->fetchAll(PDO::FETCH_ASSOC);
        $hasMore=count($rows)>self::PAGE_SIZE;
        $rows=array_slice($rows,0,self::PAGE_SIZE);
        $cursor=['afterId'=>$rows?(string)end($rows)['id']:$after,'storeId'=>$storeId,'ceiling'=>max(0,$ceiling)];
        return ['rows'=>$rows,'hasMore'=>$hasMore,'cursor'=>$cursor];
    }

    public static function keywords(string $message): array {
        $stops=explode(' ','toi minh cho gi mon an cua mot nao cac ban co gia duoi muon thu nhung voi xin hay the la ve nay duoc va nhat hom neu se ngan sach bua trua rang quan nha hang menu thuc don bao nhieu tien phan khau goi y de xuat nguoi trong tu den khong day vay dang tim thich hoi muon biet nhin thay mot chut khong a o tai nhe giup ket cau thi ay foodhub cua hang ngon phu hop mang vi');
        $words=preg_split('/[^\p{L}\p{N}]+/u',mb_strtolower($message),-1,PREG_SPLIT_NO_EMPTY);
        return array_slice(array_values(array_unique(array_filter($words,static fn($w)=>mb_strlen($w)>=2 && !in_array(MealAdvisor::folded($w),$stops,true)))),0,14);
    }

    /** Rank across all eligible rows in SQL before bounding the Gemini context. */
    public static function candidates(PDO $db,float $ceiling,bool $cheap=false,string $message='',array $refs=[],?string $storeId=null): array {
        $sql=self::FIELDS.self::AVAILABLE;
        $params=[$ceiling];
        if ($storeId!==null) { $sql.=' AND d.store_id=?'; $params[]=$storeId; }
        $order=[];
        if ($refs) {
            $order[]='CASE WHEN d.id IN ('.implode(',',array_fill(0,count($refs),'?')).') THEN 1 ELSE 0 END DESC';
            array_push($params,...$refs);
        }
        $scores=[];
        foreach (self::keywords($message) as $word) {
            $scores[]='(CASE WHEN LOWER(CONCAT_WS(\' \',d.name,mc.name,s.name)) LIKE ? THEN 3 WHEN LOWER(d.description) LIKE ? THEN 1 ELSE 0 END)';
            $params[]='%'.$word.'%'; $params[]='%'.$word.'%';
        }
        if ($scores) $order[]='('.implode('+',$scores).') DESC';
        $order[]=$cheap?'d.price ASC':'d.rating_avg DESC';
        $q=$db->prepare($sql.' ORDER BY '.implode(',',$order).',d.id DESC LIMIT 81');
        $q->execute($params);
        return $q->fetchAll(PDO::FETCH_ASSOC);
    }

    public static function wantsMeal(string $message): bool {
        return (bool)preg_match('/\b(khau phan|suat (?:an|com|trua)|phan an|bua (?:an )?trua|an trua|set an|combo cho (?:mot|1) nguoi)\b/',self::text($message));
    }

    /** Resolve a budget-only follow-up to the most recent lunch request, preserving its preferences. */
    public static function mealQuestion(array $input): string {
        $question=$input['message'];
        if (self::wantsMeal($question) || !$input['history']) return $question;
        $money='/\b\d+(?:[.,]\d+)*\s*(?:k|nghin|ngan|trieu|tr|d|dong|vnd)\b/u';
        $text=MealAdvisor::folded($question);
        if (!preg_match($money,$text)) return $question;
        $left=preg_replace($money,' ',$text);
        $left=preg_replace('/\b(vay|neu|con|chi|co|toi|minh|duoi|toi da|ngan sach|thi sao|thi|duoc khong|nhe|tam|khoang|tang|giam|xuong|len)\b/',' ',$left);
        if (self::text($left)!=='') return $question;
        foreach (array_reverse($input['history']) as $turn) {
            if (self::wantsMeal($turn['question']))
                return trim(preg_replace($money,' ',MealAdvisor::folded($turn['question']))).' '.$text;
            if (!preg_match($money,MealAdvisor::folded($turn['question']))) break;
        }
        return $question;
    }

    public static function mealIntent(array $input,array $stores): array {
        $text=MealAdvisor::folded($input['message']);
        foreach (self::mentionedStores($text,$stores) as $store)
            $text=str_replace(MealAdvisor::folded($store['name']),' ',$text);
        $text=preg_replace('/\b(de xuat|dua ra|tu van|chuan bi|sap xep|len|giup|hay|toi can|khau phan|suat an|phan an|set an|bua an trua|bua trua|an trua|cho toi|cho minh|tai|nha hang|quan|gom phi giao|tinh ca phi giao|tinh ca ship|bao gom phi giao hang|1 nguoi|1|mot)\b/',' ',$text);
        $text=preg_replace('/\b(thi sao|vay|neu|chi|con|tang|giam|xuong|len|combo)\b/',' ',$text);
        return MealAdvisor::localIntent(array_merge($input,['message'=>trim($text)]));
    }

    /** Compose one order at one restaurant. Only a main dish is required. */
    public static function meal(array $rows,float $ceiling,array $intent): array {
        if (!$intent['canFilter'] || $intent['unsupported']) return [];
        $rows=MealAdvisor::matchingRows($rows,$intent);
        $mains=[]; $sides=[];
        foreach ($rows as $row) {
            if ((float)$row['price']>$ceiling) continue;
            $category=self::text((string)$row['category']);
            $name=self::text($row['name']);
            if (preg_match('/\b(khai vi|trang mieng|mon phu|do uong|nuoc uong|thuc uong)\b/',$category))
                $sides[$row['store_id']][]=$row;
            elseif (preg_match('/\b(mon chinh|com|pho|bun|mi|pizza|burger)\b/',$category.' '.$name))
                $mains[]=$row;
        }
        if (!$mains) return [];
        if ($intent['cheap']) usort($mains,static fn($a,$b)=>$a['price']<=>$b['price']);
        foreach ($mains as $main) {
            if ($intent['cheap']) break;
            foreach ($sides[$main['store_id']]??[] as $side)
                if ($main['price']+$side['price']<=$ceiling) return [$main,$side];
        }
        return [$mains[0]];
    }

    public static function mealAnswer(array $items,float $fee,bool $includeDelivery): string {
        $names=[];$total=0;
        foreach ($items as $item) {
            $dish=$item['dish'];
            $names[]='1 phần '.$dish['name'].' ('.self::money($dish['price']).')';
            $total+=$dish['price'];
        }
        $reply='Cho một người, mình gợi ý '.implode(' và ',$names).' tại '.$items[0]['restaurantName'].'. Tổng tiền món '.self::money($total).'.';
        if ($includeDelivery) $reply.=' Tính một lần phí giao '.self::money($fee).', dự tính '.self::money($total+$fee).'.';
        return $reply;
    }

    /** Look up evidence in public menu fields when the provider is unavailable. */
    public static function evidence(string $question,array $rows,array $refs): ?array {
        $text=self::text($question);
        if (preg_match('/\b(mon nay|mon tren|trong (?:so|cac|nhung) mon|vua (?:xem|goi y))\b/',$text)
            && preg_match('/\b(re (?:hon|nhat)|gia thap)\b/',$text)) {
            $previous=array_filter($rows,static fn($r)=>in_array((string)$r['id'],$refs,true));
            if (count($previous)>=2 && !preg_match('/\b(khong|tranh|kieng|nhung phai)\b/',$text)) {
                usort($previous,static fn($a,$b)=>$a['price']<=>$b['price']);
                return ['kind'=>'price','rows'=>[$previous[0]]];
            }
        }
        $statement=preg_replace('/\s+khong(?: a)?$/','',$text);
        if (preg_match('/\b(khong|tranh|kieng|tru|it|nhieu hon|dat nhat|di ung|chay|an toan|it nhat)\b/',$statement)) return null;
        $words=self::keywords(preg_replace('/\b\d+(?:[.,]\d+)*\s*(?:k|nghìn|ngàn|đ|vnd)\b/ui',' ',$question));
        if (!$words) return null;
        $matches=[];
        foreach ($rows as $row) {
            $raw=mb_strtolower(implode(' ',[$row['name'],$row['category']??'',$row['restaurant_name'],$row['description']??'']));
            $folded=' '.self::text($raw).' ';
            foreach ($words as $word) {
                if (!str_contains($folded,' '.self::text($word).' ')) continue 2;
                if (in_array($word,['cá','bò','cà','bơ'],true) && !preg_match('/(?<!\p{L})'.preg_quote($word,'/').'(?!\p{L})/u',$raw)) continue 2;
            }
            $matches[]=$row;
        }
        if (!$matches) return null;
        $kind=preg_match('/\b(quan|nha hang) nao\b/',$text)?'store':'evidence';
        return ['kind'=>$kind,'rows'=>array_slice($matches,0,3)];
    }

    public static function money(float $value): string { return number_format($value,0,',','.').'đ'; }

    /** Store-level answers are calculated from SQL aggregates over all eligible dishes. */
    public static function storeComparison(string $question,array $stores): ?string {
        $question=trim(preg_replace('/^(?:cho (?:toi|minh) (?:biet )?|ban co the |giup minh )/','',$question));
        if (preg_match('/^(?:nha hang|quan) nao (?:dang |hien )?(?:co |ban )?nhieu mon(?: an)? nhat$/',$question)) {
            if (!$stores) return 'Trong ngân sách này chưa có quán nào có món đang bán.';
            $top=max(array_column($stores,'dishes'));
            $names=array_column(array_filter($stores,static fn($s)=>$s['dishes']==$top),'name');
            return implode(', ',$names).' có nhiều món nhất, với '.$top.' món đang bán'.(count($names)>1?' mỗi quán.':'.');
        }
        if (preg_match('/^(?:(?:so sanh|xem) (?:muc |khoang )?gia (?:giua |cua )?(?:cac |nhung )?(?:quan|nha hang)|(?:nha hang |quan )?(?:co )?(?:khoang gia|muc gia)(?: the nao| bao nhieu| ra sao)?)$/',$question)) {
            if (!$stores) return 'Trong ngân sách này chưa có món để so sánh.';
            return implode("\n",array_map(static fn($s)=>$s['name'].' · '.self::money((float)$s['min_price']).' đến '.self::money((float)$s['max_price']).' ('.$s['dishes'].' món).',array_slice($stores,0,12)));
        }
        return null;
    }
}
