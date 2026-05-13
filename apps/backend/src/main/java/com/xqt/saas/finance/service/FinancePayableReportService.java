package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.request.FinancePayableReportQueryRequest;
import com.xqt.saas.finance.dto.request.FinancePayableReportSaveRequest;
import com.xqt.saas.finance.dto.response.FinancePayableReportView;
import com.xqt.saas.finance.entity.FinancePayableReport;
import com.xqt.saas.finance.mapper.FinancePayableReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 应付报表服务类
 * 提供应付报表的CRUD操作和导出功能
 */
@Service
@RequiredArgsConstructor
public class FinancePayableReportService extends ServiceImpl<FinancePayableReportMapper, FinancePayableReport> {

    private final FinancePayableReportMapper financePayableReportMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinancePayableReportView save(FinancePayableReportSaveRequest request) {
        FinancePayableReport entity = new FinancePayableReport();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setUserName(request.getUserName());
        entity.setCurrency(request.getCurrency());
        entity.setTurnover(request.getTurnover());
        entity.setPaidAmount(request.getPaidAmount());
        entity.setPendingAmount(request.getPendingAmount());
        entity.setIssuedBill(request.getIssuedBill());
        entity.setPendingBill(request.getPendingBill());
        entity.setPaidBill(request.getPaidBill());
        entity.setPendingPaymentBill(request.getPendingPaymentBill());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financePayableReportMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinancePayableReportView update(FinancePayableReportSaveRequest request) {
        FinancePayableReport entity = financePayableReportMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("应付报表不存在");
        }

        entity.setUserName(request.getUserName());
        entity.setCurrency(request.getCurrency());
        entity.setTurnover(request.getTurnover());
        entity.setPaidAmount(request.getPaidAmount());
        entity.setPendingAmount(request.getPendingAmount());
        entity.setIssuedBill(request.getIssuedBill());
        entity.setPendingBill(request.getPendingBill());
        entity.setPaidBill(request.getPaidBill());
        entity.setPendingPaymentBill(request.getPendingPaymentBill());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financePayableReportMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinancePayableReport entity = financePayableReportMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("应付报表不存在");
        }
        financePayableReportMapper.deleteById(id);
    }

    public FinancePayableReportView getById(Long id) {
        FinancePayableReport entity = financePayableReportMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("应付报表不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinancePayableReportView> page(FinancePayableReportQueryRequest request) {
        Page<FinancePayableReport> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinancePayableReport> queryWrapper = buildQueryWrapper(request);

        IPage<FinancePayableReport> resultPage = financePayableReportMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinancePayableReportView> list(FinancePayableReportQueryRequest request) {
        LambdaQueryWrapper<FinancePayableReport> queryWrapper = buildQueryWrapper(request);
        List<FinancePayableReport> list = financePayableReportMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    public List<FinancePayableReport> exportExcel(FinancePayableReportQueryRequest request) {
        LambdaQueryWrapper<FinancePayableReport> queryWrapper = buildQueryWrapper(request);
        return financePayableReportMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<FinancePayableReport> buildQueryWrapper(FinancePayableReportQueryRequest request) {
        LambdaQueryWrapper<FinancePayableReport> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinancePayableReport::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(FinancePayableReport::getUserName, request.getUserName());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinancePayableReport::getCurrency, request.getCurrency());
        }

        queryWrapper.orderByDesc(FinancePayableReport::getCreateTime);
        return queryWrapper;
    }

    private FinancePayableReportView convertToView(FinancePayableReport entity) {
        FinancePayableReportView view = new FinancePayableReportView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setUserName(entity.getUserName());
        view.setCurrency(entity.getCurrency());
        view.setTurnover(entity.getTurnover());
        view.setPaidAmount(entity.getPaidAmount());
        view.setPendingAmount(entity.getPendingAmount());
        view.setIssuedBill(entity.getIssuedBill());
        view.setPendingBill(entity.getPendingBill());
        view.setPaidBill(entity.getPaidBill());
        view.setPendingPaymentBill(entity.getPendingPaymentBill());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}
