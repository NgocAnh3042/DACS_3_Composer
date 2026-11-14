<?php
declare(strict_types=1);
// This is a local CLI tool, never an HTTP endpoint.
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }

require_once dirname(__DIR__).'/database/connection.php';
$configPath = $argv[1] ?? dirname(__DIR__).'/config.tidb.local.php';
if (!is_file($configPath)) {
    fwrite(STDERR, "Chưa có file cấu hình. Tạo config.tidb.local.php trước.\n");
    exit(1);
}
try {
    $config = require $configPath;
    if (!empty($config['mysql']['ssl']['required']) && (string)$config['mysql']['password'] === '') {
        throw new RuntimeException('Chưa điền mật khẩu TiDB trong file cấu hình local.');
    }
    $pdo = foodhubConnectDatabase($config['mysql']);
    $info = $pdo->query('SELECT DATABASE() AS database_name, VERSION() AS server_version, @@session.time_zone AS session_timezone')->fetch();
    $info['table_count'] = (int)$pdo->query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")->fetchColumn();
    $ssl = $pdo->query("SHOW SESSION STATUS LIKE 'Ssl_cipher'")->fetch();
    $info['tls_cipher'] = $ssl['Value'] ?? '';
    $info['missing_auto_increment'] = $pdo->query("SELECT table_name FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='id' AND column_key='PRI' AND extra NOT LIKE '%auto_increment%' ORDER BY table_name")->fetchAll(PDO::FETCH_COLUMN);
    $info['row_counts'] = [];
    foreach (['users', 'stores', 'dishes', 'orders'] as $table) {
        $info['row_counts'][$table] = (int)$pdo->query('SELECT COUNT(*) FROM `'.$table.'`')->fetchColumn();
    }
    echo json_encode($info, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES).PHP_EOL;
    if ($info['table_count'] !== 32) {
        fwrite(STDERR, "Số bảng khác 32; đối chiếu schema trước khi chuyển API.\n");
        exit(1);
    }
    if ($info['missing_auto_increment']) {
        fwrite(STDERR, "Schema thiếu AUTO_INCREMENT cho cột id; chưa thể tạo dữ liệu mới an toàn.\n");
        exit(1);
    }
} catch (PDOException $e) {
    // Avoid printing exception messages, connection strings or credentials.
    $driverCode = (int)($e->errorInfo[1] ?? 0);
    $hints = [
        1045 => 'Kiểm tra username đầy đủ và mật khẩu TiDB.',
        1049 => 'Không tìm thấy database đã cấu hình.',
        2002 => 'Kiểm tra host, cổng, Internet và kết nối TLS.',
        2026 => 'Kiểm tra chứng chỉ CA và TLS.',
    ];
    fwrite(STDERR, 'Kết nối/kiểm tra thất bại (SQLSTATE '.$e->getCode().', mã '.$driverCode.'). '.($hints[$driverCode] ?? 'Đối chiếu kết nối và schema database.').PHP_EOL);
    exit(1);
} catch (Throwable $e) {
    fwrite(STDERR, "Chưa kiểm tra được database. Kiểm tra mật khẩu, đường dẫn CA và cấu hình local.\n");
    exit(1);
}
