package com.meper.chatbi.web.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 引导管理员校验（阶段 1）。账号密码来自配置（生产用环境变量覆盖）；
 * 比较使用常数时间实现。P2 接入正式身份体系后整体替换。
 */
@Component
public class BootstrapAdminAuth {

    private final String username;
    private final String password;

    public BootstrapAdminAuth(@Value("${meper.bootstrap-admin.username}") String username,
                              @Value("${meper.bootstrap-admin.password}") String password) {
        this.username = username;
        this.password = password;
    }

    public boolean matches(String username, String password) {
        return MessageDigest.isEqual(
                username.getBytes(StandardCharsets.UTF_8), this.username.getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8), this.password.getBytes(StandardCharsets.UTF_8));
    }
}
