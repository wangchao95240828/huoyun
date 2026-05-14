package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinancePriceMaintenanceQueryRequest;
import com.xqt.saas.finance.dto.request.FinancePriceMaintenanceSaveRequest;
import com.xqt.saas.finance.dto.response.FinancePriceMaintenanceView;
import com.xqt.saas.finance.entity.FinancePriceMaintenance;
import com.xqt.saas.finance.mapper.FinancePriceMaintenanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 运价维护服务类
 * 提供运价维护的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinancePriceMaintenanceService extends ServiceImpl<FinancePriceMaintenanceMapper, FinancePriceMaintenance> {

    private final FinancePriceMaintenanceMapper financePriceMaintenanceMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinancePriceMaintenanceView save(FinancePriceMaintenanceSaveRequest request) {
        FinancePriceMaintenance entity = new FinancePriceMaintenance();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setName(request.getName());
        entity.setService(request.getService());
        entity.setReceiveArea(request.getReceiveArea());
        entity.setPriority(request.getPriority());
        entity.setUserLevel(request.getUserLevel());
        entity.setUserName(request.getUserName());
        entity.setMinWeight(request.getMinWeight());
        entity.setMaxWeight(request.getMaxWeight());
        entity.setZipPrefix(request.getZipPrefix());
        entity.setParentId(request.getParentId());
        entity.setStatus(request.getStatus());
        entity.setPriceType(request.getPriceType());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financePriceMaintenanceMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinancePriceMaintenanceView update(FinancePriceMaintenanceSaveRequest request) {
        FinancePriceMaintenance entity = financePriceMaintenanceMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("运价维护不存在");
        }

        entity.setName(request.getName());
        entity.setService(request.getService());
        entity.setReceiveArea(request.getReceiveArea());
        entity.setPriority(request.getPriority());
        entity.setUserLevel(request.getUserLevel());
        entity.setUserName(request.getUserName());
        entity.setMinWeight(request.getMinWeight());
        entity.setMaxWeight(request.getMaxWeight());
        entity.setZipPrefix(request.getZipPrefix());
        entity.setParentId(request.getParentId());
        entity.setStatus(request.getStatus());
        entity.setPriceType(request.getPriceType());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financePriceMaintenanceMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinancePriceMaintenance entity = financePriceMaintenanceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("运价维护不存在");
        }
        financePriceMaintenanceMapper.deleteById(id);
    }

    public FinancePriceMaintenanceView getById(Long id) {
        FinancePriceMaintenance entity = financePriceMaintenanceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("运价维护不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinancePriceMaintenanceView> page(FinancePriceMaintenanceQueryRequest request) {
        Page<FinancePriceMaintenance> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinancePriceMaintenance> queryWrapper = buildQueryWrapper(request);

        IPage<FinancePriceMaintenance> resultPage = financePriceMaintenanceMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinancePriceMaintenanceView> list(FinancePriceMaintenanceQueryRequest request) {
        LambdaQueryWrapper<FinancePriceMaintenance> queryWrapper = buildQueryWrapper(request);
        List<FinancePriceMaintenance> list = financePriceMaintenanceMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<FinancePriceMaintenance> buildQueryWrapper(FinancePriceMaintenanceQueryRequest request) {
        LambdaQueryWrapper<FinancePriceMaintenance> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinancePriceMaintenance::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getName())) {
            queryWrapper.like(FinancePriceMaintenance::getName, request.getName());
        }
        if (StringUtils.hasText(request.getService())) {
            queryWrapper.like(FinancePriceMaintenance::getService, request.getService());
        }
        if (StringUtils.hasText(request.getReceiveArea())) {
            queryWrapper.like(FinancePriceMaintenance::getReceiveArea, request.getReceiveArea());
        }
        if (StringUtils.hasText(request.getUserLevel())) {
            queryWrapper.like(FinancePriceMaintenance::getUserLevel, request.getUserLevel());
        }
        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(FinancePriceMaintenance::getUserName, request.getUserName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(FinancePriceMaintenance::getStatus, request.getStatus());
        }
        if (request.getPriceType() != null) {
            queryWrapper.eq(FinancePriceMaintenance::getPriceType, request.getPriceType());
        }

        queryWrapper.orderByDesc(FinancePriceMaintenance::getCreateTime);
        return queryWrapper;
    }

    private FinancePriceMaintenanceView convertToView(FinancePriceMaintenance entity) {
        FinancePriceMaintenanceView view = new FinancePriceMaintenanceView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setName(entity.getName());
        view.setService(entity.getService());
        view.setReceiveArea(entity.getReceiveArea());
        view.setPriority(entity.getPriority());
        view.setUserLevel(entity.getUserLevel());
        view.setUserName(entity.getUserName());
        view.setMinWeight(entity.getMinWeight());
        view.setMaxWeight(entity.getMaxWeight());
        view.setZipPrefix(entity.getZipPrefix());
        view.setParentId(entity.getParentId());
        view.setStatus(entity.getStatus());
        view.setPriceType(entity.getPriceType());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    public static String getPriceTypeText(Integer priceType) {
        if (priceType == null) {
            return "";
        }
        switch (priceType) {
            case 1:
                return "公布价";
            case 2:
                return "销售员底价";
            case 3:
                return "成本价";
            default:
                return "";
        }
    }
}
