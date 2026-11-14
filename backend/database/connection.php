<?php
declare(strict_types=1);

/** Shared by the HTTP API and the CLI connection check. */
function foodhubDatabaseOptions(array $mysql): array {
    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_TIMEOUT => 10,
    ];
    if (!empty($mysql['ssl']['required'])) {
        $ca = (string)($mysql['ssl']['ca'] ?? '');
        if ($ca === '' || !is_file($ca) || !is_readable($ca)) {
            throw new RuntimeException('Không đọc được chứng chỉ CA cho kết nối database TLS.');
        }
        $options[PDO::MYSQL_ATTR_SSL_CA] = $ca;
        $options[PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT] = true;
    }
    return $options;
}

function foodhubConnectDatabase(array $mysql): PDO {
    $host = (string)$mysql['host'];
    $port = (int)$mysql['port'];
    $database = (string)$mysql['database'];
    $charset = (string)($mysql['charset'] ?? 'utf8mb4');
    $pdo = new PDO(
        "mysql:host=$host;port=$port;dbname=$database;charset=$charset",
        (string)$mysql['username'],
        (string)$mysql['password'],
        foodhubDatabaseOptions($mysql)
    );
    if (!empty($mysql['ssl']['required'])) {
        $status = $pdo->query("SHOW SESSION STATUS LIKE 'Ssl_cipher'")->fetch();
        if (empty($status['Value'])) {
            throw new RuntimeException('Database không thiết lập được kết nối TLS.');
        }
    }
    // Fixed offset avoids depending on timezone tables installed on the server.
    if (isset($mysql['timezone'])) {
        $timezone = (string)$mysql['timezone'];
        if (!preg_match('/^[+-](?:0[0-9]|1[0-3]):[0-5][0-9]$/D', $timezone)) {
            throw new RuntimeException('Múi giờ database phải là độ lệch dạng +07:00.');
        }
        $pdo->exec('SET SESSION time_zone = '.$pdo->quote($timezone));
    }
    return $pdo;
}
