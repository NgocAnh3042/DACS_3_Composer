<?php
declare(strict_types=1);

/** Account facts and mutations stay on FoodHub; the language model never supplies an authorization scope. */
final class FoodHubSupport {
    private const STATUS=['pending_payment'=>'đang chờ thanh toán','pending'=>'đang chờ quán xác nhận','accepted'=>'đã được quán nhận','processing'=>'đang được quán chuẩn bị','shipping'=>'đang được giao','completed'=>'đã hoàn tất','cancelled'=>'đã hủy'];
    private const FROM=' FROM orders o JOIN stores s ON s.id=o.store_id JOIN merchants m ON m.id=s.merchant_id LEFT JOIN deliveries d ON d.order_id=o.id LEFT JOIN drivers dr ON dr.id=d.driver_id LEFT JOIN users ship ON ship.id=dr.user_id ';
    private static function query(PDO $db,string $sql,array $args=[]): PDOStatement {
        $q=$db->prepare($sql);$q->execute($args);return $q;
    }
    public static function scope(array $me): array {
        return match($me['role']) {
            'customer'=>['o.customer_id=?',[$me['id']]],'seller'=>['m.owner_user_id=?',[$me['id']]],
            'driver'=>['dr.user_id=?',[$me['id']]],'admin'=>['1=1',[]],
            default=>throw new MealAdvisorException(403,'Tài khoản chưa có quyền hỗ trợ.')
        };
    }
    private static function order(PDO $db,array $me,?string $id): ?array {
        [$where,$params]=self::scope($me);
        if ($id!==null) {$where.=' AND o.id=?';$params[]=$id;}
        return self::query($db,'SELECT o.id,o.status,o.total_amount,o.payment_method,o.payment_status,s.name,ship.name AS shipper'.self::FROM.' WHERE '.$where.' ORDER BY o.placed_at DESC,o.id DESC LIMIT 1',$params)->fetch(PDO::FETCH_ASSOC)?:null;
    }
    private static function normalize(string $text): string {
        return preg_replace(['/\bdh\b/', '/\b(?:ko|khum|hong|k)\b/', '/\bshp\b/'], ['don hang','khong','shop'], MealAdvisor::folded($text));
    }
    public static function intent(string $text): ?string {
        $q=self::normalize($text);
        foreach ([
            'feedback'=>'^(?:danh gia|gop y cau tra loi|phan hoi tro ly)',
            'submit'=>'^gui (?:phieu|yeu cau) ho tro(?: #?\d+)?[.! ]*$',
            'confirmCancel'=>'^xac nhan huy (?:don )?#?\d+[.! ]*$',
            'cancel'=>'huy (don|giup|dum|ho minh)|bo don',
            'report'=>'doanh thu|bao cao|thong ke|phan loai khieu nai|phan hoi chua giai quyet',
            'incident'=>'(giao|nhan|bi) (sai|nham|thieu)|thieu mon|bi do|do het|do an hong|khieu nai|tranh chap|chiem quyen|nhan vien (that|ho tro)|gap admin|bao su co|tru tien.*(loi|hai lan|2 lan)|khong hai long',
            'refund'=>'hoan tien|hoan tra',
            'voucher'=>'voucher|ma (giam|khuyen)|khuyen mai',
            'history'=>'lich su (mua|dat|don)|nhung mon.*(tung|da) (mua|dat)|hay dat mon gi',
            'pay'=>'thanh toan|vi dien tu|tien mat|payos|chuyen khoan',
            'order'=>'(?<!thuc )don (tui|toi|minh|hang|cua|moi|gan|nay|do|dau|#?\d+)|(?<!thuc )don.*(dat|giao)|shipper|ship toi dau|chua (thay )?giao|doi.*(tieng|phut)|giao cham|khi nao.*(giao|toi)|tai xe',
            'fee'=>'^(?:cho (?:minh|toi) hoi )?(?:phi (?:ship|giao hang|van chuyen)|tien ship)(?: la| bao nhieu| the nao| vay| a| nhi)*[?!. ]*$',
            'allergy'=>'di ung|phan ve',
            'role'=>'quan ly mon|cap nhat don|nhan don|huong dan giao|tro giup|ban giup duoc gi|^(?:xin chao|chao shop|chao ban|hello|hi)[!. ]*$',
            'thanks'=>'^(cam on|cam on shop|cam on ban|thanks|ok cam on)[!. ]*$'
        ] as $intent=>$pattern) if (preg_match('~'.$pattern.'~u',$q)) return $intent;
        return null;
    }
    /** Reuse open support inbox and its image messages. Serialize identical submissions per account. */
    private static function ticket(PDO $db,array $me,?array $order,string $summary): string {
        $db->beginTransaction();
        try {
            self::query($db,'SELECT id FROM users WHERE id=?'.($db->getAttribute(PDO::ATTR_DRIVER_NAME)==='sqlite'?'':' FOR UPDATE'),[$me['id']]);
            $id=self::query($db,"SELECT c.id FROM conversations c JOIN messages msg ON msg.conversation_id=c.id WHERE c.type='support' AND c.status='open' AND c.created_by=? AND (c.order_id=? OR (c.order_id IS NULL AND ? IS NULL)) AND msg.content=? ORDER BY c.id DESC LIMIT 1",[$me['id'],$order['id']??null,$order['id']??null,$summary])->fetchColumn();
            if (!$id) {
                self::query($db,"INSERT INTO conversations(order_id,type,created_by) VALUES(?,'support',?)",[$order['id']??null,$me['id']]);
                $id=$db->lastInsertId();
                self::query($db,"INSERT INTO conversation_participants(conversation_id,user_id) SELECT ?,id FROM users WHERE id=? OR (role='admin' AND status='active' AND deleted_at IS NULL)",[$id,$me['id']]);
                self::query($db,"INSERT INTO messages(conversation_id,sender_user_id,message_type,content) VALUES(?,?,'text',?)",[$id,$me['id'],$summary]);
            }
            $db->commit();return (string)$id;
        } catch(Throwable $err) {if($db->inTransaction())$db->rollBack();throw $err;}
    }
    public static function reply(PDO $db,array $me,array $input,float $fee,?callable $classify=null): ?string {
        [$scope,$args]=self::scope($me);
        $text=$input['message'];$q=self::normalize($text);$kind=self::intent($text);
        $history=$input['history'];$prev=end($history)?:[];
        $prior=(string)($prev['answer']??'');
        if (!$kind && str_contains($prior,'Gửi phiếu hỗ trợ') && preg_match('/^(da |minh |toi |tui |bi |anh |co |chua |thu |van |thieu |mon )/',$q)) $kind='incident';
        if (!$kind && preg_match('/^(vay |gio |the )?(sao roi|toi dau roi|bao lau nua|ai giao)[?.! ]*$/',$q) && str_contains($prior,'Đơn #')) $kind='order';
        if (!$kind && $prev && (self::intent($prev['question']??'')==='allergy' || str_contains($prior,'nguy cơ tiếp xúc chéo')) && !preg_match('/doi chu de|xem menu|thuc don|khau phan/',$q)) $kind='allergy';
        if (!$kind && $classify && ($me['role']!=='customer' || !$prev || !($prev['dishIds']??[]) || preg_match('/\b(tien|ship|giao|don|doi|loi|buc|hu|hong|su co|tai khoan)\b/',$q)) && !preg_match('/\b(mon|menu|thuc don|an|mi|com|bun|pho|quan|cay|re nhat|goi y)\b/',$q)) {
            $kind=$classify($text,(string)($prev['question']??''));
        }
        if (!$kind || $kind==='meal') return $me['role']==='customer'?null:self::guide($me['role']);
        if ($kind==='clarify') return 'Bạn cho mình biết thêm một chút nhé. Bạn đang hỏi về đơn vừa đặt, tìm món hay gặp sự cố khi dùng FoodHub?';
        $id=null;
        if (preg_match('/(?:#|\bdon\s+)(\d+)\b/',$q,$m)) $id=$m[1];
        elseif (!preg_match('/moi nhat|gan nhat|hoi nay/',$q)) {
            foreach(array_reverse($history) as $turn) if(preg_match('/Đơn #(\d+)\b/u',$turn['answer']??'',$m)) {$id=$m[1];break;}
        }
        if ($kind==='thanks') return 'Dạ, cảm ơn bạn! Khi cần xem đơn hoặc tìm món, cứ nhắn mình nhé.';
        if ($kind==='role') return self::guide($me['role']);
        if ($kind==='fee') return 'Phí giao hệ thống hiện là '.MenuKnowledge::money($fee).'. Màn hình xác nhận đơn sẽ hiển thị tổng sau khi áp dụng voucher.';
        if ($kind==='allergy') {
            $refs=$prev['dishIds']??[];
            $facts=[];
            foreach (MenuKnowledge::candidates($db,(float)($input['budget']??10000000),false,$text,$refs) as $dish) {
                $name=self::normalize($dish['name']);
                if (!str_contains($q,$name) && !(count($refs)===1 && (string)$dish['id']===(string)$refs[0])) continue;
                $facts[]=$dish['name'].' ('.$dish['restaurant_name'].'): '.(trim((string)$dish['description'])?:'quán chưa ghi thành phần').'.';
                if (count($facts)===2) break;
            }
            return ($facts?'Theo mô tả của quán: '.implode(' ',$facts):'Bạn gửi tên món và nguyên liệu cần tránh để mình đối chiếu mô tả của quán nhé.').' Quán có thể chưa ghi đủ thành phần hoặc nguy cơ tiếp xúc chéo. Hãy xác nhận với quán trước khi đặt.';
        }
        if ($kind==='voucher') {
            $v=self::query($db,"SELECT code,min_order_amount FROM vouchers WHERE is_active=1 AND store_id IS NULL AND (starts_at IS NULL OR starts_at<=NOW()) AND (ends_at IS NULL OR ends_at>=NOW()) AND (usage_limit IS NULL OR used_count<usage_limit) ORDER BY ends_at LIMIT 4")->fetchAll();
            return 'Bạn chọn voucher ở bước xác nhận đơn. '.($v?'Mã áp dụng chung hiện có: '.implode(', ',array_map(static fn($r)=>$r['code'].' (đơn từ '.MenuKnowledge::money((float)$r['min_order_amount']).')',$v)).'.':'Hiện chưa thấy mã dùng chung còn lượt. Mã riêng của quán phụ thuộc giỏ hàng.');
        }
        if ($kind==='feedback') {
            $summary="[Phản hồi AI · chờ nhân viên duyệt]\n".$text."\nCâu hỏi trước: ".($prev['question']??'')."\nTrả lời trước: ".$prior;
            $ticket=self::ticket($db,$me,null,$summary);
            return "Đã ghi nhận góp ý #$ticket để nhân viên xem lại câu trả lời.";
        }
        if ($kind==='history') {
            $rows=self::query($db,'SELECT o.id,s.name,i.dish_name,i.quantity'.self::FROM.' JOIN order_items i ON i.order_id=o.id WHERE '.$scope." AND o.status='completed' ORDER BY o.placed_at DESC,o.id DESC,i.id LIMIT 8",$args)->fetchAll();
            return $rows?'Những món trong các đơn đã hoàn tất gần đây: '.implode('; ',array_map(static fn($r)=>$r['dish_name'].' ×'.$r['quantity'].' tại '.$r['name'].' (#'.$r['id'].')',$rows)).'.':'Mình chưa thấy đơn đã hoàn tất trong lịch sử này.';
        }
        if ($kind==='report') {
            if ($me['role']==='customer') return 'Bạn có thể xem lịch sử mua của mình. Báo cáo vận hành dành cho tài khoản quản lý.';
            if ($me['role']==='admin' && preg_match('/khieu nai|phan hoi|phieu/',$q)) {
                $rows=self::query($db,"SELECT msg.content FROM conversations c JOIN (SELECT conversation_id,MIN(id) AS first_id FROM messages GROUP BY conversation_id) initial_msg ON initial_msg.conversation_id=c.id JOIN messages msg ON msg.id=initial_msg.first_id WHERE c.type='support' AND c.status='open' AND (msg.content LIKE '[FoodHub · %' OR msg.content LIKE '[Phản hồi AI · %') ORDER BY c.id DESC LIMIT 100")->fetchAll(PDO::FETCH_COLUMN);
                $counts=[];
                foreach ($rows as $content) {
                    $category=strtok($content,"\n");
                    if (str_contains($category,'Phản hồi AI')) $category=str_contains(self::normalize(strtok("\n")?:''),'chua giai quyet')?'Phản hồi chưa giải quyết':'Góp ý chờ duyệt';
                    $category=trim(str_replace('FoodHub · ','',$category),'[]');
                    $counts[$category]=($counts[$category]??0)+1;
                }
                if (!$counts) return 'Hiện chưa có phiếu mở do trợ lý chuyển.';
                arsort($counts);
                $items=[];foreach ($counts as $category=>$count) $items[]=$category.' ('.$count.')';
                return 'Trong tối đa 100 phiếu mở gần nhất từ trợ lý: '.implode(', ',$items).'. Mở Tin nhắn để đối chiếu câu hỏi với kết quả xử lý trước khi cập nhật hướng dẫn cho trợ lý.';
            }
            $r=self::query($db,'SELECT COUNT(*) AS n,COALESCE(SUM('.($me['role']==='driver'?'o.delivery_fee':'o.total_amount').'),0) AS total'.self::FROM.' WHERE '.$scope." AND o.status='completed'",$args)->fetch();
            return 'Trong phạm vi tài khoản của bạn có '.$r['n'].' đơn hoàn tất. '.($me['role']==='driver'?'Tổng phí giao':'Tổng giá trị đơn gồm phí giao').' là '.MenuKnowledge::money((float)$r['total']).'.';
        }
        $accountIncident=preg_match('/chiem quyen|tai khoan/',self::normalize($text.' '.($prev['question']??'')));
        if ($accountIncident && in_array($kind,['incident','submit'],true)) $id=null;
        $general=$accountIncident || ($me['role']!=='customer' && $id===null);
        $o=$general?null:self::order($db,$me,$id);
        if ($id!==null && !$o) return 'Mình không tìm thấy đơn đó trong phạm vi tài khoản này. Bạn kiểm tra lại mã đơn giúp mình nhé.';
        $label=$o?'Đơn #'.$o['id'].' tại '.$o['name']: 'Tài khoản của bạn';
        if ($kind==='submit') {
            if (!str_contains($prior,'Gửi phiếu hỗ trợ')) return 'Bạn mô tả sự cố và cho mình biết đơn liên quan trước nhé.';
            $lines=[];foreach($history as $turn) $lines[]=mb_substr($turn['question'],0,350);
            $details=self::normalize(implode(' ',$lines));
            $category=match(true) {
                str_contains($details,'thieu')=>'Thiếu món',preg_match('/nham|sai mon/',$details)===1=>'Sai món',
                str_contains($details,'bi do')=>'Đổ món',preg_match('/thanh toan|tru tien|hoan tien/',$details)===1=>'Đối soát',
                str_contains($details,'tai khoan')=>'Tài khoản',default=>'Sự cố cần kiểm tra'
            };
            $summary="[FoodHub · $category]\n".$label."\nKhách mô tả: ".implode("\n",$lines);
            $ticket=self::ticket($db,$me,$o,$summary);
            return "Đã chuyển phiếu #$ticket vào hộp thư hỗ trợ của Admin. Bạn có thể bổ sung ảnh và theo dõi phản hồi trong Tin nhắn.";
        }
        if ($kind==='incident') {
            $detail=match(true) {
                $accountIncident=>'Bạn còn đăng nhập được tài khoản không? Đừng gửi mật khẩu hay mã xác thực trong chat.',
                (bool)preg_match('/tru tien|thanh toan|hoan tien/',$q)=>'Bạn cho mình biết số tiền và khoảng giờ giao dịch để nhân viên đối soát nhé.',
                str_contains($q,'thieu')=>'Bạn cho mình biết tên món và số phần còn thiếu nhé.',
                (bool)preg_match('/sai|nham/',$q)=>'Bạn đặt món gì và thực tế nhận được món nào?',
                (bool)preg_match('/bi do|do het/',$q)=>'Phần nào bị đổ vậy bạn? Nếu còn ảnh lúc nhận, bạn có thể gửi trong Tin nhắn sau khi mở phiếu.',
                default=>'Bạn cho mình biết chi tiết sự cố và cách bạn đã thử xử lý nhé.'
            };
            if (str_contains($prior,'Gửi phiếu hỗ trợ')) return $label.'. Mình đã ghi thêm thông tin bạn vừa cung cấp. Nhắn “Gửi phiếu hỗ trợ” để chuyển nội dung cho nhân viên.';
            return 'Mình rất tiếc vì bạn gặp sự cố. '.$label.'. '.$detail.' Nhắn “Gửi phiếu hỗ trợ” để chuyển nội dung cho nhân viên.';
        }
        if ($kind==='pay' && !preg_match('/loi|that bai|tru tien|don |chua nhan/',$q)) return 'FoodHub hỗ trợ tiền mặt khi nhận hàng và thanh toán trực tuyến qua PayOS. Chọn phương thức tại bước xác nhận đơn; với PayOS, mở liên kết hoặc quét QR và chờ hệ thống xác nhận.';
        if (!$o && $general) return 'Bạn gửi mã đơn, ví dụ “đơn #123”, để mình kiểm tra đúng giao dịch nhé.';
        if (!$o) return 'Mình chưa thấy đơn nào thuộc phạm vi tài khoản này. Nếu bạn đã bị trừ tiền, nhắn “Gửi phiếu hỗ trợ” để nhân viên đối soát.';
        if ($kind==='refund'||$kind==='pay') {
            $payment=self::query($db,'SELECT status FROM payments WHERE order_id=? ORDER BY id DESC LIMIT 1',[$o['id']])->fetchColumn();
            if ($payment==='refunded') return "$label: giao dịch đã được hệ thống ghi nhận hoàn tiền.";
            if ($kind==='pay' && $payment==='paid') return "$label đã được xác nhận thanh toán.";
            return "$label. ".($kind==='refund'?'Mình chưa thấy giao dịch được xác nhận hoàn tiền.':'Hệ thống chưa xác nhận giao dịch thành công. Nếu đã bị trừ tiền, bạn đừng thanh toán lần nữa.').' Nhắn “Gửi phiếu hỗ trợ” để nhân viên kiểm tra.';
        }
        if ($kind==='cancel'||$kind==='confirmCancel') {
            if ($me['role']!=='customer') return "$label. Bạn mở chi tiết đơn trong màn hình công việc để xem thao tác được phép.";
            if ($o['status']==='cancelled') return "$label đã hủy.";
            if ($o['status']!=='pending'||$o['payment_method']!=='cash') return "$label. Với trạng thái hoặc phương thức thanh toán này, mình cần nhân viên kiểm tra trước khi hủy. Nhắn “Gửi phiếu hỗ trợ” nếu bạn muốn tiếp tục.";
            $confirm='Xác nhận hủy đơn #'.$o['id'];
            if ($kind!=='confirmCancel'||!str_contains($prior,$confirm)) return "$label đang chờ quán xác nhận. Nếu muốn hủy, nhắn “{$confirm}”.";
            $db->beginTransaction();
            try {
                $changed=self::query($db,"UPDATE orders SET status='cancelled',cancelled_at=NOW(),cancel_reason='Khách xác nhận qua trợ lý' WHERE id=? AND customer_id=? AND status='pending' AND payment_method='cash'",[$o['id'],$me['id']])->rowCount();
                if (!$changed) {$db->rollBack();return 'Đơn vừa thay đổi trạng thái. Bạn kiểm tra lại trước khi hủy nhé.';}
                self::query($db,"UPDATE deliveries SET status='cancelled' WHERE order_id=?",[$o['id']]);
                self::query($db,"INSERT INTO order_status_logs(order_id,status,actor_user_id,note) VALUES(?,'cancelled',?,'Khách xác nhận qua trợ lý')",[$o['id'],$me['id']]);
                $db->commit();
            } catch(Throwable $err) {if($db->inTransaction())$db->rollBack();throw $err;}
            return "$label đã được hủy.";
        }
        $delay=preg_match('/doi|lau|cham|gap|chua thay|kieu gi/',$q)?'Mình hiểu bạn đang sốt ruột. ':'';
        return $delay.$label.' '.(self::STATUS[$o['status']]??'đang được kiểm tra').'. '.($o['shipper']?'Shipper phụ trách: '.$o['shipper'].'. ':'').(in_array($o['status'],['completed','cancelled'],true)?'':'Hiện chưa có giờ giao dự kiến chính xác.');
    }
    private static function guide(string $role): string {
        return match ($role) {
            'seller'=>'Bạn mở Thực đơn để quản lý món và mục Đơn hàng để cập nhật tiến độ. Mình có thể tra đơn của quán hoặc tổng giá trị đơn hoàn tất.',
            'driver'=>'Bạn mở danh sách đơn có thể nhận để chọn chuyến. Mình có thể tra đơn được giao cho bạn, tổng phí giao hoặc giúp báo sự cố.',
            'admin'=>'Mình có thể tổng hợp đơn hoàn tất, thống kê phiếu hỗ trợ đang mở và tra cứu đơn theo mã.',
            default=>'Mình có thể giúp bạn tìm món, xem đơn vừa đặt, kiểm tra thanh toán hoặc gửi sự cố cho nhân viên.'
        };
    }
}
