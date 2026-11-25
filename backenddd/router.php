<?php
declare(strict_types=1);

// PHP's built-in server does not read Apache .htaccess rewrite rules.
$requestPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
if (!is_string($requestPath)) {
    http_response_code(400);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'Đường dẫn không hợp lệ'], JSON_UNESCAPED_UNICODE);
    return true;
}

if ($requestPath === '/api' || str_starts_with($requestPath, '/api/')) {
    // api/index.php extracts routes after the hosting prefix `/food-delivery-api/api/`.
    // Give it that same URI shape locally; otherwise `/api/users` is parsed as
    // resource `api` because index.php trims the leading slash before extraction.
    $query = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_QUERY);
    $normalizedPath = $requestPath === '/api' ? '/api/' : $requestPath;
    $_SERVER['REQUEST_URI'] = '/food-delivery-api' . $normalizedPath
        . (is_string($query) && $query !== '' ? '?' . $query : '');
    require __DIR__ . '/api/index.php';
    return true;
}

if (preg_match('#^/food-delivery-api/api(?:/|$)#', $requestPath) === 1) {
    require __DIR__ . '/api/index.php';
    return true;
}

$relativePath = ltrim(str_replace('/', DIRECTORY_SEPARATOR, $requestPath), DIRECTORY_SEPARATOR);
$staticFile = __DIR__ . DIRECTORY_SEPARATOR . $relativePath;
if ($requestPath !== '/' && is_file($staticFile)) {
    return false;
}

http_response_code(404);
header('Content-Type: application/json; charset=utf-8');
echo json_encode(['error' => 'Không tìm thấy đường dẫn'], JSON_UNESCAPED_UNICODE);
return true;
