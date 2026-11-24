<?php
declare(strict_types=1);

require_once __DIR__ . '/SearchHistoryService.php';

/**
 * Lightweight, interpretable Content-Based Food Recommendation Engine.
 * Combines TextSimilarity (0.40), CategorySimilarity (0.25),
 * Recency Exponential Decay (0.20), and Log-Scaled Frequency (0.15),
 * with diversity capping, product eligibility filtering, and cold-start fallback.
 */
final class RecommendationEngine {
    public const WEIGHT_TEXT = 0.40;
    public const WEIGHT_CATEGORY = 0.25;
    public const WEIGHT_RECENCY = 0.20;
    public const WEIGHT_FREQUENCY = 0.15;

    public const DEFAULT_RECENCY_LAMBDA = 0.08; // ~0.57 after 7d, ~0.09 after 30d
    public const DEFAULT_FREQ_CAP = 10;
    public const CACHE_TTL_SECONDS = 180;

    /** Curated partial-similarity synonym groups (Vietnamese food domain). */
    private const SYNONYMS = [
        'ga ran' => ['ga chien', 'ga gion', 'ga sot', 'fried chicken', 'ga lac'],
        'ga chien' => ['ga ran', 'ga gion', 'ga sot', 'fried chicken'],
        'ga gion' => ['ga ran', 'ga chien', 'ga sot'],
        'ga sot' => ['ga ran', 'ga chien', 'ga gion'],
        'tra sua' => ['milk tea', 'tran chau', 'matcha', 'tra dao', 'tra trai cay'],
        'matcha' => ['tra sua', 'matcha latte', 'tra xanh'],
        'tra dao' => ['tra sua', 'tra trai cay', 'do uong'],
        'com tam' => ['com suon', 'com ga', 'com'],
        'com ga' => ['com', 'ga gion', 'ga ran'],
        'com' => ['com tam', 'com ga', 'com suon', 'bibimbap'],
        'pizza' => ['banh pizza', 'pizza hai san'],
        'mi cay' => ['mi kim chi', 'mi han', 'mi y', 'mi'],
        'mi' => ['mi cay', 'mi kim chi', 'mi y', 'ramen', 'udon'],
        'bun' => ['bun bo', 'bun cha', 'bun rieu', 'pho'],
        'lau' => ['lau bo', 'lau hai san', 'lau thai'],
        'an vat' => ['tokbokki', 'chuoi', 'nem', 'goi cuon', 'kem', 'sundae', 'banh'],
        'trang mieng' => ['kem', 'sundae', 'banh xoai', 'chuoi'],
    ];

    /**
     * Build personalized (or cold-start popular) recommendations for authenticated $userId.
     *
     * @return array{success: bool, type: string, title: string, data: array}
     */
    public static function recommend(
        PDO $db,
        int $userId,
        int $limit = 10,
        ?string $historyFile = null,
        ?string $prefsFile = null,
        ?string $cacheFile = null,
        ?int $now = null
    ): array {
        $now ??= time();
        $limit = max(1, min(20, $limit));

        // 1. Check per-user cache first (keyed by limit + userId)
        $prefs = SearchHistoryService::getPreferences($db, $userId, $prefsFile);
        $cacheKey = $userId . ':' . $limit . ':' . ($prefs['searchPersonalizationEnabled'] ? '1' : '0');
        if ($cacheFile !== null && is_file($cacheFile)) {
            $cached = self::readCache($cacheFile, $cacheKey, $now);
            if ($cached !== null) {
                return $cached;
            }
        }

        // 2. Fetch eligible real dishes from database
        $dishes = self::fetchEligibleDishes($db);
        if (!$dishes) {
            return [
                'success' => true,
                'type' => 'popular',
                'title' => 'Món phổ biến hôm nay',
                'data' => [],
            ];
        }

        // 3. Respect privacy setting: if disabled, strictly return non-personalized popular dishes
        if (!$prefs['searchPersonalizationEnabled']) {
            $result = self::buildPopularResponse($db, $dishes, $limit);
            if ($cacheFile !== null) self::writeCache($cacheFile, $cacheKey, $result, $now + self::CACHE_TTL_SECONDS);
            return $result;
        }

        // 4. Load recent search history for this user only
        $history = SearchHistoryService::list($db, $userId, 60, $historyFile, $prefsFile, $now);
        if (!$history) {
            $result = self::buildPopularResponse($db, $dishes, $limit);
            if ($cacheFile !== null) self::writeCache($cacheFile, $cacheKey, $result, $now + self::CACHE_TTL_SECONDS);
            return $result;
        }

        // 5. Build Search Interest Profile (group by normalizedQuery)
        $profile = self::buildInterestProfile($history, $now);
        if (!$profile) {
            $result = self::buildPopularResponse($db, $dishes, $limit);
            if ($cacheFile !== null) self::writeCache($cacheFile, $cacheKey, $result, $now + self::CACHE_TTL_SECONDS);
            return $result;
        }

        // 6. Score each eligible dish
        $scored = [];
        foreach ($dishes as $dish) {
            $bestScore = 0.0;
            $matchedQuery = '';
            foreach ($profile as $interest) {
                $textSim = self::textSimilarity($interest['query'], $dish);
                $catSim = self::categorySimilarity($interest['query'], $dish);
                if ($textSim <= 0.0 && $catSim <= 0.0) {
                    continue;
                }
                $recency = exp(-self::DEFAULT_RECENCY_LAMBDA * max(0.0, $interest['daysSince']));
                $frequency = min(1.0, log(1.0 + $interest['frequency']) / log(1.0 + self::DEFAULT_FREQ_CAP));
                $score = (self::WEIGHT_TEXT * $textSim)
                    + (self::WEIGHT_CATEGORY * $catSim)
                    + (self::WEIGHT_RECENCY * $recency)
                    + (self::WEIGHT_FREQUENCY * $frequency);
                if ($score > $bestScore) {
                    $bestScore = $score;
                    $matchedQuery = $interest['rawQuery'];
                }
            }
            if ($bestScore > 0.0) {
                $dish['_score'] = round($bestScore, 4);
                $dish['_matchedQuery'] = $matchedQuery;
                $scored[] = $dish;
            }
        }

        // If nothing in real catalog matches past searches -> honest cold-start fallback
        if (!$scored) {
            $result = self::buildPopularResponse($db, $dishes, $limit);
            if ($cacheFile !== null) self::writeCache($cacheFile, $cacheKey, $result, $now + self::CACHE_TTL_SECONDS);
            return $result;
        }

        usort($scored, static function (array $a, array $b): int {
            $cmp = $b['_score'] <=> $a['_score'];
            if ($cmp !== 0) return $cmp;
            $rcmp = ((float)$b['rating_avg']) <=> ((float)$a['rating_avg']);
            return $rcmp !== 0 ? $rcmp : ((int)$b['id'] <=> (int)$a['id']);
        });

        // 7. Apply diversity cap (avoid near-duplicate variants & single-restaurant dominance)
        $diverse = self::applyDiversity($scored, $limit);

        $result = [
            'success' => true,
            'type' => 'personalized',
            'title' => 'Dành riêng cho bạn',
            'data' => array_map([self::class, 'formatDish'], $diverse),
        ];

        if ($cacheFile !== null) {
            self::writeCache($cacheFile, $cacheKey, $result, $now + self::CACHE_TTL_SECONDS);
        }

        return $result;
    }

    /** Group raw search history rows into an aggregated interest profile. */
    public static function buildInterestProfile(array $history, int $now): array {
        $groups = [];
        foreach ($history as $item) {
            $norm = (string)($item['normalizedQuery'] ?? '');
            if ($norm === '') continue;
            $ts = strtotime((string)($item['searchedAt'] ?? '') . ' UTC');
            if ($ts === false) $ts = $now;
            if (!isset($groups[$norm])) {
                $groups[$norm] = [
                    'query' => $norm,
                    'rawQuery' => (string)($item['query'] ?? $norm),
                    'frequency' => 0,
                    'lastTs' => $ts,
                ];
            }
            $groups[$norm]['frequency']++;
            if ($ts > $groups[$norm]['lastTs']) {
                $groups[$norm]['lastTs'] = $ts;
                $groups[$norm]['rawQuery'] = (string)($item['query'] ?? $norm);
            }
        }
        foreach ($groups as &$g) {
            $g['daysSince'] = max(0.0, ($now - $g['lastTs']) / 86400.0);
        }
        unset($g);
        return array_values($groups);
    }

    /** Normalized text similarity in [0.0, 1.0] using exact match, token overlap, and curated synonyms. */
    public static function textSimilarity(string $normalizedQuery, array $dish): float {
        $name = SearchHistoryService::normalizeQuery((string)($dish['name'] ?? ''));
        $desc = SearchHistoryService::normalizeQuery((string)($dish['description'] ?? ''));
        $store = SearchHistoryService::normalizeQuery((string)($dish['restaurant_name'] ?? ''));
        $combined = trim($name . ' ' . $desc);
        if ($normalizedQuery === '' || $combined === '') return 0.0;

        // Exact or substring match in dish name
        if ($name === $normalizedQuery) return 1.0;
        if (str_contains(' ' . $name . ' ', ' ' . $normalizedQuery . ' ')) return 0.92;
        if (str_contains($name, $normalizedQuery)) return 0.85;

        // Exact phrase in description or store name
        if ($desc !== '' && str_contains(' ' . $desc . ' ', ' ' . $normalizedQuery . ' ')) return 0.65;
        if ($store !== '' && str_contains(' ' . $store . ' ', ' ' . $normalizedQuery . ' ')) return 0.55;

        // Token overlap against dish name & description (filter generic filler words)
        $generic = ['mon', 'an', 'do', 'banh', 'nuoc', 'quan', 'cua', 'cho', 'voi'];
        $qTokens = array_values(array_filter(
            explode(' ', $normalizedQuery),
            static fn($t) => mb_strlen($t) >= 2 && !in_array($t, $generic, true)
        ));
        if ($qTokens) {
            $nameTokens = explode(' ', $name);
            $allTokens = explode(' ', $combined);
            $nameHits = 0;
            $allHits = 0;
            foreach ($qTokens as $qt) {
                if (in_array($qt, $nameTokens, true)) $nameHits++;
                if (in_array($qt, $allTokens, true)) $allHits++;
            }
            $nameRatio = $nameHits / count($qTokens);
            $allRatio = $allHits / count($qTokens);
            if ($nameHits > 0 && $nameRatio >= 0.5) {
                return round(0.55 + 0.30 * $nameRatio, 4);
            }
            if ($allHits > 0 && $allRatio >= 0.5) {
                return round(0.35 + 0.25 * $allRatio, 4);
            }
        }

        // Curated synonym dictionary (partial relevance, never 1.0)
        $bestSyn = 0.0;
        foreach (self::SYNONYMS as $key => $syns) {
            if ($normalizedQuery === $key || str_contains(' ' . $normalizedQuery . ' ', ' ' . $key . ' ')) {
                foreach ($syns as $syn) {
                    if (str_contains(' ' . $name . ' ', ' ' . $syn . ' ')) {
                        $bestSyn = max($bestSyn, 0.68);
                    } elseif ($desc !== '' && str_contains(' ' . $desc . ' ', ' ' . $syn . ' ')) {
                        $bestSyn = max($bestSyn, 0.50);
                    }
                }
            }
        }

        return $bestSyn;
    }

    /** Category similarity in [0.0, 1.0] against menu_categories and platform_categories. */
    public static function categorySimilarity(string $normalizedQuery, array $dish): float {
        $menuCat = SearchHistoryService::normalizeQuery((string)($dish['category'] ?? ''));
        $platCat = SearchHistoryService::normalizeQuery((string)($dish['platform_category'] ?? ''));
        $cats = array_values(array_filter([$menuCat, $platCat], static fn($c) => $c !== ''));
        if (!$cats || $normalizedQuery === '') return 0.0;

        foreach ($cats as $cat) {
            if ($cat === $normalizedQuery) return 1.0;
            if (str_contains(' ' . $cat . ' ', ' ' . $normalizedQuery . ' ') || str_contains(' ' . $normalizedQuery . ' ', ' ' . $cat . ' ')) {
                return 0.80;
            }
        }

        // Check if query belongs to a curated synonym group matching the dish category
        foreach (self::SYNONYMS as $key => $syns) {
            $queryInGroup = ($normalizedQuery === $key || in_array($normalizedQuery, $syns, true));
            if (!$queryInGroup) continue;
            foreach ($cats as $cat) {
                if ($cat === $key || in_array($cat, $syns, true) || str_contains(' ' . $cat . ' ', ' ' . $key . ' ')) {
                    return 0.65;
                }
            }
        }

        return 0.0;
    }

    /** Limit near-duplicate variants and single-restaurant dominance while keeping high relevance. */
    public static function applyDiversity(array $ranked, int $limit, int $maxPerStore = 3): array {
        $selected = [];
        $overflow = [];
        $seenIds = [];
        $seenRoots = [];
        $storeCounts = [];

        foreach ($ranked as $dish) {
            $id = (string)$dish['id'];
            if (isset($seenIds[$id])) continue;
            $seenIds[$id] = true;

            $storeId = (string)$dish['store_id'];
            $root = self::dishNameRoot((string)$dish['name']);

            $rootCount = $seenRoots[$root] ?? 0;
            $storeCount = $storeCounts[$storeId] ?? 0;

            // Avoid duplicate near-identical variants (e.g., "Gà rán size S / M / L") and cap store dominance on first pass
            if ($rootCount >= 1 || $storeCount >= $maxPerStore) {
                $overflow[] = $dish;
                continue;
            }

            $selected[] = $dish;
            $seenRoots[$root] = $rootCount + 1;
            $storeCounts[$storeId] = $storeCount + 1;
            if (count($selected) >= $limit) break;
        }

        // If catalog is small and we still have slots, fill from overflow (still avoiding duplicate IDs)
        if (count($selected) < $limit && $overflow) {
            foreach ($overflow as $dish) {
                $selected[] = $dish;
                if (count($selected) >= $limit) break;
            }
        }

        return $selected;
    }

    /** Normalize dish name stripping size/combo suffixes to detect near-duplicate variants. */
    private static function dishNameRoot(string $name): string {
        $n = SearchHistoryService::normalizeQuery($name);
        $n = (string)preg_replace('/\b(size [sml]|co [sml]|combo \d+|phan \d+|nho|vua|lon|dac biet)\b/u', '', $n);
        return trim((string)preg_replace('/\s+/u', ' ', $n));
    }

    /** Fetch strictly eligible, orderable, non-deleted dishes with valid positive prices. */
    private static function fetchEligibleDishes(PDO $db): array {
        $sql = "SELECT d.id, d.store_id, d.name, d.description, d.price, d.image_url, d.rating_avg,
                       s.name AS restaurant_name,
                       COALESCE(mc.name, '') AS category,
                       COALESCE(pc.name, '') AS platform_category
                FROM dishes d
                JOIN stores s ON s.id = d.store_id
                JOIN merchants m ON m.id = s.merchant_id
                JOIN users owner ON owner.id = m.owner_user_id
                LEFT JOIN menu_categories mc ON mc.id = d.menu_category_id
                LEFT JOIN platform_categories pc ON pc.id = d.platform_category_id
                WHERE d.deleted_at IS NULL
                  AND d.is_available = 1
                  AND d.price > 0
                  AND (d.stock_quantity IS NULL OR d.stock_quantity > 0)
                  AND s.deleted_at IS NULL
                  AND s.status = 'active'
                  AND s.is_open = 1
                  AND m.status = 'active'
                  AND owner.status = 'active'
                  AND owner.deleted_at IS NULL
                ORDER BY d.rating_avg DESC, d.id DESC
                LIMIT 200";
        return $db->query($sql)->fetchAll(PDO::FETCH_ASSOC);
    }

    /** Cold-start fallback ranked by real valid order counts + real rating_avg (never synthetic sales). */
    private static function buildPopularResponse(PDO $db, array $dishes, int $limit): array {
        $orderCounts = [];
        try {
            $q = $db->query("SELECT oi.dish_id, SUM(oi.quantity) AS total_qty
                             FROM order_items oi
                             JOIN orders o ON o.id = oi.order_id
                             WHERE oi.dish_id IS NOT NULL AND o.status <> 'cancelled'
                             GROUP BY oi.dish_id");
            foreach ($q->fetchAll(PDO::FETCH_ASSOC) as $row) {
                $orderCounts[(string)$row['dish_id']] = (int)$row['total_qty'];
            }
        } catch (Throwable) {
            $orderCounts = [];
        }

        usort($dishes, static function (array $a, array $b) use ($orderCounts): int {
            $qa = $orderCounts[(string)$a['id']] ?? 0;
            $qb = $orderCounts[(string)$b['id']] ?? 0;
            if ($qa !== $qb) return $qb <=> $qa;
            $rc = ((float)$b['rating_avg']) <=> ((float)$a['rating_avg']);
            return $rc !== 0 ? $rc : ((int)$b['id'] <=> (int)$a['id']);
        });

        $diverse = self::applyDiversity($dishes, $limit);
        return [
            'success' => true,
            'type' => 'popular',
            'title' => 'Món phổ biến hôm nay',
            'data' => array_map([self::class, 'formatDish'], $diverse),
        ];
    }

    /** Format dish compatible with both Android Dish DTO and Master Prompt recommendation fields. */
    private static function formatDish(array $r): array {
        $idStr = (string)$r['id'];
        $storeStr = (string)$r['store_id'];
        $name = (string)$r['name'];
        $price = (float)$r['price'];
        $item = [
            'id' => $idStr,
            'foodId' => (int)$r['id'],
            'name' => $name,
            'foodName' => $name,
            'price' => $price,
            'imageUrl' => (string)($r['image_url'] ?? ''),
            'category' => (string)($r['category'] !== '' ? $r['category'] : ($r['platform_category'] ?? 'Tất cả')),
            'description' => (string)($r['description'] ?? ''),
            'available' => true,
            'restaurantId' => $storeStr,
            'restaurantName' => (string)($r['restaurant_name'] ?? ''),
        ];
        if (isset($r['_score'])) {
            $item['score'] = $r['_score'];
        }
        return $item;
    }

    private static function readCache(string $path, string $key, int $now): ?array {
        if (!is_file($path) || (@filesize($path) ?: 0) > 131072) return null;
        $entry = json_decode((string)@file_get_contents($path), true);
        if (!is_array($entry) || ($entry['key'] ?? '') !== $key || ($entry['expires'] ?? 0) <= $now || !is_array($entry['data'] ?? null)) {
            return null;
        }
        return $entry['data'];
    }

    private static function writeCache(string $path, string $key, array $data, int $expires): void {
        $tmp = $path . '.tmp-' . bin2hex(random_bytes(4));
        try {
            $raw = json_encode(['key' => $key, 'expires' => $expires, 'data' => $data], JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            if (@file_put_contents($tmp, $raw, LOCK_EX) !== false) {
                @chmod($tmp, 0600);
                @rename($tmp, $path);
            }
        } finally {
            if (is_file($tmp)) @unlink($tmp);
        }
    }
}
