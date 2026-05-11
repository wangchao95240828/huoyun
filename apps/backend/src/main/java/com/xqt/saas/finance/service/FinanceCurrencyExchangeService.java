package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceCurrencyExchangeSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCurrencyExchangeView;
import com.xqt.saas.finance.entity.FinanceCurrencyExchangeType;
import com.xqt.saas.finance.mapper.FinanceCurrencyExchangeMapper;
import com.xqt.saas.finance.mapper.FinanceCurrencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class FinanceCurrencyExchangeService extends ServiceImpl<FinanceCurrencyExchangeMapper, FinanceCurrencyExchangeType> {
    private final FinanceCurrencyExchangeMapper exchangeMapper;
    private final FinanceCurrencyMapper currencyMapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceCurrencyExchangeView save(Long id, FinanceCurrencyExchangeSaveRequest body) {
        var currency = currencyMapper.selectById(id);
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

        save(entity);
        return convertToView(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long currencyId, Long historyId) {
        var currency = currencyMapper.selectById(currencyId);
        if (currency == null) {
            throw new RuntimeException("无货币");
        }
        var history = exchangeMapper.selectById(historyId);
        if (history == null) {
            throw new RuntimeException("无汇率记录");
        }
        if (!history.getCode().equals(currency.getCode())) {
            throw new RuntimeException("货币与汇率记录不对应");
        }
        exchangeMapper.deleteById(historyId);
    }

    public IPage<FinanceCurrencyExchangeView> page(Long id, Long pageNum, Long pageSize) {
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
