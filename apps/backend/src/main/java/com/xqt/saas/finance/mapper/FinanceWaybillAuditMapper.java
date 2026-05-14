package com.xqt.saas.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.finance.entity.FinanceWaybillAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运单审计Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface FinanceWaybillAuditMapper extends BaseMapper<FinanceWaybillAudit> {
}
