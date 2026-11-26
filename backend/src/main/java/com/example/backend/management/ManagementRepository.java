package com.example.backend.management;

import com.example.backend.catalog.PromotionDto;
import com.example.backend.management.ManagementDtos.*;
import com.example.backend.user.UserProfileDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ManagementRepository {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    public ManagementRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public StoreMine mine(long userId) {
        List<StoreMine> rows=jdbc.query("""
                SELECT s.id,s.name,s.rating_avg,s.address_line,s.avg_prep_minutes,s.description,s.cover_url,s.logo_url,
                       s.email,s.phone,s.open_time,s.close_time,s.order_notify,s.sound_notify,s.push_notify,
                       s.email_notify,s.promo_notify,s.latitude,s.longitude,s.is_open
                FROM stores s JOIN merchants m ON m.id=s.merchant_id
                WHERE m.owner_user_id=? AND s.deleted_at IS NULL ORDER BY s.id LIMIT 1
                """,(rs,n)->storeMine(rs),userId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public long createStore(long userId,String email,StoreRequest r) {
        List<Long> merchants=jdbc.query("SELECT id FROM merchants WHERE owner_user_id=? ORDER BY id LIMIT 1",(rs,n)->rs.getLong(1),userId);
        long merchantId;
        if(merchants.isEmpty()){
            GeneratedKeyHolder key=new GeneratedKeyHolder();
            jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO merchants(owner_user_id,brand_name) VALUES(?,?)",new String[]{"id"});ps.setLong(1,userId);ps.setString(2,has(r.name())?r.name().trim():"Thương hiệu mới");return ps;},key);
            merchantId=key.getKey().longValue();
        } else merchantId=merchants.getFirst();
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("""
                INSERT INTO stores(merchant_id,name,description,address_line,phone,email,open_time,close_time,cover_url,logo_url,status)
                VALUES(?,?,?,?,?,?,?,?,?,?, 'active')
                """,new String[]{"id"});ps.setLong(1,merchantId);ps.setString(2,has(r.name())?r.name().trim():"Cửa hàng của tôi");
            ps.setString(3,r.description());ps.setString(4,r.address());ps.setString(5,r.phone());ps.setString(6,has(r.email())?r.email():email);
            ps.setString(7,has(r.openTime())?r.openTime():"08:00");ps.setString(8,has(r.closeTime())?r.closeTime():"22:00");ps.setString(9,r.coverImage());ps.setString(10,r.avatarUrl());return ps;},key);
        long storeId=key.getKey().longValue();
        jdbc.update("INSERT INTO store_staff(store_id,user_id,staff_role) VALUES(?,?,'owner')",storeId,userId);
        return storeId;
    }
    public void updateStore(long userId,StoreRequest r) {
        StoreMine current=mine(userId);if(current==null)return;
        List<String> sets=new ArrayList<>();List<Object> values=new ArrayList<>();
        add(sets,values,"name",trim(r.name()));add(sets,values,"description",r.description());add(sets,values,"address_line",r.address());
        add(sets,values,"phone",r.phone());add(sets,values,"email",r.email());add(sets,values,"open_time",r.openTime());add(sets,values,"close_time",r.closeTime());
        add(sets,values,"cover_url",r.coverImage());add(sets,values,"logo_url",r.avatarUrl());add(sets,values,"is_open",r.isOpen());
        add(sets,values,"order_notify",r.orderNotify());add(sets,values,"sound_notify",r.soundNotify());add(sets,values,"push_notify",r.pushNotify());
        add(sets,values,"email_notify",r.emailNotify());add(sets,values,"promo_notify",r.promoNotify());add(sets,values,"latitude",r.latitude());add(sets,values,"longitude",r.longitude());
        if(!sets.isEmpty()){values.add(Long.parseLong(current.id()));jdbc.update("UPDATE stores SET "+String.join(",",sets)+" WHERE id=? AND deleted_at IS NULL",values.toArray());}
    }

    public Long storeOwner(long storeId) {
        List<Long> rows=jdbc.query("SELECT m.owner_user_id FROM stores s JOIN merchants m ON m.id=s.merchant_id WHERE s.id=? AND s.deleted_at IS NULL",(rs,n)->rs.getLong(1),storeId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean storeExists(long storeId){return storeOwner(storeId)!=null;}
    public Long resolveMenuCategory(long storeId,String category){
        if(category!=null&&category.trim().matches("\\d+"))return null;
        String name=category==null||category.isBlank()?"Món chính":category.trim().replaceAll("\\s+"," ");
        List<Long> ids=jdbc.query("SELECT id FROM menu_categories WHERE store_id=? AND LOWER(name)=LOWER(?) ORDER BY id LIMIT 1",(rs,n)->rs.getLong(1),storeId,name);
        if(!ids.isEmpty())return ids.getFirst();GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO menu_categories(store_id,name) VALUES(?,?)",new String[]{"id"});ps.setLong(1,storeId);ps.setString(2,name);return ps;},key);return key.getKey().longValue();
    }
    public long insertDish(DishRequest r,Long menuCategoryId,Long platformCategoryId){
        GeneratedKeyHolder key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO dishes(store_id,platform_category_id,menu_category_id,name,description,image_url,price,is_available) VALUES(?,?,?,?,?,?,?,?)",new String[]{"id"});
            ps.setLong(1,r.restaurantId());if(platformCategoryId==null)ps.setNull(2,java.sql.Types.BIGINT);else ps.setLong(2,platformCategoryId);
            if(menuCategoryId==null)ps.setNull(3,java.sql.Types.BIGINT);else ps.setLong(3,menuCategoryId);ps.setString(4,r.name().trim());ps.setString(5,r.description());ps.setString(6,r.imageUrl());ps.setBigDecimal(7,r.price());ps.setBoolean(8,r.available()==null||r.available());return ps;},key);return key.getKey().longValue();
    }
    public Long dishStore(long dishId){List<Long> rows=jdbc.query("SELECT store_id FROM dishes WHERE id=? AND deleted_at IS NULL",(rs,n)->rs.getLong(1),dishId);return rows.isEmpty()?null:rows.getFirst();}
    public void updateDish(long dishId,long storeId,DishRequest r,Long menuCategoryId,Long platformCategoryId){
        List<String> sets=new ArrayList<>();List<Object> args=new ArrayList<>();
        add(sets,args,"name",trim(r.name()));add(sets,args,"description",r.description());add(sets,args,"image_url",r.imageUrl());add(sets,args,"price",r.price());add(sets,args,"is_available",r.available());
        if(r.category()!=null){sets.add("menu_category_id=?");args.add(menuCategoryId);sets.add("platform_category_id=?");args.add(platformCategoryId);}
        if(!sets.isEmpty()){args.add(dishId);args.add(storeId);jdbc.update("UPDATE dishes SET "+String.join(",",sets)+" WHERE id=? AND store_id=? AND deleted_at IS NULL",args.toArray());}
    }
    public void softDeleteDish(long id){jdbc.update("UPDATE dishes SET deleted_at=CURRENT_TIMESTAMP,is_available=FALSE WHERE id=? AND deleted_at IS NULL",id);}

    public long insertPromotion(PromotionRequest r,Timestamp start,Timestamp end,boolean active){
        GeneratedKeyHolder key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO vouchers(store_id,code,title,description,image_url,discount_type,discount_value,max_discount,min_order_amount,usage_limit,starts_at,ends_at,is_active) VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,?)",new String[]{"id"});
            if(r.storeId()==null)ps.setNull(1,java.sql.Types.BIGINT);else ps.setLong(1,r.storeId());ps.setString(2,r.code().trim());ps.setString(3,r.title().trim());ps.setString(4,r.description());ps.setString(5,r.imageUrl());ps.setString(6,r.type());ps.setBigDecimal(7,r.value());ps.setBigDecimal(8,r.maxDiscount());ps.setBigDecimal(9,r.minOrderValue()==null?BigDecimal.ZERO:r.minOrderValue());
            if(r.usageLimit()==null)ps.setNull(10,java.sql.Types.INTEGER);else ps.setInt(10,r.usageLimit());ps.setTimestamp(11,start);ps.setTimestamp(12,end);ps.setBoolean(13,active);return ps;},key);return key.getKey().longValue();
    }
    public boolean promotionStoreExists(long id){return storeExists(id);}
    public void setPromotionStatus(long id,boolean active){jdbc.update("UPDATE vouchers SET is_active=? WHERE id=?",active,id);}
    public boolean promotionExists(long id){return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM vouchers WHERE id=?)",Boolean.class,id);}
    public boolean promotionUsed(long id){return jdbc.queryForObject("SELECT (EXISTS(SELECT 1 FROM order_vouchers WHERE voucher_id=?) OR EXISTS(SELECT 1 FROM vouchers WHERE id=? AND used_count>0))",Boolean.class,id,id);}
    public void deletePromotion(long id){jdbc.update("DELETE FROM vouchers WHERE id=?",id);}

    public List<UserProfileDto> users(){return jdbc.query(userSelect()+" WHERE deleted_at IS NULL ORDER BY created_at DESC",ManagementRepository::userRow);}
    public UserProfileDto user(String uid){List<UserProfileDto> rows=jdbc.query(userSelect()+" WHERE firebase_uid=? AND deleted_at IS NULL",ManagementRepository::userRow,uid);return rows.isEmpty()?null:rows.getFirst();}
    public boolean sharesConversation(long viewerId,String targetUid){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM conversation_participants a JOIN conversation_participants b ON b.conversation_id=a.conversation_id JOIN users u ON u.id=b.user_id WHERE a.user_id=? AND u.firebase_uid=? AND u.deleted_at IS NULL)",Boolean.class,viewerId,targetUid));}
    public void updateUser(String uid,UserPatch p,String role,String status){
        String name=p.fullName()!=null?p.fullName():p.name();String phone=p.phoneNumber()!=null?p.phoneNumber():p.phone();
        jdbc.update("UPDATE users SET name=COALESCE(?,name),email=COALESCE(?,email),phone=COALESCE(?,phone),role=COALESCE(?,role),status=COALESCE(?,status) WHERE firebase_uid=? AND deleted_at IS NULL",
                trim(name),p.email(),phone,role,status,uid);
    }
    public boolean userExists(String uid){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE firebase_uid=? AND deleted_at IS NULL)",Boolean.class,uid));}
    public List<AdminRow> lockActiveAdmins(){return jdbc.query("SELECT id,firebase_uid FROM users WHERE role='admin' AND status='active' AND deleted_at IS NULL ORDER BY id FOR UPDATE",(rs,n)->new AdminRow(rs.getLong("id"),rs.getString("firebase_uid")));}
    public AdminRow lockUser(String uid){List<AdminRow> rows=jdbc.query("SELECT id,firebase_uid,role,status FROM users WHERE firebase_uid=? AND deleted_at IS NULL FOR UPDATE",(rs,n)->new AdminRow(rs.getLong("id"),rs.getString("firebase_uid"),rs.getString("role"),rs.getString("status")),uid);return rows.isEmpty()?null:rows.getFirst();}
    public void softDeleteUser(String uid){jdbc.update("UPDATE users SET status='suspended',deleted_at=CURRENT_TIMESTAMP WHERE firebase_uid=? AND deleted_at IS NULL",uid);}
    private static String userSelect(){return "SELECT id,firebase_uid,name,email,phone,address_line,avatar_url,role,status FROM users";}
    private static UserProfileDto userRow(ResultSet rs,int row)throws SQLException{
        String role=switch(rs.getString("role")){case "seller"->"restaurant";case "driver"->"shipper";case "admin"->"admin";default->"user";};
        String name=text(rs,"name"),phone=text(rs,"phone"),status=rs.getString("status");
        return new UserProfileDto(Long.toString(rs.getLong("id")),rs.getString("firebase_uid"),name,name,text(rs,"email"),phone,phone,text(rs,"address_line"),text(rs,"avatar_url"),role,status.toUpperCase(java.util.Locale.ROOT),"active".equals(status));
    }
    private static StoreMine storeMine(ResultSet rs)throws SQLException{return new StoreMine(Long.toString(rs.getLong("id")),text(rs,"name"),decimal(rs,"rating_avg"),text(rs,"address_line"),Integer.toString(rs.getObject("avg_prep_minutes")==null?30:rs.getInt("avg_prep_minutes")),"",text(rs,"description"),text(rs,"cover_url"),text(rs,"logo_url"),text(rs,"email"),text(rs,"phone"),time(rs,"open_time"),time(rs,"close_time"),rs.getBoolean("order_notify"),rs.getBoolean("sound_notify"),rs.getBoolean("push_notify"),rs.getBoolean("email_notify"),rs.getBoolean("promo_notify"),nullableDouble(rs,"latitude"),nullableDouble(rs,"longitude"),rs.getBoolean("is_open"));}
    private static String time(ResultSet rs,String field)throws SQLException{var t=rs.getTime(field);return t==null?"":t.toString();}
    private static String text(ResultSet rs,String field)throws SQLException{String value=rs.getString(field);return value==null?"":value;}
    private static double decimal(ResultSet rs,String field)throws SQLException{var n=rs.getBigDecimal(field);return n==null?0:n.doubleValue();}
    private static Double nullableDouble(ResultSet rs,String field)throws SQLException{double n=rs.getDouble(field);return rs.wasNull()?null:n;}
    private static String trim(String value){return value==null?null:value.trim();}
    private static boolean has(String value){return value!=null&&!value.isBlank();}
    private static void add(List<String> sets,List<Object> args,String column,Object value){if(value!=null){sets.add(column+"=?");args.add(value);}}
    public record AdminRow(long id,String uid,String role,String status){public AdminRow(long id,String uid){this(id,uid,null,null);}}
}
