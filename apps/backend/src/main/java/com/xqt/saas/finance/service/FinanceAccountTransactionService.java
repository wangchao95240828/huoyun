package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceAccountTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceAccountTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceAccountTransactionView;
import com.xqt.saas.finance.entity.FinanceAccountTransaction;
import com.xqt.saas.finance.mapper.FinanceAccountTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 账户流水服务类
 * 提供账户流水的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceAccountTransactionService extends ServiceImpl<FinanceAccountTransactionMapper, FinanceAccountTransaction> {

    private final FinanceAccountTransactionMapper financeAccountTransactionMapper;

    /**
     * 保存账户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceAccountTransactionView save(FinanceAccountTransactionSaveRequest request) {
        FinanceAccountTransaction entity = new FinanceAccountTransaction();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setTransactionNo(request.getTransactionNo());
        entity.setAccountId(request.getAccountId());
        entity.setCustomerId(request.getCustomerId());
        entity.setTransactionType(request.getTransactionType());
        entity.setCurrency(request.getCurrency());
        entity.setAmount(request.getAmount());
        entity.setFee(request.getFee());
        entity.setCreditAmount(request.getCreditAmount());
        entity.setRemark(request.getRemark());
        entity.setPaymentTime(request.getPaymentTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeAccountTransactionMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新账户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceAccountTransactionView update(FinanceAccountTransactionSaveRequest request) {
        FinanceAccountTransaction entity = financeAccountTransactionMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("账户流水不存在");
        }

        entity.setTransactionNo(request.getTransactionNo());
        entity.setAccountId(request.getAccountId());
        entity.setCustomerId(request.getCustomerId());
        entity.setTransactionType(request.getTransactionType());
        entity.setCurrency(request.getCurrency());
        entity.setAmount(request.getAmount());
        entity.setFee(request.getFee());
        entity.setCreditAmount(request.getCreditAmount());
        entity.setRemark(request.getRemark());
        entity.setPaymentTime(request.getPaymentTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeAccountTransactionMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除账户流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceAccountTransaction entity = financeAccountTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("账户流水不存在");
        }
        financeAccountTransactionMapper.deleteById(id);
    }

    /**
     * 根据ID查询账户流水
     */
    public FinanceAccountTransactionView getById(Long id) {
        FinanceAccountTransaction entity = financeAccountTransactionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("账户流水不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询账户流水列表
     */
    public IPage<FinanceAccountTransactionView> page(FinanceAccountTransactionQueryRequest request, Long pageNum, Long pageSize) {
        Page<FinanceAccountTransaction> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FinanceAccountTransaction> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceAccountTransaction> resultPage = financeAccountTransactionMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询账户流水列表
     */
    public List<FinanceAccountTransactionView> list(FinanceAccountTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceAccountTransaction> queryWrapper = buildQueryWrapper(request);
        List<FinanceAccountTransaction> list = financeAccountTransactionMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceAccountTransaction> buildQueryWrapper(FinanceAccountTransactionQueryRequest request) {
        LambdaQueryWrapper<FinanceAccountTransaction> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getTransactionNo())) {
            queryWrapper.like(FinanceAccountTransaction::getTransactionNo, request.getTransactionNo());
        }
        if (request.getAccountId() != null) {
            queryWrapper.eq(FinanceAccountTransaction::getAccountId, request.getAccountId());
        }
        if (request.getCustomerId() != null) {
            queryWrapper.eq(FinanceAccountTransaction::getCustomerId, request.getCustomerId());
        }
        if (request.getTransactionType() != null) {
            queryWrapper.eq(FinanceAccountTransaction::getTransactionType, request.getTransactionType());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceAccountTransaction::getCurrency, request.getCurrency());
        }
        if (StringUtils.hasText(request.getRemark())) {
            queryWrapper.like(FinanceAccountTransaction::getRemark, request.getRemark());
        }
        if (request.getPaymentTimeStart() != null) {
            queryWrapper.ge(FinanceAccountTransaction::getPaymentTime, request.getPaymentTimeStart());
        }
        if (request.getPaymentTimeEnd() != null) {
            queryWrapper.le(FinanceAccountTransaction::getPaymentTime, request.getPaymentTimeEnd());
        }
        queryWrapper.orderByDesc(FinanceAccountTransaction::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceAccountTransactionView convertToView(FinanceAccountTransaction entity) {
        FinanceAccountTransactionView view = new FinanceAccountTransactionView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setTransactionNo(entity.getTransactionNo());
        view.setAccountId(entity.getAccountId());
        view.setCustomerId(entity.getCustomerId());
        view.setTransactionType(entity.getTransactionType());
        view.setCurrency(entity.getCurrency());
        view.setAmount(entity.getAmount());
        view.setFee(entity.getFee());
        view.setCreditAmount(entity.getCreditAmount());
        view.setRemark(entity.getRemark());
        view.setPaymentTime(entity.getPaymentTime());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}
