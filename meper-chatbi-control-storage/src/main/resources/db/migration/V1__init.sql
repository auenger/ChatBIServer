-- MEPER ChatBI 控制库初始 schema
-- 原则：数据源表不含密码列（凭据独立版本化加密存储）；审计不含凭据与完整结果

CREATE TABLE meper_datasource_profile (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(128) NOT NULL,
    db_type       VARCHAR(32)  NOT NULL,
    host          VARCHAR(255) NOT NULL,
    port          INT          NOT NULL,
    database_name VARCHAR(255) NULL,
    username      VARCHAR(128) NOT NULL,
    ssl_mode      VARCHAR(16)  NOT NULL DEFAULT 'DISABLED',
    extend_info   JSON         NULL,
    credential_version BIGINT  NOT NULL DEFAULT 1,
    created_by    VARCHAR(128) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_datasource_profile PRIMARY KEY (id),
    CONSTRAINT uq_datasource_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE meper_credential (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    datasource_id BIGINT        NOT NULL,
    version       BIGINT        NOT NULL,
    ciphertext    VARBINARY(1024) NOT NULL,
    iv            VARBINARY(32) NOT NULL,
    status        VARCHAR(16)   NOT NULL COMMENT 'ACTIVE / RETIRED',
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_credential PRIMARY KEY (id),
    CONSTRAINT uq_credential_ds_version UNIQUE (datasource_id, version),
    CONSTRAINT fk_credential_datasource FOREIGN KEY (datasource_id)
        REFERENCES meper_datasource_profile (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE meper_execution (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    datasource_id     BIGINT        NOT NULL,
    subject           VARCHAR(128)  NOT NULL,
    purpose           VARCHAR(32)   NOT NULL,
    correlation_id    VARCHAR(64)   NOT NULL,
    sql_text          MEDIUMTEXT    NOT NULL,
    statement_count   INT           NOT NULL,
    status            VARCHAR(16)   NOT NULL COMMENT 'SUCCESS / FAILED / PARTIAL',
    error             VARCHAR(1024) NULL,
    enforcement_state VARCHAR(48)   NOT NULL,
    started_at        DATETIME(6)   NOT NULL,
    finished_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_execution PRIMARY KEY (id),
    KEY idx_execution_ds (datasource_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE meper_execution_statement (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    execution_id BIGINT        NOT NULL,
    seq          INT           NOT NULL,
    sql_text     MEDIUMTEXT    NOT NULL,
    category     VARCHAR(16)   NOT NULL,
    is_query     TINYINT(1)    NOT NULL,
    row_count    INT           NULL,
    update_count INT           NULL,
    truncated    TINYINT(1)    NOT NULL DEFAULT 0,
    duration_ms  BIGINT        NOT NULL,
    status       VARCHAR(16)   NOT NULL COMMENT 'SUCCESS / FAILED',
    error        VARCHAR(1024) NULL,
    CONSTRAINT pk_execution_statement PRIMARY KEY (id),
    CONSTRAINT fk_stmt_execution FOREIGN KEY (execution_id)
        REFERENCES meper_execution (id) ON DELETE CASCADE,
    KEY idx_stmt_execution (execution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE meper_audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    tenant_id    VARCHAR(64)  NOT NULL,
    subject      VARCHAR(128) NOT NULL,
    action       VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id  VARCHAR(64)  NULL,
    detail       JSON         NULL COMMENT '脱敏后的上下文，禁止凭据与完整结果',
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    KEY idx_audit_time (occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
