/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.export;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOM 导出器的单测（不连库：手搭一棵两层的成本报告喂进去）。
 *
 * <p>钉住四件事：
 * <ol>
 *   <li>平铺顺序与层级：根件(0) → 子件(1) → 孙件(2)，深度优先；</li>
 *   <li>CSV 带 UTF-8 BOM（否则 Excel 打开中文乱码）且中文表头/数据都在；</li>
 *   <li>生成的 xls/xlsx 能被 POI 读回（不是"写了个长得像 Excel 的字节流"）；</li>
 *   <li>PDF 能生成且文本可抽回（含中文 —— 字体链路不对时中文会整段丢失）。</li>
 * </ol>
 */
class BomExporterTest {

    private final BomExporter exporter = new BomExporter();

    /** 父件 激光鼠标-X3 (PART-1 / A.4)，下挂 子件A（用量 2，单位成本 3.5）→ 孙件B（用量 5，单位成本 1.2） */
    private BomCostReportVO report() {
        BomCostReportVO vo = new BomCostReportVO();
        vo.setParentIterationOid("iter-root");
        vo.setParentCode("PART-1");
        vo.setParentName("激光鼠标-X3");
        vo.setParentVersion("A.4");
        vo.setParentStatus("DRAFT");
        vo.setDirectCost(7.0);
        vo.setTotalCost(13.0);
        vo.setTotalLines(2);
        vo.setMissingCostLines(0);
        vo.setWarnings(new ArrayList<>(List.of("测试用提示")));

        BomTreeNode grandChild = node("PART-3", "微动开关", "A.1", 5d, 1.2, 6.0, 6.0);
        BomTreeNode child = node("PART-2", "鼠标外壳,含漆", "B.2", 2d, 3.5, 7.0, 13.0);
        child.setLineNumber(10);
        child.setChildren(new ArrayList<>(List.of(grandChild)));
        vo.setLines(new ArrayList<>(List.of(child)));
        return vo;
    }

    private static BomTreeNode node(String code, String name, String version, Double qty,
                                    Double unitCost, Double extended, Double rolledUp) {
        BomTreeNode n = new BomTreeNode();
        n.setChildPartNumber(code);
        n.setChildPartName(name);
        n.setChildVersion(version);
        n.setQuantity(qty);
        n.setUnit("件");
        n.setUnitCost(unitCost);
        n.setExtendedCost(extended);
        n.setRolledUpCost(rolledUp);
        return n;
    }

    @Test
    void 平铺_根件在最前_子件按深度优先且层级递增() {
        List<BomExporter.Row> rows = exporter.rows(report());
        assertEquals(3, rows.size());
        assertEquals(0, rows.get(0).level);
        assertEquals("PART-1", rows.get(0).code);
        assertEquals(1, rows.get(1).level);
        assertEquals("PART-2", rows.get(1).code);
        assertEquals(2, rows.get(2).level);
        assertEquals("PART-3", rows.get(2).code);
        // 根行的成本取自报告合计（完整单位成本 = 卷积总成本）
        assertEquals(13.0, rows.get(0).unitCost);
        assertEquals(7.0, rows.get(0).extendedCost);
    }

    @Test
    void csv_带BOM且中文表头与数据完好() {
        byte[] bytes = exporter.write(exporter.rows(report()), report(), BomExportFormat.CSV);
        assertTrue(bytes.length > 3, "CSV 不该是空的");
        // UTF-8 BOM：没有它 Excel 按本地代码页解码，中文表头直接乱码
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.contains("层级,行号,子件编码"), "缺少表头: " + text.lines().findFirst().orElse(""));
        assertTrue(text.contains("激光鼠标-X3"));
        // 含逗号的名称必须被引号包住，否则列会错位
        assertTrue(text.contains("\"鼠标外壳,含漆\""), "含逗号的字段未加引号");
        assertTrue(text.contains("13.00"), "累计金额应两位小数");
    }

    @Test
    void xlsx_能被POI读回且中文完好() throws Exception {
        byte[] bytes = exporter.write(exporter.rows(report()), report(), BomExportFormat.XLSX);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets(), "应有「BOM 结构」与「导出说明」两页");
            Sheet sheet = wb.getSheet("BOM 结构");
            assertEquals("层级", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("激光鼠标-X3", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("PART-2", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals(2d, sheet.getRow(2).getCell(5).getNumericCellValue());
            // 说明页带上成本口径
            assertTrue(hasCell(wb.getSheet("导出说明"), "卷积总成本（含下级）"));
        }
    }

    @Test
    void xls_能被POI读回() throws Exception {
        byte[] bytes = exporter.write(exporter.rows(report()), report(), BomExportFormat.XLS);
        try (Workbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("BOM 结构");
            assertEquals("子件名称", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("鼠标外壳,含漆", sheet.getRow(2).getCell(3).getStringCellValue());
            assertEquals("微动开关", sheet.getRow(3).getCell(3).getStringCellValue());
        }
    }

    @Test
    void pdf_能生成且文本可抽回() throws Exception {
        byte[] bytes = exporter.write(exporter.rows(report()), report(), BomExportFormat.PDF);
        assertTrue(bytes.length > 500);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.ISO_8859_1));

        try (PdfReader reader = new PdfReader(bytes)) {
            assertTrue(reader.getNumberOfPages() >= 1);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertFalse(text.isBlank(), "PDF 第一页没有可抽取的文本");
            assertTrue(text.contains("PART-1"), "PDF 缺少父件编码，抽到的文本: " + text);
            // 中文能抽回来 = 字体链路正确（CID 或系统字体）；抽不到说明字体回退了西文
            assertTrue(text.contains("激光鼠标") || text.contains("鼠标外壳"),
                    "PDF 中文未正确写入，抽到的文本: " + text);
        }
    }

    private static boolean hasCell(Sheet sheet, String value) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                if (cell != null && value.equals(cell.getStringCellValue())) {
                    return true;
                }
            }
        }
        return false;
    }
}
