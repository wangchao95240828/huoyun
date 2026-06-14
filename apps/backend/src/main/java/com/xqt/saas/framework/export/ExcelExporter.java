package com.xqt.saas.framework.export;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Excel 导出工具 — 对齐 ACC PHPExcel 导出（带表头样式）。
 *
 * 用法:
 *   List<Map<String,Object>> rows = ...;
 *   String[] cols = {"order_no","customer","amount"};
 *   String[] heads = {"订单号","客户","金额"};
 *   return exporter.exportXlsx("orders", rows, cols, heads);
 */
@Component
public class ExcelExporter {

    public ResponseEntity<byte[]> exportXlsx(String fileName,
                                              List<Map<String, Object>> rows,
                                              String[] columnKeys,
                                              String[] headers) {
        // 构建表头
        List<List<String>> head = new ArrayList<>();
        for (String h : headers) head.add(List.of(h));
        // 构建数据
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>();
            for (String key : columnKeys) {
                Object v = row.get(key);
                line.add(v == null ? "" : v);
            }
            data.add(line);
        }
        // 样式：表头蓝底白字粗体，内容默认
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setColor((short) 9); // white
        headFont.setFontHeightInPoints((short) 11);
        headStyle.setWriteFont(headFont);
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        HorizontalCellStyleStrategy strategy = new HorizontalCellStyleStrategy(headStyle, contentStyle);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out)
            .head(head)
            .registerWriteHandler(strategy)
            .sheet(fileName)
            .doWrite(data);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        responseHeaders.setContentDispositionFormData("attachment", fileName + ".xlsx");
        return new ResponseEntity<>(out.toByteArray(), responseHeaders, 200);
    }
}
