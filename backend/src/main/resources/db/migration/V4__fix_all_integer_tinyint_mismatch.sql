-- ==================================================================
-- V4__fix_all_integer_tinyint_mismatch.sql
-- 修正 051 轮判断错误：Hibernate 6 Integer 字段期望 INT，不是 TINYINT
-- 15 Entity 全量扫：所有 Integer 字段但 V1 错写 TINYINT 的 11 处，一次改完
-- 保持 TINYINT(1) 正确的列：所有 isXxx Boolean 字段（isTop/isPreset/isDone/isTimeCapsule/isRead/isRecalled 等）→ 不动
-- ==================================================================

ALTER TABLE users                        MODIFY COLUMN partner_idx     INT;
ALTER TABLE mood_checkins                MODIFY COLUMN partner_idx     INT;
ALTER TABLE diaries                      MODIFY COLUMN partner_idx     INT;
ALTER TABLE diary_comments               MODIFY COLUMN partner_idx     INT;
ALTER TABLE daily_quiz_answers           MODIFY COLUMN partner_idx     INT;   -- 本轮当前报错列
ALTER TABLE love_letters                 MODIFY COLUMN partner_idx     INT;
ALTER TABLE icebreak_task_records        MODIFY COLUMN partner_idx     INT;
ALTER TABLE tacit_games                  MODIFY COLUMN p1_partner_idx  INT;
ALTER TABLE tacit_games                  MODIFY COLUMN p2_partner_idx  INT;
ALTER TABLE tacit_games                  MODIFY COLUMN game_status     INT;
ALTER TABLE private_messages             MODIFY COLUMN partner_idx     INT;