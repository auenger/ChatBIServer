package com.meper.chatbi.agent.gateway;

/**
 * 连通测试结果（与数据源 TestResult 同构：成功/摘要/延迟）。
 *
 * @param success   是否连通
 * @param message   失败摘要（脱敏，不含 apiKey）
 * @param latencyMs 往返延迟
 */
public record ProbeResult(boolean success, String message, long latencyMs) {
}
