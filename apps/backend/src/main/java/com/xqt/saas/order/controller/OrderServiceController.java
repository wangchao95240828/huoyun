package com.xqt.saas.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.order.common.R;
import com.xqt.saas.order.common.UserContext;
import com.xqt.saas.order.dto.request.OrderServiceQueryRequest;
import com.xqt.saas.order.dto.request.OrderServiceSaveRequest;
import com.xqt.saas.order.dto.response.OrderServiceView;
import com.xqt.saas.order.service.OrderServiceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 服务控制器
 * 提供服务的RESTful API接口
 */
@RestController
@RequestMapping("/api/order-services")
public class OrderServiceController {

    @Resource
    private OrderServiceService orderServiceService;

    @PostMapping
    public R save(@RequestBody OrderServiceSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", orderServiceService.save(request));
    }

    @PutMapping
    public R update(@RequestBody OrderServiceSaveRequest request) {
        return R.success("更新成功", orderServiceService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        orderServiceService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(orderServiceService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) OrderServiceQueryRequest request) {
        if (request == null) {
            request = new OrderServiceQueryRequest();
        }
        IPage<OrderServiceView> pageResult = orderServiceService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) OrderServiceQueryRequest request) {
        if (request == null) {
            request = new OrderServiceQueryRequest();
        }
        return R.success(orderServiceService.list(request));
    }
}
