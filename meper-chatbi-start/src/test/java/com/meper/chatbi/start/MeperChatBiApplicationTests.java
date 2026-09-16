package com.meper.chatbi.start;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试：验证多模块装配后 Spring 上下文可以启动。
 * 不连接任何数据库（骨架阶段尚未配置数据源）。
 */
@SpringBootTest
class MeperChatBiApplicationTests {

    @Test
    void contextLoads() {
    }
}
