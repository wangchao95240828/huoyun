package com.xqt.saas.finance.controller;

import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.service.FinanceCurrencyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance-currencies")
public class FinanceCurrencyController {
    @Resource
    public FinanceCurrencyService financeCurrencyService;

    @PostMapping("/save")
    public R save(@RequestBody FinanceCurrencySaveRequest req) {
        req = new FinanceCurrencySaveRequest(UserContext.getUserId(), req.code(), req.name());
        return R.success("保存成功", financeCurrencyService.save(req));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeCurrencyService.delete(id);
        return R.success("删除成功");
    }

    // 获取某个货币的汇率历史
    @GetMapping("/{id}")
    public R getHistory(@PathVariable Long id) {
        // todo: 获取某个货币的汇率历史
    }

    // 分页获取每个货币的应收/应付汇率与其生效时间
    @GetMapping("/page")
    public R page(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", financeCurrencyService.pageCurrencyWithExchangeRate(pageNum, pageSize));
    }
}
