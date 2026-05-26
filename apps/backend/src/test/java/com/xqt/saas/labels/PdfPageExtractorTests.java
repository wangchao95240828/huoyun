package com.xqt.saas.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.xqt.saas.labels.PdfPageExtractor.Layout;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class PdfPageExtractorTests {

    @Test
    void layoutDefaultsTo100x150NoOffset() {
        Layout layout = PdfPageExtractor.layoutFor(null, null);
        assertThat(layout.printWidthMm()).isEqualTo(100);
        assertThat(layout.printHeightMm()).isEqualTo(150);
        assertThat(layout.pdfWidthMm()).isEqualTo(100.0);
        assertThat(layout.pdfLeftMm()).isZero();
    }

    @Test
    void layoutAppliesAccScalingFormulaWhenHeightChanges() {
        // ACC: pdfWeight = round(printHeight/150*100, 2); pdfLeft = round((printWidth-pdfWeight)/2, 2)
        // 输入 printWidth=80, printHeight=120 → pdfWidth=80.0, pdfLeft=0
        Layout l1 = PdfPageExtractor.layoutFor(80, 120);
        assertThat(l1.pdfWidthMm()).isEqualTo(80.0);
        assertThat(l1.pdfLeftMm()).isEqualTo(0.0);

        // 输入 printWidth=100, printHeight=120 → pdfWidth=80.0, pdfLeft=10.0
        Layout l2 = PdfPageExtractor.layoutFor(100, 120);
        assertThat(l2.pdfWidthMm()).isEqualTo(80.0);
        assertThat(l2.pdfLeftMm()).isEqualTo(10.0);
    }

    @Test
    void layoutClampsNonPositiveToDefault() {
        // ACC: if(!isNumber(printWidth) || $printWidth<=0) $printWidth=100;
        Layout l = PdfPageExtractor.layoutFor(0, -5);
        assertThat(l.printWidthMm()).isEqualTo(100);
        assertThat(l.printHeightMm()).isEqualTo(150);
    }

    @Test
    void extractAndScaleReturnsValidSinglePagePdfAtTargetSize() throws IOException {
        byte[] src = makeMultiPagePdf(3);
        Layout layout = PdfPageExtractor.layoutFor(100, 150);

        byte[] out = PdfPageExtractor.extractAndScale(src, 2, layout);

        try (PDDocument doc = Loader.loadPDF(out)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            PDRectangle box = doc.getPage(0).getMediaBox();
            float mm = 72f / 25.4f;
            assertThat(box.getWidth()).isCloseTo(100 * mm, org.assertj.core.data.Offset.offset(0.5f));
            assertThat(box.getHeight()).isCloseTo(150 * mm, org.assertj.core.data.Offset.offset(0.5f));
        }
    }

    @Test
    void extractAndScaleRejectsOutOfRangePageIndex() throws IOException {
        byte[] src = makeMultiPagePdf(2);
        Layout layout = PdfPageExtractor.layoutFor(100, 150);
        assertThatThrownBy(() -> PdfPageExtractor.extractAndScale(src, 5, layout))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pageIndex 5");
    }

    @Test
    void extractAndScaleRejectsZeroPageIndex() throws IOException {
        byte[] src = makeMultiPagePdf(1);
        Layout layout = PdfPageExtractor.layoutFor(100, 150);
        assertThatThrownBy(() -> PdfPageExtractor.extractAndScale(src, 0, layout))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1-based");
    }

    private byte[] makeMultiPagePdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
