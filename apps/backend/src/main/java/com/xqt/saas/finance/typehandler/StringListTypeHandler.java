package com.xqt.saas.finance.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.Arrays;
import java.util.List;

@MappedTypes({List.class})
@MappedJdbcTypes({JdbcType.ARRAY})
public class StringListTypeHandler extends BaseTypeHandler<List<String>> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        // 将Java List转换为Java数组
        String[] stringArray = parameter.toArray(new String[0]);
        // 创建PostgreSQL数组对象
        Array array = ps.getConnection().createArrayOf("text", stringArray);
        // 设置到PreparedStatement中
        ps.setArray(i, array);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 从ResultSet中获取Array对象并转换为Java List
        Array array = rs.getArray(columnName);
        return array == null ? null : Arrays.asList((String[]) array.getArray());
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Array array = rs.getArray(columnIndex);
        return array == null ? null : Arrays.asList((String[]) array.getArray());
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Array array = cs.getArray(columnIndex);
        return array == null ? null : Arrays.asList((String[]) array.getArray());
    }
}
