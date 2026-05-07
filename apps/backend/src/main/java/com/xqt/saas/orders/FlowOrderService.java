package com.xqt.saas.orders;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.AuditService;
import com.xqt.saas.common.CommandResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.common.PageResponse;
import com.xqt.saas.common.RequestContext;
import com.xqt.saas.orders.OrderResponses.OrderLineView;
import com.xqt.saas.orders.OrderResponses.OrderView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class FlowOrderService {
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String SOURCE_LOCAL = "LOCAL";

    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final JsonSupport json;
    private final AuditService auditService;

    public FlowOrderService(JdbcTemplate jdbc, RequestContext context, JsonSupport json, AuditService auditService) {
        this.jdbc = jdbc;
        this.context = context;
        this.json = json;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderView> search(AuthPrincipal auth, FlowDefinition flow, OrderRequests.Search request) {
        context.setTenant(auth);
        int page = request == null || request.page() == null ? DEFAULT_PAGE : Math.max(1, request.page());
        int pageSize = request == null || request.pageSize() == null
            ? DEFAULT_PAGE_SIZE
            : Math.max(1, Math.min(request.pageSize(), MAX_PAGE_SIZE));
        int offset = (page - 1) * pageSize;

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT
              o.id,
              o.order_no,
              o.customer_ref,
              o.status,
              o.source,
              o.customer_direction,
              o.order_entry_type,
              o.service_mode,
              o.created_at,
              o.updated_at,
              c.code AS customer_code,
              c.name AS customer_name,
              s.code AS service_code,
              s.name AS service_name,
              creator.display_name AS created_by_name,
              updater.display_name AS updated_by_name
            FROM orders o
            JOIN customers c ON c.tenant_id = o.tenant_id AND c.id = o.customer_id
            LEFT JOIN services s ON s.tenant_id = o.tenant_id AND s.id = o.service_id
            LEFT JOIN users creator ON creator.tenant_id = o.tenant_id AND creator.id = o.created_by
            LEFT JOIN users updater ON updater.tenant_id = o.tenant_id AND updater.id = o.updated_by
            WHERE o.tenant_id = ?::uuid
              AND o.customer_direction = ?
              AND o.service_mode = ?
              AND o.deleted_at IS NULL
            """);
        args.add(auth.tenantId());
        args.add(flow.customerDirection());
        args.add(flow.serviceMode());

        if (request != null && !isBlank(request.customerId())) {
            sql.append(" AND o.customer_id = ?::uuid");
            args.add(request.customerId());
        }
        if (request != null && !isBlank(request.customerCode())) {
            sql.append(" AND c.code = ?");
            args.add(request.customerCode());
        }
        if (request != null && !isBlank(request.status())) {
            sql.append(" AND o.status = ?");
            args.add(request.status());
        }
        if (request != null && !isBlank(request.keyword())) {
            sql.append("""
                 AND (
                   o.order_no ILIKE ?
                   OR o.customer_ref ILIKE ?
                   OR c.code ILIKE ?
                   OR c.name ILIKE ?
                 )
                """);
            String keyword = "%" + request.keyword().trim() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        sql.append(" ORDER BY o.created_at DESC LIMIT ? OFFSET ?");
        args.add(pageSize);
        args.add(offset);

        List<OrderView> items = json.rows(jdbc.queryForList(sql.toString(), args.toArray()))
            .stream()
            .map(row -> OrderResponses.orderView(row, List.of()))
            .toList();
        return new PageResponse<>(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public ItemResponse<OrderView> get(AuthPrincipal auth, FlowDefinition flow, String orderId) {
        context.setTenant(auth);
        OrderView order = findOrder(auth, flow, orderId);
        if (order == null) {
            throw new ResponseStatusException(NOT_FOUND, "order not found");
        }
        return new ItemResponse<>(order);
    }

    @Transactional
    public ItemResponse<OrderView> create(AuthPrincipal auth, FlowDefinition flow, OrderRequests.Save request) {
        context.setTenant(auth);
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        String customerId = resolveCustomerId(auth, flow, request.customerId(), request.customerCode());
        String orderNo = isBlank(request.orderNo()) ? generateOrderNo(flow.orderNoPrefix()) : request.orderNo().trim();
        String orderId = jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id,
              order_no,
              customer_id,
              service_id,
              status,
              source,
              customer_ref,
              created_by,
              updated_by,
              metadata,
              customer_direction,
              order_entry_type,
              service_mode
            )
            VALUES (
              ?::uuid,
              ?,
              ?::uuid,
              NULLIF(?, '')::uuid,
              ?,
              ?,
              ?,
              ?::uuid,
              ?::uuid,
              ?::jsonb,
              ?,
              ?,
              ?
            )
            RETURNING id::text
            """,
            String.class,
            auth.tenantId(),
            orderNo,
            customerId,
            nullToBlank(request.serviceId()),
            nonBlank(request.status(), STATUS_DRAFT),
            SOURCE_LOCAL,
            request.customerRef(),
            auth.userId(),
            auth.userId(),
            json.toJson(request.metadata()),
            flow.customerDirection(),
            nonBlank(request.orderEntryType(), flow.defaultOrderEntryType()),
            flow.serviceMode()
        );
        replaceLines(auth, orderId, request.lines());
        OrderView after = findOrder(auth, flow, orderId);
        auditService.log(auth, "order", orderId, "CREATE", null, after);
        return new ItemResponse<>(after);
    }

    @Transactional
    public ItemResponse<OrderView> update(AuthPrincipal auth, FlowDefinition flow, String orderId, OrderRequests.Save request) {
        context.setTenant(auth);
        OrderView before = findOrder(auth, flow, orderId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "order not found");
        }

        String customerId = null;
        if (request != null && (!isBlank(request.customerId()) || !isBlank(request.customerCode()))) {
            customerId = resolveCustomerId(auth, flow, request.customerId(), request.customerCode());
        }

        jdbc.update("""
            UPDATE orders
            SET customer_id = COALESCE(NULLIF(?, '')::uuid, customer_id),
                service_id = COALESCE(NULLIF(?, '')::uuid, service_id),
                customer_ref = ?,
                status = COALESCE(NULLIF(?, ''), status),
                order_entry_type = COALESCE(NULLIF(?, ''), order_entry_type),
                metadata = ?::jsonb,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND customer_direction = ?
              AND service_mode = ?
              AND deleted_at IS NULL
            """,
            nullToBlank(customerId),
            request == null ? "" : nullToBlank(request.serviceId()),
            request == null ? before.customerRef() : request.customerRef(),
            request == null ? "" : nullToBlank(request.status()),
            request == null ? "" : nullToBlank(request.orderEntryType()),
            json.toJson(request == null ? before.metadata() : request.metadata()),
            auth.userId(),
            auth.tenantId(),
            orderId,
            flow.customerDirection(),
            flow.serviceMode()
        );
        if (request != null && request.lines() != null) {
            replaceLines(auth, orderId, request.lines());
        }

        OrderView after = findOrder(auth, flow, orderId);
        auditService.log(auth, "order", orderId, "UPDATE", before, after);
        return new ItemResponse<>(after);
    }

    @Transactional
    public CommandResponse delete(AuthPrincipal auth, FlowDefinition flow, String orderId) {
        context.setTenant(auth);
        OrderView before = findOrder(auth, flow, orderId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "order not found");
        }
        jdbc.update("""
            UPDATE orders
            SET status = ?,
                deleted_at = now(),
                deleted_by = ?::uuid,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND customer_direction = ?
              AND service_mode = ?
              AND deleted_at IS NULL
            """, STATUS_CANCELLED, auth.userId(), auth.userId(), auth.tenantId(), orderId, flow.customerDirection(), flow.serviceMode());
        auditService.log(auth, "order", orderId, "DELETE", before, Map.of("id", orderId, "deleted", true));
        return CommandResponse.ok();
    }

    private OrderView findOrder(AuthPrincipal auth, FlowDefinition flow, String orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              o.id,
              o.order_no,
              o.customer_id,
              o.customer_ref,
              o.status,
              o.source,
              o.customer_direction,
              o.order_entry_type,
              o.service_mode,
              o.metadata,
              o.created_at,
              o.updated_at,
              c.code AS customer_code,
              c.name AS customer_name,
              s.code AS service_code,
              s.name AS service_name,
              creator.display_name AS created_by_name,
              updater.display_name AS updated_by_name
            FROM orders o
            JOIN customers c ON c.tenant_id = o.tenant_id AND c.id = o.customer_id
            LEFT JOIN services s ON s.tenant_id = o.tenant_id AND s.id = o.service_id
            LEFT JOIN users creator ON creator.tenant_id = o.tenant_id AND creator.id = o.created_by
            LEFT JOIN users updater ON updater.tenant_id = o.tenant_id AND updater.id = o.updated_by
            WHERE o.tenant_id = ?::uuid
              AND o.id = ?::uuid
              AND o.customer_direction = ?
              AND o.service_mode = ?
              AND o.deleted_at IS NULL
            """, auth.tenantId(), orderId, flow.customerDirection(), flow.serviceMode());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> order = json.row(rows.get(0));
        List<OrderLineView> lines = json.rows(jdbc.queryForList("""
            SELECT
              id,
              line_no,
              item_name,
              sku,
              quantity,
              declared_value,
              declared_currency,
              weight_kg,
              metadata,
              created_at,
              updated_at
            FROM order_lines
            WHERE tenant_id = ?::uuid
              AND order_id = ?::uuid
              AND deleted_at IS NULL
            ORDER BY line_no, created_at
            """, auth.tenantId(), orderId))
            .stream()
            .map(OrderResponses::orderLineView)
            .toList();
        return OrderResponses.orderView(order, lines);
    }

    private String resolveCustomerId(AuthPrincipal auth, FlowDefinition flow, String customerId, String customerCode) {
        List<Map<String, Object>> rows;
        if (!isBlank(customerId)) {
            rows = jdbc.queryForList("""
                SELECT id
                FROM customers
                WHERE tenant_id = ?::uuid
                  AND id = ?::uuid
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                  AND customer_direction IN (?, 'BOTH')
                """, auth.tenantId(), customerId, flow.customerDirection());
        } else if (!isBlank(customerCode)) {
            rows = jdbc.queryForList("""
                SELECT id
                FROM customers
                WHERE tenant_id = ?::uuid
                  AND code = ?
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                  AND customer_direction IN (?, 'BOTH')
                """, auth.tenantId(), customerCode, flow.customerDirection());
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "customerId or customerCode is required");
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "customer not found or not allowed for this flow");
        }
        return rows.get(0).get("id").toString();
    }

    private void replaceLines(AuthPrincipal auth, String orderId, List<OrderRequests.Line> lines) {
        jdbc.update("""
            UPDATE order_lines
            SET deleted_at = now(),
                deleted_by = ?::uuid,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND order_id = ?::uuid
              AND deleted_at IS NULL
            """, auth.userId(), auth.userId(), auth.tenantId(), orderId);
        if (lines == null || lines.isEmpty()) {
            return;
        }

        int index = 1;
        for (OrderRequests.Line line : lines) {
            if (line == null || isBlank(line.itemName())) {
                throw new ResponseStatusException(BAD_REQUEST, "line itemName is required");
            }
            jdbc.update("""
                INSERT INTO order_lines (
                  tenant_id,
                  order_id,
                  line_no,
                  item_name,
                  sku,
                  quantity,
                  declared_value,
                  declared_currency,
                  weight_kg,
                  metadata,
                  created_by,
                  updated_by
                )
                VALUES (
                  ?::uuid,
                  ?::uuid,
                  ?,
                  ?,
                  ?,
                  COALESCE(?, 1),
                  ?,
                  ?,
                  ?,
                  ?::jsonb,
                  ?::uuid,
                  ?::uuid
                )
                """,
                auth.tenantId(),
                orderId,
                line.lineNo() == null ? index : line.lineNo(),
                line.itemName(),
                line.sku(),
                line.quantity(),
                line.declaredValue(),
                line.declaredCurrency(),
                line.weightKg(),
                json.toJson(line.metadata()),
                auth.userId(),
                auth.userId()
            );
            index++;
        }
    }

    private String generateOrderNo(String prefix) {
        return prefix + "-" + ORDER_NO_TIME.format(LocalDateTime.now());
    }

    private String nonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
