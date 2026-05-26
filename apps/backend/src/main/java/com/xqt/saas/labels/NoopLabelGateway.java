package com.xqt.saas.labels;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 默认占位 LabelGateway。生成一个最小合法 PDF（足以被 PDF 阅读器打开）+ 一个主单号 + 一个子单号。
 * 真实渠道接入时新建 bean 替代即可，业务层无需改动。
 */
@Component
public class NoopLabelGateway implements LabelGateway {
    private static final AtomicLong COUNTER = new AtomicLong();

    @Override
    public LabelArtifact print(PrintContext ctx) {
        long seq = COUNTER.incrementAndGet();
        String main = "NOOP-LBL-" + String.format("%010d", seq);
        String sub = "NOOP-SUB-" + String.format("%010d", seq);
        boolean wantZpl = "ZPL".equalsIgnoreCase(ctx.requestedType());

        byte[] content;
        String type;
        String ext;
        if (wantZpl) {
            String zpl = "^XA^FO50,50^A0N,40,40^FD" + ctx.shipmentNo() + "^FS^XZ";
            content = zpl.getBytes(StandardCharsets.UTF_8);
            type = "ZPL";
            ext = "zpl";
        } else {
            content = minimalPdf(ctx.shipmentNo() + " " + main);
            type = "PDF";
            ext = "pdf";
        }
        return new LabelArtifact(content, type, ext, main, List.of(sub),
            Map.of("provider", "NOOP", "seq", seq));
    }

    /** 生成一个 1 页极简 PDF，包含一行文本，足够阅读器打开。 */
    private static byte[] minimalPdf(String text) {
        String safe = text == null ? "" : text.replace("(", "\\(").replace(")", "\\)");
        String stream = "BT /F1 12 Tf 50 700 Td (" + safe + ") Tj ET";
        int streamLen = stream.getBytes(StandardCharsets.US_ASCII).length;
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        long[] off = new long[6];
        off[1] = pdf.length();
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        off[2] = pdf.length();
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        off[3] = pdf.length();
        pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] ")
            .append("/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");
        off[4] = pdf.length();
        pdf.append("4 0 obj\n<< /Length ").append(streamLen).append(" >>\nstream\n")
            .append(stream).append("\nendstream\nendobj\n");
        off[5] = pdf.length();
        pdf.append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        long xref = pdf.length();
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            pdf.append(String.format("%010d 00000 n %n", off[i]));
        }
        pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n")
            .append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
