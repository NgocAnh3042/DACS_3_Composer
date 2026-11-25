package com.example.backend.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FoodCatalogRepository {
    private static final RowMapper<StoreRow> STORE_ROW = (rs, row) -> storeRow(rs);
    private static final RowMapper<DishRow> DISH_ROW = (rs, row) -> dishRow(rs);
    private static final RowMapper<PromotionDto> PROMOTION_ROW = (rs, row) -> new PromotionDto(
            Long.toString(rs.getLong("id")), text(rs, "code"), text(rs, "image_url"),
            text(rs, "discount_type"), rs.getBigDecimal("discount_value").doubleValue(),
            number(rs, "max_discount"), rs.getBigDecimal("min_order_amount").doubleValue(),
            rs.getLong("used_count"), numberLong(rs, "usage_limit"), text(rs, "description"),
            text(rs, "title"), rs.getBoolean("is_active") ? "active" : "expired",
            datetime(rs, "starts_at"), datetime(rs, "ends_at"));

    private final JdbcTemplate jdbc;

    public FoodCatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<StoreRow> stores(int limit, int offset) {
        return jdbc.query("""
                SELECT s.id, s.name, s.rating_avg, s.address_line, s.avg_prep_minutes,
                       s.latitude, s.longitude, s.description, s.cover_url, s.logo_url,
                       s.email, s.phone, s.open_time, s.close_time, s.is_open
                FROM stores s
                WHERE s.status = 'active' AND s.deleted_at IS NULL
                ORDER BY s.name, s.id LIMIT ? OFFSET ?
                """, STORE_ROW, limit, offset);
    }

    public StoreRow store(long id) {
        List<StoreRow> rows = jdbc.query("""
                SELECT s.id, s.name, s.rating_avg, s.address_line, s.avg_prep_minutes,
                       s.latitude, s.longitude, s.description, s.cover_url, s.logo_url,
                       s.email, s.phone, s.open_time, s.close_time, s.is_open
                FROM stores s WHERE s.id = ? AND s.deleted_at IS NULL
                """, STORE_ROW, id);
        return rows.stream().findFirst().orElse(null);
    }

    public List<CategoryDto> categories(long restaurantId, int limit, int offset) {
        String sql = """
                SELECT mc.name, COUNT(d.id) AS dish_count
                FROM menu_categories mc
                JOIN stores s ON s.id = mc.store_id
                LEFT JOIN dishes d ON d.menu_category_id = mc.id
                     AND d.deleted_at IS NULL AND d.is_available = TRUE
                WHERE mc.is_active = TRUE AND s.deleted_at IS NULL AND s.status = 'active'
                """;
        List<Object> params = new ArrayList<>();
        if (restaurantId > 0) {
            sql += " AND mc.store_id = ?";
            params.add(restaurantId);
        }
        sql += " GROUP BY mc.name ORDER BY dish_count DESC, mc.name ASC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return jdbc.query(sql, (rs, row) -> new CategoryDto(rs.getString("name"), rs.getLong("dish_count")),
                params.toArray());
    }

    public List<DishRow> dishes(long restaurantId, String category, boolean includeUnavailable,
            int limit, int offset) {
        String sql = """
                SELECT d.id, d.name, d.price, d.image_url, d.platform_category_id,
                       d.description, d.is_available, d.store_id, mc.name AS menu_category_name
                FROM dishes d
                JOIN stores s ON s.id = d.store_id
                LEFT JOIN menu_categories mc ON mc.id = d.menu_category_id
                WHERE d.deleted_at IS NULL AND s.status = 'active'
                """;
        List<Object> params = new ArrayList<>();
        if (!includeUnavailable) sql += " AND d.is_available = TRUE";
        if (restaurantId > 0) {
            sql += " AND d.store_id = ?";
            params.add(restaurantId);
        }
        if (category != null && !category.isBlank()) {
            if (category.matches("\\d+")) {
                sql += " AND (d.platform_category_id = ? OR d.menu_category_id = ?)";
                long categoryId = parseLongOrZero(category);
                params.add(categoryId);
                params.add(categoryId);
            } else {
                sql += " AND LOWER(mc.name) = LOWER(?)";
                params.add(category.trim());
            }
        }
        sql += " ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return jdbc.query(sql, DISH_ROW, params.toArray());
    }

    public DishRow dish(long id) {
        List<DishRow> rows = jdbc.query("""
                SELECT d.id, d.name, d.price, d.image_url, d.platform_category_id,
                       d.description, d.is_available, d.store_id, mc.name AS menu_category_name,
                       s.name AS restaurant_name, s.address_line AS restaurant_address,
                       s.logo_url AS restaurant_logo, s.is_open AS restaurant_open,
                       d.rating_avg, d.rating_count, m.owner_user_id
                FROM dishes d
                JOIN stores s ON s.id = d.store_id
                JOIN merchants m ON m.id = s.merchant_id
                LEFT JOIN menu_categories mc ON mc.id = d.menu_category_id
                WHERE d.id = ? AND d.deleted_at IS NULL AND s.status = 'active'
                """, (rs, row) -> dishRow(rs, true), id);
        return rows.stream().findFirst().orElse(null);
    }

    public boolean ownsStore(long storeId, long userId) {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stores s JOIN merchants m ON m.id = s.merchant_id
                WHERE s.id = ? AND m.owner_user_id = ?
                """, Integer.class, storeId, userId);
        return exists != null && exists > 0;
    }

    public List<PromotionDto> promotions(boolean admin) {
        String sql = """
                SELECT id, code, image_url, discount_type, discount_value, max_discount,
                       min_order_amount, used_count, usage_limit, description, title,
                       is_active, starts_at, ends_at
                FROM vouchers
                """;
        if (!admin) {
            sql += " WHERE is_active = TRUE AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)"
                    + " AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)";
        }
        sql += " ORDER BY created_at DESC";
        return jdbc.query(sql, PROMOTION_ROW);
    }

    private static StoreRow storeRow(ResultSet rs) throws SQLException {
        return new StoreRow(rs.getLong("id"), text(rs, "name"), rs.getBigDecimal("rating_avg").doubleValue(),
                text(rs, "address_line"), nullableInt(rs, "avg_prep_minutes"), nullableDouble(rs, "latitude"),
                nullableDouble(rs, "longitude"), text(rs, "description"), text(rs, "cover_url"),
                text(rs, "logo_url"), text(rs, "email"), text(rs, "phone"),
                text(rs, "open_time"), text(rs, "close_time"), rs.getBoolean("is_open"));
    }

    private static DishRow dishRow(ResultSet rs) throws SQLException { return dishRow(rs, false); }

    private static DishRow dishRow(ResultSet rs, boolean detail) throws SQLException {
        return new DishRow(rs.getLong("id"), text(rs, "name"), rs.getBigDecimal("price").doubleValue(),
                text(rs, "image_url"), nullableLong(rs, "platform_category_id"), text(rs, "menu_category_name"),
                text(rs, "description"), rs.getBoolean("is_available"), rs.getLong("store_id"),
                detail ? text(rs, "restaurant_name") : null,
                detail ? text(rs, "restaurant_address") : null,
                detail ? text(rs, "restaurant_logo") : null,
                detail ? rs.getBoolean("restaurant_open") : null,
                detail ? rs.getBigDecimal("rating_avg").doubleValue() : null,
                detail ? rs.getInt("rating_count") : null,
                detail ? rs.getLong("owner_user_id") : null);
    }

    private static String text(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static double number(ResultSet rs, String column) throws SQLException {
        var value = rs.getBigDecimal(column);
        return value == null ? 0d : value.doubleValue();
    }

    private static long numberLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? 0L : value;
    }

    private static String datetime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static long parseLongOrZero(String value) {
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
    }

    public record StoreRow(long id, String name, double rating, String address, Integer prepMinutes,
            Double latitude, Double longitude, String description, String coverImage, String avatarUrl,
            String email, String phone, String openTime, String closeTime, boolean isOpen) { }

    public record DishRow(long id, String name, double price, String imageUrl, Long platformCategoryId,
            String menuCategoryName, String description, boolean available, long restaurantId,
            String restaurantName, String restaurantAddress, String restaurantLogo,
            Boolean restaurantOpen, Double ratingAvg, Integer ratingCount, Long ownerUserId) { }
}
