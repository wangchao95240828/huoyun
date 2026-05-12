package com.xqt.saas.finance.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.*;

// 👇 明确指定处理的Java类型为 UUID 的 List 或数组
@MappedTypes({List.class, UUID[].class})
@MappedJdbcTypes({JdbcType.ARRAY})
public class UuidListTypeHandler extends BaseTypeHandler<List<UUID>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<UUID> parameter, JdbcType jdbcType) throws SQLException {
        UUID[] uuidArray = parameter.toArray(new UUID[0]);
        // 关键：创建数组时，类型名称必须指定为 "uuid"
        Array array = ps.getConnection().createArrayOf("uuid", uuidArray);
        ps.setArray(i, array);
    }

    @Override
    public List<UUID> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Array array = rs.getArray(columnName);
        if (array == null) return null;
        UUID[] uuidArray = (UUID[]) array.getArray();
        return uuidArray == null ? null : Arrays.asList(uuidArray);
    }

    @Override
    public List<UUID> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Array array = rs.getArray(columnIndex);
        if (array == null) return null;
        UUID[] uuidArray = (UUID[]) array.getArray();
        return uuidArray == null ? null : Arrays.asList(uuidArray);
    }

    @Override
    public List<UUID> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Array array = cs.getArray(columnIndex);
        if (array == null) return null;
        UUID[] uuidArray = (UUID[]) array.getArray();
        return uuidArray == null ? null : Arrays.asList(uuidArray);
    }
}