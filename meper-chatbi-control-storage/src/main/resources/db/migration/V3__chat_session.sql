-- 0.1 Agent chat：对话记录持久化（会话 = sessionId 分组；工具活动不落库）
-- P2 权限模型顺延为 V4__permission_model.sql（见 docs/plans/p01-agent-chat-mvp.md）

CREATE TABLE meper_chat_message (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    session_id    VARCHAR(64)   NOT NULL,
    subject       VARCHAR(128)  NOT NULL,
    datasource_id BIGINT        NOT NULL,
    provider_id   BIGINT        NOT NULL,
    namespace     VARCHAR(255)  NULL,
    seq           INT           NOT NULL COMMENT '会话内序号（0 起）',
    role          VARCHAR(16)   NOT NULL COMMENT 'USER / ASSISTANT',
    content       MEDIUMTEXT    NOT NULL,
    session_title VARCHAR(128)  NULL COMMENT '会话标题（取首条 USER 消息截断，仅该行携带）',
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_message PRIMARY KEY (id),
    KEY idx_chat_session (subject, session_id, seq),
    KEY idx_chat_subject_time (subject, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
