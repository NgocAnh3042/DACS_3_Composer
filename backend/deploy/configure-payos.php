<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
try {
    $config=require $argv[1];
    $url='https://chinh.alwaysdata.net/food-delivery-api/api/payos/webhook';
    $payos=$config['payos']??[];
    if(empty($payos['client_id'])||empty($payos['api_key']))throw new RuntimeException('Missing PayOS configuration');
    $curl=curl_init('https://api-merchant.payos.vn/confirm-webhook');
    curl_setopt_array($curl,[CURLOPT_POST=>true,CURLOPT_RETURNTRANSFER=>true,CURLOPT_CONNECTTIMEOUT=>10,CURLOPT_TIMEOUT=>35,
        CURLOPT_SSL_VERIFYPEER=>true,CURLOPT_SSL_VERIFYHOST=>2,
        CURLOPT_HTTPHEADER=>['Content-Type: application/json','x-client-id: '.$payos['client_id'],'x-api-key: '.$payos['api_key']],
        CURLOPT_POSTFIELDS=>json_encode(['webhookUrl'=>$url]),
    ]);
    $raw=curl_exec($curl);$status=(int)curl_getinfo($curl,CURLINFO_RESPONSE_CODE);curl_close($curl);
    $response=is_string($raw)?json_decode($raw,true):null;
    if($status!==200||($response['code']??'')!=='00'){
        echo json_encode(['confirmed'=>false,'http_status'=>$status,'provider_code'=>$response['code']??null]).PHP_EOL;exit(1);
    }
    echo json_encode(['confirmed'=>true,'webhookUrl'=>$url],JSON_UNESCAPED_SLASHES).PHP_EOL;
}catch(Throwable $e){echo 'Webhook configuration failed: '.get_class($e).' code '.$e->getCode().PHP_EOL;exit(1);}
