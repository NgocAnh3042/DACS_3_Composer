-- FoodHub AI Phase 4 & 5: Personalization Preferences & AI Quality Feedback
-- Safe additive migration (CREATE TABLE IF NOT EXISTS). No destructive changes.

CREATE TABLE IF NOT EXISTS user_ai_preferences (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    personalization_enabled TINYINT(1) NOT NULL DEFAULT 0,
    use_food_preferences TINYINT(1) NOT NULL DEFAULT 0,
    remember_communication TINYINT(1) NOT NULL DEFAULT 0,
    consent_version VARCHAR(32) NOT NULL DEFAULT '1.0',
    consent_updated_at DATETIME NULL,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'vi',
    preferred_pronoun VARCHAR(16) NOT NULL DEFAULT '',
    communication_style VARCHAR(24) NOT NULL DEFAULT 'friendly',
    response_length VARCHAR(16) NOT NULL DEFAULT 'medium',
    emoji_preference VARCHAR(16) NOT NULL DEFAULT 'low',
    food_preferences_json TEXT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_ai_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    rating ENUM('positive', 'negative') NOT NULL,
    reason VARCHAR(48) NULL,
    prompt_version VARCHAR(32) NOT NULL DEFAULT '2.1.0',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_feedback_user_msg (user_id, conversation_id, message_id),
    KEY idx_ai_feedback_created (created_at),
    KEY idx_ai_feedback_version (prompt_version, rating),
    CONSTRAINT fk_ai_feedback_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
