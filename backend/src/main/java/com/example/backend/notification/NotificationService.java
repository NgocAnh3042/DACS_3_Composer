package com.example.backend.notification;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.notification.NotificationDtos.DeviceTokenRequest;
import com.example.backend.notification.NotificationDtos.Item;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    public NotificationService(NotificationRepository repository){this.repository=repository;}
    @Transactional public void upsert(FirebasePrincipal principal,DeviceTokenRequest request){
        long userId=userId(principal);
        if(request==null||request.token()==null||request.token().isBlank())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"Thiếu FCM token");
        String platform=request.platform()==null||request.platform().isBlank()?"android":request.platform().trim().toLowerCase(Locale.ROOT);
        if(!List.of("android","ios","web").contains(platform))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"Platform không hợp lệ");
        repository.upsertDeviceToken(userId,request.token().trim(),platform);
    }
    public List<Item> list(FirebasePrincipal principal){return repository.notifications(userId(principal));}
    @Transactional public void markAllRead(FirebasePrincipal principal){repository.markAllRead(userId(principal));}
    @Transactional public void markRead(FirebasePrincipal principal,long id){repository.markRead(id,userId(principal));}
    private long userId(FirebasePrincipal principal){if(principal==null||principal.userId()==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ tài khoản");return principal.userId();}
}
