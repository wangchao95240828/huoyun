package com.xqt.saas.customerapi;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 默认占位渠道适配器:
 *   - 默认 allowFake=false → 提交时立刻报错, 让用户知道这渠道还没接真 API
 *   - dev/demo 模式 (app.carrier.allow-noop-fake=true) 时生成假 tracking 跑通流程
 *
 * 之前: 一律生成 NOOP-XXX 假 tracking → 客户下完单不知道是假的, 下载面单才发现
 * 现在: 提交时直接报错 "渠道账号未真接入, 请配 UPS/FedEx API key"
 */
@Component
public class NoopCarrierGateway implements CarrierGateway {
    private static final AtomicLong COUNTER = new AtomicLong();

    @Value("${app.carrier.allow-noop-fake:false}")
    private boolean allowFake;

    @Override
    public String gatewayKey() { return "NOOP"; }

    @Override
    public Issuance submit(SubmitContext ctx) {
        if (!allowFake) {
            throw com.xqt.saas.common.ApiException.badRequest(
                "❌ 提交失败: 渠道账号未真接入承运商 API (provider_code=NOOP). "
                + "请到 [基础信息 → 渠道账号] 找该渠道账号, 改 provider_code = UPS / FEDEX / KARRIO, "
                + "并填 api_key + api_secret. "
                + "当前真实可用账号: J602B0 (UPS-GROUND-US, UPS 真发).");
        }
        long seq = COUNTER.incrementAndGet();
        String suffix = ctx.orderNo() + "-" + String.format("%06d", seq % 1_000_000);
        String tracking = "NOOP-" + suffix;
        String master = "NOOP-M-" + String.format("%010d", seq);
        return new Issuance(tracking, master, Map.of(
            "provider", "NOOP",
            "submittedAt", java.time.Instant.now().toString()
        ));
    }
}
