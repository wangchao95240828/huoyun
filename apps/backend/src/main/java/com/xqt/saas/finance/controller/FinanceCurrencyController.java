package com.xqt.saas.finance.controller;

import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinanceCurrencyExchangeRateHistorySaveRequest;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.service.FinanceCurrencyExchangeRateHistoryService;
import com.xqt.saas.finance.service.FinanceCurrencyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance-currencies")
public class FinanceCurrencyController {
    @Resource
    public FinanceCurrencyService currencyService;
    @Resource
    public FinanceCurrencyExchangeRateHistoryService historyService;

    // 添加货币
    @PostMapping("/save")
    public R save(@RequestBody FinanceCurrencySaveRequest req) {
        req = new FinanceCurrencySaveRequest(UserContext.getUserId(), req.code(), req.name());
        return R.success("保存成功", currencyService.save(req));
    }

    // 删除货币
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        currencyService.delete(id);
        return R.success("删除成功");
    }

    // 添加某个货币的汇率历史
    @PostMapping("/{id}/exchange-rate")
    public R saveHistory(@PathVariable Long id, @RequestBody FinanceCurrencyExchangeRateHistorySaveRequest req) {
        req = new FinanceCurrencyExchangeRateHistorySaveRequest(UserContext.getUserId(), req.applicationScenario(), req.rate(), req.effectiveFrom());
        return R.success("保存成功", historyService.save(id, req));
    }

    // 删除某个货币的汇率历史
    @DeleteMapping("/{currencyId}/exchange-rate/{exchangeRateId}")
    public R deleteHistory(@PathVariable Long currencyId, @PathVariable Long exchangeRateId) {
        historyService.delete(currencyId, exchangeRateId);
        return R.success("删除成功");
    }

    // 获取某个货币的汇率历史
    @GetMapping("/{id}/page")
    public R getHistory(@PathVariable Long id, @RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        var histories = historyService.page(id, pageNum, pageSize);
        return R.success("查询成功", histories);
    }

    // 分页获取每个货币的应收/应付汇率与其生效时间
    @GetMapping("/page")
    public R page(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", currencyService.pageCurrencyWithExchangeRate(pageNum, pageSize));
    }
}
