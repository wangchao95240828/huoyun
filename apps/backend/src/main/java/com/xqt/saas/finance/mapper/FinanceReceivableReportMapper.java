package com.xqt.saas.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.finance.entity.FinanceReceivableReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应收报表Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface FinanceReceivableReportMapper extends BaseMapper<FinanceReceivableReport> {
}
