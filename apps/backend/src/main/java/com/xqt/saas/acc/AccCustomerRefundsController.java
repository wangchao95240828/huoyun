package com.xqt.saas.acc;

import java.util.Map;

import com.xqt.saas.common.BranchAccessFilter;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/acc/customer-refunds")
public class AccCustomerRefundsController extends AccFinanceTxnsBase {
        private final BranchAccessFilter branchAccess;

public AccCustomerRefundsController(JdbcTemplate jdbc, JsonSupport json,
                                        CascadeChecker cascadeChecker, FieldGate fieldGate,
                                        MoneySnapshotService moneySnapshotService,
                                  BranchAccessFilter branchAccess) {
        super(jdbc, json, cascadeChecker, fieldGate, moneySnapshotService, branchAccess);
            this.branchAccess = branchAccess;
    }
    @Override protected String side()    { return "CUSTOMER"; }
    @Override protected String txnType() { return "REFUND"; }
    @Override protected String labelCn() { return "客户退款"; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required=false) Integer page,
                                    @RequestParam(required=false) Integer pageSize,
                                    @RequestParam(required=false) String keyword,
                                    @RequestParam(required=false) String dateFrom,
                                    @RequestParam(required=false) String dateTo,
                                    @RequestParam(required=false) String status) {
        return listImpl(page, pageSize, keyword, dateFrom, dateTo, status);
    }
    @GetMapping("/{id}/raw") public Map<String, Object> raw(@PathVariable String id) { return rawImpl(id); }
    @PostMapping public Map<String, Object> create(@RequestBody Map<String, Object> body) { return createImpl(body); }
    @PutMapping("/{id}") public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) { return updateImpl(id, body); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(@PathVariable String id) { return deleteImpl(id); }

    /** 批量汇款：body = { ids:[...], financialAccountId, remitName } */
    @PostMapping("/batch-remit")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchRemit(@RequestBody Map<String, Object> body) {
        java.util.List<String> ids = (java.util.List<String>) body.getOrDefault("ids", java.util.List.of());
        String acct = (String) body.get("financialAccountId");
        String remitName = (String) body.getOrDefault("remitName", "");
        return batchRemitImpl(ids, acct, remitName);
    }
}
