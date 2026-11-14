<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require_once dirname(__DIR__).'/database/connection.php';

function expect(bool $condition, string $message): void {
    if (!$condition) { throw new RuntimeException($message); }
}

$local = foodhubDatabaseOptions([]);
expect(!isset($local[PDO::MYSQL_ATTR_SSL_CA]), 'Local MySQL must remain compatible without TLS.');
foreach (['', __DIR__.'/missing-ca.pem'] as $invalidCa) {
    $rejected = false;
    try {
        foodhubDatabaseOptions(['ssl'=>['required'=>true, 'ca'=>$invalidCa]]);
    } catch (RuntimeException $e) { $rejected = true; }
    expect($rejected, 'Required TLS must reject missing CA before connecting.');
}
$secure = foodhubDatabaseOptions(['ssl'=>[
    'required'=>true,
    'ca'=>dirname(__DIR__).'/certs/isrgrootx1.pem',
]]);
expect($secure[PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT] === true, 'TLS server verification must be enabled.');
echo "PASS: local compatibility, missing CA rejection, TLS certificate verification.\n";
