package com.xqt.saas.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.order.common.UserContext;
import com.xqt.saas.order.dto.request.OrderServiceQueryRequest;
import com.xqt.saas.order.dto.request.OrderServiceSaveRequest;
import com.xqt.saas.order.dto.response.OrderServiceView;
import com.xqt.saas.order.entity.OrderService;
import com.xqt.saas.order.mapper.OrderServiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 服务服务类
 * 提供服务的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class OrderServiceService extends ServiceImpl<OrderServiceMapper, OrderService> {

    private final OrderServiceMapper orderServiceMapper;

    @Transactional(rollbackFor = Exception.class)
    public OrderServiceView save(OrderServiceSaveRequest request) {
        OrderService entity = new OrderService();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setServiceName(request.getServiceName());
        entity.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        entity.setServiceCode(request.getServiceCode());
        entity.setType(request.getType());
        entity.setCarrierType(request.getCarrierType());
        entity.setServiceCategory(request.getServiceCategory());
        entity.setBillingMethod(request.getBillingMethod());
        entity.setWeightMethod(request.getWeightMethod());
        entity.setBubbleFactor(request.getBubbleFactor());
        entity.setBubbleShare(request.getBubbleShare());
        entity.setPieceRange(request.getPieceRange());
        entity.setPrintAccount(request.getPrintAccount());
        entity.setLabelType(request.getLabelType());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        orderServiceMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderServiceView update(OrderServiceSaveRequest request) {
        OrderService entity = orderServiceMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("服务不存在");
        }

        entity.setServiceName(request.getServiceName());
        entity.setStatus(request.getStatus());
        entity.setServiceCode(request.getServiceCode());
        entity.setType(request.getType());
        entity.setCarrierType(request.getCarrierType());
        entity.setServiceCategory(request.getServiceCategory());
        entity.setBillingMethod(request.getBillingMethod());
        entity.setWeightMethod(request.getWeightMethod());
        entity.setBubbleFactor(request.getBubbleFactor());
        entity.setBubbleShare(request.getBubbleShare());
        entity.setPieceRange(request.getPieceRange());
        entity.setPrintAccount(request.getPrintAccount());
        entity.setLabelType(request.getLabelType());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        orderServiceMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrderService entity = orderServiceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("服务不存在");
        }
        orderServiceMapper.deleteById(id);
    }

    public OrderServiceView getById(Long id) {
        OrderService entity = orderServiceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("服务不存在");
        }
        return convertToView(entity);
    }

    public IPage<OrderServiceView> page(OrderServiceQueryRequest request) {
        Page<OrderService> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<OrderService> queryWrapper = buildQueryWrapper(request);

        IPage<OrderService> resultPage = orderServiceMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<OrderServiceView> list(OrderServiceQueryRequest request) {
        LambdaQueryWrapper<OrderService> queryWrapper = buildQueryWrapper(request);
        List<OrderService> list = orderServiceMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<OrderService> buildQueryWrapper(OrderServiceQueryRequest request) {
        LambdaQueryWrapper<OrderService> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderService::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getServiceName())) {
            queryWrapper.like(OrderService::getServiceName, request.getServiceName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(OrderService::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getServiceCode())) {
            queryWrapper.like(OrderService::getServiceCode, request.getServiceCode());
        }
        if (StringUtils.hasText(request.getType())) {
            queryWrapper.like(OrderService::getType, request.getType());
        }
        if (StringUtils.hasText(request.getCarrierType())) {
            queryWrapper.like(OrderService::getCarrierType, request.getCarrierType());
        }
        if (StringUtils.hasText(request.getServiceCategory())) {
            queryWrapper.like(OrderService::getServiceCategory, request.getServiceCategory());
        }

        queryWrapper.orderByDesc(OrderService::getCreateTime);
        return queryWrapper;
    }

    private OrderServiceView convertToView(OrderService entity) {
        OrderServiceView view = new OrderServiceView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setServiceName(entity.getServiceName());
        view.setStatus(entity.getStatus());
        view.setStatusText(getStatusText(entity.getStatus()));
        view.setServiceCode(entity.getServiceCode());
        view.setType(entity.getType());
        view.setCarrierType(entity.getCarrierType());
        view.setServiceCategory(entity.getServiceCategory());
        view.setBillingMethod(entity.getBillingMethod());
        view.setWeightMethod(entity.getWeightMethod());
        view.setBubbleFactor(entity.getBubbleFactor());
        view.setBubbleShare(entity.getBubbleShare());
        view.setPieceRange(entity.getPieceRange());
        view.setPrintAccount(entity.getPrintAccount());
        view.setLabelType(entity.getLabelType());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    public static String getStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case 1:
                return "正常";
            case 2:
                return "停用";
            default:
                return "";
        }
    }
}
