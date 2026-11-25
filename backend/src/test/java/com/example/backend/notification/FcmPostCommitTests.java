package com.example.backend.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.FirebaseTokenVerifier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:foodhubfcmtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class FcmPostCommitTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean FirebaseTokenVerifier verifier;
    @MockitoBean FcmClient fcmClient;

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM user_device_tokens");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,firebase_uid,name,email,role,status) VALUES(1,'uid-fcm','Fcm test','fcm@test.invalid','customer','active')");
        jdbc.update("INSERT INTO user_device_tokens(user_id,fcm_token,platform) VALUES(1,'test-registration-token','android')");
        when(fcmClient.send(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(FcmSendResult.SENT);
        clearInvocations(fcmClient);
    }

    @Test void sendsOnlyAfterTransactionCommit(){
        new TransactionTemplate(transactionManager).execute(status->{
            events.publishEvent(new FcmNotificationEvent(List.of(1L),"order_status","Title","Body",42L));
            verify(fcmClient,never()).send(anyString(),anyString(),anyString(),anyString(),anyString());
            return null;
        });
        verify(fcmClient,timeout(3000)).send("test-registration-token","Title","Body","order_status","42");
    }

    @Test void rollbackDoesNotSend() throws Exception {
        new TransactionTemplate(transactionManager).execute(status->{
            events.publishEvent(new FcmNotificationEvent(List.of(1L),"order_status","Title","Body",42L));
            status.setRollbackOnly();
            return null;
        });
        Thread.sleep(500);
        verify(fcmClient,never()).send(anyString(),anyString(),anyString(),anyString(),anyString());
    }

    @Test void unregisteredDeviceTokenIsDeleted(){
        when(fcmClient.send(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(FcmSendResult.UNREGISTERED);
        new TransactionTemplate(transactionManager).execute(status->{
            events.publishEvent(new FcmNotificationEvent(List.of(1L),"order_status","Title","Body",42L));
            return null;
        });
        awaitCount(0);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_device_tokens WHERE fcm_token='test-registration-token'",Integer.class));
    }

    private void awaitCount(int count){
        long deadline=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<deadline){
            Integer actual=jdbc.queryForObject("SELECT COUNT(*) FROM user_device_tokens WHERE fcm_token='test-registration-token'",Integer.class);
            if(actual!=null&&actual==count)return;
            try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        }
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM user_device_tokens WHERE fcm_token='test-registration-token'",Integer.class));
    }
}
