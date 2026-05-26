package com.xqt.saas.finance.controller;

import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.request.FinanceCurrencyExchangeSaveRequest;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.service.FinanceCurrencyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/finance-currencies")
public class FinanceCurrencyController {
    @Resource
    private FinanceCurrencyService service;

    // 添加货币
    @PostMapping
    public R create(@RequestBody FinanceCurrencySaveRequest req) {
        req = new FinanceCurrencySaveRequest(UserContext.getUserId(), req.code(), req.name());
        return R.success("保存成功", service.createCurrency(TenantUtils.currentUuid(), req));
    }

    // 删除货币
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        service.deleteCurrency(TenantUtils.currentUuid(), id);
        return R.success("删除成功");
    }

    // 获取所有货币的应收/应付汇率与其生效时间
    @GetMapping
    public R getAll() {
        return R.success(service.getCurrenciesWithExchange(TenantUtils.currentUuid()));
    }

    // 分页获取每个货币的应收/应付汇率与其生效时间
    @GetMapping("/page")
    public R pageCurrencies(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", service.getCurrenciesWithExchangePage(TenantUtils.currentUuid(), pageNum, pageSize));
    }

    // 添加某个货币的汇率历史
    @PostMapping("/{id}")
    public R createExchange(@PathVariable Long id, @RequestBody FinanceCurrencyExchangeSaveRequest req) {
        req = new FinanceCurrencyExchangeSaveRequest(UserContext.getUserId(), req.applicationScenario(), req.rate(), req.effectiveFrom());
        return R.success("保存成功", service.createExchange(TenantUtils.currentUuid(), id, req));
    }

    // 删除某个货币的汇率历史
    @DeleteMapping("/{currencyId}/{exchangeId}")
    public R deleteExchange(@PathVariable Long currencyId, @PathVariable Long exchangeId) {
        service.deleteExchange(TenantUtils.currentUuid(), currencyId, exchangeId);
        return R.success("删除成功");
    }

    // 分页获取某个货币的汇率历史
    @GetMapping("/{id}")
    public R pageCurrencyExchanges(@PathVariable Long id, @RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", service.getCurrencyExchangesPage(TenantUtils.currentUuid(), id, pageNum, pageSize));
    }
}
