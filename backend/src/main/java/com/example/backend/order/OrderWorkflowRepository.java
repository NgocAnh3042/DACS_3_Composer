package com.example.backend.order;

import com.example.backend.order.OrderDtos.OrderItem;
import com.example.backend.order.OrderDtos.OrderView;
import com.example.backend.order.OrderDtos.AvailableOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderWorkflowRepository {
    private final JdbcTemplate jdbc;
    public OrderWorkflowRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<OrderView> list(long userId, String role, Long orderId, int limit, int offset) {
        String sql = """
                SELECT o.id,o.placed_at,COALESCE(o.delivered_at,completed_log.completed_at) completed_at,
                       o.status,o.payment_method,o.payment_status,
                       o.subtotal,o.delivery_fee,o.total_amount,o.customer_id,o.receiver_name,o.receiver_phone,
                       o.delivery_address,o.delivery_lat,o.delivery_lng,o.store_id,s.name store_name,
                       s.latitude restaurant_lat,s.longitude restaurant_lng,s.cover_url restaurant_cover,
                       cu.firebase_uid customer_uid,su.firebase_uid shipper_uid
                FROM orders o JOIN stores s ON s.id=o.store_id JOIN users cu ON cu.id=o.customer_id
                LEFT JOIN deliveries de ON de.order_id=o.id LEFT JOIN drivers dr ON dr.id=de.driver_id
                LEFT JOIN users su ON su.id=dr.user_id
                LEFT JOIN (SELECT order_id,MAX(created_at) completed_at FROM order_status_logs
                           WHERE status='completed' GROUP BY order_id) completed_log ON completed_log.order_id=o.id
                """;
        List<Object> args = new ArrayList<>();
        if ("customer".equals(role)) { sql += " WHERE o.customer_id=?"; args.add(userId); }
        else if ("seller".equals(role)) {
            sql += " JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=?"; args.add(userId);
        } else if ("driver".equals(role)) {
            sql += " WHERE (dr.user_id=? OR (o.status='accepted' AND de.driver_id IS NULL))"; args.add(userId);
        }
        if (orderId != null) { sql += args.isEmpty() ? " WHERE" : " AND"; sql += " o.id=?"; args.add(orderId); }
        sql += " ORDER BY o.placed_at DESC,o.id DESC";
        if (orderId == null) { sql += " LIMIT ? OFFSET ?"; args.add(limit); args.add(offset); }
        List<OrderView> rows = jdbc.query(sql, (rs, n) -> new OrderView(
                Long.toString(rs.getLong("id")), rs.getString("placed_at"),
                rs.getString("completed_at"), rs.getString("status").toUpperCase(),
                rs.getString("payment_method").toUpperCase(), "paid".equals(rs.getString("payment_status")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("delivery_fee"), rs.getBigDecimal("total_amount"),
                rs.getString("customer_uid"), rs.getString("receiver_name"), rs.getString("receiver_phone"),
                rs.getString("delivery_address"), nullableDouble(rs, "delivery_lat"), nullableDouble(rs, "delivery_lng"),
                Long.toString(rs.getLong("store_id")), rs.getString("store_name"),
                nullableDouble(rs, "restaurant_lat"), nullableDouble(rs, "restaurant_lng"),
                value(rs.getString("restaurant_cover")), value(rs.getString("shipper_uid")), List.of()), args.toArray());
        if (rows.isEmpty()) return rows;
        List<Long> ids = rows.stream().map(row -> Long.valueOf(row.id())).toList();
        String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<Long,List<OrderItem>> items = new HashMap<>();
        jdbc.query("SELECT oi.order_id,oi.dish_id,oi.dish_name,oi.quantity,oi.unit_price,d.image_url "
                + "FROM order_items oi LEFT JOIN dishes d ON d.id=oi.dish_id WHERE oi.order_id IN ("+marks+") ORDER BY oi.order_id,oi.id",
                (RowCallbackHandler) rs -> items.computeIfAbsent(rs.getLong("order_id"), key -> new ArrayList<>()).add(
                        new OrderItem(rs.getString("dish_id") == null ? "" : Long.toString(rs.getLong("dish_id")),
                                rs.getString("dish_name"),rs.getInt("quantity"),rs.getBigDecimal("unit_price"),value(rs.getString("image_url")))), ids.toArray());
        return rows.stream().map(o -> new OrderView(o.id(),o.time(),o.completedAt(),o.status(),o.paymentMethod(),o.isPaid(),
                o.totalDishPrice(),o.shippingFee(),o.totalPrice(),o.userId(),o.customerName(),o.customerPhone(),o.customerAddress(),
                o.customerLat(),o.customerLng(),o.restaurantId(),o.restaurantName(),o.restaurantLat(),o.restaurantLng(),
                o.restaurantCoverImage(),o.shipperId(),items.getOrDefault(Long.valueOf(o.id()),List.of()))).toList();
    }

    public List<AvailableOrder> available(int limit,int offset) {
        return jdbc.query("""
                SELECT o.id,o.placed_at,o.delivered_at,o.status,o.payment_method,o.payment_status,o.subtotal,
                       o.delivery_fee,o.total_amount,o.receiver_name,o.receiver_phone,o.delivery_address,
                       o.delivery_lat,o.delivery_lng,o.store_id,s.name store_name,s.latitude restaurant_lat,
                       s.longitude restaurant_lng
                FROM orders o JOIN stores s ON s.id=o.store_id JOIN deliveries d ON d.order_id=o.id
                WHERE o.status='accepted' AND d.driver_id IS NULL
                ORDER BY o.placed_at,o.id LIMIT ? OFFSET ?
                """,(rs,n)->new AvailableOrder(Long.toString(rs.getLong("id")),rs.getString("placed_at"),
                rs.getString("delivered_at"),rs.getString("status").toUpperCase(),rs.getString("payment_method").toUpperCase(),
                "paid".equals(rs.getString("payment_status")),rs.getBigDecimal("subtotal"),rs.getBigDecimal("delivery_fee"),
                rs.getBigDecimal("total_amount"),rs.getString("receiver_name"),rs.getString("receiver_phone"),
                rs.getString("delivery_address"),nullableDouble(rs,"delivery_lat"),nullableDouble(rs,"delivery_lng"),
                Long.toString(rs.getLong("store_id")),rs.getString("store_name"),nullableDouble(rs,"restaurant_lat"),
                nullableDouble(rs,"restaurant_lng"),"",List.of()),limit,offset);
    }

    public StoreRow storeForOrder(long storeId) {
        return jdbc.query("SELECT id,name,latitude,longitude FROM stores WHERE id=? AND is_open=1 AND status='active' AND deleted_at IS NULL FOR UPDATE",
                rs -> rs.next() ? new StoreRow(rs.getLong("id"),rs.getString("name"),nullableDouble(rs,"latitude"),nullableDouble(rs,"longitude")) : null, storeId);
    }
    public Map<Long,DishRow> orderDishes(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String marks=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        Map<Long,DishRow> found=new HashMap<>();
        jdbc.query("SELECT id,name,price,store_id FROM dishes WHERE id IN ("+marks+") AND is_available=1 AND deleted_at IS NULL", (RowCallbackHandler) rs ->
                found.put(rs.getLong("id"),new DishRow(rs.getLong("id"),rs.getString("name"),rs.getBigDecimal("price"),rs.getLong("store_id"))), ids.toArray());
        return found;
    }
    public long insertOrder(String code,long customerId,long storeId,String status,String name,String phone,String address,
            BigDecimal lat,BigDecimal lng,String note,BigDecimal subtotal,BigDecimal fee,BigDecimal discount,
            BigDecimal total,String method) {
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO orders(code,customer_id,store_id,status,receiver_name,receiver_phone,delivery_address,delivery_lat,delivery_lng,customer_note,subtotal,delivery_fee,discount_amount,total_amount,payment_method,payment_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'pending')", new String[]{"id"});
            ps.setString(1,code);ps.setLong(2,customerId);ps.setLong(3,storeId);ps.setString(4,status);ps.setString(5,name);ps.setString(6,phone);ps.setString(7,address);ps.setBigDecimal(8,lat);ps.setBigDecimal(9,lng);ps.setString(10,note);ps.setBigDecimal(11,subtotal);ps.setBigDecimal(12,fee);ps.setBigDecimal(13,discount);ps.setBigDecimal(14,total);ps.setString(15,method);return ps;},key);
        Number id=key.getKey(); if(id==null) throw new IllegalStateException("Không lấy được mã đơn hàng"); return id.longValue();
    }
    public void insertItem(long orderId,DishRow dish,int quantity) {
        jdbc.update("INSERT INTO order_items(order_id,dish_id,dish_name,unit_price,quantity,line_total) VALUES(?,?,?,?,?,?)",
                orderId,dish.id(),dish.name(),dish.price(),quantity,dish.price().multiply(BigDecimal.valueOf(quantity)));
    }
    public void insertRelated(long orderId,long userId,String status,BigDecimal fee,String paymentMethod) {
        jdbc.update("INSERT INTO order_status_logs(order_id,status,actor_user_id) VALUES(?,?,?)",orderId,status,userId);
        jdbc.update("INSERT INTO deliveries(order_id,status,delivery_fee) VALUES(?,'unassigned',?)",orderId,fee);
        jdbc.update("INSERT INTO payments(order_id,provider,method,amount,status) SELECT ?,?,?,total_amount,'pending' FROM orders WHERE id=?",orderId,paymentMethod,paymentMethod,orderId);
    }
    public VoucherRow lockVoucher(long voucherId,long storeId) {
        return jdbc.query("SELECT id,discount_type,discount_value,max_discount,min_order_amount,usage_limit,used_count FROM vouchers WHERE id=? AND is_active=1 AND (store_id IS NULL OR store_id=?) AND (starts_at IS NULL OR starts_at<=CURRENT_TIMESTAMP) AND (ends_at IS NULL OR ends_at>=CURRENT_TIMESTAMP) FOR UPDATE",
                rs -> rs.next()?new VoucherRow(rs.getLong("id"),rs.getString("discount_type"),rs.getBigDecimal("discount_value"),rs.getBigDecimal("max_discount"),rs.getBigDecimal("min_order_amount"),(Integer)rs.getObject("usage_limit"),rs.getInt("used_count")):null,voucherId,storeId);
    }
    public void useVoucher(long orderId,long voucherId,BigDecimal discount) {
        jdbc.update("INSERT INTO order_vouchers(order_id,voucher_id,discount_amount) VALUES(?,?,?)",orderId,voucherId,discount);
        jdbc.update("UPDATE vouchers SET used_count=used_count+1 WHERE id=?",voucherId);
    }
    public OrderRow lockOrder(long id) {
        return jdbc.query("SELECT id,customer_id,store_id,status FROM orders WHERE id=? FOR UPDATE",
                rs -> rs.next()?new OrderRow(rs.getLong("id"),rs.getLong("customer_id"),rs.getLong("store_id"),rs.getString("status")):null,id);
    }
    public boolean ownsStore(long storeId,long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=? AND m.owner_user_id=?)",Boolean.class,storeId,userId));
    }
    public boolean assignedDriver(long orderId,long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM deliveries d JOIN drivers dr ON dr.id=d.driver_id WHERE d.order_id=? AND dr.user_id=?)",Boolean.class,orderId,userId));
    }
    public void updateOrderStatus(long id,String status,String cancelReason) {
        String timestamp=switch(status){case "accepted","processing"->",accepted_at=CURRENT_TIMESTAMP";case "shipping"->",picked_up_at=CURRENT_TIMESTAMP";case "completed"->",delivered_at=CURRENT_TIMESTAMP";case "cancelled"->",cancelled_at=CURRENT_TIMESTAMP";default->"";};
        jdbc.update("UPDATE orders SET status=?"+timestamp+",cancel_reason=? WHERE id=?",status,cancelReason,id);
    }
    public void updateDeliveryStatus(long id,String status) {
        switch(status){case "shipping"->jdbc.update("UPDATE deliveries SET status='picked_up',picked_up_at=CURRENT_TIMESTAMP WHERE order_id=?",id);
            case "completed"->{jdbc.update("UPDATE deliveries SET status='delivered',delivered_at=CURRENT_TIMESTAMP WHERE order_id=?",id);jdbc.update("UPDATE drivers SET completed_trips=completed_trips+1,work_status='available' WHERE id=(SELECT driver_id FROM deliveries WHERE order_id=?) AND EXISTS(SELECT 1 FROM deliveries WHERE order_id=? AND driver_id IS NOT NULL)",id,id);}
            case "cancelled"->jdbc.update("UPDATE deliveries SET status='cancelled' WHERE order_id=?",id); default -> {}}
    }
    public void insertStatusLog(long id,String status,String note,long userId){jdbc.update("INSERT INTO order_status_logs(order_id,status,note,actor_user_id) VALUES(?,?,?,?)",id,status,note,userId);}
    public Long notifyStoreOwner(long storeId,long orderId,String title,String body){
        List<Long> owners=jdbc.query("SELECT m.owner_user_id FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=?",(rs,n)->rs.getLong(1),storeId);
        if(owners.isEmpty()) return null;
        Long ownerId=owners.getFirst();
        jdbc.update("INSERT INTO notifications(user_id,type,title,body,related_order_id) VALUES(?,'new_order',?,?,?)",ownerId,title,body,orderId);
        return ownerId;
    }
    public void notifyCustomer(long customerId,long orderId,String status){
        jdbc.update("INSERT INTO notifications(user_id,type,title,body,related_order_id) VALUES(?,'order_status','Cập nhật đơn hàng',?,?)",customerId,"Đơn hàng của bạn: "+status.replace('_',' '),orderId);
    }
    public String customerName(long userId){return jdbc.query("SELECT name FROM users WHERE id=?",rs->rs.next()?rs.getString(1):"",userId);}
    public String customerPhone(long userId){return jdbc.query("SELECT phone FROM users WHERE id=?",rs->rs.next()?rs.getString(1):"",userId);}
    public long ensureDriver(long userId) {
        jdbc.update("INSERT INTO drivers(user_id,work_status) VALUES(?,'offline') ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",userId);
        return jdbc.queryForObject("SELECT id FROM drivers WHERE user_id=?",Long.class,userId);
    }
    public DriverRow driver(long userId){return jdbc.queryForObject("SELECT id,vehicle_model,vehicle_type,work_status,completed_trips FROM drivers WHERE user_id=?",(rs,n)->new DriverRow(rs.getLong("id"),value(rs.getString("vehicle_model")),value(rs.getString("vehicle_type")),rs.getString("work_status"),rs.getInt("completed_trips")),userId);}
    public void updateDriver(long userId,String status,String model,String type){jdbc.update("UPDATE drivers SET work_status=?,vehicle_model=COALESCE(?,vehicle_model),vehicle_type=COALESCE(?,vehicle_type) WHERE user_id=?",status,model,type,userId);}
    public long lockDriver(long userId){List<Long> ids=jdbc.query("SELECT id FROM drivers WHERE user_id=? AND work_status='available' FOR UPDATE",(rs,n)->rs.getLong(1),userId);return ids.isEmpty()?0:ids.getFirst();}
    public boolean lockAssignableOrder(long orderId){List<Long> ids=jdbc.query("SELECT d.order_id FROM deliveries d JOIN orders o ON o.id=d.order_id WHERE d.order_id=? AND d.driver_id IS NULL AND o.status='accepted' FOR UPDATE",(rs,n)->rs.getLong(1),orderId);return !ids.isEmpty();}
    public int assign(long driverId,long orderId){return jdbc.update("UPDATE deliveries SET driver_id=?,status='assigned',assigned_at=CURRENT_TIMESTAMP WHERE order_id=? AND driver_id IS NULL",driverId,orderId);}
    public void markDriverBusy(long id){jdbc.update("UPDATE drivers SET work_status='busy' WHERE id=?",id);}
    private static Double nullableDouble(java.sql.ResultSet rs,String c)throws java.sql.SQLException{double v=rs.getDouble(c);return rs.wasNull()?null:v;}
    private static String value(String v){return v==null?"":v;}
    public record StoreRow(long id,String name,Double lat,Double lng){}
    public record DishRow(long id,String name,BigDecimal price,long storeId){}
    public record VoucherRow(long id,String type,BigDecimal value,BigDecimal maxDiscount,BigDecimal minimum,Integer limit,int used){}
    public record OrderRow(long id,long customerId,long storeId,String status){}
    public record DriverRow(long id,String model,String type,String status,int trips){}
}
