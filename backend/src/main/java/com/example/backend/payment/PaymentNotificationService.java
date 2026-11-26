package com.example.backend.payment;

import com.example.backend.notification.FcmNotificationEvent;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class PaymentNotificationService {
    private final JdbcTemplate jdbc;private final ApplicationEventPublisher events;
    public PaymentNotificationService(JdbcTemplate jdbc,ApplicationEventPublisher events){this.jdbc=jdbc;this.events=events;}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void publishPaidNotifications(PaymentSettledEvent event){
        String customerTitle="Thanh toán thành công",customerBody="Thanh toán đơn hàng đã được PayOS xác nhận.";
        jdbc.update("INSERT INTO notifications(user_id,type,title,body,related_order_id) VALUES(?,'payment',?,?,?)",event.customerId(),customerTitle,customerBody,event.orderId());
        jdbc.update("INSERT INTO notifications(user_id,type,title,body,related_order_id) VALUES(?,'new_order','Đơn hàng đã thanh toán','Đơn hàng thanh toán online đã được xác nhận.',?)",event.ownerId(),event.orderId());
        events.publishEvent(new FcmNotificationEvent(List.of(event.customerId()),"payment",customerTitle,customerBody,event.orderId()));
        events.publishEvent(new FcmNotificationEvent(List.of(event.ownerId()),"new_order","Đơn hàng đã thanh toán","Đơn hàng thanh toán online đã được xác nhận.",event.orderId()));
    }
}
