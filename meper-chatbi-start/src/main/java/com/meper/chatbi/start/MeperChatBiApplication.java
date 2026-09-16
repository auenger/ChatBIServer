package com.meper.chatbi.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MEPER ChatBI Server 组装入口。
 *
 * <p>本模块只负责装配、配置与健康检查，不承载业务逻辑；
 * 组件扫描覆盖整个 {@code com.meper.chatbi} 命名空间。
 */
@SpringBootApplication(scanBasePackages = "com.meper.chatbi")
public class MeperChatBiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeperChatBiApplication.class, args);
    }
}
