-- One-time migration for the API query patterns in backenddd/api/index.php.
-- Run against food_delivery_app after reviewing SHOW INDEX output; this file is intentionally not auto-applied.

-- Public store catalog: filter active/non-deleted rows, then sort by name and stable id.
-- Benefits: SELECT s.<projected fields> FROM stores s WHERE s.status='active' AND s.deleted_at IS NULL ORDER BY s.name,s.id LIMIT ? OFFSET ?
CREATE INDEX ix_stores_catalog_page ON stores(status, deleted_at, name, id);

-- Store menu: filter by store, availability and soft deletion, then return newest dishes first.
-- Benefits: dishes list query with d.store_id=?, d.is_available=1, d.deleted_at IS NULL ORDER BY d.created_at DESC,d.id DESC.
CREATE INDEX ix_dishes_store_catalog_page ON dishes(store_id, is_available, deleted_at, created_at, id);

-- Cross-store public dish catalog: same filtering/sort when restaurantId is omitted.
-- Benefits: dishes list query with d.is_available=1 AND d.deleted_at IS NULL ORDER BY d.created_at DESC,d.id DESC.
CREATE INDEX ix_dishes_catalog_page ON dishes(is_available, deleted_at, created_at, id);

-- Seller order list: current index starts with (store_id,status), but this endpoint does not constrain status.
-- Benefits: orders joined through stores/merchants and sorted by o.placed_at DESC,o.id DESC for a seller.
CREATE INDEX ix_orders_store_placed_page ON orders(store_id, placed_at, id);

-- Latest visible message per conversation and stable message pagination, excluding soft-deleted rows.
-- Benefits: message history WHERE conversation_id=? AND deleted_at IS NULL ORDER BY created_at DESC,id DESC;
CREATE INDEX ix_messages_conversation_visible_time ON messages(conversation_id, deleted_at, created_at, id);

-- Latest message lookup groups by conversation and takes MAX(id), while excluding soft-deleted messages.
-- Benefits: latest-message derived query used by GET /conversations.
CREATE INDEX ix_messages_conversation_visible_id ON messages(conversation_id, deleted_at, id);

-- Unread count groups visible incoming messages by conversation and tests sender/read timestamp.
-- Benefits: unread aggregation query used by GET /conversations.
CREATE INDEX ix_messages_conversation_unread ON messages(conversation_id, deleted_at, sender_user_id, created_at);

-- Completion timestamp lookup filters status logs for each returned order by order, then status.
-- Benefits: correlated MAX(created_at) lookup used to populate completedAt in GET /orders.
CREATE INDEX ix_order_logs_order_status_time ON order_status_logs(order_id, status, created_at);

-- Conversation list lookup starts from the current user's participant row and then joins its conversation.
-- Benefits: SELECT conversations for a participant, with conversation_id available from the covering index.
CREATE INDEX ix_participants_user_conversation ON conversation_participants(user_id, conversation_id);
