-- 🔴B7红线：wish_orders 兑换订单表（三操作同事务第3步：扣币后必须写订单防并发）
-- 唯一索引 uk_wish_couple 防止同一情侣重复兑换同一愿望
CREATE TABLE IF NOT EXISTS wish_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wish_id BIGINT NOT NULL COMMENT '愿望ID',
    couple_id BIGINT NOT NULL COMMENT '情侣组ID',
    created_by BIGINT NULL COMMENT '愿望创建人(申请人)',
    approver_id BIGINT NULL COMMENT '审批人(扣币执行人)',
    cost INT NOT NULL DEFAULT 0 COMMENT '兑换扣币值快照',
    title_snap VARCHAR(500) NULL COMMENT '愿望标题快照(便于对账)',
    created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wish_couple (wish_id, couple_id),
    KEY idx_couple_id (couple_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='愿望兑换订单(红线B7三操作同事务必写)';