INSERT INTO vouchers (code,title,discount_type,discount_value,max_discount,min_order_amount,image_url,usage_limit,starts_at,ends_at,is_active) VALUES
('FHBANHMI10','Bánh mì giòn nóng','fixed',10000,NULL,45000,'https://res.cloudinary.com/dhscw17vq/image/upload/c_fill,w_360,h_420,q_auto:good,f_jpg/v1795179931/food_banh_mi_v2_jdrkep.jpg',100,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY),1),
('FHCOM15','Cơm ngon trọn bữa','percentage',15,25000,65000,'https://res.cloudinary.com/dhscw17vq/image/upload/c_fill,w_360,h_420,q_auto:good,f_jpg/v1795179940/food_com_v2_btvzo5.jpg',100,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY),1),
('FHTRASUA12','Trà sữa ngọt ngào','fixed',12000,NULL,59000,'https://res.cloudinary.com/dhscw17vq/image/upload/c_fill,w_360,h_420,q_auto:good,f_jpg/v1795179949/food_tra_sua_v2_qvh9we.jpg',100,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY),1),
('FHBUN20','Hẹn nhau ăn bún','percentage',20,35000,99000,'https://res.cloudinary.com/dhscw17vq/image/upload/c_fill,w_360,h_420,q_auto:good,f_jpg/v1795179958/food_bun_v2_fs6tfq.jpg',100,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY),1),
('FHPHOSHIP','Phở nóng đến nhà','free_shipping',0,NULL,89000,'https://res.cloudinary.com/dhscw17vq/image/upload/c_fill,w_360,h_420,q_auto:good,f_jpg/v1795179967/food_pho_v2_qfpuct.jpg',100,NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY),1)
ON DUPLICATE KEY UPDATE code=vouchers.code;
