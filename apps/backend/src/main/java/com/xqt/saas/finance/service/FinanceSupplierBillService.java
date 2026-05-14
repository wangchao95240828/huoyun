package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinanceSupplierBillQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSupplierBillSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSupplierBillView;
import com.xqt.saas.finance.entity.FinanceSupplierBill;
import com.xqt.saas.finance.mapper.FinanceSupplierBillMapper;
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
 * 供应商账单服务类
 * 提供供应商账单的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceSupplierBillService extends ServiceImpl<FinanceSupplierBillMapper, FinanceSupplierBill> {

    private final FinanceSupplierBillMapper financeSupplierBillMapper;

    /**
     * 保存供应商账单
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSupplierBillView save(FinanceSupplierBillSaveRequest request) {
        FinanceSupplierBill entity = new FinanceSupplierBill();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setBillNo(request.getBillNo());
        entity.setSupplier(request.getSupplier());
        entity.setCurrency(request.getCurrency());
        entity.setStatus(request.getStatus());
        entity.setTransactionTotal(request.getTransactionTotal());
        entity.setBillAmount(request.getBillAmount());
        entity.setCustomFlag(request.getCustomFlag());
        entity.setBillDate(request.getBillDate());
        entity.setDueDate(request.getDueDate());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSupplierBillMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新供应商账单
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSupplierBillView update(FinanceSupplierBillSaveRequest request) {
        FinanceSupplierBill entity = financeSupplierBillMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("供应商账单不存在");
        }

        entity.setBillNo(request.getBillNo());
        entity.setSupplier(request.getSupplier());
        entity.setCurrency(request.getCurrency());
        entity.setStatus(request.getStatus());
        entity.setTransactionTotal(request.getTransactionTotal());
        entity.setBillAmount(request.getBillAmount());
        entity.setCustomFlag(request.getCustomFlag());
        entity.setBillDate(request.getBillDate());
        entity.setDueDate(request.getDueDate());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSupplierBillMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除供应商账单
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceSupplierBill entity = financeSupplierBillMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("供应商账单不存在");
        }
        financeSupplierBillMapper.deleteById(id);
    }

    /**
     * 根据ID查询供应商账单
     */
    public FinanceSupplierBillView getById(Long id) {
        FinanceSupplierBill entity = financeSupplierBillMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("供应商账单不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询供应商账单列表
     */
    public IPage<FinanceSupplierBillView> page(FinanceSupplierBillQueryRequest request) {
        Page<FinanceSupplierBill> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceSupplierBill> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceSupplierBill> resultPage = financeSupplierBillMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询供应商账单列表
     */
    public List<FinanceSupplierBillView> list(FinanceSupplierBillQueryRequest request) {
        LambdaQueryWrapper<FinanceSupplierBill> queryWrapper = buildQueryWrapper(request);
        List<FinanceSupplierBill> list = financeSupplierBillMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导出供应商账单
     */
    public List<FinanceSupplierBill> exportExcel(FinanceSupplierBillQueryRequest request) {
        LambdaQueryWrapper<FinanceSupplierBill> queryWrapper = buildQueryWrapper(request);
        return financeSupplierBillMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceSupplierBill> buildQueryWrapper(FinanceSupplierBillQueryRequest request) {
        LambdaQueryWrapper<FinanceSupplierBill> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceSupplierBill::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getBillNo())) {
            queryWrapper.like(FinanceSupplierBill::getBillNo, request.getBillNo());
        }
        if (StringUtils.hasText(request.getSupplier())) {
            queryWrapper.like(FinanceSupplierBill::getSupplier, request.getSupplier());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceSupplierBill::getCurrency, request.getCurrency());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(FinanceSupplierBill::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getCustomFlag())) {
            queryWrapper.like(FinanceSupplierBill::getCustomFlag, request.getCustomFlag());
        }
        if (request.getBillDateStart() != null) {
            queryWrapper.ge(FinanceSupplierBill::getBillDate, request.getBillDateStart());
        }
        if (request.getBillDateEnd() != null) {
            queryWrapper.le(FinanceSupplierBill::getBillDate, request.getBillDateEnd());
        }
        if (request.getDueDateStart() != null) {
            queryWrapper.ge(FinanceSupplierBill::getDueDate, request.getDueDateStart());
        }
        if (request.getDueDateEnd() != null) {
            queryWrapper.le(FinanceSupplierBill::getDueDate, request.getDueDateEnd());
        }

        queryWrapper.orderByDesc(FinanceSupplierBill::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceSupplierBillView convertToView(FinanceSupplierBill entity) {
        FinanceSupplierBillView view = new FinanceSupplierBillView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setBillNo(entity.getBillNo());
        view.setSupplier(entity.getSupplier());
        view.setCurrency(entity.getCurrency());
        view.setStatus(entity.getStatus());
        view.setTransactionTotal(entity.getTransactionTotal());
        view.setBillAmount(entity.getBillAmount());
        view.setCustomFlag(entity.getCustomFlag());
        view.setBillDate(entity.getBillDate());
        view.setDueDate(entity.getDueDate());
        view.setWriteOffTime(entity.getWriteOffTime());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    /**
     * 获取状态文本
     */
    public static String getStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case 1:
                return "待审核";
            case 2:
                return "待核销";
            case 3:
                return "已核销";
            default:
                return "";
        }
    }
}
