package com.example.backend.payment;

import com.example.backend.payment.PaymentDtos.PaymentItem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;
    public PaymentRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public PaymentRow ownedPayosPayment(long orderId,long customerId){
        List<PaymentRow> rows=jdbc.query("SELECT p.id,p.order_id,p.amount,p.status,p.method,p.provider,p.payment_link_id,o.total_amount,o.status order_status,o.customer_id,o.code,m.owner_user_id FROM payments p JOIN orders o ON o.id=p.order_id JOIN stores s ON s.id=o.store_id JOIN merchants m ON m.id=s.merchant_id WHERE p.order_id=? AND o.customer_id=? AND o.payment_method='payos' AND p.provider='payos' LIMIT 1",
                (rs,n)->new PaymentRow(rs.getLong("id"),rs.getLong("order_id"),rs.getBigDecimal("amount"),rs.getString("status"),rs.getString("method"),rs.getString("provider"),rs.getString("payment_link_id"),rs.getBigDecimal("total_amount"),rs.getString("order_status"),rs.getLong("customer_id"),rs.getString("code"),rs.getLong("owner_user_id")),orderId,customerId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public int saveLink(long paymentId,long orderCode,String paymentLinkId,String checkoutUrl){
        return jdbc.update("UPDATE payments SET payos_order_code=?,payment_link_id=?,checkout_url=?,status='processing' WHERE id=? AND status<>'paid'",orderCode,paymentLinkId,checkoutUrl,paymentId);
    }
    public List<PaymentItem> listForCustomer(long userId,String firebaseUid){
        return jdbc.query("SELECT p.id,p.order_id,p.amount,p.payment_link_id,p.gateway_txn_id,p.method,p.status,p.created_at,p.updated_at,o.code FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.customer_id=? ORDER BY p.created_at DESC LIMIT 200",
                (rs,n)->new PaymentItem(Long.toString(rs.getLong("id")),firebaseUid,Long.toString(rs.getLong("order_id")),rs.getBigDecimal("amount").doubleValue(),
                        value(rs.getString("payment_link_id")),rs.getString("gateway_txn_id"),rs.getString("method").toUpperCase(java.util.Locale.ROOT),
                        "Thanh toán đơn #"+rs.getString("code"),rs.getString("status").toUpperCase(java.util.Locale.ROOT),epoch(rs.getTimestamp("created_at").toLocalDateTime()),epoch(rs.getTimestamp("updated_at").toLocalDateTime())),userId);
    }
    public WebhookPayment lockByPaymentLink(String linkId){
        List<WebhookPayment> rows=jdbc.query("SELECT p.id,p.order_id,p.amount,p.status,p.payment_link_id,o.total_amount,o.status order_status,o.customer_id,m.owner_user_id FROM payments p JOIN orders o ON o.id=p.order_id JOIN stores s ON s.id=o.store_id JOIN merchants m ON m.id=s.merchant_id WHERE p.payment_link_id=? AND p.provider='payos' LIMIT 1 FOR UPDATE",
                (rs,n)->new WebhookPayment(rs.getLong("id"),rs.getLong("order_id"),rs.getBigDecimal("amount"),rs.getString("status"),rs.getBigDecimal("total_amount"),rs.getString("order_status"),rs.getLong("customer_id"),rs.getLong("owner_user_id")),linkId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean insertWebhookEvent(long paymentId,String eventId,String payloadHash,String eventType){
        try{jdbc.update("INSERT INTO payment_webhook_events(payment_id,provider_event_id,payload_hash,event_type,processed_at,process_status) VALUES(?,?,?,?,CURRENT_TIMESTAMP,'processed')",paymentId,eventId,payloadHash,eventType);return true;}
        catch(DuplicateKeyException duplicate){return false;}
    }
    public int markPaidIfUnpaid(WebhookPayment payment,String transactionId){
        return jdbc.update("UPDATE payments SET status='paid',gateway_txn_id=?,paid_at=CURRENT_TIMESTAMP WHERE id=? AND status<>'paid'",transactionId,payment.paymentId());
    }
    public void markOrderPaid(long orderId){jdbc.update("UPDATE orders SET payment_status='paid',status=CASE WHEN status='pending_payment' THEN 'pending' ELSE status END WHERE id=?",orderId);}
    public void insertPendingStatusLog(long orderId){jdbc.update("INSERT INTO order_status_logs(order_id,status,note) VALUES(?,'pending','PayOS đã xác nhận thanh toán')",orderId);}
    private static long epoch(LocalDateTime value){return value.atZone(java.time.ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli();}
    private static String value(String value){return value==null?"":value;}
    public record PaymentRow(long paymentId,long orderId,java.math.BigDecimal amount,String status,String method,String provider,
            String paymentLinkId,java.math.BigDecimal orderTotal,String orderStatus,long customerId,String orderCode,long ownerId){}
    public record WebhookPayment(long paymentId,long orderId,java.math.BigDecimal amount,String paymentStatus,
            java.math.BigDecimal orderTotal,String orderStatus,long customerId,long ownerId){}
}
