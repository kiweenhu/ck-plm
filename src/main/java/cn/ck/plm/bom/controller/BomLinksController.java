/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.service.BomExportService;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * BomLinks REST 控制器。
 */
@RestController
@RequestMapping("/api/bom-links")
public class BomLinksController {

    @Autowired
    private BomLinksService bomLinksService;

    @Autowired
    private BomExportService bomExportService;

    @PostMapping
    public ApiResponse<BomLinks> create(@RequestBody BomLinks bomLinks) {
        return ApiResponse.ok(bomLinksService.create(bomLinks));
    }

    /**
     * 更新 BOM 行（行号 / 数量 / 单位 / 单位成本）。
     *
     * <p>失败折成 {@code code=400 + 可读原因}：项目里没有全局异常处理器，
     * 抛出去前端只能看到「服务器错误 (500)」——而这里最需要说清的恰恰是
     * "为什么没生效"（行已被检出的工作副本取代 / 已被删除）。
     */
    @PutMapping("/{oid}")
    public ApiResponse<BomLinks> update(@PathVariable String oid, @RequestBody BomLinks bomLinks) {
        bomLinks.setOid(oid);
        try {
            return ApiResponse.ok(bomLinksService.update(bomLinks));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        bomLinksService.deleteByOid(oid);
        return ApiResponse.ok();
    }

    @GetMapping("/{oid}")
    public BomLinks getByOid(@PathVariable String oid) {
        return bomLinksService.getByOid(oid);
    }

    @GetMapping("/by-parent-iteration/{parentIterationOid}")
    public ApiResponse<List<BomLinks>> listByParentIteration(@PathVariable String parentIterationOid) {
        return ApiResponse.ok(bomLinksService.listByParentIterationOid(parentIterationOid));
    }

    /** 递归构建某父迭代下的完整多层 BOM 树（含子件展示信息） */
    @GetMapping("/tree/{parentIterationOid}")
    public ApiResponse<List<BomTreeNode>> getTree(@PathVariable String parentIterationOid) {
        return ApiResponse.ok(bomLinksService.buildTree(parentIterationOid));
    }

    /**
     * BOM 成本报告（卷积）：每一层「数量 × 单位成本」递归累加，含总成本与逐层明细。
     *
     * <p>入参用<b>父件迭代 oid</b>而不是零件主对象 oid：成本是有版本的口径，
     * A.4 与 B.1 的结构和成本都可能不同（BOM 行就挂在迭代上）。
     */
    @GetMapping("/cost-report/{parentIterationOid}")
    public ApiResponse<BomCostReportVO> getCostReport(@PathVariable String parentIterationOid) {
        return ApiResponse.ok(bomLinksService.costReport(parentIterationOid));
    }

    /**
     * 导出 BOM（{@code csv} / {@code xls} / {@code xlsx} / {@code pdf}）——
     * 当前这一版的<b>完整多层结构 + 成本</b>。
     *
     * <p>入参与成本报告同口径（父件迭代 oid）：导出跟着版本走，不自行挑"最新版"，
     * 否则会出现"页面看的是 A.4、导出的是 B.1"的错配，而两份文件放一起看不出来。
     *
     * <p>返回文件字节（Content-Disposition 里带文件名）；前端要用 blob 拉取后本地保存 ——
     * 这个接口要鉴权，{@code window.open} 带不上 Authorization 头。
     */
    @GetMapping("/export/{parentIterationOid}")
    public ResponseEntity<byte[]> export(@PathVariable String parentIterationOid,
                                         @RequestParam(name = "format", defaultValue = "xlsx") String format) {
        BomExportService.ExportFile file;
        try {
            file = bomExportService.export(parentIterationOid, format);
        } catch (IllegalArgumentException e) {
            // 找不到版本这类"用户可纠正"的错误给 404 + JSON：前端拿的是 blob，
            // 只有 JSON 体才能把这句话读出来显示（否则只能弹"服务器错误 (500)"）
            String json = "{\"code\":404,\"message\":\"" + escapeJson(e.getMessage()) + "\",\"data\":null}";
            return ResponseEntity.status(404)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                    .body(json.getBytes(StandardCharsets.UTF_8));
        }
        // 文件名可能是中文：filename= 给 ASCII 兜底，filename*= 给 UTF-8 真名（RFC 5987）
        String ascii = file.getFileName().replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
                .body(file.getBytes());
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @GetMapping("/by-child-part/{childPartOid}")
    public ApiResponse<List<BomLinks>> listByChildPart(@PathVariable String childPartOid) {
        return ApiResponse.ok(bomLinksService.listByChildPartOid(childPartOid));
    }

    @GetMapping("/by-child-iteration/{childIterationOid}")
    public List<BomLinks> listByChildIteration(@PathVariable String childIterationOid) {
        return bomLinksService.listByChildIterationOid(childIterationOid);
    }

    @PutMapping("/{oid}/resolve-iteration")
    public void refreshResolvedIteration(@PathVariable String oid, @RequestParam String resolvedIterationOid) {
        bomLinksService.refreshResolvedIteration(oid, resolvedIterationOid);
    }

    @GetMapping
    public List<BomLinks> listAll() {
        return bomLinksService.listAll();
    }
}
