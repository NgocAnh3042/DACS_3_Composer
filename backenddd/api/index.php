<?php
declare(strict_types=1);

$foodhubRequestStartedAt = hrtime(true);
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Authorization, Content-Type');
header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { error_log(sprintf('[FoodHub request] method=OPTIONS route=/preflight duration_ms=%.2f sql_queries=0',(hrtime(true)-$foodhubRequestStartedAt)/1_000_000));http_response_code(204);exit; }
if (extension_loaded('zlib') && !ini_get('zlib.output_compression') && stripos((string)($_SERVER['HTTP_ACCEPT_ENCODING'] ?? ''), 'gzip') !== false) {
    @ob_start('ob_gzhandler');
}

function out(int $status, array $body): never {
    $resource = (string)($GLOBALS['foodhub_resource'] ?? '');
    if ($status === 200 && ($_SERVER['REQUEST_METHOD'] ?? '') === 'GET' && in_array($resource, ['categories','stores','dishes'], true)) {
        $encoded = json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if (is_string($encoded)) {
            $etag = '"'.hash('sha256', $encoded).'"';
            header('Cache-Control: private, max-age=60, stale-while-revalidate=120');
            header('ETag: '.$etag);
            header('Vary: Accept-Encoding');
            $provided = trim((string)($_SERVER['HTTP_IF_NONE_MATCH'] ?? ''));
            if ($provided === '*' || in_array($etag, array_map('trim', explode(',', $provided)), true)) {
                if(ob_get_level()>0)ob_end_clean();
                http_response_code(304);
                exit;
            }
            echo $encoded;
            http_response_code($status);
            exit;
        }
    }
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function body(): array {
    $json = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($json)) out(400, ['error'=>'JSON không hợp lệ']);
    return $json;
}
function bearer(): string {
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '');
    if ($header === '') {
        foreach ($headers as $name => $value) {
            if (strcasecmp((string)$name, 'Authorization') === 0) { $header = (string)$value; break; }
        }
    }
    if (!preg_match('/^Bearer\s+(.+)$/i', $header, $m)) out(401,['error'=>'Thiếu Firebase ID token']);
    return trim($m[1]);
}
function b64url(string $value): string {
    $raw=base64_decode(strtr($value,'-_','+/').str_repeat('=',(4-strlen($value)%4)%4),true);
    return $raw===false ? '' : $raw;
}
function foodhubDistanceKm(?float $lat1,?float $lng1,?float $lat2,?float $lng2): ?float {
    if($lat1===null||$lng1===null||$lat2===null||$lng2===null)return null;
    if(!is_finite($lat1)||!is_finite($lng1)||!is_finite($lat2)||!is_finite($lng2))return null;
    if((abs($lat1)<0.0001&&abs($lng1)<0.0001)||(abs($lat2)<0.0001&&abs($lng2)<0.0001))return null;
    if($lat1<-90||$lat1>90||$lat2<-90||$lat2>90||$lng1<-180||$lng1>180||$lng2<-180||$lng2>180)return null;
    $r=6371.0;$dLat=deg2rad($lat2-$lat1);$dLng=deg2rad($lng2-$lng1);
    $a=sin($dLat/2)**2+cos(deg2rad($lat1))*cos(deg2rad($lat2))*sin($dLng/2)**2;
    $raw=2*$r*atan2(sqrt($a),sqrt(max(0.0,1.0-$a)));
    return round(max(0.1,$raw),1);
}
function foodhubShippingFee(?float $distanceKm,float $baseFee=15000.0): float {
    if($distanceKm===null||!is_finite($distanceKm)||$distanceKm<=3.0)return $baseFee;
    $extraKm=max(1,(int)ceil(round($distanceKm-3.0,4)-1e-9));
    return $baseFee+($extraKm*10000.0);
}
function foodhubDeliveryEstimate(?float $distanceKm,?int $prepMinutes): string {
    $prep=($prepMinutes===null||$prepMinutes<=0||$prepMinutes>=28)?12:max(8,min(25,$prepMinutes));
    if($distanceKm===null||!is_finite($distanceKm))return ($prep+8).'–'.($prep+15).' phút';
    $travel=(int)ceil($distanceKm*2.5)+3;
    $min=max(12,$prep+$travel);
    $max=$min+($distanceKm>5.0?7:5);
    return $min.'–'.$max.' phút';
}
function foodhubCertificateCacheTtl(array $headers): int {
    $cacheControl='';$expiresAt=null;$age=0;
    foreach($headers as $header){
        if(preg_match('/^Cache-Control:\s*(.*)$/i',(string)$header,$m))$cacheControl=$m[1];
        elseif(preg_match('/^Expires:\s*(.*)$/i',(string)$header,$m))$expiresAt=strtotime($m[1])?:null;
        elseif(preg_match('/^Age:\s*(\d+)/i',(string)$header,$m))$age=(int)$m[1];
    }
    if(preg_match('/(?:^|,)\s*max-age\s*=\s*"?(\d+)/i',$cacheControl,$m))$ttl=(int)$m[1]-$age;
    elseif($expiresAt!==null)$ttl=$expiresAt-time()-$age;
    else $ttl=3600;
    return max(1,min(86400,$ttl));
}
function foodhubFetchFirebaseCertificates(): ?array {
    $url='https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
    $ctx=stream_context_create(['http'=>['timeout'=>8,'ignore_errors'=>true,'header'=>"Accept: application/json\r\n"]]);
    $raw=@file_get_contents($url,false,$ctx);
    $headers=is_array($http_response_header??null)?$http_response_header:[];
    $streamStatus=0;foreach($headers as $header)if(preg_match('#^HTTP/\S+\s+(\d{3})#i',(string)$header,$match))$streamStatus=(int)$match[1];
    if($raw!==false&&($streamStatus<200||$streamStatus>=300))$raw=false;
    if($raw===false && function_exists('curl_init')) {
        $curl=curl_init($url);$headers=[];
        curl_setopt_array($curl,[CURLOPT_RETURNTRANSFER=>true,CURLOPT_CONNECTTIMEOUT=>5,CURLOPT_TIMEOUT=>8,CURLOPT_HTTPHEADER=>['Accept: application/json'],CURLOPT_HEADERFUNCTION=>static function($handle,string $line)use(&$headers):int{$headers[]=trim($line);return strlen($line);}]);
        $raw=curl_exec($curl);$status=(int)curl_getinfo($curl,CURLINFO_RESPONSE_CODE);curl_close($curl);
        if($status<200||$status>=300)$raw=false;
    }
    $certs=is_string($raw)?json_decode($raw,true):null;
    if(!is_array($certs)||!$certs)return null;
    return ['certs'=>$certs,'expires_at'=>time()+foodhubCertificateCacheTtl($headers)];
}
function foodhubFirebaseCertificates(bool $forceRefresh=false): ?array {
    $cache=foodhubCacheFile('foodhub-firebase-certs.json');
    if(!$forceRefresh && ($handle=@fopen($cache,'rb'))){
        $raw=false;if(flock($handle,LOCK_SH))$raw=stream_get_contents($handle);fclose($handle);
        $decoded=is_string($raw)?json_decode($raw,true):null;
        if(is_array($decoded)){
            // Accept the legacy raw cert map once, then refresh according to Google's response headers.
            $keys=is_array($decoded['certs']??null)?$decoded['certs']:$decoded;
            $expires=(int)($decoded['expires_at']??((@filemtime($cache)?:0)+3600));
            if($keys && $expires>time())return ['certs'=>$keys,'expires_at'=>$expires];
        }
    }
    $fresh=foodhubFetchFirebaseCertificates();
    if($fresh!==null)foodhubWriteJsonCache('foodhub-firebase-certs.json',$fresh);
    return $fresh;
}
function verifyToken(string $token, string $project): array {
    $parts=explode('.',$token);
    if(count($parts)!==3) out(401,['error'=>'Firebase ID token không hợp lệ']);
    $head=json_decode(b64url($parts[0]),true) ?: [];
    $claims=json_decode(b64url($parts[1]),true) ?: [];
    $signature=b64url($parts[2]);
    if(($head['alg']??'')!=='RS256' || empty($head['kid']) || $signature==='') out(401,['error'=>'Token không hợp lệ']);
    $certificateSet=foodhubFirebaseCertificates();
    if($certificateSet===null)out(503,['error'=>'API chưa tải được khóa xác minh Firebase; kiểm tra kết nối HTTPS của PHP']);
    $keys=$certificateSet['certs'];
    $cert=$keys[$head['kid']]??null;
    if(!$cert){
        $refreshed=foodhubFirebaseCertificates(true);
        if($refreshed!==null){$keys=$refreshed['certs'];$cert=$keys[$head['kid']]??null;}
    }
    if(!$cert)out(401,['error'=>'Không tìm thấy khóa Firebase phù hợp với token (kid không khớp)']);
    $key=openssl_pkey_get_public($cert);
    if(!$key)out(500,['error'=>'Khóa chứng chỉ Firebase trong cache không hợp lệ']);
    $signatureResult=openssl_verify($parts[0].'.'.$parts[1],$signature,$key,OPENSSL_ALGO_SHA256);
    if($signatureResult!==1) {
        error_log('[FoodHub API] Firebase token signature verification failed; OpenSSL result='.$signatureResult);
        out(401,['error'=>'Chữ ký Firebase ID token không hợp lệ']);
    }
    $now=time();
    if(($claims['aud']??'')!==$project || ($claims['iss']??'')!=="https://securetoken.google.com/$project" || empty($claims['sub']) || ($claims['exp']??0)<=$now || ($claims['iat']??PHP_INT_MAX)>$now+60) out(401,['error'=>'Token hết hạn hoặc sai Firebase project']);
    return $claims;
}
function account(?PDO &$pdo,string $uid,bool $bypassCache=false): array {
    $cacheKey='acct-'.hash('sha256',$uid).'.json';
    if(!$bypassCache){
        $cached=foodhubReadJsonCache($cacheKey,120);
        if(is_array($cached)&&isset($cached['id'],$cached['role'],$cached['status']))return $cached;
    }
    $db=foodhubDb($pdo);
    $s=$db->prepare('SELECT * FROM users WHERE firebase_uid=? AND deleted_at IS NULL');$s->execute([$uid]);$row=$s->fetch();
    if(!$row) out(404,['error'=>'Chưa có hồ sơ MySQL. Hãy gọi POST /users/me sau đăng nhập.']);
    foodhubWriteJsonCache($cacheKey,$row);
    return $row;
}
function externalUser(array $r): array {
    $roles=['customer'=>'user','seller'=>'restaurant','driver'=>'shipper','admin'=>'admin'];
    return ['id'=>(string)$r['id'],'uid'=>$r['firebase_uid'],'fullName'=>$r['name'],'name'=>$r['name'],'email'=>$r['email']??'',
        'phoneNumber'=>$r['phone']??'','phone'=>$r['phone']??'','address'=>$r['address_line']??'','avatarUrl'=>$r['avatar_url']??'',
        'role'=>$roles[$r['role']]??'user','status'=>strtoupper($r['status']),'isAvailable'=>$r['status']==='active'];
}
function b64encode(string $value): string { return rtrim(strtr(base64_encode($value),'+/','-_'),'='); }
function fcmAccessToken(string $serviceAccountPath): ?array {
    if(!is_file($serviceAccountPath))return null;
    $account=json_decode((string)file_get_contents($serviceAccountPath),true);if(!is_array($account)||empty($account['private_key'])||empty($account['client_email']))return null;
    $cache=foodhubCacheFile('foodhub-fcm-token.json');$cached=is_file($cache)?json_decode((string)file_get_contents($cache),true):null;if(is_array($cached)&&($cached['expires']??0)>time()+60)return $cached;
    $now=time();$header=b64encode(json_encode(['alg'=>'RS256','typ'=>'JWT']));$claim=b64encode(json_encode(['iss'=>$account['client_email'],'scope'=>'https://www.googleapis.com/auth/firebase.messaging','aud'=>'https://oauth2.googleapis.com/token','iat'=>$now,'exp'=>$now+3500]));$unsigned=$header.'.'.$claim;
    if(!openssl_sign($unsigned,$signature,$account['private_key'],OPENSSL_ALGO_SHA256))return null;$assertion=$unsigned.'.'.b64encode($signature);
    $ctx=stream_context_create(['http'=>['method'=>'POST','timeout'=>8,'header'=>"Content-Type: application/x-www-form-urlencoded\r\n",'content'=>http_build_query(['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$assertion]),'ignore_errors'=>true]]);
    $response=@file_get_contents('https://oauth2.googleapis.com/token',false,$ctx);$json=$response?json_decode($response,true):null;if(!is_array($json)||empty($json['access_token']))return null;
    $result=['token'=>$json['access_token'],'expires'=>$now+(int)($json['expires_in']??3500)];@file_put_contents($cache,json_encode($result),LOCK_EX);return $result;
}
function notifyUser(PDO $pdo,array $config,int $userId,?int $orderId,string $type,string $title,string $message): void {
    notifyUsers($pdo,$config,[$userId],$orderId,$type,$title,$message);
}
function notifyUsers(PDO $pdo,array $config,array $userIds,?int $orderId,string $type,string $title,string $message): void {
    $userIds=array_values(array_unique(array_filter(array_map('intval',$userIds),static fn($id)=>$id>0)));
    if(!$userIds)return;
    try {
        $values=implode(',',array_fill(0,count($userIds),'(?,?,?,?,?)'));$params=[];
        foreach($userIds as $userId)array_push($params,$userId,$type,$title,$message,$orderId);
        $q=$pdo->prepare("INSERT INTO notifications(user_id,type,title,body,related_order_id) VALUES $values");$q->execute($params);
        $marks=implode(',',array_fill(0,count($userIds),'?'));$q=$pdo->prepare("SELECT fcm_token FROM user_device_tokens WHERE user_id IN ($marks)");$q->execute($userIds);$tokens=$q->fetchAll(PDO::FETCH_COLUMN);if(!$tokens)return;
        $auth=fcmAccessToken((string)($config['firebase_service_account_file']??''));if(!$auth)return;
        $project=(string)$config['firebase_project_id'];$url="https://fcm.googleapis.com/v1/projects/$project/messages:send";
        foreach($tokens as $token){$payload=['message'=>['token'=>$token,'notification'=>['title'=>$title,'body'=>$message],'data'=>['type'=>$type,'orderId'=>(string)($orderId??'')]]];$ctx=stream_context_create(['http'=>['method'=>'POST','timeout'=>5,'header'=>"Authorization: Bearer {$auth['token']}\r\nContent-Type: application/json\r\n",'content'=>json_encode($payload,JSON_UNESCAPED_UNICODE),'ignore_errors'=>true]]);@file_get_contents($url,false,$ctx);}
    } catch(Throwable $e) { error_log('FCM notification failed: '.$e->getMessage()); }
}
function payosRequest(array $config,string $path,string $method='GET',?array $payload=null): array {
    $payos=$config['payos']??[];$clientId=trim((string)($payos['client_id']??''));$apiKey=trim((string)($payos['api_key']??''));
    if($clientId===''||$apiKey==='')out(503,['error'=>'PayOS Client ID và API Key chưa được cấu hình trong config.local.php']);
    $headers="x-client-id: $clientId\r\nx-api-key: $apiKey\r\nAccept: application/json\r\n";$content='';
    if($payload!==null){$headers.="Content-Type: application/json\r\n";$content=json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);}
    $ctx=stream_context_create(['http'=>['method'=>$method,'timeout'=>15,'ignore_errors'=>true,'header'=>$headers,'content'=>$content]]);
    $raw=@file_get_contents('https://api-merchant.payos.vn'.$path,false,$ctx);$response=$raw?json_decode($raw,true):null;
    if(!is_array($response)){error_log('PayOS API request failed: '.$path);out(502,['error'=>'Không kết nối được PayOS']);}
    return $response;
}

$base=dirname(__DIR__);
require_once $base.'/database/connection.php';
require_once $base.'/runtime.php';
$configPath=is_file($base.'/config.local.php')?$base.'/config.local.php':(is_file($base.'/config.runtime.local.php')?$base.'/config.runtime.local.php':$base.'/config.example.php');
$config=require $configPath;
date_default_timezone_set((string)($config['timezone']??'Asia/Ho_Chi_Minh'));
$path=trim((string)parse_url($_SERVER['REQUEST_URI'],PHP_URL_PATH),'/');
$parts=array_values(array_filter(explode('/',preg_replace('#^.*?/api/?#','',$path))));
$routeResource=strtolower((string)($parts[0]??'root'));
$routeId=(string)($parts[1]??'');
$knownRouteIds=['me','mine','available','read-all','create-link'];
$routeLabel=$routeResource.($routeId!==''?('/'.(in_array($routeId,$knownRouteIds,true)?$routeId:'{id}')):'');
if(($parts[2]??'')==='messages')$routeLabel.='/messages';
$requestMethod=(string)($_SERVER['REQUEST_METHOD']??'GET');
FoodHubQueryMetrics::reset();
register_shutdown_function(static function() use ($foodhubRequestStartedAt,$requestMethod,$routeLabel): void {
    $elapsedMs=(hrtime(true)-$foodhubRequestStartedAt)/1_000_000;
    error_log(sprintf('[FoodHub request] method=%s route=/%s duration_ms=%.2f sql_queries=%d', $requestMethod, $routeLabel, $elapsedMs, FoodHubQueryMetrics::count()));
});
$pdo=null;
function foodhubDb(?PDO &$pdo): PDO {
    if($pdo instanceof PDO)return $pdo;
    global $config;
    try {
        $pdo=foodhubConnectDatabase($config['mysql'], true);
        return $pdo;
    } catch(Throwable $e) {
        out(503,['error'=>'Không kết nối được database. Kiểm tra host, cổng, tài khoản, mật khẩu và chứng chỉ TLS trong config.local.php.']);
    }
}
if(!$parts) { foodhubDb($pdo); out(200,['ok'=>true,'service'=>'FoodHub API','database'=>'connected']); }
if(($parts[0]??'')==='payos' && ($parts[1]??'')==='webhook' && $_SERVER['REQUEST_METHOD']==='POST') {
    foodhubDb($pdo);
    $checksum=trim((string)($config['payos']['checksum_key']??''));
    if($checksum==='')out(503,['error'=>'PayOS checksum key chưa được cấu hình']);
    $payload=json_decode((string)file_get_contents('php://input'),true);
    if(!is_array($payload)||!is_array($payload['data']??null)||!is_string($payload['signature']??null))out(400,['error'=>'PayOS webhook payload không hợp lệ']);
    $data=$payload['data'];ksort($data,SORT_STRING);$pairs=[];
    foreach($data as $key=>$value){if($value===null||$value==='null'||$value==='undefined')$value='';elseif(is_bool($value))$value=$value?'true':'false';elseif(is_array($value))$value=json_encode($value,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);$pairs[]=$key.'='.$value;}
    $expected=hash_hmac('sha256',implode('&',$pairs),$checksum);
    if(!hash_equals($expected,$payload['signature']))out(401,['error'=>'PayOS signature không hợp lệ']);
    if(($payload['success']??false)!==true||($data['code']??'')!=='00')out(200,['ok'=>true,'ignored'=>true]);
    $linkId=(string)($data['paymentLinkId']??'');$amount=(float)($data['amount']??0);$reference=(string)($data['reference']??'');
    if($linkId==='')out(422,['error'=>'Webhook thiếu paymentLinkId']);
    try{$q=$pdo->prepare('SELECT p.id,p.order_id,p.amount,p.status,o.total_amount,o.status AS order_status,o.customer_id,s.merchant_id,m.owner_user_id FROM payments p JOIN orders o ON o.id=p.order_id JOIN stores s ON s.id=o.store_id JOIN merchants m ON m.id=s.merchant_id WHERE p.payment_link_id=? AND p.provider=\'payos\' LIMIT 1');$q->execute([$linkId]);$payment=$q->fetch();if(!$payment)out(200,['ok'=>true,'ignored'=>true]);if(abs((float)$payment['amount']-$amount)>0.01||abs((float)$payment['total_amount']-$amount)>0.01)out(422,['error'=>'Số tiền webhook không khớp đơn hàng']);
        $payloadHash=hash('sha256',json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES));$eventId=$reference!==''?$reference:('payos-'.$linkId.'-'.$payloadHash);
        $pdo->beginTransaction();$pdo->prepare('INSERT IGNORE INTO payment_webhook_events(provider_event_id,payload_hash,event_type,processed_at,process_status) VALUES(?,?,?,?,?)')->execute([$eventId,$payloadHash,'payment.succeeded',date('Y-m-d H:i:s'),'processed']);
        $paymentUpdate=$pdo->prepare('UPDATE payments SET status=\'paid\',gateway_txn_id=?,paid_at=NOW() WHERE id=? AND status<>\'paid\'');$paymentUpdate->execute([$reference?:null,$payment['id']]);$newlyPaid=$paymentUpdate->rowCount()>0;
        if($newlyPaid){$pdo->prepare('UPDATE orders SET payment_status=\'paid\',status=IF(status=\'pending_payment\',\'pending\',status) WHERE id=?')->execute([$payment['order_id']]);if($payment['order_status']==='pending_payment')$pdo->prepare('INSERT INTO order_status_logs(order_id,status,note) VALUES(?,?,?)')->execute([$payment['order_id'],'pending','PayOS đã xác nhận thanh toán']);}
        $pdo->commit();if($newlyPaid){notifyUser($pdo,$config,(int)$payment['customer_id'],(int)$payment['order_id'],'payment','Thanh toán thành công','Thanh toán đơn hàng đã được PayOS xác nhận.');if($payment['owner_user_id'])notifyUser($pdo,$config,(int)$payment['owner_user_id'],(int)$payment['order_id'],'new_order','Đơn hàng đã thanh toán','Đơn hàng thanh toán online đã được xác nhận.');}out(200,['ok'=>true]);
    }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();error_log('PayOS webhook failed: '.$e->getMessage());out(500,['error'=>'Không xử lý được PayOS webhook']);}
}
$claims=verifyToken(bearer(),(string)$config['firebase_project_id']);
$uid=(string)$claims['sub'];$method=$_SERVER['REQUEST_METHOD'];
$resource=strtolower($parts[0]);$id=$parts[1]??null;
$GLOBALS['foodhub_resource']=$resource;

if($resource==='ai' && $id==='meal-advisor' && $method==='POST' && count($parts)===2) {
    foodhubDb($pdo);
    require $base.'/ai/meal-advisor-route.php';
}
if($resource==='ai' && $id==='order-drafts') {
    foodhubDb($pdo);
    require $base.'/ai/ai-order-draft-route.php';
}
if($resource==='ai' && in_array($id,['preferences','feedback','metrics'],true)) {
    foodhubDb($pdo);
    require $base.'/ai/ai-personalization-route.php';
}
if(($resource==='search' && $id==='history') || ($resource==='recommendations' && in_array($id,['personalized','preferences'],true))) {
    foodhubDb($pdo);
    require $base.'/recommendation/recommendation-route.php';
}

try {
    if($resource==='device-tokens' && $method==='POST'){$u=account($pdo,$uid);foodhubDb($pdo);$in=body();$token=trim((string)($in['token']??''));if($token==='')out(422,['error'=>'Thiếu FCM token']);$s=$pdo->prepare('INSERT INTO user_device_tokens(user_id,fcm_token,platform,last_seen_at) VALUES(?,?,?,NOW()) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),platform=VALUES(platform),last_seen_at=NOW()');$s->execute([$u['id'],$token,$in['platform']??'android']);out(200,['ok'=>true]);}
    if($resource==='conversations') {
        $me=account($pdo,$uid);foodhubDb($pdo);
        if(($parts[2]??'')==='messages' && $id) {
            $member=$pdo->prepare('SELECT 1 FROM conversation_participants cp JOIN conversations c ON c.id=cp.conversation_id WHERE cp.conversation_id=? AND cp.user_id=?');$member->execute([$id,$me['id']]);if(!$member->fetchColumn()&&$me['role']!=='admin')out(403,['error'=>'Bạn không thuộc cuộc trò chuyện này']);
            if($method==='GET'){[$limit,$offset]=foodhubPagination(50,200);$s=$pdo->prepare('SELECT m.id,m.content,m.image_url,m.message_type,m.created_at,m.is_seen,m.is_delivered,u.firebase_uid FROM messages m JOIN users u ON u.id=m.sender_user_id WHERE m.conversation_id=? AND m.deleted_at IS NULL ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?');$s->bindValue(1,(int)$id,PDO::PARAM_INT);$s->bindValue(2,$limit,PDO::PARAM_INT);$s->bindValue(3,$offset,PDO::PARAM_INT);$s->execute();$rows=[];foreach(array_reverse($s->fetchAll()) as $r)$rows[]=['messageId'=>(string)$r['id'],'senderId'=>$r['firebase_uid'],'receiverId'=>'','content'=>$r['content']??'','imageUrl'=>$r['image_url'],'type'=>strtoupper($r['message_type']),'createdAt'=>$r['created_at'],'seen'=>(bool)$r['is_seen'],'delivered'=>(bool)$r['is_delivered'],'deleted'=>false];out(200,['data'=>$rows]);}
            if($method==='POST'){$in=body();if(($in['action']??'')==='read'){$pdo->prepare('UPDATE conversation_participants SET last_read_at=NOW() WHERE conversation_id=? AND user_id=?')->execute([$id,$me['id']]);$pdo->prepare('UPDATE messages SET is_seen=1 WHERE conversation_id=? AND sender_user_id<>?')->execute([$id,$me['id']]);out(200,['ok'=>true]);}$type=strtoupper((string)($in['type']??'TEXT'))==='IMAGE'?'image':'text';$s=$pdo->prepare('INSERT INTO messages(conversation_id,sender_user_id,message_type,content,image_url,is_delivered) VALUES(?,?,?,?,?,1)');$s->execute([$id,$me['id'],$type,$in['content']??null,$in['imageUrl']??null]);$messageId=(string)$pdo->lastInsertId();$last=$type==='image'?'Đã gửi một ảnh':(string)($in['content']??'');$pdo->prepare('UPDATE conversations SET updated_at=NOW() WHERE id=?')->execute([$id]);$recipients=$pdo->prepare('SELECT user_id FROM conversation_participants WHERE conversation_id=? AND user_id<>?');$recipients->execute([$id,$me['id']]);notifyUsers($pdo,$config,array_map('intval',$recipients->fetchAll(PDO::FETCH_COLUMN)),null,'chat','Tin nhắn mới',$last);out(201,['id'=>$messageId,'data'=>['messageId'=>$messageId,'senderId'=>$uid,'content'=>$in['content']??'','imageUrl'=>$in['imageUrl']??null,'type'=>strtoupper($type),'createdAt'=>date('Y-m-d H:i:s'),'seen'=>false,'delivered'=>true,'lastMessage'=>$last]]);}
        }
        if($method==='GET' && !$id){
            [$limit,$offset]=foodhubPagination(100,200);
            $sql='SELECT c.id,c.order_id,c.type,c.created_at,c.updated_at FROM conversations c JOIN conversation_participants cp ON cp.conversation_id=c.id WHERE cp.user_id=?';$args=[$me['id']];
            if($me['role']==='admin'){$sql='SELECT DISTINCT c.id,c.order_id,c.type,c.created_at,c.updated_at FROM conversations c LEFT JOIN conversation_participants cp ON cp.conversation_id=c.id WHERE cp.user_id=? OR c.type=\'support\'';}
            $sql.=' ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?';$s=$pdo->prepare($sql);$s->bindValue(1,(int)$me['id'],PDO::PARAM_INT);$s->bindValue(2,$limit,PDO::PARAM_INT);$s->bindValue(3,$offset,PDO::PARAM_INT);$s->execute();$convs=$s->fetchAll();
            if(!$convs)out(200,['data'=>[]]);
            $convIds=array_map(static fn($c)=>(int)$c['id'],$convs);$marks=implode(',',array_fill(0,count($convIds),'?'));
            $partByConv=[];$myReadAt=[];
            $pq=$pdo->prepare("SELECT cp.conversation_id,cp.user_id,cp.last_read_at,u.firebase_uid,u.role FROM conversation_participants cp JOIN users u ON u.id=cp.user_id WHERE cp.conversation_id IN ($marks)");
            $pq->execute($convIds);
            foreach($pq as $pr){$cid=(int)$pr['conversation_id'];$partByConv[$cid][]=$pr;if((int)$pr['user_id']===(int)$me['id'])$myReadAt[$cid]=$pr['last_read_at'];}
            $lastByConv=[];
            $lq=$pdo->prepare("SELECT m.conversation_id,m.content,m.message_type,m.created_at FROM messages m JOIN (SELECT conversation_id,MAX(id) AS max_id FROM messages WHERE conversation_id IN ($marks) AND deleted_at IS NULL GROUP BY conversation_id) latest ON latest.max_id=m.id");
            $lq->execute($convIds);
            foreach($lq as $lr)$lastByConv[(int)$lr['conversation_id']]=$lr;
            $unreadByConv=[];
            $uq=$pdo->prepare("SELECT m.conversation_id,COUNT(*) AS cnt FROM messages m LEFT JOIN conversation_participants cp ON cp.conversation_id=m.conversation_id AND cp.user_id=? WHERE m.conversation_id IN ($marks) AND m.deleted_at IS NULL AND m.sender_user_id<>? AND (cp.last_read_at IS NULL OR m.created_at>cp.last_read_at) GROUP BY m.conversation_id");
            $uq->execute(array_merge([(int)$me['id']],$convIds,[(int)$me['id']]));
            foreach($uq as $ur)$unreadByConv[(int)$ur['conversation_id']]=(int)$ur['cnt'];
            $rows=[];
            foreach($convs as $c){
                $cid=(int)$c['id'];$participants=[];$roles=[];$unread=[];
                foreach($partByConv[$cid]??[] as $pr){$participants[]=$pr['firebase_uid'];$roles[$pr['firebase_uid']]=$pr['role'];$unread[$pr['firebase_uid']]=0;}
                $unread[$uid]=$unreadByConv[$cid]??0;$lm=$lastByConv[$cid]??null;
                $rows[]=['id'=>(string)$cid,'participants'=>$participants,'participantRoles'=>$roles,'orderId'=>$c['order_id']===null?null:(string)$c['order_id'],'type'=>strtoupper($c['type']),'lastMessage'=>$lm?($lm['message_type']==='image'?'Đã gửi một ảnh':($lm['content']??'')):'','lastMessageTime'=>$lm['created_at']??$c['created_at'],'createdAt'=>$c['created_at'],'unreadCount'=>$unread,'typing'=>(object)[]];
            }
            out(200,['data'=>$rows]);
        }
        if($method==='POST' && !$id){$in=body();$type=strtolower((string)($in['type']??'customer_store'));$type=$type==='support'?'support':(in_array($type,['customer_driver','customer_store'],true)?$type:'customer_store');$uids=array_values(array_unique(array_map('strval',$in['participants']??[$uid])));if(!in_array($uid,$uids,true))$uids[]=$uid;$pdo->beginTransaction();$storeIds=array_values(array_filter($uids,static fn($v)=>ctype_digit($v)));if($storeIds){$marks=implode(',',array_fill(0,count($storeIds),'?'));$storeOwner=$pdo->prepare("SELECT s.id,u.firebase_uid FROM stores s JOIN merchants m ON m.id=s.merchant_id JOIN users u ON u.id=m.owner_user_id WHERE s.id IN ($marks)");$storeOwner->execute(array_map('intval',$storeIds));$owners=[];foreach($storeOwner as $owner)$owners[(string)$owner['id']]=$owner['firebase_uid'];foreach($uids as $key=>$participantUid)if(isset($owners[$participantUid]))$uids[$key]=$owners[$participantUid];}$uids=array_values(array_unique($uids));$marks=implode(',',array_fill(0,count($uids),'?'));$find=$pdo->prepare("SELECT id FROM users WHERE firebase_uid IN ($marks) AND deleted_at IS NULL");$find->execute($uids);$participantIds=array_map('intval',$find->fetchAll(PDO::FETCH_COLUMN));$pdo->prepare('INSERT INTO conversations(order_id,type,created_by) VALUES(?,?,?)')->execute([isset($in['orderId'])&&$in['orderId']!==''?(int)$in['orderId']:null,$type,$me['id']]);$convId=(int)$pdo->lastInsertId();if($participantIds){$values=implode(',',array_fill(0,count($participantIds),'(?,?)'));$params=[];foreach($participantIds as $participantId){$params[]=$convId;$params[]=$participantId;}$add=$pdo->prepare("INSERT IGNORE INTO conversation_participants(conversation_id,user_id) VALUES $values");$add->execute($params);}$pdo->commit();out(201,['id'=>(string)$convId]);}
    }
    if($resource==='users' && $id!== 'me' && ($id!==null || $method==='GET')) {
        $viewer=account($pdo,$uid);foodhubDb($pdo);if($method==='GET' && $id!==null && $viewer['role']!=='admin'){$q=$pdo->prepare('SELECT 1 FROM conversation_participants a JOIN conversation_participants b ON b.conversation_id=a.conversation_id JOIN users u ON u.id=b.user_id WHERE a.user_id=? AND u.firebase_uid=? LIMIT 1');$q->execute([$viewer['id'],$id]);if(!$q->fetchColumn())out(403,['error'=>'Chỉ có thể xem thông tin người đang trò chuyện với bạn']);}
        elseif($viewer['role']!=='admin')out(403,['error'=>'Chỉ quản trị viên được quản lý tài khoản']);
        if($method==='GET'){if($id!==null){$q=$pdo->prepare('SELECT * FROM users WHERE firebase_uid=? AND deleted_at IS NULL');$q->execute([$id]);$user=$q->fetch();if(!$user)out(404,['error'=>'Không tìm thấy tài khoản']);out(200,['data'=>externalUser($user)]);}$q=$pdo->query('SELECT * FROM users WHERE deleted_at IS NULL ORDER BY created_at DESC');out(200,['data'=>array_map('externalUser',$q->fetchAll())]);}
        if($method==='PATCH'){$in=body();$status=isset($in['isAvailable'])?((bool)$in['isAvailable']?'active':'suspended'):null;$q=$pdo->prepare('UPDATE users SET name=COALESCE(?,name),email=COALESCE(?,email),phone=COALESCE(?,phone),role=COALESCE(?,role),status=COALESCE(?,status) WHERE firebase_uid=? AND deleted_at IS NULL');$role=isset($in['role'])?match(strtolower((string)$in['role'])){'restaurant','seller'=>'seller','shipper','driver'=>'driver','admin'=>'admin',default=>'customer'}:null;$q->execute([$in['name']??$in['fullName']??null,$in['email']??null,$in['phone']??$in['phoneNumber']??null,$role,$status,$id]);foodhubInvalidateJsonCache('acct-'.hash('sha256',(string)$id).'.json','catalog-stores.json','catalog-dishes.json');out(200,['ok'=>true]);}
        if($method==='DELETE'){$q=$pdo->prepare('UPDATE users SET status="suspended",deleted_at=NOW() WHERE firebase_uid=?');$q->execute([$id]);foodhubInvalidateJsonCache('acct-'.hash('sha256',(string)$id).'.json','catalog-stores.json','catalog-dishes.json');out(200,['ok'=>true]);}
    }
    if($resource==='users' && ($id==='me' || $id===null)) {
        if($method==='GET') { $u=account($pdo,$uid);out(200,['data'=>externalUser($u)]); }
        if($method==='POST' || $method==='PUT' || $method==='PATCH') {
            foodhubDb($pdo);
            $in=body();$exists=$pdo->prepare('SELECT id FROM users WHERE firebase_uid=?');$exists->execute([$uid]);$old=$exists->fetch();
            $fullName=trim((string)($in['fullName']??$in['name']??''));
            $email=trim((string)($claims['email']??$in['email']??''));$phone=trim((string)($in['phoneNumber']??$in['phone']??''));
            $verifiedEmail=trim((string)($claims['email']??''));
            if(!$old && $verifiedEmail!=='' && ($claims['email_verified']??false)===true) {
                // A Firebase project change can issue a new UID for the same verified Google email.
                // Reattach that identity to the existing MySQL row instead of creating a duplicate profile.
                $legacy=$pdo->prepare('SELECT id FROM users WHERE email=? AND deleted_at IS NULL LIMIT 1');
                $legacy->execute([$verifiedEmail]);
                $legacyId=$legacy->fetchColumn();
                if($legacyId!==false) {
                    $relink=$pdo->prepare('UPDATE users SET firebase_uid=? WHERE id=? AND deleted_at IS NULL');
                    $relink->execute([$uid,$legacyId]);
                    $exists->execute([$uid]);$old=$exists->fetch();
                }
            }
            if(!$old) {
                $requested=strtolower(trim((string)($in['role']??'customer')));
                $role=match($requested){'restaurant','seller','nhà hàng'=>'seller','shipper','driver'=>'driver',default=>'customer'};
                $s=$pdo->prepare('INSERT INTO users(firebase_uid,name,email,phone,role,avatar_url) VALUES(?,?,?,?,?,?)');
                $s->execute([$uid,$fullName,$email?:null,$phone?:null,$role,$in['avatarUrl']??null]);
            } else {
                $s=$pdo->prepare('UPDATE users SET name=COALESCE(NULLIF(?,\'\'),name),email=COALESCE(NULLIF(?,\'\'),email),phone=COALESCE(NULLIF(?,\'\'),phone),address_line=COALESCE(?,address_line),avatar_url=COALESCE(?,avatar_url),last_login_at=NOW() WHERE firebase_uid=?');
                $s->execute([$fullName,$email,$phone,$in['address']??null,$in['avatarUrl']??null,$uid]);
            }
            out(200,['data'=>externalUser(account($pdo,$uid,true))]);
        }
    }
    if($resource==='addresses') {
        $u=account($pdo,$uid);$userId=(int)$u['id'];$addrCache='addrs-'.hash('sha256',$uid).'.json';
        if($method==='GET') {
            $cachedAddrs=foodhubReadJsonCache($addrCache,120);
            if(is_array($cachedAddrs))out(200,['data'=>$cachedAddrs]);
            foodhubDb($pdo);
            $s=$pdo->prepare('SELECT * FROM user_addresses WHERE user_id=? ORDER BY is_default DESC,updated_at DESC');$s->execute([$userId]);
            $rows=array_map(static fn($r)=>['id'=>(string)$r['id'],'label'=>$r['label'],'name'=>$r['receiver_name'],'receiverName'=>$r['receiver_name'],'phone'=>$r['receiver_phone'],'receiverPhone'=>$r['receiver_phone'],'address'=>$r['address_line'],'addressDetail'=>$r['address_line'],'ward'=>$r['ward'],'district'=>$r['district'],'city'=>$r['city'],'latitude'=>$r['latitude']===null?null:(float)$r['latitude'],'longitude'=>$r['longitude']===null?null:(float)$r['longitude'],'isDefault'=>(bool)$r['is_default']],$s->fetchAll());
            foodhubWriteJsonCache($addrCache,$rows);
            out(200,['data'=>$rows]);
        }
        foodhubDb($pdo);
        if(in_array($method,['POST','PUT','PATCH'],true)) {
            $in=body();$label=(string)($in['label']??$in['name']??'Địa chỉ');$receiver=(string)($in['receiverName']??$in['name']??'');$phone=(string)($in['receiverPhone']??$in['phone']??'');$address=(string)($in['address']??$in['addressDetail']??'');
            $lat=$in['latitude']??null;$lng=$in['longitude']??null;$default=(int)(bool)($in['isDefault']??false);
            $pdo->beginTransaction();if($default)$pdo->prepare('UPDATE user_addresses SET is_default=0 WHERE user_id=?')->execute([$userId]);
            if($id && ctype_digit($id)){$s=$pdo->prepare('UPDATE user_addresses SET label=?,receiver_name=?,receiver_phone=?,address_line=?,latitude=?,longitude=?,is_default=? WHERE id=? AND user_id=?');$s->execute([$label,$receiver,$phone,$address,$lat,$lng,$default,$id,$userId]);$newId=(int)$id;}
            else{$s=$pdo->prepare('INSERT INTO user_addresses(user_id,label,receiver_name,receiver_phone,address_line,latitude,longitude,is_default) VALUES(?,?,?,?,?,?,?,?)');$s->execute([$userId,$label,$receiver,$phone,$address,$lat,$lng,$default]);$newId=(int)$pdo->lastInsertId();}
            $pdo->commit();foodhubInvalidateJsonCache($addrCache);out(200,['id'=>(string)$newId]);
        }
        if($method==='DELETE' && $id){$s=$pdo->prepare('DELETE FROM user_addresses WHERE id=? AND user_id=?');$s->execute([$id,$userId]);foodhubInvalidateJsonCache($addrCache);out(200,['ok'=>true]);}
    }
    if($resource==='stores') {
        if($id==='mine') {
            $u=account($pdo,$uid);foodhubDb($pdo);
            $storeFields='s.id,s.merchant_id,s.name,s.description,s.address_line,s.phone,s.email,s.open_time,s.close_time,s.cover_url,s.logo_url,s.is_open,s.order_notify,s.sound_notify,s.push_notify,s.email_notify,s.promo_notify,s.latitude,s.longitude,s.rating_avg,s.avg_prep_minutes';$owned=$pdo->prepare("SELECT $storeFields FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=? AND s.deleted_at IS NULL ORDER BY s.id LIMIT 1");$owned->execute([$u['id']]);$store=$owned->fetch();
            if(!$store && $method==='POST') {
                if(!in_array($u['role'],['seller','admin'],true))out(403,['error'=>'Tài khoản chưa có quyền quản lý cửa hàng']);$in=body();$pdo->beginTransaction();
                $q=$pdo->prepare('SELECT id FROM merchants WHERE owner_user_id=? LIMIT 1');$q->execute([$u['id']]);$merchantId=$q->fetchColumn();
                if(!$merchantId){$q=$pdo->prepare('INSERT INTO merchants(owner_user_id,brand_name,hotline,status) VALUES(?,?,?,?)');$q->execute([$u['id'],$in['name']??'Thương hiệu mới',$in['phone']??null,'active']);$merchantId=$pdo->lastInsertId();}
                $q=$pdo->prepare('INSERT INTO stores(merchant_id,name,description,address_line,phone,email,open_time,close_time,cover_url,logo_url,status) VALUES(?,?,?,?,?,?,?,?,?,?,?)');$q->execute([$merchantId,$in['name']??'Cửa hàng của tôi',$in['description']??null,$in['address']??null,$in['phone']??null,$in['email']??$u['email'],$in['openTime']??'08:00',$in['closeTime']??'22:00',$in['coverImage']??null,$in['avatarUrl']??null,'active']);$storeId=(int)$pdo->lastInsertId();$pdo->prepare('INSERT INTO store_staff(store_id,user_id,staff_role) VALUES(?,?,?)')->execute([$storeId,$u['id'],'owner']);$pdo->commit();foodhubInvalidateJsonCache('catalog-stores.json','catalog-dishes.json','catalog-categories');$q=$pdo->prepare("SELECT $storeFields FROM stores s WHERE s.id=?");$q->execute([$storeId]);$store=$q->fetch();
            }
            if(!$store)out(404,['error'=>'Tài khoản chưa có cửa hàng']);
            if(in_array($method,['PATCH','PUT'],true)){$in=body();$q=$pdo->prepare('UPDATE stores SET name=?,description=?,address_line=?,phone=?,email=?,open_time=?,close_time=?,cover_url=?,logo_url=?,is_open=?,order_notify=?,sound_notify=?,push_notify=?,email_notify=?,promo_notify=?,latitude=?,longitude=? WHERE id=?');$q->execute([$in['name']??$store['name'],$in['description']??$store['description'],$in['address']??$store['address_line'],$in['phone']??$store['phone'],$in['email']??$store['email'],$in['openTime']??$store['open_time'],$in['closeTime']??$store['close_time'],$in['coverImage']??$store['cover_url'],$in['avatarUrl']??$store['logo_url'],isset($in['isOpen'])?(int)(bool)$in['isOpen']:(int)$store['is_open'],isset($in['orderNotify'])?(int)(bool)$in['orderNotify']:(int)$store['order_notify'],isset($in['soundNotify'])?(int)(bool)$in['soundNotify']:(int)$store['sound_notify'],isset($in['pushNotify'])?(int)(bool)$in['pushNotify']:(int)$store['push_notify'],isset($in['emailNotify'])?(int)(bool)$in['emailNotify']:(int)$store['email_notify'],isset($in['promoNotify'])?(int)(bool)$in['promoNotify']:(int)$store['promo_notify'],$in['latitude']??$store['latitude'],$in['longitude']??$store['longitude'],$store['id']]);foodhubInvalidateJsonCache('catalog-stores.json','catalog-dishes.json','catalog-categories');$q=$pdo->prepare("SELECT $storeFields FROM stores s WHERE s.id=?");$q->execute([$store['id']]);$store=$q->fetch();}
            $data=['id'=>(string)$store['id'],'name'=>$store['name'],'rating'=>(float)$store['rating_avg'],'address'=>$store['address_line']??'','deliveryTime'=>(string)($store['avg_prep_minutes']??30),'distance'=>'','description'=>$store['description']??'','coverImage'=>$store['cover_url']??'','avatarUrl'=>$store['logo_url']??'','email'=>$store['email']??'','phone'=>$store['phone']??'','openTime'=>$store['open_time']??'','closeTime'=>$store['close_time']??'','orderNotify'=>(bool)$store['order_notify'],'soundNotify'=>(bool)$store['sound_notify'],'pushNotify'=>(bool)$store['push_notify'],'emailNotify'=>(bool)$store['email_notify'],'promoNotify'=>(bool)$store['promo_notify'],'latitude'=>$store['latitude']===null?null:(float)$store['latitude'],'longitude'=>$store['longitude']===null?null:(float)$store['longitude'],'isOpen'=>(bool)$store['is_open']];out(200,['data'=>$data]);
        }
        if($method==='GET'){
            $uLat=isset($_GET['lat'])&&is_numeric($_GET['lat'])?(float)$_GET['lat']:null;$uLng=isset($_GET['lng'])&&is_numeric($_GET['lng'])?(float)$_GET['lng']:null;
            [$limit,$offset]=foodhubPagination(100,200);
            $storeFields='s.id,s.name,s.rating_avg,s.address_line,s.avg_prep_minutes,s.latitude,s.longitude,s.description,s.cover_url,s.logo_url,s.email,s.phone,s.open_time,s.close_time,s.is_open';
            if(!$id){
                $storePageCache='catalog-stores.json-page-'.$limit.'-'.$offset.'.json';$storePage=foodhubReadJsonCache($storePageCache,90);
                if(!is_array($storePage)){foodhubDb($pdo);$s=$pdo->prepare("SELECT $storeFields FROM stores s WHERE s.status='active' AND s.deleted_at IS NULL ORDER BY s.name,s.id LIMIT ? OFFSET ?");$s->bindValue(1,$limit,PDO::PARAM_INT);$s->bindValue(2,$offset,PDO::PARAM_INT);$s->execute();$storePage=$s->fetchAll();foodhubWriteJsonCache($storePageCache,$storePage);}
                $rows=[];foreach($storePage as $r){$sLat=$r['latitude']===null?null:(float)$r['latitude'];$sLng=$r['longitude']===null?null:(float)$r['longitude'];$km=foodhubDistanceKm($sLat,$sLng,$uLat,$uLng);$prep=$r['avg_prep_minutes']===null?null:(int)$r['avg_prep_minutes'];$rows[]=['id'=>(string)$r['id'],'name'=>$r['name'],'rating'=>(float)$r['rating_avg'],'address'=>$r['address_line']??'','deliveryTime'=>$km!==null?foodhubDeliveryEstimate($km,$prep):(string)($r['avg_prep_minutes']??15),'distance'=>$km!==null?number_format($km,1,'.','').' km':'','distanceKm'=>$km,'shippingFee'=>foodhubShippingFee($km,(float)($config['delivery_fee']??15000)),'description'=>$r['description']??'','coverImage'=>$r['cover_url']??'','avatarUrl'=>$r['logo_url']??'','email'=>$r['email']??'','phone'=>$r['phone']??'','openTime'=>$r['open_time']??'','closeTime'=>$r['close_time']??'','latitude'=>$sLat,'longitude'=>$sLng];}
                out(200,['data'=>$rows]);
            }
            foodhubDb($pdo);$s=$pdo->prepare("SELECT $storeFields FROM stores s WHERE s.id=? AND s.deleted_at IS NULL");$s->execute([$id]);$r=$s->fetch();
            if(!$r)out(404,['error'=>'Không tìm thấy cửa hàng']);
            $sLat=$r['latitude']===null?null:(float)$r['latitude'];$sLng=$r['longitude']===null?null:(float)$r['longitude'];$km=foodhubDistanceKm($sLat,$sLng,$uLat,$uLng);$prep=$r['avg_prep_minutes']===null?null:(int)$r['avg_prep_minutes'];
            out(200,['data'=>['id'=>(string)$r['id'],'name'=>$r['name'],'rating'=>(float)$r['rating_avg'],'address'=>$r['address_line']??'','deliveryTime'=>$km!==null?foodhubDeliveryEstimate($km,$prep):(string)($r['avg_prep_minutes']??15),'distance'=>$km!==null?number_format($km,1,'.','').' km':'','distanceKm'=>$km,'shippingFee'=>foodhubShippingFee($km,(float)($config['delivery_fee']??15000)),'description'=>$r['description']??'','coverImage'=>$r['cover_url']??'','avatarUrl'=>$r['logo_url']??'','email'=>$r['email']??'','phone'=>$r['phone']??'','openTime'=>$r['open_time']??'','closeTime'=>$r['close_time']??'','latitude'=>$sLat,'longitude'=>$sLng,'isOpen'=>(bool)$r['is_open']]]);
        }
    }
    if($resource==='dishes') {
        if($method==='DELETE') {
            $u=account($pdo,$uid);foodhubDb($pdo);if(!$id||!ctype_digit($id))out(400,['error'=>'ID món không hợp lệ']);
            $q=$pdo->prepare('SELECT d.store_id,m.owner_user_id FROM dishes d JOIN stores s ON s.id=d.store_id JOIN merchants m ON m.id=s.merchant_id WHERE d.id=? AND d.deleted_at IS NULL');$q->execute([$id]);$dish=$q->fetch();
            if(!$dish)out(404,['error'=>'Không tìm thấy món']);
            if($u['role']!=='admin' && (int)$dish['owner_user_id']!==(int)$u['id'])out(403,['error'=>'Bạn không quản lý cửa hàng này']);
            $pdo->prepare('UPDATE dishes SET deleted_at=NOW(),is_available=0 WHERE id=?')->execute([$id]);
            foodhubInvalidateJsonCache('catalog-dishes.json','catalog-categories');
            out(200,['ok'=>true]);
        }
        if($method==='GET'){
            $includeUnavailable=($_GET['includeUnavailable']??'0')==='1';
            [$limit,$offset]=foodhubPagination(100,200);
            if(!$includeUnavailable){
                if($id!==null){
                    if(!ctype_digit($id))out(400,['error'=>'ID món không hợp lệ']);
                    foodhubDb($pdo);
                    $q=$pdo->prepare("SELECT d.id,d.name,d.price,d.image_url,d.menu_category_id,d.platform_category_id,d.description,d.is_available,d.store_id,d.rating_avg,d.rating_count,mc.name AS menu_category_name,s.name AS restaurant_name,s.address_line AS restaurant_address,s.logo_url AS restaurant_logo,s.is_open AS restaurant_open FROM dishes d JOIN stores s ON s.id=d.store_id LEFT JOIN menu_categories mc ON mc.id=d.menu_category_id WHERE d.id=? AND d.deleted_at IS NULL AND s.status='active' LIMIT 1");
                    $q->execute([$id]);$r=$q->fetch();
                    if(!$r)out(404,['error'=>'Không tìm thấy món ăn']);
                    out(200,['data'=>['id'=>(string)$r['id'],'name'=>$r['name'],'price'=>(float)$r['price'],'imageUrl'=>$r['image_url']??'','category'=>$r['menu_category_name']??(string)($r['platform_category_id']??''),'description'=>$r['description']??'','available'=>(bool)$r['is_available'],'restaurantId'=>(string)$r['store_id'],'restaurantName'=>$r['restaurant_name']??'','restaurantAddress'=>$r['restaurant_address']??'','restaurantLogo'=>$r['restaurant_logo']??'','restaurantOpen'=>(bool)$r['restaurant_open'],'ratingAvg'=>(float)($r['rating_avg']??0),'ratingCount'=>(int)($r['rating_count']??0)]]);
                }
                $storeId=(int)($_GET['restaurantId']??0);
                $catParam=isset($_GET['category'])?trim((string)$_GET['category']):'';
                $sql="SELECT d.id,d.name,d.price,d.image_url,d.menu_category_id,d.platform_category_id,d.description,d.is_available,d.store_id,mc.name AS menu_category_name FROM dishes d JOIN stores s ON s.id=d.store_id LEFT JOIN menu_categories mc ON mc.id=d.menu_category_id WHERE d.deleted_at IS NULL AND s.status='active' AND d.is_available=1";$params=[];
                if($storeId){$sql.=' AND d.store_id=?';$params[]=$storeId;}
                if($catParam!==''){if(ctype_digit($catParam)){$sql.=' AND (d.platform_category_id=? OR d.menu_category_id=?)';$params[]=(int)$catParam;$params[]=(int)$catParam;}else{$sql.=' AND LOWER(mc.name)=LOWER(?)';$params[]=$catParam;}}
                $sql.=' ORDER BY d.created_at DESC,d.id DESC LIMIT ? OFFSET ?';$dishPageCache='catalog-dishes.json-page-'.hash('sha256',serialize([$storeId,$catParam,$limit,$offset])).'.json';$dishPage=foodhubReadJsonCache($dishPageCache,90);
                if(!is_array($dishPage)){foodhubDb($pdo);$q=$pdo->prepare($sql);foreach($params as $i=>$param)$q->bindValue($i+1,$param,is_int($param)?PDO::PARAM_INT:PDO::PARAM_STR);$q->bindValue(count($params)+1,$limit,PDO::PARAM_INT);$q->bindValue(count($params)+2,$offset,PDO::PARAM_INT);$q->execute();$dishPage=$q->fetchAll();foodhubWriteJsonCache($dishPageCache,$dishPage);}
                $rows=[];foreach($dishPage as $r)$rows[]=['id'=>(string)$r['id'],'name'=>$r['name'],'price'=>(float)$r['price'],'imageUrl'=>$r['image_url']??'','category'=>$r['menu_category_name']??(string)($r['platform_category_id']??''),'description'=>$r['description']??'','available'=>(bool)$r['is_available'],'restaurantId'=>(string)$r['store_id']];
                out(200,['data'=>$rows]);
            }
            $storeId=(int)($_GET['restaurantId']??0);
            $u=account($pdo,$uid);foodhubDb($pdo);$owner=$pdo->prepare('SELECT 1 FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=? AND (?=1 OR m.owner_user_id=?)');$owner->execute([$storeId,$u['role']==='admin'?1:0,$u['id']]);if(!$owner->fetchColumn())out(403,['error'=>'Bạn không quản lý cửa hàng này']);
            $sql="SELECT d.id,d.name,d.price,d.image_url,d.platform_category_id,d.description,d.is_available,d.store_id,d.created_at,mc.name AS menu_category_name FROM dishes d JOIN stores s ON s.id=d.store_id LEFT JOIN menu_categories mc ON mc.id=d.menu_category_id WHERE d.deleted_at IS NULL AND s.status='active'";
            $params=[];if($storeId){$sql.=' AND d.store_id=?';$params[]=$storeId;}
            if(isset($_GET['category']) && trim((string)$_GET['category'])!==''){$catParam=trim((string)$_GET['category']);if(ctype_digit($catParam)){$sql.=' AND (d.platform_category_id=? OR d.menu_category_id=?)';$params[]=(int)$catParam;$params[]=(int)$catParam;}else{$sql.=' AND LOWER(mc.name)=LOWER(?)';$params[]=$catParam;}}
            $sql.=' ORDER BY d.created_at DESC,d.id DESC LIMIT ? OFFSET ?';$s=$pdo->prepare($sql);foreach($params as $i=>$param)$s->bindValue($i+1,$param,is_int($param)?PDO::PARAM_INT:PDO::PARAM_STR);$s->bindValue(count($params)+1,$limit,PDO::PARAM_INT);$s->bindValue(count($params)+2,$offset,PDO::PARAM_INT);$s->execute();$rows=[];
            foreach($s as $r)$rows[]=['id'=>(string)$r['id'],'name'=>$r['name'],'price'=>(float)$r['price'],'imageUrl'=>$r['image_url']??'','category'=>$r['menu_category_name']??(string)($r['platform_category_id']??''),'description'=>$r['description']??'','available'=>(bool)$r['is_available'],'restaurantId'=>(string)$r['store_id']];out(200,['data'=>$rows]);
        }
        if(in_array($method,['POST','PUT','PATCH'],true)) {
            $in=body();$storeId=(int)($in['restaurantId']??0);$u=account($pdo,$uid);foodhubDb($pdo);
            if($u['role']!=='seller' && $u['role']!=='admin')out(403,['error'=>'Chỉ tài khoản cửa hàng được quản lý món']);
            $owner=$pdo->prepare('SELECT 1 FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=? AND (?=1 OR m.owner_user_id=?)');$owner->execute([$storeId,$u['role']==='admin'?1:0,$u['id']]);if(!$owner->fetchColumn())out(403,['error'=>'Bạn không quản lý cửa hàng này']);
            $catId=null;$menuCatId=null;$cat=mb_substr(trim((string)preg_replace('/\s+/u',' ',(string)($in['category']??''))),0,120,'UTF-8');
            if($cat===''){$cat='Món chính';}
            if(ctype_digit($cat)){$catId=(int)$cat;}
            else{$q=$pdo->prepare('SELECT id FROM menu_categories WHERE store_id=? AND LOWER(name)=LOWER(?) LIMIT 1');$q->execute([$storeId,$cat]);$menuCatId=$q->fetchColumn();if(!$menuCatId){$q=$pdo->prepare('INSERT INTO menu_categories(store_id,name) VALUES(?,?)');$q->execute([$storeId,$cat]);$menuCatId=$pdo->lastInsertId();}}
            $name=trim((string)($in['name']??''));$price=max(0,(float)($in['price']??0));$image=$in['imageUrl']??null;$desc=$in['description']??null;$available=(int)(bool)($in['available']??true);
            if($method==='POST'){$q=$pdo->prepare('INSERT INTO dishes(store_id,platform_category_id,menu_category_id,name,description,image_url,price,is_available) VALUES(?,?,?,?,?,?,?,?)');$q->execute([$storeId,$catId,$menuCatId,$name,$desc,$image,$price,$available]);foodhubInvalidateJsonCache('catalog-dishes.json','catalog-categories');out(201,['id'=>(string)$pdo->lastInsertId()]);}
            if(!$id||!ctype_digit($id))out(400,['error'=>'ID món không hợp lệ']);
            $q=$pdo->prepare('UPDATE dishes SET name=?,platform_category_id=?,menu_category_id=?,description=?,image_url=?,price=?,is_available=? WHERE id=? AND store_id=?');$q->execute([$name,$catId,$menuCatId,$desc,$image,$price,$available,$id,$storeId]);foodhubInvalidateJsonCache('catalog-dishes.json','catalog-categories');out(200,['ok'=>true]);
        }
    }
    if($resource==='categories' && $method==='GET') {
        $storeId=(int)($_GET['restaurantId']??0);
        [$limit,$offset]=foodhubPagination(100,200);
        $catCache='catalog-categories-'.$storeId.'-'.$limit.'-'.$offset.'.json';
        $cachedCats=foodhubReadJsonCache($catCache,90);
        if(is_array($cachedCats))out(200,['data'=>array_slice($cachedCats,$offset,$limit)]);
        foodhubDb($pdo);
        $sql="SELECT mc.name,COUNT(d.id) AS dish_count FROM menu_categories mc JOIN stores s ON s.id=mc.store_id LEFT JOIN dishes d ON d.menu_category_id=mc.id AND d.deleted_at IS NULL AND d.is_available=1 WHERE mc.is_active=1 AND s.deleted_at IS NULL AND s.status='active'";
        $params=[];if($storeId){$sql.=' AND mc.store_id=?';$params[]=$storeId;}
        $sql.=' GROUP BY mc.name ORDER BY dish_count DESC,mc.name ASC LIMIT ? OFFSET ?';
        $q=$pdo->prepare($sql);foreach($params as $i=>$param)$q->bindValue($i+1,$param,PDO::PARAM_INT);$q->bindValue(count($params)+1,$limit,PDO::PARAM_INT);$q->bindValue(count($params)+2,$offset,PDO::PARAM_INT);$q->execute();
        $rows=[];foreach($q as $r)$rows[]=['name'=>$r['name'],'dishCount'=>(int)$r['dish_count']];
        foodhubWriteJsonCache($catCache,$rows);
        out(200,['data'=>$rows]);
    }
    if($resource==='promotions' || $resource==='vouchers') {
        $u=account($pdo,$uid);
        if($method==='GET'){
            if($u['role']!=='admin'){
                $cachedVouchers=foodhubReadJsonCache('catalog-vouchers.json',60);
                if(is_array($cachedVouchers))out(200,['data'=>$cachedVouchers]);
            }
            foodhubDb($pdo);
            $sql='SELECT * FROM vouchers';if($u['role']!=='admin')$sql.=' WHERE is_active=1 AND (starts_at IS NULL OR starts_at<=NOW()) AND (ends_at IS NULL OR ends_at>=NOW())';$sql.=' ORDER BY created_at DESC';$s=$pdo->query($sql);$rows=[];foreach($s as $r)$rows[]=['id'=>(string)$r['id'],'code'=>$r['code'],'imageUrl'=>$r['image_url']??'','type'=>$r['discount_type'],'value'=>(float)$r['discount_value'],'maxDiscount'=>(float)($r['max_discount']??0),'minOrderValue'=>(float)$r['min_order_amount'],'usageCount'=>(int)$r['used_count'],'usageLimit'=>(int)($r['usage_limit']??0),'description'=>$r['description']??'','title'=>$r['title'],'status'=>$r['is_active']?'active':'expired','startDate'=>$r['starts_at'],'endDate'=>$r['ends_at']];
            if($u['role']!=='admin')foodhubWriteJsonCache('catalog-vouchers.json',$rows);
            out(200,['data'=>$rows]);
        }
        foodhubDb($pdo);
        if($u['role']!=='admin')out(403,['error'=>'Chỉ admin được quản lý voucher']);
        if($method==='POST'){$in=body();$q=$pdo->prepare('INSERT INTO vouchers(store_id,code,title,description,discount_type,discount_value,max_discount,min_order_amount,usage_limit,used_count,starts_at,ends_at,is_active) VALUES(?,?,?,?,?,?,?,?,?,0,?,?,?)');$q->execute([isset($in['storeId'])?(int)$in['storeId']:null,$in['code']??'', $in['title']??'', $in['description']??null,$in['type']??'percentage',(float)($in['value']??0),$in['maxDiscount']??null,(float)($in['minOrderValue']??0),isset($in['usageLimit'])?(int)$in['usageLimit']:null,$in['startDate']??null,$in['endDate']??null,($in['status']??'active')==='active'?1:0]);foodhubInvalidateJsonCache('catalog-vouchers.json');out(201,['id'=>(string)$pdo->lastInsertId()]);}
        if($method==='PATCH' && $id){$in=body();if(isset($in['status'])){$q=$pdo->prepare('UPDATE vouchers SET is_active=? WHERE id=?');$q->execute([$in['status']==='active'?1:0,$id]);}foodhubInvalidateJsonCache('catalog-vouchers.json');out(200,['ok'=>true]);}
        if($method==='DELETE' && $id){$q=$pdo->prepare('DELETE FROM vouchers WHERE id=?');$q->execute([$id]);foodhubInvalidateJsonCache('catalog-vouchers.json');out(200,['ok'=>true]);}
    }
    foodhubDb($pdo);
    if($resource==='activity_logs') {
        $u=account($pdo,$uid);
        if($method==='POST'){$in=body();$storeId=isset($in['storeId'])?(int)$in['storeId']:null;if($storeId===null){$q=$pdo->prepare('SELECT s.id FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=? ORDER BY s.id LIMIT 1');$q->execute([$u['id']]);$storeId=$q->fetchColumn()?:null;}$q=$pdo->prepare('INSERT INTO activity_logs(actor_user_id,store_id,type,title,description,details) VALUES(?,?,?,?,?,?)');$q->execute([$u['id'],$storeId,$in['type']??'general',$in['title']??'Hoạt động',$in['description']??null,$in['details']??null]);out(201,['id'=>(string)$pdo->lastInsertId()]);}
        if($method==='GET'){$sql='SELECT a.* FROM activity_logs a';$args=[];if($u['role']==='seller'){$sql.=' JOIN stores s ON s.id=a.store_id JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=?';$args[]=$u['id'];}elseif($u['role']!=='admin'){$sql.=' WHERE a.actor_user_id=?';$args[]=$u['id'];}$sql.=' ORDER BY a.created_at DESC LIMIT 200';$q=$pdo->prepare($sql);$q->execute($args);$rows=[];foreach($q as $r)$rows[]=['id'=>(string)$r['id'],'title'=>$r['title'],'description'=>$r['description']??'','details'=>$r['details']??'','type'=>$r['type'],'timestamp'=>strtotime($r['created_at'])*1000,'imageUrl'=>''];out(200,['data'=>$rows]);}
    }
    if($resource==='payments') {
        $u=account($pdo,$uid);
        if($method==='POST' && $id==='create-link'){$in=body();$checksum=trim((string)($config['payos']['checksum_key']??''));if($checksum==='')out(503,['error'=>'PayOS Checksum Key chưa được cấu hình']);$orderId=(int)($in['orderId']??0);$q=$pdo->prepare('SELECT p.id,p.amount,p.status,o.total_amount,o.status AS order_status FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.order_id=? AND o.customer_id=? AND o.payment_method=\'payos\' LIMIT 1');$q->execute([$orderId,$u['id']]);$payment=$q->fetch();if(!$payment)out(404,['error'=>'Không tìm thấy đơn thanh toán PayOS']);if(abs((float)$payment['amount']-(float)$payment['total_amount'])>0.01)out(409,['error'=>'Số tiền phiên thanh toán không khớp đơn']);if($payment['status']==='paid')out(409,['error'=>'Đơn hàng đã thanh toán']);
            $amount=(int)round((float)$payment['amount']);$orderCode=random_int(100000000,2147483647);$description='FH'.(string)$orderId;$returnUrl='dacs3://payment_callback?orderId='.$orderId.'&status=PAID';$cancelUrl='dacs3://payment_callback?orderId='.$orderId.'&status=CANCELLED';$signed="amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl";$signature=hash_hmac('sha256',$signed,$checksum);$payload=['orderCode'=>$orderCode,'amount'=>$amount,'description'=>$description,'cancelUrl'=>$cancelUrl,'returnUrl'=>$returnUrl,'signature'=>$signature];$response=payosRequest($config,'/v2/payment-requests','POST',$payload);$result=$response['data']??null;if(($response['code']??'')!=='00'||!is_array($result)||empty($result['checkoutUrl'])||empty($result['paymentLinkId'])){error_log('PayOS create-link rejected: '.($response['desc']??'unknown'));out(502,['error'=>'PayOS không tạo được link thanh toán']);}$q=$pdo->prepare('UPDATE payments SET payos_order_code=?,payment_link_id=?,checkout_url=?,status=\'processing\' WHERE id=?');$q->execute([$orderCode,$result['paymentLinkId'],$result['checkoutUrl'],$payment['id']]);out(200,['checkoutUrl'=>$result['checkoutUrl'],'paymentLinkId'=>$result['paymentLinkId'],'qrCode'=>$result['qrCode']??'']);}
        if($method==='GET'){$q=$pdo->prepare('SELECT p.*,o.code,o.customer_id FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.customer_id=? ORDER BY p.created_at DESC LIMIT 200');$q->execute([$u['id']]);$rows=[];foreach($q as $r)$rows[]=['id'=>(string)$r['id'],'userId'=>$uid,'orderId'=>(string)$r['order_id'],'amount'=>(float)$r['amount'],'paymentLinkId'=>$r['payment_link_id']??'','transactionId'=>$r['gateway_txn_id']??null,'paymentMethod'=>strtoupper($r['method']),'description'=>'Thanh toán đơn #'.$r['code'],'paymentStatus'=>strtoupper($r['status']),'createdAt'=>strtotime($r['created_at'])*1000,'updatedAt'=>strtotime($r['updated_at'])*1000];out(200,['data'=>$rows]);}
        if($method==='POST'){$in=body();$orderId=(int)($in['orderId']??0);$q=$pdo->prepare('SELECT id,total_amount FROM orders WHERE id=? AND customer_id=? AND payment_method=\'payos\'');$q->execute([$orderId,$u['id']]);$order=$q->fetch();if(!$order)out(404,['error'=>'Không tìm thấy đơn thanh toán']);if(abs((float)$order['total_amount']-(float)($in['amount']??0))>0.01)out(422,['error'=>'Số tiền thanh toán không khớp đơn hàng']);$q=$pdo->prepare('UPDATE payments SET payment_link_id=?,checkout_url=?,status=\'processing\' WHERE order_id=? AND provider=\'payos\'');$q->execute([$in['paymentLinkId']??null,$in['checkoutUrl']??null,$orderId]);out(200,['ok'=>true]);}
    }
    if($resource==='notifications') {
        $u=account($pdo,$uid);
        if($method==='GET'){$q=$pdo->prepare('SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 200');$q->execute([$u['id']]);$rows=[];foreach($q as $r)$rows[]=['id'=>(string)$r['id'],'title'=>$r['title'],'content'=>$r['body'],'time'=>$r['created_at'],'type'=>str_contains($r['type'],'order')?'order':'system','isRead'=>(bool)$r['is_read']];out(200,['data'=>$rows]);}
        if($method==='PATCH' && $id==='read-all'){$pdo->prepare('UPDATE notifications SET is_read=1,read_at=NOW() WHERE user_id=? AND is_read=0')->execute([$u['id']]);out(200,['ok'=>true]);}
        if($method==='PATCH' && $id){$pdo->prepare('UPDATE notifications SET is_read=1,read_at=NOW() WHERE id=? AND user_id=?')->execute([$id,$u['id']]);out(200,['ok'=>true]);}
    }
    if($resource==='drivers' && $id==='me'){
        $u=account($pdo,$uid);if($u['role']!=='driver')out(403,['error'=>'Tài khoản không phải shipper']);$pdo->prepare('INSERT IGNORE INTO drivers(user_id,work_status) VALUES(?,?)')->execute([$u['id'],'offline']);$q=$pdo->prepare('SELECT * FROM drivers WHERE user_id=?');$q->execute([$u['id']]);$d=$q->fetch();
        if($method==='GET')out(200,['data'=>['id'=>(string)$d['id'],'userId'=>$uid,'vehicleName'=>$d['vehicle_model']??'','vehicleType'=>$d['vehicle_type']??'','isAvailable'=>$d['work_status']==='available','workStatus'=>$d['work_status'],'completedTrips'=>(int)$d['completed_trips']]]);
        if($method==='PATCH'){$in=body();$work=array_key_exists('isAvailable',$in)?((bool)$in['isAvailable']?'available':'offline'):$d['work_status'];$pdo->prepare('UPDATE drivers SET work_status=?,vehicle_model=COALESCE(?,vehicle_model),vehicle_type=COALESCE(?,vehicle_type) WHERE user_id=?')->execute([$work,$in['vehicleName']??null,$in['vehicleType']??null,$u['id']]);out(200,['ok'=>true]);}
    }
    if($resource==='deliveries' && $id && ($parts[2]??'')==='accept' && $method==='POST'){
        $u=account($pdo,$uid);if($u['role']!=='driver')out(403,['error'=>'Chỉ shipper được nhận đơn']);$pdo->beginTransaction();$q=$pdo->prepare('SELECT id,work_status FROM drivers WHERE user_id=? FOR UPDATE');$q->execute([$u['id']]);$driver=$q->fetch();if(!$driver||$driver['work_status']!=='available'){$pdo->rollBack();out(409,['error'=>'Hãy bật trạng thái sẵn sàng trước khi nhận đơn']);}$q=$pdo->prepare("SELECT d.order_id FROM deliveries d JOIN orders o ON o.id=d.order_id WHERE d.order_id=? AND d.driver_id IS NULL AND o.status='accepted' FOR UPDATE");$q->execute([$id]);if(!$q->fetchColumn()){$pdo->rollBack();out(409,['error'=>'Đơn vừa được nhận hoặc không còn khả dụng']);}$pdo->prepare("UPDATE deliveries SET driver_id=?,status='assigned',assigned_at=NOW() WHERE order_id=? AND driver_id IS NULL")->execute([$driver['id'],$id]);$pdo->prepare("UPDATE drivers SET work_status='busy' WHERE id=?")->execute([$driver['id']]);$pdo->commit();out(200,['ok'=>true]);
    }
    if($resource==='orders') {
        $u=account($pdo,$uid);$userId=(int)$u['id'];
        if($id==='available' && $method==='GET'){
            if($u['role']!=='driver')out(403,['error'=>'Chỉ shipper được xem đơn trống']);
            [$limit,$offset]=foodhubPagination(100,200);$q=$pdo->prepare("SELECT o.id,o.placed_at,o.delivered_at,o.status,o.payment_method,o.payment_status,o.subtotal,o.delivery_fee,o.total_amount,o.receiver_name,o.receiver_phone,o.delivery_address,o.delivery_lat,o.delivery_lng,o.store_id,s.name AS store_name,s.latitude AS restaurant_lat,s.longitude AS restaurant_lng FROM orders o JOIN stores s ON s.id=o.store_id JOIN deliveries d ON d.order_id=o.id WHERE o.status='accepted' AND d.driver_id IS NULL ORDER BY o.placed_at,o.id LIMIT ? OFFSET ?");$q->bindValue(1,$limit,PDO::PARAM_INT);$q->bindValue(2,$offset,PDO::PARAM_INT);$q->execute();$rows=[];foreach($q as $o)$rows[]=['id'=>(string)$o['id'],'time'=>$o['placed_at'],'completedAt'=>$o['delivered_at']??null,'status'=>strtoupper($o['status']),'paymentMethod'=>strtoupper($o['payment_method']),'isPaid'=>$o['payment_status']==='paid','totalDishPrice'=>(float)$o['subtotal'],'shippingFee'=>(float)$o['delivery_fee'],'totalPrice'=>(float)$o['total_amount'],'customerName'=>$o['receiver_name'],'customerPhone'=>$o['receiver_phone'],'customerAddress'=>$o['delivery_address'],'customerLat'=>$o['delivery_lat']===null?null:(float)$o['delivery_lat'],'customerLng'=>$o['delivery_lng']===null?null:(float)$o['delivery_lng'],'restaurantId'=>(string)$o['store_id'],'restaurantName'=>$o['store_name'],'restaurantLat'=>$o['restaurant_lat']===null?null:(float)$o['restaurant_lat'],'restaurantLng'=>$o['restaurant_lng']===null?null:(float)$o['restaurant_lng'],'shipperId'=>'','items'=>[]];out(200,['data'=>$rows]);
        }
        if($method==='GET'){
            $sql='SELECT o.id,o.placed_at,o.delivered_at,o.status,o.payment_method,o.payment_status,o.subtotal,o.delivery_fee,o.total_amount,o.customer_id,o.receiver_name,o.receiver_phone,o.delivery_address,o.delivery_lat,o.delivery_lng,o.store_id,s.name AS store_name,s.latitude AS restaurant_lat,s.longitude AS restaurant_lng,s.cover_url AS restaurant_cover,su.firebase_uid AS shipper_uid,cu.firebase_uid AS customer_uid,COALESCE(o.delivered_at,de.delivered_at,(SELECT MAX(sl.created_at) FROM order_status_logs sl WHERE sl.order_id=o.id AND sl.status=\'completed\')) AS completed_at FROM orders o JOIN stores s ON s.id=o.store_id JOIN users cu ON cu.id=o.customer_id LEFT JOIN deliveries de ON de.order_id=o.id LEFT JOIN drivers dr ON dr.id=de.driver_id LEFT JOIN users su ON su.id=dr.user_id';$params=[];
            if($u['role']==='customer'){$sql.=' WHERE o.customer_id=?';$params[]=$userId;}
            elseif($u['role']==='seller'){$sql.=' JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=?';$params[]=$userId;}
            elseif($u['role']==='driver'){$sql.=' WHERE (dr.user_id=? OR (o.status=\'accepted\' AND de.driver_id IS NULL))';$params[]=$userId;}
            if($id){$sql.=($params?' AND':' WHERE').' o.id=?';$params[]=$id;}
            $sql.=' ORDER BY o.placed_at DESC,o.id DESC';if($id){$sql.=' LIMIT 1';$s=$pdo->prepare($sql);$s->execute($params);}else{[$limit,$offset]=foodhubPagination(100,200);$sql.=' LIMIT ? OFFSET ?';$s=$pdo->prepare($sql);foreach($params as $i=>$param)$s->bindValue($i+1,$param,is_int($param)?PDO::PARAM_INT:PDO::PARAM_STR);$s->bindValue(count($params)+1,$limit,PDO::PARAM_INT);$s->bindValue(count($params)+2,$offset,PDO::PARAM_INT);$s->execute();}$rows=[];
            $orders=$s->fetchAll();
            $itemsByOrder=[];
            foreach(array_chunk(array_column($orders,'id'),500) as $ids){
                $marks=implode(',',array_fill(0,count($ids),'?'));
                $itemsQuery=$pdo->prepare("SELECT oi.order_id,oi.dish_id,oi.dish_name,oi.quantity,oi.unit_price,d.image_url AS dish_image_url FROM order_items oi LEFT JOIN dishes d ON d.id=oi.dish_id WHERE oi.order_id IN ($marks) ORDER BY oi.order_id,oi.id");
                $itemsQuery->execute($ids);
                foreach($itemsQuery as $i){
                    $itemsByOrder[$i['order_id']][]=['dishId'=>(string)($i['dish_id']??''),'name'=>$i['dish_name'],'quantity'=>(int)$i['quantity'],'price'=>(float)$i['unit_price'],'imageUrl'=>$i['dish_image_url']??''];
                }
            }
            foreach($orders as $o){$items=$itemsByOrder[$o['id']]??[];$rows[]=['id'=>(string)$o['id'],'time'=>$o['placed_at'],'completedAt'=>$o['completed_at']??$o['delivered_at']??null,'status'=>strtoupper($o['status']),'paymentMethod'=>strtoupper($o['payment_method']),'isPaid'=>$o['payment_status']==='paid','totalDishPrice'=>(float)$o['subtotal'],'shippingFee'=>(float)$o['delivery_fee'],'totalPrice'=>(float)$o['total_amount'],'userId'=>$o['customer_uid'],'customerName'=>$o['receiver_name'],'customerPhone'=>$o['receiver_phone'],'customerAddress'=>$o['delivery_address'],'customerLat'=>$o['delivery_lat']===null?null:(float)$o['delivery_lat'],'customerLng'=>$o['delivery_lng']===null?null:(float)$o['delivery_lng'],'restaurantId'=>(string)$o['store_id'],'restaurantName'=>$o['store_name'],'restaurantLat'=>$o['restaurant_lat']===null?null:(float)$o['restaurant_lat'],'restaurantLng'=>$o['restaurant_lng']===null?null:(float)$o['restaurant_lng'],'restaurantCoverImage'=>$o['restaurant_cover']??'','shipperId'=>$o['shipper_uid']??'','items'=>$items];}
            out(200,['data'=>$id?($rows[0]??null):$rows]);
        }
        if($method==='POST'){
            if($u['role']!=='customer')out(403,['error'=>'Chỉ khách hàng được tạo đơn']);$in=body();$items=$in['items']??[];$storeId=(int)($in['restaurantId']??0);if(!$storeId||!is_array($items)||!count($items))out(422,['error'=>'Thiếu cửa hàng hoặc món']);
            $pdo->beginTransaction();try{$store=$pdo->prepare('SELECT id,name,latitude,longitude FROM stores WHERE id=? AND is_open=1 AND status=\'active\' FOR UPDATE');$store->execute([$storeId]);$sr=$store->fetch();if(!$sr)throw new RuntimeException('Cửa hàng hiện không nhận đơn');
                $dishIds=[];foreach($items as $item){$dishId=(int)($item['dishId']??0);if($dishId>0)$dishIds[$dishId]=$dishId;}if(!$dishIds)throw new RuntimeException('Món không hợp lệ hoặc không thuộc cùng cửa hàng');$marks=implode(',',array_fill(0,count($dishIds),'?'));$dishQuery=$pdo->prepare("SELECT id,name,price,store_id FROM dishes WHERE id IN ($marks) AND is_available=1 AND deleted_at IS NULL");$dishQuery->execute(array_values($dishIds));$dishById=[];foreach($dishQuery as $dish)$dishById[(int)$dish['id']]=$dish;$priced=[];$subtotal=0.0;foreach($items as $item){$dishId=(int)($item['dishId']??0);$qty=max(1,min(99,(int)($item['quantity']??1)));$d=$dishById[$dishId]??null;if(!$d||(int)$d['store_id']!==$storeId)throw new RuntimeException('Món không hợp lệ hoặc không thuộc cùng cửa hàng');$priced[]=[$d,$qty];$subtotal+=(float)$d['price']*$qty;}
                $storeLat=$sr['latitude']!==null?(float)$sr['latitude']:null;$storeLng=$sr['longitude']!==null?(float)$sr['longitude']:null;
                $custLat=isset($in['customerLat'])&&is_numeric($in['customerLat'])?(float)$in['customerLat']:null;$custLng=isset($in['customerLng'])&&is_numeric($in['customerLng'])?(float)$in['customerLng']:null;
                $distKm=foodhubDistanceKm($storeLat,$storeLng,$custLat,$custLng);
                $fee=foodhubShippingFee($distKm,(float)($config['delivery_fee']??15000));$discount=0.0;$voucherId=(int)($in['appliedPromotionId']??0);$voucher=null;
                if($voucherId){$q=$pdo->prepare('SELECT * FROM vouchers WHERE id=? AND is_active=1 AND (store_id IS NULL OR store_id=?) AND (starts_at IS NULL OR starts_at<=NOW()) AND (ends_at IS NULL OR ends_at>=NOW()) FOR UPDATE');$q->execute([$voucherId,$storeId]);$voucher=$q->fetch();if(!$voucher)throw new RuntimeException('Voucher không còn hiệu lực cho cửa hàng này');if($subtotal<(float)$voucher['min_order_amount'])throw new RuntimeException('Đơn hàng chưa đạt giá trị tối thiểu của voucher');if($voucher['usage_limit']!==null&&(int)$voucher['used_count']>=(int)$voucher['usage_limit'])throw new RuntimeException('Voucher đã hết lượt sử dụng');if($voucher['discount_type']==='percentage'){$discount=$subtotal*(float)$voucher['discount_value']/100;if($voucher['max_discount']!==null)$discount=min($discount,(float)$voucher['max_discount']);}elseif($voucher['discount_type']==='fixed')$discount=(float)$voucher['discount_value'];elseif($voucher['discount_type']==='free_shipping')$discount=$fee;$discount=min($discount,$subtotal+$fee);}
                $total=max(0,$subtotal+$fee-$discount);$address=(string)($in['customerAddress']??'');$receiver=(string)($in['customerName']??$u['name']);$phone=(string)($in['customerPhone']??$u['phone']);$method=strtolower((string)($in['paymentMethod']??'cash'));$method=in_array($method,['payos','online'],true)?'payos':'cash';$initialStatus=$method==='payos'?'pending_payment':'pending';$code='FH'.date('ymd').strtoupper(bin2hex(random_bytes(4)));
                $q=$pdo->prepare('INSERT INTO orders(code,customer_id,store_id,status,receiver_name,receiver_phone,delivery_address,delivery_lat,delivery_lng,customer_note,subtotal,delivery_fee,discount_amount,total_amount,payment_method,payment_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)');$q->execute([$code,$userId,$storeId,$initialStatus,$receiver,$phone,$address,$in['customerLat']??null,$in['customerLng']??null,$in['note']??null,$subtotal,$fee,$discount,$total,$method,'pending']);$orderId=(int)$pdo->lastInsertId();
                $itemValues=implode(',',array_fill(0,count($priced),'(?,?,?,?,?,?)'));$itemParams=[];foreach($priced as [$d,$qty])array_push($itemParams,$orderId,$d['id'],$d['name'],$d['price'],$qty,(float)$d['price']*$qty);$pdo->prepare("INSERT INTO order_items(order_id,dish_id,dish_name,unit_price,quantity,line_total) VALUES $itemValues")->execute($itemParams);if($voucher){$pdo->prepare('INSERT INTO order_vouchers(order_id,voucher_id,discount_amount) VALUES(?,?,?)')->execute([$orderId,$voucherId,$discount]);$pdo->prepare('UPDATE vouchers SET used_count=used_count+1 WHERE id=?')->execute([$voucherId]);}$pdo->prepare('INSERT INTO order_status_logs(order_id,status,actor_user_id) VALUES(?,?,?)')->execute([$orderId,$initialStatus,$userId]);$pdo->prepare('INSERT INTO deliveries(order_id,status,delivery_fee) VALUES(?,?,?)')->execute([$orderId,'unassigned',$fee]);$pdo->prepare('INSERT INTO payments(order_id,provider,method,amount,status) VALUES(?,?,?,?,?)')->execute([$orderId,$method,$method,$total,'pending']);$pdo->commit();if($voucher)foodhubInvalidateJsonCache('catalog-vouchers.json');$ownerQ=$pdo->prepare('SELECT m.owner_user_id FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=?');$ownerQ->execute([$storeId]);$sellerId=$ownerQ->fetchColumn();if($sellerId)notifyUser($pdo,$config,(int)$sellerId,$orderId,'new_order','Có đơn hàng mới',"Bạn vừa nhận đơn #$code");out(201,['id'=>(string)$orderId,'data'=>['id'=>(string)$orderId,'status'=>strtoupper($initialStatus),'totalPrice'=>$total,'restaurantId'=>(string)$storeId]]);
            }catch(Throwable $e){$pdo->rollBack();out(422,['error'=>$e->getMessage()]);}
        }
        if($method==='PATCH' && $id){$in=body();$status=strtolower((string)($in['status']??''));$allowed=['pending','processing','accepted','shipping','completed','cancelled'];if(!in_array($status,$allowed,true))out(422,['error'=>'Trạng thái đơn không hợp lệ']);
            $pdo->beginTransaction();$q=$pdo->prepare('SELECT id,customer_id,store_id FROM orders WHERE id=? FOR UPDATE');$q->execute([$id]);$o=$q->fetch();if(!$o){$pdo->rollBack();out(404,['error'=>'Không tìm thấy đơn']);}
            $owned=false;if($u['role']==='customer')$owned=(int)$o['customer_id']===$userId && $status==='cancelled';elseif($u['role']==='admin')$owned=true;elseif($u['role']==='seller'){$x=$pdo->prepare('SELECT 1 FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=? AND m.owner_user_id=?');$x->execute([$o['store_id'],$userId]);$owned=(bool)$x->fetchColumn();}elseif($u['role']==='driver'){$x=$pdo->prepare('SELECT 1 FROM deliveries d JOIN drivers dr ON dr.id=d.driver_id WHERE d.order_id=? AND dr.user_id=?');$x->execute([$id,$userId]);$owned=(bool)$x->fetchColumn();}if(!$owned){$pdo->rollBack();out(403,['error'=>'Không có quyền cập nhật đơn này']);}
            $timeField=['accepted'=>'accepted_at','processing'=>'accepted_at','shipping'=>'picked_up_at','completed'=>'delivered_at','cancelled'=>'cancelled_at'][$status]??null;$q=$pdo->prepare('UPDATE orders SET status=?'.($timeField?", $timeField=NOW()":'').',cancel_reason=? WHERE id=?');$q->execute([$status,$in['cancelReason']??null,$id]);
            if($status==='shipping')$pdo->prepare("UPDATE deliveries SET status='picked_up',picked_up_at=NOW() WHERE order_id=?")->execute([$id]);
            if($status==='completed'){$pdo->prepare("UPDATE deliveries SET status='delivered',delivered_at=NOW() WHERE order_id=?")->execute([$id]);$pdo->prepare("UPDATE drivers dr JOIN deliveries de ON de.driver_id=dr.id SET dr.completed_trips=dr.completed_trips+1,dr.work_status='available' WHERE de.order_id=?")->execute([$id]);}
            if($status==='cancelled')$pdo->prepare("UPDATE deliveries SET status='cancelled' WHERE order_id=?")->execute([$id]);
            $pdo->prepare('INSERT INTO order_status_logs(order_id,status,note,actor_user_id) VALUES(?,?,?,?)')->execute([$id,$status,$in['note']??null,$userId]);$pdo->commit();if((int)$o['customer_id']!==$userId)notifyUser($pdo,$config,(int)$o['customer_id'],(int)$id,'order_status','Cập nhật đơn hàng','Đơn hàng của bạn: '.str_replace('_',' ',$status));out(200,['ok'=>true]);
        }
    }
    out(404,['error'=>'API route chưa được hỗ trợ']);
} catch(Throwable $e) { if($pdo->inTransaction())$pdo->rollBack();error_log($e->__toString());out(500,['error'=>'Lỗi xử lý yêu cầu']); }
