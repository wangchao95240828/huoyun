package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceCustomerBillExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceCustomerBillQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceCustomerBillSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceCustomerBillView;
import com.xqt.saas.finance.entity.FinanceCustomerBill;
import com.xqt.saas.finance.service.FinanceCustomerBillService;
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
 * 客户账单控制器
 * 提供客户账单的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-customer-bills")
public class FinanceCustomerBillController {

    @Resource
    private FinanceCustomerBillService financeCustomerBillService;

    /**
     * 保存客户账单
     */
    @PostMapping
    public R save(@RequestBody FinanceCustomerBillSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeCustomerBillService.save(request));
    }

    /**
     * 更新客户账单
     */
    @PutMapping
    public R update(@RequestBody FinanceCustomerBillSaveRequest request) {
        return R.success("更新成功", financeCustomerBillService.update(request));
    }

    /**
     * 删除客户账单
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeCustomerBillService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询客户账单
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeCustomerBillService.getById(id));
    }

    /**
     * 分页查询客户账单列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceCustomerBillQueryRequest request) {
        if (request == null) {
            request = new FinanceCustomerBillQueryRequest();
        }
        IPage<FinanceCustomerBillView> pageResult = financeCustomerBillService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询客户账单列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceCustomerBillQueryRequest request) {
        if (request == null) {
            request = new FinanceCustomerBillQueryRequest();
        }
        return R.success(financeCustomerBillService.list(request));
    }

    /**
     * 导入客户账单Excel
     */
    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String tenantId = UserContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            List<FinanceCustomerBillExcelDTO> dataList = EasyExcel.read(inputStream)
                    .head(FinanceCustomerBillExcelDTO.class)
                    .sheet()
                    .doReadSync();
            
            financeCustomerBillService.importExcel(dataList, tenantId);
            return R.success("导入成功");
        }
    }

    /**
     * 导出客户账单Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String userSettle,
            @RequestParam(required = false) String branchCompany,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String salesRep,
            @RequestParam(required = false) String customerServiceRep,
            @RequestParam(required = false) String financeRep,
            @RequestParam(required = false) String dueAction,
            @RequestParam(required = false) Boolean billConfirmed,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) java.time.LocalDateTime billDateStart,
            @RequestParam(required = false) java.time.LocalDateTime billDateEnd,
            @RequestParam(required = false) java.time.LocalDateTime dueDateStart,
            @RequestParam(required = false) java.time.LocalDateTime dueDateEnd) throws IOException {

        FinanceCustomerBillQueryRequest request = new FinanceCustomerBillQueryRequest();
        request.setBillNo(billNo);
        request.setUserSettle(userSettle);
        request.setBranchCompany(branchCompany);
        request.setCurrency(currency);
        request.setSalesRep(salesRep);
        request.setCustomerServiceRep(customerServiceRep);
        request.setFinanceRep(financeRep);
        request.setDueAction(dueAction);
        request.setBillConfirmed(billConfirmed);
        request.setStatus(status);
        request.setBillDateStart(billDateStart);
        request.setBillDateEnd(billDateEnd);
        request.setDueDateStart(dueDateStart);
        request.setDueDateEnd(dueDateEnd);

        List<FinanceCustomerBill> dataList = financeCustomerBillService.exportExcel(request);
        List<FinanceCustomerBillExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceCustomerBillExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("客户账单")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("客户账单.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceCustomerBillExcelDTO convertToExcelDTO(FinanceCustomerBill entity) {
        FinanceCustomerBillExcelDTO dto = new FinanceCustomerBillExcelDTO();
        dto.setBillNo(entity.getBillNo());
        dto.setUserSettle(entity.getUserSettle());
        dto.setBranchCompany(entity.getBranchCompany());
        dto.setCurrency(entity.getCurrency());
        dto.setBillAmount(entity.getBillAmount());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setRemainingAmount(entity.getRemainingAmount());
        dto.setSalesRep(entity.getSalesRep());
        dto.setCustomerServiceRep(entity.getCustomerServiceRep());
        dto.setFinanceRep(entity.getFinanceRep());
        dto.setDueAction(entity.getDueAction());
        dto.setBillConfirmedText(FinanceCustomerBillService.getBillConfirmedText(entity.getBillConfirmed()));
        dto.setStatusText(FinanceCustomerBillService.getStatusText(entity.getStatus()));
        dto.setBillDate(entity.getBillDate());
        dto.setDueDate(entity.getDueDate());
        dto.setWriteOffTime(entity.getWriteOffTime());
        return dto;
    }
}
