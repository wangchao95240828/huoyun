package com.xqt.saas.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * 任务 S9 单测：
 *  1. zplToPdf：渲染含已知文本的 ZPL，从转出 PDF 提取文本应包含原文
 *  2. zplToPdf 多 ^FD 段全部渲染
 *  3. zplToPdf null 报错
 *  4. imageToPdf：100×100 PNG → PDF，验证 1 页 + 页面尺寸正确
 *  5. imageToPdf 空 bytes 报错
 *  6. imageToPdf 非图片 bytes 报错
 */
class LabelFormatConverterTest {

    private final LabelFormatConverter converter = new LabelFormatConverter();

    @Test
    void zplToPdfRendersFdText() throws Exception {
        String zpl = "^XA^FO50,50^A0N,40,40^FDHello SBX-001^FS^XZ";
        byte[] pdf = converter.zplToPdf(zpl, 100, 150);
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Hello SBX-001");
        }
    }

    @Test
    void zplToPdfRendersAllFdSegments() throws Exception {
        String zpl = "^XA"
            + "^FO10,10^FDLine1^FS"
            + "^FO10,40^FDLine2^FS"
            + "^FO10,70^FDLine3^FS"
            + "^XZ";
        byte[] pdf = converter.zplToPdf(zpl, 100, 150);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Line1").contains("Line2").contains("Line3");
        }
    }

    @Test
    void zplToPdfNullThrows() {
        assertThatThrownBy(() -> converter.zplToPdf(null, 100, 150))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void imageToPdfWrapsPngInPdfPage() throws Exception {
        BufferedImage bi = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLACK);
        g.drawRect(10, 10, 80, 80);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(bi, "PNG", png);

        byte[] pdf = converter.imageToPdf(png.toByteArray(), 100, 150);

        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            float pageWidth = doc.getPage(0).getMediaBox().getWidth();
            // 100mm × 2.83465 ≈ 283.5 points
            assertThat(pageWidth).isBetween(282f, 285f);
        }
    }

    @Test
    void imageToPdfEmptyBytesThrows() {
        assertThatThrownBy(() -> converter.imageToPdf(new byte[0], 100, 150))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void imageToPdfNonImageThrows() {
        byte[] junk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThatThrownBy(() -> converter.imageToPdf(junk, 100, 150))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
