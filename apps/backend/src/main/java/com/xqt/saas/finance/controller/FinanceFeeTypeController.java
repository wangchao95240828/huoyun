package com.xqt.saas.finance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xqt.saas.finance.common.R;
import com.xqt.saas.finance.common.UserContext;
import com.xqt.saas.finance.dto.excel.FinanceFeeTypeExcelDTO;
import com.xqt.saas.finance.dto.request.FinanceFeeTypeQueryRequest;
import com.xqt.saas.finance.dto.request.FinanceFeeTypeSaveRequest;
import com.xqt.saas.finance.dto.response.FinanceFeeTypeView;
import com.xqt.saas.finance.entity.FinanceFeeType;
import com.xqt.saas.finance.service.FinanceFeeTypeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/finance-fee-types")
public class FinanceFeeTypeController {

    @Resource
    public FinanceFeeTypeService financeFeeTypeService;

    @PostMapping("/save")
    public R save(@RequestBody FinanceFeeTypeSaveRequest request) {
        //获取租户ID
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeFeeTypeService.save(request));
    }

    @PutMapping
    public R update(@Valid @RequestBody FinanceFeeTypeSaveRequest request) {
        return R.success("更新成功", financeFeeTypeService.update(request));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeFeeTypeService.delete(id);
        return R.success("删除成功");
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeFeeTypeService.getById(id));
    }

    @GetMapping("/page")
    public R page(@RequestParam(defaultValue = "1") Long pageNum, @RequestParam(defaultValue = "10") Long pageSize, @RequestParam(required = false) String code, @RequestParam(required = false) String name, @RequestParam(required = false) String type, @RequestParam(required = false) Boolean isShow) {
        FinanceFeeTypeQueryRequest queryRequest = new FinanceFeeTypeQueryRequest();
        queryRequest.setCode(code);
        queryRequest.setName(name);
        queryRequest.setType(type);
        queryRequest.setIsShow(isShow);

        IPage<FinanceFeeTypeView> pageResult = financeFeeTypeService.page(queryRequest, pageNum, pageSize);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/list")
    public R list(@RequestParam(required = false) String code, @RequestParam(required = false) String name, @RequestParam(required = false) String type, @RequestParam(required = false) Boolean isShow) {
        FinanceFeeTypeQueryRequest queryRequest = new FinanceFeeTypeQueryRequest();
        queryRequest.setCode(code);
        queryRequest.setName(name);
        queryRequest.setType(type);
        queryRequest.setIsShow(isShow);

        return R.success(financeFeeTypeService.list(queryRequest));
    }

    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file, @RequestParam("tenantId") String tenantId) throws IOException {
        List<FinanceFeeTypeExcelDTO> dataList = EasyExcel.read(file.getInputStream()).head(FinanceFeeTypeExcelDTO.class).sheet().doReadSync();
        List<FinanceFeeType> entityList = dataList.stream().map(dto -> convertToEntity(dto, tenantId)).collect(Collectors.toList());
        financeFeeTypeService.importExcel(entityList);
        return R.success("导入成功");
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String code, @RequestParam(required = false) String name, @RequestParam(required = false) String type, @RequestParam(required = false) Boolean isShow) throws IOException {
        FinanceFeeTypeQueryRequest queryRequest = new FinanceFeeTypeQueryRequest();
        queryRequest.setCode(code);
        queryRequest.setName(name);
        queryRequest.setType(type);
        queryRequest.setIsShow(isShow);

        List<FinanceFeeType> dataList = financeFeeTypeService.exportExcel(queryRequest);
        List<FinanceFeeTypeExcelDTO> excelList = dataList.stream().map(this::convertToExcelDTO).collect(Collectors.toList());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, FinanceFeeTypeExcelDTO.class).registerWriteHandler(new LongestMatchColumnWidthStyleStrategy()).sheet("费用类型").doWrite(excelList);

        String fileName = URLEncoder.encode("费用类型.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    private FinanceFeeType convertToEntity(FinanceFeeTypeExcelDTO dto, String tenantId) {
        FinanceFeeType entity = new FinanceFeeType();
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setPrice(dto.getPrice());
        entity.setIsShow(dto.getIsShow());
        return entity;
    }

    private FinanceFeeTypeExcelDTO convertToExcelDTO(FinanceFeeType entity) {
        FinanceFeeTypeExcelDTO dto = new FinanceFeeTypeExcelDTO();
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setPrice(entity.getPrice());
        dto.setIsShow(entity.getIsShow());
        return dto;
    }
}