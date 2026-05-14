package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceTransactionView;
import com.xqt.saas.finance.entity.FinanceTransaction;
import com.xqt.saas.finance.service.FinanceTransactionService;
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
 * 财务流水控制器
 * 提供财务流水的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-transactions")
public class FinanceTransactionController {

    @Resource
    private FinanceTransactionService financeTransactionService;

    @PostMapping
    public R save(@RequestBody FinanceTransactionSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeTransactionService.save(request));
    }

    @PutMapping
    public R update(@RequestBody FinanceTransactionSaveRequest request) {
        return R.success("更新成功", financeTransactionService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeTransactionService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeTransactionService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceTransactionQueryRequest();
        }
        IPage<FinanceTransactionView> pageResult = financeTransactionService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceTransactionQueryRequest();
        }
        return R.success(financeTransactionService.list(request));
    }

    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String tenantId = UserContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            List<FinanceTransactionExcelDTO> dataList = EasyExcel.read(inputStream)
                    .head(FinanceTransactionExcelDTO.class)
                    .sheet()
                    .doReadSync();
            
            financeTransactionService.importExcel(dataList, tenantId);
            return R.success("导入成功");
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String transactionNo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String companyAccount,
            @RequestParam(required = false) String userAccount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer paymentStatus,
            @RequestParam(required = false) Boolean invoiced,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) java.time.LocalDateTime auditTimeStart,
            @RequestParam(required = false) java.time.LocalDateTime auditTimeEnd) throws IOException {

        FinanceTransactionQueryRequest request = new FinanceTransactionQueryRequest();
        request.setTransactionNo(transactionNo);
        request.setUserName(userName);
        request.setCompanyAccount(companyAccount);
        request.setUserAccount(userAccount);
        request.setCurrency(currency);
        request.setType(type);
        request.setPaymentStatus(paymentStatus);
        request.setInvoiced(invoiced);
        request.setBillNo(billNo);
        request.setAuditTimeStart(auditTimeStart);
        request.setAuditTimeEnd(auditTimeEnd);

        List<FinanceTransaction> dataList = financeTransactionService.exportExcel(request);
        List<FinanceTransactionExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceTransactionExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("财务流水")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("财务流水.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    private FinanceTransactionExcelDTO convertToExcelDTO(FinanceTransaction entity) {
        FinanceTransactionExcelDTO dto = new FinanceTransactionExcelDTO();
        dto.setTransactionNo(entity.getTransactionNo());
        dto.setUserName(entity.getUserName());
        dto.setCompanyAccount(entity.getCompanyAccount());
        dto.setUserAccount(entity.getUserAccount());
        dto.setCurrency(entity.getCurrency());
        dto.setAmount(entity.getAmount());
        dto.setFee(entity.getFee());
        dto.setTypeText(FinanceTransactionService.getTypeText(entity.getType()));
        dto.setAuditTransactionNo(entity.getAuditTransactionNo());
        dto.setPaymentStatusText(FinanceTransactionService.getPaymentStatusText(entity.getPaymentStatus()));
        dto.setInvoicedText(FinanceTransactionService.getInvoicedText(entity.getInvoiced()));
        dto.setBillNo(entity.getBillNo());
        dto.setAuditTime(entity.getAuditTime());
        dto.setPaymentTime(entity.getPaymentTime());
        return dto;
    }
}
