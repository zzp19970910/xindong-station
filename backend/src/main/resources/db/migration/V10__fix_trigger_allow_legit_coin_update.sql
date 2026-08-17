-- 🔧 修复：B5红线触发器把 CoinService.addCoins 合法更新也误拦了
-- 方案：引入 session 级变量 @TRG_ALLOW_COIN_UPDATE
--   1) CoinService.addCoins 在 UPDATE couples 前先 SET @TRG_ALLOW_COIN_UPDATE=1 → 触发器放行
--   2) 任何绕过Service的直改DB（jdbcTemplate/B5红线）不会设置此变量 → 触发器拦 50703
--   3) 放行后触发器立刻把变量置 NULL（避免同一连接后续误放过），session级不会并发串号
DELIMITER $$
DROP TRIGGER IF EXISTS trg_block_illegal_coin_update $$
CREATE TRIGGER trg_block_illegal_coin_update
BEFORE UPDATE ON couples
FOR EACH ROW
BEGIN
    IF NEW.coins_total <> OLD.coins_total THEN
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