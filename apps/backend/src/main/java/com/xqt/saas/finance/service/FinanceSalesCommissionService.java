package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSalesCommissionView;
import com.xqt.saas.finance.entity.FinanceSalesCommission;
import com.xqt.saas.finance.mapper.FinanceSalesCommissionMapper;
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
 * 销售提成单服务类
 * 提供销售提成单的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceSalesCommissionService extends ServiceImpl<FinanceSalesCommissionMapper, FinanceSalesCommission> {

    private final FinanceSalesCommissionMapper financeSalesCommissionMapper;

    /**
     * 保存销售提成单
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSalesCommissionView save(FinanceSalesCommissionSaveRequest request) {
        FinanceSalesCommission entity = new FinanceSalesCommission();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setCommissionNo(request.getCommissionNo());
        entity.setSales(request.getSales());
        entity.setCurrency(request.getCurrency());
        entity.setCommissionAmount(request.getCommissionAmount());
        entity.setStatus(request.getStatus());
        entity.setPaid(request.getPaid() != null ? request.getPaid() : BigDecimal.ZERO);
        entity.setBalance(request.getBalance() != null ? request.getBalance() : BigDecimal.ZERO);
        entity.setRemark(request.getRemark());
        entity.setBillDate(request.getBillDate());
        entity.setDueDate(request.getDueDate());
        entity.setAuditTime(request.getAuditTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSalesCommissionMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新销售提成单
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceSalesCommissionView update(FinanceSalesCommissionSaveRequest request) {
        FinanceSalesCommission entity = financeSalesCommissionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("销售提成单不存在");
        }

        entity.setCommissionNo(request.getCommissionNo());
        entity.setSales(request.getSales());
        entity.setCurrency(request.getCurrency());
        entity.setCommissionAmount(request.getCommissionAmount());
        entity.setStatus(request.getStatus());
        entity.setPaid(request.getPaid());
        entity.setBalance(request.getBalance());
        entity.setRemark(request.getRemark());
        entity.setBillDate(request.getBillDate());
        entity.setDueDate(request.getDueDate());
        entity.setAuditTime(request.getAuditTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeSalesCommissionMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除销售提成单
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceSalesCommission entity = financeSalesCommissionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("销售提成单不存在");
        }
        financeSalesCommissionMapper.deleteById(id);
    }

    /**
     * 根据ID查询销售提成单
     */
    public FinanceSalesCommissionView getById(Long id) {
        FinanceSalesCommission entity = financeSalesCommissionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("销售提成单不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询销售提成单列表
     */
    public IPage<FinanceSalesCommissionView> page(FinanceSalesCommissionQueryRequest request) {
        Page<FinanceSalesCommission> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceSalesCommission> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceSalesCommission> resultPage = financeSalesCommissionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询销售提成单列表
     */
    public List<FinanceSalesCommissionView> list(FinanceSalesCommissionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCommission> queryWrapper = buildQueryWrapper(request);
        List<FinanceSalesCommission> list = financeSalesCommissionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导出销售提成单
     */
    public List<FinanceSalesCommission> exportExcel(FinanceSalesCommissionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCommission> queryWrapper = buildQueryWrapper(request);
        return financeSalesCommissionMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceSalesCommission> buildQueryWrapper(FinanceSalesCommissionQueryRequest request) {
        LambdaQueryWrapper<FinanceSalesCommission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceSalesCommission::getTenantId, UUID.fromString(UserContext.getTenantId()));
        
        if (StringUtils.hasText(request.getCommissionNo())) {
            queryWrapper.like(FinanceSalesCommission::getCommissionNo, request.getCommissionNo());
        }
        if (StringUtils.hasText(request.getSales())) {
            queryWrapper.like(FinanceSalesCommission::getSales, request.getSales());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceSalesCommission::getCurrency, request.getCurrency());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(FinanceSalesCommission::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getRemark())) {
            queryWrapper.like(FinanceSalesCommission::getRemark, request.getRemark());
        }
        if (request.getBillDateStart() != null) {
            queryWrapper.ge(FinanceSalesCommission::getBillDate, request.getBillDateStart());
        }
        if (request.getBillDateEnd() != null) {
            queryWrapper.le(FinanceSalesCommission::getBillDate, request.getBillDateEnd());
        }
        if (request.getDueDateStart() != null) {
            queryWrapper.ge(FinanceSalesCommission::getDueDate, request.getDueDateStart());
        }
        if (request.getDueDateEnd() != null) {
            queryWrapper.le(FinanceSalesCommission::getDueDate, request.getDueDateEnd());
        }
        
        queryWrapper.orderByDesc(FinanceSalesCommission::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceSalesCommissionView convertToView(FinanceSalesCommission entity) {
        FinanceSalesCommissionView view = new FinanceSalesCommissionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setCommissionNo(entity.getCommissionNo());
        view.setSales(entity.getSales());
        view.setCurrency(entity.getCurrency());
        view.setCommissionAmount(entity.getCommissionAmount());
        view.setStatus(entity.getStatus());
        view.setPaid(entity.getPaid());
        view.setBalance(entity.getBalance());
        view.setRemark(entity.getRemark());
        view.setBillDate(entity.getBillDate());
        view.setDueDate(entity.getDueDate());
        view.setAuditTime(entity.getAuditTime());
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
                return "已审核";
            case 3:
                return "已发放";
            default:
                return "";
        }
    }
}
