<?php
declare(strict_types=1);

/** Store token caches outside the public directory on shared hosting. */
function foodhubCacheFile(string $name): string {
    global $config;
    $directory = (string)($config['cache_directory'] ?? sys_get_temp_dir());
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        throw new RuntimeException('Không tạo được thư mục cache của API.');
    }
    return rtrim($directory, '/\\').'/'.$name;
}
