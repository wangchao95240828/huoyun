package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceReceivableReportExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceReceivableReportQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceReceivableReportSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceReceivableReportView;
import com.xqt.saas.finance.entity.FinanceReceivableReport;
import com.xqt.saas.finance.service.FinanceReceivableReportService;
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
 * 应收报表控制器
 * 提供应收报表的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-receivable-reports")
public class FinanceReceivableReportController {

    @Resource
    private FinanceReceivableReportService financeReceivableReportService;

    @PostMapping
    public R save(@RequestBody FinanceReceivableReportSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeReceivableReportService.save(request));
    }

    @PutMapping
    public R update(@RequestBody FinanceReceivableReportSaveRequest request) {
        return R.success("更新成功", financeReceivableReportService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeReceivableReportService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeReceivableReportService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceReceivableReportQueryRequest request) {
        if (request == null) {
            request = new FinanceReceivableReportQueryRequest();
        }
        IPage<FinanceReceivableReportView> pageResult = financeReceivableReportService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceReceivableReportQueryRequest request) {
        if (request == null) {
            request = new FinanceReceivableReportQueryRequest();
        }
        return R.success(financeReceivableReportService.list(request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String userLevel) throws IOException {

        FinanceReceivableReportQueryRequest request = new FinanceReceivableReportQueryRequest();
        request.setUserName(userName);
        request.setCurrency(currency);
        request.setUserLevel(userLevel);

        List<FinanceReceivableReport> dataList = financeReceivableReportService.exportExcel(request);
        List<FinanceReceivableReportExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceReceivableReportExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("应收报表")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("应收报表.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    private FinanceReceivableReportExcelDTO convertToExcelDTO(FinanceReceivableReport entity) {
        FinanceReceivableReportExcelDTO dto = new FinanceReceivableReportExcelDTO();
        dto.setUserName(entity.getUserName());
        dto.setCurrency(entity.getCurrency());
        dto.setTurnover(entity.getTurnover());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setPendingAmount(entity.getPendingAmount());
        dto.setIssuedBill(entity.getIssuedBill());
        dto.setPendingBill(entity.getPendingBill());
        dto.setPaidBill(entity.getPaidBill());
        dto.setPendingPaymentBill(entity.getPendingPaymentBill());
        dto.setActualAmount(entity.getActualAmount());
        dto.setCustomerServiceRep(entity.getCustomerServiceRep());
        dto.setSalesRep(entity.getSalesRep());
        dto.setFinanceRep(entity.getFinanceRep());
        dto.setSettlementMethod(entity.getSettlementMethod());
        dto.setUserLevel(entity.getUserLevel());
        dto.setUserRemark(entity.getUserRemark());
        return dto;
    }
}
