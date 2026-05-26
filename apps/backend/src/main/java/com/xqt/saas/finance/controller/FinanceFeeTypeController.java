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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 费用类型控制器
 * 提供费用类型的RESTful API接口
 */
@RestController
@RequestMapping("/api/finance-fee-types")
public class FinanceFeeTypeController {

    @Resource
    private FinanceFeeTypeService financeFeeTypeService;

    /**
     * 保存费用类型
     */
    @PostMapping("/save")
    public R save(@RequestBody FinanceFeeTypeSaveRequest request) {
        request.setTenantId(UserContext.getTenantId());
        return R.success("保存成功", financeFeeTypeService.save(request));
    }

    /**
     * 更新费用类型
     */
    @PutMapping
    public R update(@Valid @RequestBody FinanceFeeTypeSaveRequest request) {
        return R.success("更新成功", financeFeeTypeService.update(request));
    }

    /**
     * 删除费用类型
     */
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        financeFeeTypeService.delete(id);
        return R.success("删除成功");
    }

    /**
     * 根据ID查询费用类型
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.success(financeFeeTypeService.getById(id));
    }

    /**
     * 分页查询费用类型列表
     */
    @PostMapping("/page")
    public R page(@RequestBody(required = false) FinanceFeeTypeQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceFeeTypeQueryRequest();
        }
        IPage<FinanceFeeTypeView> pageResult = financeFeeTypeService.page(queryRequest);
        return R.success("查询成功", pageResult.getRecords(), pageResult.getTotal());
    }

    /**
     * 查询费用类型列表
     */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) FinanceFeeTypeQueryRequest queryRequest) {
        if (queryRequest == null) {
            queryRequest = new FinanceFeeTypeQueryRequest();
        }
        return R.success(financeFeeTypeService.list(queryRequest));
    }

    /**
     * 导入费用类型Excel
     */
    @PostMapping("/import")
    public R importExcel(@RequestParam("file") MultipartFile file, @RequestParam("tenantId") String tenantId) throws IOException {
        List<FinanceFeeTypeExcelDTO> dataList = EasyExcel.read(file.getInputStream()).head(FinanceFeeTypeExcelDTO.class).sheet().doReadSync();
        List<FinanceFeeType> entityList = dataList.stream().map(dto -> convertToEntity(dto, tenantId)).collect(Collectors.toList());
        financeFeeTypeService.importExcel(entityList);
        return R.success("导入成功");
    }

    /**
     * 导出费用类型Excel
     */
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

    /**
     * 转换为实体对象
     */
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

    /**
     * 转换为ExcelDTO对象
     */
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
