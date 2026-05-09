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

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyExchangeRateHistoryService extends ServiceImpl<FinanceCurrencyExchangeRateHistoryMapper, FinanceCurrencyExchangeRateHistoryType> {
    private final FinanceCurrencyExchangeRateHistoryMapper historyMapper;
    private final FinanceCurrencyMapper currencyMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyExchangeRateHistoryView save(FinanceCurrencyExchangeRateHistorySaveRequest req) {
        var entity = new FinanceCurrencyExchangeRateHistoryType();
        var now = ZonedDateTime.now();
        entity.setCreatedAt(now);
        entity.setCreatedBy(req.createdBy());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(req.createdBy());
        entity.setCode(req.code());
        entity.setApplicationScenario(req.applicationScenario());
        entity.setRate(req.rate());
        entity.setEffectiveFrom(req.effectiveFrom());

        save(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    public IPage<FinanceCurrencyExchangeRateHistoryView> page(String code, Long pageNum, Long pageSize) {
        var res = historyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceCurrencyExchangeRateHistoryType>().
                        eq(FinanceCurrencyExchangeRateHistoryType::getCode, code).
                        orderByDesc(FinanceCurrencyExchangeRateHistoryType::getCreatedAt)
        );
        return res.convert(this::convertToView);
    }

    private FinanceCurrencyExchangeRateHistoryView convertToView(FinanceCurrencyExchangeRateHistoryType entity) {
        return new FinanceCurrencyExchangeRateHistoryView(entity.getId(), entity.getCode(), entity.getApplicationScenario(), entity.getRate(), entity.getEffectiveFrom());
    }
}
