package com.xqt.saas.customerapi;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.customerapi.CustomerApiResponses.ChannelInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复刻 ACC acc/api/sumy.php 第三方推单入口。
 *
 * 走 customer-api 签名鉴权（CustomerApiPrincipal 由签名过滤器注入）。
 * 路径对应文档建议：/api/customer-api/external-orders/sumy
 *
 *   POST /api/customer-api/external-orders/sumy        → act=push 批量推单
 *   GET  /api/customer-api/external-orders/sumy/channels → act=channel 列可用渠道
 *
 * 响应保持 sumy 兼容格式（PascalCase），方便旧第三方客户端最小改造接入。
 */
@RestController
@RequestMapping("/api/customer-api/external-orders/sumy")
public class SumyController {
    private final SumyService sumyService;
    private final CustomerApiService customerApiService;

    public SumyController(SumyService sumyService, CustomerApiService customerApiService) {
        this.sumyService = sumyService;
        this.customerApiService = customerApiService;
    }

    @PostMapping
    public Map<String, Object> push(@RequestBody Map<String, Object> body) {
        return sumyService.push(principal(), body);
    }

    /** 对应 sumy act=channel：列出客户可用渠道 [{ Name, Code, Logistics }]。 */
    @GetMapping("/channels")
    public List<Map<String, Object>> channels() {
        return customerApiService.listChannels(principal()).data().stream()
            .map(this::toSumyChannel)
            .collect(Collectors.toList());
    }

    private Map<String, Object> toSumyChannel(ChannelInfo c) {
        return Map.of(
            "Name", c.name() == null ? "" : c.name(),
            "Code", c.code() == null ? "" : c.code(),
            "Logistics", c.lane() == null ? "" : c.lane());
    }

    private CustomerApiPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || !(authentication.getPrincipal() instanceof CustomerApiPrincipal customer)) {
            throw ApiException.unauthorized("customer-api context not initialized");
        }
        return customer;
    }
}
