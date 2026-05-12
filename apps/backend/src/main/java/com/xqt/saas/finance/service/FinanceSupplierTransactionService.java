package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceSupplierTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSupplierTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSupplierTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSupplierTransactionView;
import com.xqt.saas.finance.entity.FinanceSupplierTransaction;
import com.xqt.saas.finance.mapper.FinanceSupplierTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 供应商流水服务类
 * 提供供应商流水的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceSupplierTransactionService extends ServiceImpl<FinanceSupplierTransactionMapper, FinanceSupplierTransaction> {

    private final FinanceSupplierTransactionMapper financeSupplierTransactionMapper;

    /**
     * 保存供应商流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSupplierTransactionView save(FinanceSupplierTransactionSaveRequest request) {
        FinanceSupplierTransaction entity = new FinanceSupplierTransaction();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setTransactionNo(request.getTransactionNo());
        entity.setSupplier(request.getSupplier());
        entity.setBillNo(request.getBillNo());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setWaybillSales(request.getWaybillSales());
        entity.setBillOfLadingNo(request.getBillOfLadingNo());
        entity.setTransferNo(request.getTransferNo());
        entity.setFeeType(request.getFeeType());
        entity.setQuantity(request.getQuantity());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setWriteOffStatus(request.getWriteOffStatus());
        entity.setCustomFlag(request.getCustomFlag());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSupplierTransactionMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新供应商流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSupplierTransactionView update(FinanceSupplierTransactionSaveRequest request) {
        FinanceSupplierTransaction entity = financeSupplierTransactionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("供应商流水不存在");
        }

        entity.setTransactionNo(request.getTransactionNo());
        entity.setSupplier(request.getSupplier());
        entity.setBillNo(request.getBillNo());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setWaybillSales(request.getWaybillSales());
        entity.setBillOfLadingNo(request.getBillOfLadingNo());
        entity.setTransferNo(request.getTransferNo());
        entity.setFeeType(request.getFeeType());
        entity.setQuantity(request.getQuantity());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setWriteOffStatus(request.getWriteOffStatus());
        entity.setCustomFlag(request.getCustomFlag());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSupplierTransactionMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除供应商流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceSupplierTransaction entity = financeSupplierTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("供应商流水不存在");
        }
        financeSupplierTransactionMapper.deleteById(id);
    }

    /**
     * 根据ID查询供应商流水
     */
    public FinanceSupplierTransactionView getById(Long id) {
        FinanceSupplierTransaction entity = financeSupplierTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("供应商流水不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询供应商流水列表
     */
    public IPage<FinanceSupplierTransactionView> page(FinanceSupplierTransactionQueryRequest request) {
        Page<FinanceSupplierTransaction> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceSupplierTransaction> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceSupplierTransaction> resultPage = financeSupplierTransactionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询供应商流水列表
     */
    public List<FinanceSupplierTransactionView> list(FinanceSupplierTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSupplierTransaction> queryWrapper = buildQueryWrapper(request);
        List<FinanceSupplierTransaction> list = financeSupplierTransactionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导入供应商流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceSupplierTransactionExcelDTO> dataList, String tenantId) {
        List<FinanceSupplierTransaction> entityList = dataList.stream()
                .map(dto -> convertToEntity(dto, tenantId))
                .collect(Collectors.toList());
        
        for (FinanceSupplierTransaction entity : entityList) {
            financeSupplierTransactionMapper.insert(entity);
        }
    }

    /**
     * 导出供应商流水
     */
    public List<FinanceSupplierTransaction> exportExcel(FinanceSupplierTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSupplierTransaction> queryWrapper = buildQueryWrapper(request);
        return financeSupplierTransactionMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceSupplierTransaction> buildQueryWrapper(FinanceSupplierTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSupplierTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceSupplierTransaction::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getTransactionNo())) {
            queryWrapper.like(FinanceSupplierTransaction::getTransactionNo, request.getTransactionNo());
        }
        if (StringUtils.hasText(request.getSupplier())) {
            queryWrapper.like(FinanceSupplierTransaction::getSupplier, request.getSupplier());
        }
        if (StringUtils.hasText(request.getBillNo())) {
            queryWrapper.like(FinanceSupplierTransaction::getBillNo, request.getBillNo());
        }
        if (StringUtils.hasText(request.getWaybillNo())) {
            queryWrapper.like(FinanceSupplierTransaction::getWaybillNo, request.getWaybillNo());
        }
        if (StringUtils.hasText(request.getWaybillSales())) {
            queryWrapper.like(FinanceSupplierTransaction::getWaybillSales, request.getWaybillSales());
        }
        if (StringUtils.hasText(request.getBillOfLadingNo())) {
            queryWrapper.like(FinanceSupplierTransaction::getBillOfLadingNo, request.getBillOfLadingNo());
        }
        if (StringUtils.hasText(request.getTransferNo())) {
            queryWrapper.like(FinanceSupplierTransaction::getTransferNo, request.getTransferNo());
        }
        if (StringUtils.hasText(request.getFeeType())) {
            queryWrapper.like(FinanceSupplierTransaction::getFeeType, request.getFeeType());
        }
        if (request.getAuditStatus() != null) {
            queryWrapper.eq(FinanceSupplierTransaction::getAuditStatus, request.getAuditStatus());
        }
        if (request.getWriteOffStatus() != null) {
            queryWrapper.eq(FinanceSupplierTransaction::getWriteOffStatus, request.getWriteOffStatus());
        }
        if (StringUtils.hasText(request.getCustomFlag())) {
            queryWrapper.like(FinanceSupplierTransaction::getCustomFlag, request.getCustomFlag());
        }
        if (request.getBusinessTimeStart() != null) {
            queryWrapper.ge(FinanceSupplierTransaction::getBusinessTime, request.getBusinessTimeStart());
        }
        if (request.getBusinessTimeEnd() != null) {
            queryWrapper.le(FinanceSupplierTransaction::getBusinessTime, request.getBusinessTimeEnd());
        }

        queryWrapper.orderByDesc(FinanceSupplierTransaction::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceSupplierTransactionView convertToView(FinanceSupplierTransaction entity) {
        FinanceSupplierTransactionView view = new FinanceSupplierTransactionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTransactionNo(entity.getTransactionNo());
        view.setSupplier(entity.getSupplier());
        view.setBillNo(entity.getBillNo());
        view.setWaybillNo(entity.getWaybillNo());
        view.setWaybillSales(entity.getWaybillSales());
        view.setBillOfLadingNo(entity.getBillOfLadingNo());
        view.setTransferNo(entity.getTransferNo());
        view.setFeeType(entity.getFeeType());
        view.setQuantity(entity.getQuantity());
        view.setFee(entity.getFee());
        view.setExchangeRate(entity.getExchangeRate());
        view.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        view.setAuditStatus(entity.getAuditStatus());
        view.setWriteOffStatus(entity.getWriteOffStatus());
        view.setCustomFlag(entity.getCustomFlag());
        view.setWriteOffTime(entity.getWriteOffTime());
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
    private FinanceSupplierTransaction convertToEntity(FinanceSupplierTransactionExcelDTO dto, String tenantId) {
        FinanceSupplierTransaction entity = new FinanceSupplierTransaction();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setTransactionNo(dto.getTransactionNo());
        entity.setSupplier(dto.getSupplier());
        entity.setBillNo(dto.getBillNo());
        entity.setWaybillNo(dto.getWaybillNo());
        entity.setWaybillSales(dto.getWaybillSales());
        entity.setBillOfLadingNo(dto.getBillOfLadingNo());
        entity.setTransferNo(dto.getTransferNo());
        entity.setFeeType(dto.getFeeType());
        entity.setQuantity(dto.getQuantity());
        entity.setFee(dto.getFee());
        entity.setExchangeRate(dto.getExchangeRate());
        entity.setLocalCurrencyFee(dto.getLocalCurrencyFee());
        entity.setAuditStatus(parseAuditStatus(dto.getAuditStatusText()));
        entity.setWriteOffStatus(parseWriteOffStatus(dto.getWriteOffStatusText()));
        entity.setCustomFlag(dto.getCustomFlag());
        entity.setWriteOffTime(dto.getWriteOffTime());
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
     * 解析核销状态文本
     */
    private Integer parseWriteOffStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        switch (statusText.trim()) {
            case "待核销":
                return 1;
            case "已核销":
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

    /**
     * 获取核销状态文本
     */
    public static String getWriteOffStatusText(Integer writeOffStatus) {
        if (writeOffStatus == null) {
            return "";
        }
        switch (writeOffStatus) {
            case 1:
                return "待核销";
            case 2:
                return "已核销";
            default:
                return "";
        }
    }
}
