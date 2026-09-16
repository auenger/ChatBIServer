package com.meper.chatbi.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 存活探针：验证 Web 层装配可用。
 *
 * <p>正式业务 API 按《架构方案》§4 统一操作管线另行设计；
 * 健康检查（actuator）在 start 模块。
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "meper-chatbi-server",
                "time", OffsetDateTime.now().toString()
        );
    }
}
