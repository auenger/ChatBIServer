package com.meper.chatbi.spi.model;

import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.Purpose;

import java.time.Instant;
import java.util.UUID;

/**
 * 执行上下文：由服务端依据已验证身份生成（前端不可自行填写授权），
 * 随调用显式传递，禁止用 ThreadLocal 隐式携带（架构方案 §4 原则 4）。
 *
 * @param tenantId        租户（阶段 1 固定 "default"）
 * @param subject         已验证主体（阶段 1 为引导管理员用户名）
 * @param purpose         调用用途
 * @param datasourceId    目标数据源
 * @param enforcementState 权限执法状态（阶段 1 恒为 BOOTSTRAP_ADMIN_UNRESTRICTED）
 * @param correlationId   关联 ID（贯穿日志/审计）
 * @param issuedAt        签发时间
 */
public record ExecutionContext(
        String tenantId,
        String subject,
        Purpose purpose,
        long datasourceId,
        EnforcementState enforcementState,
        String correlationId,
        Instant issuedAt) {

    public ExecutionContext {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject 不能为空");
        }
        if (enforcementState == EnforcementState.ENFORCED) {
            throw new IllegalStateException("ENFORCED 状态需 P2 权限体系接入后启用");
        }
    }

    /** 服务端工厂入口：生成带随机 correlationId 的上下文。 */
    public static ExecutionContext of(String tenantId, String subject, Purpose purpose,
                                      long datasourceId, EnforcementState enforcementState) {
        return new ExecutionContext(tenantId, subject, purpose, datasourceId,
                enforcementState, UUID.randomUUID().toString(), Instant.now());
    }
}
