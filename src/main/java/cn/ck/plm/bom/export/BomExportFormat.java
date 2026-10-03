/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.export;

/**
 * BOM 导出格式：{@code csv} / {@code xls} / {@code xlsx} / {@code pdf}。
 *
 * <p>把「扩展名」与「Content-Type」收在一处：控制器只认枚举，前端只传字符串，
 * 两边都不必各写一张映射表（那种表最容易在新增格式时漏改一边）。
 */
public enum BomExportFormat {

    /** 纯文本表格（UTF-8 带 BOM，Excel 双击打开中文不乱码），可直接回导 */
    CSV("csv", "text/csv; charset=UTF-8"),

    /** Excel 97-2003 二进制格式（HSSF） */
    XLS("xls", "application/vnd.ms-excel"),

    /** Excel 2007+（XSSF）—— 默认格式 */
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),

    /** PDF 报表（横向 A4，表头跨页重复） */
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    BomExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 宽松解析：大小写不敏感，可写 {@code xlsx} / {@code XLSX}；空或无法识别时给 {@link #XLSX}
     * —— 导出这种操作"给个最通用的"比"报错让用户重来"更合适。
     */
    public static BomExportFormat of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return XLSX;
        }
        String v = value.trim();
        for (BomExportFormat f : values()) {
            if (f.extension.equalsIgnoreCase(v) || f.name().equalsIgnoreCase(v)) {
                return f;
            }
        }
        return XLSX;
    }
}
