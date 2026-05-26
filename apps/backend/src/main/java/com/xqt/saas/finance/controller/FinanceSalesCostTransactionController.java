package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceSalesCostTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSalesCostTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSalesCostTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSalesCostTransactionView;
import com.xqt.saas.finance.entity.FinanceSalesCostTransaction;
import com.xqt.saas.finance.service.FinanceSalesCostTransactionService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 销售成本流水控制器
 * 提供销售成本流水的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-sales-cost-transactions")
public class FinanceSalesCostTransactionController {

    @Resource
    private FinanceSalesCostTransactionService financeSalesCostTransactionService;

    /**
     * 保存销售成本流水
     */
    @PostMapping
    public R save(@RequestBody FinanceSalesCostTransactionSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeSalesCostTransactionService.save(request));
    }

    /**
     * 更新销售成本流水
     */
    @PutMapping
    public R update(@RequestBody FinanceSalesCostTransactionSaveRequest request) {
        return R.success("更新成功", financeSalesCostTransactionService.update(request));
    }

    /**
     * 删除销售成本流水
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeSalesCostTransactionService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询销售成本流水
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeSalesCostTransactionService.getById(id));
    }

    /**
     * 分页查询销售成本流水列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceSalesCostTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceSalesCostTransactionQueryRequest();
        }
        IPage<FinanceSalesCostTransactionView> pageResult = financeSalesCostTransactionService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询销售成本流水列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceSalesCostTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceSalesCostTransactionQueryRequest();
        }
        return R.success(financeSalesCostTransactionService.list(request));
    }

    /**
     * 导入销售成本流水Excel
     */
    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String tenantId = UserContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            List<FinanceSalesCostTransactionExcelDTO> dataList = EasyExcel.read(inputStream)
                    .head(FinanceSalesCostTransactionExcelDTO.class)
                    .sheet()
                    .doReadSync();
            
            financeSalesCostTransactionService.importExcel(dataList, tenantId);
            return R.success("导入成功");
        }
    }

    /**
     * 导出销售成本流水Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String transactionNo,
            @RequestParam(required = false) String employee,
            @RequestParam(required = false) String waybillNo,
            @RequestParam(required = false) String transferNo,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeStart,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeEnd) throws IOException {

        FinanceSalesCostTransactionQueryRequest request = new FinanceSalesCostTransactionQueryRequest();
        request.setTransactionNo(transactionNo);
        request.setEmployee(employee);
        request.setWaybillNo(waybillNo);
        request.setTransferNo(transferNo);
        request.setFeeType(feeType);
        request.setAuditStatus(auditStatus);
        request.setRemark(remark);
        request.setBusinessTimeStart(businessTimeStart);
        request.setBusinessTimeEnd(businessTimeEnd);

        List<FinanceSalesCostTransaction> dataList = financeSalesCostTransactionService.exportExcel(request);
        List<FinanceSalesCostTransactionExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceSalesCostTransactionExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("销售成本流水")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("销售成本流水.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceSalesCostTransactionExcelDTO convertToExcelDTO(FinanceSalesCostTransaction entity) {
        FinanceSalesCostTransactionExcelDTO dto = new FinanceSalesCostTransactionExcelDTO();
        dto.setTransactionNo(entity.getTransactionNo());
        dto.setEmployee(entity.getEmployee());
        dto.setWaybillNo(entity.getWaybillNo());
        dto.setTransferNo(entity.getTransferNo());
        dto.setFeeType(entity.getFeeType());
        dto.setFee(entity.getFee());
        dto.setExchangeRate(entity.getExchangeRate());
        dto.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        dto.setAuditStatusText(FinanceSalesCostTransactionService.getAuditStatusText(entity.getAuditStatus()));
        dto.setRemark(entity.getRemark());
        dto.setBusinessTime(entity.getBusinessTime());
        return dto;
    }
}
