package com.example.backend.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.FirebaseTokenVerifier;
import com.example.backend.notification.FcmClient;
import com.example.backend.notification.FcmSendResult;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:foodhubchattest;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc @ActiveProfiles("test")
class ChatApiTests {
    private static final String CUSTOMER="chat-customer",PEER="chat-peer",OUTSIDER="chat-outsider",ADMIN="chat-admin",RATE_LIMIT="chat-rate-limit";
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired JsonMapper json;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean FirebaseTokenVerifier verifier;@MockitoBean FcmClient fcmClient;

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM notifications");jdbc.update("DELETE FROM user_device_tokens");jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversation_participants");jdbc.update("DELETE FROM conversations");jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM merchants");jdbc.update("DELETE FROM users");
        for(String table:new String[]{"messages","conversations"})jdbc.execute("ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH 1");
        jdbc.update("INSERT INTO users(id,firebase_uid,name,email,role,status) VALUES(1,'uid-customer','Customer','customer-chat@test.invalid','customer','active'),(2,'uid-peer','Peer','peer-chat@test.invalid','customer','active'),(3,'uid-seller','Seller','seller-chat@test.invalid','seller','active'),(4,'uid-outsider','Outsider','outsider-chat@test.invalid','customer','active'),(5,'uid-admin','Admin','admin-chat@test.invalid','admin','active'),(6,'uid-rate-limit','Rate limit','rate-chat@test.invalid','customer','active')");
        jdbc.update("INSERT INTO merchants(id,owner_user_id,brand_name) VALUES(1,3,'Merchant')");
        jdbc.update("INSERT INTO stores(id,merchant_id,name,is_open,status) VALUES(100,1,'Chat store',TRUE,'active')");
        when(verifier.verify(CUSTOMER)).thenReturn(claims("uid-customer"));when(verifier.verify(PEER)).thenReturn(claims("uid-peer"));
        when(verifier.verify(OUTSIDER)).thenReturn(claims("uid-outsider"));when(verifier.verify(ADMIN)).thenReturn(claims("uid-admin"));
        when(verifier.verify(RATE_LIMIT)).thenReturn(claims("uid-rate-limit"));
        when(fcmClient.send(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(FcmSendResult.SENT);
    }

    @Test void createsConversationMapsNumericStoreAndReturnsExistingPair() throws Exception{
        String first=createConversation(CUSTOMER,"""
                {"participants":["uid-peer","100"],"type":"customer_store"}
                """);
        String reversed=createConversation(PEER,"""
                {"participants":["uid-seller","uid-customer"],"type":"customer_store"}
                """);
        assertEquals(first,reversed);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM conversations",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_participants WHERE conversation_id=?",Integer.class,Long.valueOf(first)));
        JsonNode list=read(mvc.perform(get("/api/conversations").header("Authorization","Bearer "+CUSTOMER)).andExpect(status().isOk()).andReturn());
        JsonNode item=list.at("/data/0");assertEquals(first,item.get("id").stringValue());
        assertEquals("seller",item.at("/participantRoles/uid-seller").stringValue());assertTrue(item.get("typing").isObject());
    }

    @Test void nonParticipantCannotReadOrPostMessagesButAdminCanRead() throws Exception{
        String conversation=createConversation(CUSTOMER,"{\"participants\":[\"uid-peer\"]}");
        mvc.perform(get("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+OUTSIDER))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+OUTSIDER)
                        .contentType("application/json").content("{\"content\":\"intrusion\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+ADMIN))
                .andExpect(status().isOk());
    }

    @Test void rejectsOverlongMessagesAndAllowsTwentyPerTenSecondsThenReturns429() throws Exception{
        String conversation=createConversation(CUSTOMER,"{\"participants\":[\"uid-rate-limit\"]}");
        String tooLong="x".repeat(4001);
        mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+RATE_LIMIT)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("content",tooLong))))
                .andExpect(status().isUnprocessableEntity());
        for(int i=0;i<20;i++)mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+RATE_LIMIT)
                        .contentType("application/json").content("{\"content\":\"rate-"+i+"\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+RATE_LIMIT)
                        .contentType("application/json").content("{\"content\":\"over-limit\"}"))
                .andExpect(status().isTooManyRequests());
        assertEquals(20,jdbc.queryForObject("SELECT COUNT(*) FROM messages",Integer.class));
    }

    @Test void sendMatchesFixturePersistsNotificationAndSendsFcmAfterCommit() throws Exception{
        String conversation=createConversation(CUSTOMER,"{\"participants\":[\"uid-peer\"]}");
        jdbc.update("INSERT INTO user_device_tokens(user_id,fcm_token,platform) VALUES(2,'peer-device','android')");
        MvcResult result=mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+CUSTOMER)
                .contentType("application/json").content("{\"type\":\"TEXT\",\"content\":\"Xin chào\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode actual=read(result);String id=actual.get("id").stringValue();
        String fixture=fixture("fixtures/chat-message-created.json").replace("__MESSAGE_ID__",id)
                .replace("__CREATED_AT__",actual.at("/data/createdAt").stringValue());
        assertEquals(json.readTree(fixture),actual);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=2 AND type='chat'",Integer.class));
        verify(fcmClient,timeout(3000)).send("peer-device","Tin nhắn mới","Xin chào","chat","");
    }

    @Test void readActionMarksIncomingMessagesSeenAndUpdatesLastRead() throws Exception{
        String conversation=createConversation(CUSTOMER,"{\"participants\":[\"uid-peer\"]}");
        mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+CUSTOMER)
                .contentType("application/json").content("{\"content\":\"Seen test\"}" )).andExpect(status().isCreated());
        JsonNode response=read(mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+PEER)
                .contentType("application/json").content("{\"action\":\"read\"}")).andExpect(status().isOk()).andReturn());
        assertTrue(response.get("ok").booleanValue());
        assertEquals(1,jdbc.queryForObject("SELECT is_seen FROM messages WHERE conversation_id=?",Integer.class,Long.valueOf(conversation)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM conversation_participants WHERE conversation_id=? AND user_id=2 AND last_read_at IS NOT NULL",Integer.class,Long.valueOf(conversation)));
        JsonNode conversations=read(mvc.perform(get("/api/conversations").header("Authorization","Bearer "+PEER)).andExpect(status().isOk()).andReturn());
        assertEquals(0,conversations.at("/data/0/unreadCount/uid-peer").intValue());
    }

    @Test void messagePagingSupportsLatestBeforeAndAfterCursors() throws Exception{
        String conversation=createConversation(CUSTOMER,"{\"participants\":[\"uid-peer\"]}");
        for(int i=1;i<=4;i++)mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+CUSTOMER)
                .contentType("application/json").content("{\"content\":\"msg"+i+"\"}" )).andExpect(status().isCreated());
        JsonNode latest=read(mvc.perform(get("/api/conversations/"+conversation+"/messages").param("limit","2").header("Authorization","Bearer "+CUSTOMER)).andExpect(status().isOk()).andReturn()).get("data");
        assertEquals(2,latest.size());assertEquals("3",latest.get(0).get("messageId").stringValue());assertEquals("4",latest.get(1).get("messageId").stringValue());
        JsonNode before=read(mvc.perform(get("/api/conversations/"+conversation+"/messages").param("limit","2").param("before","3").header("Authorization","Bearer "+CUSTOMER)).andExpect(status().isOk()).andReturn()).get("data");
        assertEquals("1",before.get(0).get("messageId").stringValue());assertEquals("2",before.get(1).get("messageId").stringValue());
        JsonNode after=read(mvc.perform(get("/api/conversations/"+conversation+"/messages").param("after","2").header("Authorization","Bearer "+CUSTOMER)).andExpect(status().isOk()).andReturn()).get("data");
        assertEquals(2,after.size());assertEquals("3",after.get(0).get("messageId").stringValue());assertEquals("4",after.get(1).get("messageId").stringValue());
    }

    @Test void messageAndNotificationRollbackDoesNotSendFcmAfterCommitFailure() throws Exception{
        String conversation=createConversation(CUSTOMER,"{\"participants\":[\"uid-peer\"]}");
        jdbc.update("INSERT INTO user_device_tokens(user_id,fcm_token,platform) VALUES(2,'rollback-peer-device','android')");
        TransactionTemplate tx=new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class,()->tx.executeWithoutResult(status->{
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("forced rollback after chat event publication");}
            });
            try{mvc.perform(post("/api/conversations/"+conversation+"/messages").header("Authorization","Bearer "+CUSTOMER)
                    .contentType("application/json").content("{\"content\":\"force-rollback\"}")).andExpect(status().isCreated());}
            catch(Exception e){throw new RuntimeException(e);}
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM messages",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Integer.class));
        Thread.sleep(300);verify(fcmClient,never()).send(anyString(),anyString(),anyString(),anyString(),anyString());
    }

    private String createConversation(String token,String body)throws Exception{
        MvcResult result=mvc.perform(post("/api/conversations").header("Authorization","Bearer "+token).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();return read(result).get("id").stringValue();
    }
    private FirebaseTokenVerifier.FirebaseClaims claims(String uid){return new FirebaseTokenVerifier.FirebaseClaims(uid,Map.of("email",uid+"@test.invalid","email_verified",true));}
    private JsonNode read(MvcResult result){try{return json.readTree(result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private String fixture(String name)throws Exception{try(InputStream in=new ClassPathResource(name).getInputStream()){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
}
