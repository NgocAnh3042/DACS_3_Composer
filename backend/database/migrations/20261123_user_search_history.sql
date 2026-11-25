-- FoodHub Personalized Food Recommendations: Search History & Privacy Preferences
-- Safe additive migration (CREATE TABLE IF NOT EXISTS). Do not run blindly on production without review.

CREATE TABLE IF NOT EXISTS user_search_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    search_query VARCHAR(255) NOT NULL,
    normalized_query VARCHAR(255) NOT NULL,
    searched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_searched (user_id, searched_at),
    KEY idx_user_query (user_id, normalized_query),
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_recommendation_preferences (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    search_personalization_enabled TINYINT(1) NOT NULL DEFAULT 1,
    retention_days SMALLINT UNSIGNED NOT NULL DEFAULT 90,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rec_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
