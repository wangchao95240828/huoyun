package com.xqt.saas.stowage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.customerapi.CustomerApiPrincipal;
import com.xqt.saas.stowage.StowageRequests.SyncRequest;
import com.xqt.saas.stowage.StowageResponses.SyncResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 复刻 ACC api/APIClass.php::doSync。逐项对照 ACC 校验和写入规则。
 *
 * ACC 行为顺序：
 *   1. 校验 No / Type / Count / Piece / Quantity / Weight / Category / Items 共 11 项
 *   2. 查 Stowage by No：不存在 → 新建；存在但客户不同 → 报错；存在且同客户 → 看是否需要更新
 *   3. 取 oldItems：当前 stowage 关联但不在新 items 列表里的 → 解绑
 *   4. 检查 TrackNo 冲突：items 中已被其它 stowage 绑定 → 报错
 *   5. 计算 updateItems：items 中 stowage != 当前 stowage 的 → 绑定到当前 stowage
 *   6. Lock + Insert/Update + 批量改 Online_Package_Item.Stowage
 */
@Service
public class StowageService {
    private final StowageRepository repository;
    private final JdbcTemplate jdbc;

    public StowageService(StowageRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncResult sync(CustomerApiPrincipal principal, SyncRequest body) {
        if (body == null) throw ApiException.badRequest("body is required");
        setTenant(principal);

        // === 校验（对应 ACC 11 项 case）===
        validateNo(body.no());
        validateType(body.type());
        validateInteger("Count", body.count(), false);
        validateInteger("Piece", body.piece(), true);
        validateInteger("Quantity", body.quantity(), true);
        validatePositiveNumber("Weight", body.weight());
        if (body.items() == null || body.items().isEmpty()) {
            throw ApiException.badRequest("当前配载暂未添加货件，请添加之后再同步。");
        }
        // Category 必须能查到（ACC: $rs->eof → output(248, '找不到指定的配载分类...')）
        String categoryId = null;
        if (body.category() != null && !body.category().isBlank()) {
            categoryId = repository.findCategoryIdByName(principal.tenantId(), body.category());
            if (categoryId == null) {
                throw ApiException.badRequest("找不到指定的配载分类【" + body.category() + "】，请联系服务商添加。");
            }
        }

        // === Items 反查 + 跨客户检查（对应 ACC `if($rs->get('a.Customer')!=$this->Customer)`） ===
        List<Map<String, Object>> cartons = repository.findCartonsByTrackingNos(
            principal.tenantId(), body.items());
        Map<String, Map<String, Object>> byTracking = new HashMap<>();
        for (Map<String, Object> row : cartons) {
            String track = ((String) row.get("tracking_no")).toLowerCase();
            byTracking.putIfAbsent(track, row);
            String customerId = (String) row.get("customer_id");
            if (!principal.customerId().equals(customerId)) {
                throw ApiException.badRequest("部分快件是其它客户的，你没有权限配载。");
            }
        }
        List<String> notFound = new ArrayList<>();
        for (String item : body.items()) {
            if (!byTracking.containsKey(item.toLowerCase())) notFound.add(item);
        }
        if (!notFound.isEmpty()) {
            throw ApiException.badRequest("共计有【" + notFound.size() + "】个快件无法在服务商的系统中找到，同步失败。");
        }

        // === 主单查找 + 客户归属检查（对应 ACC `if($rs->get('Customer')!=$this->Customer)`） ===
        Map<String, Object> existing = repository.findStowageByNo(principal.tenantId(), body.no());
        String stowageId = null;
        boolean created;
        if (existing != null) {
            String ownerId = (String) existing.get("customer_id");
            if (!principal.customerId().equals(ownerId)) {
                throw ApiException.badRequest("已经有其它客户的相同单号的配载存在，同步失败。");
            }
            stowageId = (String) existing.get("id");
        }

        // === TrackNo 冲突（已被其它 stowage 绑定）===
        List<String> conflictTracks = new ArrayList<>();
        List<String> updateCartonIds = new ArrayList<>();
        for (Map<String, Object> row : cartons) {
            String boundStowageId = (String) row.get("stowage_id");
            if (boundStowageId != null && !boundStowageId.equals(stowageId)) {
                conflictTracks.add((String) row.get("tracking_no"));
            } else if (!isSameStowage(boundStowageId, stowageId)) {
                updateCartonIds.add((String) row.get("carton_id"));
            }
        }
        if (!conflictTracks.isEmpty()) {
            String msg;
            if (conflictTracks.size() > 5) {
                msg = "单号【" + String.join(",", conflictTracks.subList(0, 5))
                    + "】等【" + conflictTracks.size() + "】个单号已经被其它的配载绑定了，同步失败。";
            } else {
                msg = "单号【" + String.join(",", conflictTracks) + "】已经被其它的配载绑定了，同步失败。";
            }
            throw ApiException.badRequest(msg);
        }

        // === oldItems：当前 stowage 关联但不在新 items 列表中的 ===
        List<String> keepCartonIds = new ArrayList<>();
        for (Map<String, Object> row : cartons) {
            keepCartonIds.add((String) row.get("carton_id"));
        }
        List<String> detachCartonIds = stowageId == null
            ? List.of()
            : repository.findCartonsToDetach(principal.tenantId(), stowageId, keepCartonIds);

        // === 端口 / 时间字段查找 ===
        String departurePortId = repository.findPortIdByName(principal.tenantId(), body.departurePort());
        String arrivalPortId = repository.findPortIdByName(principal.tenantId(), body.arrivalPort());
        String addName = (body.addName() == null ? "" : body.addName()) + "【同步】";

        // === Insert / Update ===
        if (stowageId == null) {
            stowageId = repository.insertStowage(
                principal.tenantId(), principal.customerId(), body.no(),
                body.theDate(),
                body.type() == null ? 1 : body.type(),
                categoryId,
                body.count(), body.piece(), body.quantity(),
                body.weight(), body.volume(),
                body.declaredValue() == null ? BigDecimal.ZERO : body.declaredValue(),
                body.taxAmount() == null ? BigDecimal.ZERO : body.taxAmount(),
                body.departureTime(), departurePortId,
                body.arrivalTime(), arrivalPortId,
                body.remark(), addName,
                body.addTime(), body.modifyTime()
            );
            created = true;
        } else {
            repository.updateStowage(
                stowageId,
                body.theDate(),
                body.type() == null ? 1 : body.type(),
                categoryId,
                body.count(), body.piece(), body.quantity(),
                body.weight(), body.volume(),
                body.declaredValue() == null ? BigDecimal.ZERO : body.declaredValue(),
                body.taxAmount() == null ? BigDecimal.ZERO : body.taxAmount(),
                body.departureTime(), departurePortId,
                body.arrivalTime(), arrivalPortId,
                body.remark(), addName,
                body.addTime(), body.modifyTime()
            );
            created = false;
        }

        int detached = repository.detachCartons(principal.tenantId(), detachCartonIds);
        int attached = repository.attachCartons(principal.tenantId(), stowageId, updateCartonIds);

        return new SyncResult(stowageId, body.no(), attached, detached, created);
    }

    // ===== 校验工具，与 ACC output(N, msg) 一一对齐 =====

    private void validateNo(String no) {
        if (no == null || no.isBlank()) {
            throw ApiException.badRequest("参数【No】不能为空。");
        }
        if (no.length() < 6 || no.length() > 30 || !no.matches("[0-9A-Za-z-]+")) {
            throw ApiException.badRequest("订单号长度必须是6-30，并且只能为【数字,字母,-】组合！");
        }
    }

    private void validateType(Integer type) {
        if (type == null || (type != 0 && type != 1)) {
            throw ApiException.badRequest("配载类型【" + type + "】必须为0或1");
        }
    }

    private void validateInteger(String name, Integer value, boolean positive) {
        if (value == null) {
            throw ApiException.badRequest(name + " 不能为空");
        }
        if (positive && value <= 0) {
            throw ApiException.badRequest(name + "【" + value + "】必须为大于零的整数");
        }
    }

    private void validatePositiveNumber(String name, BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw ApiException.badRequest(name + "【" + value + "】必须为大于零的数字");
        }
    }

    private boolean isSameStowage(String a, String b) {
        if (a == null && b == null) return false;  // 新 stowage 待绑定也算"不同"，待 attach
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void setTenant(CustomerApiPrincipal principal) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)",
            String.class, principal.tenantId());
    }
}
