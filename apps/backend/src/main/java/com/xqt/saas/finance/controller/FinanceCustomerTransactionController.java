package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceCustomerTransactionExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceCustomerTransactionQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceCustomerTransactionSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCustomerTransactionView;
import com.xqt.saas.finance.entity.FinanceCustomerTransaction;
import com.xqt.saas.finance.service.FinanceCustomerTransactionService;
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
 * 客户流水控制器
 * 提供客户流水的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-customer-transactions")
public class FinanceCustomerTransactionController {

    @Resource
    private FinanceCustomerTransactionService financeCustomerTransactionService;

    /**
     * 保存客户流水
     */
    @PostMapping
    public R save(@RequestBody FinanceCustomerTransactionSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeCustomerTransactionService.save(request));
    }

    /**
     * 更新客户流水
     */
    @PutMapping
    public R update(@RequestBody FinanceCustomerTransactionSaveRequest request) {
        return R.success("更新成功", financeCustomerTransactionService.update(request));
    }

    /**
     * 删除客户流水
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeCustomerTransactionService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询客户流水
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeCustomerTransactionService.getById(id));
    }

    /**
     * 分页查询客户流水列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceCustomerTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceCustomerTransactionQueryRequest();
        }
        IPage<FinanceCustomerTransactionView> pageResult = financeCustomerTransactionService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询客户流水列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceCustomerTransactionQueryRequest request) {
        if (request == null) {
            request = new FinanceCustomerTransactionQueryRequest();
        }
        return R.success(financeCustomerTransactionService.list(request));
    }

    /**
     * 导入客户流水Excel
     */
    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String tenantId = UserContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            List<FinanceCustomerTransactionExcelDTO> dataList = EasyExcel.read(inputStream)
                    .head(FinanceCustomerTransactionExcelDTO.class)
                    .sheet()
                    .doReadSync();
            
            financeCustomerTransactionService.importExcel(dataList, tenantId);
            return R.success("导入成功");
        }
    }

    /**
     * 导出客户流水Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String transactionNo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String userSalesRep,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String waybillNo,
            @RequestParam(required = false) String billOfLadingNo,
            @RequestParam(required = false) String transferNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer writeOffStatus,
            @RequestParam(required = false) Integer approvalStatus,
            @RequestParam(required = false) String approver,
            @RequestParam(required = false) String customFlag,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeStart,
            @RequestParam(required = false) java.time.LocalDateTime businessTimeEnd) throws IOException {

        FinanceCustomerTransactionQueryRequest request = new FinanceCustomerTransactionQueryRequest();
        request.setTransactionNo(transactionNo);
        request.setUserName(userName);
        request.setUserSalesRep(userSalesRep);
        request.setBillNo(billNo);
        request.setWaybillNo(waybillNo);
        request.setBillOfLadingNo(billOfLadingNo);
        request.setTransferNo(transferNo);
        request.setOrderNo(orderNo);
        request.setService(service);
        request.setFeeType(feeType);
        request.setAuditStatus(auditStatus);
        request.setWriteOffStatus(writeOffStatus);
        request.setApprovalStatus(approvalStatus);
        request.setApprover(approver);
        request.setCustomFlag(customFlag);
        request.setBusinessTimeStart(businessTimeStart);
        request.setBusinessTimeEnd(businessTimeEnd);

        List<FinanceCustomerTransaction> dataList = financeCustomerTransactionService.exportExcel(request);
        List<FinanceCustomerTransactionExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceCustomerTransactionExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("客户流水")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("客户流水.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceCustomerTransactionExcelDTO convertToExcelDTO(FinanceCustomerTransaction entity) {
        FinanceCustomerTransactionExcelDTO dto = new FinanceCustomerTransactionExcelDTO();
        dto.setTransactionNo(entity.getTransactionNo());
        dto.setUserName(entity.getUserName());
        dto.setUserSalesRep(entity.getUserSalesRep());
        dto.setBillNo(entity.getBillNo());
        dto.setWaybillNo(entity.getWaybillNo());
        dto.setBillOfLadingNo(entity.getBillOfLadingNo());
        dto.setTransferNo(entity.getTransferNo());
        dto.setOrderNo(entity.getOrderNo());
        dto.setService(entity.getService());
        dto.setFeeType(entity.getFeeType());
        dto.setQuantity(entity.getQuantity());
        dto.setFee(entity.getFee());
        dto.setExchangeRate(entity.getExchangeRate());
        dto.setLocalCurrencyFee(entity.getLocalCurrencyFee());
        dto.setAuditStatusText(FinanceCustomerTransactionService.getAuditStatusText(entity.getAuditStatus()));
        dto.setWriteOffStatusText(FinanceCustomerTransactionService.getWriteOffStatusText(entity.getWriteOffStatus()));
        dto.setApprovalStatusText(FinanceCustomerTransactionService.getApprovalStatusText(entity.getApprovalStatus()));
        dto.setApprover(entity.getApprover());
        dto.setCustomFlag(entity.getCustomFlag());
        dto.setRemark(entity.getRemark());
        dto.setWriteOffTime(entity.getWriteOffTime());
        dto.setBusinessTime(entity.getBusinessTime());
        return dto;
    }
}
