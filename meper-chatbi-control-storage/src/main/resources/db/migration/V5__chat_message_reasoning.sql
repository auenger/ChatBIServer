-- 0.1 Agent chat：助手消息携带思维链（思考过程回放）
-- P2 权限模型顺延为 V6__permission_model.sql

ALTER TABLE meper_chat_message
    ADD COLUMN reasoning MEDIUMTEXT NULL COMMENT '思维链全文（仅 ASSISTANT 行携带；模型 reasoning_content 聚合）';
