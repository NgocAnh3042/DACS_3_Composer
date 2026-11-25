package com.example.backend.activity;

import com.example.backend.activity.ActivityLogDtos.Item;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityLogRepository {
    private final JdbcTemplate jdbc;
    public ActivityLogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Item> list(long userId,String role){
        String sql="SELECT a.id,a.title,a.description,a.details,a.type,a.created_at FROM activity_logs a";
        Object[] args;
        if("seller".equals(role)){
            sql+=" JOIN stores s ON s.id=a.store_id JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=?";args=new Object[]{userId};
        } else if("admin".equals(role)) args=new Object[0];
        else {sql+=" WHERE a.actor_user_id=?";args=new Object[]{userId};}
        sql+=" ORDER BY a.created_at DESC LIMIT 200";
        return jdbc.query(sql,(rs,n)->new Item(Long.toString(rs.getLong("id")),rs.getString("title"),
                value(rs.getString("description")),value(rs.getString("details")),rs.getString("type"),
                rs.getTimestamp("created_at").getTime(),""),args);
    }
    public Long firstOwnedStore(long userId){
        var ids=jdbc.query("SELECT s.id FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE m.owner_user_id=? ORDER BY s.id LIMIT 1",
                (rs,n)->rs.getLong(1),userId);return ids.isEmpty()?null:ids.getFirst();
    }
    public boolean ownsStore(long storeId,long userId){
        Boolean found=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=? AND m.owner_user_id=?)",Boolean.class,storeId,userId);
        return Boolean.TRUE.equals(found);
    }
    public long insert(long actorUserId,Long storeId,String type,String title,String description,String details){
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO activity_logs(actor_user_id,store_id,type,title,description,details) VALUES(?,?,?,?,?,?)",new String[]{"id"});
            ps.setLong(1,actorUserId);if(storeId==null)ps.setNull(2,java.sql.Types.BIGINT);else ps.setLong(2,storeId);
            ps.setString(3,type);ps.setString(4,title);ps.setString(5,description);ps.setString(6,details);return ps;},key);
        return key.getKey().longValue();
    }
    private static String value(String value){return value==null?"":value;}
}
