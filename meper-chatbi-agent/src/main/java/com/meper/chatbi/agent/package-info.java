/**
 * Agent 层：Agent 定义、Run 编排、工具范围与交付。
 *
 * <p>约束（见架构方案 §5、实施方案 §1）：
 * <ul>
 *   <li>Agent 工具仅经 QueryEnforcement 执行，不持有业务库连接或凭据；</li>
 *   <li>Agent 工具权限永远是主体权限的收窄；Agent 初始默认只读；</li>
 *   <li>提案（propose_mutation / propose_object_change）不等于执行许可，交付前需再鉴权；</li>
 *   <li>模型输出不得扩大权限；历史/知识预算有限。</li>
 *   <li>gateway 归一化 OpenAI reasoning 与 Anthropic thinking；思考独立交付和落库，不混入回答正文。</li>
 *   <li>内置图表技能仅提供输出规范；图表数据来自既有只读查询工具，不新增数据库访问路径。</li>
 * </ul>
 * <p>DataWiki 的 glob/grep/read 桥接在 gateway，绑定当前会话数据源；每轮知识调用和字符预算有限，不全量注入。
 * <p>分析和知识编辑用途工具分离；模型仅写 Wiki 草稿，不能发布或写业务库；会话固定范围与有效模型。活动页面只提供关注上下文；可显式收窄页面范围，本会话创建的文档仍可继续编辑。
 */
package com.meper.chatbi.agent;
