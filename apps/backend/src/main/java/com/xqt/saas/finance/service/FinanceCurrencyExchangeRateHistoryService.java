package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceCurrencyExchangeRateHistorySaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCurrencyExchangeRateHistoryView;
import com.xqt.saas.finance.entity.FinanceCurrencyExchangeRateHistoryType;
import com.xqt.saas.finance.mapper.FinanceCurrencyExchangeRateHistoryMapper;
import com.xqt.saas.finance.mapper.FinanceCurrencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyExchangeRateHistoryService extends ServiceImpl<FinanceCurrencyExchangeRateHistoryMapper, FinanceCurrencyExchangeRateHistoryType> {
    private final FinanceCurrencyExchangeRateHistoryMapper historyMapper;
    private final FinanceCurrencyMapper currencyMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyExchangeRateHistoryView save(Long id, FinanceCurrencyExchangeRateHistorySaveRequest body) {
        var currency = currencyMapper.selectById(id);
        if (currency == null) {
            throw new RuntimeException("无货币");
        }

        var now = OffsetDateTime.now();
        var entity = new FinanceCurrencyExchangeRateHistoryType();
        entity.setCreatedAt(now);
        entity.setCreatedBy(body.createdBy());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(body.createdBy());
        entity.setCode(currency.getCode());
        entity.setApplicationScenario(body.applicationScenario());
        entity.setRate(body.rate());
        entity.setEffectiveFrom(body.effectiveFrom());

        save(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long currencyId, Long historyId) {
        var currency = currencyMapper.selectById(currencyId);
        if (currency == null) {
            throw new RuntimeException("无货币");
        }
        var history = historyMapper.selectById(historyId);
        if (history == null) {
            throw new RuntimeException("无汇率记录");
        }
        if (!history.getCode().equals(currency.getCode())) {
            throw new RuntimeException("货币与汇率记录不对应");
        }
        historyMapper.deleteById(historyId);
    }

    public IPage<FinanceCurrencyExchangeRateHistoryView> page(Long id, Long pageNum, Long pageSize) {
        var currency = currencyMapper.selectById(id);
        if (currency == null) {
            return new Page<>();
        }
        var res = historyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyExchangeRateHistoryType>().
                        eq(FinanceCurrencyExchangeRateHistoryType::getCode, currency.getCode()).
                        orderByDesc(FinanceCurrencyExchangeRateHistoryType::getCreatedAt)
        );
        return res.convert(this::convertToView);
    }

    private FinanceCurrencyExchangeRateHistoryView convertToView(FinanceCurrencyExchangeRateHistoryType entity) {
        return new FinanceCurrencyExchangeRateHistoryView(entity.getId(), entity.getCode(), entity.getApplicationScenario(), entity.getRate(), entity.getEffectiveFrom());
    }
}
