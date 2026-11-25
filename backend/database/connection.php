<?php
declare(strict_types=1);

/** Shared by the HTTP API and the CLI connection check. */
function foodhubDatabaseOptions(array $mysql): array {
    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => true,
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

function foodhubConnectDatabase(array $mysql, bool $reuseForRead = true): PDO {
    static $verifiedPools = [];
    $host = (string)$mysql['host'];
    $port = (int)$mysql['port'];
    $database = (string)$mysql['database'];
    $charset = (string)$mysql['charset'] ?? 'utf8mb4';
    $options = foodhubDatabaseOptions($mysql);
    if (isset($mysql['timezone'])) {
        $timezone = (string)$mysql['timezone'];
        if (!preg_match('/^[+-](?:0[0-9]|1[0-3]):[0-5][0-9]$/D', $timezone)) {
            throw new RuntimeException('Múi giờ database phải là độ lệch dạng +07:00.');
        }
        $options[PDO::MYSQL_ATTR_INIT_COMMAND] = "SET SESSION time_zone = '$timezone'";
    }
    $poolKey = 'foodhub-pool-'.hash('sha256', serialize([$host, $port, $database, $charset, $options]));
    if ($reuseForRead) {
        $options[PDO::ATTR_PERSISTENT] = $poolKey;
    }
    $dsn = "mysql:host=$host;port=$port;dbname=$database;charset=$charset";
    try {
        $pdo = new PDO($dsn, (string)$mysql['username'], (string)$mysql['password'], $options);
    } catch (PDOException $error) {
        if (!$reuseForRead) throw $error;
        unset($verifiedPools[$poolKey]);
        return foodhubConnectDatabase($mysql, false);
    }
    if (!empty($mysql['ssl']['required']) && empty($verifiedPools[$poolKey])) {
        try {
            $status = $pdo->query("SHOW SESSION STATUS LIKE 'Ssl_cipher'")->fetch();
        } catch (PDOException $error) {
            if (!$reuseForRead || !in_array((int)($error->errorInfo[1] ?? 0), [2006, 2013], true)) throw $error;
            unset($verifiedPools[$poolKey]);
            return foodhubConnectDatabase($mysql, false);
        }
        if (empty($status['Value'])) {
            throw new RuntimeException('Database không thiết lập được kết nối TLS.');
        }
        if ($reuseForRead) {
            $verifiedPools[$poolKey] = true;
        }
    }
    return $pdo;
}
