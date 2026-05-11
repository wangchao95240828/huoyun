package com.xqt.saas.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.finance.entity.FinanceAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账户Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface FinanceAccountMapper extends BaseMapper<FinanceAccount> {
}
