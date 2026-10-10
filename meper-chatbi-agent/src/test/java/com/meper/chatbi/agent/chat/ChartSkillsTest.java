package com.meper.chatbi.agent.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartSkillsTest {

    @Test
    void builtInSkillsProvideChartFormatAndRequireRealQueryData() {
        for (String name : new String[]{"trend", "comparison", "proportion"}) {
            String skill = ChartSkills.read(name);
            assertTrue(skill.contains("```GPT-Vis"));
            assertTrue(skill.contains("实际回答必须替换为查询结果"));
            assertTrue(skill.contains("空结果或查询失败时不生成图表"));
        }
        assertTrue(ChartSkills.read("proportion").contains("不得称为全量占比"));
    }

    @Test
    void unregisteredNamesCannotReadArbitraryResources() {
        for (String name : new String[]{null, "", "unknown", "../../application.yml", "https://example.com"}) {
            assertTrue(ChartSkills.read(name).startsWith("不支持的图表技能"));
        }
    }
}
