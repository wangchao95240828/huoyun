package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.request.FinanceReceivableReportQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceReceivableReportSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceReceivableReportView;
import com.xqt.saas.finance.entity.FinanceReceivableReport;
import com.xqt.saas.finance.mapper.FinanceReceivableReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 应收报表服务类
 * 提供应收报表的CRUD操作和导出功能
 */
@Service
@RequiredArgsConstructor
public class FinanceReceivableReportService extends ServiceImpl<FinanceReceivableReportMapper, FinanceReceivableReport> {

    private final FinanceReceivableReportMapper financeReceivableReportMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceReceivableReportView save(FinanceReceivableReportSaveRequest request) {
        FinanceReceivableReport entity = new FinanceReceivableReport();
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
        entity.setActualAmount(request.getActualAmount());
        entity.setCustomerServiceRep(request.getCustomerServiceRep());
        entity.setSalesRep(request.getSalesRep());
        entity.setFinanceRep(request.getFinanceRep());
        entity.setSettlementMethod(request.getSettlementMethod());
        entity.setUserLevel(request.getUserLevel());
        entity.setUserRemark(request.getUserRemark());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeReceivableReportMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceReceivableReportView update(FinanceReceivableReportSaveRequest request) {
        FinanceReceivableReport entity = financeReceivableReportMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("应收报表不存在");
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
        entity.setActualAmount(request.getActualAmount());
        entity.setCustomerServiceRep(request.getCustomerServiceRep());
        entity.setSalesRep(request.getSalesRep());
        entity.setFinanceRep(request.getFinanceRep());
        entity.setSettlementMethod(request.getSettlementMethod());
        entity.setUserLevel(request.getUserLevel());
        entity.setUserRemark(request.getUserRemark());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeReceivableReportMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceReceivableReport entity = financeReceivableReportMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("应收报表不存在");
        }
        financeReceivableReportMapper.deleteById(id);
    }

    public FinanceReceivableReportView getById(Long id) {
        FinanceReceivableReport entity = financeReceivableReportMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("应收报表不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinanceReceivableReportView> page(FinanceReceivableReportQueryRequest request) {
        Page<FinanceReceivableReport> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceReceivableReport> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceReceivableReport> resultPage = financeReceivableReportMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinanceReceivableReportView> list(FinanceReceivableReportQueryRequest request) {
        LambdaQueryWrapper<FinanceReceivableReport> queryWrapper = buildQueryWrapper(request);
        List<FinanceReceivableReport> list = financeReceivableReportMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    public List<FinanceReceivableReport> exportExcel(FinanceReceivableReportQueryRequest request) {
        LambdaQueryWrapper<FinanceReceivableReport> queryWrapper = buildQueryWrapper(request);
        return financeReceivableReportMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<FinanceReceivableReport> buildQueryWrapper(FinanceReceivableReportQueryRequest request) {
        LambdaQueryWrapper<FinanceReceivableReport> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceReceivableReport::getTenantId, TenantUtils.currentUuid());

        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(FinanceReceivableReport::getUserName, request.getUserName());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceReceivableReport::getCurrency, request.getCurrency());
        }
        if (StringUtils.hasText(request.getUserLevel())) {
            queryWrapper.like(FinanceReceivableReport::getUserLevel, request.getUserLevel());
        }

        queryWrapper.orderByDesc(FinanceReceivableReport::getCreateTime);
        return queryWrapper;
    }

    private FinanceReceivableReportView convertToView(FinanceReceivableReport entity) {
        FinanceReceivableReportView view = new FinanceReceivableReportView();
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
        view.setActualAmount(entity.getActualAmount());
        view.setCustomerServiceRep(entity.getCustomerServiceRep());
        view.setSalesRep(entity.getSalesRep());
        view.setFinanceRep(entity.getFinanceRep());
        view.setSettlementMethod(entity.getSettlementMethod());
        view.setUserLevel(entity.getUserLevel());
        view.setUserRemark(entity.getUserRemark());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}
