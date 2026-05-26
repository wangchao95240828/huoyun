package com.xqt.saas.labels;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.customerapi.CustomerApiPrincipal;
import com.xqt.saas.labels.LabelResponses.LabelBatch;
import com.xqt.saas.labels.LabelResponses.RelabelResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 把 ACC act=Label 和 api/getNewLabel.php 暴露为 customer-api 子路径。
 * 走 CustomerApiAuthFilter 自动鉴权，路径必须在 /api/customer-api/** 下。
 */
@RestController
@RequestMapping("/api/customer-api/labels")
public class LabelController {
    private final LabelService service;

    public LabelController(LabelService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ApiResponse<ItemResponse<LabelBatch>> generate(@RequestBody LabelRequests.GenerateLabel body) {
        return ApiResponse.ok(new ItemResponse<>(service.generate(principal(), body)));
    }

    @PostMapping("/relabel")
    public ApiResponse<ItemResponse<RelabelResult>> relabel(@RequestBody LabelRequests.RelabelPdf body) {
        return ApiResponse.ok(new ItemResponse<>(service.relabel(principal(), body)));
    }

    private CustomerApiPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomerApiPrincipal customer)) {
            throw ApiException.unauthorized("customer-api context not initialized");
        }
        return customer;
    }
}
