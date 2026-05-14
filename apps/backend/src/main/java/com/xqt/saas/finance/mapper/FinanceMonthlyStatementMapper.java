package com.xqt.saas.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.finance.entity.FinanceMonthlyStatement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 月结单Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface FinanceMonthlyStatementMapper extends BaseMapper<FinanceMonthlyStatement> {
}
