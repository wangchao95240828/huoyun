package com.xqt.saas.labels;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * 任务 S9：面单格式转换。
 *
 * - `zplToPdf`：把 ZPL 字符串里 `^FD...^FS` 文本段渲染到一张 PDF 页面（最小可用实现）
 * - `imageToPdf`：把 PNG/JPG 单页转 PDF（用 PDFBox PDImageXObject）
 *
 * 生产环境若需 ZPL 完美还原，应该调 Labelary REST API（外部公共服务）。本地实现保留
 * 用作 fallback 和单测可重现路径，不依赖网络。
 */
@Component
public class LabelFormatConverter {
    private static final float MM_TO_POINTS = 2.83465f; // 1 mm = 2.83465 PDF points
    private static final Pattern ZPL_FD = Pattern.compile("\\^FD([^^]+)\\^FS");

    /**
     * 把 ZPL 字符串转 PDF。提取所有 `^FD<text>^FS` 文本段顺序写到页面上。
     * widthMm × heightMm 决定页面尺寸；常见面单 100×150。
     */
    public byte[] zplToPdf(String zpl, int widthMm, int heightMm) {
        if (zpl == null) throw new IllegalArgumentException("zpl is null");
        List<String> texts = extractZplText(zpl);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            float pageWidth = widthMm * MM_TO_POINTS;
            float pageHeight = heightMm * MM_TO_POINTS;
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                float y = pageHeight - 30;
                cs.newLineAtOffset(20, y);
                for (String text : texts) {
                    cs.showText(text);
                    cs.newLineAtOffset(0, -18);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("zplToPdf failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 把单张图片（PNG / JPG）转 PDF。widthMm × heightMm 决定页面尺寸；
     * 图片按比例 fit 到页面（保持长宽比）。
     */
    public byte[] imageToPdf(byte[] image, int widthMm, int heightMm) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("image is empty");
        }
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(image));
            if (bi == null) throw new IllegalArgumentException("not a valid image");
            float pageWidth = widthMm * MM_TO_POINTS;
            float pageHeight = heightMm * MM_TO_POINTS;
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            doc.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bi);
            // 保持长宽比 fit 到页面
            float scale = Math.min(pageWidth / pdImage.getWidth(), pageHeight / pdImage.getHeight());
            float drawW = pdImage.getWidth() * scale;
            float drawH = pdImage.getHeight() * scale;
            float x = (pageWidth - drawW) / 2;
            float y = (pageHeight - drawH) / 2;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, x, y, drawW, drawH);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("imageToPdf failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 把 artifact 自动转 PDF：labelType=ZPL → zplToPdf；labelType=PNG/JPG → imageToPdf；
     * 已是 PDF 直接返回 in。默认 100mm × 150mm（标准国际面单尺寸）。
     */
    public LabelGateway.LabelArtifact convertToPdfIfNeeded(LabelGateway.LabelArtifact in) {
        if (in == null || in.content() == null) return in;
        String type = in.labelType() == null ? "" : in.labelType().toUpperCase();
        if ("PDF".equals(type)) return in;
        byte[] pdf;
        if ("ZPL".equals(type)) {
            String zpl = new String(in.content(), java.nio.charset.StandardCharsets.UTF_8);
            pdf = zplToPdf(zpl, 100, 150);
        } else if ("PNG".equals(type) || "JPG".equals(type) || "JPEG".equals(type)) {
            pdf = imageToPdf(in.content(), 100, 150);
        } else {
            return in;
        }
        return new LabelGateway.LabelArtifact(
            pdf, "PDF", "pdf",
            in.mainTrackingNo(), in.subTrackingNos(), in.raw());
    }

    private List<String> extractZplText(String zpl) {
        List<String> out = new ArrayList<>();
        Matcher m = ZPL_FD.matcher(zpl);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
