package com.xqt.saas.finance.controller;

import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.request.FinanceApprovalSaveRequest;
import com.xqt.saas.finance.service.FinanceApprovalService;
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
@RequestMapping("/api/finance-approvals")
public class FinanceApprovalController {
    @Resource
    private FinanceApprovalService service;

    @PostMapping
    public R create(@RequestBody FinanceApprovalSaveRequest req) {
        var userId = UserContext.getUserId();
        req = req.withCreatedBy(userId);
        return R.success("保存成功", service.saveOne(TenantUtils.currentUuid(), req));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        service.deleteOne(id, TenantUtils.currentUuid());
        return R.success("删除成功");
    }

    @GetMapping
    public R getAll() {
        return R.success("查询成功", service.listAll(TenantUtils.currentUuid()));
    }

    @GetMapping("/page")
    public R getPage(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", service.page(TenantUtils.currentUuid(), pageNum, pageSize));
    }
}
