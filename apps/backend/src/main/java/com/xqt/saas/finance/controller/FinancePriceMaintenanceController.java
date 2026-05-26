package com.xqt.saas.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinancePriceMaintenanceQueryRequest;
import com.xqt.saas.finance.dto.request.FinancePriceMaintenanceSaveRequest;
import com.xqt.saas.finance.dto.response.FinancePriceMaintenanceView;
import com.xqt.saas.finance.service.FinancePriceMaintenanceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运价维护控制器
 * 提供运价维护的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-price-maintenances")
public class FinancePriceMaintenanceController {

    @Resource
    private FinancePriceMaintenanceService financePriceMaintenanceService;

    @PostMapping
    public R save(@RequestBody FinancePriceMaintenanceSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financePriceMaintenanceService.save(request));
    }

    @PutMapping
    public R update(@RequestBody FinancePriceMaintenanceSaveRequest request) {
        return R.success("更新成功", financePriceMaintenanceService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financePriceMaintenanceService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financePriceMaintenanceService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinancePriceMaintenanceQueryRequest request) {
        if (request == null) {
            request = new FinancePriceMaintenanceQueryRequest();
        }
        IPage<FinancePriceMaintenanceView> pageResult = financePriceMaintenanceService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinancePriceMaintenanceQueryRequest request) {
        if (request == null) {
            request = new FinancePriceMaintenanceQueryRequest();
        }
        return R.success(financePriceMaintenanceService.list(request));
    }
}
