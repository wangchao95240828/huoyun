package com.xqt.saas.customerapi;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.customerapi.CustomerApiResponses.BalanceList;
import com.xqt.saas.customerapi.CustomerApiResponses.CancelResult;
import com.xqt.saas.customerapi.CustomerApiResponses.ChannelList;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDetail;
import com.xqt.saas.customerapi.CustomerApiResponses.OrderDetailList;
import com.xqt.saas.customerapi.CustomerApiResponses.PreOrderResult;
import com.xqt.saas.customerapi.CustomerApiResponses.StatusList;
import com.xqt.saas.customerapi.CustomerApiResponses.SubmitResult;
import com.xqt.saas.customerapi.CustomerApiResponses.TrackingList;
import com.xqt.saas.rates.RateEngine;
import com.xqt.saas.rates.RateQuoteRequest;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-api")
public class CustomerApiController {
    private final CustomerApiService service;
    private final RateEngine rateEngine;
    private final com.xqt.saas.tracking.TrackingAggregator trackingAggregator;

    public CustomerApiController(CustomerApiService service, RateEngine rateEngine,
                                  com.xqt.saas.tracking.TrackingAggregator trackingAggregator) {
        this.service = service;
        this.rateEngine = rateEngine;
        this.trackingAggregator = trackingAggregator;
    }

    @GetMapping("/balance")
    public ApiResponse<ItemResponse<BalanceList>> balance() {
        return ApiResponse.ok(new ItemResponse<>(service.queryBalance(principal())));
    }

    @PostMapping("/orders")
    public ApiResponse<ItemResponse<PreOrderResult>> preOrder(@RequestBody CustomerApiRequests.PreOrder body) {
        return ApiResponse.ok(new ItemResponse<>(service.preOrder(principal(), body)));
    }

    @PutMapping("/orders/{no}")
    public ApiResponse<ItemResponse<OrderDetail>> modifyOrder(@PathVariable("no") String no,
                                                              @RequestBody CustomerApiRequests.ModifyOrder body) {
        return ApiResponse.ok(new ItemResponse<>(service.modifyOrder(principal(), no, body)));
    }

    @PostMapping("/orders/{no}/pre-submit")
    public ApiResponse<ItemResponse<com.xqt.saas.customerapi.CustomerApiResponses.PreSubmitResult>>
            preSubmitOrder(@PathVariable("no") String no) {
        return ApiResponse.ok(new ItemResponse<>(service.preSubmitOrder(principal(), no)));
    }

    @PostMapping("/orders/{no}/submit")
    public ApiResponse<ItemResponse<SubmitResult>> submitOrder(@PathVariable("no") String no) {
        return ApiResponse.ok(new ItemResponse<>(service.submitOrder(principal(), no)));
    }

    @PostMapping("/orders/{no}/cancel")
    public ApiResponse<ItemResponse<CancelResult>> cancelOrder(@PathVariable("no") String no) {
        return ApiResponse.ok(new ItemResponse<>(service.cancelOrder(principal(), no)));
    }

    @PostMapping("/rates/quote")
    public ApiResponse<ItemResponse<Quote>> quote(@RequestBody RateQuoteRequest body) {
        Quote quote = rateEngine.quote(principal().tenantId(), body);
        // blockers 是硬性拒绝（电池禁运 / 限重超 / 渠道账号超量等），报价接口必须明确失败，
        // 不能把不可下单的报价静默返回给客户。
        if (quote.blockers() != null && !quote.blockers().isEmpty()) {
            throw com.xqt.saas.common.ApiException.badRequest(
                "无法报价：" + String.join("；", quote.blockers()));
        }
        return ApiResponse.ok(new ItemResponse<>(quote));
    }

    @PostMapping("/orders/status")
    public ApiResponse<ItemResponse<StatusList>> orderStatus(@RequestBody CustomerApiRequests.OrderRefList body) {
        return ApiResponse.ok(new ItemResponse<>(service.queryStatus(principal(), body)));
    }

    @PostMapping("/orders/query")
    public ApiResponse<ItemResponse<OrderDetailList>> orderQuery(@RequestBody CustomerApiRequests.OrderRefList body) {
        return ApiResponse.ok(new ItemResponse<>(service.queryDetail(principal(), body)));
    }

    @GetMapping("/channels")
    public ApiResponse<ItemResponse<ChannelList>> channels() {
        return ApiResponse.ok(new ItemResponse<>(service.listChannels(principal())));
    }

    @PostMapping("/tracking/query")
    public ApiResponse<ItemResponse<TrackingList>> trackingQuery(@RequestBody CustomerApiRequests.OrderRefList body) {
        return ApiResponse.ok(new ItemResponse<>(service.queryTracking(principal(), body)));
    }

    /**
     * 客户视角时间线（任务 S1）：按子单号查多源轨迹，剥 operator/internal remark。
     * 对应 ACC 客户端按单号查 Express_Process 综合状态流。
     */
    @GetMapping("/tracking/{trackingNo}/timeline")
    public ApiResponse<java.util.Map<String, Object>> trackingTimeline(@PathVariable String trackingNo) {
        var events = trackingAggregator.aggregateByTrackingNo(principal().tenantId(), trackingNo);
        return ApiResponse.ok(java.util.Map.of(
            "trackingNo", trackingNo,
            "events", trackingAggregator.toPublic(events)
        ));
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
