package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 渠道适配器抽象。
 *
 * ACC 旧逻辑里 Submit 通过 `inc/online/<Code>.php` 插件向各家渠道（UPS/FedEx/EDI/自营）取号。
 * 新平台把"渠道取号"抽象为单一接口，业务层只负责状态机和数据落地，具体渠道走独立实现。
 *
 * 当前唯一实现是 {@link NoopCarrierGateway}，生成占位子单号，使 Submit 端到端可跑通。
 * 真实渠道实现按渠道类型新建 bean，比如 UpsRestCarrierGateway，由配置或 channels.metadata 路由。
 */
public interface CarrierGateway {
    /** 注册中心索引键。例如 "UPS_DEMO"、"FEDEX_DEMO"、"NOOP"。 */
    String gatewayKey();

    Issuance submit(SubmitContext ctx);

    /**
     * 任务 S2：取消 provider 那侧的子单号（补偿用）。
     * 用于 DB 后续 insert 失败时主动作废 provider 已发出的单号，避免幽灵单号。
     *
     * 默认实现 no-op，仅返回 false 不抛错；真实 adapter 应实现真正的 cancel HTTP 调用。
     *
     * @return true = 成功取消；false = 失败或不支持（调用方记录 ORPHAN）
     */
    default boolean cancel(String tenantId, String masterTrackingNo) {
        return false;
    }

    record SubmitContext(
        String tenantId,
        String customerCode,
        String orderNo,
        String customerRef,
        String channelCode,
        String country,
        BigDecimal weightKg,
        Integer piece,
        Map<String, Object> receiver
    ) {
    }

    record Issuance(
        String carrierTrackingNo,
        String carrierMasterTrackingNo,
        Map<String, Object> raw
    ) {
    }
}
