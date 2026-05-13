package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceCustomerTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceCustomerTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceCustomerTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCustomerTransactionView;
import com.xqt.saas.finance.entity.FinanceCustomerTransaction;
import com.xqt.saas.finance.mapper.FinanceCustomerTransactionMapper;
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
 * 客户流水服务类
 * 提供客户流水的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceCustomerTransactionService extends ServiceImpl<FinanceCustomerTransactionMapper, FinanceCustomerTransaction> {

    private final FinanceCustomerTransactionMapper financeCustomerTransactionMapper;

    /**
     * 保存客户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerTransactionView save(FinanceCustomerTransactionSaveRequest request) {
        FinanceCustomerTransaction entity = new FinanceCustomerTransaction();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setTransactionNo(request.getTransactionNo());
        entity.setUserName(request.getUserName());
        entity.setUserSalesRep(request.getUserSalesRep());
        entity.setBillNo(request.getBillNo());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setBillOfLadingNo(request.getBillOfLadingNo());
        entity.setTransferNo(request.getTransferNo());
        entity.setOrderNo(request.getOrderNo());
        entity.setService(request.getService());
        entity.setFeeType(request.getFeeType());
        entity.setQuantity(request.getQuantity());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setWriteOffStatus(request.getWriteOffStatus());
        entity.setApprovalStatus(request.getApprovalStatus());
        entity.setApprover(request.getApprover());
        entity.setCustomFlag(request.getCustomFlag());
        entity.setRemark(request.getRemark());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeCustomerTransactionMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新客户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerTransactionView update(FinanceCustomerTransactionSaveRequest request) {
        FinanceCustomerTransaction entity = financeCustomerTransactionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("客户流水不存在");
        }

        entity.setTransactionNo(request.getTransactionNo());
        entity.setUserName(request.getUserName());
        entity.setUserSalesRep(request.getUserSalesRep());
        entity.setBillNo(request.getBillNo());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setBillOfLadingNo(request.getBillOfLadingNo());
        entity.setTransferNo(request.getTransferNo());
        entity.setOrderNo(request.getOrderNo());
        entity.setService(request.getService());
        entity.setFeeType(request.getFeeType());
        entity.setQuantity(request.getQuantity());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setWriteOffStatus(request.getWriteOffStatus());
        entity.setApprovalStatus(request.getApprovalStatus());
        entity.setApprover(request.getApprover());
        entity.setCustomFlag(request.getCustomFlag());
        entity.setRemark(request.getRemark());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeCustomerTransactionMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除客户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceCustomerTransaction entity = financeCustomerTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("客户流水不存在");
        }
        financeCustomerTransactionMapper.deleteById(id);
    }

    /**
     * 根据ID查询客户流水
     */
    public FinanceCustomerTransactionView getById(Long id) {
        FinanceCustomerTransaction entity = financeCustomerTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("客户流水不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询客户流水列表
     */
    public IPage<FinanceCustomerTransactionView> page(FinanceCustomerTransactionQueryRequest request) {
        Page<FinanceCustomerTransaction> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceCustomerTransaction> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceCustomerTransaction> resultPage = financeCustomerTransactionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询客户流水列表
     */
    public List<FinanceCustomerTransactionView> list(FinanceCustomerTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceCustomerTransaction> queryWrapper = buildQueryWrapper(request);
        List<FinanceCustomerTransaction> list = financeCustomerTransactionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导入客户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceCustomerTransactionExcelDTO> dataList, String tenantId) {
        List<FinanceCustomerTransaction> entityList = dataList.stream()
                .map(dto -> convertToEntity(dto, tenantId))
                .collect(Collectors.toList());
        
        for (FinanceCustomerTransaction entity : entityList) {
            financeCustomerTransactionMapper.insert(entity);
        }
    }

    /**
     * 导出客户流水
     */
    public List<FinanceCustomerTransaction> exportExcel(FinanceCustomerTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceCustomerTransaction> queryWrapper = buildQueryWrapper(request);
        return financeCustomerTransactionMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceCustomerTransaction> buildQueryWrapper(FinanceCustomerTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceCustomerTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceCustomerTransaction::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getTransactionNo())) {
            queryWrapper.like(FinanceCustomerTransaction::getTransactionNo, request.getTransactionNo());
        }
        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(FinanceCustomerTransaction::getUserName, request.getUserName());
        }
        if (StringUtils.hasText(request.getUserSalesRep())) {
            queryWrapper.like(FinanceCustomerTransaction::getUserSalesRep, request.getUserSalesRep());
        }
        if (StringUtils.hasText(request.getBillNo())) {
            queryWrapper.like(FinanceCustomerTransaction::getBillNo, request.getBillNo());
        }
        if (StringUtils.hasText(request.getWaybillNo())) {
            queryWrapper.like(FinanceCustomerTransaction::getWaybillNo, request.getWaybillNo());
        }
        if (StringUtils.hasText(request.getBillOfLadingNo())) {
            queryWrapper.like(FinanceCustomerTransaction::getBillOfLadingNo, request.getBillOfLadingNo());
        }
        if (StringUtils.hasText(request.getTransferNo())) {
            queryWrapper.like(FinanceCustomerTransaction::getTransferNo, request.getTransferNo());
        }
        if (StringUtils.hasText(request.getOrderNo())) {
            queryWrapper.like(FinanceCustomerTransaction::getOrderNo, request.getOrderNo());
        }
        if (StringUtils.hasText(request.getService())) {
            queryWrapper.like(FinanceCustomerTransaction::getService, request.getService());
        }
        if (StringUtils.hasText(request.getFeeType())) {
            queryWrapper.like(FinanceCustomerTransaction::getFeeType, request.getFeeType());
        }
        if (request.getAuditStatus() != null) {
            queryWrapper.eq(FinanceCustomerTransaction::getAuditStatus, request.getAuditStatus());
        }
        if (request.getWriteOffStatus() != null) {
            queryWrapper.eq(FinanceCustomerTransaction::getWriteOffStatus, request.getWriteOffStatus());
        }
        if (request.getApprovalStatus() != null) {
            queryWrapper.eq(FinanceCustomerTransaction::getApprovalStatus, request.getApprovalStatus());
        }
        if (StringUtils.hasText(request.getApprover())) {
            queryWrapper.like(FinanceCustomerTransaction::getApprover, request.getApprover());
        }
        if (StringUtils.hasText(request.getCustomFlag())) {
            queryWrapper.like(FinanceCustomerTransaction::getCustomFlag, request.getCustomFlag());
        }
        if (request.getBusinessTimeStart() != null) {
            queryWrapper.ge(FinanceCustomerTransaction::getBusinessTime, request.getBusinessTimeStart());
        }
        if (request.getBusinessTimeEnd() != null) {
            queryWrapper.le(FinanceCustomerTransaction::getBusinessTime, request.getBusinessTimeEnd());
        }

        queryWrapper.orderByDesc(FinanceCustomerTransaction::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceCustomerTransactionView convertToView(FinanceCustomerTransaction entity) {
        FinanceCustomerTransactionView view = new FinanceCustomerTransactionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTransactionNo(entity.getTransactionNo());
        view.setUserName(entity.getUserName());
        view.setUserSalesRep(entity.getUserSalesRep());
        view.setBillNo(entity.getBillNo());
        view.setWaybillNo(entity.getWaybillNo());
        view.setBillOfLadingNo(entity.getBillOfLadingNo());
        view.setTransferNo(entity.getTransferNo());
        view.setOrderNo(entity.getOrderNo());
        view.setService(entity.getService());
        view.setFeeType(entity.getFeeType());
        view.setQuantity(entity.getQuantity());
        view.setFee(entity.getFee());
        view.setExchangeRate(entity.getExchangeRate());
        view.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        view.setAuditStatus(entity.getAuditStatus());
        view.setWriteOffStatus(entity.getWriteOffStatus());
        view.setApprovalStatus(entity.getApprovalStatus());
        view.setApprover(entity.getApprover());
        view.setCustomFlag(entity.getCustomFlag());
        view.setRemark(entity.getRemark());
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
    private FinanceCustomerTransaction convertToEntity(FinanceCustomerTransactionExcelDTO dto, String tenantId) {
        FinanceCustomerTransaction entity = new FinanceCustomerTransaction();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setTransactionNo(dto.getTransactionNo());
        entity.setUserName(dto.getUserName());
        entity.setUserSalesRep(dto.getUserSalesRep());
        entity.setBillNo(dto.getBillNo());
        entity.setWaybillNo(dto.getWaybillNo());
        entity.setBillOfLadingNo(dto.getBillOfLadingNo());
        entity.setTransferNo(dto.getTransferNo());
        entity.setOrderNo(dto.getOrderNo());
        entity.setService(dto.getService());
        entity.setFeeType(dto.getFeeType());
        entity.setQuantity(dto.getQuantity());
        entity.setFee(dto.getFee());
        entity.setExchangeRate(dto.getExchangeRate());
        entity.setLocalCurrencyFee(dto.getLocalCurrencyFee());
        entity.setAuditStatus(parseAuditStatus(dto.getAuditStatusText()));
        entity.setWriteOffStatus(parseWriteOffStatus(dto.getWriteOffStatusText()));
        entity.setApprovalStatus(parseApprovalStatus(dto.getApprovalStatusText()));
        entity.setApprover(dto.getApprover());
        entity.setCustomFlag(dto.getCustomFlag());
        entity.setRemark(dto.getRemark());
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
     * 解析审批状态文本
     */
    private Integer parseApprovalStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        switch (statusText.trim()) {
            case "待审批":
                return 1;
            case "已审批":
                return 2;
            case "已拒绝":
                return 3;
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

    /**
     * 获取审批状态文本
     */
    public static String getApprovalStatusText(Integer approvalStatus) {
        if (approvalStatus == null) {
            return "";
        }
        switch (approvalStatus) {
            case 1:
                return "待审批";
            case 2:
                return "已审批";
            case 3:
                return "已拒绝";
            default:
                return "";
        }
    }
}
