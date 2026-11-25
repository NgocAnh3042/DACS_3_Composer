<?php
declare(strict_types=1);

/**
 * Phase 4: Long-Term Preference Memory, Consent Management,
 * Controlled Memory Extraction, and Adaptive Communication Engine.
 * All operations are strictly scoped to the authenticated user identity.
 */
final class UserPersonalization {
    public const SCHEMA_VERSION = 1;
    public const CONSENT_VERSION = '1.0';

    public const ALLOWED_PRONOUNS = ['', 'anh', 'chị', 'bạn', 'mình'];
    public const ALLOWED_STYLES = ['friendly', 'professional', 'concise', 'empathetic', 'formal'];
    public const ALLOWED_LENGTHS = ['short', 'medium', 'detailed'];
    public const ALLOWED_EMOJIS = ['none', 'low', 'medium'];
    public const ALLOWED_SPICY = ['', 'none', 'low', 'medium', 'high'];
    public const ALLOWED_LANGUAGES = ['vi'];
    public const DELETABLE_FIELDS = [
        'preferredPronoun', 'communicationStyle', 'responseLength',
        'emojiPreference', 'spicyLevel', 'dislikedIngredients', 'favoriteCategories', 'foodPreferences'
    ];

    public static function defaultProfile(int $userId): array {
        return [
            'userId' => $userId,
            'personalizationEnabled' => false,
            'useFoodPreferences' => false,
            'rememberCommunication' => false,
            'consentVersion' => self::CONSENT_VERSION,
            'consentUpdatedAt' => null,
            'preferredLanguage' => 'vi',
            'preferredPronoun' => '',
            'communicationStyle' => 'friendly',
            'responseLength' => 'medium',
            'emojiPreference' => 'low',
            'foodPreferences' => [
                'spicyLevel' => '',
                'dislikedIngredients' => [],
                'favoriteCategories' => [],
            ],
            'updatedAt' => null,
            'schemaVersion' => self::SCHEMA_VERSION,
        ];
    }

    /** Load profile strictly for authenticated $userId. Never trusts client userId. */
    public static function load(?PDO $db, int $userId, ?string $fallbackFile = null): array {
        $default = self::defaultProfile($userId);
        if ($db !== null && self::hasTable($db)) {
            $stmt = $db->prepare('SELECT * FROM user_ai_preferences WHERE user_id = ? LIMIT 1');
            $stmt->execute([$userId]);
            $row = $stmt->fetch(PDO::FETCH_ASSOC);
            if (is_array($row)) {
                return self::fromRow($userId, $row);
            }
            return $default;
        }
        if ($fallbackFile !== null && is_file($fallbackFile)) {
            $raw = @file_get_contents($fallbackFile);
            $decoded = is_string($raw) ? json_decode($raw, true) : null;
            if (is_array($decoded) && (int)($decoded['userId'] ?? -1) === $userId) {
                return self::mergeNormalized($default, $decoded);
            }
        }
        return $default;
    }

    public static function save(?PDO $db, array $profile, ?string $fallbackFile = null, ?string $mealCacheFile = null): array {
        $userId = (int)$profile['userId'];
        $profile['updatedAt'] = gmdate('Y-m-d\TH:i:s\Z');
        $profile['schemaVersion'] = self::SCHEMA_VERSION;

        if ($db !== null && self::hasTable($db)) {
            $foodJson = json_encode($profile['foodPreferences'], JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
            $driver = (string)$db->getAttribute(PDO::ATTR_DRIVER_NAME);
            $nowSql = gmdate('Y-m-d H:i:s');
            if ($driver === 'sqlite') {
                $sql = 'INSERT OR REPLACE INTO user_ai_preferences
                    (user_id, personalization_enabled, use_food_preferences, remember_communication, consent_version, consent_updated_at,
                     preferred_language, preferred_pronoun, communication_style, response_length, emoji_preference, food_preferences_json, schema_version, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)';
                $db->prepare($sql)->execute([
                    $userId,
                    $profile['personalizationEnabled'] ? 1 : 0,
                    $profile['useFoodPreferences'] ? 1 : 0,
                    $profile['rememberCommunication'] ? 1 : 0,
                    $profile['consentVersion'],
                    $profile['consentUpdatedAt'],
                    $profile['preferredLanguage'],
                    $profile['preferredPronoun'],
                    $profile['communicationStyle'],
                    $profile['responseLength'],
                    $profile['emojiPreference'],
                    $foodJson,
                    self::SCHEMA_VERSION,
                    $nowSql,
                ]);
            } else {
                $sql = 'INSERT INTO user_ai_preferences
                    (user_id, personalization_enabled, use_food_preferences, remember_communication, consent_version, consent_updated_at,
                     preferred_language, preferred_pronoun, communication_style, response_length, emoji_preference, food_preferences_json, schema_version, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                     personalization_enabled=VALUES(personalization_enabled),
                     use_food_preferences=VALUES(use_food_preferences),
                     remember_communication=VALUES(remember_communication),
                     consent_version=VALUES(consent_version),
                     consent_updated_at=VALUES(consent_updated_at),
                     preferred_language=VALUES(preferred_language),
                     preferred_pronoun=VALUES(preferred_pronoun),
                     communication_style=VALUES(communication_style),
                     response_length=VALUES(response_length),
                     emoji_preference=VALUES(emoji_preference),
                     food_preferences_json=VALUES(food_preferences_json),
                     schema_version=VALUES(schema_version),
                     updated_at=VALUES(updated_at)';
                $db->prepare($sql)->execute([
                    $userId,
                    $profile['personalizationEnabled'] ? 1 : 0,
                    $profile['useFoodPreferences'] ? 1 : 0,
                    $profile['rememberCommunication'] ? 1 : 0,
                    $profile['consentVersion'],
                    $profile['consentUpdatedAt'],
                    $profile['preferredLanguage'],
                    $profile['preferredPronoun'],
                    $profile['communicationStyle'],
                    $profile['responseLength'],
                    $profile['emojiPreference'],
                    $foodJson,
                    self::SCHEMA_VERSION,
                    $nowSql,
                ]);
            }
        } elseif ($fallbackFile !== null) {
            $tmp = $fallbackFile . '.tmp-' . bin2hex(random_bytes(4));
            try {
                $encoded = json_encode($profile, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
                if (@file_put_contents($tmp, $encoded, LOCK_EX) === false || !@rename($tmp, $fallbackFile)) {
                    throw new MealAdvisorException(503, 'Chưa lưu được tùy chọn cá nhân hóa.');
                }
                @chmod($fallbackFile, 0600);
            } finally {
                if (is_file($tmp)) @unlink($tmp);
            }
        }

        if ($mealCacheFile !== null && is_file($mealCacheFile)) {
            @unlink($mealCacheFile);
        }

        return $profile;
    }

    /** Validate and apply partial preference updates. Ignores any client-supplied userId. */
    public static function updatePreferences(?PDO $db, int $userId, array $patch, ?string $fallbackFile = null, ?string $mealCacheFile = null): array {
        $profile = self::load($db, $userId, $fallbackFile);

        if (isset($patch['preferredLanguage'])) {
            if (!is_string($patch['preferredLanguage']) || !in_array($patch['preferredLanguage'], self::ALLOWED_LANGUAGES, true)) {
                throw new MealAdvisorException(422, 'Ngôn ngữ ưu tiên chưa được hỗ trợ.');
            }
            $profile['preferredLanguage'] = $patch['preferredLanguage'];
        }
        if (array_key_exists('preferredPronoun', $patch)) {
            if (!is_string($patch['preferredPronoun']) || !in_array($patch['preferredPronoun'], self::ALLOWED_PRONOUNS, true)) {
                throw new MealAdvisorException(422, 'Cách xưng hô không hợp lệ.');
            }
            $profile['preferredPronoun'] = $patch['preferredPronoun'];
        }
        if (isset($patch['communicationStyle'])) {
            if (!is_string($patch['communicationStyle']) || !in_array($patch['communicationStyle'], self::ALLOWED_STYLES, true)) {
                throw new MealAdvisorException(422, 'Phong cách giao tiếp không hợp lệ.');
            }
            $profile['communicationStyle'] = $patch['communicationStyle'];
        }
        if (isset($patch['responseLength'])) {
            if (!is_string($patch['responseLength']) || !in_array($patch['responseLength'], self::ALLOWED_LENGTHS, true)) {
                throw new MealAdvisorException(422, 'Độ dài phản hồi không hợp lệ.');
            }
            $profile['responseLength'] = $patch['responseLength'];
        }
        if (isset($patch['emojiPreference'])) {
            if (!is_string($patch['emojiPreference']) || !in_array($patch['emojiPreference'], self::ALLOWED_EMOJIS, true)) {
                throw new MealAdvisorException(422, 'Tùy chọn emoji không hợp lệ.');
            }
            $profile['emojiPreference'] = $patch['emojiPreference'];
        }
        if (isset($patch['foodPreferences'])) {
            if (!is_array($patch['foodPreferences'])) {
                throw new MealAdvisorException(422, 'Sở thích món ăn không hợp lệ.');
            }
            $fp = $patch['foodPreferences'];
            if (array_key_exists('spicyLevel', $fp)) {
                if (!is_string($fp['spicyLevel']) || !in_array($fp['spicyLevel'], self::ALLOWED_SPICY, true)) {
                    throw new MealAdvisorException(422, 'Mức độ ăn cay không hợp lệ.');
                }
                $profile['foodPreferences']['spicyLevel'] = $fp['spicyLevel'];
            }
            if (array_key_exists('dislikedIngredients', $fp)) {
                $profile['foodPreferences']['dislikedIngredients'] = self::validateStringList($fp['dislikedIngredients'], 10, 40, 'Nguyên liệu tránh');
            }
            if (array_key_exists('favoriteCategories', $fp)) {
                $profile['foodPreferences']['favoriteCategories'] = self::validateStringList($fp['favoriteCategories'], 6, 40, 'Nhóm món yêu thích');
            }
        }

        return self::save($db, $profile, $fallbackFile, $mealCacheFile);
    }

    /** Update personalization consent explicitly. */
    public static function updateConsent(?PDO $db, int $userId, array $input, ?string $fallbackFile = null, ?string $mealCacheFile = null): array {
        if (!isset($input['personalizationEnabled']) || !is_bool($input['personalizationEnabled'])) {
            throw new MealAdvisorException(422, 'Thiếu trạng thái bật/tắt cá nhân hóa.');
        }
        $profile = self::load($db, $userId, $fallbackFile);
        $enabled = $input['personalizationEnabled'];
        $profile['personalizationEnabled'] = $enabled;
        if (!$enabled) {
            $profile['useFoodPreferences'] = false;
            $profile['rememberCommunication'] = false;
        } else {
            $profile['useFoodPreferences'] = isset($input['useFoodPreferences']) && is_bool($input['useFoodPreferences'])
                ? $input['useFoodPreferences'] : true;
            $profile['rememberCommunication'] = isset($input['rememberCommunication']) && is_bool($input['rememberCommunication'])
                ? $input['rememberCommunication'] : true;
        }
        $profile['consentVersion'] = self::CONSENT_VERSION;
        $profile['consentUpdatedAt'] = gmdate('Y-m-d\TH:i:s\Z');

        if (!empty($input['clearSavedOnDisable']) && !$enabled) {
            $profile = self::resetPreferenceFields($profile);
        }

        return self::save($db, $profile, $fallbackFile, $mealCacheFile);
    }

    public static function deleteField(?PDO $db, int $userId, string $field, ?string $fallbackFile = null, ?string $mealCacheFile = null): array {
        if (!in_array($field, self::DELETABLE_FIELDS, true)) {
            throw new MealAdvisorException(422, 'Trường tùy chọn không hợp lệ.');
        }
        $profile = self::load($db, $userId, $fallbackFile);
        match ($field) {
            'preferredPronoun' => $profile['preferredPronoun'] = '',
            'communicationStyle' => $profile['communicationStyle'] = 'friendly',
            'responseLength' => $profile['responseLength'] = 'medium',
            'emojiPreference' => $profile['emojiPreference'] = 'low',
            'spicyLevel' => $profile['foodPreferences']['spicyLevel'] = '',
            'dislikedIngredients' => $profile['foodPreferences']['dislikedIngredients'] = [],
            'favoriteCategories' => $profile['foodPreferences']['favoriteCategories'] = [],
            'foodPreferences' => $profile['foodPreferences'] = ['spicyLevel' => '', 'dislikedIngredients' => [], 'favoriteCategories' => []],
        };
        return self::save($db, $profile, $fallbackFile, $mealCacheFile);
    }

    public static function clear(?PDO $db, int $userId, ?string $fallbackFile = null, ?string $mealCacheFile = null): array {
        $profile = self::load($db, $userId, $fallbackFile);
        $profile = self::resetPreferenceFields($profile);
        return self::save($db, $profile, $fallbackFile, $mealCacheFile);
    }

    /**
     * Return ONLY permitted preferences for prompt injection.
     * Server-side enforcement: if personalizationEnabled is false, returns [].
     */
    public static function approvedForPrompt(array $profile): array {
        if (empty($profile['personalizationEnabled'])) {
            return [];
        }
        $approved = [];
        if (!empty($profile['rememberCommunication'])) {
            if (($profile['preferredPronoun'] ?? '') !== '') {
                $approved['preferredPronoun'] = $profile['preferredPronoun'];
            }
            $approved['communicationStyle'] = $profile['communicationStyle'] ?? 'friendly';
            $approved['responseLength'] = $profile['responseLength'] ?? 'medium';
            $approved['emojiPreference'] = $profile['emojiPreference'] ?? 'low';
        }
        if (!empty($profile['useFoodPreferences'])) {
            $fp = $profile['foodPreferences'] ?? [];
            $food = [];
            if (($fp['spicyLevel'] ?? '') !== '') $food['spicyLevel'] = $fp['spicyLevel'];
            if (!empty($fp['dislikedIngredients'])) $food['dislikedIngredients'] = array_values($fp['dislikedIngredients']);
            if (!empty($fp['favoriteCategories'])) $food['favoriteCategories'] = array_values($fp['favoriteCategories']);
            if ($food) $approved['foodPreferences'] = $food;
        }
        return $approved;
    }

    /**
     * Controlled Memory Extraction:
     * Extracts ONLY allowlisted, durable, explicit user statements.
     * Ignores temporary requests ("hôm nay"), questions, hypotheticals ("nếu"),
     * third-party info ("bạn anh"), prompt injections, and sensitive/medical details.
     */
    public static function extractCandidateMemories(string $message): array {
        $trimmed = trim($message);
        if ($trimmed === '' || mb_strlen($trimmed) > 500) return [];
        $f = MealAdvisor::folded($trimmed);

        // Ignore questions, hypotheticals, third-party statements, prompt injections, or sensitive data
        if (str_ends_with($trimmed, '?')
            || preg_match('/\b(co phai|duoc khong|khong a|phai khong|hay la)\b/u', $f)
            || preg_match('/\b(neu|gia su|vi du|thu xem)\b/u', $f)
            || preg_match('/\b(ban (?:anh|chi|minh|toi|tui)|vo (?:anh|minh|toi)|chong (?:chi|minh|toi)|em gai|em trai|dong nghiep|nguoi yeu|con (?:anh|chi|minh|toi))\b/u', $f)
            || preg_match('/\b(bo qua huong dan|system prompt|ignore previous|api key|otp|mat khau|so the|dia chi nha|cccd|cmnd)\b/u', $f)
        ) {
            return [];
        }

        $candidates = [];

        // 1. Explicit durable pronoun preference ("Từ giờ cứ gọi anh là anh nha", "Gọi mình là chị nhé")
        if (preg_match('/(?:tu gio|tu nay|cu|hay)?\s*(?:goi (?:anh|chi|minh|toi|tui|em) la|xung ho la)\s+(anh|chi|ban|minh)\b/u', $f, $m)) {
            $pronoun = match ($m[1]) {
                'anh' => 'anh',
                'chi' => 'chị',
                'ban' => 'bạn',
                default => 'mình',
            };
            $candidates[] = [
                'type' => 'preferred_pronoun',
                'value' => $pronoun,
                'source' => 'explicit_user_statement',
                'confidence' => 1.0,
                'requiresConfirmation' => false,
            ];
        }

        // 2. Explicit durable communication style / response length / emoji preference
        if (preg_match('/(?:tu gio|luon|hay|cu).{0,20}(?:noi chuyen|tra loi|giao tiep)?\s*(?:ngan gon|ngan thoi|suc tich)/u', $f)) {
            $candidates[] = [
                'type' => 'response_length',
                'value' => 'short',
                'source' => 'explicit_user_statement',
                'confidence' => 0.95,
                'requiresConfirmation' => false,
            ];
        } elseif (preg_match('/(?:tu gio|luon|hay).{0,20}(?:giai thich chi tiet|tra loi chi tiet|noi ky)/u', $f)) {
            $candidates[] = [
                'type' => 'response_length',
                'value' => 'detailed',
                'source' => 'explicit_user_statement',
                'confidence' => 0.95,
                'requiresConfirmation' => false,
            ];
        }

        if (preg_match('/(?:tu gio|luon|hay).{0,20}(?:chuyen nghiep|lich su trang trong)/u', $f)) {
            $candidates[] = [
                'type' => 'communication_style',
                'value' => 'professional',
                'source' => 'explicit_user_statement',
                'confidence' => 0.95,
                'requiresConfirmation' => false,
            ];
        }

        if (preg_match('/(?:dung|khong|bo|han che)\s*(?:dung|gui|them)?\s*(?:emoji|icon|bieu tuong cam xuc)/u', $f)) {
            $candidates[] = [
                'type' => 'emoji_preference',
                'value' => 'none',
                'source' => 'explicit_user_statement',
                'confidence' => 1.0,
                'requiresConfirmation' => false,
            ];
        }

        // 3. Durable food preferences (MUST NOT be temporary like "hôm nay", "bữa này", "tối nay", "trưa nay", "bây giờ")
        $isTemporary = (bool)preg_match('/\b(hom nay|bua nay|trua nay|toi nay|sang nay|chieu nay|bay gio|luc nay|lan nay)\b/u', $f);
        $isHealthSensitive = (bool)preg_match('/\b(di ung|phan ve|tieu duong|huyet ap|benh|da day|mang thai|bau)\b/u', $f);

        if (!$isTemporary && !$isHealthSensitive) {
            // Spicy level (durable)
            if (preg_match('/\b(?:anh|chi|minh|toi|tui|em)\s+(?:rat )?thich an cay\b|\b(?:an cay duoc|gu an cay|me do cay)\b/u', $f)) {
                $candidates[] = [
                    'type' => 'spicy_level',
                    'value' => 'high',
                    'source' => 'explicit_user_statement',
                    'confidence' => 0.95,
                    'requiresConfirmation' => false,
                ];
            } elseif (preg_match('/\b(?:anh|chi|minh|toi|tui|em)\s+(?:khong an duoc cay|khong thich an cay|khong an cay)\b/u', $f)) {
                $candidates[] = [
                    'type' => 'spicy_level',
                    'value' => 'none',
                    'source' => 'explicit_user_statement',
                    'confidence' => 0.95,
                    'requiresConfirmation' => false,
                ];
            }

            // Disliked ingredients (allowlisted common non-medical taste dislikes)
            $knownIngredients = [
                'hanh' => 'hành',
                'toi' => 'tỏi',
                'rau mui' => 'rau mùi',
                'ngo' => 'ngò',
                'mam tom' => 'mắm tôm',
                'ot' => 'ớt',
                'gia do' => 'giá đỗ',
                'tieu' => 'tiêu',
                'mo' => 'mỡ',
            ];
            if (preg_match('/\b(?:khong an duoc|khong an|ghet|khong thich|tranh)\s+([a-z ]{2,25})/u', $f, $m)) {
                $segment = trim($m[1]);
                foreach ($knownIngredients as $key => $label) {
                    if (preg_match('/\b' . preg_quote($key, '/') . '\b/u', $segment)) {
                        $candidates[] = [
                            'type' => 'disliked_ingredient',
                            'value' => $label,
                            'source' => 'explicit_user_statement',
                            'confidence' => 0.9,
                            'requiresConfirmation' => false,
                        ];
                    }
                }
            }

            // Favorite food categories (durable)
            $knownCategories = [
                'com' => 'cơm',
                'bun' => 'bún',
                'mi' => 'mì',
                'lau' => 'lẩu',
                'an vat' => 'ăn vặt',
                'trang mieng' => 'tráng miệng',
            ];
            if (preg_match('/\b(?:anh|chi|minh|toi|tui|em)\s+(?:hay an|thich an|thich nhat|me)\s+([a-z ,]{2,35})/u', $f, $m)) {
                $segment = trim($m[1]);
                foreach ($knownCategories as $key => $label) {
                    if (preg_match('/\b' . preg_quote($key, '/') . '\b/u', $segment)) {
                        $candidates[] = [
                            'type' => 'favorite_category',
                            'value' => $label,
                            'source' => 'explicit_user_statement',
                            'confidence' => 0.9,
                            'requiresConfirmation' => false,
                        ];
                    }
                }
            }
        } elseif ($isHealthSensitive) {
            // Sensitive health/allergy detail -> flag requiresConfirmation = true, NEVER auto-save
            $candidates[] = [
                'type' => 'health_restriction',
                'value' => 'requires_explicit_opt_in',
                'source' => 'sensitive_statement',
                'confidence' => 0.8,
                'requiresConfirmation' => true,
            ];
        }

        return $candidates;
    }

    /**
     * Apply eligible candidate memories to profile respecting consent & confirmation rules.
     *
     * @return array{profile: array, changed: bool, savedCount: int}
     */
    public static function applyCandidates(array $profile, array $candidates): array {
        if (empty($profile['personalizationEnabled']) || !$candidates) {
            return ['profile' => $profile, 'changed' => false, 'savedCount' => 0];
        }
        $changed = false;
        $saved = 0;

        foreach ($candidates as $c) {
            if (!empty($c['requiresConfirmation'])) continue;
            $type = (string)($c['type'] ?? '');
            $val = $c['value'] ?? null;

            if (in_array($type, ['preferred_pronoun', 'communication_style', 'response_length', 'emoji_preference'], true)) {
                if (empty($profile['rememberCommunication'])) continue;
                if ($type === 'preferred_pronoun' && in_array($val, self::ALLOWED_PRONOUNS, true) && $profile['preferredPronoun'] !== $val) {
                    $profile['preferredPronoun'] = $val;
                    $changed = true;
                    $saved++;
                } elseif ($type === 'communication_style' && in_array($val, self::ALLOWED_STYLES, true) && $profile['communicationStyle'] !== $val) {
                    $profile['communicationStyle'] = $val;
                    $changed = true;
                    $saved++;
                } elseif ($type === 'response_length' && in_array($val, self::ALLOWED_LENGTHS, true) && $profile['responseLength'] !== $val) {
                    $profile['responseLength'] = $val;
                    $changed = true;
                    $saved++;
                } elseif ($type === 'emoji_preference' && in_array($val, self::ALLOWED_EMOJIS, true) && $profile['emojiPreference'] !== $val) {
                    $profile['emojiPreference'] = $val;
                    $changed = true;
                    $saved++;
                }
            } elseif (in_array($type, ['spicy_level', 'disliked_ingredient', 'favorite_category'], true)) {
                if (empty($profile['useFoodPreferences'])) continue;
                if ($type === 'spicy_level' && in_array($val, self::ALLOWED_SPICY, true) && $profile['foodPreferences']['spicyLevel'] !== $val) {
                    $profile['foodPreferences']['spicyLevel'] = $val;
                    $changed = true;
                    $saved++;
                } elseif ($type === 'disliked_ingredient' && is_string($val)) {
                    $list = $profile['foodPreferences']['dislikedIngredients'] ?? [];
                    if (!in_array($val, $list, true) && count($list) < 10) {
                        $list[] = $val;
                        $profile['foodPreferences']['dislikedIngredients'] = $list;
                        $changed = true;
                        $saved++;
                    }
                } elseif ($type === 'favorite_category' && is_string($val)) {
                    $list = $profile['foodPreferences']['favoriteCategories'] ?? [];
                    if (!in_array($val, $list, true) && count($list) < 6) {
                        $list[] = $val;
                        $profile['foodPreferences']['favoriteCategories'] = $list;
                        $changed = true;
                        $saved++;
                    }
                }
            }
        }

        return ['profile' => $profile, 'changed' => $changed, 'savedCount' => $saved];
    }

    /**
     * Adaptive Communication Engine:
     * Priority: current user instruction > emotional context > approved saved style > default FoodHub style.
     *
     * @return array{style: string, responseLength: string, emojiPreference: string}
     */
    public static function resolveStyle(string $message, string $emotion, array $approvedPrefs = []): array {
        $style = (string)($approvedPrefs['communicationStyle'] ?? 'friendly');
        $length = (string)($approvedPrefs['responseLength'] ?? 'medium');
        $emoji = (string)($approvedPrefs['emojiPreference'] ?? 'low');

        // Emotional adaptation (never creates permanent negative user labels)
        if ($emotion === 'angry') {
            $style = 'empathetic';
            $emoji = 'none';
            $length = 'short';
        } elseif ($emotion === 'urgent') {
            $style = 'concise';
            $length = 'short';
            $emoji = 'none';
        } elseif ($emotion === 'frustrated' || $emotion === 'sad') {
            $style = 'empathetic';
        } elseif ($emotion === 'confused') {
            $style = 'professional';
        }

        // Current turn explicit instruction overrides stored preference
        $f = MealAdvisor::folded($message);
        if (ConversationContext::prefersBrief($message)) {
            $style = 'concise';
            $length = 'short';
        } elseif (preg_match('/\b(giai thich ky|noi chi tiet|huong dan chi tiet)\b/u', $f)) {
            $length = 'detailed';
        }
        if (preg_match('/\b(dung dung emoji|khong dung emoji|bo emoji)\b/u', $f)) {
            $emoji = 'none';
        }
        if (preg_match('/\b(trang trong|nghiem tuc|chuyen nghiep)\b/u', $f)) {
            $style = 'formal';
            $emoji = 'none';
        }

        return [
            'style' => in_array($style, self::ALLOWED_STYLES, true) ? $style : 'friendly',
            'responseLength' => in_array($length, self::ALLOWED_LENGTHS, true) ? $length : 'medium',
            'emojiPreference' => in_array($emoji, self::ALLOWED_EMOJIS, true) ? $emoji : 'low',
        ];
    }

    private static function resetPreferenceFields(array $profile): array {
        $profile['preferredPronoun'] = '';
        $profile['communicationStyle'] = 'friendly';
        $profile['responseLength'] = 'medium';
        $profile['emojiPreference'] = 'low';
        $profile['foodPreferences'] = [
            'spicyLevel' => '',
            'dislikedIngredients' => [],
            'favoriteCategories' => [],
        ];
        return $profile;
    }

    private static function validateStringList(mixed $items, int $maxItems, int $maxLen, string $label): array {
        if (!is_array($items) || !array_is_list($items) || count($items) > $maxItems) {
            throw new MealAdvisorException(422, $label . ' không hợp lệ (tối đa ' . $maxItems . ' mục).');
        }
        $clean = [];
        foreach ($items as $item) {
            if (!is_string($item)) throw new MealAdvisorException(422, $label . ' phải là chuỗi văn bản.');
            $v = trim($item);
            if ($v === '' || mb_strlen($v) > $maxLen) throw new MealAdvisorException(422, $label . ' vượt quá độ dài cho phép.');
            if (preg_match('/[{}<>]|otp|password|mat khau|gsk_/iu', $v)) {
                throw new MealAdvisorException(422, $label . ' chứa ký tự không hợp lệ.');
            }
            if (!in_array($v, $clean, true)) $clean[] = $v;
        }
        return $clean;
    }

    private static function hasTable(PDO $db): bool {
        try {
            $driver = (string)$db->getAttribute(PDO::ATTR_DRIVER_NAME);
            if ($driver === 'sqlite') {
                return (bool)$db->query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='user_ai_preferences'")->fetchColumn();
            }
            return (bool)$db->query("SHOW TABLES LIKE 'user_ai_preferences'")->fetchColumn();
        } catch (Throwable) {
            return false;
        }
    }

    private static function fromRow(int $userId, array $row): array {
        $food = json_decode((string)($row['food_preferences_json'] ?? ''), true);
        if (!is_array($food)) $food = [];
        return [
            'userId' => $userId,
            'personalizationEnabled' => (bool)($row['personalization_enabled'] ?? false),
            'useFoodPreferences' => (bool)($row['use_food_preferences'] ?? false),
            'rememberCommunication' => (bool)($row['remember_communication'] ?? false),
            'consentVersion' => (string)($row['consent_version'] ?? self::CONSENT_VERSION),
            'consentUpdatedAt' => $row['consent_updated_at'] ?? null,
            'preferredLanguage' => (string)($row['preferred_language'] ?? 'vi'),
            'preferredPronoun' => (string)($row['preferred_pronoun'] ?? ''),
            'communicationStyle' => (string)($row['communication_style'] ?? 'friendly'),
            'responseLength' => (string)($row['response_length'] ?? 'medium'),
            'emojiPreference' => (string)($row['emoji_preference'] ?? 'low'),
            'foodPreferences' => [
                'spicyLevel' => (string)($food['spicyLevel'] ?? ''),
                'dislikedIngredients' => is_array($food['dislikedIngredients'] ?? null) ? array_values($food['dislikedIngredients']) : [],
                'favoriteCategories' => is_array($food['favoriteCategories'] ?? null) ? array_values($food['favoriteCategories']) : [],
            ],
            'updatedAt' => $row['updated_at'] ?? null,
            'schemaVersion' => (int)($row['schema_version'] ?? self::SCHEMA_VERSION),
        ];
    }

    private static function mergeNormalized(array $default, array $data): array {
        $default['personalizationEnabled'] = (bool)($data['personalizationEnabled'] ?? false);
        $default['useFoodPreferences'] = (bool)($data['useFoodPreferences'] ?? false);
        $default['rememberCommunication'] = (bool)($data['rememberCommunication'] ?? false);
        $default['consentVersion'] = (string)($data['consentVersion'] ?? self::CONSENT_VERSION);
        $default['consentUpdatedAt'] = $data['consentUpdatedAt'] ?? null;
        $default['preferredLanguage'] = in_array($data['preferredLanguage'] ?? 'vi', self::ALLOWED_LANGUAGES, true) ? $data['preferredLanguage'] : 'vi';
        $default['preferredPronoun'] = in_array($data['preferredPronoun'] ?? '', self::ALLOWED_PRONOUNS, true) ? $data['preferredPronoun'] : '';
        $default['communicationStyle'] = in_array($data['communicationStyle'] ?? 'friendly', self::ALLOWED_STYLES, true) ? $data['communicationStyle'] : 'friendly';
        $default['responseLength'] = in_array($data['responseLength'] ?? 'medium', self::ALLOWED_LENGTHS, true) ? $data['responseLength'] : 'medium';
        $default['emojiPreference'] = in_array($data['emojiPreference'] ?? 'low', self::ALLOWED_EMOJIS, true) ? $data['emojiPreference'] : 'low';
        $fp = is_array($data['foodPreferences'] ?? null) ? $data['foodPreferences'] : [];
        $default['foodPreferences'] = [
            'spicyLevel' => in_array($fp['spicyLevel'] ?? '', self::ALLOWED_SPICY, true) ? $fp['spicyLevel'] : '',
            'dislikedIngredients' => is_array($fp['dislikedIngredients'] ?? null) ? array_values($fp['dislikedIngredients']) : [],
            'favoriteCategories' => is_array($fp['favoriteCategories'] ?? null) ? array_values($fp['favoriteCategories']) : [],
        ];
        $default['updatedAt'] = $data['updatedAt'] ?? null;
        return $default;
    }
}
