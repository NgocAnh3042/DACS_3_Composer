package com.example.backend.notification;

import com.example.backend.notification.NotificationDtos.Item;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void upsertDeviceToken(long userId,String token,String platform){
        jdbc.update("INSERT INTO user_device_tokens(user_id,fcm_token,platform,last_seen_at) VALUES(?,?,?,CURRENT_TIMESTAMP) "
                + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),platform=VALUES(platform),last_seen_at=CURRENT_TIMESTAMP",
                userId,token,platform);
    }
    public List<Item> notifications(long userId){
        return jdbc.query("SELECT id,title,body,created_at,type,is_read FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 200",
                (rs,n)->new Item(Long.toString(rs.getLong("id")),rs.getString("title"),rs.getString("body"),
                        rs.getTimestamp("created_at").toLocalDateTime(),rs.getString("type").contains("order")?"order":"system",rs.getBoolean("is_read")),userId);
    }
    public void markAllRead(long userId){jdbc.update("UPDATE notifications SET is_read=TRUE,read_at=CURRENT_TIMESTAMP WHERE user_id=? AND is_read=FALSE",userId);}
    public void markRead(long notificationId,long userId){jdbc.update("UPDATE notifications SET is_read=TRUE,read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",notificationId,userId);}
    public List<DeviceToken> tokensForUsers(List<Long> userIds){
        if(userIds.isEmpty())return List.of();
        String marks=String.join(",",java.util.Collections.nCopies(userIds.size(),"?"));
        return jdbc.query("SELECT user_id,fcm_token FROM user_device_tokens WHERE user_id IN ("+marks+")",
                (rs,n)->new DeviceToken(rs.getLong("user_id"),rs.getString("fcm_token")),userIds.toArray());
    }
    public void deleteToken(String token){jdbc.update("DELETE FROM user_device_tokens WHERE fcm_token=?",token);}
    public record DeviceToken(long userId,String value) { }
}
