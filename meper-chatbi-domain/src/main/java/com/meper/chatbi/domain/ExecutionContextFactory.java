package com.meper.chatbi.domain;

import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.Purpose;
import com.meper.chatbi.spi.model.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * ExecutionContext 唯一签发入口：由服务端依据已验证身份生成，前端不可自填授权。
 * 阶段 1 的执法状态恒为 BOOTSTRAP_ADMIN_UNRESTRICTED（显式声明，防止遗忘本阶段无权限约束）。
 */
@Component
public class ExecutionContextFactory {

    private static final String DEFAULT_TENANT = "default";

    public ExecutionContext workbench(String subject, long datasourceId) {
        return ExecutionContext.of(DEFAULT_TENANT, subject, Purpose.WORKBENCH, datasourceId,
                EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED);
    }

    public ExecutionContext datasourceAdmin(String subject, long datasourceId) {
        return ExecutionContext.of(DEFAULT_TENANT, subject, Purpose.DATASOURCE_ADMIN, datasourceId,
                EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED);
    }
}
