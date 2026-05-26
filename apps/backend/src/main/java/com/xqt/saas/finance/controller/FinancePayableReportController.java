package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinancePayableReportExcelDTO;
import com.xqt.saas.finance.dto.request.FinancePayableReportQueryRequest;
import com.xqt.saas.finance.dto.request.FinancePayableReportSaveRequest;
import com.xqt.saas.finance.dto.response.FinancePayableReportView;
import com.xqt.saas.finance.entity.FinancePayableReport;
import com.xqt.saas.finance.service.FinancePayableReportService;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应付报表控制器
 * 提供应付报表的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-payable-reports")
public class FinancePayableReportController {

    @Resource
    private FinancePayableReportService financePayableReportService;

    @PostMapping
    public R save(@RequestBody FinancePayableReportSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financePayableReportService.save(request));
    }

    @PutMapping
    public R update(@RequestBody FinancePayableReportSaveRequest request) {
        return R.success("更新成功", financePayableReportService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financePayableReportService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financePayableReportService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinancePayableReportQueryRequest request) {
        if (request == null) {
            request = new FinancePayableReportQueryRequest();
        }
        IPage<FinancePayableReportView> pageResult = financePayableReportService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinancePayableReportQueryRequest request) {
        if (request == null) {
            request = new FinancePayableReportQueryRequest();
        }
        return R.success(financePayableReportService.list(request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String currency) throws IOException {

        FinancePayableReportQueryRequest request = new FinancePayableReportQueryRequest();
        request.setUserName(userName);
        request.setCurrency(currency);

        List<FinancePayableReport> dataList = financePayableReportService.exportExcel(request);
        List<FinancePayableReportExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinancePayableReportExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("应付报表")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("应付报表.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    private FinancePayableReportExcelDTO convertToExcelDTO(FinancePayableReport entity) {
        FinancePayableReportExcelDTO dto = new FinancePayableReportExcelDTO();
        dto.setUserName(entity.getUserName());
        dto.setCurrency(entity.getCurrency());
        dto.setTurnover(entity.getTurnover());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setPendingAmount(entity.getPendingAmount());
        dto.setIssuedBill(entity.getIssuedBill());
        dto.setPendingBill(entity.getPendingBill());
        dto.setPaidBill(entity.getPaidBill());
        dto.setPendingPaymentBill(entity.getPendingPaymentBill());
        return dto;
    }
}
