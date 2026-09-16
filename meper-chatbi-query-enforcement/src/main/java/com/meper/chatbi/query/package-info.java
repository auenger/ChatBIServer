/**
 * 查询与操作执法层：SQL/对象操作分类、目标资源解析、权限校验、执行规划与结果治理（裁剪/脱敏/限行）。
 *
 * <p>所有 DQL/DML/DDL/DCL、例程、导入导出与非关系型操作必须经统一管线
 * （见架构方案 §4），不存在绕过本层的执行路径：
 * 管理员接口与 Agent 工具同样不得直连 JDBC。
 *
 * <p>{@code Connection.setReadOnly(true)} 只是连接提示，不是授权机制；
 * 真正边界是策略决策、受限数据库凭据、操作分类与执行前校验。
 */
package com.meper.chatbi.query;
