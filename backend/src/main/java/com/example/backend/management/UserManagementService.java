package com.example.backend.management;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.management.ManagementDtos.UserPatch;
import com.example.backend.user.UserProfileDto;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class UserManagementService {
    private final ManagementRepository repository;
    public UserManagementService(ManagementRepository repository){this.repository=repository;}
    public List<UserProfileDto> list(FirebasePrincipal p){requireAdmin(p);return repository.users();}
    public UserProfileDto get(FirebasePrincipal p,String uid){
        if(!isAdmin(p)&& (p==null||p.userId()==null||!repository.sharesConversation(p.userId(),uid)))throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ có thể xem thông tin người đang trò chuyện với bạn");
        UserProfileDto user=repository.user(uid);if(user==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản");return user;
    }
    @Transactional public void patch(FirebasePrincipal p,String uid,UserPatch request){
        requireAdmin(p);if(request==null)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"Thiếu dữ liệu tài khoản");
        List<ManagementRepository.AdminRow> admins=repository.lockActiveAdmins();ManagementRepository.AdminRow target=repository.lockUser(uid);
        if(target==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản");
        String role=request.role()==null?null:role(request.role());String status=request.isAvailable()==null?null:request.isAvailable()?"active":"suspended";
        boolean self=uid.equals(p.firebaseUid());
        if(self&&((role!=null&&!"admin".equals(role))||(status!=null&&!"active".equals(status))))throw new ApiException(HttpStatus.CONFLICT,"Không thể tự hạ quyền hoặc tự khóa tài khoản quản trị");
        boolean removesAdmin= "admin".equals(target.role())&&((role!=null&&!"admin".equals(role))||(status!=null&&!"active".equals(status)));
        if(removesAdmin&&admins.size()<=1)throw new ApiException(HttpStatus.CONFLICT,"Không thể vô hiệu hóa quản trị viên cuối cùng");
        repository.updateUser(uid,request,role,status);
    }
    @Transactional public void delete(FirebasePrincipal p,String uid){requireAdmin(p);List<ManagementRepository.AdminRow> admins=repository.lockActiveAdmins();ManagementRepository.AdminRow target=repository.lockUser(uid);if(target==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản");if(uid.equals(p.firebaseUid()))throw new ApiException(HttpStatus.CONFLICT,"Không thể tự xóa tài khoản quản trị");if("admin".equals(target.role())&&admins.size()<=1)throw new ApiException(HttpStatus.CONFLICT,"Không thể xóa quản trị viên cuối cùng");repository.softDeleteUser(uid);}
    private String role(String raw){return switch(raw.trim().toLowerCase(Locale.ROOT)){case "user","customer"->"customer";case "restaurant","seller"->"seller";case "shipper","driver"->"driver";case "admin"->"admin";default->throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"Vai trò không hợp lệ");};}
    private static boolean isAdmin(FirebasePrincipal p){return p!=null&&"admin".equals(p.databaseRole());}
    private static void requireAdmin(FirebasePrincipal p){if(!isAdmin(p))throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ quản trị viên được quản lý tài khoản");}
}
