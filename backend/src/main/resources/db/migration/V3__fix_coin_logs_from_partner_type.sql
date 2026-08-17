-- ==================================================================
-- V3__fix_coin_logs_from_partner_type.sql
-- 修复 050 轮 SchemaValidation 类型不匹配：
--   CoinLog Entity 的 fromPartner 字段是 Long（→BIGINT），但 V1 里错写成了 TINYINT
--   二次全扫确认：15 张 Entity 所有列类型仅此 1 处错位，其余全对齐
-- ==================================================================

ALTER TABLE coin_logs MODIFY COLUMN from_partner BIGINT;