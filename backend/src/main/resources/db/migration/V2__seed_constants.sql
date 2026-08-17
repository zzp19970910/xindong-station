-- Flyway V2：Seed 常量数据初始化（MySQL 原生版，列名严格对齐V1）
-- 注：1. 心情类型字典6条 + 每周主题12条（主库启动由Flyway自动执行）
--     2. 200道默契题+30条清单预置数据量极大，生产部署用Java代码批量BATCH INSERT
--        实现位置：com.xindong.common.seed.SeedDataConstants.java
--        启动触发类（可选）：@PostConstruct 读取 SeedDataConstants BATCH 插入（推荐）
-- =========================================

-- V2__seed_constants.sql：只执行字典类小数据，200题库由Java BATCH写入

-- 6种心情字典（列名对齐V1 mood_types: code/name/emoji/color/sort_order）
INSERT IGNORE INTO mood_types(id, code, name, emoji, color, sort_order) VALUES
(1, 'HAPPY',    '开心', '😊', '#FFD93D', 1),
(2, 'CALM',     '平静', '😌', '#6BCB77', 2),
(3, 'EXCITED',  '兴奋', '🤩', '#FF6B6B', 3),
(4, 'SAD',      '难过', '🥺', '#4D96FF', 4),
(5, 'ANGRY',    '生气', '😤', '#FF6B6B', 5),
(6, 'TIRED',    '疲惫', '😴', '#AAAAAA', 6);

-- 12周主题字典（列名对齐V1 weekly_themes: date_str/theme_name/theme_desc）
-- 用空占位date_str(YYYY-MM-DD周一开始占位，SeedRunner启动时会按情侣together_date动态覆盖填充)
INSERT IGNORE INTO weekly_themes(date_str, theme_name, theme_desc, created_at) VALUES
('0000-01-01', '萌芽周',    '两颗心靠近的第1周，解锁每日登录+心情打卡两个小习惯吧～',            CURRENT_TIMESTAMP),
('0000-01-08', '甜周',      '第二周甜蜜翻倍，试着写一封情书，记录心动瞬间',                      CURRENT_TIMESTAMP),
('0000-01-15', '默契周',    '第三周来挑战默契答题，看看你们是不是天生一对',                        CURRENT_TIMESTAMP),
('0000-01-22', '清单周',    '第四周一起勾选恋爱清单，解锁第10条里程碑50金币空投',                  CURRENT_TIMESTAMP),
('0000-01-29', '回忆周',    '第五周整理你们的合照和聊天记录，做一个时光相册',                      CURRENT_TIMESTAMP),
('0000-02-05', '出游周',    '第六周安排一个短途旅行，破冰大转盘转起来',                            CURRENT_TIMESTAMP),
('0000-02-12', '仪式周',    '第七周给对方准备一个小小的惊喜仪式，无关价格',                        CURRENT_TIMESTAMP),
('0000-02-19', '沟通周',    '第八周每晚聊30分钟，聊童年聊梦想，更懂彼此',                          CURRENT_TIMESTAMP),
('0000-02-26', '梦想周',    '第九周一起写下5个共同目标，慢慢实现它们',                              CURRENT_TIMESTAMP),
('0000-03-05', '里程碑周',  '第十周！清单第20条里程碑100金币空投已开启，冲！',                     CURRENT_TIMESTAMP),
('0000-03-12', '温馨周',    '第十一周试着一起做饭+收拾家，模拟婚后的日常',                         CURRENT_TIMESTAMP),
('0000-03-19', '百日周',    '第十二周！恋爱100天纪念周，解锁第30条里程碑200金币超级空投～',        CURRENT_TIMESTAMP);