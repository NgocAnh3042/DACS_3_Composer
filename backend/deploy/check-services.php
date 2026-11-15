<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
try {
    $config=require $argv[1];
    date_default_timezone_set((string)($config['timezone']??'Asia/Ho_Chi_Minh'));
    $sa=json_decode(file_get_contents($config['firebase_service_account_file']),true,512,JSON_THROW_ON_ERROR);
    $b64=fn($s)=>rtrim(strtr(base64_encode($s),'+/','-_'),'=');
    $now=time();
    $unsigned=$b64(json_encode(['alg'=>'RS256','typ'=>'JWT'])).'.'.$b64(json_encode([
        'iss'=>$sa['client_email'],'scope'=>'https://www.googleapis.com/auth/firebase.messaging',
        'aud'=>'https://oauth2.googleapis.com/token','iat'=>$now,'exp'=>$now+600,
    ]));
    if(!openssl_sign($unsigned,$signature,$sa['private_key'],OPENSSL_ALGO_SHA256))throw new RuntimeException('Cannot sign OAuth assertion');
    $context=stream_context_create(['http'=>['method'=>'POST','timeout'=>20,'ignore_errors'=>true,
        'header'=>"Content-Type: application/x-www-form-urlencoded\r\n",
        'content'=>http_build_query(['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$unsigned.'.'.$b64($signature)]),
    ]]);
    $response=json_decode((string)@file_get_contents('https://oauth2.googleapis.com/token',false,$context),true);
    if(empty($response['access_token']))throw new RuntimeException('FCM OAuth request failed');
    echo json_encode(['fcm_oauth'=>true,'notification_sent'=>false,'php_timezone'=>date_default_timezone_get()]).PHP_EOL;
}catch(Throwable $e){echo 'Service check failed: '.get_class($e).' code '.$e->getCode().PHP_EOL;exit(1);}
