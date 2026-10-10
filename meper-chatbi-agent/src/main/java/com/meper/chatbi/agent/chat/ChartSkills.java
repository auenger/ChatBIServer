package com.meper.chatbi.agent.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 内置图表技能目录；仅从固定资源读取，不访问用户路径或外部服务。 */
public final class ChartSkills {

    private static final Map<String, String> RESOURCES = Map.of(
            "trend", "/skills/charts/trend.md",
            "comparison", "/skills/charts/comparison.md",
            "proportion", "/skills/charts/proportion.md");

    private ChartSkills() {
    }

    public static String read(String name) {
        String path = RESOURCES.get(name == null ? "" : name.trim());
        if (path == null) {
            return "不支持的图表技能。可用技能：trend（趋势）、comparison（对比）、proportion（占比）。";
        }
        try (var stream = ChartSkills.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("内置图表技能资源缺失");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取内置图表技能失败", e);
        }
    }
}
