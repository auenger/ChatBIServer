/**
 * 权限与策略层：身份映射、权限域、表/字段/行/操作策略决策、审批流。
 *
 * <p>目标模型（见架构方案 §5）：AccessModel、PermissionDomain、ResourcePolicy、
 * PolicySnapshot、OperationApproval、OperationAudit。表策略至少覆盖
 * SELECT/INSERT/UPDATE/DELETE、行规则、可读/筛选/新增/修改/脱敏字段、最大行数与 revision。
 *
 * <p>注意：空字段列表的语义必须显式版本化 —— MEPER 采用「空列表 = 默认拒绝」，
 * 不得静默继承 Chat2DB「空列表 = 全部可读」的语义；历史策略迁入前必须显式转换。
 */
package com.meper.chatbi.policy;
