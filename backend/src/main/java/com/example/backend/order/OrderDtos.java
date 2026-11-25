package com.example.backend.order;

import java.math.BigDecimal;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() { }

    public record CreateRequest(Long restaurantId, List<ItemRequest> items, String customerName,
            String customerPhone, String customerAddress, BigDecimal customerLat, BigDecimal customerLng,
            String note, String paymentMethod, Long appliedPromotionId) { }
    public record ItemRequest(Long dishId, Integer quantity) { }
    public record StatusRequest(String status, String cancelReason, String note) { }
    public record OrderSummary(String id, String status, BigDecimal totalPrice, String restaurantId) { }
    public record OrderItem(String dishId, String name, int quantity, BigDecimal price, String imageUrl) { }
    public record OrderView(String id, String time, String completedAt, String status, String paymentMethod,
            boolean isPaid, BigDecimal totalDishPrice, BigDecimal shippingFee, BigDecimal totalPrice,
            String userId, String customerName, String customerPhone, String customerAddress,
            Double customerLat, Double customerLng, String restaurantId, String restaurantName,
            Double restaurantLat, Double restaurantLng, String restaurantCoverImage, String shipperId,
            List<OrderItem> items) { }
    public record DriverView(String id, String userId, String vehicleName, String vehicleType,
            boolean isAvailable, String workStatus, int completedTrips) { }
    public record AvailableOrder(String id,String time,String completedAt,String status,String paymentMethod,
            boolean isPaid,BigDecimal totalDishPrice,BigDecimal shippingFee,BigDecimal totalPrice,
            String customerName,String customerPhone,String customerAddress,Double customerLat,Double customerLng,
            String restaurantId,String restaurantName,Double restaurantLat,Double restaurantLng,String shipperId,
            List<OrderItem> items) { }
}
