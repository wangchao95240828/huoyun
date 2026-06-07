package com.xqt.saas.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 单租户化的副产品：业务退化成单租户后，API 响应不应该再暴露 tenant_id 字段。
 * 但 DB 层还保留 tenant_id 列（迁移 058），所以 SELECT 返回的 Map 里仍带这个 key。
 * 这里在序列化前递归剥掉所有响应体中的 tenant_id / tenantId 字段。
 *
 * 不动业务代码，对所有 controller 透明生效。
 *
 * 注：仅过滤响应体里的 Map / List 嵌套结构。
 *     record / DTO 里如果显式声明了 tenantId 字段，仍会原样返回（业务代码请删除）。
 */
@ControllerAdvice
public class TenantIdResponseStripper implements ResponseBodyAdvice<Object> {

    private static final String[] STRIP_KEYS = {"tenant_id", "tenantId"};

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converterType) {
        // 只对 JSON 响应处理；二进制流（PDF/Excel 下载）跳过
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) return null;
        return stripRecursive(body);
    }

    @SuppressWarnings("unchecked")
    private Object stripRecursive(Object node) {
        if (node instanceof Map<?, ?> rawMap) {
            // 拷贝一份，避免改原 SQL 结果引用
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                String key = e.getKey() == null ? null : e.getKey().toString();
                if (key != null && isStrippable(key)) continue;
                copy.put(key, stripRecursive(e.getValue()));
            }
            return copy;
        }
        if (node instanceof List<?> rawList) {
            List<Object> out = new ArrayList<>(rawList.size());
            for (Object item : rawList) out.add(stripRecursive(item));
            return out;
        }
        // 标量 / record / 其他 — 不递归（避免反射开销 + 暴 record 内部字段）
        return node;
    }

    private static boolean isStrippable(String key) {
        for (String k : STRIP_KEYS) {
            if (k.equals(key)) return true;
        }
        return false;
    }
}
