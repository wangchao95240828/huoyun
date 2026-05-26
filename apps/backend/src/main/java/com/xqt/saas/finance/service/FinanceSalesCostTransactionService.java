package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.excel.FinanceSalesCostTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSalesCostTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSalesCostTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSalesCostTransactionView;
import com.xqt.saas.finance.entity.FinanceSalesCostTransaction;
import com.xqt.saas.finance.mapper.FinanceSalesCostTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 销售成本流水服务类
 * 提供销售成本流水的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceSalesCostTransactionService extends ServiceImpl<FinanceSalesCostTransactionMapper, FinanceSalesCostTransaction> {

    private final FinanceSalesCostTransactionMapper financeSalesCostTransactionMapper;

    /**
     * 保存销售成本流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSalesCostTransactionView save(FinanceSalesCostTransactionSaveRequest request) {
        FinanceSalesCostTransaction entity = new FinanceSalesCostTransaction();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setTransactionNo(request.getTransactionNo());
        entity.setEmployee(request.getEmployee());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setTransferNo(request.getTransferNo());
        entity.setFeeType(request.getFeeType());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setRemark(request.getRemark());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSalesCostTransactionMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新销售成本流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSalesCostTransactionView update(FinanceSalesCostTransactionSaveRequest request) {
        FinanceSalesCostTransaction entity = financeSalesCostTransactionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("销售成本流水不存在");
        }

        entity.setTransactionNo(request.getTransactionNo());
        entity.setEmployee(request.getEmployee());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setTransferNo(request.getTransferNo());
        entity.setFeeType(request.getFeeType());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setRemark(request.getRemark());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSalesCostTransactionMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除销售成本流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceSalesCostTransaction entity = financeSalesCostTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("销售成本流水不存在");
        }
        financeSalesCostTransactionMapper.deleteById(id);
    }

    /**
     * 根据ID查询销售成本流水
     */
    public FinanceSalesCostTransactionView getById(Long id) {
        FinanceSalesCostTransaction entity = financeSalesCostTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("销售成本流水不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询销售成本流水列表
     */
    public IPage<FinanceSalesCostTransactionView> page(FinanceSalesCostTransactionQueryRequest request) {
        Page<FinanceSalesCostTransaction> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceSalesCostTransaction> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceSalesCostTransaction> resultPage = financeSalesCostTransactionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询销售成本流水列表
     */
    public List<FinanceSalesCostTransactionView> list(FinanceSalesCostTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCostTransaction> queryWrapper = buildQueryWrapper(request);
        List<FinanceSalesCostTransaction> list = financeSalesCostTransactionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导入销售成本流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceSalesCostTransactionExcelDTO> dataList, String tenantId) {
        List<FinanceSalesCostTransaction> entityList = dataList.stream()
                .map(dto -> convertToEntity(dto, tenantId))
                .collect(Collectors.toList());
        
        for (FinanceSalesCostTransaction entity : entityList) {
            financeSalesCostTransactionMapper.insert(entity);
        }
    }

    /**
     * 导出销售成本流水
     */
    public List<FinanceSalesCostTransaction> exportExcel(FinanceSalesCostTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCostTransaction> queryWrapper = buildQueryWrapper(request);
        return financeSalesCostTransactionMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceSalesCostTransaction> buildQueryWrapper(FinanceSalesCostTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCostTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceSalesCostTransaction::getTenantId, TenantUtils.currentUuid());

        if (StringUtils.hasText(request.getTransactionNo())) {
            queryWrapper.like(FinanceSalesCostTransaction::getTransactionNo, request.getTransactionNo());
        }
        if (StringUtils.hasText(request.getEmployee())) {
            queryWrapper.like(FinanceSalesCostTransaction::getEmployee, request.getEmployee());
        }
        if (StringUtils.hasText(request.getWaybillNo())) {
            queryWrapper.like(FinanceSalesCostTransaction::getWaybillNo, request.getWaybillNo());
        }
        if (StringUtils.hasText(request.getTransferNo())) {
            queryWrapper.like(FinanceSalesCostTransaction::getTransferNo, request.getTransferNo());
        }
        if (StringUtils.hasText(request.getFeeType())) {
            queryWrapper.like(FinanceSalesCostTransaction::getFeeType, request.getFeeType());
        }
        if (request.getAuditStatus() != null) {
            queryWrapper.eq(FinanceSalesCostTransaction::getAuditStatus, request.getAuditStatus());
        }
        if (StringUtils.hasText(request.getRemark())) {
            queryWrapper.like(FinanceSalesCostTransaction::getRemark, request.getRemark());
        }
        if (request.getBusinessTimeStart() != null) {
            queryWrapper.ge(FinanceSalesCostTransaction::getBusinessTime, request.getBusinessTimeStart());
        }
        if (request.getBusinessTimeEnd() != null) {
            queryWrapper.le(FinanceSalesCostTransaction::getBusinessTime, request.getBusinessTimeEnd());
        }

        queryWrapper.orderByDesc(FinanceSalesCostTransaction::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceSalesCostTransactionView convertToView(FinanceSalesCostTransaction entity) {
        FinanceSalesCostTransactionView view = new FinanceSalesCostTransactionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTransactionNo(entity.getTransactionNo());
        view.setEmployee(entity.getEmployee());
        view.setWaybillNo(entity.getWaybillNo());
        view.setTransferNo(entity.getTransferNo());
        view.setFeeType(entity.getFeeType());
        view.setFee(entity.getFee());
        view.setExchangeRate(entity.getExchangeRate());
        view.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        view.setAuditStatus(entity.getAuditStatus());
        view.setRemark(entity.getRemark());
        view.setBusinessTime(entity.getBusinessTime());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    /**
     * 转换为实体对象（用于导入）
     */
    private FinanceSalesCostTransaction convertToEntity(FinanceSalesCostTransactionExcelDTO dto, String tenantId) {
        FinanceSalesCostTransaction entity = new FinanceSalesCostTransaction();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setTransactionNo(dto.getTransactionNo());
        entity.setEmployee(dto.getEmployee());
        entity.setWaybillNo(dto.getWaybillNo());
        entity.setTransferNo(dto.getTransferNo());
        entity.setFeeType(dto.getFeeType());
        entity.setFee(dto.getFee());
        entity.setExchangeRate(dto.getExchangeRate());
        entity.setLocalCurrencyFee(dto.getLocalCurrencyFee());
        entity.setAuditStatus(parseAuditStatus(dto.getAuditStatusText()));
        entity.setRemark(dto.getRemark());
        entity.setBusinessTime(dto.getBusinessTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 解析审核状态文本
     */
    private Integer parseAuditStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        switch (statusText.trim()) {
            case "待审计":
                return 1;
            case "已审计":
                return 2;
            default:
                return null;
        }
    }

    /**
     * 获取审核状态文本
     */
    public static String getAuditStatusText(Integer auditStatus) {
        if (auditStatus == null) {
            return "";
        }
        switch (auditStatus) {
            case 1:
                return "待审计";
            case 2:
                return "已审计";
            default:
                return "";
        }
    }
}
