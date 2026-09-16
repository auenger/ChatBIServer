package com.meper.chatbi.spi.model;

/**
 * 凭据引用：只指向加密存储中的某个版本，不含任何机密内容。
 *
 * <p>轮换语义：新版本 ACTIVE 后旧版本 RETIRED；连接池按 version 隔离，
 * RETIRED 版本的池被立即逐出（实施方案 §4：撤权/轮换后不得复用旧连接）。
 */
public record CredentialRef(long datasourceId, long version, Status status) {

    public enum Status { ACTIVE, RETIRED }
}
