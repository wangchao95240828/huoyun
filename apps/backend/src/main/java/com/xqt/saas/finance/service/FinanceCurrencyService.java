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
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyService extends ServiceImpl<FinanceCurrencyMapper, FinanceCurrencyType> {
    private final FinanceCurrencyMapper currencyMapper;
    private final FinanceCurrencyExchangeMapper exchangeMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyView createCurrency(FinanceCurrencySaveRequest req) {
        var entity = new FinanceCurrencyType();
        var now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setCreatedBy(req.createdBy());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(req.createdBy());
        entity.setCode(req.code());
        entity.setName(req.name());

        save(entity);
        return new FinanceCurrencyView(entity.getId(), entity.getCode(), entity.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrency(Long id) {
        removeById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyExchangeView createExchange(Long currencyId, FinanceCurrencyExchangeSaveRequest body) {
        var currency = currencyMapper.selectById(currencyId);
        if (currency == null) {
            throw new RuntimeException("无货币");
        }

        var now = OffsetDateTime.now();
        var entity = new FinanceCurrencyExchangeType();
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
    public void deleteExchange(Long currencyId, Long exchangeId) {
        var currency = currencyMapper.selectById(currencyId);
        if (currency == null) {
            throw new RuntimeException("无货币");
        }
        var history = exchangeMapper.selectById(exchangeId);
        if (history == null) {
            throw new RuntimeException("无汇率记录");
        }
        if (!history.getCode().equals(currency.getCode())) {
            throw new RuntimeException("货币与汇率记录不对应");
        }
        exchangeMapper.deleteById(exchangeId);
    }

    public IPage<FinanceCurrencyWithExchangeView> getCurrenciesWithExchangePage(Long pageNum, Long pageSize) {
        // 先获取到该分页的 codes，根据 code 查询对应的应收、应付
        var currencies = getCurrencyPage(pageNum, pageSize);

        var records = new ArrayList<FinanceCurrencyWithExchangeView>();
        for (var currency : currencies.getRecords()) {
            var fromHistory = getCurrencyLatestExchange(currency.getCode(), "应收");
            var toHistory = getCurrencyLatestExchange(currency.getCode(), "应付");

            records.add(new FinanceCurrencyWithExchangeView(
                    currency.getId(),
                    currency.getCode(),
                    currency.getName(),
                    fromHistory == null ? null : fromHistory.getRate(),
                    fromHistory == null ? null : fromHistory.getEffectiveFrom(),
                    toHistory == null ? null : toHistory.getRate(),
                    toHistory == null ? null : toHistory.getEffectiveFrom()
            ));
        }
        var result = new Page<FinanceCurrencyWithExchangeView>();
        result.setRecords(records);
        result.setTotal(currencies.getRecords().size());
        result.setSize(records.size());
        result.setCurrent(currencies.getCurrent());

        return result;
    }

    private IPage<FinanceCurrencyType> getCurrencyPage(Long pageNum, Long pageSize) {
        return currencyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyType>().
                        orderByDesc(FinanceCurrencyType::getCreatedAt)
        );
    }


    // 获取某个货币的最新汇率与汇率生效时间
    private @Nullable FinanceCurrencyExchangeType getCurrencyLatestExchange(@NotNull String code, @NotNull String scenario) {
        var wrapper = new LambdaQueryWrapper<FinanceCurrencyExchangeType>();
        wrapper.eq(FinanceCurrencyExchangeType::getCode, code).
                eq(FinanceCurrencyExchangeType::getApplicationScenario, scenario).
                orderByDesc(FinanceCurrencyExchangeType::getEffectiveFrom).
                last("limit 1");
        return exchangeMapper.selectOne(wrapper);
    }


    // 分页获取某个货币的汇率记录
    public IPage<FinanceCurrencyExchangeView> getCurrencyExchangesPage(Long id, Long pageNum, Long pageSize) {
        var currency = currencyMapper.selectById(id);
        if (currency == null) {
            return new Page<>();
        }
        var res = exchangeMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyExchangeType>().
                        eq(FinanceCurrencyExchangeType::getCode, currency.getCode()).
                        orderByDesc(FinanceCurrencyExchangeType::getCreatedAt)
        );
        return res.convert(this::convertToView);
    }

    private FinanceCurrencyExchangeView convertToView(FinanceCurrencyExchangeType entity) {
        return new FinanceCurrencyExchangeView(entity.getId(), entity.getCode(), entity.getApplicationScenario(), entity.getRate(), entity.getEffectiveFrom());
    }
}
