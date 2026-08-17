-- 🔴B5红线：禁止绕过 CoinService.addCoins() 直接修改 couples.coins_total
-- 触发场景：任何 UPDATE couples 且修改了 coins_total 的语句（含 QA 红线 /admin/coins/direct_modify 直改）
-- 只要 coins_total 变化且不是由正常业务流水驱动 → 立刻 SIGNAL 回滚，返回 50703 给上层转业务异常
DELIMITER $$
DROP TRIGGER IF EXISTS trg_block_illegal_coin_update $$
CREATE TRIGGER trg_block_illegal_coin_update
BEFORE UPDATE ON couples
FOR EACH ROW
BEGIN
    -- 只拦 coins_total 被直接修改的情况（其他字段比如 sign_streak/theme 正常更新放行）
    IF NEW.coins_total <> OLD.coins_total THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '50703:BLOCK_ILLEGAL_COIN_UPDATE: couples.coins_total 必须通过 CoinService.addCoins() 修改，禁止直改DB',
                MYSQL_ERRNO = 50703;
    END IF;
END $$
DELIMITER ;