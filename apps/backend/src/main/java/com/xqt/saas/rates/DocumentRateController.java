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

    public DocumentRateController(RateEngine engine, RequestContext context) {
        this.engine = engine;
        this.context = context;
    }

    @PostMapping("/quote")
    @PreAuthorize("hasAuthority('flow.document.read')")
    public ApiResponse<ItemResponse<Quote>> quote(Authentication authentication,
                                                  @RequestBody RateQuoteRequest request) {
        AuthPrincipal principal = context.principal(authentication);
        return ApiResponse.ok(new ItemResponse<>(engine.quote(principal.tenantId(), request)));
    }
}
