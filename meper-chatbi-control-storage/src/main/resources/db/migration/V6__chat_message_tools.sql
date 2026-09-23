-- 0.1 Agent chat：助手消息携带该轮工具调用摘要（回放渲染）
-- P2 权限模型顺延为 V7__permission_model.sql

ALTER TABLE meper_chat_message
    ADD COLUMN tools JSON NULL COMMENT '该轮工具调用摘要数组（按发生顺序，如 ["list_tables(meperdb)","run_query: SELECT ..."]，仅 ASSISTANT 行携带）';
