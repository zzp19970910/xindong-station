-- 🔴B7红线兜底：并发2个wish兑换（couple=108，每个price=600，余额1000 → 只能成功1个 另一个20701余额不足）
-- wish_id=601 和 602 归属 couple_id=108 (Token A/B所在情侣)，状态必须是 PENDING_APPROVAL 才能触发 redeemDirect 余额拦截
INSERT IGNORE INTO wishes(id, couple_id, title, cost, cover_img, created_by, status, steps_json, total_steps, completed_steps, created_at, updated_at)
VALUES (601, 108, '[红线B7]并发兑换愿望1(600币)', 600, 'redline_b7_1.png', 201, 'PENDING_APPROVAL',
        '[{"name":"执行兑现","done":false}]', 1, 0, NOW(), NOW());

INSERT IGNORE INTO wishes(id, couple_id, title, cost, cover_img, created_by, status, steps_json, total_steps, completed_steps, created_at, updated_at)
VALUES (602, 108, '[红线B7]并发兑换愿望2(600币)', 600, 'redline_b7_2.png', 201, 'PENDING_APPROVAL',
        '[{"name":"执行兑现","done":false}]', 1, 0, NOW(), NOW());

-- 防止之前被SeedRunner插成错误的APPROVED状态 → 强制改回PENDING_APPROVAL（避免跳过余额拦截）
UPDATE wishes SET status='PENDING_APPROVAL', updated_at=NOW() WHERE id IN (601, 602) AND status='APPROVED';