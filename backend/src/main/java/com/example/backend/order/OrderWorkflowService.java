package com.example.backend.order;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.order.OrderDtos.CreateRequest;
import com.example.backend.order.OrderDtos.DriverView;
import com.example.backend.order.OrderDtos.OrderSummary;
import com.example.backend.order.OrderDtos.OrderView;
import com.example.backend.order.OrderDtos.AvailableOrder;
import com.example.backend.order.OrderDtos.StatusRequest;
import com.example.backend.notification.FcmNotificationEvent;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderWorkflowService {
    private final OrderWorkflowRepository repository;
    private final BigDecimal baseDeliveryFee;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public OrderWorkflowService(OrderWorkflowRepository repository, ApplicationEventPublisher events,
            @Value("${foodhub.delivery-fee:15000}") BigDecimal baseDeliveryFee) {
        this.repository = repository;
        this.events = events;
        this.baseDeliveryFee = baseDeliveryFee;
    }

    public List<OrderView> list(FirebasePrincipal principal, Long id, int limit, int offset) {
        return repository.list(requireUserId(principal), principal.databaseRole(), id, limit, offset);
    }
    public List<AvailableOrder> available(FirebasePrincipal principal,int limit,int offset){requireRole(principal,"driver");return repository.available(limit,offset);}

    @Transactional
    public CreateResponse create(FirebasePrincipal principal, CreateRequest input) {
        long userId=requireUserId(principal);
        if (!"customer".equals(principal.databaseRole())) throw error(HttpStatus.FORBIDDEN,"Chỉ khách hàng được tạo đơn");
        if (input==null || input.restaurantId()==null || input.restaurantId()<=0 || input.items()==null || input.items().isEmpty())
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Thiếu cửa hàng hoặc món");
        var store=repository.storeForOrder(input.restaurantId());
        if(store==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Cửa hàng hiện không nhận đơn");
        List<Long> ids=input.items().stream().filter(i->i!=null&&i.dishId()!=null&&i.dishId()>0).map(i->i.dishId()).distinct().toList();
        var dishes=repository.orderDishes(ids);
        BigDecimal subtotal=BigDecimal.ZERO;
        List<PricedItem> priced=new ArrayList<>();
        for(var requested:input.items()) {
            if(requested==null||requested.dishId()==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Món không hợp lệ hoặc không thuộc cùng cửa hàng");
            var dish=dishes.get(requested.dishId());
            if(dish==null||dish.storeId()!=input.restaurantId()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Món không hợp lệ hoặc không thuộc cùng cửa hàng");
            int quantity=Math.max(1,Math.min(99,requested.quantity()==null?1:requested.quantity()));
            priced.add(new PricedItem(dish,quantity)); subtotal=subtotal.add(dish.price().multiply(BigDecimal.valueOf(quantity)));
        }
        double distance=distance(store.lat(),store.lng(),input.customerLat()==null?null:input.customerLat().doubleValue(),input.customerLng()==null?null:input.customerLng().doubleValue());
        BigDecimal fee=baseDeliveryFee.add(distance<=3?BigDecimal.ZERO:BigDecimal.valueOf(Math.max(1,(long)Math.ceil(distance-3))*10_000));
        BigDecimal discount=BigDecimal.ZERO; OrderWorkflowRepository.VoucherRow voucher=null;
        if(input.appliedPromotionId()!=null&&input.appliedPromotionId()>0) {
            voucher=repository.lockVoucher(input.appliedPromotionId(),input.restaurantId());
            if(voucher==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Voucher không còn hiệu lực cho cửa hàng này");
            if(subtotal.compareTo(voucher.minimum())<0) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Đơn hàng chưa đạt giá trị tối thiểu của voucher");
            if(voucher.limit()!=null&&voucher.used()>=voucher.limit()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Voucher đã hết lượt sử dụng");
            discount=switch(voucher.type()) {
                case "percentage" -> subtotal.multiply(voucher.value()).divide(BigDecimal.valueOf(100),2,java.math.RoundingMode.HALF_UP);
                case "fixed" -> voucher.value(); case "free_shipping" -> fee;
                default -> throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Loại voucher không hợp lệ");
            };
            if("percentage".equals(voucher.type())&&voucher.maxDiscount()!=null) discount=discount.min(voucher.maxDiscount());
            discount=discount.min(subtotal.add(fee));
        }
        BigDecimal total=subtotal.add(fee).subtract(discount).max(BigDecimal.ZERO);
        String method="payos".equalsIgnoreCase(input.paymentMethod())||"online".equalsIgnoreCase(input.paymentMethod())?"payos":"cash";
        String initial="payos".equals(method)?"pending_payment":"pending";
        String code="FH"+LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))+String.format(Locale.ROOT,"%08X",random.nextInt());
        String receiver=first(input.customerName(),repository.customerName(userId));
        String phone=first(input.customerPhone(),repository.customerPhone(userId));
        long orderId=repository.insertOrder(code,userId,input.restaurantId(),initial,receiver,phone,value(input.customerAddress()),input.customerLat(),input.customerLng(),input.note(),subtotal,fee,discount,total,method);
        for(PricedItem item:priced) repository.insertItem(orderId,item.dish(),item.quantity());
        if(voucher!=null) repository.useVoucher(orderId,voucher.id(),discount);
        repository.insertRelated(orderId,userId,initial,fee,method);
        Long ownerId=repository.notifyStoreOwner(input.restaurantId(),orderId,"Có đơn hàng mới","Bạn vừa nhận đơn #"+code);
        if(ownerId!=null) events.publishEvent(new FcmNotificationEvent(List.of(ownerId),"new_order","Có đơn hàng mới","Bạn vừa nhận đơn #"+code,orderId));
        return new CreateResponse(Long.toString(orderId),new OrderSummary(Long.toString(orderId),initial.toUpperCase(Locale.ROOT),total,Long.toString(input.restaurantId())));
    }

    @Transactional
    public void changeStatus(FirebasePrincipal principal,long id,StatusRequest input) {
        long userId=requireUserId(principal);
        String status=input==null||input.status()==null?"":input.status().toLowerCase(Locale.ROOT);
        if(!List.of("pending_payment","pending","accepted","processing","shipping","completed","cancelled").contains(status)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Trạng thái đơn không hợp lệ");
        var order=repository.lockOrder(id); if(order==null) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy đơn");
        // The legacy PHP route checked actor ownership but did not constrain the from -> to edge.
        // This explicit graph intentionally tightens that behavior while preserving its response shape.
        if(!validTransition(order.status(),status)) throw error(HttpStatus.CONFLICT,"Không thể chuyển trạng thái đơn từ "+order.status()+" sang "+status);
        boolean roleAllowed=switch(principal.databaseRole()) {
            case "customer" -> order.customerId()==userId&&"cancelled".equals(status);
            case "seller" -> repository.ownsStore(order.storeId(),userId)&&List.of("accepted","processing","cancelled").contains(status);
            case "driver" -> repository.assignedDriver(id,userId)&&List.of("shipping","completed").contains(status);
            case "admin" -> true; default -> false;
        };
        if(!roleAllowed) throw error(HttpStatus.FORBIDDEN,"Không có quyền cập nhật đơn này");
        repository.updateOrderStatus(id,status,input.cancelReason()); repository.updateDeliveryStatus(id,status);
        repository.insertStatusLog(id,status,input.note(),userId);
        if(order.customerId()!=userId) {
            String title="Cập nhật đơn hàng";
            String body="Đơn hàng của bạn: "+status.replace('_',' ');
            repository.notifyCustomer(order.customerId(),id,status);
            events.publishEvent(new FcmNotificationEvent(List.of(order.customerId()),"order_status",title,body,id));
        }
    }

    private static boolean validTransition(String from,String to){
        return switch(from){
            case "pending_payment" -> List.of("pending","cancelled").contains(to);
            case "pending" -> List.of("accepted","cancelled").contains(to);
            case "accepted" -> List.of("processing","shipping","cancelled").contains(to);
            case "processing" -> List.of("shipping","cancelled").contains(to);
            case "shipping" -> "completed".equals(to);
            case "completed","cancelled" -> false;
            default -> false;
        };
    }

    @Transactional
    public DriverView driver(FirebasePrincipal principal) {
        long userId=requireRole(principal,"driver"); repository.ensureDriver(userId);
        var d=repository.driver(userId);
        return new DriverView(Long.toString(d.id()),principal.firebaseUid(),d.model(),d.type(),"available".equals(d.status()),d.status(),d.trips());
    }
    @Transactional
    public void updateDriver(FirebasePrincipal principal, Boolean available,String vehicleName,String vehicleType) {
        long userId=requireRole(principal,"driver"); repository.ensureDriver(userId);
        var old=repository.driver(userId); String status=available==null?old.status():(available?"available":"offline");
        repository.updateDriver(userId,status,vehicleName,vehicleType);
    }
    @Transactional
    public void accept(FirebasePrincipal principal,long orderId) {
        long userId=requireRole(principal,"driver");
        long driverId=repository.lockDriver(userId);
        if(driverId==0) throw error(HttpStatus.CONFLICT,"Hãy bật trạng thái sẵn sàng trước khi nhận đơn");
        if(!repository.lockAssignableOrder(orderId)) throw error(HttpStatus.CONFLICT,"Đơn vừa được nhận hoặc không còn khả dụng");
        if(repository.assign(driverId,orderId)==0) throw error(HttpStatus.CONFLICT,"Đơn vừa được nhận hoặc không còn khả dụng");
        repository.markDriverBusy(driverId);
    }

    private static long requireUserId(FirebasePrincipal p){if(p==null||p.userId()==null)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ tài khoản");return p.userId();}
    private static long requireRole(FirebasePrincipal p,String role){long id=requireUserId(p);if(!role.equals(p.databaseRole()))throw error(HttpStatus.FORBIDDEN,"Không có quyền thực hiện thao tác");return id;}
    private static ApiException error(HttpStatus s,String message){return new ApiException(s,message);}
    private static String value(String s){return s==null?"":s;}
    private static String first(String a,String b){return a==null||a.isBlank()?value(b):a;}
    private static double distance(Double a,Double b,Double c,Double d){if(a==null||b==null||c==null||d==null||Math.abs(a)<.0001&&Math.abs(b)<.0001||Math.abs(c)<.0001&&Math.abs(d)<.0001||a< -90||a>90||c< -90||c>90||b< -180||b>180||d< -180||d>180)return 0;double p1=Math.toRadians(a),p2=Math.toRadians(c),dp=Math.toRadians(c-a),dl=Math.toRadians(d-b);double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return Math.max(.1,Math.round(6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h))*10)/10d);}
    private record PricedItem(OrderWorkflowRepository.DishRow dish,int quantity){}
    public record CreateResponse(String id,OrderSummary data){}
}
