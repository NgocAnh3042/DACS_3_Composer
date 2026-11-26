package com.example.backend.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentSettledListener {
    private static final Logger log=LoggerFactory.getLogger(PaymentSettledListener.class);
    private final PaymentNotificationService notifications;
    public PaymentSettledListener(PaymentNotificationService notifications){this.notifications=notifications;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterCommit(PaymentSettledEvent event){
        try{notifications.publishPaidNotifications(event);}
        catch(RuntimeException exception){log.warn("Payment notification creation failed ({})",exception.getClass().getSimpleName());}
    }
}
