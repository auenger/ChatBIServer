/**
 * Session Wiki：授权范围内的语义目录与有限会话历史，为网页与 Agent 提供表/字段业务语义上下文。
 *
 * <p>约束：目录内容必须经过授权过滤（借鉴 ITrustedQueryService 的身份/权限交集思路）；
 * 会话历史有预算上限；不因语义目录的存在扩大任何主体的权限。
 */
package com.meper.chatbi.wiki;
