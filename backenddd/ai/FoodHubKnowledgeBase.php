<?php
declare(strict_types=1);

/**
 * Curated Vietnamese Knowledge Base & Lightweight Context Retriever for FoodHub AI.
 * Grounded strictly in actual FoodHub application capabilities (no fabricated hotlines or unsupported features).
 */
final class FoodHubKnowledgeBase {
    /**
     * Retrieve the best matching knowledge article for a Vietnamese query and role.
     *
     * @return array{id: string, topic: string, facts: string, fallback: string, requiresTicketHint: bool, score: int}|null
     */
    public static function retrieve(string $message, string $role = 'customer', array $history = []): ?array {
        $q = self::expandVietnamese($message);
        if ($q === '') return null;

        $best = null;
        $bestScore = 0;

        foreach (self::articles() as $article) {
            if (!empty($article['roles']) && !in_array($role, $article['roles'], true)) {
                continue;
            }
            $score = 0;
            foreach ($article['patterns'] as $pattern => $weight) {
                if (preg_match('~' . $pattern . '~u', $q)) {
                    $score += $weight;
                }
            }
            if ($score > $bestScore) {
                $bestScore = $score;
                $best = $article;
            }
        }

        if ($best === null || $bestScore < 2) {
            return null;
        }

        return [
            'id' => $best['id'],
            'topic' => $best['topic'],
            'facts' => $best['facts'],
            'fallback' => $best['fallback'],
            'requiresTicketHint' => !empty($best['requiresTicketHint']),
            'score' => $bestScore,
        ];
    }

    /** Detect everyday Vietnamese meal/weather/mood cues that should route to TiDB MealAdvisor instead of clarify. */
    public static function isMealMoodContext(string $message): bool {
        $q = self::expandVietnamese($message);
        return (bool)preg_match(
            '~\b(doi bung|doi qua|doi muon xiu|bung keu|chua an gi|tut huyet ap|buon mieng|khat nuoc|kho co|nong buc|giai nhiet|nham nhi|them an|them gi|troi mua|troi lanh|nang nong|cuoi thang|het tien|tiet kiem|it tien|trua nay|toi nay|sang nay|chieu nay|dem khuya|an dem|lot da|chua biet an gi|khong biet an gi|an gi bay gio|an gi ngon|uong gi mat|met qua muon|o mot minh an|eat clean|an nhe)\b~u',
            $q
        );
    }

    /** Normalize everyday Vietnamese slang, teencode, regional terms, and abbreviations (North/Central/South). */
    public static function expandVietnamese(string $text): string {
        $pre = preg_replace('/\bbùn\b/ui', 'buồn', $text);
        $folded = MealAdvisor::folded((string)$pre);
        $patterns = [
            '/\bdh\b/u',
            '/(?<!\d)\b(?:ko|kh|khum|hong|hok|hem)\b/u',
            '/(?<!\d)\bk\b(?!\s*(?:d\b|dong|vnd|m\b))/u',
            '/\b(?:shp|sh)\b/u',
            '/\b(?:dc|dk)\b/u',
            '/\b(?:dchi|dia chi giao)\b/u',
            '/\b(?:sdt|so dt|so dien thoai)\b/u',
            '/\b(?:mk|mik|tui|m)\b/u',
            '/\b(?:bn|nhiu|nhieu tien)\b/u',
            '/\bntn\b/u',
            '/\bvs\b/u',
            '/\bcx\b/u',
            '/\b(?:dg|dang)\b/u',
            '/\b(?:ib|nt)\b/u',
            '/\b(?:rep|trl)\b/u',
            '/\b(?:tks|thx|cam on)\b/u',
            '/\b(?:ck|chuyen khoan)\b/u',
            '/\bcod\b/u',
            '/\b(?:fs|freeship|free ship)\b/u',
            '/(?<!\d\s)(?<!\d)\bkm\b/u',
            '/\btx\b/u',
            '/\b(?:kb|ko bt|khong bt|khong bit)\b/u',
            '/\b(?:j|gj)\b/u',
            '/\b(?:mun|muon)\b/u',
            '/\b(?:rui|r)\b/u',
            '/\blun\b/u',
            '/\b(?:dzay|zay|z)\b/u',
            '/\bdzo\b/u',
            '/\bnc\b/u',
            '/\b(?:cf|cafe)\b/u',
            '/\b(?:ts|trasua)\b/u',
            '/\b(?:bh|bjo)\b/u',
        ];
        $replacements = [
            'don hang',
            'khong',
            'khong',
            'shop',
            'duoc',
            'dia chi',
            'so dien thoai',
            'minh',
            'bao nhieu',
            'nhu the nao',
            'voi',
            'cung',
            'dang',
            'nhan tin',
            'tra loi',
            'cam on',
            'chuyen khoan',
            'tien mat',
            'mien phi giao hang',
            'khuyen mai',
            'tai xe',
            'khong biet',
            'gi',
            'muon',
            'roi',
            'luon',
            'vay',
            'vao',
            'nuoc',
            'ca phe',
            'tra sua',
            'bay gio',
        ];
        return trim((string)preg_replace('/\s+/u', ' ', (string)preg_replace($patterns, $replacements, $folded)));
    }

    private static function articles(): array {
        return [
            // 1. Đổi địa chỉ / SĐT nhận hàng
            [
                'id' => 'change_address_phone',
                'topic' => 'Địa chỉ và số điện thoại nhận hàng',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:doi|sua|thay|cap nhat|nham|sai)\s+dia chi' => 4,
                    'them dia chi|luu dia chi|quan ly dia chi|dia chi o dau' => 4,
                    '(?:doi|sua|nham|sai)\s+so dien thoai' => 4,
                    'nham so|doi sdt|sua sdt' => 3,
                ],
                'facts' => 'Trước khi đặt đơn, khách có thể thêm hoặc đổi địa chỉ tại mục Tài khoản > Địa chỉ giao hàng hoặc ngay tại bước thanh toán. Nếu đơn đã đặt xong mà nhầm địa chỉ/SĐT, khách nên nhắn trực tiếp cho shipper/quán trong mục Tin nhắn của đơn hàng hoặc gửi phiếu hỗ trợ để nhân viên kiểm tra.',
                'fallback' => 'Trước khi đặt đơn, mình vào Tài khoản > Địa chỉ giao hàng để thêm hoặc sửa địa chỉ/SĐT nha. Nếu đơn đã đặt rồi mà lỡ nhập nhầm, mình nhắn ngay cho tài xế trong mục Tin nhắn của chi tiết đơn hoặc nhắn “Gửi phiếu hỗ trợ” để nhân viên hỗ trợ nhé.',
                'requiresTicketHint' => false,
            ],
            // 2. Giỏ hàng 1 quán / đặt nhiều quán cùng lúc
            [
                'id' => 'cart_multi_store',
                'topic' => 'Quy định giỏ hàng nhiều quán',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:hai|2|nhieu)\s+(?:quan|nha hang|cua hang)' => 4,
                    'khong them duoc.*gio|xoa gio cu|doi nha hang trong gio' => 4,
                    'dat gop|gom chung.*quan' => 3,
                ],
                'facts' => 'Trên FoodHub mỗi đơn hàng áp dụng cho một cửa hàng để shipper lấy món nhanh, giữ đồ ăn nóng và tính đúng phí giao. Nếu muốn đặt món ở 2 quán khác nhau, khách đặt thành 2 đơn riêng biệt.',
                'fallback' => 'Hiện mỗi đơn trên FoodHub chỉ đặt từ 1 quán để shipper giao nhanh và giữ món ngon hơn ạ. Nếu mình muốn ăn món của 2 quán khác nhau, mình cứ đặt thành 2 đơn riêng nhé!',
            ],
            // 3. Hướng dẫn đặt món trên FoodHub
            [
                'id' => 'how_to_order',
                'topic' => 'Hướng dẫn đặt món',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:lam sao|cach|huong dan)\s*(?:de )?(?:dat mon|dat hang|dat do an|mua hang)' => 4,
                    'dat hang nhu the nao|mua tren app sao' => 4,
                    'dat truoc gio|hen gio giao' => 3,
                ],
                'facts' => 'Các bước đặt món: 1) Chạm vào thẻ món ăn ở Trang chủ, Tìm kiếm hoặc nhắn trực tiếp cho FoodHub AI (hỗ trợ cả Đặt lại đơn cũ 1 chạm, Gợi ý món kèm mở khóa voucher giảm sâu và Hẹn giờ giao hàng trước trong giờ mở cửa của quán); 2) Kiểm tra báo giá tổng tiền, địa chỉ, phí ship theo km, voucher và chọn Tiền mặt hoặc PayOS; 3) Xác nhận đặt hàng và theo dõi tiến độ ở mục Đơn hàng.',
                'fallback' => 'Mình có thể chạm vào thẻ món để thêm vào giỏ hoặc nhắn trực tiếp cho FoodHub AI đặt món giúp mình (hỗ trợ đặt lại đơn cũ, tự tìm mã giảm giá tốt nhất và hẹn giờ giao trước trong khung giờ quán mở cửa) nha!',
            ],
            // 4. Ghi chú món ăn (không hành, ít cay, đũa muỗng)
            [
                'id' => 'order_note',
                'topic' => 'Ghi chú cho quán',
                'roles' => ['customer'],
                'patterns' => [
                    'ghi chu|dan quan|note cho quan|khong lay dua|lay them dua|de rieng nuoc' => 4,
                    'nhac quan|nhan quan.*(?:khong hanh|it cay|khong ot)' => 3,
                ],
                'facts' => 'Khách có thể nhắn trực tiếp cho quán qua mục Tin nhắn trong chi tiết đơn hàng ngay sau khi đặt nếu muốn dặn thêm (như không hành, ít cay, để riêng nước dùng). Với dị ứng thực phẩm, luôn xác nhận trực tiếp với quán trước khi đặt.',
                'fallback' => 'Sau khi đặt đơn, mình có thể nhắn trực tiếp cho quán trong mục Tin nhắn của chi tiết đơn để dặn thêm (như không hành, ít cay, thêm đũa) nha. Nếu có dị ứng thực phẩm, mình nhớ hỏi kỹ quán trước khi đặt ạ.',
            ],
            // 5. Liên hệ shipper / quán qua ứng dụng
            [
                'id' => 'contact_shipper_store',
                'topic' => 'Liên hệ quán và tài xế',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:lien he|nhan tin|chat|goi|xin so|so dien thoai)\s*(?:cho |voi )?(?:quan|nha hang|shipper|tai xe)' => 4,
                    'nhan tin voi shipper|goi tai xe o dau|chat voi quan' => 4,
                ],
                'facts' => 'Khách có thể mở mục Đơn hàng -> chọn đơn đang giao -> dùng nút Tin nhắn để trao đổi trực tiếp với quán hoặc shipper mà không cần tìm số ngoài ứng dụng.',
                'fallback' => 'Mình vào mục Đơn hàng, chọn đơn đang xử lý rồi bấm biểu tượng Tin nhắn để chat trực tiếp với quán hoặc tài xế nhé!',
            ],
            // 6. Báo đã giao nhưng chưa nhận được / lỡ cuộc gọi shipper
            [
                'id' => 'delivered_not_received',
                'topic' => 'Báo đã giao nhưng chưa nhận được hàng',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:bao|trang thai)\s*da giao.*(?:chua nhan|khong thay|chua thay)' => 5,
                    'chua nhan duoc.*da giao|khong goi.*da giao' => 5,
                    'shipper goi.*(?:khong bat may|lo cuoc goi|khong nghe may)' => 4,
                ],
                'facts' => 'Nếu app báo đã giao nhưng khách chưa nhận được hàng, khách kiểm tra khu vực lễ tân/cổng hoặc tin nhắn từ tài xế trước; nếu vẫn chưa thấy thì nhắn “Gửi phiếu hỗ trợ” ngay để Admin kiểm tra và đối soát với tài xế.',
                'fallback' => 'Mình kiểm tra lại khu vực cổng/lễ tân và mục Tin nhắn xem tài xế có nhắn gửi gì chưa nha. Nếu vẫn chưa nhận được hàng dù app báo đã giao, mình nhắn “Gửi phiếu hỗ trợ” để Admin kiểm tra gấp giúp mình nhé!',
                'requiresTicketHint' => true,
            ],
            // 7. Chất lượng đồ ăn (nguội, sống, mặn, dị vật, ôi thiu, sai ghi chú)
            [
                'id' => 'food_quality_issue',
                'topic' => 'Sự cố chất lượng món ăn',
                'roles' => ['customer'],
                'patterns' => [
                    'do an.*(?:\bbi nguoi\b|\bnguoi ngat\b|\bbi song\b|\boi thiu\b|\bthiu\b|\bbi chua\b|\bqua chua\b|\bqua man\b|\bbi man\b|\bkhet\b|\bbi chay\b|\bchay khet\b|\bco mui hoi\b|\bhoi thiu\b|\bdo an nguoi\b|\bdo an song\b|\bdo an hoi\b)' => 4,
                    '(?:nguoi ngat|bi song|oi thiu|co toc|di vat|con trung|ruoi|gian)' => 5,
                    'khong dung ghi chu|dan khong.*van bo|bo day hanh' => 4,
                ],
                'facts' => 'Với sự cố chất lượng món ăn (nguội, sống, có dị vật, không đúng ghi chú), khách nên chụp ảnh rõ phần đồ ăn gặp vấn đề và nhắn “Gửi phiếu hỗ trợ” để Admin tiếp nhận và xử lý cùng quán.',
                'fallback' => 'Em rất tiếc vì chất lượng món lần này không như mong đợi ạ 😔 Mình chụp lại ảnh món ăn bị vấn đề rồi nhắn “Gửi phiếu hỗ trợ” để nhân viên FoodHub kiểm tra và xử lý cùng quán cho mình nhé!',
                'requiresTicketHint' => true,
            ],
            // 8. Lỗi không áp dụng được voucher / điều kiện dùng mã
            [
                'id' => 'voucher_troubleshoot',
                'topic' => 'Lỗi không áp dụng được mã giảm giá',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:khong ap|khong dung|loi|khong nhap|sao khong).*?(?:voucher|ma giam|ma khuyen mai)' => 5,
                    '(?:voucher|ma giam|ma khuyen mai).*?(?:khong ap|khong dung|loi|het luot|khong hop le|khong du)' => 5,
                    'dieu kien.*(?:voucher|ma giam)|ap nhieu ma|dung 2 ma' => 4,
                ],
                'facts' => 'Lý do thường gặp không áp được voucher: 1) Tổng tiền món chưa đạt giá trị đơn tối thiểu; 2) Mã đã hết lượt sử dụng hoặc hết hạn; 3) Mã chỉ áp dụng riêng cho một cửa hàng khác; 4) Mỗi đơn hàng chỉ áp dụng 1 mã khuyến mãi.',
                'fallback' => 'Mã giảm giá thường không áp được do đơn chưa đủ giá trị tối thiểu, mã đã hết lượt/hết hạn hoặc chỉ áp riêng cho quán khác (và mỗi đơn chỉ dùng 1 mã ạ). Mình kiểm tra lại điều kiện mã ở bước Giỏ hàng giúp em nha!',
            ],
            // 9. Miễn phí giao hàng / freeship
            [
                'id' => 'freeship_promo',
                'topic' => 'Mã miễn phí giao hàng',
                'roles' => ['customer'],
                'patterns' => [
                    'mien phi giao hang|ma freeship|giam phi ship|bot tien ship' => 4,
                ],
                'facts' => 'FoodHub có các mã khuyến mãi (bao gồm mã miễn phí giao hàng tùy đợt) hiển thị ngay ở banner Trang chủ và bước chọn voucher trong Giỏ hàng.',
                'fallback' => 'Các mã giảm giá và miễn phí giao hàng đang mở đều hiển thị trên banner Trang chủ và trong mục Chọn voucher ở Giỏ hàng nha. Mình có thể hỏi “có mã giảm giá nào không” để em tra mã chung còn lượt ngay ạ!',
            ],
            // 10. Quán tự hủy đơn / lý do bị hủy
            [
                'id' => 'order_cancelled_by_store',
                'topic' => 'Đơn bị quán hủy và tiền thanh toán',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:tai sao|vi sao|sao).*(?:bi huy|quan huy|tu huy)' => 4,
                    'quan huy don|don tu huy|bi huy.*payos|huy don.*da thanh toan' => 5,
                ],
                'facts' => 'Đơn có thể bị quán hủy khi món tạm hết, quán quá tải hoặc sắp đóng cửa. Nếu đơn đã thanh toán online qua PayOS mà bị hủy, khách nhắn “Gửi phiếu hỗ trợ” để Admin đối soát hoàn tiền (chỉ xác nhận hoàn tiền khi hệ thống báo refunded).',
                'fallback' => 'Đơn bị hủy thường do quán hết món hoặc quá tải đột xuất ạ. Nếu mình đã thanh toán PayOS cho đơn đó, mình nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để nhân viên kiểm tra đối soát hoàn tiền giúp mình nhé!',
                'requiresTicketHint' => true,
            ],
            // 11. Tài khoản, quên mật khẩu, đăng nhập Google, hồ sơ
            [
                'id' => 'account_login_password',
                'topic' => 'Tài khoản và đăng nhập',
                'roles' => ['customer', 'seller', 'driver'],
                'patterns' => [
                    'quen mat khau|doi mat khau|khong dang nhap duoc|loi dang nhap|dang nhap google' => 4,
                    'doi ten|doi avatar|sua thong tin ca nhan|xoa tai khoan' => 4,
                ],
                'facts' => 'FoodHub sử dụng đăng nhập bằng Email/Mật khẩu hoặc Google (Firebase Authentication). Nếu quên mật khẩu, chọn Quên mật khẩu trên màn hình đăng nhập để nhận email đặt lại. Đổi tên/ảnh đại diện tại mục Tài khoản. Không bao giờ gửi mật khẩu hay mã OTP vào khung chat.',
                'fallback' => 'Nếu quên mật khẩu, mình bấm “Quên mật khẩu” ở màn hình đăng nhập để đặt lại qua email nhé; đổi tên hay ảnh đại diện thì vào mục Tài khoản ạ. Lưu ý mình đừng gửi mật khẩu hay OTP vào chat nha!',
            ],
            // 12. Đăng ký bán hàng (Quán) / Đăng ký làm Shipper
            [
                'id' => 'partner_register',
                'topic' => 'Đăng ký đối tác Nhà hàng hoặc Shipper',
                'roles' => ['customer', 'seller', 'driver'],
                'patterns' => [
                    '(?:dang ky|mo quan|ban hang|tro thanh)\s*(?:tren foodhub|nha hang|quan|doi tac|ban do an)' => 4,
                    '(?:dang ky|chay|lam)\s*(?:shipper|tai xe|giao hang)' => 4,
                ],
                'facts' => 'Trên màn hình Đăng ký tài khoản của FoodHub, người dùng có thể chọn vai trò Nhà hàng hoặc Shipper, điền hồ sơ và chờ Admin duyệt kích hoạt tài khoản.',
                'fallback' => 'Mình có thể chọn vai trò Nhà hàng hoặc Shipper ngay tại màn hình Đăng ký của FoodHub rồi gửi hồ sơ để Admin duyệt kích hoạt nhé!',
            ],
            // 13. Giờ mở cửa / quán đóng / món hết hàng
            [
                'id' => 'store_hours_availability',
                'topic' => 'Giờ mở cửa và tình trạng món',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:may gio|gio mo cua|gio dong cua|giao toi may gio|hoat dong may gio)' => 4,
                    'sao quan.*dong cua|mon.*het hang|khi nao co lai' => 4,
                ],
                'facts' => 'Giờ phục vụ phụ thuộc từng cửa hàng trên FoodHub. Khi quán tắt trạng thái mở cửa hoặc món tạm hết hàng, ứng dụng tự ẩn/báo tạm hết để khách không bị đặt nhầm.',
                'fallback' => 'Giờ mở cửa và tình trạng còn món phụ thuộc từng quán trên FoodHub ạ. Khi quán tạm nghỉ hoặc hết món, app sẽ tự cập nhật ngay để mình tiện chọn quán khác đang mở nha!',
            ],
            // 14. Thời gian giao hàng trung bình & theo dõi bản đồ
            [
                'id' => 'delivery_time_tracking',
                'topic' => 'Theo dõi giao hàng trên bản đồ',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:giao hang|ship)\s*(?:thuong )?mat bao lau|xem vi tri shipper|theo doi don o dau|ban do giao hang' => 4,
                ],
                'facts' => 'Thời gian giao phụ thuộc thời gian quán chuẩn bị và khoảng cách thực tế. Khi có tài xế nhận đơn, khách có thể mở mục Đơn hàng -> chọn đơn để theo dõi trạng thái và vị trí trực tiếp trên bản đồ. Không tự bịa số phút cố định.',
                'fallback' => 'Thời gian giao phụ thuộc lúc quán chuẩn bị và quãng đường nên không cố định ạ. Khi tài xế nhận đơn, mình mở chi tiết đơn trong mục Đơn hàng là xem được tiến độ và bản đồ giao hàng trực tiếp nha!',
            ],
            // 15. Đánh giá món / quán
            [
                'id' => 'review_rating',
                'topic' => 'Đánh giá đơn hàng và quán ăn',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:danh gia|review|cham sao|viet nhan xet)\s*(?:quan|mon|don|shipper)?' => 3,
                    'danh gia o dau|sua danh gia' => 4,
                ],
                'facts' => 'Sau khi đơn hàng hoàn tất, khách có thể mở chi tiết đơn trong mục Đơn hàng để gửi đánh giá về trải nghiệm. Góp ý về chất lượng dịch vụ cũng có thể gửi cho nhân viên qua Tin nhắn hỗ trợ.',
                'fallback' => 'Sau khi đơn hoàn tất, mình mở lại đơn trong mục Đơn hàng để gửi đánh giá nhé. Nếu có góp ý cần nhân viên xử lý riêng, mình cứ nhắn em hỗ trợ tạo phiếu nha!',
            ],
            // 16. Hóa đơn đỏ / VAT
            [
                'id' => 'invoice_vat',
                'topic' => 'Biên nhận và hóa đơn VAT',
                'roles' => ['customer'],
                'patterns' => [
                    'hoa don do|xuat hoa don|hoa don vat|bien lai|chung tu' => 4,
                ],
                'facts' => 'Ứng dụng hiển thị đầy đủ chi tiết thanh toán trong mục Đơn hàng / Lịch sử thanh toán. Với yêu cầu xuất hóa đơn VAT, khách vui lòng liên hệ trực tiếp cửa hàng cung cấp món.',
                'fallback' => 'Chi tiết đơn và thanh toán có sẵn trong mục Đơn hàng/Lịch sử thanh toán ạ. Còn hóa đơn VAT thì mình nhắn trực tiếp với quán cung cấp món để hỏi hỗ trợ xuất hóa đơn nhé!',
            ],
            // 17. Shipper: quán đóng cửa / hết món / làm lâu
            [
                'id' => 'driver_store_issue',
                'topic' => 'Shipper gặp sự cố tại quán',
                'roles' => ['driver'],
                'patterns' => [
                    'quan.*(?:dong cua|nghi ban|het mon|tu choi|lam.*lau)|cho quan' => 4,
                ],
                'facts' => 'Nếu tài xế đến quán nhưng quán đóng cửa, hết món hoặc quá tải làm quá lâu, tài xế nên nhắn cho khách biết tình trạng qua Tin nhắn đơn hàng và nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để Admin xác nhận và xử lý kịp thời.',
                'fallback' => 'Nếu quán đóng cửa, hết món hoặc chờ quá lâu, anh/chị nhắn báo trước cho khách trong Tin nhắn đơn rồi nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để Admin ghi nhận và hỗ trợ xử lý ngay nhé!',
                'requiresTicketHint' => true,
            ],
            // 18. Shipper: gọi khách không nghe máy
            [
                'id' => 'driver_customer_unreachable',
                'topic' => 'Shipper không liên hệ được khách',
                'roles' => ['driver'],
                'patterns' => [
                    'khach khong nghe may|khong lien he duoc khach|khach khong ra nhan|bom hang' => 4,
                ],
                'facts' => 'Khi không liên hệ được khách, tài xế thử gọi lại và gửi tin nhắn trong ứng dụng để lưu bằng chứng liên hệ, sau đó nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để Admin hướng dẫn xử lý đúng quy trình.',
                'fallback' => 'Anh/chị thử gọi lại và nhắn 1 tin trong mục Tin nhắn đơn hàng để lưu bằng chứng liên hệ, rồi nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để Admin hướng dẫn xử lý ngay nha!',
                'requiresTicketHint' => true,
            ],
            // 19. Nhà hàng: cập nhật thực đơn / tạm tắt món / đóng quán tạm thời
            [
                'id' => 'seller_menu_operations',
                'topic' => 'Quán cập nhật thực đơn và trạng thái mở cửa',
                'roles' => ['seller'],
                'patterns' => [
                    '(?:them|sua|xoa|tat|an|cap nhat)\s*(?:mon|gia|menu|thuc don)' => 4,
                    'tam dong cua|nghi ban|tat quan|qua tai|khong nhan duoc don' => 4,
                ],
                'facts' => 'Chủ quán vào mục Thực đơn để thêm/sửa giá hoặc tắt trạng thái còn bán của từng món. Để tạm nghỉ khi quá tải, vào Cài đặt cửa hàng tắt trạng thái Đang mở cửa. Nếu ứng dụng không reo đơn mới, kiểm tra kết nối mạng và bật thông báo đơn hàng trong Cài đặt quán.',
                'fallback' => 'Anh/chị vào mục Thực đơn để chỉnh giá hoặc tắt tạm món hết hàng, còn muốn tạm nghỉ giờ cao điểm thì tắt “Đang mở cửa” trong Cài đặt cửa hàng nhé. Nếu app không báo đơn mới, anh/chị kiểm tra mục Thông báo đơn hàng trong Cài đặt giúp em nha!',
            ],
            // 20. Khách đặt nhầm món / muốn đổi hoặc thêm món sau khi đặt
            [
                'id' => 'change_or_modify_items',
                'topic' => 'Đổi hoặc thêm món sau khi đã đặt đơn',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:dat nham|chon nham|bam nham|doi sang)\s*mon' => 5,
                    '(?:them|bot|doi)\s*mon.*(?:da dat|trong don hang|sau khi dat|vua dat)' => 4,
                ],
                'facts' => 'Nếu đơn đang ở trạng thái Chờ xác nhận (quán chưa làm món), khách có thể hủy đơn đó và đặt lại giỏ hàng mới đúng món mong muốn. Nếu quán đã nhận và đang chuẩn bị món, khách nhắn ngay cho quán trong mục Tin nhắn của chi tiết đơn hàng để hỏi hỗ trợ.',
                'fallback' => 'Nếu đơn vẫn đang “Chờ xác nhận”, mình có thể hủy đơn rồi đặt lại món mới cho nhanh nha. Còn nếu quán đã nhận làm món rồi, mình vào chi tiết đơn bấm Tin nhắn để trao đổi trực tiếp với quán xem kịp điều chỉnh không nhé!',
            ],
            // 21. Đã chuyển khoản PayOS nhưng app vẫn báo chờ thanh toán
            [
                'id' => 'payos_pending_sync',
                'topic' => 'Chuyển khoản PayOS xong nhưng đơn vẫn báo chờ thanh toán',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:chuyen khoan|thanh toan|quet qr|payos).*(?:van bao cho|chua cap nhat|van cho thanh toan|chua xac nhan)' => 5,
                    'tru tien roi.*(?:cho thanh toan|chua cap nhat)' => 5,
                ],
                'facts' => 'Hệ thống PayOS thường tự động xác nhận trong vài giây khi chuyển khoản đúng nội dung QR. Nếu tài khoản đã bị trừ tiền quá 2 phút mà đơn vẫn ở trạng thái Chờ thanh toán, khách giữ lại ảnh giao dịch và nhắn “Gửi phiếu hỗ trợ” để Admin kiểm tra đối soát ngay.',
                'fallback' => 'Thông thường PayOS sẽ tự động cập nhật trong vài giây ạ. Nếu mình đã bị trừ tiền hơn 2 phút mà đơn vẫn hiện “Chờ thanh toán”, mình giữ lại ảnh biên lai giao dịch rồi nhắn “Gửi phiếu hỗ trợ” để Admin đối soát xác nhận ngay giúp mình nhé!',
                'requiresTicketHint' => true,
            ],
            // 22. Xem trang Profile chi tiết món ăn từ gợi ý AI / Trang chủ
            [
                'id' => 'dish_profile_navigation',
                'topic' => 'Xem chi tiết Profile món ăn khi nhấn thẻ gợi ý',
                'roles' => ['customer'],
                'patterns' => [
                    '(?:nhan|bam|cham)\s*vao\s*(?:mon|the mon|goi y).*(?:ra gi|ra trang|mo ra|mo trang|profile|chi tiet|nha hang)' => 5,
                    '(?:trang|man hinh)\s*(?:profile|chi tiet)\s*mon an' => 4,
                ],
                'facts' => 'Khi khách chạm vào thẻ món ăn trên Trang chủ, Tìm kiếm hoặc ngay trong khung gợi ý của FoodHub AI, ứng dụng sẽ mở trực tiếp trang Chi tiết món ăn (Profile món) hiển thị ảnh lớn, giá, danh mục, mô tả, nút chọn số lượng để Thêm vào giỏ ngay mà không phải tự kéo tìm trong menu nhà hàng; từ đó khách cũng có thể bấm vào thẻ quán nếu muốn xem thêm các món khác.',
                'fallback' => 'Khi mình chạm vào thẻ món ăn (kể cả món do FoodHub AI vừa gợi ý), ứng dụng sẽ mở thẳng trang Chi tiết món ăn để mình xem mô tả, chọn số lượng và bấm Thêm vào giỏ ngay mà không cần kéo tìm lại trong menu quán nha!',
            ],
            // 23. Nhà hàng: xem chi tiết biểu đồ doanh thu & tạo danh mục món tùy ý
            [
                'id' => 'seller_revenue_and_categories',
                'topic' => 'Nhà hàng xem biểu đồ doanh thu chi tiết và thêm danh mục món mới',
                'roles' => ['seller'],
                'patterns' => [
                    'bieu do doanh thu|xem chi tiet doanh thu|doanh thu tuan|doanh thu thang' => 5,
                    '(?:them|tao|nhap)\s*danh muc\s*(?:moi|tuy y|khac)|ngoai mon chinh.*khai vi' => 5,
                ],
                'facts' => 'Ở tài khoản Nhà hàng: 1) Tại trang Tổng quan, chạm vào từng cột T2–CN để xem nhanh doanh thu ngày hoặc bấm “Chi tiết” trên thẻ Doanh thu để mở màn hình phân tích theo Tuần/Tháng, biểu đồ tăng trưởng lũy kế và danh sách đơn từng ngày; 2) Trong mục Thực đơn khi thêm/sửa món, chủ quán có thể chọn nhanh hoặc tự nhập bất kỳ danh mục nào (như Nước giải khát, Ăn vặt, Trà sữa, Cà phê, Đồ nướng...) và FoodHub AI sẽ tự học danh mục đó để tư vấn cho khách.',
                'fallback' => 'Anh/chị có thể bấm nút “Chi tiết” ở thẻ Doanh thu trên trang Tổng quan để xem biểu đồ phân tích theo Tuần/Tháng và danh sách đơn chi tiết từng ngày nha. Còn khi thêm món trong Thực đơn, anh/chị nhập tự do bất kỳ tên danh mục nào (như Nước giải khát, Ăn vặt, Trà sữa...) đều được hệ thống lưu và đồng bộ cho AI gợi ý ạ!',
            ],
            // 24. Shipper gặp sự cố hỏng xe / mưa ngập giữa đường khi đang giao
            [
                'id' => 'driver_vehicle_breakdown_or_incident',
                'topic' => 'Shipper hỏng xe hoặc gặp sự cố trên đường giao hàng',
                'roles' => ['driver'],
                'patterns' => [
                    '(?:hong xe|be banh|thung lop|tai nan|ngap nuoc|ket xe nang).*(?:dang giao|giua duong|don hang)?' => 5,
                ],
                'facts' => 'Nếu gặp sự cố hỏng xe, thủng lốp hoặc ngập nước khi đang giao đơn, tài xế đảm bảo an toàn trước, nhắn tin báo ngay cho khách trong mục Tin nhắn của đơn hàng để khách nắm tình hình, sau đó nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để Admin hỗ trợ điều phối.',
                'fallback' => 'Anh/chị ưu tiên đảm bảo an toàn trước nhé! Sau đó mình nhắn 1 tin trong mục Tin nhắn đơn hàng để báo khách thông cảm, rồi nhắn “Gửi phiếu hỗ trợ” kèm mã đơn để Admin hỗ trợ xử lý đơn ngay ạ.',
                'requiresTicketHint' => true,
            ],
        ];
    }
}
