package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerApiRepository {
    private final JdbcTemplate jdbc;
    private final com.xqt.saas.finance.FxSnapshotCapture fxCapture;

    public CustomerApiRepository(JdbcTemplate jdbc,
                                  com.xqt.saas.finance.FxSnapshotCapture fxCapture) {
        this.jdbc = jdbc;
        this.fxCapture = fxCapture;
    }

    @Transactional(readOnly = true)
    public CustomerApiCredential findByAccessKey(String accessKey) {
        enableServiceRole();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              ac.id::text AS credential_id,
              ac.tenant_id::text AS tenant_id,
              ac.owner_id::text AS customer_id,
              c.code AS customer_code,
              ac.access_key,
              ac.secret_hash,
              ac.status
            FROM api_credentials ac
            JOIN customers c ON c.id = ac.owner_id AND c.tenant_id = ac.tenant_id
            WHERE ac.access_key = ?
              AND ac.owner_type = 'CUSTOMER'
            """, accessKey);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new CustomerApiCredential(
            (String) row.get("credential_id"),
            (String) row.get("tenant_id"),
            (String) row.get("customer_id"),
            (String) row.get("customer_code"),
            (String) row.get("access_key"),
            (String) row.get("secret_hash"),
            (String) row.get("status")
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void markCalled(String credentialId) {
        enableServiceRole();
        jdbc.update("""
            UPDATE api_credentials
            SET last_used_at = now()
            WHERE id = ?::uuid
            """, credentialId);
    }

    private void enableServiceRole() {
        jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
    }

    public List<Map<String, Object>> findBalances(String tenantId, String customerId) {
        return jdbc.queryForList("""
            SELECT currency, balance, account_name, status
            FROM financial_accounts
            WHERE tenant_id = ?::uuid
              AND owner_type = 'CUSTOMER'
              AND owner_id = ?::uuid
              AND metadata->>'purpose' = 'customer-api.balance'
              AND status = 'ACTIVE'
            ORDER BY currency
            """, tenantId, customerId);
    }

    /** 拿订单当前状态 + metadata，给 Submit/Modify/Cancel 的状态机用。 */
    public Map<String, Object> findOrderForCustomerApi(String tenantId, String customerId, String no) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              o.id::text       AS order_id,
              o.order_no,
              o.customer_ref,
              o.status,
              o.metadata
            FROM orders o
            WHERE o.tenant_id = ?::uuid
              AND o.customer_id = ?::uuid
              AND (o.customer_ref = ? OR o.order_no = ?)
            LIMIT 1
            """, tenantId, customerId, no, no);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Submit：把 orders 切到 SUBMITTED，记 submitted_at。 */
    public int markOrderSubmitted(String orderId) {
        return jdbc.update("""
            UPDATE orders
            SET status = 'SUBMITTED', submitted_at = now()
            WHERE id = ?::uuid AND status = 'DRAFT'
            """, orderId);
    }

    /** Modify：仅 DRAFT 状态允许覆写 metadata。 */
    public int updateOrderMetadata(String orderId, String metadataJson) {
        return jdbc.update("""
            UPDATE orders
            SET metadata = ?::jsonb
            WHERE id = ?::uuid AND status = 'DRAFT'
            """, metadataJson, orderId);
    }

    /** Cancel：把 orders 切到 CANCELLED；状态机由 service 层先校验。 */
    public int markOrderCancelled(String orderId) {
        return jdbc.update("""
            UPDATE orders
            SET status = 'CANCELLED'
            WHERE id = ?::uuid AND status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'FULFILLING')
            """, orderId);
    }

    public String findChannelIdByCode(String tenantId, String code) {
        List<String> rows = jdbc.queryForList("""
            SELECT id::text FROM channels
            WHERE tenant_id = ?::uuid AND code = ? AND active = true
            LIMIT 1
            """, String.class, tenantId, code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String insertShipment(String tenantId, String customerId, String channelId,
                                 String shipmentNo, String customerRef,
                                 String country, BigDecimal declaredValue,
                                 String declaredCurrency) {
        return jdbc.queryForObject("""
            INSERT INTO shipments (
              tenant_id, customer_id, channel_id, shipment_no, customer_ref,
              status, destination_country, declared_value, declared_currency, ordered_at
            ) VALUES (
              ?::uuid, ?::uuid, ?::uuid, ?, ?,
              'ORDERED', ?, ?, ?, now()
            )
            RETURNING id::text
            """, String.class,
            tenantId, customerId, channelId, shipmentNo, customerRef,
            country, declaredValue, declaredCurrency);
    }

    /**
     * 任务 S8：建立 shipment ↔ order 强关联（替代 customer_ref 软关联）。
     * 幂等：ON CONFLICT DO NOTHING（同 shipment-order 重复提交不报错）。
     */
    public void insertShipmentOrderLink(String tenantId, String shipmentId, String orderId,
                                         String linkType) {
        jdbc.update("""
            INSERT INTO shipment_order_links (tenant_id, shipment_id, order_id, link_type)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?)
            ON CONFLICT (tenant_id, shipment_id, order_id) DO NOTHING
            """, tenantId, shipmentId, orderId, linkType == null ? "SUBMIT" : linkType);
    }

    public void insertCarton(String tenantId, String shipmentId, String cartonNo,
                             BigDecimal actualWeightKg, String trackingNo,
                             String carrierMasterTrackingNo) {
        insertCarton(tenantId, shipmentId, cartonNo, actualWeightKg, trackingNo,
            carrierMasterTrackingNo, null);
    }

    /**
     * 带 provider evidence 的 insertCarton：取号成功后落 carrier 的 request/response，
     * 便于事后对账 / 排障（对应 ACC Express_Status 文本记录的 evidence 化升级）。
     */
    public void insertCarton(String tenantId, String shipmentId, String cartonNo,
                             BigDecimal actualWeightKg, String trackingNo,
                             String carrierMasterTrackingNo, String evidenceJson) {
        jdbc.update("""
            INSERT INTO cartons (
              tenant_id, shipment_id, carton_no, actual_weight_kg,
              tracking_no, carrier_master_tracking_no, carrier_evidence
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, coalesce(?::jsonb, '{}'::jsonb))
            """, tenantId, shipmentId, cartonNo, actualWeightKg, trackingNo,
            carrierMasterTrackingNo, evidenceJson);
    }

    public void insertDeclaration(String tenantId, String shipmentId, String itemName,
                                  String material, String hsCode, BigDecimal quantity,
                                  BigDecimal valueAmount, String attributesJson) {
        jdbc.update("""
            INSERT INTO declarations (
              tenant_id, shipment_id, item_name, material, hs_code,
              quantity, value_amount, attributes
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?::jsonb)
            """, tenantId, shipmentId, itemName, material, hsCode,
            quantity, valueAmount, attributesJson);
    }

    public int markShipmentExceptionForOrder(String tenantId, String customerRef) {
        return jdbc.update("""
            UPDATE shipments
            SET status = 'EXCEPTION'
            WHERE tenant_id = ?::uuid
              AND customer_ref = ?
              AND status <> 'CLOSED'
              AND status <> 'DELIVERED'
            """, tenantId, customerRef);
    }

    public String insertDraftOrder(String tenantId, String customerId, String orderNo,
                                   String customerRef, String metadataJson) {
        return jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref,
              metadata, customer_direction, order_entry_type, service_mode
            ) VALUES (
              ?::uuid, ?, ?::uuid, 'DRAFT', 'LOCAL', ?,
              ?::jsonb, 'DOCUMENT_CUSTOMER', 'API_ORDER', 'DOCUMENT_SHIPPING'
            )
            RETURNING id::text
            """, String.class, tenantId, orderNo, customerId, customerRef, metadataJson);
    }

    /**
     * 对应 ACC act=Status：按客户提交的 No 反查订单当前状态。
     * 兼容两种命中：orders.customer_ref（旧 No 原值）或 orders.order_no（新生成号）。
     */
    public List<Map<String, Object>> findOrderStatuses(String tenantId, String customerId, List<String> nos) {
        if (nos == null || nos.isEmpty()) {
            return List.of();
        }
        String[] arr = nos.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              o.order_no,
              o.customer_ref,
              o.status
            FROM orders o
            WHERE o.tenant_id = ?::uuid
              AND o.customer_id = ?::uuid
              AND (o.customer_ref = ANY (?) OR o.order_no = ANY (?))
            """, tenantId, customerId, arr, arr);
    }

    /**
     * 对应 ACC act=Query：拉订单主档 + 元数据（receiver/declare 原样存在 metadata.acc_compat）。
     * declarations / cartons 在 Submit 之后才会有数据，目前仅返回 orders 维度。
     */
    public List<Map<String, Object>> findOrdersDetail(String tenantId, String customerId, List<String> nos) {
        if (nos == null || nos.isEmpty()) {
            return List.of();
        }
        String[] arr = nos.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              o.id::text         AS order_id,
              o.order_no,
              o.customer_ref,
              o.status,
              o.metadata,
              o.created_at
            FROM orders o
            WHERE o.tenant_id = ?::uuid
              AND o.customer_id = ?::uuid
              AND (o.customer_ref = ANY (?) OR o.order_no = ANY (?))
            """, tenantId, customerId, arr, arr);
    }

    /** Submit 之后会落 shipments；现在 cartons.tracking_no 也可能为空，单独查容错。 */
    public List<Map<String, Object>> findShipmentTrackingNos(String tenantId, List<String> orderRefs) {
        if (orderRefs == null || orderRefs.isEmpty()) {
            return List.of();
        }
        String[] arr = orderRefs.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              s.shipment_no,
              s.customer_ref,
              s.status::text AS shipment_status,
              c.tracking_no,
              c.carrier_master_tracking_no
            FROM shipments s
            LEFT JOIN cartons c ON c.shipment_id = s.id
            WHERE s.tenant_id = ?::uuid
              AND (s.shipment_no = ANY (?) OR s.customer_ref = ANY (?))
            ORDER BY s.shipment_no, c.carton_no
            """, tenantId, arr, arr);
    }

    /**
     * 对应 ACC act=Track：按 No 找到当前客户的 shipments 主档（含一个代表性 tracking_no）。
     * shipments 表只有在 Submit 之后才会有行，Draft 阶段会返回空集合（前端要兜底空 Track）。
     */
    public List<Map<String, Object>> findShipmentsForTracking(String tenantId, String customerId, List<String> nos) {
        if (nos == null || nos.isEmpty()) {
            return List.of();
        }
        String[] arr = nos.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              s.id::text         AS shipment_id,
              s.shipment_no,
              s.customer_ref,
              s.status::text     AS status,
              (
                SELECT c2.tracking_no FROM cartons c2
                WHERE c2.shipment_id = s.id AND c2.tracking_no IS NOT NULL
                ORDER BY c2.carton_no LIMIT 1
              ) AS first_tracking_no
            FROM shipments s
            WHERE s.tenant_id = ?::uuid
              AND s.customer_id = ?::uuid
              AND (s.shipment_no = ANY (?) OR s.customer_ref = ANY (?))
            """, tenantId, customerId, arr, arr);
    }

    /** 拉一组 shipment_id 上的全部 tracking_events，按时间正序。 */
    public List<Map<String, Object>> findTrackingEvents(String tenantId, List<String> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return List.of();
        }
        String[] arr = shipmentIds.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              shipment_id::text                AS shipment_id,
              event_time,
              raw_status,
              normalized_status::text          AS normalized_status,
              location,
              source::text                     AS source,
              tracking_no
            FROM tracking_events
            WHERE tenant_id = ?::uuid
              AND shipment_id = ANY (?::uuid[])
            ORDER BY shipment_id, event_time
            """, tenantId, arr);
    }

    /** 对应 ACC act=Product / act=Channel：返回租户启用的渠道列表（带 channel_code）。 */
    public List<Map<String, Object>> findActiveChannels(String tenantId) {
        return jdbc.queryForList("""
            SELECT
              code,
              name,
              lane,
              last_mile_method,
              active
            FROM channels
            WHERE tenant_id = ?::uuid
              AND active = true
            ORDER BY code
            """, tenantId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 余额预扣 / 反扣 —— 复刻 ACC Customer_Balance + Express_Charge
    // ─────────────────────────────────────────────────────────────────────────

    /** 按客户+币种找预付余额账户。对应 ACC 旧 Customer_Balance 行。 */
    public Map<String, Object> findCustomerBalanceAccount(String tenantId, String customerId, String currency) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, balance, currency, account_name
            FROM financial_accounts
            WHERE tenant_id = ?::uuid
              AND owner_type = 'CUSTOMER'
              AND owner_id = ?::uuid
              AND currency = ?
              AND metadata->>'purpose' = 'customer-api.balance'
              AND status = 'ACTIVE'
            LIMIT 1
            """, tenantId, customerId, currency);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 扣余额（balance -= amount）。返回是否成功扣减。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean decrementBalance(String accountId, BigDecimal amount) {
        int rows = jdbc.update("""
            UPDATE financial_accounts
            SET balance = balance - ?
            WHERE id = ?::uuid AND status = 'ACTIVE'
            """, amount, accountId);
        return rows > 0;
    }

    /** 反扣余额（balance += amount）。用于 Cancel 时退还。 */
    @Transactional(rollbackFor = Exception.class)
    public int restoreBalance(String accountId, BigDecimal amount) {
        return jdbc.update("""
            UPDATE financial_accounts
            SET balance = balance + ?
            WHERE id = ?::uuid
            """, amount, accountId);
    }

    /** 落预扣费用行。对应 ACC Express_Charge 表的"提交预扣"。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertPrepaidCharge(String tenantId, String shipmentId, String chargeItemId,
                                      BigDecimal amount, String currency, String evidenceJson) {
        return jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount,
              evidence
            ) VALUES (
              ?::uuid, ?::uuid, ?::uuid, 'AR', 'ESTIMATED', ?, ?, ?::jsonb
            )
            RETURNING id::text
            """, String.class, tenantId, shipmentId, chargeItemId,
            currency, amount, evidenceJson);
    }

    /** 落 AP 成本估算行（status='ESTIMATED' side='AP'）。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertCostCharge(String tenantId, String shipmentId, String chargeItemId,
                                   BigDecimal amount, String currency, String evidenceJson) {
        return jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount,
              evidence
            ) VALUES (
              ?::uuid, ?::uuid, ?::uuid, 'AP', 'ESTIMATED', ?, ?, ?::jsonb
            )
            RETURNING id::text
            """, String.class, tenantId, shipmentId, chargeItemId,
            currency, amount, evidenceJson);
    }

    /**
     * 按 charge_item code 找 id（FREIGHT / FUEL / REMOTE 等）。
     * 找不到时回退到默认 FREIGHT，保证拆行时永远有 charge_item_id 可用。
     */
    public String findChargeItemIdByCode(String tenantId, String code) {
        try {
            return jdbc.queryForObject("""
                SELECT id::text FROM charge_items
                WHERE tenant_id = ?::uuid AND code = ?
                LIMIT 1
                """, String.class, tenantId, code);
        } catch (org.springframework.dao.DataAccessException ex) {
            return findDefaultFreightChargeItemId(tenantId);
        }
    }

    /** 落一笔指定 side/status 的费用行（用于 Submit 拆 AR/AP 多费用行）。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertChargeLine(String tenantId, String shipmentId, String chargeItemId,
                                   String side, BigDecimal amount, String currency, String evidenceJson) {
        return jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount, evidence
            ) VALUES (
              ?::uuid, ?::uuid, ?::uuid, ?::charge_side, 'ESTIMATED', ?, ?, ?::jsonb
            )
            RETURNING id::text
            """, String.class, tenantId, shipmentId, chargeItemId,
            side, currency, amount, evidenceJson);
    }

    /** 读资金账户当前余额（写流水时取 before/after 用）。 */
    public BigDecimal findAccountBalance(String accountId) {
        try {
            return jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    /**
     * 写一条资金账户流水（balance_ledger）。对应 ACC Customer_Balance_History。
     * amount 传正数，方向由 direction（CREDIT 增 / DEBIT 减）表达。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordBalanceLedger(String tenantId, String accountId, String ownerType,
                                    String ownerId, String bizType, String sourceType,
                                    String sourceRef, String currency, String direction,
                                    BigDecimal amount, BigDecimal balanceBefore,
                                    BigDecimal balanceAfter, String operator, String remark) {
        jdbc.update("""
            INSERT INTO balance_ledger (
              tenant_id, account_id, owner_type, owner_id, biz_type,
              source_type, source_ref, currency, direction, amount,
              balance_before, balance_after, operator, remark
            ) VALUES (
              ?::uuid, ?::uuid, ?, ?::uuid, ?::balance_ledger_biz_type,
              ?, ?, ?, ?::balance_ledger_direction, ?,
              ?, ?, ?, ?
            )
            """, tenantId, accountId, ownerType, ownerId, bizType,
            sourceType, sourceRef, currency, direction, amount,
            balanceBefore, balanceAfter, operator, remark);
        // 任务 S6：fx 快照捕获（11 类 biz_type 全部覆盖）
        fxCapture.captureForLedger(tenantId, currency, bizType, sourceType, sourceRef);
    }

    /** 查某资金账户的流水（余额追溯）。 */
    public List<Map<String, Object>> findBalanceLedger(String tenantId, String accountId, int limit) {
        return jdbc.queryForList("""
            SELECT id::text AS id, biz_type, source_type, source_ref, currency,
                   direction, amount, balance_before, balance_after, operator, remark, created_at
            FROM balance_ledger
            WHERE tenant_id = ?::uuid AND account_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT ?
            """, tenantId, accountId, Math.min(limit, 200));
    }

    /**
     * Submit 取号成功后累加渠道账号当日票池（channel_account_daily_usage）。
     * upsert：当天首单插入，后续累加 count/piece/weight。RateEngine 限额检查读这张表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bumpChannelAccountUsage(String tenantId, String channelId, String accountCode,
                                        int piece, BigDecimal weight) {
        if (accountCode == null || accountCode.isBlank() || channelId == null) return;
        jdbc.update("""
            INSERT INTO channel_account_daily_usage (
              tenant_id, channel_id, account_code, usage_date, count, piece, weight
            ) VALUES (?::uuid, ?::uuid, ?, current_date, 1, ?, ?)
            ON CONFLICT (tenant_id, channel_id, account_code, usage_date)
            DO UPDATE SET
              count  = channel_account_daily_usage.count + 1,
              piece  = channel_account_daily_usage.piece + excluded.piece,
              weight = channel_account_daily_usage.weight + excluded.weight
            """, tenantId, channelId, accountCode, piece,
            weight == null ? BigDecimal.ZERO : weight);
    }

    /** 找一条默认 FREIGHT 类型的 charge_item，用于落预扣行。 */
    public String findDefaultFreightChargeItemId(String tenantId) {
        try {
            return jdbc.queryForObject("""
                SELECT id::text FROM charge_items
                WHERE tenant_id = ?::uuid
                ORDER BY (category = 'FREIGHT') DESC, code
                LIMIT 1
                """, String.class, tenantId);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    /** 找一笔订单/运单关联的所有"预扣"AR 费用。Cancel 时用来反扣。 */
    public List<Map<String, Object>> findPrepaidCharges(String tenantId, String customerRef) {
        return jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.currency, ch.evidence
            FROM charges ch
            JOIN shipments s ON s.id = ch.shipment_id
            WHERE ch.tenant_id = ?::uuid
              AND s.customer_ref = ?
              AND ch.side = 'AR'
              AND ch.status = 'ESTIMATED'
              AND ch.evidence->>'prepaid' = 'true'
            """, tenantId, customerRef);
    }

    /** 把一组 ESTIMATED 的预扣 charges 标 VOID。 */
    @Transactional(rollbackFor = Exception.class)
    public int voidPrepaidCharges(String tenantId, List<String> chargeIds) {
        if (chargeIds == null || chargeIds.isEmpty()) return 0;
        String[] arr = chargeIds.toArray(new String[0]);
        return jdbc.update("""
            UPDATE charges
            SET status = 'VOID'
            WHERE tenant_id = ?::uuid
              AND id = ANY (?::uuid[])
              AND status = 'ESTIMATED'
            """, tenantId, arr);
    }
}
