package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceSupplierBillExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceSupplierBillQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceSupplierBillSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceSupplierBillView;
import com.xqt.saas.finance.entity.FinanceSupplierBill;
import com.xqt.saas.finance.service.FinanceSupplierBillService;
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
 * 供应商账单控制器
 * 提供供应商账单的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-supplier-bills")
public class FinanceSupplierBillController {

    @Resource
    private FinanceSupplierBillService financeSupplierBillService;

    /**
     * 保存供应商账单
     */
    @PostMapping
    public R save(@RequestBody FinanceSupplierBillSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeSupplierBillService.save(request));
    }

    /**
     * 更新供应商账单
     */
    @PutMapping
    public R update(@RequestBody FinanceSupplierBillSaveRequest request) {
        return R.success("更新成功", financeSupplierBillService.update(request));
    }

    /**
     * 删除供应商账单
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeSupplierBillService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询供应商账单
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeSupplierBillService.getById(id));
    }

    /**
     * 分页查询供应商账单列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceSupplierBillQueryRequest request) {
        if (request == null) {
            request = new FinanceSupplierBillQueryRequest();
        }
        IPage<FinanceSupplierBillView> pageResult = financeSupplierBillService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询供应商账单列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceSupplierBillQueryRequest request) {
        if (request == null) {
            request = new FinanceSupplierBillQueryRequest();
        }
        return R.success(financeSupplierBillService.list(request));
    }

    /**
     * 导出供应商账单Excel
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String customFlag,
            @RequestParam(required = false) java.time.LocalDateTime billDateStart,
            @RequestParam(required = false) java.time.LocalDateTime billDateEnd,
            @RequestParam(required = false) java.time.LocalDateTime dueDateStart,
            @RequestParam(required = false) java.time.LocalDateTime dueDateEnd) throws IOException {

        FinanceSupplierBillQueryRequest request = new FinanceSupplierBillQueryRequest();
        request.setBillNo(billNo);
        request.setSupplier(supplier);
        request.setCurrency(currency);
        request.setStatus(status);
        request.setCustomFlag(customFlag);
        request.setBillDateStart(billDateStart);
        request.setBillDateEnd(billDateEnd);
        request.setDueDateStart(dueDateStart);
        request.setDueDateEnd(dueDateEnd);

        List<FinanceSupplierBill> dataList = financeSupplierBillService.exportExcel(request);
        List<FinanceSupplierBillExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceSupplierBillExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("供应商账单")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("供应商账单.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * 转换为ExcelDTO对象
     */
    private FinanceSupplierBillExcelDTO convertToExcelDTO(FinanceSupplierBill entity) {
        FinanceSupplierBillExcelDTO dto = new FinanceSupplierBillExcelDTO();
        dto.setBillNo(entity.getBillNo());
        dto.setSupplier(entity.getSupplier());
        dto.setCurrency(entity.getCurrency());
        dto.setStatusText(FinanceSupplierBillService.getStatusText(entity.getStatus()));
        dto.setTransactionTotal(entity.getTransactionTotal());
        dto.setBillAmount(entity.getBillAmount());
        dto.setCustomFlag(entity.getCustomFlag());
        dto.setBillDate(entity.getBillDate());
        dto.setDueDate(entity.getDueDate());
        dto.setWriteOffTime(entity.getWriteOffTime());
        return dto;
    }
}
