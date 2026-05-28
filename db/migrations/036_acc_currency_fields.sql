-- AccCurrenciesController symbol/decimal 真实字段（阶段 2-K）。
--
-- 对照 docs/acc-gap-report-for-claude-2026-05-28.md §4 / §5.7 + 旧 ACC `Currency.php`：
--   - 旧 Currency.Symbol  → finance_currency.symbol（货币符号，固有属性进币种表）
--   - 旧 Currency.Decimal → finance_currency.decimal_places
--   - 旧 Currency.Rate    → 从 exchange_rates 取当日对 CNY 汇率（不入币种表，动态查）

alter table finance_currency
  add column if not exists symbol text,
  add column if not exists decimal_places smallint not null default 2;

-- 预填常见货币符号 / 小数位（仅当为空时）
update finance_currency set symbol = '¥', decimal_places = 2 where code = 'CNY' and symbol is null;
update finance_currency set symbol = '$', decimal_places = 2 where code = 'USD' and symbol is null;
update finance_currency set symbol = '€', decimal_places = 2 where code = 'EUR' and symbol is null;
update finance_currency set symbol = '£', decimal_places = 2 where code = 'GBP' and symbol is null;
update finance_currency set symbol = 'HK$', decimal_places = 2 where code = 'HKD' and symbol is null;
update finance_currency set symbol = '¥', decimal_places = 0 where code = 'JPY' and symbol is null;
