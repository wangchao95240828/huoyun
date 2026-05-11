package com.xqt.saas.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.dto.request.FinanceMonthlyStatementQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceMonthlyStatementSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceMonthlyStatementView;
import com.xqt.saas.finance.service.FinanceMonthlyStatementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/finance-monthly-statements")
public class FinanceMonthlyStatementController {

    @Resource
    public FinanceMonthlyStatementService financeMonthlyStatementService;

    @PostMapping
    public R save(@Valid @RequestBody FinanceMonthlyStatementSaveRequest request) {
        return R.success("保存成功", financeMonthlyStatementService.save(request));
    }

    @PutMapping
    public R update(@Valid @RequestBody FinanceMonthlyStatementSaveRequest request) {
        return R.success("更新成功", financeMonthlyStatementService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeMonthlyStatementService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeMonthlyStatementService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceMonthlyStatementQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceMonthlyStatementQueryRequest();
        }
        IPage<FinanceMonthlyStatementView> pageResult = financeMonthlyStatementService.page(queryRequest);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceMonthlyStatementQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceMonthlyStatementQueryRequest();
        }
        return R.success(financeMonthlyStatementService.list(queryRequest));
    }
}