<?php
// Copy to config.local.php and fill your own values. Never commit that file.
return [
    'mysql' => [
        'host' => 'YOUR_TIDB_HOST',
        'port' => 4000,
        'database' => 'food_delivery_app',
        'username' => 'YOUR_TIDB_USERNAME',
        'password' => '',
        'charset' => 'utf8mb4',
        'timezone' => '+07:00',
        'ssl' => ['required' => true, 'ca' => __DIR__.'/certs/isrgrootx1.pem'],
    ],
    'firebase_project_id' => 'YOUR_FIREBASE_PROJECT_ID',
    // Keep the service-account JSON outside the website and repository.
    'firebase_service_account_file' => getenv('FOODHUB_FIREBASE_SERVICE_ACCOUNT') ?: '',
    'timezone' => 'Asia/Ho_Chi_Minh',
    'cache_directory' => sys_get_temp_dir().'/foodhub-cache',
    'delivery_fee' => 15000,
    'payos' => ['client_id' => '', 'api_key' => '', 'checksum_key' => ''],
];
