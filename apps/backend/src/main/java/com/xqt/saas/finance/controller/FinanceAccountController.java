package com.xqt.saas.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.dto.request.FinanceAccountQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceAccountSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceAccountView;
import com.xqt.saas.finance.service.FinanceAccountService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance-accounts")
public class FinanceAccountController {

    @Resource
    public FinanceAccountService financeAccountService;

    @PostMapping
    public R save(@Valid @RequestBody FinanceAccountSaveRequest request) {
        return R.success("保存成功", financeAccountService.save(request));
    }

    @PutMapping
    public R update(@Valid @RequestBody FinanceAccountSaveRequest request) {
        return R.success("更新成功", financeAccountService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeAccountService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeAccountService.getById(id));
    }

    @GetMapping("/page")
    public R page(@RequestParam(defaultValue = "1") Long pageNum,
                  @RequestParam(defaultValue = "10") Long pageSize,
                  @RequestParam(required = false) String accountName,
                  @RequestParam(required = false) String currency,
                  @RequestParam(required = false) String bankName,
                  @RequestParam(required = false) Integer type,
                  @RequestParam(required = false) Boolean visible,
                  @RequestParam(required = false) String remark) {
        FinanceAccountQueryRequest queryRequest = new FinanceAccountQueryRequest();
        queryRequest.setAccountName(accountName);
        queryRequest.setCurrency(currency);
        queryRequest.setBankName(bankName);
        queryRequest.setType(type);
        queryRequest.setVisible(visible);
        queryRequest.setRemark(remark);

        IPage<FinanceAccountView> pageResult = financeAccountService.page(queryRequest, pageNum, pageSize);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/list")
    public R list(@RequestParam(required = false) String accountName,
                  @RequestParam(required = false) String currency,
                  @RequestParam(required = false) String bankName,
                  @RequestParam(required = false) Integer type,
                  @RequestParam(required = false) Boolean visible,
                  @RequestParam(required = false) String remark) {
        FinanceAccountQueryRequest queryRequest = new FinanceAccountQueryRequest();
        queryRequest.setAccountName(accountName);
        queryRequest.setCurrency(currency);
        queryRequest.setBankName(bankName);
        queryRequest.setType(type);
        queryRequest.setVisible(visible);
        queryRequest.setRemark(remark);

        return R.success(financeAccountService.list(queryRequest));
    }
}