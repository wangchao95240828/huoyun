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
