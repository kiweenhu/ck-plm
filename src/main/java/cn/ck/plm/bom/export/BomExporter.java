/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.export;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BOM 导出器 —— 把「某一版父件」的完整 BOM 结构写成一个文件（csv / xls / xlsx / pdf）。
 *
 * <h3>为什么放在后端</h3>
 * 四种格式里最麻烦的两件（Excel 二进制、PDF 中文）都在后端做一次就够：口径统一
 * （结构树与成本报告同源，都是 {@link BomCostReportVO}），大数据量不进浏览器内存，
 * 前端只负责触发下载。
 *
 * <h3>行列口径</h3>
 * <ul>
 *   <li><b>第一行是根件</b>（层级 0）：本级就是"这一版零件"，用量按 1 记，
 *       单位成本/累计金额取<b>卷积总成本</b>（含全部下级）。没有它，导出的表就是
 *       "一堆子件"而看不出是谁的 BOM。</li>
 *   <li>其余行按 <b>深度优先</b>（与页面左侧树同序）平铺，<b>层级列 + 名称缩进</b>表达从属关系 ——
 *       用户在 Excel 里可以直接按层级列筛选/透视。</li>
 *   <li>成本三列取自成本报告的卷积结果（{@code extendedCost} 本层金额 /
 *       {@code rolledUpCost} 累计金额），与页面「成本报告」逐行对得上。</li>
 * </ul>
 */
@Component
public class BomExporter {

    private static final Logger log = LoggerFactory.getLogger(BomExporter.class);

    private static final String[] HEADERS = {
            "层级", "行号", "子件编码", "子件名称", "版本", "用量", "单位", "单位成本", "本层金额", "累计金额"
    };

    /** 各列宽度（Excel 字符数；PDF 用相对权重） */
    private static final int[] COLUMN_CHARS = { 6, 8, 22, 40, 10, 10, 8, 14, 14, 14 };
    private static final float[] PDF_WIDTHS = { 4f, 5f, 12f, 22f, 6f, 5f, 5f, 8f, 8f, 8f };

    private static final String SHEET_MAIN = "BOM 结构";
    private static final String SHEET_META = "导出说明";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 没有 CID 字体时依次尝试的系统字体（TrueType）。放在这里是为了 PDF 在任何部署上都能出中文：
     * CID 字体（STSong-Light）只引用不嵌入、最省事；系统字体则嵌入子集，万无一失。
     */
    private static final String[] SYSTEM_FONT_CANDIDATES = {
            "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/Deng.ttf", "C:/Windows/Fonts/simsun.ttc,0",
            "/usr/share/fonts/truetype/arphic/uming.ttc,0",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",
    };

    private volatile BaseFont cjkFont;

    // ==================== 对外 ====================

    /**
     * 平铺成本报告：根件一行（层级 0） + 明细按深度优先。
     *
     * <p>纯函数：不查库、不抛业务异常 —— 单测可以直接喂一棵手搭的树。
     */
    public List<Row> rows(BomCostReportVO report) {
        List<Row> rows = new ArrayList<>();
        if (report == null) {
            return rows;
        }
        Row root = new Row();
        root.level = 0;
        root.lineNumber = "";
        root.code = report.getParentCode();
        root.name = report.getParentName();
        root.version = report.getParentVersion();
        root.quantity = 1d;
        root.unit = "";
        root.unitCost = report.getTotalCost();       // 这一版零件的完整单位成本（含下级）
        root.extendedCost = report.getDirectCost();  // 本层 = 直接下挂各行之合
        root.rolledUpCost = report.getTotalCost();
        rows.add(root);

        if (report.getLines() != null) {
            for (BomTreeNode node : report.getLines()) {
                append(rows, node, 1);
            }
        }
        return rows;
    }

    /** 按格式写文件（format 为空/未知时按 xlsx） */
    public byte[] write(List<Row> rows, BomCostReportVO report, BomExportFormat format) {
        BomExportFormat fmt = format == null ? BomExportFormat.XLSX : format;
        try {
            switch (fmt) {
                case CSV:
                    return csv(rows);
                case XLS:
                    return excel(rows, report, false);
                case PDF:
                    return pdf(rows, report);
                case XLSX:
                default:
                    return excel(rows, report, true);
            }
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("生成 BOM 导出文件失败（" + fmt.extension() + "）: " + e.getMessage(), e);
        }
    }

    // ==================== 平铺 ====================

    private void append(List<Row> rows, BomTreeNode node, int level) {
        Row row = new Row();
        row.level = level;
        row.lineNumber = node.getLineNumber() == null ? "" : String.valueOf(node.getLineNumber());
        row.code = node.getChildPartNumber();
        row.name = node.getChildPartName();
        // 名称缺失时退回 BOM 行上的 name（有的历史数据只有行名）
        if (row.name == null || row.name.isEmpty()) {
            row.name = node.getName();
        }
        row.version = node.getChildVersion();
        row.quantity = node.getQuantity();
        row.unit = node.getUnit();
        row.unitCost = node.getUnitCost();
        row.extendedCost = node.getExtendedCost();
        row.rolledUpCost = node.getRolledUpCost();
        rows.add(row);

        if (node.getChildren() != null) {
            for (BomTreeNode child : node.getChildren()) {
                append(rows, child, level + 1);
            }
        }
    }

    // ==================== CSV ====================

    /**
     * CSV：UTF-8 <b>带 BOM</b> + CRLF。
     *
     * <p>不带 BOM 时 Excel 会按本地代码页解码，"子件编码"这类表头直接乱码；
     * CRLF 则是 Excel 与 RFC 4180 的共同口径。缩进用空格（CSV 没有样式可用）。
     */
    private byte[] csv(List<Row> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(",", HEADERS)).append("\r\n");
        for (Row row : rows) {
            sb.append(csvCell(text(row.level))).append(',')
              .append(csvCell(row.lineNumber)).append(',')
              .append(csvCell(row.code)).append(',')
              .append(csvCell(indent(row.name, row.level))).append(',')
              .append(csvCell(row.version)).append(',')
              .append(csvCell(num(row.quantity))).append(',')
              .append(csvCell(row.unit)).append(',')
              .append(csvCell(unitCost(row.unitCost))).append(',')
              .append(csvCell(money(row.extendedCost))).append(',')
              .append(csvCell(money(row.rolledUpCost))).append("\r\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    // ==================== Excel（HSSF / XSSF 共用）====================

    /**
     * Excel：一个工作簿两个表 ——
     * <ul>
     *   <li>{@code BOM 结构}：纯表格（首行表头冻结 + 自动筛选），可直接透视/回导；</li>
     *   <li>{@code 导出说明}：这批数据是谁的、什么时候导的、成本口径与数据缺口提示。</li>
     * </ul>
     * 说明单独放一页而不是插在表格上方：表格上方的说明行会让"按第一行做表头"的
     * 透视/回导全部失灵。
     */
    private byte[] excel(List<Row> rows, BomCostReportVO report, boolean xlsx) throws IOException {
        try (Workbook wb = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle textStyle = wb.createCellStyle();
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(wb.createDataFormat().getFormat("0.####"));
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            Sheet sheet = wb.createSheet(SHEET_MAIN);
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            // 名称列的缩进样式按层级缓存：每行 cloneStyleFrom 会一行一个新样式，
            // 而 HSSF 的样式上限是 4096 —— 大 BOM 直接写不出来
            java.util.Map<Integer, CellStyle> indentStyles = new java.util.HashMap<>();
            for (Row row : rows) {
                org.apache.poi.ss.usermodel.Row line = sheet.createRow(r++);
                putText(line, 0, text(row.level), textStyle);
                putText(line, 1, row.lineNumber, textStyle);
                putText(line, 2, row.code, textStyle);
                // 名称用 Excel 原生缩进（而不是前导空格）：排序/查找都不受影响
                Cell nameCell = line.createCell(3);
                nameCell.setCellValue(nullToEmpty(row.name));
                int indent = Math.max(0, row.level > 0 ? row.level - 1 : 0);
                CellStyle indented = indentStyles.computeIfAbsent(indent, level -> {
                    CellStyle style = wb.createCellStyle();
                    style.cloneStyleFrom(textStyle);
                    style.setIndention(level.shortValue());
                    return style;
                });
                nameCell.setCellStyle(indented);
                putText(line, 4, row.version, textStyle);
                putNumber(line, 5, row.quantity, numberStyle);
                putText(line, 6, row.unit, textStyle);
                putNumber(line, 7, row.unitCost, numberStyle);
                putNumber(line, 8, row.extendedCost, moneyStyle);
                putNumber(line, 9, row.rolledUpCost, moneyStyle);
            }

            for (int i = 0; i < COLUMN_CHARS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_CHARS[i] * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rows.size()), 0, HEADERS.length - 1));

            Sheet meta = wb.createSheet(SHEET_META);
            int m = 0;
            m = metaRow(meta, m, "父件编码", report.getParentCode());
            m = metaRow(meta, m, "父件名称", report.getParentName());
            m = metaRow(meta, m, "版本", report.getParentVersion());
            m = metaRow(meta, m, "生命周期状态", report.getParentStatus());
            m = metaRow(meta, m, "本层直接成本", money(report.getDirectCost()));
            m = metaRow(meta, m, "卷积总成本（含下级）", money(report.getTotalCost()));
            m = metaRow(meta, m, "BOM 行数（含各层）", text(report.getTotalLines() == null ? 0 : report.getTotalLines()));
            m = metaRow(meta, m, "未填单位成本行数", text(report.getMissingCostLines() == null ? 0 : report.getMissingCostLines()));
            m = metaRow(meta, m, "导出时间", LocalDateTime.now().format(TS));
            if (report.getWarnings() != null) {
                for (String w : report.getWarnings()) {
                    m = metaRow(meta, m, "提示", w);
                }
            }
            meta.setColumnWidth(0, 24 * 256);
            meta.setColumnWidth(1, 60 * 256);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int metaRow(Sheet sheet, int index, String label, String value) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label == null ? "" : label);
        row.createCell(1).setCellValue(value == null ? "" : value);
        return index + 1;
    }

    private static void putText(org.apache.poi.ss.usermodel.Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(nullToEmpty(value));
        c.setCellStyle(style);
    }

    private static void putNumber(org.apache.poi.ss.usermodel.Row row, int col, Double value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value == null) {
            c.setBlank();
        } else {
            c.setCellValue(value);
        }
        c.setCellStyle(style);
    }

    // ==================== PDF ====================

    /**
     * PDF：横向 A4 报表 —— 标题（谁、哪一版、什么时候导） + 提示 + 明细表
     * （表头跨页重复、层级用左内边距表达、页脚带页码）。
     */
    private byte[] pdf(List<Row> rows, BomCostReportVO report) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BaseFont bf = cjkBaseFont();
        Font title = new Font(bf, 14, Font.BOLD);
        Font normal = new Font(bf, 8.5f, Font.NORMAL);
        Font bold = new Font(bf, 8.5f, Font.BOLD);
        Font grey = new Font(bf, 8.5f, Font.NORMAL, new Color(110, 110, 110));
        Font warn = new Font(bf, 8.5f, Font.NORMAL, new Color(180, 95, 6));

        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 30, 34);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new PageFooter(new Font(bf, 8, Font.NORMAL, new Color(130, 130, 130))));
        doc.open();

        doc.add(new Paragraph("BOM 结构 · " + nullToEmpty(report.getParentCode()) + " "
                + nullToEmpty(report.getParentName()), title));

        StringBuilder meta = new StringBuilder();
        meta.append("版本 ").append(nullToEmpty(report.getParentVersion()));
        if (report.getParentStatus() != null && !report.getParentStatus().isEmpty()) {
            meta.append(" · 状态 ").append(report.getParentStatus());
        }
        meta.append(" · 行数 ").append(report.getTotalLines() == null ? 0 : report.getTotalLines());
        meta.append(" · 本层直接成本 ").append(money(report.getDirectCost()));
        meta.append(" · 卷积总成本 ").append(money(report.getTotalCost()));
        meta.append(" · 导出时间 ").append(LocalDateTime.now().format(TS));
        doc.add(new Paragraph(meta.toString(), grey));
        if (report.getMissingCostLines() != null && report.getMissingCostLines() > 0) {
            doc.add(new Paragraph("注意：" + report.getMissingCostLines()
                    + " 行未填「单位成本」，已按 0 计入 —— 实际成本可能高于本报表", warn));
        }
        if (report.getWarnings() != null) {
            for (String w : report.getWarnings()) {
                doc.add(new Paragraph("提示：" + w, warn));
            }
        }
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(HEADERS.length);
        table.setWidths(PDF_WIDTHS);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(h, bold));
            cell.setBackgroundColor(new Color(238, 238, 238));
            cell.setPadding(4f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        for (Row row : rows) {
            table.addCell(pdfCell(text(row.level), normal, row.level, Element.ALIGN_CENTER));
            table.addCell(pdfCell(row.lineNumber, normal, 0, Element.ALIGN_CENTER));
            table.addCell(pdfCell(row.code, normal, 0, Element.ALIGN_LEFT));
            table.addCell(pdfCell(row.name, normal, row.level, Element.ALIGN_LEFT));
            table.addCell(pdfCell(row.version, normal, 0, Element.ALIGN_CENTER));
            table.addCell(pdfCell(num(row.quantity), normal, 0, Element.ALIGN_RIGHT));
            table.addCell(pdfCell(row.unit, normal, 0, Element.ALIGN_CENTER));
            table.addCell(pdfCell(unitCost(row.unitCost), normal, 0, Element.ALIGN_RIGHT));
            table.addCell(pdfCell(money(row.extendedCost), normal, 0, Element.ALIGN_RIGHT));
            table.addCell(pdfCell(money(row.rolledUpCost), normal, 0, Element.ALIGN_RIGHT));
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private static PdfPCell pdfCell(String text, Font font, int level, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(nullToEmpty(text), font));
        cell.setPadding(4f);
        // 层级用左内边距表达：比前导空格稳（换行时不会跑到行尾）
        if (level > 0) {
            cell.setPaddingLeft(4f + level * 10f);
        }
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /** 页脚页码：几十行的 BOM 也要能对上纸质版 */
    private static class PageFooter extends PdfPageEventHelper {
        private final Font font;

        PageFooter(Font font) {
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Phrase phrase = new Phrase("第 " + writer.getPageNumber() + " 页", font);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER, phrase,
                    (document.getPageSize().getLeft() + document.getPageSize().getRight()) / 2,
                    document.getPageSize().getBottom() - 10, 0);
        }
    }

    /**
     * PDF 用的中文字体。<b>优先嵌入系统中文字体</b>，其次才是 OpenPDF 自带的中文 CID 字体。
     *
     * <p>为什么这个顺序：CID 字体（STSong-Light）只是"按名字引用"、不嵌入、也没有 ToUnicode 表 ——
     * 肉眼看排版正常，但 PDF 里的文字<b>搜不到也复制不出来</b>（采购想拷个料号都不行，
     * 属性测试里表现为"第一页抽不到任何文本"）。TrueType 嵌入子集后文字可搜索可复制，
     * 代价只是文件大几百 KB。两者都拿不到就<b>明确失败</b>：生成一份中文全空白的 PDF
     * 比报错更糟 —— 用户以为导出了，拿去打印才发现没字。
     */
    private BaseFont cjkBaseFont() {
        BaseFont cached = cjkFont;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cjkFont != null) {
                return cjkFont;
            }
            for (String candidate : SYSTEM_FONT_CANDIDATES) {
                String path = candidate.contains(",") ? candidate.substring(0, candidate.indexOf(',')) : candidate;
                if (!new File(path).isFile()) {
                    continue;
                }
                try {
                    cjkFont = BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    log.info("PDF 使用系统字体 {}", candidate);
                    return cjkFont;
                } catch (Exception e) {
                    log.warn("加载系统字体失败 {}: {}", candidate, e.getMessage());
                }
            }
            try {
                // 兜底：能出中文，但文字不可抽取（无 ToUnicode），PDF 也更小
                cjkFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                log.warn("未找到系统中文字体，PDF 改用 CID 字体 STSong-Light：排版正常，但文字不可搜索/复制");
                return cjkFont;
            } catch (Exception e) {
                log.warn("中文 CID 字体也不可用: {}", e.getMessage());
            }
            throw new IllegalStateException("服务器上没有可用的中文字体，无法生成中文 PDF；请改用 Excel/CSV 导出，"
                    + "或为服务器安装中文字体（如 simhei.ttf / NotoSansCJK）");
        }
    }

    // ==================== 取值/格式化 ====================

    /** 一行导出数据（含根行） */
    public static class Row {
        /** 层级：根件为 0，其直接子件为 1，依次递增 */
        public int level;
        public String lineNumber;
        public String code;
        public String name;
        public String version;
        public Double quantity;
        public String unit;
        public Double unitCost;
        public Double extendedCost;
        public Double rolledUpCost;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String text(int v) {
        return String.valueOf(v);
    }

    private static String text(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 数量/单位成本：按原值，去掉多余的 0（12.50 → 12.5，0.005 保留三位） */
    private static String num(Double v) {
        if (v == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%s", new java.math.BigDecimal(String.valueOf(v))
                .stripTrailingZeros().toPlainString());
    }

    private static String unitCost(Double v) {
        return num(v);
    }

    /** 金额：两位小数（人民币口径） */
    private static String money(Double v) {
        return v == null ? "" : String.format(Locale.ROOT, "%.2f", v);
    }

    /** CSV 里用空格表达缩进（无样式可用） */
    private static String indent(String name, int level) {
        if (name == null) {
            return "";
        }
        if (level <= 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < level; i++) {
            sb.append("  ");
        }
        return sb.append(name).toString();
    }
}
