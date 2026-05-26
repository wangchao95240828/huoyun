package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSalesCommissionTransactionView;
import com.xqt.saas.finance.entity.FinanceSalesCommissionTransaction;
import com.xqt.saas.finance.mapper.FinanceSalesCommissionTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 销售提成流水服务类
 * 提供销售提成流水的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceSalesCommissionTransactionService extends ServiceImpl<FinanceSalesCommissionTransactionMapper, FinanceSalesCommissionTransaction> {

    private final FinanceSalesCommissionTransactionMapper financeSalesCommissionTransactionMapper;

    /**
     * 保存销售提成流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSalesCommissionTransactionView save(FinanceSalesCommissionTransactionSaveRequest request) {
        FinanceSalesCommissionTransaction entity = new FinanceSalesCommissionTransaction();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setTransactionNo(request.getTransactionNo());
        entity.setSales(request.getSales());
        entity.setCommissionNo(request.getCommissionNo());
        entity.setWaybillNo(request.getWaybillNo());
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

        financeSalesCommissionTransactionMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新销售提成流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSalesCommissionTransactionView update(FinanceSalesCommissionTransactionSaveRequest request) {
        FinanceSalesCommissionTransaction entity = financeSalesCommissionTransactionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("销售提成流水不存在");
        }

        entity.setTransactionNo(request.getTransactionNo());
        entity.setSales(request.getSales());
        entity.setCommissionNo(request.getCommissionNo());
        entity.setWaybillNo(request.getWaybillNo());
        entity.setFeeType(request.getFeeType());
        entity.setFee(request.getFee());
        entity.setExchangeRate(request.getExchangeRate());
        entity.setLocalCurrencyFee(request.getLocalCurrencyFee());
        entity.setAuditStatus(request.getAuditStatus());
        entity.setRemark(request.getRemark());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSalesCommissionTransactionMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除销售提成流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceSalesCommissionTransaction entity = financeSalesCommissionTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("销售提成流水不存在");
        }
        financeSalesCommissionTransactionMapper.deleteById(id);
    }

    /**
     * 根据ID查询销售提成流水
     */
    public FinanceSalesCommissionTransactionView getById(Long id) {
        FinanceSalesCommissionTransaction entity = financeSalesCommissionTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("销售提成流水不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询销售提成流水列表
     */
    public IPage<FinanceSalesCommissionTransactionView> page(FinanceSalesCommissionTransactionQueryRequest request) {
        Page<FinanceSalesCommissionTransaction> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceSalesCommissionTransaction> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceSalesCommissionTransaction> resultPage = financeSalesCommissionTransactionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询销售提成流水列表
     */
    public List<FinanceSalesCommissionTransactionView> list(FinanceSalesCommissionTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCommissionTransaction> queryWrapper = buildQueryWrapper(request);
        List<FinanceSalesCommissionTransaction> list = financeSalesCommissionTransactionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导出销售提成流水
     */
    public List<FinanceSalesCommissionTransaction> exportExcel(FinanceSalesCommissionTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCommissionTransaction> queryWrapper = buildQueryWrapper(request);
        return financeSalesCommissionTransactionMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceSalesCommissionTransaction> buildQueryWrapper(FinanceSalesCommissionTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCommissionTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceSalesCommissionTransaction::getTenantId, TenantUtils.currentUuid());

        if (StringUtils.hasText(request.getTransactionNo())) {
            queryWrapper.like(FinanceSalesCommissionTransaction::getTransactionNo, request.getTransactionNo());
        }
        if (StringUtils.hasText(request.getSales())) {
            queryWrapper.like(FinanceSalesCommissionTransaction::getSales, request.getSales());
        }
        if (StringUtils.hasText(request.getCommissionNo())) {
            queryWrapper.like(FinanceSalesCommissionTransaction::getCommissionNo, request.getCommissionNo());
        }
        if (StringUtils.hasText(request.getWaybillNo())) {
            queryWrapper.like(FinanceSalesCommissionTransaction::getWaybillNo, request.getWaybillNo());
        }
        if (StringUtils.hasText(request.getFeeType())) {
            queryWrapper.like(FinanceSalesCommissionTransaction::getFeeType, request.getFeeType());
        }
        if (request.getAuditStatus() != null) {
            queryWrapper.eq(FinanceSalesCommissionTransaction::getAuditStatus, request.getAuditStatus());
        }
        if (StringUtils.hasText(request.getRemark())) {
            queryWrapper.like(FinanceSalesCommissionTransaction::getRemark, request.getRemark());
        }
        if (request.getBusinessTimeStart() != null) {
            queryWrapper.ge(FinanceSalesCommissionTransaction::getBusinessTime, request.getBusinessTimeStart());
        }
        if (request.getBusinessTimeEnd() != null) {
            queryWrapper.le(FinanceSalesCommissionTransaction::getBusinessTime, request.getBusinessTimeEnd());
        }

        queryWrapper.orderByDesc(FinanceSalesCommissionTransaction::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceSalesCommissionTransactionView convertToView(FinanceSalesCommissionTransaction entity) {
        FinanceSalesCommissionTransactionView view = new FinanceSalesCommissionTransactionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTransactionNo(entity.getTransactionNo());
        view.setSales(entity.getSales());
        view.setCommissionNo(entity.getCommissionNo());
        view.setWaybillNo(entity.getWaybillNo());
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
