package com.xqt.saas.stowage;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.customerapi.CustomerApiPrincipal;
import com.xqt.saas.stowage.StowageResponses.SyncResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对应 ACC api/APIClass.php?act=Sync。在 customer-api 前缀下，鉴权由 CustomerApiAuthFilter 自动覆盖。 */
@RestController
@RequestMapping("/api/customer-api/stowages")
public class StowageController {
    private final StowageService service;

    public StowageController(StowageService service) {
        this.service = service;
    }

    @PostMapping("/sync")
    public ApiResponse<ItemResponse<SyncResult>> sync(@RequestBody StowageRequests.SyncRequest body) {
        return ApiResponse.ok(new ItemResponse<>(service.sync(principal(), body)));
    }

    private CustomerApiPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomerApiPrincipal customer)) {
            throw ApiException.unauthorized("customer-api context not initialized");
        }
        return customer;
    }
}
