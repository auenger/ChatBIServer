-- 0.1 Agent chat MVP：模型 provider 配置（协议 + 接入点 + 模型）
-- 原则：apiKey 只存 AES-256-GCM 密文（meper_credential 同款模式）；API/页面/日志不回显明文
-- P2 权限模型顺延为 V3__permission_model.sql（见 docs/plans/p01-agent-chat-mvp.md §6）

CREATE TABLE meper_model_provider (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    name               VARCHAR(128)  NOT NULL,
    protocol           VARCHAR(32)   NOT NULL COMMENT 'OPENAI_COMPATIBLE / ANTHROPIC',
    base_url           VARCHAR(512)  NOT NULL,
    model              VARCHAR(128)  NOT NULL,
    api_key_ciphertext VARBINARY(1024) NOT NULL,
    api_key_iv         VARBINARY(32) NOT NULL,
    params             JSON          NULL COMMENT '采样参数等（temperature/maxTokens）',
    enabled            TINYINT(1)    NOT NULL DEFAULT 1,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_model_provider PRIMARY KEY (id),
    CONSTRAINT uq_model_provider_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
