package com.xqt.saas.labels;

import java.util.List;
import java.util.Map;

/**
 * 面单生成网关抽象。
 *
 * 对应 ACC 旧逻辑里 `Channel_Account.Code` 决定的 `inc/online/<Code>.php` 插件的 doPrint('Label') 行为：
 *   - PluginList[Code]->doPrint($Args, 'Label')
 *   - Plugin->hasPrint == true  → 返回 PDF（$Plugin->pdf->Output() 或 returnValue）
 *   - Plugin->hasPrint == false → 返回多个 ZPL 字符串数组，由 doLabel 打成 zip
 *   - 同时返回 Plugin->TrackNo（主单号）和 Plugin->TrackNoList[Key]（子单号数组）
 *
 * 新平台抽出单一接口，业务层只看 {@link LabelArtifact}，渠道实现自己决定 PDF/ZPL 与 ACC 老插件等价。
 */
public interface LabelGateway {
    LabelArtifact print(PrintContext ctx);

    /** 渠道取面单的上下文。 */
    record PrintContext(
        String tenantId,
        String customerCode,
        String shipmentId,
        String shipmentNo,
        String customerRef,
        String channelCode,
        String countryCode,
        String requestedType,   // 客户请求的 Type=PDF/ZPL
        Map<String, Object> extra
    ) {
    }

    /** 渠道返回的面单产物，包含主单号、子单号、二进制和真实类型。 */
    record LabelArtifact(
        byte[] content,
        String labelType,             // 实际产物类型：PDF / ZPL / ZIP
        String fileExt,               // pdf / zpl / zip
        String mainTrackingNo,        // 渠道返回的主单号；可能与 ACC Plugin->TrackNo 一致
        List<String> subTrackingNos,  // 渠道返回的子单号；可能与 ACC Plugin->TrackNoList[Key] 一致
        Map<String, Object> raw       // 原始响应，便于审计
    ) {
    }
}
