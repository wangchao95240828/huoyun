package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCurrencyView;
import com.xqt.saas.finance.dto.response.FinanceCurrencyWithExchangeRateView;
import com.xqt.saas.finance.entity.FinanceCurrencyExchangeRateHistoryType;
import com.xqt.saas.finance.entity.FinanceCurrencyType;
import com.xqt.saas.finance.mapper.FinanceCurrencyExchangeRateHistoryMapper;
import com.xqt.saas.finance.mapper.FinanceCurrencyMapper;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyService extends ServiceImpl<FinanceCurrencyMapper, FinanceCurrencyType> {
    private final FinanceCurrencyMapper currencyMapper;
    private final FinanceCurrencyExchangeRateHistoryMapper historyMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyView save(FinanceCurrencySaveRequest req) {
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
    public void delete(Long id) {
        removeById(id);
    }

    public IPage<FinanceCurrencyWithExchangeRateView> pageCurrencyWithExchangeRate(Long pageNum, Long pageSize) {
        // 先获取到该分页的 codes，根据 code 查询对应的应收、应付
        var currencies = page(pageNum, pageSize);

        var records = new ArrayList<FinanceCurrencyWithExchangeRateView>();
        for (var currency : currencies.getRecords()) {
            var fromHistory = getLatestRate(currency.getCode(), "应收");
            var toHistory = getLatestRate(currency.getCode(), "应付");

            records.add(new FinanceCurrencyWithExchangeRateView(
                    currency.getId(),
                    currency.getCode(),
                    currency.getName(),
                    fromHistory == null ? null : fromHistory.getRate(),
                    fromHistory == null ? null : fromHistory.getEffectiveFrom(),
                    toHistory == null ? null : toHistory.getRate(),
                    toHistory == null ? null : toHistory.getEffectiveFrom()
            ));
        }
        var result = new Page<FinanceCurrencyWithExchangeRateView>();
        result.setRecords(records);
        result.setTotal(currencies.getRecords().size());
        result.setSize(records.size());
        result.setCurrent(currencies.getCurrent());

        return result;
    }

    private IPage<FinanceCurrencyType> page(Long pageNum, Long pageSize) {
        return currencyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyType>().
                        orderByDesc(FinanceCurrencyType::getCreatedAt)
        );
    }

    private @Nullable FinanceCurrencyExchangeRateHistoryType getLatestRate(String code, String scenario) {
        var wrapper = new LambdaQueryWrapper<FinanceCurrencyExchangeRateHistoryType>();
        wrapper.eq(FinanceCurrencyExchangeRateHistoryType::getCode, code).
                eq(FinanceCurrencyExchangeRateHistoryType::getApplicationScenario, scenario).
                orderByDesc(FinanceCurrencyExchangeRateHistoryType::getEffectiveFrom).
                last("limit 1");
        return historyMapper.selectOne(wrapper);
    }
}
