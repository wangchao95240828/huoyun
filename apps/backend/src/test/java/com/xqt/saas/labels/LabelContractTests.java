package com.xqt.saas.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.xqt.saas.customerapi.CustomerApiPrincipal;
import com.xqt.saas.labels.LabelRequests.GenerateLabel;
import com.xqt.saas.labels.LabelRequests.RelabelPdf;
import com.xqt.saas.labels.LabelResponses.LabelBatch;
import com.xqt.saas.labels.LabelResponses.RelabelResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LabelContractTests {

    @Test
    void generateMethodZeroReturnsBase64AndPersistsTrackingNumbers() {
        // 对应 ACC act=Label，Method=0：响应里 PDF 是 base64，主单号/子单号写回 cartons + tracking_events。
        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repo.findShipmentsByNos("tenant-1", "cust-1", List.of("ORD-S1"))).thenReturn(List.of(
            Map.of(
                "shipment_id", "11111111-1111-1111-1111-111111111111",
                "shipment_no", "SHP-DOC-ORD-S1",
                "customer_ref", "ORD-S1",
                "status", "ORDERED",
                "destination_country", "US",
                "channel_id", "ch-1",
                "channel_code", "EU-AIR-UPS",
                "first_tracking_no", "NOOP-SUB-0000000001"
            )
        ));
        when(repo.listCartonTrackingNos(eq("tenant-1"), eq("11111111-1111-1111-1111-111111111111")))
            .thenReturn(List.of("NOOP-SUB-0000000001"));

        LabelStorage storage = new LocalFileLabelStorage(System.getProperty("java.io.tmpdir"), "/labels");
        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));

        LabelBatch result = service.generate(
            principal(),
            new GenerateLabel("ORD-S1", null, "PDF", 0)
        );

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).no()).isEqualTo("ORD-S1");
        assertThat(result.data().get(0).pdf()).isNotBlank();
        assertThat(result.data().get(0).url()).isNull();
        assertThat(result.data().get(0).type()).isEqualTo("PDF");
        assertThat(result.data().get(0).trackNo()).startsWith("NOOP-LBL-");
        assertThat(result.data().get(0).error()).isNull();

        // 子单号被写回（Express_TrackNo/Online_TrackNo 等价行为）
        verify(repo, atLeastOnce()).upsertSubTrackingNumbers(any(), any(), any(), any());
        // Express_Status 等价：tracking_events 一行
        verify(repo, atLeastOnce()).insertLabelEvent(any(), any(), any(), any(), any());
        // method=0 不写 label_files
        verify(repo, never()).insertLabelFile(any(), any(), any(), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void generateMethodOneSavesFileAndReturnsUrl() {
        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repo.findShipmentsByNos("tenant-1", "cust-1", List.of("ORD-S2"))).thenReturn(List.of(
            Map.of(
                "shipment_id", "22222222-2222-2222-2222-222222222222",
                "shipment_no", "SHP-DOC-ORD-S2",
                "customer_ref", "ORD-S2",
                "status", "ORDERED",
                "destination_country", "GB",
                "channel_id", "ch-2",
                "channel_code", "EU-AIR-UPS",
                "first_tracking_no", "NOOP-SUB-A"
            )
        ));
        when(repo.findLatestLabelFile(any(), any(), any())).thenReturn(null);
        when(repo.listCartonTrackingNos(any(), any())).thenReturn(List.of("NOOP-SUB-A"));
        when(repo.insertLabelFile(any(), any(), any(), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyInt(), any(), any())).thenReturn("label-file-1");

        LabelStorage storage = new LocalFileLabelStorage(System.getProperty("java.io.tmpdir"), "/labels");
        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));

        LabelBatch result = service.generate(
            principal(),
            new GenerateLabel(null, List.of("ORD-S2"), "PDF", 1)
        );

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).pdf()).isNull();
        assertThat(result.data().get(0).url()).startsWith("/labels/");
        verify(repo).insertLabelFile(any(), any(), any(), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void generateUnknownOrderReturnsPerNoErrorWithoutBreakingBatch() {
        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        java.util.HashMap<String, Object> okShipment = new java.util.HashMap<>();
        okShipment.put("shipment_id", "33333333-3333-3333-3333-333333333333");
        okShipment.put("shipment_no", "SHP-DOC-ORD-OK");
        okShipment.put("customer_ref", "ORD-OK");
        okShipment.put("status", "ORDERED");
        okShipment.put("destination_country", "US");
        okShipment.put("channel_id", "ch-1");
        okShipment.put("channel_code", "EU-AIR-UPS");
        okShipment.put("first_tracking_no", null);
        when(repo.findShipmentsByNos("tenant-1", "cust-1", List.of("MISSING", "ORD-OK")))
            .thenReturn(List.<Map<String, Object>>of(okShipment));
        when(repo.listCartonTrackingNos(any(), any())).thenReturn(List.of());

        LabelStorage storage = new LocalFileLabelStorage(System.getProperty("java.io.tmpdir"), "/labels");
        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));

        LabelBatch result = service.generate(
            principal(),
            new GenerateLabel(null, List.of("MISSING", "ORD-OK"), "PDF", 0)
        );

        assertThat(result.data()).hasSize(2);
        assertThat(result.data().get(0).no()).isEqualTo("MISSING");
        assertThat(result.data().get(0).error()).isEqualTo("找不到该订单");
        assertThat(result.data().get(1).no()).isEqualTo("ORD-OK");
        assertThat(result.data().get(1).error()).isNull();
        assertThat(result.data().get(1).pdf()).isNotBlank();
    }

    @Test
    void relabelResolvesCarrierMappingAndReturnsScaledPdf() throws java.io.IOException {
        // 对应 getNewLabel.php：oldNo 在 change 表里 → 拿到 newNo + Express，
        // 然后 FPDI->importPage(1) + useTemplate 缩放，新版用 PdfPageExtractor 等价。
        byte[] sourcePdf = singlePagePdf();

        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repo.findRelabelMaps("tenant-1", "OLD-123")).thenReturn(List.of(
            new java.util.HashMap<>(Map.of(
                "old_no", "OLD-123",
                "new_no", "NEW-456",
                "scope", "CARRIER",
                "status", 0
            ))
        ));
        when(repo.findShipmentByTrackingNo("tenant-1", "NEW-456")).thenReturn(
            new java.util.HashMap<>(Map.of(
                "shipment_id", "44444444-4444-4444-4444-444444444444",
                "shipment_no", "SHP-1",
                "customer_ref", "ORD-1",
                "tracking_no", "NEW-456",
                "carton_no", "001"
            ))
        );
        when(repo.listLabelFilesForShipment("tenant-1", "44444444-4444-4444-4444-444444444444"))
            .thenReturn(List.of(
                new java.util.HashMap<>(Map.of(
                    "id", "file-1",
                    "tracking_no", "NEW-456",
                    "label_type", "PDF",
                    "file_hash", "abc",
                    "file_ext", "pdf",
                    "storage_path", "/dev/null",
                    "file_size", sourcePdf.length,
                    "created_at", "2026-05-20"
                ))
            ));

        LabelStorage storage = mock(LabelStorage.class);
        when(storage.load(any())).thenReturn(sourcePdf);

        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));
        RelabelResult result = service.relabel(principal(), new RelabelPdf("OLD-123", 100, 150));

        assertThat(result.success()).isTrue();
        assertThat(result.trackNo()).isEqualTo("NEW-456");
        assertThat(result.label()).isNotBlank();
        byte[] outBytes = java.util.Base64.getDecoder().decode(result.label());
        // PDFBox 输出永远是合法 PDF：以 %PDF- 开头
        assertThat(new String(outBytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
            .isEqualTo("%PDF-");
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(outBytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void relabelHandlesHasSingleMultiPagePdfByExtractingTrackNoIndex() throws java.io.IOException {
        // 对应 ACC: $rs->Count==1 && Ext=='pdf' && $TrackNoCount>1 → importPage(TrackNoIndex)
        byte[] multiPagePdf = nPagePdf(3);

        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repo.findRelabelMaps(any(), any())).thenReturn(List.of());
        when(repo.findRelabelByNewNo(any(), any())).thenReturn(null);
        when(repo.findShipmentByTrackingNo("tenant-1", "SUB-2")).thenReturn(
            new java.util.HashMap<>(Map.of(
                "shipment_id", "ship-multi",
                "shipment_no", "SHP-M",
                "customer_ref", "ORD-M",
                "tracking_no", "SUB-2",
                "carton_no", "002"
            ))
        );
        // 唯一一份 PDF，但 cartons 列了 3 个子单号 → hasSingle 分支
        when(repo.listLabelFilesForShipment("tenant-1", "ship-multi")).thenReturn(List.of(
            new java.util.HashMap<>(Map.of(
                "id", "file-multi",
                "tracking_no", "OTHER",
                "label_type", "PDF",
                "file_hash", "h1",
                "file_ext", "pdf",
                "storage_path", "/dev/null",
                "file_size", multiPagePdf.length,
                "created_at", "2026-05-20"
            ))
        ));
        when(repo.listCartonTrackingNos("tenant-1", "ship-multi"))
            .thenReturn(List.of("SUB-1", "SUB-2", "SUB-3"));

        LabelStorage storage = mock(LabelStorage.class);
        when(storage.load(any())).thenReturn(multiPagePdf);

        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));
        RelabelResult result = service.relabel(principal(), new RelabelPdf("SUB-2", 100, 150));

        assertThat(result.success()).isTrue();
        assertThat(result.trackNo()).isEqualTo("SUB-2");
        byte[] outBytes = java.util.Base64.getDecoder().decode(result.label());
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(outBytes)) {
            // 提取后只剩 1 页
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    private byte[] singlePagePdf() throws java.io.IOException {
        return nPagePdf(1);
    }

    private byte[] nPagePdf(int pages) throws java.io.IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.A4));
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void relabelReturnsInterceptedWhenStatusOne() {
        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repo.findRelabelMaps("tenant-1", "BLOCKED")).thenReturn(List.of(
            new java.util.HashMap<>(Map.of(
                "old_no", "BLOCKED",
                "new_no", "NEW-X",
                "scope", "CARRIER",
                "status", 1
            ))
        ));
        when(repo.findShipmentByTrackingNo("tenant-1", "NEW-X")).thenReturn(
            new java.util.HashMap<>(Map.of(
                "shipment_id", "55555555-5555-5555-5555-555555555555",
                "shipment_no", "SHP-X",
                "customer_ref", "ORD-X",
                "tracking_no", "NEW-X",
                "carton_no", "001"
            ))
        );

        LabelStorage storage = mock(LabelStorage.class);
        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));
        RelabelResult result = service.relabel(principal(), new RelabelPdf("BLOCKED", null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("此单号拦截。");
    }

    @Test
    void relabelReturnsNotFoundWhenNoMatch() {
        LabelRepository repo = mock(LabelRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(Object[].class))).thenReturn("tenant-1");
        when(repo.findRelabelMaps(any(), any())).thenReturn(List.of());
        when(repo.findRelabelByNewNo(any(), any())).thenReturn(null);
        when(repo.findShipmentByTrackingNo(any(), any())).thenReturn(null);

        LabelStorage storage = mock(LabelStorage.class);
        LabelService service = new LabelService(repo, noopLabelRegistry(jdbc), storage, jdbc, new com.xqt.saas.common.JsonSupport(new com.fasterxml.jackson.databind.ObjectMapper()));
        RelabelResult result = service.relabel(principal(), new RelabelPdf("UNKNOWN", null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("找不到该单号");
    }

    private CustomerApiPrincipal principal() {
        return new CustomerApiPrincipal("cred-1", "tenant-1", "cust-1", "DOC-DEMO", "60000DEMO", "secret");
    }

    /** 测试用 registry：仅含 NoopLabelGateway，db 查 provider_code 返回 null → 走 Noop 兜底。
     *  保持改造前的行为，所有契约测试断言不变。 */
    private static LabelGatewayRegistry noopLabelRegistry(JdbcTemplate jdbc) {
        NoopLabelGateway noop = new NoopLabelGateway();
        return new LabelGatewayRegistry(java.util.List.of(noop), noop, jdbc);
    }
}
