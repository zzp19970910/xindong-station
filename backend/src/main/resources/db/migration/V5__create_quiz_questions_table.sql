-- ==================================================================
-- V5__create_quiz_questions_table.sql
-- 053 轮补 SeedRunner JdbcTemplate 直接操作的非 @Entity 字典表
-- 对应 SeedRunner.seedQuizQuestions() 第37行 INSERT 列：content, option_a, option_b, option_c, option_d, is_preset
-- ==================================================================

CREATE TABLE IF NOT EXISTS quiz_questions (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    content     VARCHAR(500) NOT NULL COMMENT '题干',
    option_a    VARCHAR(200) NOT NULL COMMENT '选项A',
    option_b    VARCHAR(200) NOT NULL COMMENT '选项B',
    option_c    VARCHAR(200)          COMMENT '选项C(可空)',
    option_d    VARCHAR(200)          COMMENT '选项D(可空)',
    is_preset   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=系统预置 0=自定义',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_quiz_questions_preset (is_preset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='默契测验题库字典表(非Entity纯JdbcTemplate操作)';