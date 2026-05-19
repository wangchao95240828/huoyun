package com.xqt.saas.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xqt.saas.order.entity.OrderService;
import org.apache.ibatis.annotations.Mapper;

/**
 * 服务Mapper接口
 * 继承BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface OrderServiceMapper extends BaseMapper<OrderService> {
}
