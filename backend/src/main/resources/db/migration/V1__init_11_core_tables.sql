-- ==================================================================
-- V1__init_11_core_tables.sql  MySQL 原生版（严格对齐15个Entity @Table列名）
-- 表名修正（历史草稿错位纠正）：
--   wish_items  → wishes           (Wish Entity)
--   mood_records→ mood_checkins    (Mood Entity)
--   letters     → love_letters     (LoveLetter Entity)
-- 主键/类型：BIGINT PK AUTO_INCREMENT / TINYINT(1)布尔 / INT整数 / DATE / DATETIME
-- ==================================================================

-- 1. users（Users）
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(11) NOT NULL UNIQUE,
    nickname VARCHAR(20),
    avatar_url VARCHAR(100),
    couple_id BIGINT,
    partner_idx TINYINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_couple_id (couple_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. couples（Couple）含 @Version 乐观锁
CREATE TABLE IF NOT EXISTS couples (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    together_date DATE,
    invite_code_p1 VARCHAR(6) UNIQUE,
    invite_code_p2 VARCHAR(6) UNIQUE,
    coins_total INT NOT NULL DEFAULT 0,
    theme VARCHAR(20) NOT NULL DEFAULT 'default',
    cooling_until DATETIME,
    cooling_lock_until DATETIME,
    sign_streak INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_invite_p1 (invite_code_p1),
    INDEX idx_invite_p2 (invite_code_p2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. coin_logs（CoinLog）
CREATE TABLE IF NOT EXISTS coin_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    reason_label VARCHAR(50),
    delta INT NOT NULL,
    balance_after INT NOT NULL,
    from_user_id BIGINT,
    from_partner TINYINT,
    biz_id VARCHAR(100),
    date_str VARCHAR(10),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_couple_date (couple_id, date_str),
    INDEX idx_couple_created (couple_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. anniversaries（Anniversary）包含 created_by 本轮最初报错的列
CREATE TABLE IF NOT EXISTS anniversaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    title VARCHAR(30) NOT NULL,
    type VARCHAR(20) NOT NULL,
    emoji VARCHAR(8),
    target_date DATE NOT NULL,
    note VARCHAR(200),
    is_top TINYINT(1) DEFAULT 0,
    display_mode VARCHAR(15),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_couple_top (couple_id, is_top DESC, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. mood_checkins（Mood）纠正草稿表名mood_records→mood_checkins
CREATE TABLE IF NOT EXISTS mood_checkins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    partner_idx TINYINT NOT NULL,
    date_str VARCHAR(10) NOT NULL,
    mood_type INT NOT NULL,
    emoji VARCHAR(8),
    note VARCHAR(100),
    image_url VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_couple_partner_date (couple_id, partner_idx, date_str),
    INDEX idx_couple_date (couple_id, date_str DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. diaries（Diary）
CREATE TABLE IF NOT EXISTS diaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    partner_idx TINYINT,
    title VARCHAR(100),
    content TEXT NOT NULL,
    mood INT,
    image_urls VARCHAR(5000),
    image_count INT DEFAULT 0,
    weather VARCHAR(50),
    location VARCHAR(100),
    record_date DATE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_couple_record_date (couple_id, record_date DESC, created_at DESC),
    INDEX idx_couple_partner (couple_id, partner_idx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. diary_comments（DiaryComment）
CREATE TABLE IF NOT EXISTS diary_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    diary_id BIGINT NOT NULL,
    couple_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    partner_idx TINYINT,
    content VARCHAR(300) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_diary_created (diary_id, created_at ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. checklists（Checklist）
CREATE TABLE IF NOT EXISTS checklists (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT,
    is_preset TINYINT(1) DEFAULT 0,
    category VARCHAR(20),
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    icon VARCHAR(32),
    cover_url VARCHAR(500),
    sort_order INT DEFAULT 0,
    is_done TINYINT(1) DEFAULT 0,
    milestone_bonus INT,
    created_by BIGINT,
    completed_at DATE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_couple_category_done (couple_id, category, is_done, created_at DESC),
    INDEX idx_couple_preset (couple_id, is_preset DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. daily_quiz_answers（DailyQuizAnswer）
CREATE TABLE IF NOT EXISTS daily_quiz_answers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    date_str VARCHAR(10) NOT NULL,
    partner_idx TINYINT NOT NULL,
    user_id BIGINT NOT NULL,
    option_id INT,
    answer_content VARCHAR(500),
    match_percent INT,
    answered_at DATETIME,
    UNIQUE KEY uk_couple_q_date_partner (couple_id, question_id, date_str, partner_idx),
    INDEX idx_couple_date (couple_id, date_str DESC, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. wishes（Wish）纠正草稿表名wish_items→wishes
CREATE TABLE IF NOT EXISTS wishes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    title VARCHAR(50) NOT NULL,
    cost INT NOT NULL,
    cover_img VARCHAR(500),
    steps_json LONGTEXT,
    total_steps INT,
    completed_steps INT DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    reject_reason VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_couple_status_created (couple_id, status, created_at DESC),
    INDEX idx_created_by (created_by, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. icebreak_task_records（IcebreakTaskRecord）
CREATE TABLE IF NOT EXISTS icebreak_task_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_name VARCHAR(100),
    finished_by_id BIGINT,
    partner_idx TINYINT,
    reflection VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_couple_created (couple_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. icebreak_sessions（IcebreakSession）
CREATE TABLE IF NOT EXISTS icebreak_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    date_str VARCHAR(10) NOT NULL,
    spins_left INT DEFAULT 3,
    current_task_id BIGINT,
    current_task_name VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_couple_date (couple_id, date_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. tacit_games（TacitGame）
CREATE TABLE IF NOT EXISTS tacit_games (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    created_by_id BIGINT,
    question_ids_cipher VARCHAR(500),
    p1_answers LONGTEXT,
    p2_answers LONGTEXT,
    p1_partner_idx TINYINT,
    p2_partner_idx TINYINT,
    game_status TINYINT DEFAULT 0,
    match_percent INT,
    p1_finished_at DATETIME,
    p2_finished_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_couple_created (couple_id, created_at DESC),
    INDEX idx_couple_status (couple_id, game_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. love_letters（LoveLetter）纠正草稿表名letters→love_letters
CREATE TABLE IF NOT EXISTS love_letters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    partner_idx TINYINT,
    receiver_id BIGINT,
    title VARCHAR(100),
    content_cipher LONGTEXT NOT NULL,
    cover_url VARCHAR(500),
    is_time_capsule TINYINT(1) DEFAULT 0,
    scheduled_at DATETIME,
    read_at DATETIME,
    reply_to_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_couple_created (couple_id, created_at DESC),
    INDEX idx_couple_capsule (couple_id, is_time_capsule, scheduled_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. private_messages（PrivateMessage）
CREATE TABLE IF NOT EXISTS private_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    couple_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    partner_idx TINYINT,
    receiver_id BIGINT,
    content_type VARCHAR(10) NOT NULL DEFAULT 'text',
    content TEXT,
    image_url VARCHAR(500),
    emoji_code VARCHAR(32),
    is_read TINYINT(1) DEFAULT 0,
    is_recalled TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_couple_created (couple_id, created_at DESC),
    INDEX idx_couple_isread (couple_id, is_read, sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. mood_types（字典表，给V2 INSERT用）
CREATE TABLE IF NOT EXISTS mood_types (
    id INT PRIMARY KEY,
    code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(20) NOT NULL,
    emoji VARCHAR(8),
    color VARCHAR(10),
    sort_order INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. weekly_themes（字典表，给V2 INSERT用）
CREATE TABLE IF NOT EXISTS weekly_themes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    date_str VARCHAR(10) UNIQUE NOT NULL,
    theme_name VARCHAR(50) NOT NULL,
    theme_desc VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;