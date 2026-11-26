-- Proposed indexes for chat query paths. Do not run automatically in production without approval.
-- User -> conversations listing and participant joins.
ALTER TABLE conversation_participants
    ADD INDEX ix_chat_participant_user_conv (user_id, conversation_id);

-- Latest message aggregation and before/after ID cursor pages within a conversation.
ALTER TABLE messages
    ADD INDEX ix_chat_messages_conversation_id (conversation_id, id);

-- Incoming unread counts and marking messages seen for a conversation.
ALTER TABLE messages
    ADD INDEX ix_chat_messages_unread (conversation_id, sender_user_id, is_seen, created_at);
