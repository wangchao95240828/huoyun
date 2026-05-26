package com.xqt.saas.acc;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/stats — 旧 PHP 返回订单/客户/收入等汇总数。
 * 新平台暂未做对应聚合，先返回零值占位避免前端报错。
 */
@RestController
@RequestMapping("/api/acc/stats")
public class AccStatsController {

    @GetMapping
    public Map<String, Object> stats() {
        return Map.of(
            "orderCount", 0,
            "customerCount", 0,
            "shipmentCount", 0,
            "revenue", 0,
            "cost", 0,
            "profit", 0
        );
    }
}
