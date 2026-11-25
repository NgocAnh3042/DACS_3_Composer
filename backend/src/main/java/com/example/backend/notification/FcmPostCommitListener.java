package com.example.backend.notification;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FcmPostCommitListener {
    private static final Logger log=LoggerFactory.getLogger(FcmPostCommitListener.class);
    private final NotificationRepository repository;
    private final FcmClient fcmClient;
    public FcmPostCommitListener(NotificationRepository repository,FcmClient fcmClient){this.repository=repository;this.fcmClient=fcmClient;}

    @Async("fcmTaskExecutor")
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void deliver(FcmNotificationEvent event){
        try {
            List<NotificationRepository.DeviceToken> tokens=repository.tokensForUsers(event.recipientUserIds());
            for(var token:tokens){
                try {
                    FcmSendResult result=fcmClient.send(token.value(),event.title(),event.body(),event.type(),
                            event.orderId()==null?"":Long.toString(event.orderId()));
                    if(result==FcmSendResult.UNREGISTERED)repository.deleteToken(token.value());
                } catch(RuntimeException exception) {
                    log.warn("Push notification delivery failed ({})",exception.getClass().getSimpleName());
                }
            }
        } catch(RuntimeException exception) {
            log.warn("Push notification dispatch failed ({})",exception.getClass().getSimpleName());
        }
    }
}
