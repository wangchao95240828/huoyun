package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceMonthlyStatementQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceMonthlyStatementSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceMonthlyStatementView;
import com.xqt.saas.finance.entity.FinanceMonthlyStatement;
import com.xqt.saas.finance.mapper.FinanceMonthlyStatementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceMonthlyStatementService extends ServiceImpl<FinanceMonthlyStatementMapper, FinanceMonthlyStatement> {

    private final FinanceMonthlyStatementMapper financeMonthlyStatementMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceMonthlyStatementView save(FinanceMonthlyStatementSaveRequest request) {
        FinanceMonthlyStatement entity = new FinanceMonthlyStatement();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setReceivable(request.getReceivable());
        entity.setPayable(request.getPayable());
        entity.setSalesCost(request.getSalesCost());
        entity.setWaybill(request.getWaybill());
        entity.setBillOfLading(request.getBillOfLading());
        entity.setRemark(request.getRemark());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeMonthlyStatementMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceMonthlyStatementView update(FinanceMonthlyStatementSaveRequest request) {
        FinanceMonthlyStatement entity = financeMonthlyStatementMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("月结单不存在");
        }

        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setReceivable(request.getReceivable());
        entity.setPayable(request.getPayable());
        entity.setSalesCost(request.getSalesCost());
        entity.setWaybill(request.getWaybill());
        entity.setBillOfLading(request.getBillOfLading());
        entity.setRemark(request.getRemark());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeMonthlyStatementMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceMonthlyStatement entity = financeMonthlyStatementMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("月结单不存在");
        }
        financeMonthlyStatementMapper.deleteById(id);
    }

    public FinanceMonthlyStatementView getById(Long id) {
        FinanceMonthlyStatement entity = financeMonthlyStatementMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("月结单不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinanceMonthlyStatementView> page(FinanceMonthlyStatementQueryRequest request, Long pageNum, Long pageSize) {
        Page<FinanceMonthlyStatement> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FinanceMonthlyStatement> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceMonthlyStatement> resultPage = financeMonthlyStatementMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinanceMonthlyStatementView> list(FinanceMonthlyStatementQueryRequest request) {
        LambdaQueryWrapper<FinanceMonthlyStatement> queryWrapper = buildQueryWrapper(request);
        List<FinanceMonthlyStatement> list = financeMonthlyStatementMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<FinanceMonthlyStatement> buildQueryWrapper(FinanceMonthlyStatementQueryRequest request) {
        LambdaQueryWrapper<FinanceMonthlyStatement> queryWrapper = new LambdaQueryWrapper<>();
        if (request.getStartTime() != null) {
            queryWrapper.ge(FinanceMonthlyStatement::getStartTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            queryWrapper.le(FinanceMonthlyStatement::getEndTime, request.getEndTime());
        }
        if (request.getReceivable() != null) {
            queryWrapper.eq(FinanceMonthlyStatement::getReceivable, request.getReceivable());
        }
        if (request.getPayable() != null) {
            queryWrapper.eq(FinanceMonthlyStatement::getPayable, request.getPayable());
        }
        if (request.getSalesCost() != null) {
            queryWrapper.eq(FinanceMonthlyStatement::getSalesCost, request.getSalesCost());
        }
        if (request.getWaybill() != null) {
            queryWrapper.eq(FinanceMonthlyStatement::getWaybill, request.getWaybill());
        }
        if (request.getBillOfLading() != null) {
            queryWrapper.eq(FinanceMonthlyStatement::getBillOfLading, request.getBillOfLading());
        }
        if (StringUtils.hasText(request.getRemark())) {
            queryWrapper.like(FinanceMonthlyStatement::getRemark, request.getRemark());
        }
        queryWrapper.orderByDesc(FinanceMonthlyStatement::getCreateTime);
        return queryWrapper;
    }

    private FinanceMonthlyStatementView convertToView(FinanceMonthlyStatement entity) {
        FinanceMonthlyStatementView view = new FinanceMonthlyStatementView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setStartTime(entity.getStartTime());
        view.setEndTime(entity.getEndTime());
        view.setReceivable(entity.getReceivable());
        view.setPayable(entity.getPayable());
        view.setSalesCost(entity.getSalesCost());
        view.setWaybill(entity.getWaybill());
        view.setBillOfLading(entity.getBillOfLading());
        view.setRemark(entity.getRemark());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}