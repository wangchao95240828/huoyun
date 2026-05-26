package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceWaybillAuditExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceWaybillAuditQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceWaybillAuditSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceWaybillAuditView;
import com.xqt.saas.finance.entity.FinanceWaybillAudit;
import com.xqt.saas.finance.service.FinanceWaybillAuditService;
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
 * 运单审计控制器
 * 提供运单审计的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-waybill-audits")
public class FinanceWaybillAuditController {

    @Resource
    private FinanceWaybillAuditService financeWaybillAuditService;

    @PostMapping
    public R save(@RequestBody FinanceWaybillAuditSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeWaybillAuditService.save(request));
    }

    @PutMapping
    public R update(@RequestBody FinanceWaybillAuditSaveRequest request) {
        return R.success("更新成功", financeWaybillAuditService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeWaybillAuditService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeWaybillAuditService.getById(id));
    }

    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceWaybillAuditQueryRequest request) {
        if (request == null) {
            request = new FinanceWaybillAuditQueryRequest();
        }
        IPage<FinanceWaybillAuditView> pageResult = financeWaybillAuditService.page(request);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceWaybillAuditQueryRequest request) {
        if (request == null) {
            request = new FinanceWaybillAuditQueryRequest();
        }
        return R.success(financeWaybillAuditService.list(request));
    }

    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String tenantId = UserContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            List<FinanceWaybillAuditExcelDTO> dataList = EasyExcel.read(inputStream)
                    .head(FinanceWaybillAuditExcelDTO.class)
                    .sheet()
                    .doReadSync();
            
            financeWaybillAuditService.importExcel(dataList, tenantId);
            return R.success("导入成功");
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String waybillNo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String customerServiceRep,
            @RequestParam(required = false) String salesRep,
            @RequestParam(required = false) java.time.LocalDateTime pickingTimeStart,
            @RequestParam(required = false) java.time.LocalDateTime pickingTimeEnd) throws IOException {

        FinanceWaybillAuditQueryRequest request = new FinanceWaybillAuditQueryRequest();
        request.setWaybillNo(waybillNo);
        request.setUserName(userName);
        request.setService(service);
        request.setCountry(country);
        request.setStatus(status);
        request.setCustomerServiceRep(customerServiceRep);
        request.setSalesRep(salesRep);
        request.setPickingTimeStart(pickingTimeStart);
        request.setPickingTimeEnd(pickingTimeEnd);

        List<FinanceWaybillAudit> dataList = financeWaybillAuditService.exportExcel(request);
        List<FinanceWaybillAuditExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceWaybillAuditExcelDTO.class)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("运单审计")
                .doWrite(excelList);

        String fileName = URLEncoder.encode("运单审计.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    private FinanceWaybillAuditExcelDTO convertToExcelDTO(FinanceWaybillAudit entity) {
        FinanceWaybillAuditExcelDTO dto = new FinanceWaybillAuditExcelDTO();
        dto.setWaybillNo(entity.getWaybillNo());
        dto.setUserName(entity.getUserName());
        dto.setService(entity.getService());
        dto.setCountry(entity.getCountry());
        dto.setPieceCount(entity.getPieceCount());
        dto.setActualWeight(entity.getActualWeight());
        dto.setVolumeWeight(entity.getVolumeWeight());
        dto.setChargeWeight(entity.getChargeWeight());
        dto.setSupplierWeight(entity.getSupplierWeight());
        dto.setStatusText(FinanceWaybillAuditService.getStatusText(entity.getStatus()));
        dto.setReceivableAmount(entity.getReceivableAmount());
        dto.setPayableAmount(entity.getPayableAmount());
        dto.setSalesCost(entity.getSalesCost());
        dto.setSalesCommission(entity.getSalesCommission());
        dto.setGrossProfit(entity.getGrossProfit());
        dto.setCustomerServiceRep(entity.getCustomerServiceRep());
        dto.setSalesRep(entity.getSalesRep());
        dto.setPickingTime(entity.getPickingTime());
        return dto;
    }
}
