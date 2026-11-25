<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
$base=dirname(__DIR__);
require_once $base.'/database/connection.php';
require_once $base.'/ai/MealAdvisor.php';
$stage='load-config';
try {
    $path=is_file($base.'/config.local.php')?$base.'/config.local.php':$base.'/config.runtime.local.php';
    $config=require $path;
    if (in_array('--repo-ca',$argv,true)) $config['mysql']['ssl']['ca']=$base.'/certs/isrgrootx1.pem';
    $stage='connect-database';
    $pdo=foodhubConnectDatabase($config['mysql']);
    $stage='query-catalog';
    $results=[];
    foreach ([60000,45000,0] as $ceiling) {
        $rows=MealAdvisor::catalog($pdo,(float)$ceiling);
        foreach ($rows as $row) if ((float)$row['price']>$ceiling) throw new RuntimeException('Budget filter failed');
        $results[]=['foodBudget'=>$ceiling,'candidateCount'=>count($rows),'budgetFilter'=>true];
    }
    echo json_encode(['databaseReadOnly'=>true,'results'=>$results],JSON_UNESCAPED_UNICODE).PHP_EOL;
} catch(Throwable $e) { fwrite(STDERR,'Meal catalog check failed at '.$stage.': '.get_class($e).PHP_EOL); exit(1); }
