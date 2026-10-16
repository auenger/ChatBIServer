package com.meper.chatbi.agent.gateway;
import com.meper.chatbi.agent.chat.ChartSkills;
import org.springframework.ai.tool.annotation.Tool;
/** 图表技能不会提供数据库或写入能力。 */
public class ChartSkillTools {
 private final ScopedToolSupport support;
 public ChartSkillTools(ScopedToolSupport support){this.support=support;}
 @Tool(description="读取图表规范trend/comparison/proportion，只根据真实查询/合并结果画图。")
 public String get_chart_skill(String skill){return support.invoke("get_chart_skill("+skill+")",()->ChartSkills.read(skill));}
}
