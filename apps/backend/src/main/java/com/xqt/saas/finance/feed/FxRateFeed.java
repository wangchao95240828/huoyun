package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 真实汇率源抽象：覆盖"表配置和快照"之上的取数层。
 *
 * 实现：
 *   - {@link OpenExchangeRateHostFeed}：用免费 exchangerate.host 公共 API（默认）
 *   - 生产推荐补：PBoC 官方接口、中国银行汇率、彭博/路透付费源
 *
 * 调用约定：返回的 Map 形如 {"USD": 7.25, "EUR": 7.89, ...}，
 * key 是 from_currency，base 是 toCurrency；找不到时返回空 Map。
 */
public interface FxRateFeed {

    /** Feed 标识。写入 exchange_rates.source 字段，方便审计。 */
    String code();

    /**
     * 拉取一组货币兑 base 的最新汇率。
     * @param baseCurrency 基准币种，如 "CNY"
     * @param currencies   要查询的源币种集合，如 ["USD", "EUR", "HKD"]
     * @param rateDate     报价日期（部分 feed 支持历史日期；不支持时忽略）
     * @return Map(from_currency -> rate)，找不到时返回空 Map
     */
    Map<String, BigDecimal> fetch(String baseCurrency,
                                   java.util.Collection<String> currencies,
                                   LocalDate rateDate);
}
