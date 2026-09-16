package com.meper.chatbi.spi;

/**
 * 执行时的权限执法状态。
 *
 * <p>阶段 1（引导管理员）所有执行均为 {@link #BOOTSTRAP_ADMIN_UNRESTRICTED}：
 * 该值必须显式出现在 ExecutionContext 与 API 响应中，防止遗忘「本阶段尚无权限约束」。
 * P2 权限体系接入后统一切换为 {@link #ENFORCED}。
 */
public enum EnforcementState {

    /** 引导管理员阶段：无权限约束。仅允许存在于阶段 1。 */
    BOOTSTRAP_ADMIN_UNRESTRICTED,

    /** 已接入策略执法（P2+）。 */
    ENFORCED
}
