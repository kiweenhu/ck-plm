/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.export.BomExportFormat;
import cn.ck.plm.bom.export.BomExporter;
import cn.ck.plm.bom.service.api.BomLinksService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * BOM 导出服务 —— 「取数（哪一版的结构与成本） + 出文件（四种格式）」的编排。
 *
 * <p><b>口径跟版本走</b>：入参是<b>父件迭代 oid</b>，与页面左侧 BOM 结构、成本报告同一口径
 * （A.4 与 B.1 的结构和成本都可能不同）。导出绝不"自己去挑最新版"—— 那样会出现
 * "页面看着是 A.4、导出的是 B.1"的错配，而两份文件放一起根本看不出来。
 */
@Service
public class BomExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final BomLinksService bomLinksService;
    private final BomExporter exporter;

    public BomExportService(BomLinksService bomLinksService, BomExporter exporter) {
        this.bomLinksService = bomLinksService;
        this.exporter = exporter;
    }

    /**
     * 导出指定父件迭代的完整 BOM 结构。
     *
     * @param parentIterationOid 父件迭代 oid（哪一版）
     * @param format             csv / xls / xlsx / pdf（空或未知按 xlsx）
     * @throws IllegalArgumentException 该迭代不存在（或不属于当前租户）
     */
    public ExportFile export(String parentIterationOid, String format) {
        BomExportFormat fmt = BomExportFormat.of(format);
        // 结构树 + 成本一次取到：导出的成本列必须与页面「成本报告」逐行对得上
        BomCostReportVO report = bomLinksService.costReport(parentIterationOid);
        if (report.getParentCode() == null && report.getParentName() == null) {
            throw new IllegalArgumentException("找不到该版本对应的零件（迭代 oid: " + parentIterationOid + "）");
        }
        List<BomExporter.Row> rows = exporter.rows(report);
        byte[] bytes = exporter.write(rows, report, fmt);
        return new ExportFile(fileName(report, fmt), fmt.contentType(), bytes);
    }

    /** 文件名：编码-版本-BOM-时间戳.扩展名（字符按各系统通吃的白名单过滤） */
    private static String fileName(BomCostReportVO report, BomExportFormat fmt) {
        StringBuilder sb = new StringBuilder();
        String code = sanitize(report.getParentCode());
        String version = sanitize(report.getParentVersion());
        sb.append(code.isEmpty() ? "BOM" : code);
        if (!version.isEmpty()) {
            sb.append('-').append(version);
        }
        sb.append("-BOM-").append(LocalDateTime.now().format(FILE_TS)).append('.').append(fmt.extension());
        return sb.toString();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    /** 导出结果：文件名 + Content-Type + 字节 */
    public static class ExportFile {

        private final String fileName;
        private final String contentType;
        private final byte[] bytes;

        public ExportFile(String fileName, String contentType, byte[] bytes) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        public String getFileName() { return fileName; }

        public String getContentType() { return contentType; }

        public byte[] getBytes() { return bytes; }
    }
}
