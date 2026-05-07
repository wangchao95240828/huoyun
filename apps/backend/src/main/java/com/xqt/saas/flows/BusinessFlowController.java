package com.xqt.saas.flows;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ListResponse;
import com.xqt.saas.common.RequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-flows")
public class BusinessFlowController {
    private final RequestContext context;
    private final BusinessFlowService service;

    public BusinessFlowController(RequestContext context, BusinessFlowService service) {
        this.context = context;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('business.flow.read','flow.seller.read','flow.document.read')")
    public ApiResponse<ListResponse<BusinessFlowView>> list(Authentication authentication) {
        AuthPrincipal auth = context.principal(authentication);
        return ApiResponse.ok(service.list(auth));
    }
}
