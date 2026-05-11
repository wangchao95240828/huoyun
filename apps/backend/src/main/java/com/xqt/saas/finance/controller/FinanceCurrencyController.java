package com.xqt.saas.finance.controller;

import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinanceCurrencyExchangeSaveRequest;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.service.FinanceCurrencyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance-currencies")
public class FinanceCurrencyController {
    @Resource
    public FinanceCurrencyService service;

    // 添加货币
    @PostMapping
    public R create(@RequestBody FinanceCurrencySaveRequest req) {
        req = new FinanceCurrencySaveRequest(UserContext.getUserId(), req.code(), req.name());
        return R.success("保存成功", service.createCurrency(req));
    }

    // 删除货币
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        service.deleteCurrency(id);
        return R.success("删除成功");
    }

    // 分页获取每个货币的应收/应付汇率与其生效时间
    @GetMapping
    public R pageCurrencies(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", service.getCurrenciesWithExchangePage(pageNum, pageSize));
    }

    // 添加某个货币的汇率历史
    @PostMapping("/{id}")
    public R createExchange(@PathVariable Long id, @RequestBody FinanceCurrencyExchangeSaveRequest req) {
        req = new FinanceCurrencyExchangeSaveRequest(UserContext.getUserId(), req.applicationScenario(), req.rate(), req.effectiveFrom());
        return R.success("保存成功", service.createExchange(id, req));
    }

    // 删除某个货币的汇率历史
    @DeleteMapping("/{currencyId}/{exchangeId}")
    public R deleteExchange(@PathVariable Long currencyId, @PathVariable Long exchangeId) {
        service.deleteExchange(currencyId, exchangeId);
        return R.success("删除成功");
    }

    // 分页获取某个货币的汇率历史
    @GetMapping("/{id}")
    public R pageCurrencyExchanges(@PathVariable Long id, @RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", service.getCurrencyExchangesPage(id, pageNum, pageSize));
    }
}
