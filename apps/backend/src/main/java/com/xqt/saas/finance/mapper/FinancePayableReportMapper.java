package com.xqt.saas.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.finance.entity.FinancePayableReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应付报表Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface FinancePayableReportMapper extends BaseMapper<FinancePayableReport> {
}
