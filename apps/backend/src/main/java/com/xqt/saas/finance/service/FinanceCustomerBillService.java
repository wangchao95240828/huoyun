package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceCustomerBillExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceCustomerBillQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceCustomerBillSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCustomerBillView;
import com.xqt.saas.finance.entity.FinanceCustomerBill;
import com.xqt.saas.finance.mapper.FinanceCustomerBillMapper;
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
 * 客户账单服务类
 * 提供客户账单的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceCustomerBillService extends ServiceImpl<FinanceCustomerBillMapper, FinanceCustomerBill> {

    private final FinanceCustomerBillMapper financeCustomerBillMapper;

    /**
     * 保存客户账单
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerBillView save(FinanceCustomerBillSaveRequest request) {
        FinanceCustomerBill entity = new FinanceCustomerBill();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setBillNo(request.getBillNo());
        entity.setUserSettle(request.getUserSettle());
        entity.setBranchCompany(request.getBranchCompany());
        entity.setCurrency(request.getCurrency());
        entity.setBillAmount(request.getBillAmount());
        entity.setPaidAmount(request.getPaidAmount());
        entity.setRemainingAmount(request.getRemainingAmount());
        entity.setSalesRep(request.getSalesRep());
        entity.setCustomerServiceRep(request.getCustomerServiceRep());
        entity.setFinanceRep(request.getFinanceRep());
        entity.setDueAction(request.getDueAction());
        entity.setBillConfirmed(request.getBillConfirmed());
        entity.setStatus(request.getStatus());
        entity.setBillDate(request.getBillDate());
        entity.setDueDate(request.getDueDate());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeCustomerBillMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新客户账单
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerBillView update(FinanceCustomerBillSaveRequest request) {
        FinanceCustomerBill entity = financeCustomerBillMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("客户账单不存在");
        }

        entity.setBillNo(request.getBillNo());
        entity.setUserSettle(request.getUserSettle());
        entity.setBranchCompany(request.getBranchCompany());
        entity.setCurrency(request.getCurrency());
        entity.setBillAmount(request.getBillAmount());
        entity.setPaidAmount(request.getPaidAmount());
        entity.setRemainingAmount(request.getRemainingAmount());
        entity.setSalesRep(request.getSalesRep());
        entity.setCustomerServiceRep(request.getCustomerServiceRep());
        entity.setFinanceRep(request.getFinanceRep());
        entity.setDueAction(request.getDueAction());
        entity.setBillConfirmed(request.getBillConfirmed());
        entity.setStatus(request.getStatus());
        entity.setBillDate(request.getBillDate());
        entity.setDueDate(request.getDueDate());
        entity.setWriteOffTime(request.getWriteOffTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeCustomerBillMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除客户账单
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceCustomerBill entity = financeCustomerBillMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("客户账单不存在");
        }
        financeCustomerBillMapper.deleteById(id);
    }

    /**
     * 根据ID查询客户账单
     */
    public FinanceCustomerBillView getById(Long id) {
        FinanceCustomerBill entity = financeCustomerBillMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("客户账单不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询客户账单列表
     */
    public IPage<FinanceCustomerBillView> page(FinanceCustomerBillQueryRequest request) {
        Page<FinanceCustomerBill> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceCustomerBill> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceCustomerBill> resultPage = financeCustomerBillMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询客户账单列表
     */
    public List<FinanceCustomerBillView> list(FinanceCustomerBillQueryRequest request) {
        LambdaQueryWrapper<FinanceCustomerBill> queryWrapper = buildQueryWrapper(request);
        List<FinanceCustomerBill> list = financeCustomerBillMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 导入客户账单
     */
    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceCustomerBillExcelDTO> dataList, String tenantId) {
        List<FinanceCustomerBill> entityList = dataList.stream()
                .map(dto -> convertToEntity(dto, tenantId))
                .collect(Collectors.toList());
        
        for (FinanceCustomerBill entity : entityList) {
            financeCustomerBillMapper.insert(entity);
        }
    }

    /**
     * 导出客户账单
     */
    public List<FinanceCustomerBill> exportExcel(FinanceCustomerBillQueryRequest request) {
        LambdaQueryWrapper<FinanceCustomerBill> queryWrapper = buildQueryWrapper(request);
        return financeCustomerBillMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceCustomerBill> buildQueryWrapper(FinanceCustomerBillQueryRequest request) {
        LambdaQueryWrapper<FinanceCustomerBill> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceCustomerBill::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getBillNo())) {
            queryWrapper.like(FinanceCustomerBill::getBillNo, request.getBillNo());
        }
        if (StringUtils.hasText(request.getUserSettle())) {
            queryWrapper.like(FinanceCustomerBill::getUserSettle, request.getUserSettle());
        }
        if (StringUtils.hasText(request.getBranchCompany())) {
            queryWrapper.like(FinanceCustomerBill::getBranchCompany, request.getBranchCompany());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceCustomerBill::getCurrency, request.getCurrency());
        }
        if (StringUtils.hasText(request.getSalesRep())) {
            queryWrapper.like(FinanceCustomerBill::getSalesRep, request.getSalesRep());
        }
        if (StringUtils.hasText(request.getCustomerServiceRep())) {
            queryWrapper.like(FinanceCustomerBill::getCustomerServiceRep, request.getCustomerServiceRep());
        }
        if (StringUtils.hasText(request.getFinanceRep())) {
            queryWrapper.like(FinanceCustomerBill::getFinanceRep, request.getFinanceRep());
        }
        if (StringUtils.hasText(request.getDueAction())) {
            queryWrapper.like(FinanceCustomerBill::getDueAction, request.getDueAction());
        }
        if (request.getBillConfirmed() != null) {
            queryWrapper.eq(FinanceCustomerBill::getBillConfirmed, request.getBillConfirmed());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(FinanceCustomerBill::getStatus, request.getStatus());
        }
        if (request.getBillDateStart() != null) {
            queryWrapper.ge(FinanceCustomerBill::getBillDate, request.getBillDateStart());
        }
        if (request.getBillDateEnd() != null) {
            queryWrapper.le(FinanceCustomerBill::getBillDate, request.getBillDateEnd());
        }
        if (request.getDueDateStart() != null) {
            queryWrapper.ge(FinanceCustomerBill::getDueDate, request.getDueDateStart());
        }
        if (request.getDueDateEnd() != null) {
            queryWrapper.le(FinanceCustomerBill::getDueDate, request.getDueDateEnd());
        }

        queryWrapper.orderByDesc(FinanceCustomerBill::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceCustomerBillView convertToView(FinanceCustomerBill entity) {
        FinanceCustomerBillView view = new FinanceCustomerBillView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setBillNo(entity.getBillNo());
        view.setUserSettle(entity.getUserSettle());
        view.setBranchCompany(entity.getBranchCompany());
        view.setCurrency(entity.getCurrency());
        view.setBillAmount(entity.getBillAmount());
        view.setPaidAmount(entity.getPaidAmount());
        view.setRemainingAmount(entity.getRemainingAmount());
        view.setSalesRep(entity.getSalesRep());
        view.setCustomerServiceRep(entity.getCustomerServiceRep());
        view.setFinanceRep(entity.getFinanceRep());
        view.setDueAction(entity.getDueAction());
        view.setBillConfirmed(entity.getBillConfirmed());
        view.setStatus(entity.getStatus());
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
     * 转换为实体对象（用于导入）
     */
    private FinanceCustomerBill convertToEntity(FinanceCustomerBillExcelDTO dto, String tenantId) {
        FinanceCustomerBill entity = new FinanceCustomerBill();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setBillNo(dto.getBillNo());
        entity.setUserSettle(dto.getUserSettle());
        entity.setBranchCompany(dto.getBranchCompany());
        entity.setCurrency(dto.getCurrency());
        entity.setBillAmount(dto.getBillAmount());
        entity.setPaidAmount(dto.getPaidAmount());
        entity.setRemainingAmount(dto.getRemainingAmount());
        entity.setSalesRep(dto.getSalesRep());
        entity.setCustomerServiceRep(dto.getCustomerServiceRep());
        entity.setFinanceRep(dto.getFinanceRep());
        entity.setDueAction(dto.getDueAction());
        entity.setBillConfirmed(parseBillConfirmed(dto.getBillConfirmedText()));
        entity.setStatus(parseStatus(dto.getStatusText()));
        entity.setBillDate(dto.getBillDate());
        entity.setDueDate(dto.getDueDate());
        entity.setWriteOffTime(dto.getWriteOffTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 解析账单确认文本
     */
    private Boolean parseBillConfirmed(String confirmedText) {
        if (confirmedText == null) {
            return false;
        }
        return "是".equals(confirmedText.trim()) || "确认".equals(confirmedText.trim()) || "true".equalsIgnoreCase(confirmedText.trim());
    }

    /**
     * 解析状态文本
     */
    private Integer parseStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        switch (statusText.trim()) {
            case "待审核":
                return 1;
            case "待核销":
                return 2;
            case "已核销":
                return 3;
            default:
                return null;
        }
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

    /**
     * 获取账单确认文本
     */
    public static String getBillConfirmedText(Boolean billConfirmed) {
        return Boolean.TRUE.equals(billConfirmed) ? "是" : "否";
    }
}
