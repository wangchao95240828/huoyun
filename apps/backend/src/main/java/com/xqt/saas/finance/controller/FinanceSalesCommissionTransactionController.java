package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceSalesCommissionTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSalesCommissionTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSalesCommissionTransactionView;
import com.xqt.saas.finance.entity.FinanceSalesCommissionTransaction;
import com.xqt.saas.finance.service.FinanceSalesCommissionTransactionService;
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
 * 销售提成流水控制器
 * 提供销售提成流水的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-sales-commission-transactions")
public class FinanceSalesCommissionTransactionController {

    @Resource
    private FinanceSalesCommissionTransactionService financeSalesCommissionTransactionService;

    /**
     * 保存销售提成流水
     */
    @PostMapping
    public R save(@RequestBody FinanceSalesCommissionTransactionSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeSalesCommissionTransactionService.save(request));
    }

    /**
     * 更新销售提成流水
     */
    @PutMapping
    public R update(@RequestBody FinanceSalesCommissionTransactionSaveRequest request) {
        return R.success("更新成功", financeSalesCommissionTransactionService.update(request));
    }

    /**
     * 删除销售提成流水
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeSalesCommissionTransactionService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询销售提成流水
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeSalesCommissionTransactionService.getById(id));
    }

    /**
     * 分页查询销售提成流水列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceSalesCommissionTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceSalesCommissionTransactionQueryRequest();
        }
        IPage<FinanceSalesCommissionTransactionView> pageResult = financeSalesCommissionTransactionService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询销售提成流水列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceSalesCommissionTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceSalesCommissionTransactionQueryRequest();
        }
        return R.success(financeSalesCommissionTransactionService.list(request));
    }

    /**
     * 导出销售提成流水Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String transactionNo,
            @RequestParam(required = false) String sales,
            @RequestParam(required = false) String commissionNo,
            @RequestParam(required = false) String waybillNo,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeStart,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeEnd) throws IOException {

        FinanceSalesCommissionTransactionQueryRequest request = new FinanceSalesCommissionTransactionQueryRequest();
        request.setTransactionNo(transactionNo);
        request.setSales(sales);
        request.setCommissionNo(commissionNo);
        request.setWaybillNo(waybillNo);
        request.setFeeType(feeType);
        request.setAuditStatus(auditStatus);
        request.setRemark(remark);
        request.setBusinessTimeStart(businessTimeStart);
        request.setBusinessTimeEnd(businessTimeEnd);

        List<FinanceSalesCommissionTransaction> dataList = financeSalesCommissionTransactionService.exportExcel(request);
        List<FinanceSalesCommissionTransactionExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceSalesCommissionTransactionExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("销售提成流水")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("销售提成流水.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceSalesCommissionTransactionExcelDTO convertToExcelDTO(FinanceSalesCommissionTransaction entity) {
        FinanceSalesCommissionTransactionExcelDTO dto = new FinanceSalesCommissionTransactionExcelDTO();
        dto.setTransactionNo(entity.getTransactionNo());
        dto.setSales(entity.getSales());
        dto.setCommissionNo(entity.getCommissionNo());
        dto.setWaybillNo(entity.getWaybillNo());
        dto.setFeeType(entity.getFeeType());
        dto.setFee(entity.getFee());
        dto.setExchangeRate(entity.getExchangeRate());
        dto.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        dto.setAuditStatusText(FinanceSalesCommissionTransactionService.getAuditStatusText(entity.getAuditStatus()));
        dto.setRemark(entity.getRemark());
        dto.setBusinessTime(entity.getBusinessTime());
        return dto;
    }
}
