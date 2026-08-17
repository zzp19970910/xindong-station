-- 🔴B6/B7红线兜底：确保 wish_id=888 存在（couple=909 price=666 PENDING_APPROVAL）
-- 不管 SeedRunner 有没有跳过初始化（COUNT(*)!=0 直接跳），Flyway 重启必跑，保证红线种子万无一失。
-- 注意：status 必须是 PENDING_APPROVAL（不是 APPROVED），不然 approve 接口会跳过余额事前拦截。
INSERT IGNORE INTO wishes(id, couple_id, title, cost, cover_img, created_by, status, steps_json, total_steps, completed_steps, created_at, updated_at)
VALUES (888, 909, '[红线B6B7]穷情侣兑换贵愿望测试', 666, 'redline_wish.png', 20101, 'PENDING_APPROVAL',
        '[{"name":"执行兑换","done":false}]', 1, 0, NOW(), NOW());

-- 如果已经存在 wish 888 但被之前的种子插成了错误的 APPROVED 状态 → 强制改回 PENDING_APPROVAL（避免 B6 不进余额拦截）
UPDATE wishes SET status='PENDING_APPROVAL', updated_at=NOW() WHERE id=888 AND status='APPROVED';