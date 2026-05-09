package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceCurrencySaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCurrencyView;
import com.xqt.saas.finance.dto.response.FinanceCurrencyWithExchangeRateView;
import com.xqt.saas.finance.entity.FinanceCurrencyExchangeRateHistoryType;
import com.xqt.saas.finance.entity.FinanceCurrencyType;
import com.xqt.saas.finance.mapper.FinanceCurrencyExchangeRateHistoryMapper;
import com.xqt.saas.finance.mapper.FinanceCurrencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyService extends ServiceImpl<FinanceCurrencyMapper, FinanceCurrencyType> {
    private final FinanceCurrencyMapper currencyMapper;
    private final FinanceCurrencyExchangeRateHistoryMapper historyMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyView save(FinanceCurrencySaveRequest req) {
        var entity = new FinanceCurrencyType();
        var now = ZonedDateTime.now();
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

    public List<FinanceCurrencyWithExchangeRateView> pageCurrencyWithExchangeRate(Long pageNum, Long pageSize) {
        // 先获取到该分页的 codes，根据 code 查询对应的应收、应付
        var currencies = page(pageNum, pageSize);

        var res = new ArrayList<FinanceCurrencyWithExchangeRateView>();
        for (var currency : currencies) {
            var fromHistory = getLatestRate(currency.getCode(), "应收");
            var toHistory = getLatestRate(currency.getCode(), "应付");

            res.add(new FinanceCurrencyWithExchangeRateView(
                    currency.getId(),
                    currency.getCode(),
                    currency.getName(),
                    fromHistory.getRate(),
                    fromHistory.getEffectiveFrom(),
                    toHistory.getRate(),
                    toHistory.getEffectiveFrom()
            ));
        }

        return res;
    }

    private List<FinanceCurrencyType> page(Long pageNum, Long pageSize) {
        var res = currencyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyType>().
                        orderByDesc(FinanceCurrencyType::getCreatedAt)
        );
        return res.getRecords();
    }

    private FinanceCurrencyExchangeRateHistoryType getLatestRate(String code, String scenario) {
        var wrapper = new LambdaQueryWrapper<FinanceCurrencyExchangeRateHistoryType>();
        wrapper.eq(FinanceCurrencyExchangeRateHistoryType::getCode, code).
                eq(FinanceCurrencyExchangeRateHistoryType::getApplicationScenario, scenario).
                orderByDesc(FinanceCurrencyExchangeRateHistoryType::getEffectiveFrom).
                last("limit 1");
        return historyMapper.selectOne(wrapper);
    }
}
