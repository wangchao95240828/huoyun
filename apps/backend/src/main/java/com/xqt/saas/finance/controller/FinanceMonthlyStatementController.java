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

/**
 * 月结单控制器
 * 提供月结单的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-monthly-statements")
public class FinanceMonthlyStatementController {

    @Resource
    public FinanceMonthlyStatementService financeMonthlyStatementService;

    /**
     * 保存月结单
     */
    @PostMapping
    public R save(@Valid @RequestBody FinanceMonthlyStatementSaveRequest request) {
        return R.success("保存成功", financeMonthlyStatementService.save(request));
    }

    /**
     * 更新月结单
     */
    @PutMapping
    public R update(@Valid @RequestBody FinanceMonthlyStatementSaveRequest request) {
        return R.success("更新成功", financeMonthlyStatementService.update(request));
    }

    /**
     * 删除月结单
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeMonthlyStatementService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询月结单
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeMonthlyStatementService.getById(id));
    }

    /**
     * 分页查询月结单列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceMonthlyStatementQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceMonthlyStatementQueryRequest();
        }
        IPage<FinanceMonthlyStatementView> pageResult = financeMonthlyStatementService.page(queryRequest);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询月结单列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceMonthlyStatementQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceMonthlyStatementQueryRequest();
        }
        return R.success(financeMonthlyStatementService.list(queryRequest));
    }
}
