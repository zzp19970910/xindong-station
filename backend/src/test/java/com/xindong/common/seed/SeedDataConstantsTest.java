package com.xindong.common.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.xindong.common.seed.SeedDataConstants.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Seed常量数据 - 默契题200道 + 恋爱清单30条 + 12种周报主题")
class SeedDataConstantsTest {

    @Test
    @DisplayName("Case1: 默契题 ≥ 200道，每题5段 (题干+4选项)，无空串")
    void quizQuestions_sizeAndContent() {
        assertTrue(QuizQuestions.QUESTION_COUNT >= 200,
                "架构方案要求≥200道预置题 实际=" + QuizQuestions.QUESTION_COUNT);
        for (int i = 0; i < QuizQuestions.QUESTION_COUNT; i++) {
            String[] q = QuizQuestions.RAW.get(i);
            assertEquals(5, q.length, "题号#" + i + "必须是5个元素(题目+4选项)");
            for (int k = 0; k < 5; k++) {
                assertNotNull(q[k], "#" + i + " 位置" + k + "为null");
                assertFalse(q[k].isEmpty(), "#" + i + " 位置" + k + "为空串");
            }
        }
    }

    @Test
    @DisplayName("Case2: 恋爱清单 = 30条预置（对应里程碑10/20/30空投节点）")
    void checklist_exactly30Preset() {
        assertEquals(30, ChecklistPreset.ITEM_COUNT,
                "清单必须30条，对应里程碑10/20/30三条空投触发节点");
        for (int i = 0; i < 30; i++) {
            assertEquals(i + 1, ChecklistPreset.sortAt(i), "第" + (i + 1) + "条的序号应正确递增 1→30");
            assertNotNull(ChecklistPreset.titleAt(i));
            assertTrue(ChecklistPreset.titleAt(i).length() >= 5,
                    "#" + i + " 标题长度太短");
        }
        assertEquals("一起认真规划三年后的共同目标", ChecklistPreset.titleAt(29), "第30条应是三年目标");
    }

    @Test
    @DisplayName("Case3: 周报主题=12种，第10/12周对应里程碑50/100/200空投")
    void weeklyTheme_12Themes_milestoneMentions() {
        assertEquals(12, WeeklyTheme.THEME_COUNT, "架构方案12个每周主题");
        assertTrue(WeeklyTheme.descriptionAt(4).contains("解锁第10条里程碑50金币空投"),
                "第4周清单周要提示第10条50金币空投");
        assertTrue(WeeklyTheme.descriptionAt(10).contains("第20条里程碑100金币空投"),
                "第10周里程碑周要提示第20条100金币空投");
        assertTrue(WeeklyTheme.descriptionAt(12).contains("里程碑200金币超级空投"),
                "第12周百日周要提示第30条200金币超级空投");
        assertEquals("萌芽周", WeeklyTheme.themeAt(1));
        assertEquals("百日周", WeeklyTheme.themeAt(12));
    }

    @Test
    @DisplayName("Case4: 心情枚举6种，index0保留，emoji与label不冲突")
    void moodTypes_sixKinds() {
        assertEquals(6, MoodTypes.EMOJI.length - 1);
        assertEquals(MoodTypes.HAPPY, 1);
        assertEquals("😊", MoodTypes.EMOJI[MoodTypes.HAPPY]);
        assertEquals("开心", MoodTypes.LABEL[MoodTypes.HAPPY]);
        assertEquals("疲惫", MoodTypes.LABEL[MoodTypes.TIRED]);
    }
}