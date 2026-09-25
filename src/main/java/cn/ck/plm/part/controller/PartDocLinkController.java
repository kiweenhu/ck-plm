/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.entity.PartDescribeLink;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.entity.PartReferenceLink;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.service.api.PartDescribeLinkService;
import cn.ck.plm.part.service.api.PartReferenceLinkService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 零件-文档关联（Part Doc Link）统一 REST 接口。
 *
 * <p>对应 Windchill 的两分模型：
 * <ul>
 *   <li>{@code DESCRIBES}（定义/描述）—— {@link PartDescribeLink}：文档的变更后果 = 零件的变更后果</li>
 *   <li>{@code REFERENCE}（参考）—— {@link PartReferenceLink}：轻量伴随关系，可晋升为 DESCRIBES</li>
 * </ul>
 *
 * <p>两类关系共享数据表 {@code ck_doc_part_link}，通过 {@code link_type} 区分，
 * 本控制器按 {@code linkType} 路由到对应 Service。
 *
 * <h3>挂接粒度</h3>
 * 关联挂在<b>零件迭代（版本）</b>上（对齐 BOM 粒度）。为兼容主数据级调用，
 * 允许传 {@code partOid}，由后端解析到该零件的最新迭代。
 */
@RestController
@RequestMapping("/api/part-doc-links")
public class PartDocLinkController {

    private final PartDescribeLinkService describeLinkService;
    private final PartReferenceLinkService referenceLinkService;
    private final PartIterationMapper partIterationMapper;

    public PartDocLinkController(PartDescribeLinkService describeLinkService,
                                 PartReferenceLinkService referenceLinkService,
                                 PartIterationMapper partIterationMapper) {
        this.describeLinkService = describeLinkService;
        this.referenceLinkService = referenceLinkService;
        this.partIterationMapper = partIterationMapper;
    }

    // ==================== 查询 ====================

    /**
     * 查询零件迭代下的文档关联。
     *
     * @param partIterationOid 零件迭代 oid（优先）
     * @param partOid          零件主数据 oid（未提供迭代时取最新迭代）
     * @param linkType         DESCRIBES / REFERENCE（可空 = 全部）
     */
    @GetMapping
    public ApiResponse<List<Object>> list(@RequestParam(required = false) String partIterationOid,
                                          @RequestParam(required = false) String partOid,
                                          @RequestParam(required = false) String linkType) {
        String iterOid = resolvePartIterationOid(partIterationOid, partOid);
        if (iterOid == null) return ApiResponse.ok(new ArrayList<>());
        String type = normalizeType(linkType);
        List<Object> result = new ArrayList<>();
        if (type == null || "DESCRIBES".equals(type)) {
            result.addAll(describeLinkService.findByPartIteration(iterOid));
        }
        if (type == null || "REFERENCE".equals(type)) {
            result.addAll(referenceLinkService.findByPartIteration(iterOid));
        }
        return ApiResponse.ok(result);
    }

    // ==================== 创建 ====================

    /**
     * 创建文档关联。
     *
     * <p>body: { partIterationOid | partOid, docMasterOid, docIterationOid?, linkType?, category? }
     */
    @PostMapping
    public ApiResponse<Object> create(@RequestBody Map<String, Object> body) {
        String iterOid = resolvePartIterationOid(
                str(body.get("partIterationOid")), str(body.get("partOid")));
        if (iterOid == null) {
            return ApiResponse.fail(400, "缺少零件迭代信息（partIterationOid 或 partOid）");
        }
        String docMasterOid = str(body.get("docMasterOid"));
        if (docMasterOid == null) {
            return ApiResponse.fail(400, "缺少文档（docMasterOid）");
        }
        String linkType = normalizeType(str(body.get("linkType")));
        String category = str(body.get("category"));
        String docIterationOid = str(body.get("docIterationOid"));
        try {
            if ("DESCRIBES".equals(linkType)) {
                PartDescribeLink link = new PartDescribeLink();
                link.setPartIterationOid(iterOid);
                link.setDocMasterOid(docMasterOid);
                link.setDocIterationOid(docIterationOid);
                link.setCategory(category);
                return ApiResponse.ok(describeLinkService.create(link));
            }
            PartReferenceLink link = new PartReferenceLink();
            link.setPartIterationOid(iterOid);
            link.setDocMasterOid(docMasterOid);
            link.setDocIterationOid(docIterationOid);
            link.setCategory(category);
            return ApiResponse.ok(referenceLinkService.create(link));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建关联失败: " + e.getMessage());
        }
    }

    // ==================== 删除 ====================

    /** 删除关联：linkType 指明删除哪一类（默认 REFERENCE） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid,
                                    @RequestParam(required = false, defaultValue = "REFERENCE") String linkType) {
        try {
            if ("DESCRIBES".equals(normalizeType(linkType))) {
                describeLinkService.delete(oid);
            } else {
                referenceLinkService.delete(oid);
            }
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除关联失败: " + e.getMessage());
        }
    }

    // ==================== 晋升 ====================

    /** 晋升：REFERENCE → DESCRIBES（进入技术状态基线） */
    @PostMapping("/{oid}/promote")
    public ApiResponse<Object> promote(@PathVariable String oid) {
        try {
            return ApiResponse.ok(describeLinkService.promote(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "晋升失败: " + e.getMessage());
        }
    }

    // ==================== 内部工具 ====================

    /** 解析零件迭代 oid：优先 partIterationOid，否则按 partOid 取最新迭代 */
    private String resolvePartIterationOid(String partIterationOid, String partOid) {
        if (partIterationOid != null && !partIterationOid.trim().isEmpty()) {
            return partIterationOid.trim();
        }
        if (partOid == null || partOid.trim().isEmpty()) return null;
        PartIteration latest = partIterationMapper.selectLatestByMasterOid(partOid.trim());
        return latest == null ? null : latest.getOid();
    }

    /** 归一化关联类型：DESCRIPTION 视为 DESCRIBES；空视为 null（全部） */
    private String normalizeType(String linkType) {
        if (linkType == null || linkType.trim().isEmpty()) return null;
        String t = linkType.trim().toUpperCase();
        if ("DESCRIPTION".equals(t) || "DESCRIBE".equals(t)) return "DESCRIBES";
        return t;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
