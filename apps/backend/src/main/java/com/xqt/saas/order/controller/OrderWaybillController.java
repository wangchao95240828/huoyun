package com.xqt.saas.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.order.common.R;
import com.xqt.saas.order.common.UserContext;
import com.xqt.saas.order.dto.request.OrderWaybillQueryRequest;
import com.xqt.saas.order.dto.request.OrderWaybillSaveRequest;
import com.xqt.saas.order.dto.response.OrderWaybillView;
import com.xqt.saas.order.service.OrderWaybillService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 运单控制器
 * 提供运单及其所有子表的RESTful API接口
 */
@RestController
@RequestMapping("/api/order-waybills")
public class OrderWaybillController {

    @Resource
    private OrderWaybillService orderWaybillService;

    @PostMapping
    public R save(@RequestBody OrderWaybillSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", orderWaybillService.save(request));
    }

    @PutMapping
    public R update(@RequestBody OrderWaybillSaveRequest request) {
        return R.success("更新成功", orderWaybillService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        orderWaybillService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(orderWaybillService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) OrderWaybillQueryRequest request) {
        if (request == null) {
            request = new OrderWaybillQueryRequest();
        }
        IPage<OrderWaybillView> pageResult = orderWaybillService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) OrderWaybillQueryRequest request) {
        if (request == null) {
            request = new OrderWaybillQueryRequest();
        }
        return R.success(orderWaybillService.list(request));
    }
}
