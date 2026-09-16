package com.meper.chatbi.spi;

/** 调用用途：参与 ExecutionContext 生成与审计分类。 */
public enum Purpose {

    /** 数据源管理控制面。 */
    DATASOURCE_ADMIN,

    /** SQL 工作台（人类交互）。 */
    WORKBENCH,

    /** 系统开放 API。 */
    API,

    /** Agent 工具调用（P5）。 */
    AGENT
}
