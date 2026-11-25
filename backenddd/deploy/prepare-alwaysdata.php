<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }

// Generates an ignored deployment bundle; credentials are never printed.
$base=dirname(__DIR__);
$bundle=dirname($base).'/build/alwaysdata-deploy/bundle';
$remotePrivate='/home/chinh/private/foodhub';
try {
    $configFile=is_file($base.'/config.local.php')?$base.'/config.local.php':$base.'/config.runtime.local.php';
    $config=require $configFile;
    if (($config['mysql']['database']??'')!=='food_delivery_app' || empty($config['mysql']['ssl']['required'])) {
        throw new RuntimeException('Expected the active TiDB database and verified TLS configuration.');
    }
    $serviceAccount=(string)($config['firebase_service_account_file']??'');
    $ca=(string)($config['mysql']['ssl']['ca']??'');
    if (!is_readable($serviceAccount) || !is_readable($ca)) throw new RuntimeException('Missing private deployment inputs.');
    foreach (['public/api','public/database','public/ai','private'] as $dir) {
        if (!is_dir($bundle.'/'.$dir) && !mkdir($bundle.'/'.$dir,0700,true)) throw new RuntimeException('Cannot create staging directory.');
    }
    $publicFiles=['.htaccess','api/.htaccess','api/index.php','database/connection.php','runtime.php','ai/FoodHubSupport.php','ai/MenuKnowledge.php','ai/MealChat.php','ai/MealAdvisor.php','ai/meal-advisor-route.php'];
    foreach ($publicFiles as $file) {
        if (!copy($base.'/'.$file,$bundle.'/public/'.$file)) throw new RuntimeException('Cannot stage public file.');
    }
    $config['mysql']['ssl']['ca']=$remotePrivate.'/isrgrootx1.pem';
    $config['firebase_service_account_file']=$remotePrivate.'/service-account.json';
    $config['cache_directory']=$remotePrivate.'/cache';
    $config['timezone']='Asia/Ho_Chi_Minh';
    if (file_put_contents($bundle.'/private/config.local.php',"<?php\nreturn ".var_export($config,true).";\n",LOCK_EX)===false) throw new RuntimeException('Cannot stage private config.');
    if (!copy($serviceAccount,$bundle.'/private/service-account.json') || !copy($ca,$bundle.'/private/isrgrootx1.pem')) throw new RuntimeException('Cannot stage private files.');
    $wrapper="<?php\nreturn require '/home/chinh/private/foodhub/config.local.php';\n";
    file_put_contents($bundle.'/public/config.local.php',$wrapper,LOCK_EX);
    $geminiInput=is_file($base.'/config.gemini.local.php') ? require $base.'/config.gemini.local.php' : [];
    if (trim((string)($geminiInput['api_key']??''))!=='') {
        copy($base.'/config.gemini.local.php',$bundle.'/private/config.gemini.local.php');
        file_put_contents($bundle.'/public/config.gemini.local.php',"<?php\nreturn require '/home/chinh/private/foodhub/config.gemini.local.php';\n",LOCK_EX);
    } else {
        // A blank local template must never overwrite an existing hosting key.
        foreach (['private/config.gemini.local.php','public/config.gemini.local.php'] as $stale) {
            if (is_file($bundle.'/'.$stale)) unlink($bundle.'/'.$stale);
        }
    }
    file_put_contents($bundle.'/public/.user.ini',"display_errors=Off\nlog_errors=On\ndate.timezone=Asia/Ho_Chi_Minh\n",LOCK_EX);
    $files=[];
    foreach (new RecursiveIteratorIterator(new RecursiveDirectoryIterator($bundle,FilesystemIterator::SKIP_DOTS)) as $file) {
        if ($file->isFile()) $files[str_replace('\\','/',substr($file->getPathname(),strlen($bundle)+1))]=hash_file('sha256',$file->getPathname());
    }
    file_put_contents(dirname($bundle).'/manifest.json',json_encode(['files'=>$files],JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES),LOCK_EX);
    echo 'Bundle ready: '.count($files).' files. Private config, service account and CA will stay outside the website root.'.PHP_EOL;
} catch (Throwable $e) {
    fwrite(STDERR,'Cannot prepare deployment: '.get_class($e).' code '.$e->getCode().PHP_EOL);exit(1);
}
