-- 0.1 Agent chat：助手消息携带该轮真实 token 用量（上下文比例在会话重开后可恢复）
-- P2 权限模型顺延为 V5__permission_model.sql

ALTER TABLE meper_chat_message
    ADD COLUMN prompt_tokens     BIGINT NULL COMMENT '该轮输入 token（仅 ASSISTANT 行携带，来源模型返回 usage）',
    ADD COLUMN completion_tokens BIGINT NULL COMMENT '该轮输出 token（仅 ASSISTANT 行携带）';
