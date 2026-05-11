package com.xqt.saas.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.dto.request.FinanceAccountTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceAccountTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceAccountTransactionView;
import com.xqt.saas.finance.service.FinanceAccountTransactionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 账户流水控制器
 * 提供账户流水的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-account-transactions")
public class FinanceAccountTransactionController {

    @Resource
    public FinanceAccountTransactionService financeAccountTransactionService;

    /**
     * 保存账户流水
     */
    @PostMapping
    public R save(@Valid @RequestBody FinanceAccountTransactionSaveRequest request) {
        return R.success("保存成功", financeAccountTransactionService.save(request));
    }

    /**
     * 更新账户流水
     */
    @PutMapping
    public R update(@Valid @RequestBody FinanceAccountTransactionSaveRequest request) {
        return R.success("更新成功", financeAccountTransactionService.update(request));
    }

    /**
     * 删除账户流水
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeAccountTransactionService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询账户流水
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeAccountTransactionService.getById(id));
    }

    /**
     * 分页查询账户流水列表
     */
    @GetMapping("/page")
    public R page(@RequestParam(defaultValue = "1") Long pageNum,
                  @RequestParam(defaultValue = "10") Long pageSize,
                  @RequestParam(required = false) String transactionNo,
                  @RequestParam(required = false) Long accountId,
                  @RequestParam(required = false) Long customerId,
                  @RequestParam(required = false) Integer transactionType,
                  @RequestParam(required = false) String currency,
                  @RequestParam(required = false) String remark,
                  @RequestParam(required = false) LocalDateTime paymentTimeStart,
                  @RequestParam(required = false) LocalDateTime paymentTimeEnd) {
        FinanceAccountTransactionQueryRequest queryRequest = new FinanceAccountTransactionQueryRequest();
        queryRequest.setTransactionNo(transactionNo);
        queryRequest.setAccountId(accountId);
        queryRequest.setCustomerId(customerId);
        queryRequest.setTransactionType(transactionType);
        queryRequest.setCurrency(currency);
        queryRequest.setRemark(remark);
        queryRequest.setPaymentTimeStart(paymentTimeStart);
        queryRequest.setPaymentTimeEnd(paymentTimeEnd);

        IPage<FinanceAccountTransactionView> pageResult = financeAccountTransactionService.page(queryRequest, pageNum, pageSize);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询账户流水列表
     */
    @GetMapping("/list")
    public R list(@RequestParam(required = false) String transactionNo,
                  @RequestParam(required = false) Long accountId,
                  @RequestParam(required = false) Long customerId,
                  @RequestParam(required = false) Integer transactionType,
                  @RequestParam(required = false) String currency,
                  @RequestParam(required = false) String remark,
                  @RequestParam(required = false) LocalDateTime paymentTimeStart,
                  @RequestParam(required = false) LocalDateTime paymentTimeEnd) {
        FinanceAccountTransactionQueryRequest queryRequest = new FinanceAccountTransactionQueryRequest();
        queryRequest.setTransactionNo(transactionNo);
        queryRequest.setAccountId(accountId);
        queryRequest.setCustomerId(customerId);
        queryRequest.setTransactionType(transactionType);
        queryRequest.setCurrency(currency);
        queryRequest.setRemark(remark);
        queryRequest.setPaymentTimeStart(paymentTimeStart);
        queryRequest.setPaymentTimeEnd(paymentTimeEnd);

        return R.success(financeAccountTransactionService.list(queryRequest));
    }
}
