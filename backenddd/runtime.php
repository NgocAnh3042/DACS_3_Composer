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

function foodhubReadJsonCache(string $name, int $ttlSeconds): ?array {
    $file = foodhubCacheFile($name);
    if (!is_file($file) || (@filesize($file) ?: 0) > 2097152) return null;
    $mtime = @filemtime($file) ?: 0;
    if ($mtime < time() - $ttlSeconds) return null;
    $raw = @file_get_contents($file);
    if (!is_string($raw) || $raw === '') return null;
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : null;
}

function foodhubWriteJsonCache(string $name, array $data): void {
    $file = foodhubCacheFile($name);
    $tmp = $file.'.tmp-'.bin2hex(random_bytes(4));
    try {
        $raw = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION);
        if (!is_string($raw)) return;
        if (@file_put_contents($tmp, $raw, LOCK_EX) !== false) {
            @chmod($tmp, 0600);
            @rename($tmp, $file);
        }
    } finally {
        if (is_file($tmp)) @unlink($tmp);
    }
}

function foodhubInvalidateJsonCache(string ...$prefixes): void {
    global $config;
    $directory = rtrim((string)($config['cache_directory'] ?? sys_get_temp_dir()), '/\\');
    if (!is_dir($directory)) return;
    foreach ($prefixes as $prefix) {
        $exact = $directory.'/'.$prefix;
        if (is_file($exact)) { @unlink($exact); continue; }
        foreach (glob($directory.'/'.$prefix.'*.json') ?: [] as $matched) {
            if (is_file($matched)) @unlink($matched);
        }
    }
}

/** Parse a bounded, backward-compatible limit/offset pair for list endpoints. */
function foodhubPagination(int $defaultLimit = 100, int $maximumLimit = 200): array {
    $limit = isset($_GET['limit']) && is_numeric($_GET['limit']) ? (int)$_GET['limit'] : $defaultLimit;
    $offset = isset($_GET['offset']) && is_numeric($_GET['offset']) ? (int)$_GET['offset'] : 0;
    return [max(1, min($maximumLimit, $limit)), max(0, min(1000000, $offset))];
}
