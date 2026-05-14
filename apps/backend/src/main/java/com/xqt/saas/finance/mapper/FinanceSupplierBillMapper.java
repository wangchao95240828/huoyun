package com.xqt.saas.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.finance.entity.FinanceSupplierBill;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商账单Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface FinanceSupplierBillMapper extends BaseMapper<FinanceSupplierBill> {
}
