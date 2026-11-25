package com.example.backend.activity;

import com.example.backend.activity.ActivityLogDtos.CreateRequest;
import com.example.backend.activity.ActivityLogDtos.Item;
import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogService {
    private final ActivityLogRepository repository;
    public ActivityLogService(ActivityLogRepository repository){this.repository=repository;}
    public List<Item> list(FirebasePrincipal principal){return repository.list(userId(principal),principal.databaseRole());}
    @Transactional public long create(FirebasePrincipal principal,CreateRequest request){
        long userId=userId(principal);CreateRequest input=request==null?new CreateRequest(null,null,null,null,null):request;
        Long storeId=input.storeId();
        if(storeId!=null&&!"admin".equals(principal.databaseRole())&&!repository.ownsStore(storeId,userId))
            throw new ApiException(HttpStatus.FORBIDDEN,"Không có quyền ghi nhật ký cho cửa hàng này");
        if(storeId==null&&"seller".equals(principal.databaseRole()))storeId=repository.firstOwnedStore(userId);
        return repository.insert(userId,storeId,value(input.type(),"general"),value(input.title(),"Hoạt động"),input.description(),input.details());
    }
    private long userId(FirebasePrincipal principal){if(principal==null||principal.userId()==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ tài khoản");return principal.userId();}
    private static String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
}
