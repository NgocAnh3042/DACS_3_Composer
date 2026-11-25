package com.example.backend.address;

import com.example.backend.address.AddressDtos.AddressView;
import com.example.backend.address.AddressDtos.SaveRequest;
import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final AddressRepository repository;
    public AddressService(AddressRepository repository){this.repository=repository;}
    public List<AddressView> list(FirebasePrincipal p){return repository.list(userId(p));}
    @Transactional public long save(FirebasePrincipal p,Long id,SaveRequest request){if(request==null)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"Dữ liệu địa chỉ không hợp lệ");return repository.save(userId(p),id,request);}
    @Transactional public void delete(FirebasePrincipal p,long id){repository.delete(id,userId(p));}
    private long userId(FirebasePrincipal p){if(p==null||p.userId()==null)throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ tài khoản");return p.userId();}
}
