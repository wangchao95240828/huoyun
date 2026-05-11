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

/**
 * 账户控制器
 * 提供账户的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-accounts")
public class FinanceAccountController {

    @Resource
    public FinanceAccountService financeAccountService;

    /**
     * 保存账户
     */
    @PostMapping
    public R save(@Valid @RequestBody FinanceAccountSaveRequest request) {
        return R.success("保存成功", financeAccountService.save(request));
    }

    /**
     * 更新账户
     */
    @PutMapping
    public R update(@Valid @RequestBody FinanceAccountSaveRequest request) {
        return R.success("更新成功", financeAccountService.update(request));
    }

    /**
     * 删除账户
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeAccountService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询账户
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeAccountService.getById(id));
    }

    /**
     * 分页查询账户列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceAccountQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceAccountQueryRequest();
        }
        IPage<FinanceAccountView> pageResult = financeAccountService.page(queryRequest);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询账户列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceAccountQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceAccountQueryRequest();
        }
        return R.success(financeAccountService.list(queryRequest));
    }
}
