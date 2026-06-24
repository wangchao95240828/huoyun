package com.xqt.saas.rates;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.common.RequestContext;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document/rates")
public class DocumentRateController {
    private final RateEngine engine;
    private final RequestContext context;
    private final RateQuoteSnapshotService snapshotService;

    public DocumentRateController(RateEngine engine, RequestContext context,
                                  RateQuoteSnapshotService snapshotService) {
        this.engine = engine;
        this.context = context;
        this.snapshotService = snapshotService;
    }

    @PostMapping("/quote")
    @PreAuthorize("hasAuthority('flow.document.read')")
    public ApiResponse<ItemResponse<Quote>> quote(Authentication authentication,
                                                  @RequestBody RateQuoteRequest request) {
        AuthPrincipal principal = context.principal(authentication);
        Quote quote = engine.quote(principal.tenantId(), request);
        // W3: 落 quote 快照 (不阻断主流程, 失败静默)
        snapshotService.snapshot(principal.tenantId(),
            request.customerId(), null, request, quote);
        return ApiResponse.ok(new ItemResponse<>(quote));
    }

    /**
     * W3 rerate: 用同 request 重算, 旧 quote 标 RE_RATED 链到新 quote.
     * 客户使用场景: 报价 24h 后下单, 发现 fuel 调了, 重报最新价.
     */
    @PostMapping("/quotes/{id}/rerate")
    @PreAuthorize("hasAuthority('flow.document.read')")
    public ApiResponse<ItemResponse<Quote>> rerate(Authentication authentication,
                                                    @org.springframework.web.bind.annotation.PathVariable String id) {
        AuthPrincipal principal = context.principal(authentication);
        Quote quote = snapshotService.rerate(principal.tenantId(), id);
        return ApiResponse.ok(new ItemResponse<>(quote));
    }
}
