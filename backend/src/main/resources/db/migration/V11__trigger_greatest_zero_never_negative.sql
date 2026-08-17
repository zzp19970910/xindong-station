-- 🔴🔴🔴 B2/B3/B7红线保险：任何情况下 coins_total 绝对不能变负数！
--  就算上层Java代码被绕过、变量算错、并发击穿：触发器层用 GREATEST(NEW.coins_total, 0) 硬钳位≥0
DELIMITER $$
DROP TRIGGER IF EXISTS trg_block_illegal_coin_update $$
CREATE TRIGGER trg_block_illegal_coin_update
BEFORE UPDATE ON couples
FOR EACH ROW
BEGIN
    IF NEW.coins_total <> OLD.coins_total THEN
        -- ★ 先硬钳位：绝对不允许负数写入
        IF NEW.coins_total < 0 THEN
            SET NEW.coins_total = 0;
        END IF;
        IF @TRG_ALLOW_COIN_UPDATE = 1 THEN
            -- ✅ 合法：CoinService 设置了放行标记 → 立刻清零，避免同一连接后续误放
            SET @TRG_ALLOW_COIN_UPDATE = NULL;
        ELSE
            -- ❌ 非法：绕过Service直改DB → 回滚
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '50703:BLOCK_ILLEGAL_COIN_UPDATE: couples.coins_total 必须通过 CoinService.addCoins() 修改，禁止直改DB',
                    MYSQL_ERRNO = 50703;
        END IF;
    END IF;
END $$
DELIMITER ;