package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceCurrencyExchangeSaveRequest;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCurrencyExchangeView;
import com.xqt.saas.finance.dto.response.FinanceCurrencyView;
import com.xqt.saas.finance.dto.response.FinanceCurrencyWithExchangeView;
import com.xqt.saas.finance.entity.FinanceCurrencyExchangeType;
import com.xqt.saas.finance.entity.FinanceCurrencyType;
import com.xqt.saas.finance.mapper.FinanceCurrencyExchangeMapper;
import com.xqt.saas.finance.mapper.FinanceCurrencyMapper;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyService extends ServiceImpl<FinanceCurrencyMapper, FinanceCurrencyType> {
    private final FinanceCurrencyMapper currencyMapper;
    private final FinanceCurrencyExchangeMapper exchangeMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyView createCurrency(UUID tenantId, FinanceCurrencySaveRequest req) {
        var entity = new FinanceCurrencyType();
        var now = OffsetDateTime.now();
        entity.setTenantId(tenantId);
        entity.setCreatedAt(now);
        entity.setCreatedBy(req.createdBy());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(req.createdBy());
        entity.setCode(req.code());
        entity.setName(req.name());

        currencyMapper.insert(entity);
        return new FinanceCurrencyView(entity.getId(), entity.getCode(), entity.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrency(UUID tenantId, Long id) {
        currencyMapper.delete(new LambdaQueryWrapper<FinanceCurrencyType>().
                eq(FinanceCurrencyType::getTenantId, tenantId).
                eq(FinanceCurrencyType::getId, id)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyExchangeView createExchange(UUID tenantId, Long currencyId, FinanceCurrencyExchangeSaveRequest body) {
        var currency = currencyMapper.selectOne(
                new LambdaQueryWrapper<FinanceCurrencyType>().
                        eq(FinanceCurrencyType::getTenantId, tenantId).
                        eq(FinanceCurrencyType::getId, currencyId)
        );
        if (currency == null) {
            throw new IllegalArgumentException("currency not found");
        }

        var now = OffsetDateTime.now();
        var entity = new FinanceCurrencyExchangeType();
        entity.setTenantId(tenantId);
        entity.setCreatedAt(now);
        entity.setCreatedBy(body.createdBy());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(body.createdBy());
        entity.setCode(currency.getCode());
        entity.setApplicationScenario(body.applicationScenario());
        entity.setRate(body.rate());
        entity.setEffectiveFrom(body.effectiveFrom());

        exchangeMapper.insert(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteExchange(UUID tenantId, Long currencyId, Long exchangeId) {
        // 货币已删除、汇率已删除、货币与汇率对应时才能正常执行
        var currency = getCurrencyById(tenantId, currencyId);
        if (currency == null) {
            return;
        }
        var exchange = exchangeMapper.selectById(exchangeId);
        if (exchange == null) {
            return;
        }

        if (!exchange.getCode().equals(currency.getCode())) {
            throw new IllegalArgumentException("currency and exchange record mismatch");
        }
        exchangeMapper.deleteById(exchangeId);
    }

    // 获取某个货币的最新应收/应付汇率与汇率生效时间
    public @Nullable FinanceCurrencyWithExchangeView getCurrencyWithExchange(UUID tenantId, Long currencyId) {
        var currency = getCurrencyById(tenantId, currencyId);
        if (currency == null) {
            return null;
        }

        var from = getCurrencyLatestExchange(tenantId, currency.getCode(), "应收");
        var to = getCurrencyLatestExchange(tenantId, currency.getCode(), "应付");
        return new FinanceCurrencyWithExchangeView(
                currency.getId(),
                currency.getCode(),
                currency.getName(),
                from == null ? null : from.getRate(),
                from == null ? null : from.getEffectiveFrom(),
                to == null ? null : to.getRate(),
                to == null ? null : to.getEffectiveFrom()
        );
    }

    public List<FinanceCurrencyWithExchangeView> getCurrenciesWithExchange(UUID tenantId) {
        var currencies = getCurrencies(tenantId);
        var result = new ArrayList<FinanceCurrencyWithExchangeView>();
        for (FinanceCurrencyType c : currencies) {
            var ce = getCurrencyWithExchange(tenantId, c.getId());
            if (ce != null) {
                result.add(ce);
            }
        }
        return result;
    }

    public IPage<FinanceCurrencyWithExchangeView> getCurrenciesWithExchangePage(UUID tenantId, Long pageNum, Long pageSize) {
        // 先获取到该分页的 codes，根据 code 查询对应的应收、应付
        var currencies = getCurrencyPage(tenantId, pageNum, pageSize);

        var records = new ArrayList<FinanceCurrencyWithExchangeView>();
        for (var c : currencies.getRecords()) {
            var ce = getCurrencyWithExchange(tenantId, c.getId());
            if (ce != null) {
                records.add(ce);
            }
        }
        var result = new Page<FinanceCurrencyWithExchangeView>();
        result.setRecords(records);
        result.setTotal(currencies.getRecords().size());
        result.setSize(records.size());
        result.setCurrent(currencies.getCurrent());

        return result;
    }

    private IPage<FinanceCurrencyType> getCurrencyPage(UUID tenantId, Long pageNum, Long pageSize) {
        return currencyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyType>().
                        eq(FinanceCurrencyType::getTenantId, tenantId).
                        orderByDesc(FinanceCurrencyType::getCreatedAt)
        );
    }

    // 获取某个货币的最新汇率与汇率生效时间
    private @Nullable FinanceCurrencyExchangeType getCurrencyLatestExchange(UUID tenantId, String code, String scenario) {
        var wrapper = new LambdaQueryWrapper<FinanceCurrencyExchangeType>();
        wrapper.
                eq(FinanceCurrencyExchangeType::getTenantId, tenantId).
                eq(FinanceCurrencyExchangeType::getCode, code).
                eq(FinanceCurrencyExchangeType::getApplicationScenario, scenario).
                orderByDesc(FinanceCurrencyExchangeType::getEffectiveFrom).
                last("limit 1");
        return exchangeMapper.selectOne(wrapper);
    }


    // 分页获取某个货币的汇率记录
    public IPage<FinanceCurrencyExchangeView> getCurrencyExchangesPage(UUID tenantId, Long id, Long pageNum, Long pageSize) {
        var currency = getCurrencyById(tenantId, id);
        if (currency == null) {
            return new Page<>(pageNum, pageSize);
        }
        var p = exchangeMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyExchangeType>().
                        eq(FinanceCurrencyExchangeType::getCode, currency.getCode()).
                        orderByDesc(FinanceCurrencyExchangeType::getCreatedAt)
        );
        return p.convert(this::convertToView);
    }

    // 根据 id 获取单个货币
    private @Nullable FinanceCurrencyType getCurrencyById(UUID tenantId, Long currencyId) {
        return currencyMapper.selectOne(new LambdaQueryWrapper<FinanceCurrencyType>().
                eq(FinanceCurrencyType::getTenantId, tenantId).
                eq(FinanceCurrencyType::getId, currencyId)
        );
    }

    // 获取所有货币
    private List<FinanceCurrencyType> getCurrencies(UUID tenantId) {
        return list(new LambdaQueryWrapper<FinanceCurrencyType>().
                eq(FinanceCurrencyType::getTenantId, tenantId));
    }

    private FinanceCurrencyExchangeView convertToView(FinanceCurrencyExchangeType entity) {
        return new FinanceCurrencyExchangeView(entity.getId(), entity.getCode(), entity.getApplicationScenario(), entity.getRate(), entity.getEffectiveFrom());
    }
}
