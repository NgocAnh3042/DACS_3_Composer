package com.example.backend.management;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.management.ManagementDtos.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogManagementService {
    private final ManagementRepository repository;
    public CatalogManagementService(ManagementRepository repository){this.repository=repository;}

    @Transactional public StoreMine storeMine(FirebasePrincipal p,String method,StoreRequest request){
        requireRole(p,"seller","admin");StoreRequest r=request==null?new StoreRequest(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null):request;
        validateStore(r);StoreMine current=repository.mine(p.userId());
        if("POST".equals(method)){if(current==null)repository.createStore(p.userId(),p.email(),r);}
        else {if(current==null)throw new ApiException(HttpStatus.NOT_FOUND,"Tài khoản chưa có cửa hàng");repository.updateStore(p.userId(),r);}
        return repository.mine(p.userId());
    }

    @Transactional public String createDish(FirebasePrincipal p,DishRequest r){
        if(r==null)throw invalid("Thiếu dữ liệu món ăn");requireRole(p,"seller","admin");requireStoreWrite(p,r.restaurantId());
        Long menuId=menuCategory(r.restaurantId(),r.category());Long platformId=platformCategory(r.category());
        return Long.toString(repository.insertDish(r,menuId,platformId));
    }
    @Transactional public void updateDish(FirebasePrincipal p,long dishId,DishRequest r){
        if(r==null)throw invalid("Thiếu dữ liệu món ăn");requireRole(p,"seller","admin");requireStoreWrite(p,r.restaurantId());Long actualStore=repository.dishStore(dishId);
        if(actualStore==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy món");if(actualStore!=r.restaurantId())throw new ApiException(HttpStatus.FORBIDDEN,"Bạn không quản lý cửa hàng này");
        Long menuId=r.category()==null?null:menuCategory(r.restaurantId(),r.category());Long platformId=r.category()==null?null:platformCategory(r.category());repository.updateDish(dishId,actualStore,r,menuId,platformId);
    }
    @Transactional public void deleteDish(FirebasePrincipal p,long dishId){
        requireRole(p,"seller","admin");Long storeId=repository.dishStore(dishId);if(storeId==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy món");requireStoreWrite(p,storeId);repository.softDeleteDish(dishId);
    }

    @Transactional public String createPromotion(FirebasePrincipal p,PromotionRequest r){
        requireRole(p,"admin");if(r==null)throw invalid("Thiếu dữ liệu khuyến mãi");validatePromotion(r);
        if(r.storeId()!=null&&!repository.promotionStoreExists(r.storeId()))throw invalid("Cửa hàng không tồn tại");
        return Long.toString(repository.insertPromotion(r,parseDate(r.startDate()),parseDate(r.endDate()),r.status()==null||"active".equals(r.status())));
    }
    @Transactional public void patchPromotion(FirebasePrincipal p,long id,StatusRequest r){requireRole(p,"admin");if(!repository.promotionExists(id))throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khuyến mãi");if(r!=null&&r.status()!=null)repository.setPromotionStatus(id,"active".equals(r.status()));}
    @Transactional public void deletePromotion(FirebasePrincipal p,long id){requireRole(p,"admin");if(!repository.promotionExists(id))throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khuyến mãi");if(repository.promotionUsed(id))repository.setPromotionStatus(id,false);else repository.deletePromotion(id);}

    private void requireStoreWrite(FirebasePrincipal p,long storeId){Long owner=repository.storeOwner(storeId);if(owner==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy cửa hàng");if(!"admin".equals(p.databaseRole())&&!owner.equals(p.userId()))throw new ApiException(HttpStatus.FORBIDDEN,"Bạn không quản lý cửa hàng này");}
    private Long menuCategory(long storeId,String category){return category!=null&&category.trim().matches("\\d+")?null:repository.resolveMenuCategory(storeId,category);}
    private Long platformCategory(String category){if(category==null||!category.trim().matches("\\d+"))return null;try{return Long.parseLong(category.trim());}catch(NumberFormatException e){throw invalid("Danh mục không hợp lệ");}}
    private void validateStore(StoreRequest r){if(r.name()!=null&&(r.name().isBlank()||r.name().trim().length()>180))throw invalid("Tên cửa hàng không hợp lệ");if(r.latitude()!=null&&(r.latitude().doubleValue() < -90||r.latitude().doubleValue()>90))throw invalid("Vĩ độ không hợp lệ");if(r.longitude()!=null&&(r.longitude().doubleValue() < -180||r.longitude().doubleValue()>180))throw invalid("Kinh độ không hợp lệ");}
    private void validatePromotion(PromotionRequest r){
        if(!List.of("percentage","fixed","free_shipping").contains(r.type()))throw invalid("Loại khuyến mãi không hợp lệ");
        if("percentage".equals(r.type())&&(r.value().signum()<0||r.value().compareTo(new java.math.BigDecimal("100"))>0))throw invalid("Phần trăm giảm phải từ 0 đến 100");
        if(r.maxDiscount()!=null&&r.maxDiscount().signum()<0||r.minOrderValue()!=null&&r.minOrderValue().signum()<0)throw invalid("Giá trị khuyến mãi không được âm");
        if(r.usageLimit()!=null&&r.usageLimit()<0)throw invalid("Số lượt sử dụng không hợp lệ");
        Timestamp from=parseDate(r.startDate()),to=parseDate(r.endDate());if(from!=null&&to!=null&&!to.after(from))throw invalid("Thời hạn khuyến mãi không hợp lệ");
        if(r.status()!=null&&!List.of("active","inactive","expired").contains(r.status()))throw invalid("Trạng thái khuyến mãi không hợp lệ");
    }
    private Timestamp parseDate(String value){if(value==null||value.isBlank())return null;try{return Timestamp.valueOf(LocalDateTime.parse(value.trim()));}catch(DateTimeParseException ignored){try{return Timestamp.valueOf(value.trim().replace('T',' '));}catch(IllegalArgumentException e){throw invalid("Ngày giờ không hợp lệ");}}}
    private static void requireRole(FirebasePrincipal p,String...roles){if(p==null||p.userId()==null||java.util.Arrays.stream(roles).noneMatch(role->role.equals(p.databaseRole())))throw new ApiException(HttpStatus.FORBIDDEN,"Không có quyền thực hiện thao tác");}
    private static ApiException invalid(String message){return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
