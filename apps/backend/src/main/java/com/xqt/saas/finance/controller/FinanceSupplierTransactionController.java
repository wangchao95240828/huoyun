package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceSupplierTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSupplierTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSupplierTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSupplierTransactionView;
import com.xqt.saas.finance.entity.FinanceSupplierTransaction;
import com.xqt.saas.finance.service.FinanceSupplierTransactionService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商流水控制器
 * 提供供应商流水的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-supplier-transactions")
public class FinanceSupplierTransactionController {

    @Resource
    private FinanceSupplierTransactionService financeSupplierTransactionService;

    /**
     * 保存供应商流水
     */
    @PostMapping
    public R save(@RequestBody FinanceSupplierTransactionSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeSupplierTransactionService.save(request));
    }

    /**
     * 更新供应商流水
     */
    @PutMapping
    public R update(@RequestBody FinanceSupplierTransactionSaveRequest request) {
        return R.success("更新成功", financeSupplierTransactionService.update(request));
    }

    /**
     * 删除供应商流水
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeSupplierTransactionService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询供应商流水
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeSupplierTransactionService.getById(id));
    }

    /**
     * 分页查询供应商流水列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceSupplierTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceSupplierTransactionQueryRequest();
        }
        IPage<FinanceSupplierTransactionView> pageResult = financeSupplierTransactionService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询供应商流水列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceSupplierTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceSupplierTransactionQueryRequest();
        }
        return R.success(financeSupplierTransactionService.list(request));
    }

    /**
     * 导入供应商流水Excel
     */
    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String tenantId = UserContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            List<FinanceSupplierTransactionExcelDTO> dataList = EasyExcel.read(inputStream)
                    .head(FinanceSupplierTransactionExcelDTO.class)
                    .sheet()
                    .doReadSync();
            
            financeSupplierTransactionService.importExcel(dataList, tenantId);
            return R.success("导入成功");
        }
    }

    /**
     * 导出供应商流水Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String transactionNo,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String waybillNo,
            @RequestParam(required = false) String waybillSales,
            @RequestParam(required = false) String billOfLadingNo,
            @RequestParam(required = false) String transferNo,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer writeOffStatus,
            @RequestParam(required = false) String customFlag,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeStart,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeEnd) throws IOException {

        FinanceSupplierTransactionQueryRequest request = new FinanceSupplierTransactionQueryRequest();
        request.setTransactionNo(transactionNo);
        request.setSupplier(supplier);
        request.setBillNo(billNo);
        request.setWaybillNo(waybillNo);
        request.setWaybillSales(waybillSales);
        request.setBillOfLadingNo(billOfLadingNo);
        request.setTransferNo(transferNo);
        request.setFeeType(feeType);
        request.setAuditStatus(auditStatus);
        request.setWriteOffStatus(writeOffStatus);
        request.setCustomFlag(customFlag);
        request.setBusinessTimeStart(businessTimeStart);
        request.setBusinessTimeEnd(businessTimeEnd);

        List<FinanceSupplierTransaction> dataList = financeSupplierTransactionService.exportExcel(request);
        List<FinanceSupplierTransactionExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceSupplierTransactionExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("供应商流水")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("供应商流水.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceSupplierTransactionExcelDTO convertToExcelDTO(FinanceSupplierTransaction entity) {
        FinanceSupplierTransactionExcelDTO dto = new FinanceSupplierTransactionExcelDTO();
        dto.setTransactionNo(entity.getTransactionNo());
        dto.setSupplier(entity.getSupplier());
        dto.setBillNo(entity.getBillNo());
        dto.setWaybillNo(entity.getWaybillNo());
        dto.setWaybillSales(entity.getWaybillSales());
        dto.setBillOfLadingNo(entity.getBillOfLadingNo());
        dto.setTransferNo(entity.getTransferNo());
        dto.setFeeType(entity.getFeeType());
        dto.setQuantity(entity.getQuantity());
        dto.setFee(entity.getFee());
        dto.setExchangeRate(entity.getExchangeRate());
        dto.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        dto.setAuditStatusText(FinanceSupplierTransactionService.getAuditStatusText(entity.getAuditStatus()));
        dto.setWriteOffStatusText(FinanceSupplierTransactionService.getWriteOffStatusText(entity.getWriteOffStatus()));
        dto.setCustomFlag(entity.getCustomFlag());
        dto.setWriteOffTime(entity.getWriteOffTime());
        dto.setBusinessTime(entity.getBusinessTime());
        return dto;
    }
}
