package com.xqt.saas.publictracking;

import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.publictracking.PublicTrackingResponses.TrackingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 Track 端点：对应 acc/api/Track.php。
 *
 * 安全语义和旧 ACC 一致 —— 单号即凭证，没有签名或 token；不在 /api/customer-api/** 前缀，
 * 因此 {@link com.xqt.saas.customerapi.CustomerApiAuthFilter} 自动跳过。
 */
@RestController
@RequestMapping("/api/public/tracking")
public class PublicTrackingController {
    private final PublicTrackingService service;

    public PublicTrackingController(PublicTrackingService service) {
        this.service = service;
    }

    @PostMapping("/query")
    public ApiResponse<ItemResponse<TrackingResult>> query(@RequestBody TrackRequest body) {
        String trackNo = body == null ? null : body.trackNo();
        return ApiResponse.ok(new ItemResponse<>(service.query(trackNo)));
    }

    public record TrackRequest(String trackNo) {
    }
}
