package com.xqt.saas.labels;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;

/**
 * 复刻 ACC api/getNewLabel.php 用 FPDI/TCPDF 做的页面提取与缩放。
 *
 * ACC PHP 行为（对应 PHP 行号 109-153）：
 *   1. printWidth/printHeight 默认 100×150 mm
 *   2. if(printHeight != 150): pdfWidth = round(printHeight/150*100, 2); pdfLeft = round((printWidth-pdfWidth)/2, 2)
 *      else:                  pdfWidth = 100; pdfLeft = 0
 *   3. 单页提取：FPDI->importPage(pageIndex)
 *   4. FPDI->useTemplate(tplIdx, pdfLeft, 0, pdfWidth, printHeight, false)  // false = 不保持宽高比
 *
 * 这里用 PDFBox 完整复刻，包括"不保持宽高比"的非均匀缩放语义。
 */
public final class PdfPageExtractor {
    private static final float MM_TO_POINTS = 72f / 25.4f;
    public static final int DEFAULT_WIDTH_MM = 100;
    public static final int DEFAULT_HEIGHT_MM = 150;

    private PdfPageExtractor() {
    }

    /** 复刻 ACC 缩放公式：return (printWidth_mm, printHeight_mm, pdfLeft_mm, pdfWidth_mm)。 */
    public static Layout layoutFor(Integer requestedWidthMm, Integer requestedHeightMm) {
        int width = (requestedWidthMm == null || requestedWidthMm <= 0)
            ? DEFAULT_WIDTH_MM : requestedWidthMm;
        int height = (requestedHeightMm == null || requestedHeightMm <= 0)
            ? DEFAULT_HEIGHT_MM : requestedHeightMm;
        double pdfWidth;
        double pdfLeft;
        if (height != DEFAULT_HEIGHT_MM) {
            pdfWidth = round2(height / 150.0 * 100.0);
            pdfLeft = round2((width - pdfWidth) / 2.0);
        } else {
            pdfWidth = DEFAULT_WIDTH_MM;
            pdfLeft = 0;
        }
        return new Layout(width, height, pdfLeft, pdfWidth);
    }

    /**
     * 把源 PDF 第 pageIndex（1-based）页提取为单页 PDF，输出尺寸 layout.printWidth × layout.printHeight，
     * 内容放在 (pdfLeft, 0) 位置，宽度 pdfWidth，高度 printHeight（非均匀缩放，与 FPDI useTemplate(...,false) 一致）。
     */
    public static byte[] extractAndScale(byte[] sourcePdf, int pageIndex, Layout layout) {
        if (sourcePdf == null || sourcePdf.length == 0) {
            throw new IllegalArgumentException("sourcePdf is empty");
        }
        if (pageIndex < 1) {
            throw new IllegalArgumentException("pageIndex must be 1-based and >= 1");
        }
        try (PDDocument src = Loader.loadPDF(sourcePdf);
             PDDocument out = new PDDocument()) {
            if (pageIndex > src.getNumberOfPages()) {
                throw new IllegalArgumentException("pageIndex " + pageIndex
                    + " exceeds source page count " + src.getNumberOfPages());
            }
            PDRectangle pageBox = new PDRectangle(
                layout.printWidthMm() * MM_TO_POINTS,
                layout.printHeightMm() * MM_TO_POINTS
            );
            PDPage target = new PDPage(pageBox);
            out.addPage(target);

            LayerUtility utility = new LayerUtility(out);
            PDFormXObject form = utility.importPageAsForm(src, pageIndex - 1);
            PDRectangle bbox = form.getBBox();

            double targetWidthPt = layout.pdfWidthMm() * MM_TO_POINTS;
            double targetHeightPt = layout.printHeightMm() * MM_TO_POINTS;
            double scaleX = targetWidthPt / bbox.getWidth();
            double scaleY = targetHeightPt / bbox.getHeight();

            AffineTransform at = AffineTransform.getTranslateInstance(
                layout.pdfLeftMm() * MM_TO_POINTS, 0);
            at.scale(scaleX, scaleY);

            try (PDPageContentStream cs = new PDPageContentStream(
                out, target, PDPageContentStream.AppendMode.APPEND, false)) {
                cs.saveGraphicsState();
                cs.transform(new Matrix(at));
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("PDF extract/scale failed", ex);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public record Layout(
        int printWidthMm,
        int printHeightMm,
        double pdfLeftMm,
        double pdfWidthMm
    ) {
    }
}
