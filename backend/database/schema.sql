-- FoodHub MVP schema for MySQL 8 / XAMPP.
-- Firebase Authentication is the identity provider; never store passwords here.
-- Live driver/order coordinates and presence remain in Firebase Realtime Database.
CREATE DATABASE IF NOT EXISTS food_delivery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE food_delivery;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  firebase_uid VARCHAR(128) NOT NULL,
  name VARCHAR(160) NOT NULL DEFAULT '',
  email VARCHAR(254) NULL,
  phone VARCHAR(32) NULL,
  address_line VARCHAR(500) NULL,
  role ENUM('customer','seller','driver','admin') NOT NULL DEFAULT 'customer',
  avatar_url VARCHAR(1024) NULL,
  status ENUM('active','suspended','pending') NOT NULL DEFAULT 'active',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id), UNIQUE KEY uq_users_firebase_uid (firebase_uid),
  UNIQUE KEY uq_users_email (email), KEY ix_users_role_status (role,status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_addresses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  label VARCHAR(60) NOT NULL DEFAULT 'Nhà riêng', receiver_name VARCHAR(160) NOT NULL,
  receiver_phone VARCHAR(32) NOT NULL, address_line VARCHAR(500) NOT NULL,
  ward VARCHAR(120) NULL, district VARCHAR(120) NULL, city VARCHAR(120) NULL,
  latitude DECIMAL(10,7) NULL, longitude DECIMAL(10,7) NULL, is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_addresses_user(user_id),
  CONSTRAINT fk_addresses_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS merchants (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, owner_user_id BIGINT UNSIGNED NOT NULL,
  brand_name VARCHAR(180) NOT NULL, hotline VARCHAR(32) NULL, description TEXT NULL,
  status ENUM('pending','active','suspended') NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_merchants_owner(owner_user_id),
  CONSTRAINT fk_merchants_owner FOREIGN KEY(owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS stores (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, merchant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(180) NOT NULL, description TEXT NULL, logo_url VARCHAR(1024) NULL,
  cover_url VARCHAR(1024) NULL, address_line VARCHAR(500) NULL, district VARCHAR(120) NULL,
  city VARCHAR(120) NULL, latitude DECIMAL(10,7) NULL, longitude DECIMAL(10,7) NULL,
  phone VARCHAR(32) NULL, email VARCHAR(254) NULL, is_open BOOLEAN NOT NULL DEFAULT TRUE,
  status ENUM('active','paused','pending') NOT NULL DEFAULT 'active',
  rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0, rating_count INT UNSIGNED NOT NULL DEFAULT 0,
  avg_prep_minutes SMALLINT UNSIGNED NULL, open_time TIME NULL, close_time TIME NULL,
  order_notify BOOLEAN NOT NULL DEFAULT TRUE, sound_notify BOOLEAN NOT NULL DEFAULT TRUE,
  push_notify BOOLEAN NOT NULL DEFAULT TRUE, email_notify BOOLEAN NOT NULL DEFAULT FALSE,
  promo_notify BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  PRIMARY KEY(id), KEY ix_stores_merchant(merchant_id), KEY ix_stores_discovery(status,is_open,city),
  CONSTRAINT fk_stores_merchant FOREIGN KEY(merchant_id) REFERENCES merchants(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS store_staff (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, store_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL, staff_role ENUM('owner','manager','staff') NOT NULL DEFAULT 'manager',
  is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_store_staff(store_id,user_id),
  CONSTRAINT fk_staff_store FOREIGN KEY(store_id) REFERENCES stores(id) ON DELETE CASCADE,
  CONSTRAINT fk_staff_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS store_opening_hours (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, store_id BIGINT UNSIGNED NOT NULL,
  weekday TINYINT UNSIGNED NOT NULL, open_time TIME NOT NULL, close_time TIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_store_weekday(store_id,weekday),
  CONSTRAINT fk_hours_store FOREIGN KEY(store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS favorites (
  user_id BIGINT UNSIGNED NOT NULL, store_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id,store_id),
  CONSTRAINT fk_favorites_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorites_store FOREIGN KEY(store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS platform_categories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, name VARCHAR(100) NOT NULL, slug VARCHAR(120) NOT NULL,
  icon_url VARCHAR(1024) NULL, sort_order INT NOT NULL DEFAULT 0, is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_platform_category_slug(slug), KEY ix_platform_categories_active(is_active,sort_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS menu_categories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, store_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(120) NOT NULL, sort_order INT NOT NULL DEFAULT 0, is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_menu_categories_store(store_id,is_active,sort_order),
  CONSTRAINT fk_menu_categories_store FOREIGN KEY(store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS dishes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, store_id BIGINT UNSIGNED NOT NULL,
  menu_category_id BIGINT UNSIGNED NULL, platform_category_id BIGINT UNSIGNED NULL,
  name VARCHAR(180) NOT NULL, description TEXT NULL, image_url VARCHAR(1024) NULL,
  cloudinary_public_id VARCHAR(255) NULL, price DECIMAL(12,2) NOT NULL,
  original_price DECIMAL(12,2) NULL, is_available BOOLEAN NOT NULL DEFAULT TRUE,
  stock_quantity INT UNSIGNED NULL, prep_minutes SMALLINT UNSIGNED NULL,
  rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0, rating_count INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL, PRIMARY KEY(id), KEY ix_dishes_store_available(store_id,is_available),
  KEY ix_dishes_category(platform_category_id,is_available), KEY ix_dishes_menu(menu_category_id,is_available),
  CONSTRAINT fk_dishes_store FOREIGN KEY(store_id) REFERENCES stores(id),
  CONSTRAINT fk_dishes_menu_category FOREIGN KEY(menu_category_id) REFERENCES menu_categories(id) ON DELETE SET NULL,
  CONSTRAINT fk_dishes_platform_category FOREIGN KEY(platform_category_id) REFERENCES platform_categories(id) ON DELETE SET NULL,
  CONSTRAINT ck_dishes_price CHECK(price >= 0), CONSTRAINT ck_dishes_original_price CHECK(original_price IS NULL OR original_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS option_groups (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, store_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(120) NOT NULL, min_select SMALLINT UNSIGNED NOT NULL DEFAULT 0, max_select SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_option_groups_store(store_id), CONSTRAINT fk_option_groups_store FOREIGN KEY(store_id) REFERENCES stores(id),
  CONSTRAINT ck_option_group_select CHECK(min_select <= max_select)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS options (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, option_group_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(120) NOT NULL, extra_price DECIMAL(12,2) NOT NULL DEFAULT 0, is_available BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_options_group(option_group_id,is_available,sort_order),
  CONSTRAINT fk_options_group FOREIGN KEY(option_group_id) REFERENCES option_groups(id) ON DELETE CASCADE,
  CONSTRAINT ck_option_extra_price CHECK(extra_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS dish_option_groups (
  dish_id BIGINT UNSIGNED NOT NULL, option_group_id BIGINT UNSIGNED NOT NULL, sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY(dish_id,option_group_id),
  CONSTRAINT fk_dog_dish FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE CASCADE,
  CONSTRAINT fk_dog_group FOREIGN KEY(option_group_id) REFERENCES option_groups(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vouchers (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, store_id BIGINT UNSIGNED NULL,
  code VARCHAR(64) NOT NULL, title VARCHAR(180) NOT NULL, description TEXT NULL,
  discount_type ENUM('percentage','fixed','free_shipping') NOT NULL,
  discount_value DECIMAL(12,2) NOT NULL, max_discount DECIMAL(12,2) NULL,
  min_order_amount DECIMAL(12,2) NOT NULL DEFAULT 0, usage_limit INT UNSIGNED NULL,
  used_count INT UNSIGNED NOT NULL DEFAULT 0, starts_at DATETIME NULL, ends_at DATETIME NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_voucher_code(code), KEY ix_vouchers_active(is_active,starts_at,ends_at),
  CONSTRAINT fk_vouchers_store FOREIGN KEY(store_id) REFERENCES stores(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS carts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  store_id BIGINT UNSIGNED NOT NULL, address_id BIGINT UNSIGNED NULL, voucher_id BIGINT UNSIGNED NULL,
  payment_method ENUM('cash','payos') NOT NULL DEFAULT 'cash',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_cart_user_store(user_id,store_id), KEY ix_carts_store(store_id),
  CONSTRAINT fk_carts_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_carts_store FOREIGN KEY(store_id) REFERENCES stores(id),
  CONSTRAINT fk_carts_address FOREIGN KEY(address_id) REFERENCES user_addresses(id) ON DELETE SET NULL,
  CONSTRAINT fk_carts_voucher FOREIGN KEY(voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cart_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, cart_id BIGINT UNSIGNED NOT NULL,
  dish_id BIGINT UNSIGNED NOT NULL, quantity SMALLINT UNSIGNED NOT NULL,
  note VARCHAR(500) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_cart_items_cart(cart_id),
  CONSTRAINT fk_cart_items_cart FOREIGN KEY(cart_id) REFERENCES carts(id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_items_dish FOREIGN KEY(dish_id) REFERENCES dishes(id),
  CONSTRAINT ck_cart_quantity CHECK(quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cart_item_options (
  cart_item_id BIGINT UNSIGNED NOT NULL, option_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY(cart_item_id,option_id),
  CONSTRAINT fk_cio_item FOREIGN KEY(cart_item_id) REFERENCES cart_items(id) ON DELETE CASCADE,
  CONSTRAINT fk_cio_option FOREIGN KEY(option_id) REFERENCES options(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, code VARCHAR(40) NOT NULL,
  customer_id BIGINT UNSIGNED NOT NULL, store_id BIGINT UNSIGNED NOT NULL,
  status ENUM('pending_payment','pending','processing','accepted','shipping','completed','cancelled') NOT NULL DEFAULT 'pending',
  receiver_name VARCHAR(160) NOT NULL, receiver_phone VARCHAR(32) NOT NULL, delivery_address VARCHAR(600) NOT NULL,
  delivery_lat DECIMAL(10,7) NULL, delivery_lng DECIMAL(10,7) NULL, customer_note VARCHAR(1000) NULL,
  subtotal DECIMAL(12,2) NOT NULL, delivery_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0, total_amount DECIMAL(12,2) NOT NULL,
  payment_method ENUM('cash','payos') NOT NULL, payment_status ENUM('pending','paid','failed','refunded') NOT NULL DEFAULT 'pending',
  placed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, accepted_at DATETIME NULL, ready_at DATETIME NULL,
  picked_up_at DATETIME NULL, delivered_at DATETIME NULL, cancelled_at DATETIME NULL, cancel_reason VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_order_code(code), KEY ix_orders_customer_placed(customer_id,placed_at),
  KEY ix_orders_store_status(store_id,status,placed_at), KEY ix_orders_status(status,placed_at),
  CONSTRAINT fk_orders_customer FOREIGN KEY(customer_id) REFERENCES users(id),
  CONSTRAINT fk_orders_store FOREIGN KEY(store_id) REFERENCES stores(id),
  CONSTRAINT ck_order_amounts CHECK(subtotal >= 0 AND delivery_fee >= 0 AND discount_amount >= 0 AND total_amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, order_id BIGINT UNSIGNED NOT NULL,
  dish_id BIGINT UNSIGNED NULL, dish_name VARCHAR(180) NOT NULL, unit_price DECIMAL(12,2) NOT NULL,
  quantity SMALLINT UNSIGNED NOT NULL, line_total DECIMAL(12,2) NOT NULL, note VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id), KEY ix_order_items_order(order_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_dish FOREIGN KEY(dish_id) REFERENCES dishes(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_item_options (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, order_item_id BIGINT UNSIGNED NOT NULL,
  option_id BIGINT UNSIGNED NULL, option_name VARCHAR(120) NOT NULL, extra_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  PRIMARY KEY(id), KEY ix_order_item_options_item(order_item_id),
  CONSTRAINT fk_oio_item FOREIGN KEY(order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
  CONSTRAINT fk_oio_option FOREIGN KEY(option_id) REFERENCES options(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_status_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, order_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL, note VARCHAR(500) NULL, actor_user_id BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id), KEY ix_order_logs(order_id,created_at),
  CONSTRAINT fk_order_logs_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_logs_actor FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_vouchers (
  order_id BIGINT UNSIGNED NOT NULL, voucher_id BIGINT UNSIGNED NOT NULL,
  discount_amount DECIMAL(12,2) NOT NULL, PRIMARY KEY(order_id,voucher_id),
  CONSTRAINT fk_order_vouchers_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_vouchers_voucher FOREIGN KEY(voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, order_id BIGINT UNSIGNED NOT NULL,
  provider ENUM('payos','cash') NOT NULL, method VARCHAR(40) NOT NULL,
  payos_order_code BIGINT UNSIGNED NULL, payment_link_id VARCHAR(120) NULL,
  gateway_txn_id VARCHAR(160) NULL, amount DECIMAL(12,2) NOT NULL,
  status ENUM('pending','processing','paid','failed','cancelled','refunded') NOT NULL DEFAULT 'pending',
  checkout_url VARCHAR(1500) NULL, paid_at DATETIME NULL, failed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_payment_payos_code(payos_order_code), KEY ix_payments_order(order_id,created_at),
  CONSTRAINT fk_payments_order FOREIGN KEY(order_id) REFERENCES orders(id), CONSTRAINT ck_payment_amount CHECK(amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, payment_id BIGINT UNSIGNED NULL,
  provider_event_id VARCHAR(180) NULL, payload_hash CHAR(64) NOT NULL, event_type VARCHAR(100) NOT NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, processed_at DATETIME NULL,
  process_status ENUM('received','processed','ignored','failed') NOT NULL DEFAULT 'received',
  PRIMARY KEY(id), UNIQUE KEY uq_webhook_event(provider_event_id), KEY ix_webhook_payment(payment_id),
  CONSTRAINT fk_webhook_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS drivers (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  vehicle_type VARCHAR(60) NULL, vehicle_model VARCHAR(120) NULL, license_plate VARCHAR(32) NULL,
  work_status ENUM('offline','available','busy') NOT NULL DEFAULT 'offline',
  rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0, completed_trips INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_driver_user(user_id), KEY ix_driver_work_status(work_status),
  CONSTRAINT fk_driver_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS deliveries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, order_id BIGINT UNSIGNED NOT NULL, driver_id BIGINT UNSIGNED NULL,
  status ENUM('unassigned','assigned','accepted','picked_up','delivered','cancelled') NOT NULL DEFAULT 'unassigned',
  delivery_fee DECIMAL(12,2) NOT NULL DEFAULT 0, driver_earning DECIMAL(12,2) NOT NULL DEFAULT 0,
  assigned_at DATETIME NULL, accepted_at DATETIME NULL, picked_up_at DATETIME NULL, delivered_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_delivery_order(order_id), KEY ix_deliveries_driver_status(driver_id,status),
  CONSTRAINT fk_delivery_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_delivery_driver FOREIGN KEY(driver_id) REFERENCES drivers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, order_id BIGINT UNSIGNED NULL,
  type ENUM('customer_store','customer_driver','support') NOT NULL DEFAULT 'customer_store',
  created_by BIGINT UNSIGNED NOT NULL, status ENUM('open','closed') NOT NULL DEFAULT 'open',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_conversations_order(order_id),
  CONSTRAINT fk_conversations_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE SET NULL,
  CONSTRAINT fk_conversations_creator FOREIGN KEY(created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversation_participants (
  conversation_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL,
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_read_at DATETIME NULL,
  PRIMARY KEY(conversation_id,user_id), KEY ix_participants_user(user_id),
  CONSTRAINT fk_participant_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_participant_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, conversation_id BIGINT UNSIGNED NOT NULL,
  sender_user_id BIGINT UNSIGNED NOT NULL, message_type ENUM('text','image') NOT NULL DEFAULT 'text',
  content TEXT NULL, image_url VARCHAR(1500) NULL, is_seen BOOLEAN NOT NULL DEFAULT FALSE,
  is_delivered BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted_at DATETIME NULL,
  PRIMARY KEY(id), KEY ix_messages_conversation_time(conversation_id,created_at),
  CONSTRAINT fk_messages_conversation FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_sender FOREIGN KEY(sender_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(60) NOT NULL, title VARCHAR(180) NOT NULL, body VARCHAR(1000) NOT NULL,
  related_order_id BIGINT UNSIGNED NULL, is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, read_at DATETIME NULL,
  PRIMARY KEY(id), KEY ix_notifications_user(user_id,is_read,created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_order FOREIGN KEY(related_order_id) REFERENCES orders(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_device_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, user_id BIGINT UNSIGNED NOT NULL,
  fcm_token VARCHAR(512) NOT NULL, platform ENUM('android','ios','web') NOT NULL DEFAULT 'android',
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uq_fcm_token(fcm_token), KEY ix_device_tokens_user(user_id),
  CONSTRAINT fk_device_token_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activity_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, actor_user_id BIGINT UNSIGNED NULL,
  store_id BIGINT UNSIGNED NULL, type VARCHAR(60) NOT NULL, title VARCHAR(180) NOT NULL,
  description VARCHAR(1000) NULL, details TEXT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(id), KEY ix_activity_store(store_id,created_at), KEY ix_activity_actor(actor_user_id,created_at),
  CONSTRAINT fk_activity_actor FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_activity_store FOREIGN KEY(store_id) REFERENCES stores(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Deliberately omitted for the current app: wallets, KYC documents, regions/hubs,
-- tax/settlement, ads, loyalty, AI chat/recommendation history, and dining tables.
