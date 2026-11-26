package com.example.backend.chat;

import com.example.backend.chat.ChatDtos.ConversationItem;
import com.example.backend.chat.ChatDtos.MessageItem;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {
    private static final DateTimeFormatter DB_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    public ChatRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<Long> resolveParticipants(List<String> identifiers){
        Map<String,String> uidByIdentifier=new LinkedHashMap<>();for(String value:identifiers)uidByIdentifier.put(value,value);
        List<Long> storeIds=new ArrayList<>();for(String value:identifiers)if(value.matches("\\d{1,18}"))try{storeIds.add(Long.parseLong(value));}catch(NumberFormatException ignored){}
        if(!storeIds.isEmpty()){
            String marks=marks(storeIds.size());
            jdbc.query("SELECT s.id,u.firebase_uid FROM stores s JOIN merchants m ON m.id=s.merchant_id JOIN users u ON u.id=m.owner_user_id WHERE s.id IN ("+marks+")",
                    (RowCallbackHandler)rs->uidByIdentifier.put(Long.toString(rs.getLong("id")),rs.getString("firebase_uid")),storeIds.toArray());
        }
        List<String> uids=uidByIdentifier.values().stream().distinct().toList();if(uids.isEmpty())return List.of();
        String marks=marks(uids.size());
        return jdbc.query("SELECT id FROM users WHERE firebase_uid IN ("+marks+") AND deleted_at IS NULL ORDER BY id",
                (rs,n)->rs.getLong("id"),uids.toArray());
    }
    public void lockUsers(List<Long> userIds){
        if(userIds.isEmpty())return;String marks=marks(userIds.size());
        jdbc.query("SELECT id FROM users WHERE id IN ("+marks+") ORDER BY id FOR UPDATE",(RowCallbackHandler)rs->{},userIds.toArray());
    }
    public Long findDuplicate(List<Long> userIds,String type,Long orderId){
        if(userIds.isEmpty())return null;
        String participants=marks(userIds.size());
        String orderClause=orderId==null?"c.order_id IS NULL":"c.order_id=?";
        String sql="SELECT c.id FROM conversations c JOIN conversation_participants cp ON cp.conversation_id=c.id WHERE c.type=? AND "+orderClause+
                " GROUP BY c.id HAVING COUNT(DISTINCT cp.user_id)=? AND SUM(CASE WHEN cp.user_id IN ("+participants+") THEN 1 ELSE 0 END)=? ORDER BY c.id LIMIT 1";
        List<Object> args=new ArrayList<>();args.add(type);if(orderId!=null)args.add(orderId);args.add(userIds.size());args.addAll(userIds);args.add(userIds.size());
        List<Long> rows=jdbc.query(sql,(rs,n)->rs.getLong(1),args.toArray());return rows.isEmpty()?null:rows.getFirst();
    }
    public long insertConversation(Long orderId,String type,long creatorId){
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO conversations(order_id,type,created_by) VALUES(?,?,?)",new String[]{"id"});
            if(orderId==null)ps.setNull(1,java.sql.Types.BIGINT);else ps.setLong(1,orderId);ps.setString(2,type);ps.setLong(3,creatorId);return ps;},key);
        Number id=key.getKey();if(id==null)throw new IllegalStateException("Không tạo được hội thoại");return id.longValue();
    }
    public void addParticipants(long conversationId,List<Long> userIds){
        if(userIds.isEmpty())return;String values=String.join(",",java.util.Collections.nCopies(userIds.size(),"(?,?)"));List<Object> args=new ArrayList<>();
        for(long userId:userIds){args.add(conversationId);args.add(userId);}
        jdbc.update("INSERT INTO conversation_participants(conversation_id,user_id) VALUES "+values,args.toArray());
    }
    public List<ConversationItem> conversations(long userId,String firebaseUid,String role,int limit,int offset){
        String base="SELECT DISTINCT c.id,c.order_id,c.type,c.created_at,c.updated_at FROM conversations c JOIN conversation_participants cp ON cp.conversation_id=c.id WHERE cp.user_id=?";
        List<Object> args=new ArrayList<>();args.add(userId);
        if("admin".equals(role))base="SELECT DISTINCT c.id,c.order_id,c.type,c.created_at,c.updated_at FROM conversations c LEFT JOIN conversation_participants cp ON cp.conversation_id=c.id WHERE cp.user_id=? OR c.type='support'";
        base+=" ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?";args.add(limit);args.add(offset);
        List<ConversationRow> rows=jdbc.query(base,(rs,n)->new ConversationRow(rs.getLong("id"),nullableLong(rs,"order_id"),rs.getString("type"),format(rs.getTimestamp("created_at"))),args.toArray());
        if(rows.isEmpty())return List.of();List<Long> ids=rows.stream().map(ConversationRow::id).toList();String in=marks(ids.size());
        Map<Long,List<String>> participants=new LinkedHashMap<>();Map<Long,Map<String,String>> roles=new LinkedHashMap<>();Map<Long,Map<String,Integer>> unread=new LinkedHashMap<>();
        jdbc.query("SELECT cp.conversation_id,u.firebase_uid,u.role FROM conversation_participants cp JOIN users u ON u.id=cp.user_id WHERE cp.conversation_id IN ("+in+") ORDER BY cp.conversation_id,cp.user_id",
                (RowCallbackHandler)rs->{long id=rs.getLong("conversation_id");String uid=rs.getString("firebase_uid");participants.computeIfAbsent(id,k->new ArrayList<>()).add(uid);roles.computeIfAbsent(id,k->new LinkedHashMap<>()).put(uid,rs.getString("role"));unread.computeIfAbsent(id,k->new LinkedHashMap<>()).put(uid,0);},ids.toArray());
        Map<Long,LastMessage> last=new LinkedHashMap<>();
        jdbc.query("SELECT m.conversation_id,m.content,m.message_type,m.created_at FROM messages m JOIN (SELECT conversation_id,MAX(id) max_id FROM messages WHERE conversation_id IN ("+in+") AND deleted_at IS NULL GROUP BY conversation_id) latest ON latest.max_id=m.id",
                (RowCallbackHandler)rs->last.put(rs.getLong("conversation_id"),new LastMessage(rs.getString("content"),rs.getString("message_type"),format(rs.getTimestamp("created_at")))),ids.toArray());
        jdbc.query("SELECT m.conversation_id,COUNT(*) unread_count FROM messages m LEFT JOIN conversation_participants cp ON cp.conversation_id=m.conversation_id AND cp.user_id=? WHERE m.conversation_id IN ("+in+") AND m.deleted_at IS NULL AND m.sender_user_id<>? AND (cp.last_read_at IS NULL OR m.created_at>cp.last_read_at) GROUP BY m.conversation_id",
                (RowCallbackHandler)rs->unread.computeIfAbsent(rs.getLong("conversation_id"),k->new LinkedHashMap<>()).put(firebaseUid,rs.getInt("unread_count")),prepend(ids,userId,userId));
        List<ConversationItem> result=new ArrayList<>();for(ConversationRow row:rows){LastMessage message=last.get(row.id());String lastText=message==null?"":("image".equals(message.type())?"Đã gửi một ảnh":value(message.content()));String lastTime=message==null?row.createdAt():message.createdAt();
            Map<String,Integer> unreadMap=unread.computeIfAbsent(row.id(),k->new LinkedHashMap<>());unreadMap.putIfAbsent(firebaseUid,0);
            result.add(new ConversationItem(Long.toString(row.id()),participants.getOrDefault(row.id(),List.of()),roles.getOrDefault(row.id(),Map.of()),
                    row.orderId()==null?null:Long.toString(row.orderId()),row.type().toUpperCase(java.util.Locale.ROOT),lastText,lastTime,row.createdAt(),unreadMap,Map.of()));}
        return result;
    }
    public boolean participant(long conversationId,long userId){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id=? AND user_id=?)",Boolean.class,conversationId,userId));}
    public boolean conversationExists(long conversationId){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM conversations WHERE id=?)",Boolean.class,conversationId));}
    public List<MessageItem> messages(long conversationId,String senderUid,int limit,int offset,Long before,Long after){
        StringBuilder sql=new StringBuilder("SELECT m.id,m.content,m.image_url,m.message_type,m.created_at,m.is_seen,m.is_delivered,u.firebase_uid FROM messages m JOIN users u ON u.id=m.sender_user_id WHERE m.conversation_id=? AND m.deleted_at IS NULL");
        List<Object> args=new ArrayList<>();args.add(conversationId);if(before!=null){sql.append(" AND m.id<?");args.add(before);}if(after!=null){sql.append(" AND m.id>?");args.add(after);}
        boolean ascending=after!=null&&before==null;sql.append(ascending?" ORDER BY m.id ASC":" ORDER BY m.id DESC");sql.append(" LIMIT ? OFFSET ?");args.add(limit);args.add(offset);
        List<MessageItem> found=jdbc.query(sql.toString(),(rs,n)->new MessageItem(Long.toString(rs.getLong("id")),rs.getString("firebase_uid"),"",
                value(rs.getString("content")),rs.getString("image_url"),rs.getString("message_type").toUpperCase(java.util.Locale.ROOT),format(rs.getTimestamp("created_at")),rs.getBoolean("is_seen"),rs.getBoolean("is_delivered"),false),args.toArray());
        if(ascending)return found;java.util.Collections.reverse(found);return found;
    }
    public long insertMessage(long conversationId,long senderId,String type,String content,String imageUrl){
        var key=new GeneratedKeyHolder();jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO messages(conversation_id,sender_user_id,message_type,content,image_url,is_delivered) VALUES(?,?,?,?,?,1)",new String[]{"id"});
            ps.setLong(1,conversationId);ps.setLong(2,senderId);ps.setString(3,type);ps.setString(4,content);ps.setString(5,imageUrl);return ps;},key);
        Number id=key.getKey();if(id==null)throw new IllegalStateException("Không tạo được tin nhắn");return id.longValue();
    }
    public String messageCreatedAt(long messageId){return jdbc.queryForObject("SELECT created_at FROM messages WHERE id=?",(rs,n)->format(rs.getTimestamp("created_at")),messageId);}
    public void updateConversationTime(long conversationId){jdbc.update("UPDATE conversations SET updated_at=CURRENT_TIMESTAMP WHERE id=?",conversationId);}
    public void markRead(long conversationId,long userId){jdbc.update("UPDATE conversation_participants SET last_read_at=CURRENT_TIMESTAMP WHERE conversation_id=? AND user_id=?",conversationId,userId);jdbc.update("UPDATE messages SET is_seen=TRUE WHERE conversation_id=? AND sender_user_id<>? AND deleted_at IS NULL",conversationId,userId);}
    public Long orderId(long conversationId){List<Long> values=jdbc.query("SELECT order_id FROM conversations WHERE id=?",(rs,n)->nullableLong(rs,"order_id"),conversationId);return values.isEmpty()?null:values.getFirst();}
    public List<Long> otherParticipantIds(long conversationId,long senderId){return jdbc.query("SELECT user_id FROM conversation_participants WHERE conversation_id=? AND user_id<>? ORDER BY user_id",(rs,n)->rs.getLong(1),conversationId,senderId);}
    public void addChatNotifications(List<Long> recipients,Long orderId,String body){for(long recipient:recipients)jdbc.update("INSERT INTO notifications(user_id,type,title,body,related_order_id) VALUES(?,'chat','Tin nhắn mới',?,?)",recipient,body,orderId);}
    private static Object[] prepend(List<Long> ids,long first,long last){Object[] result=new Object[ids.size()+2];result[0]=first;for(int i=0;i<ids.size();i++)result[i+1]=ids.get(i);result[result.length-1]=last;return result;}
    private static String marks(int size){return String.join(",",java.util.Collections.nCopies(size,"?"));}
    private static Long nullableLong(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long value=rs.getLong(column);return rs.wasNull()?null:value;}
    private static String format(Timestamp timestamp){return timestamp==null?null:timestamp.toLocalDateTime().format(DB_TIME);}
    private static String value(String value){return value==null?"":value;}
    private record ConversationRow(long id,Long orderId,String type,String createdAt){}
    private record LastMessage(String content,String type,String createdAt){}
}
