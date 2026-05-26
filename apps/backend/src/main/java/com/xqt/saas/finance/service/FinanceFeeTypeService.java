package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.common.TenantUtils;
import com.xqt.saas.finance.dto.request.FinanceFeeTypeQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceFeeTypeSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceFeeTypeView;
import com.xqt.saas.finance.entity.FinanceFeeType;
import com.xqt.saas.finance.mapper.FinanceFeeTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 费用类型服务类
 * 提供费用类型的CRUD操作
 */
@Service
@RequiredArgsConstructor
public class FinanceFeeTypeService extends ServiceImpl<FinanceFeeTypeMapper, FinanceFeeType> {

    private final FinanceFeeTypeMapper financeFeeTypeMapper;

    /**
     * 保存费用类型
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceFeeTypeView save(FinanceFeeTypeSaveRequest request) {
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            throw new IllegalArgumentException("租户ID不能为空");
        }

        FinanceFeeType entity = new FinanceFeeType();
        try {
            entity.setTenantId(UUID.fromString(request.getTenantId()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("租户ID格式不正确，应为UUID格式");
        }
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setPrice(request.getPrice());
        entity.setIsShow(request.getIsShow());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeFeeTypeMapper.insert(entity);
        return convertToView(entity);
    }

    /**
     * 更新费用类型
     */
    @Transactional(rollbackFor = Exception.class)
    public FinanceFeeTypeView update(FinanceFeeTypeSaveRequest request) {
        FinanceFeeType entity = financeFeeTypeMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("费用类型不存在");
        }

        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setPrice(request.getPrice());
        entity.setIsShow(request.getIsShow());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());

        financeFeeTypeMapper.updateById(entity);
        return convertToView(entity);
    }

    /**
     * 删除费用类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceFeeType entity = financeFeeTypeMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("费用类型不存在");
        }
        financeFeeTypeMapper.deleteById(id);
    }

    /**
     * 根据ID查询费用类型
     */
    public FinanceFeeTypeView getById(Long id) {
        FinanceFeeType entity = financeFeeTypeMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("费用类型不存在");
        }
        return convertToView(entity);
    }

    /**
     * 分页查询费用类型列表
     */
    public IPage<FinanceFeeTypeView> page(FinanceFeeTypeQueryRequest request) {
        Page<FinanceFeeType> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceFeeType> resultPage = financeFeeTypeMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    /**
     * 查询费用类型列表
     */
    public List<FinanceFeeTypeView> list(FinanceFeeTypeQueryRequest request) {
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = buildQueryWrapper(request);
        List<FinanceFeeType> list = financeFeeTypeMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    /**
     * 批量导入费用类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void importExcel(List<FinanceFeeType> dataList) {
        for (FinanceFeeType entity : dataList) {
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            financeFeeTypeMapper.insert(entity);
        }
    }

    /**
     * 导出费用类型
     */
    public List<FinanceFeeType> exportExcel(FinanceFeeTypeQueryRequest request) {
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = buildQueryWrapper(request);
        return financeFeeTypeMapper.selectList(queryWrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<FinanceFeeType> buildQueryWrapper(FinanceFeeTypeQueryRequest request) {
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FinanceFeeType::getTenantId, TenantUtils.currentUuid());
        if (StringUtils.hasText(request.getCode())) {
            queryWrapper.like(FinanceFeeType::getCode, request.getCode());
        }
        if (StringUtils.hasText(request.getName())) {
            queryWrapper.like(FinanceFeeType::getName, request.getName());
        }
        if (StringUtils.hasText(request.getType())) {
            queryWrapper.eq(FinanceFeeType::getType, request.getType());
        }
        if (request.getIsShow() != null) {
            queryWrapper.eq(FinanceFeeType::getIsShow, request.getIsShow());
        }
        queryWrapper.orderByDesc(FinanceFeeType::getCreateTime);
        return queryWrapper;
    }

    /**
     * 转换为视图对象
     */
    private FinanceFeeTypeView convertToView(FinanceFeeType entity) {
        FinanceFeeTypeView view = new FinanceFeeTypeView();
        view.setId(entity.getId());
        view.setCode(entity.getCode());
        view.setName(entity.getName());
        view.setType(entity.getType());
        view.setPrice(entity.getPrice());
        view.setIsShow(entity.getIsShow());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }
}
