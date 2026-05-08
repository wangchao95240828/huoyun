package com.xqt.saas.orders;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.CommandResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.common.PageResponse;
import com.xqt.saas.common.RequestContext;
import com.xqt.saas.orders.OrderResponses.OrderView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document/orders")
public class DocumentOrderController {
    private static final FlowDefinition FLOW = new FlowDefinition(
        "DOCUMENT_CUSTOMER",
        "DOCUMENT_SHIPPING",
        "MANUAL_DOCUMENT",
        "DO"
    );

    private final FlowOrderService service;
    private final RequestContext context;

    public DocumentOrderController(FlowOrderService service, RequestContext context) {
        this.service = service;
        this.context = context;
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('flow.document.read')")
    public ApiResponse<PageResponse<OrderView>> search(Authentication authentication, @RequestBody(required = false) OrderRequests.Search request) {
        return ApiResponse.ok(service.search(principal(authentication), FLOW, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('flow.document.read')")
    public ApiResponse<ItemResponse<OrderView>> get(Authentication authentication, @PathVariable("id") String orderId) {
        return ApiResponse.ok(service.get(principal(authentication), FLOW, orderId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('flow.document.write')")
    public ApiResponse<ItemResponse<OrderView>> create(Authentication authentication, @RequestBody OrderRequests.Save request) {
        return ApiResponse.ok(service.create(principal(authentication), FLOW, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('flow.document.write')")
    public ApiResponse<ItemResponse<OrderView>> update(
        Authentication authentication,
        @PathVariable("id") String orderId,
        @RequestBody OrderRequests.Save request
    ) {
        return ApiResponse.ok(service.update(principal(authentication), FLOW, orderId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('flow.document.write')")
    public ApiResponse<CommandResponse> delete(Authentication authentication, @PathVariable("id") String orderId) {
        return ApiResponse.ok(service.delete(principal(authentication), FLOW, orderId));
    }

    private AuthPrincipal principal(Authentication authentication) {
        return context.principal(authentication);
    }
}
