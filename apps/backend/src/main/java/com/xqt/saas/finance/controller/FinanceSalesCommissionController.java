package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceSalesCommissionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSalesCommissionView;
import com.xqt.saas.finance.entity.FinanceSalesCommission;
import com.xqt.saas.finance.service.FinanceSalesCommissionService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 销售提成单
 * 提供销售提成单的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-sales-commissions")
public class FinanceSalesCommissionController {

    @Resource
    private FinanceSalesCommissionService financeSalesCommissionService;

    /**
     * 保存销售提成单
     */
    @PostMapping
    public R save(@RequestBody FinanceSalesCommissionSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeSalesCommissionService.save(request));





        //TODO
        /**
         在260513sql 创建运价维护表：
         名称	服务	收货区域	优先级	用户等级	用户	最小重量	最大重量	邮编开头	父级	状态 价格类型 1.公布价 2.销售员底价 3.成本价
         并在finance编写CRUD接口

         在260513sql 创建应付报表表：
         用户
         币种
         营业额
         已支付
         待支付
         已出账单
         待出账单
         已付账单
         待付账单
         并在finance编写CRUD接口和导出

         在260513sql 创建运单审计表：
         运单号	用户	服务	国家	件数	实重	材重	收费重	供应商重量 状态：1已收货 2转运中 3已签收 4退件	应收	应付	销售成本	销售提成	毛利	客服代表	销售代表	拣货时间
         并在finance编写CRUD接口和导入导出接口

         在260513sql 创建财务流水表：
         流水号	用户	公司账户	用户账户	币种	金额	手续费 类型 1客户充值 2客户提现 3支付供应商 4供应商退款 5经营收入 6经营支出 7工资发放 8提成发放 9内部转账	审核流水号	支付状态	是否已开票	账单	审核时间	支付时间
         并在finance编写CRUD接口和导入导出接口
         */



    }

    /**
     * 更新销售提成单
     */
    @PutMapping
    public R update(@RequestBody FinanceSalesCommissionSaveRequest request) {
        return R.success("更新成功", financeSalesCommissionService.update(request));
    }

    /**
     * 删除销售提成单
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeSalesCommissionService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询销售提成单
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeSalesCommissionService.getById(id));
    }

    /**
     * 分页查询销售提成单列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceSalesCommissionQueryRequest request) {
        if (request == null) {
            request = new FinanceSalesCommissionQueryRequest();
        }
        IPage<FinanceSalesCommissionView> pageResult = financeSalesCommissionService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询销售提成单列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceSalesCommissionQueryRequest request) {
        if (request == null) {
            request = new FinanceSalesCommissionQueryRequest();
        }
        return R.success(financeSalesCommissionService.list(request));
    }

    /**
     * 导出销售提成单Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String commissionNo,
            @RequestParam(required = false) String sales,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) java.time.LocalDateTime billDateStart,
            @RequestParam(required = false) java.time.LocalDateTime billDateEnd,
            @RequestParam(required = false) java.time.LocalDateTime dueDateStart,
            @RequestParam(required = false) java.time.LocalDateTime dueDateEnd) throws IOException {
        
        FinanceSalesCommissionQueryRequest request = new FinanceSalesCommissionQueryRequest();
        request.setCommissionNo(commissionNo);
        request.setSales(sales);
        request.setCurrency(currency);
        request.setStatus(status);
        request.setRemark(remark);
        request.setBillDateStart(billDateStart);
        request.setBillDateEnd(billDateEnd);
        request.setDueDateStart(dueDateStart);
        request.setDueDateEnd(dueDateEnd);

        List<FinanceSalesCommission> dataList = financeSalesCommissionService.exportExcel(request);
        List<FinanceSalesCommissionExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceSalesCommissionExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("销售提成单")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("销售提成单.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceSalesCommissionExcelDTO convertToExcelDTO(FinanceSalesCommission entity) {
        FinanceSalesCommissionExcelDTO dto = new FinanceSalesCommissionExcelDTO();
        dto.setCommissionNo(entity.getCommissionNo());
        dto.setSales(entity.getSales());
        dto.setCurrency(entity.getCurrency());
        dto.setCommissionAmount(entity.getCommissionAmount());
        dto.setStatusText(FinanceSalesCommissionService.getStatusText(entity.getStatus()));
        dto.setPaid(entity.getPaid());
        dto.setBalance(entity.getBalance());
        dto.setRemark(entity.getRemark());
        dto.setBillDate(entity.getBillDate());
        dto.setDueDate(entity.getDueDate());
        dto.setAuditTime(entity.getAuditTime());
        return dto;
    }
}
