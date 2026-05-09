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

    @GetMapping("/page")
    public R page(@RequestParam(defaultValue = "1") Long pageNum,
                  @RequestParam(defaultValue = "10") Long pageSize,
                  @RequestParam(required = false) LocalDateTime startTime,
                  @RequestParam(required = false) LocalDateTime endTime,
                  @RequestParam(required = false) Boolean receivable,
                  @RequestParam(required = false) Boolean payable,
                  @RequestParam(required = false) Boolean salesCost,
                  @RequestParam(required = false) Boolean waybill,
                  @RequestParam(required = false) Boolean billOfLading,
                  @RequestParam(required = false) String remark) {
        FinanceMonthlyStatementQueryRequest queryRequest = new FinanceMonthlyStatementQueryRequest();
        queryRequest.setStartTime(startTime);
        queryRequest.setEndTime(endTime);
        queryRequest.setReceivable(receivable);
        queryRequest.setPayable(payable);
        queryRequest.setSalesCost(salesCost);
        queryRequest.setWaybill(waybill);
        queryRequest.setBillOfLading(billOfLading);
        queryRequest.setRemark(remark);

        IPage<FinanceMonthlyStatementView> pageResult = financeMonthlyStatementService.page(queryRequest, pageNum, pageSize);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/list")
    public R list(@RequestParam(required = false) LocalDateTime startTime,
                  @RequestParam(required = false) LocalDateTime endTime,
                  @RequestParam(required = false) Boolean receivable,
                  @RequestParam(required = false) Boolean payable,
                  @RequestParam(required = false) Boolean salesCost,
                  @RequestParam(required = false) Boolean waybill,
                  @RequestParam(required = false) Boolean billOfLading,
                  @RequestParam(required = false) String remark) {
        FinanceMonthlyStatementQueryRequest queryRequest = new FinanceMonthlyStatementQueryRequest();
        queryRequest.setStartTime(startTime);
        queryRequest.setEndTime(endTime);
        queryRequest.setReceivable(receivable);
        queryRequest.setPayable(payable);
        queryRequest.setSalesCost(salesCost);
        queryRequest.setWaybill(waybill);
        queryRequest.setBillOfLading(billOfLading);
        queryRequest.setRemark(remark);

        return R.success(financeMonthlyStatementService.list(queryRequest));
    }
}