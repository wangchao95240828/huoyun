package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceWaybillAuditExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceWaybillAuditQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceWaybillAuditSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceWaybillAuditView;
import com.xqt.saas.finance.entity.FinanceWaybillAudit;
import com.xqt.saas.finance.mapper.FinanceWaybillAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 运单审计服务类
 * 提供运单审计的CRUD操作和导入导出功能
 */
@Service
@RequiredArgsConstructor
public class FinanceWaybillAuditService extends ServiceImpl<FinanceWaybillAuditMapper, FinanceWaybillAudit> {

    private final FinanceWaybillAuditMapper financeWaybillAuditMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceWaybillAuditView save(FinanceWaybillAuditSaveRequest request) {
        FinanceWaybillAudit entity = new FinanceWaybillAudit();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setWaybillNo(request.getWaybillNo());
        entity.setUserName(request.getUserName());
        entity.setService(request.getService());
        entity.setCountry(request.getCountry());
        entity.setPieceCount(request.getPieceCount());
        entity.setActualWeight(request.getActualWeight());
        entity.setVolumeWeight(request.getVolumeWeight());
        entity.setChargeWeight(request.getChargeWeight());
        entity.setSupplierWeight(request.getSupplierWeight());
        entity.setStatus(request.getStatus());
        entity.setReceivableAmount(request.getReceivableAmount());
        entity.setPayableAmount(request.getPayableAmount());
        entity.setSalesCost(request.getSalesCost());
        entity.setSalesCommission(request.getSalesCommission());
        entity.setGrossProfit(request.getGrossProfit());
        entity.setCustomerServiceRep(request.getCustomerServiceRep());
        entity.setSalesRep(request.getSalesRep());
        entity.setPickingTime(request.getPickingTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeWaybillAuditMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceWaybillAuditView update(FinanceWaybillAuditSaveRequest request) {
        FinanceWaybillAudit entity = financeWaybillAuditMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("运单审计不存在");
        }

        entity.setWaybillNo(request.getWaybillNo());
        entity.setUserName(request.getUserName());
        entity.setService(request.getService());
        entity.setCountry(request.getCountry());
        entity.setPieceCount(request.getPieceCount());
        entity.setActualWeight(request.getActualWeight());
        entity.setVolumeWeight(request.getVolumeWeight());
        entity.setChargeWeight(request.getChargeWeight());
        entity.setSupplierWeight(request.getSupplierWeight());
        entity.setStatus(request.getStatus());
        entity.setReceivableAmount(request.getReceivableAmount());
        entity.setPayableAmount(request.getPayableAmount());
        entity.setSalesCost(request.getSalesCost());
        entity.setSalesCommission(request.getSalesCommission());
        entity.setGrossProfit(request.getGrossProfit());
        entity.setCustomerServiceRep(request.getCustomerServiceRep());
        entity.setSalesRep(request.getSalesRep());
        entity.setPickingTime(request.getPickingTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeWaybillAuditMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceWaybillAudit entity = financeWaybillAuditMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("运单审计不存在");
        }
        financeWaybillAuditMapper.deleteById(id);
    }

    public FinanceWaybillAuditView getById(Long id) {
        FinanceWaybillAudit entity = financeWaybillAuditMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("运单审计不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinanceWaybillAuditView> page(FinanceWaybillAuditQueryRequest request) {
        Page<FinanceWaybillAudit> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceWaybillAudit> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceWaybillAudit> resultPage = financeWaybillAuditMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinanceWaybillAuditView> list(FinanceWaybillAuditQueryRequest request) {
        LambdaQueryWrapper<FinanceWaybillAudit> queryWrapper = buildQueryWrapper(request);
        List<FinanceWaybillAudit> list = financeWaybillAuditMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceWaybillAuditExcelDTO> dataList, String tenantId) {
        List<FinanceWaybillAudit> entityList = dataList.stream()
                .map(dto -> convertToEntity(dto, tenantId))
                .collect(Collectors.toList());
        
        for (FinanceWaybillAudit entity : entityList) {
            financeWaybillAuditMapper.insert(entity);
        }
    }

    public List<FinanceWaybillAudit> exportExcel(FinanceWaybillAuditQueryRequest request) {
        LambdaQueryWrapper<FinanceWaybillAudit> queryWrapper = buildQueryWrapper(request);
        return financeWaybillAuditMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<FinanceWaybillAudit> buildQueryWrapper(FinanceWaybillAuditQueryRequest request) {
        LambdaQueryWrapper<FinanceWaybillAudit> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceWaybillAudit::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getWaybillNo())) {
            queryWrapper.like(FinanceWaybillAudit::getWaybillNo, request.getWaybillNo());
        }
        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(FinanceWaybillAudit::getUserName, request.getUserName());
        }
        if (StringUtils.hasText(request.getService())) {
            queryWrapper.like(FinanceWaybillAudit::getService, request.getService());
        }
        if (StringUtils.hasText(request.getCountry())) {
            queryWrapper.like(FinanceWaybillAudit::getCountry, request.getCountry());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(FinanceWaybillAudit::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getCustomerServiceRep())) {
            queryWrapper.like(FinanceWaybillAudit::getCustomerServiceRep, request.getCustomerServiceRep());
        }
        if (StringUtils.hasText(request.getSalesRep())) {
            queryWrapper.like(FinanceWaybillAudit::getSalesRep, request.getSalesRep());
        }
        if (request.getPickingTimeStart() != null) {
            queryWrapper.ge(FinanceWaybillAudit::getPickingTime, request.getPickingTimeStart());
        }
        if (request.getPickingTimeEnd() != null) {
            queryWrapper.le(FinanceWaybillAudit::getPickingTime, request.getPickingTimeEnd());
        }

        queryWrapper.orderByDesc(FinanceWaybillAudit::getCreateTime);
        return queryWrapper;
    }

    private FinanceWaybillAuditView convertToView(FinanceWaybillAudit entity) {
        FinanceWaybillAuditView view = new FinanceWaybillAuditView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillNo(entity.getWaybillNo());
        view.setUserName(entity.getUserName());
        view.setService(entity.getService());
        view.setCountry(entity.getCountry());
        view.setPieceCount(entity.getPieceCount());
        view.setActualWeight(entity.getActualWeight());
        view.setVolumeWeight(entity.getVolumeWeight());
        view.setChargeWeight(entity.getChargeWeight());
        view.setSupplierWeight(entity.getSupplierWeight());
        view.setStatus(entity.getStatus());
        view.setReceivableAmount(entity.getReceivableAmount());
        view.setPayableAmount(entity.getPayableAmount());
        view.setSalesCost(entity.getSalesCost());
        view.setSalesCommission(entity.getSalesCommission());
        view.setGrossProfit(entity.getGrossProfit());
        view.setCustomerServiceRep(entity.getCustomerServiceRep());
        view.setSalesRep(entity.getSalesRep());
        view.setPickingTime(entity.getPickingTime());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private FinanceWaybillAudit convertToEntity(FinanceWaybillAuditExcelDTO dto, String tenantId) {
        FinanceWaybillAudit entity = new FinanceWaybillAudit();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setWaybillNo(dto.getWaybillNo());
        entity.setUserName(dto.getUserName());
        entity.setService(dto.getService());
        entity.setCountry(dto.getCountry());
        entity.setPieceCount(dto.getPieceCount());
        entity.setActualWeight(dto.getActualWeight());
        entity.setVolumeWeight(dto.getVolumeWeight());
        entity.setChargeWeight(dto.getChargeWeight());
        entity.setSupplierWeight(dto.getSupplierWeight());
        entity.setStatus(parseStatus(dto.getStatusText()));
        entity.setReceivableAmount(dto.getReceivableAmount());
        entity.setPayableAmount(dto.getPayableAmount());
        entity.setSalesCost(dto.getSalesCost());
        entity.setSalesCommission(dto.getSalesCommission());
        entity.setGrossProfit(dto.getGrossProfit());
        entity.setCustomerServiceRep(dto.getCustomerServiceRep());
        entity.setSalesRep(dto.getSalesRep());
        entity.setPickingTime(dto.getPickingTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    private Integer parseStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        switch (statusText.trim()) {
            case "已收货":
                return 1;
            case "转运中":
                return 2;
            case "已签收":
                return 3;
            case "退件":
                return 4;
            default:
                return null;
        }
    }

    public static String getStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case 1:
                return "已收货";
            case 2:
                return "转运中";
            case 3:
                return "已签收";
            case 4:
                return "退件";
            default:
                return "";
        }
    }
}
