package com.example.backend.address;

public final class AddressDtos {
    private AddressDtos() { }
    public record SaveRequest(String label,String name,String receiverName,String receiverPhone,String phone,
            String address,String addressDetail,String ward,String district,String city,Double latitude,
            Double longitude,Boolean isDefault) { }
    public record AddressView(String id,String label,String name,String receiverName,String phone,
            String receiverPhone,String address,String addressDetail,String ward,String district,String city,
            Double latitude,Double longitude,boolean isDefault) { }
    public record IdResponse(String id) { }
    public record OkResponse(boolean ok) { }
}
