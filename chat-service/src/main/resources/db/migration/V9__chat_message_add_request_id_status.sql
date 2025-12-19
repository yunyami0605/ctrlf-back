ALTER TABLE chat.chat_message
  ADD COLUMN request_id VARCHAR(100),
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_message_request_id
  ON chat.chat_message (request_id)
  WHERE request_id IS NOT NULL;
