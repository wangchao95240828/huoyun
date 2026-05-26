package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.excel.FinanceTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceTransactionView;
import com.xqt.saas.finance.entity.FinanceTransaction;
import com.xqt.saas.finance.mapper.FinanceTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 财务流水服务类
 * 提供财务流水的CRUD操作和导入导出功能
 */
@Service
@RequiredArgsConstructor
public class FinanceTransactionService extends ServiceImpl<FinanceTransactionMapper, FinanceTransaction> {

    private final FinanceTransactionMapper financeTransactionMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceTransactionView save(FinanceTransactionSaveRequest request) {
        FinanceTransaction entity = new FinanceTransaction();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setTransactionNo(request.getTransactionNo());
        entity.setUserName(request.getUserName());
        entity.setCompanyAccount(request.getCompanyAccount());
        entity.setUserAccount(request.getUserAccount());
        entity.setCurrency(request.getCurrency());
        entity.setAmount(request.getAmount());
        entity.setFee(request.getFee());
        entity.setType(request.getType());
        entity.setAuditTransactionNo(request.getAuditTransactionNo());
        entity.setPaymentStatus(request.getPaymentStatus());
        entity.setInvoiced(request.getInvoiced());
        entity.setBillNo(request.getBillNo());
        entity.setAuditTime(request.getAuditTime());
        entity.setPaymentTime(request.getPaymentTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeTransactionMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceTransactionView update(FinanceTransactionSaveRequest request) {
        FinanceTransaction entity = financeTransactionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("财务流水不存在");
        }

        entity.setTransactionNo(request.getTransactionNo());
        entity.setUserName(request.getUserName());
        entity.setCompanyAccount(request.getCompanyAccount());
        entity.setUserAccount(request.getUserAccount());
        entity.setCurrency(request.getCurrency());
        entity.setAmount(request.getAmount());
        entity.setFee(request.getFee());
        entity.setType(request.getType());
        entity.setAuditTransactionNo(request.getAuditTransactionNo());
        entity.setPaymentStatus(request.getPaymentStatus());
        entity.setInvoiced(request.getInvoiced());
        entity.setBillNo(request.getBillNo());
        entity.setAuditTime(request.getAuditTime());
        entity.setPaymentTime(request.getPaymentTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeTransactionMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceTransaction entity = financeTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("财务流水不存在");
        }
        financeTransactionMapper.deleteById(id);
    }

    public FinanceTransactionView getById(Long id) {
        FinanceTransaction entity = financeTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("财务流水不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinanceTransactionView> page(FinanceTransactionQueryRequest request) {
        Page<FinanceTransaction> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceTransaction> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceTransaction> resultPage = financeTransactionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinanceTransactionView> list(FinanceTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceTransaction> queryWrapper = buildQueryWrapper(request);
        List<FinanceTransaction> list = financeTransactionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceTransactionExcelDTO> dataList, String tenantId) {
        List<FinanceTransaction> entityList = dataList.stream()
                .map(dto -> convertToEntity(dto, tenantId))
                .collect(Collectors.toList());
        
        for (FinanceTransaction entity : entityList) {
            financeTransactionMapper.insert(entity);
        }
    }

    public List<FinanceTransaction> exportExcel(FinanceTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceTransaction> queryWrapper = buildQueryWrapper(request);
        return financeTransactionMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<FinanceTransaction> buildQueryWrapper(FinanceTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceTransaction::getTenantId, TenantUtils.currentUuid());

        if (StringUtils.hasText(request.getTransactionNo())) {
            queryWrapper.like(FinanceTransaction::getTransactionNo, request.getTransactionNo());
        }
        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(FinanceTransaction::getUserName, request.getUserName());
        }
        if (StringUtils.hasText(request.getCompanyAccount())) {
            queryWrapper.like(FinanceTransaction::getCompanyAccount, request.getCompanyAccount());
        }
        if (StringUtils.hasText(request.getUserAccount())) {
            queryWrapper.like(FinanceTransaction::getUserAccount, request.getUserAccount());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceTransaction::getCurrency, request.getCurrency());
        }
        if (request.getType() != null) {
            queryWrapper.eq(FinanceTransaction::getType, request.getType());
        }
        if (request.getPaymentStatus() != null) {
            queryWrapper.eq(FinanceTransaction::getPaymentStatus, request.getPaymentStatus());
        }
        if (request.getInvoiced() != null) {
            queryWrapper.eq(FinanceTransaction::getInvoiced, request.getInvoiced());
        }
        if (StringUtils.hasText(request.getBillNo())) {
            queryWrapper.like(FinanceTransaction::getBillNo, request.getBillNo());
        }
        if (request.getAuditTimeStart() != null) {
            queryWrapper.ge(FinanceTransaction::getAuditTime, request.getAuditTimeStart());
        }
        if (request.getAuditTimeEnd() != null) {
            queryWrapper.le(FinanceTransaction::getAuditTime, request.getAuditTimeEnd());
        }

        queryWrapper.orderByDesc(FinanceTransaction::getCreateTime);
        return queryWrapper;
    }

    private FinanceTransactionView convertToView(FinanceTransaction entity) {
        FinanceTransactionView view = new FinanceTransactionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTransactionNo(entity.getTransactionNo());
        view.setUserName(entity.getUserName());
        view.setCompanyAccount(entity.getCompanyAccount());
        view.setUserAccount(entity.getUserAccount());
        view.setCurrency(entity.getCurrency());
        view.setAmount(entity.getAmount());
        view.setFee(entity.getFee());
        view.setType(entity.getType());
        view.setAuditTransactionNo(entity.getAuditTransactionNo());
        view.setPaymentStatus(entity.getPaymentStatus());
        view.setInvoiced(entity.getInvoiced());
        view.setBillNo(entity.getBillNo());
        view.setAuditTime(entity.getAuditTime());
        view.setPaymentTime(entity.getPaymentTime());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private FinanceTransaction convertToEntity(FinanceTransactionExcelDTO dto, String tenantId) {
        FinanceTransaction entity = new FinanceTransaction();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setTransactionNo(dto.getTransactionNo());
        entity.setUserName(dto.getUserName());
        entity.setCompanyAccount(dto.getCompanyAccount());
        entity.setUserAccount(dto.getUserAccount());
        entity.setCurrency(dto.getCurrency());
        entity.setAmount(dto.getAmount());
        entity.setFee(dto.getFee());
        entity.setType(parseType(dto.getTypeText()));
        entity.setAuditTransactionNo(dto.getAuditTransactionNo());
        entity.setPaymentStatus(parsePaymentStatus(dto.getPaymentStatusText()));
        entity.setInvoiced(parseInvoiced(dto.getInvoicedText()));
        entity.setBillNo(dto.getBillNo());
        entity.setAuditTime(dto.getAuditTime());
        entity.setPaymentTime(dto.getPaymentTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    private Integer parseType(String typeText) {
        if (typeText == null) {
            return null;
        }
        switch (typeText.trim()) {
            case "客户充值":
                return 1;
            case "客户提现":
                return 2;
            case "支付供应商":
                return 3;
            case "供应商退款":
                return 4;
            case "经营收入":
                return 5;
            case "经营支出":
                return 6;
            case "工资发放":
                return 7;
            case "提成发放":
                return 8;
            case "内部转账":
                return 9;
            default:
                return null;
        }
    }

    private Integer parsePaymentStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        switch (statusText.trim()) {
            case "待支付":
                return 1;
            case "已支付":
                return 2;
            case "支付失败":
                return 3;
            default:
                return null;
        }
    }

    private Boolean parseInvoiced(String invoicedText) {
        if (invoicedText == null) {
            return false;
        }
        return "是".equals(invoicedText.trim()) || "已开票".equals(invoicedText.trim());
    }

    public static String getTypeText(Integer type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case 1:
                return "客户充值";
            case 2:
                return "客户提现";
            case 3:
                return "支付供应商";
            case 4:
                return "供应商退款";
            case 5:
                return "经营收入";
            case 6:
                return "经营支出";
            case 7:
                return "工资发放";
            case 8:
                return "提成发放";
            case 9:
                return "内部转账";
            default:
                return "";
        }
    }

    public static String getPaymentStatusText(Integer paymentStatus) {
        if (paymentStatus == null) {
            return "";
        }
        switch (paymentStatus) {
            case 1:
                return "待支付";
            case 2:
                return "已支付";
            case 3:
                return "支付失败";
            default:
                return "";
        }
    }

    public static String getInvoicedText(Boolean invoiced) {
        return Boolean.TRUE.equals(invoiced) ? "是" : "否";
    }
}
