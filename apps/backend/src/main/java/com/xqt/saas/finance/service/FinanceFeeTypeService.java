package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceFeeTypeService extends ServiceImpl<FinanceFeeTypeMapper, FinanceFeeType> {

    private final FinanceFeeTypeMapper financeFeeTypeMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceFeeTypeView save(FinanceFeeTypeSaveRequest request) {
        FinanceFeeType entity = new FinanceFeeType();
        entity.setTenantId(request.getTenantId());
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

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinanceFeeType entity = financeFeeTypeMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("费用类型不存在");
        }
        financeFeeTypeMapper.deleteById(id);
    }


    public FinanceFeeTypeView getById(Long id) {
        FinanceFeeType entity = financeFeeTypeMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("费用类型不存在");
        }
        return convertToView(entity);
    }


    public IPage<FinanceFeeTypeView> page(FinanceFeeTypeQueryRequest request, Long pageNum, Long pageSize) {
        Page<FinanceFeeType> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = buildQueryWrapper(request);

        IPage<FinanceFeeType> resultPage = financeFeeTypeMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }


    public List<FinanceFeeTypeView> list(FinanceFeeTypeQueryRequest request) {
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = buildQueryWrapper(request);
        List<FinanceFeeType> list = financeFeeTypeMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }


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


    public List<FinanceFeeType> exportExcel(FinanceFeeTypeQueryRequest request) {
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = buildQueryWrapper(request);
        return financeFeeTypeMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<FinanceFeeType> buildQueryWrapper(FinanceFeeTypeQueryRequest request) {
        LambdaQueryWrapper<FinanceFeeType> queryWrapper = new LambdaQueryWrapper<>();
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