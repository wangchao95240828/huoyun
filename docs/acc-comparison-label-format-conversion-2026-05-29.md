# Comparison Case：ZPL → PDF / 图片 → PDF 转换（任务 S9）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S9 + 任务书 §3.5#6

## 1. 问题

`SandboxLabelGateway` 只产 placeholder bytes，不是真实 PDF；真实 provider 接入时，
面单格式可能是：
- **ZPL**：UPS / FedEx 部分账号默认返回 ZPL（条码打印机原生格式）
- **PNG / JPG**：部分小渠道返回图片
- **PDF**：主流标准

下游打印机 / 客户系统多数只接 PDF，需要在 LabelService 出口前统一转换。

## 2. 实现

### `com.xqt.saas.labels.LabelFormatConverter`
两个 public 方法：

**`zplToPdf(zpl, widthMm, heightMm) → byte[]`**
- 正则 `\\^FD(...)\\^FS` 提取 ZPL 文本段
- PDFBox 新建一张 widthMm × heightMm 的页面
- 用 Helvetica 12pt 逐行渲染所有 `^FD` 文本
- 适合：测试 / 本地 fallback；生产推荐调 Labelary REST API 实现完美还原

**`imageToPdf(image, widthMm, heightMm) → byte[]`**
- `ImageIO.read` 解析 PNG / JPG 字节
- PDFBox `LosslessFactory.createFromImage` 嵌入
- 保持长宽比 fit 居中到页面

### 关键设计

**MM_TO_POINTS = 2.83465**：PDF 标准 1 inch = 72 points, 1 inch = 25.4 mm；100 mm × 2.83465 ≈ 283.5 points。

**ZPL 最小可用实现**：不要求完美还原（ZPL 含字体/旋转/条码等几十种命令），先把核心 `^FD` 文本流转出可读 PDF；
生产环境想 100% 还原应调 Labelary HTTP API（外部公共服务），失败时降级到本地实现。

**imageToPdf 长宽比保留**：避免拉伸变形，按 `min(pageW/imgW, pageH/imgH)` 缩放，居中。

## 3. 测试（LabelFormatConverterTest 6 项全过）

1. `zplToPdfRendersFdText`：ZPL "Hello SBX-001" → 转出 PDF，PDFTextStripper 提取确实含 "Hello SBX-001"
2. `zplToPdfRendersAllFdSegments`：3 段 `^FD` → 3 行全部出现在 PDF 中
3. `zplToPdfNullThrows`：null ZPL → IllegalArgumentException
4. `imageToPdfWrapsPngInPdfPage`：100×100 PNG → 1 页 PDF，页面宽度 282-285 points（100mm 验证）
5. `imageToPdfEmptyBytesThrows`：空 bytes → IllegalArgumentException
6. `imageToPdfNonImageThrows`：非图片 bytes → IllegalArgumentException

### 全工程
**185 测试全过**（+6 vs S8 结束时 179）。

## 4. 后续接入指南

### LabelService.generate 末段加判断
```java
if ("ZPL".equalsIgnoreCase(artifact.type())) {
    byte[] pdf = converter.zplToPdf(new String(artifact.content(), StandardCharsets.UTF_8), 100, 150);
    artifact = artifact.withContent(pdf, "PDF", "pdf");
} else if (artifact.type().equalsIgnoreCase("PNG") || artifact.type().equalsIgnoreCase("JPG")) {
    byte[] pdf = converter.imageToPdf(artifact.content(), 100, 150);
    artifact = artifact.withContent(pdf, "PDF", "pdf");
}
```

需要保留原始 source 字段（label_files.original_format），便于客户后台要求 ZPL 时回吐：
```sql
alter table label_files
  add column original_format text;
```

### 真实 provider 适配
**UPS REST API**：默认返回 base64 ZPL，base64 decode 后传 `zplToPdf` 即可。

**FedEx Shipping API**：可指定 `LabelType=COMMON2D` 返回 PNG，传 `imageToPdf`。

## 5. ACC 对照

| ACC | 新系统 |
|-----|--------|
| `getNewLabel.php` 用 TCPDF + FPDI 拼贴客户 logo | PDFBox 一致 |
| ZPL 转换：ACC 没做，要求 provider 直接给 PDF | 本任务补齐 ZPL → PDF |
| 图片转换：ACC 用 GD 库 → mPDF | PDFBox `LosslessFactory` 等价 |
| 单单合并多页：ACC 用 FPDI multi-import | PDFBox `PDPageContentStream` + 多 page |
