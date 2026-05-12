package com.xqt.saas.customerapi;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceList;
import com.xqt.saas.customerapi.CustomerApiResponses.PreOrderResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-api")
public class CustomerApiController {
    private final CustomerApiService service;

    public CustomerApiController(CustomerApiService service) {
        this.service = service;
    }

    @GetMapping("/balance")
    public ApiResponse<ItemResponse<BalanceList>> balance() {
        return ApiResponse.ok(new ItemResponse<>(service.queryBalance(principal())));
    }

    @PostMapping("/orders")
    public ApiResponse<ItemResponse<PreOrderResult>> preOrder(@RequestBody CustomerApiRequests.PreOrder body) {
        return ApiResponse.ok(new ItemResponse<>(service.preOrder(principal(), body)));
    }

    @GetMapping("/ping")
    public ApiResponse<ItemResponse<String>> ping() {
        return ApiResponse.ok(new ItemResponse<>(principal().customerCode()));
    }

    private CustomerApiPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerApiPrincipal customer)) {
            throw ApiException.unauthorized("customer-api context not initialized");
        }
        return customer;
    }
}
