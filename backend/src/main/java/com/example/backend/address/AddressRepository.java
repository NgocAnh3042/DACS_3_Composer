package com.example.backend.address;

import com.example.backend.address.AddressDtos.AddressView;
import com.example.backend.address.AddressDtos.SaveRequest;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AddressRepository {
    private final JdbcTemplate jdbc;
    public AddressRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<AddressView> list(long userId){return jdbc.query("SELECT id,label,receiver_name,receiver_phone,address_line,ward,district,city,latitude,longitude,is_default FROM user_addresses WHERE user_id=? ORDER BY is_default DESC,updated_at DESC",
            (rs,n)->new AddressView(Long.toString(rs.getLong("id")),rs.getString("label"),rs.getString("receiver_name"),rs.getString("receiver_name"),rs.getString("receiver_phone"),rs.getString("receiver_phone"),rs.getString("address_line"),rs.getString("address_line"),rs.getString("ward"),rs.getString("district"),rs.getString("city"),nullable(rs,"latitude"),nullable(rs,"longitude"),rs.getBoolean("is_default")),userId);}
    public long save(long userId,Long id,SaveRequest r){
        String label=first(r.label(),r.name(),"Địa chỉ"),name=first(r.receiverName(),r.name(),""),phone=first(r.receiverPhone(),r.phone(),""),address=first(r.address(),r.addressDetail(),"");
        if(Boolean.TRUE.equals(r.isDefault()))jdbc.update("UPDATE user_addresses SET is_default=0 WHERE user_id=?",userId);
        if(id!=null){jdbc.update("UPDATE user_addresses SET label=?,receiver_name=?,receiver_phone=?,address_line=?,ward=?,district=?,city=?,latitude=?,longitude=?,is_default=? WHERE id=? AND user_id=?",label,name,phone,address,r.ward(),r.district(),r.city(),r.latitude(),r.longitude(),Boolean.TRUE.equals(r.isDefault()),id,userId);return id;}
        var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO user_addresses(user_id,label,receiver_name,receiver_phone,address_line,ward,district,city,latitude,longitude,is_default) VALUES(?,?,?,?,?,?,?,?,?,?,?)",new String[]{"id"});ps.setLong(1,userId);ps.setString(2,label);ps.setString(3,name);ps.setString(4,phone);ps.setString(5,address);ps.setString(6,r.ward());ps.setString(7,r.district());ps.setString(8,r.city());ps.setObject(9,r.latitude());ps.setObject(10,r.longitude());ps.setBoolean(11,Boolean.TRUE.equals(r.isDefault()));return ps;},key);return key.getKey().longValue();
    }
    public int delete(long id,long userId){return jdbc.update("DELETE FROM user_addresses WHERE id=? AND user_id=?",id,userId);}
    private static String first(String a,String b,String fallback){return a!=null?a:b!=null?b:fallback;}
    private static Double nullable(java.sql.ResultSet rs,String c)throws java.sql.SQLException{double v=rs.getDouble(c);return rs.wasNull()?null:v;}
}
