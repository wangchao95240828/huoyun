package com.xqt.saas.finance.controller;

import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinanceApprovalSaveRequest;
import com.xqt.saas.finance.service.FinanceApprovalService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance-approvals")
public class FinanceApprovalController {
    @Resource
    public FinanceApprovalService service;

    @PostMapping
    public R create(@RequestBody FinanceApprovalSaveRequest req) {
        var userId = UserContext.getUserId();
        req = req.withCreatedBy(userId);
        return R.success("保存成功", service.saveOne(UUID.fromString(UserContext.getTenantId()), req));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        service.deleteOne(id, UUID.fromString(UserContext.getTenantId()));
        return R.success("删除成功");
    }

    @GetMapping
    public R getAll() {
        return R.success("查询成功", service.listAll(UUID.fromString(UserContext.getTenantId())));
    }

    @GetMapping("/page")
    public R getPage(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize) {
        return R.success("查询成功", service.page(UUID.fromString(UserContext.getTenantId()), pageNum, pageSize));
    }
}
