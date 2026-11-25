package com.example.backend.catalog;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FoodCatalogService {
    private final FoodCatalogRepository repository;
    private final double deliveryFee;

    public FoodCatalogService(FoodCatalogRepository repository,
            @Value("${foodhub.delivery-fee:15000}") double deliveryFee) {
        this.repository = repository;
        this.deliveryFee = deliveryFee;
    }

    public List<CategoryDto> categories(String restaurantId, String limit, String offset) {
        return repository.categories(parseLong(restaurantId, 0), pageLimit(limit), pageOffset(offset));
    }

    public List<StoreCardDto> stores(String lat, String lng, String limit, String offset) {
        Double userLat = numeric(lat);
        Double userLng = numeric(lng);
        return repository.stores(pageLimit(limit), pageOffset(offset)).stream()
                .map(row -> storeCard(row, userLat, userLng)).toList();
    }

    public StoreDetailDto store(String id, String lat, String lng) {
        long storeId = parseRequiredId(id, "Cửa hàng không hợp lệ");
        FoodCatalogRepository.StoreRow row = repository.store(storeId);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng");
        StoreCardDto card = storeCard(row, numeric(lat), numeric(lng));
        return new StoreDetailDto(card.id(), card.name(), card.rating(), card.address(), card.deliveryTime(),
                card.distance(), card.distanceKm(), card.shippingFee(), card.description(), card.coverImage(),
                card.avatarUrl(), card.email(), card.phone(), card.openTime(), card.closeTime(),
                card.latitude(), card.longitude(), row.isOpen());
    }

    public List<DishListDto> dishes(String restaurantId, String category, String includeUnavailable,
            String limit, String offset, FirebasePrincipal principal) {
        boolean include = "1".equals(includeUnavailable);
        long storeId = parseLong(restaurantId, 0);
        if (include) {
            boolean admin = "admin".equals(principal.databaseRole());
            boolean owner = storeId > 0 && principal.userId() != null
                    && repository.ownsStore(storeId, principal.userId());
            if (!admin && !owner) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không quản lý cửa hàng này");
            }
        }
        return repository.dishes(storeId, category, include, pageLimit(limit), pageOffset(offset)).stream()
                .map(FoodCatalogService::dishListDto).toList();
    }

    public DishDetailDto dish(String id, FirebasePrincipal principal) {
        long dishId = parseRequiredId(id, "ID món không hợp lệ");
        FoodCatalogRepository.DishRow row = repository.dish(dishId);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn");
        if (!row.available()) {
            boolean admin = "admin".equals(principal.databaseRole());
            boolean owner = principal.userId() != null && principal.userId().equals(row.ownerUserId());
            if (!admin && !owner) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không quản lý cửa hàng này");
            }
        }
        return new DishDetailDto(Long.toString(row.id()), row.name(), row.price(), row.imageUrl(),
                category(row), row.description(), row.available(), Long.toString(row.restaurantId()),
                row.restaurantName(), row.restaurantAddress(), row.restaurantLogo(), row.restaurantOpen(),
                row.ratingAvg(), row.ratingCount());
    }

    public List<PromotionDto> promotions(FirebasePrincipal principal) {
        return repository.promotions("admin".equals(principal.databaseRole()));
    }

    private StoreCardDto storeCard(FoodCatalogRepository.StoreRow row, Double userLat, Double userLng) {
        Double distanceKm = distanceKm(row.latitude(), row.longitude(), userLat, userLng);
        String distance = distanceKm == null ? "" : String.format(Locale.US, "%.1f km", distanceKm);
        String deliveryTime = distanceKm == null
                ? (row.prepMinutes() == null ? "15" : Integer.toString(row.prepMinutes()))
                : deliveryEstimate(distanceKm, row.prepMinutes());
        return new StoreCardDto(Long.toString(row.id()), row.name(), row.rating(), row.address(), deliveryTime,
                distance, distanceKm, shippingFee(distanceKm), row.description(), row.coverImage(),
                row.avatarUrl(), row.email(), row.phone(), row.openTime(), row.closeTime(),
                row.latitude(), row.longitude());
    }

    private static DishListDto dishListDto(FoodCatalogRepository.DishRow row) {
        return new DishListDto(Long.toString(row.id()), row.name(), row.price(), row.imageUrl(), category(row),
                row.description(), row.available(), Long.toString(row.restaurantId()));
    }

    private static String category(FoodCatalogRepository.DishRow row) {
        return !row.menuCategoryName().isEmpty() ? row.menuCategoryName()
                : row.platformCategoryId() == null ? "" : Long.toString(row.platformCategoryId());
    }

    private double shippingFee(Double distanceKm) {
        if (distanceKm == null || distanceKm <= 3.0) return deliveryFee;
        int extraKm = Math.max(1, (int) Math.ceil(Math.round((distanceKm - 3.0) * 10000.0) / 10000.0 - 1e-9));
        return deliveryFee + extraKm * 10000.0;
    }

    private static String deliveryEstimate(double distanceKm, Integer prepMinutes) {
        int prep = prepMinutes == null || prepMinutes <= 0 || prepMinutes >= 28
                ? 12 : Math.max(8, Math.min(25, prepMinutes));
        int travel = (int) Math.ceil(distanceKm * 2.5) + 3;
        int min = Math.max(12, prep + travel);
        int max = min + (distanceKm > 5.0 ? 7 : 5);
        return min + "–" + max + " phút";
    }

    private static Double distanceKm(Double storeLat, Double storeLng, Double userLat, Double userLng) {
        if (storeLat == null || storeLng == null || userLat == null || userLng == null) return null;
        if (!Double.isFinite(storeLat) || !Double.isFinite(storeLng)
                || !Double.isFinite(userLat) || !Double.isFinite(userLng)) return null;
        if ((Math.abs(storeLat) < 0.0001 && Math.abs(storeLng) < 0.0001)
                || (Math.abs(userLat) < 0.0001 && Math.abs(userLng) < 0.0001)) return null;
        if (storeLat < -90 || storeLat > 90 || userLat < -90 || userLat > 90
                || storeLng < -180 || storeLng > 180 || userLng < -180 || userLng > 180) return null;
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(userLat - storeLat);
        double dLng = Math.toRadians(userLng - storeLng);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(storeLat)) * Math.cos(Math.toRadians(userLat))
                * Math.pow(Math.sin(dLng / 2), 2);
        double raw = 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
        return Math.round(Math.max(0.1, raw) * 10.0) / 10.0;
    }

    private static int pageLimit(String value) {
        long parsed = parseLong(value, 100);
        return (int) Math.max(1, Math.min(200, parsed));
    }

    private static int pageOffset(String value) {
        long parsed = parseLong(value, 0);
        return (int) Math.max(0, Math.min(1_000_000, parsed));
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? (long) parsed : fallback;
        }
        catch (NumberFormatException exception) { return fallback; }
    }

    private static long parseRequiredId(String value, String message) {
        if (value == null || !value.matches("\\d+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, message); }
    }

    private static Double numeric(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Double.valueOf(value); } catch (NumberFormatException exception) { return null; }
    }
}
