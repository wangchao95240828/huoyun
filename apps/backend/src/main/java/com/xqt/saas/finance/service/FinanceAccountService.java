package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceAccountQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceAccountSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceAccountView;
import com.xqt.saas.finance.entity.FinanceAccount;
import com.xqt.saas.finance.mapper.FinanceAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceAccountService extends ServiceImpl<FinanceAccountMapper, FinanceAccount> {

    private final FinanceAccountMapper financeAccountMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceAccountView save(FinanceAccountSaveRequest request) {
        FinanceAccount entity = new FinanceAccount();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setAccountName(request.getAccountName());
        entity.setCurrency(request.getCurrency());
        entity.setBalance(request.getBalance() != null ? request.getBalance() : BigDecimal.ZERO);
        entity.setBankName(request.getBankName());
        entity.setType(request.getType());
        entity.setVisible(request.getVisible() != null ? request.getVisible() : true);
        entity.setRemark(request.getRemark());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeAccountMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceAccountView update(FinanceAccountSaveRequest request) {
        FinanceAccount entity = financeAccountMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("账户不存在");
        }

        entity.setAccountName(request.getAccountName());
        entity.setCurrency(request.getCurrency());
        if (request.getBalance() != null) {
            entity.setBalance(request.getBalance());
        }
        entity.setBankName(request.getBankName());
        entity.setType(request.getType());
        if (request.getVisible() != null) {
            entity.setVisible(request.getVisible());
        }
        entity.setRemark(request.getRemark());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeAccountMapper.updateById(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceAccount entity = financeAccountMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("账户不存在");
        }
        financeAccountMapper.deleteById(id);
    }

    public FinanceAccountView getById(Long id) {
        FinanceAccount entity = financeAccountMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("账户不存在");
        }
        return convertToView(entity);
    }

    public IPage<FinanceAccountView> page(FinanceAccountQueryRequest request, Long pageNum, Long pageSize) {
        Page<FinanceAccount> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FinanceAccount> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceAccount> resultPage = financeAccountMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<FinanceAccountView> list(FinanceAccountQueryRequest request) {
        LambdaQueryWrapper<FinanceAccount> queryWrapper = buildQueryWrapper(request);
        List<FinanceAccount> list = financeAccountMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<FinanceAccount> buildQueryWrapper(FinanceAccountQueryRequest request) {
        LambdaQueryWrapper<FinanceAccount> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getAccountName())) {
            queryWrapper.like(FinanceAccount::getAccountName, request.getAccountName());
        }
        if (StringUtils.hasText(request.getCurrency())) {
            queryWrapper.eq(FinanceAccount::getCurrency, request.getCurrency());
        }
        if (StringUtils.hasText(request.getBankName())) {
            queryWrapper.like(FinanceAccount::getBankName, request.getBankName());
        }
        if (request.getType() != null) {
            queryWrapper.eq(FinanceAccount::getType, request.getType());
        }
        if (request.getVisible() != null) {
            queryWrapper.eq(FinanceAccount::getVisible, request.getVisible());
        }
        if (StringUtils.hasText(request.getRemark())) {
            queryWrapper.like(FinanceAccount::getRemark, request.getRemark());
        }
        queryWrapper.orderByDesc(FinanceAccount::getCreateTime);
        return queryWrapper;
    }

    private FinanceAccountView convertToView(FinanceAccount entity) {
        FinanceAccountView view = new FinanceAccountView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setAccountName(entity.getAccountName());
        view.setCurrency(entity.getCurrency());
        view.setBalance(entity.getBalance());
        view.setBankName(entity.getBankName());
        view.setType(entity.getType());
        view.setVisible(entity.getVisible());
        view.setRemark(entity.getRemark());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}