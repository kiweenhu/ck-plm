/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.controller;

import cn.ck.plm.document.dto.EngDocPartLinkVO;
import cn.ck.plm.document.entity.EngDocPartLink;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;
import cn.ck.plm.document.mapper.EngineeringDocumentIterationMapper;
import cn.ck.plm.document.mapper.EngineeringDocumentMapper;
import cn.ck.plm.document.service.EngDocPartLinkService;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.mapper.PartMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工程数据 ↔ 零部件构建规则 REST 接口（{@code /api/eng-doc-part-links}）。
 *
 * <h3>挂接粒度</h3>
 * 关系挂在<b>零件迭代</b>上，工程数据侧记主对象 + 可选精确迭代
 * （为空 = 跟随该工程数据的最新版本）。为兼容主数据级调用，
 * 允许传 {@code partOid}，由后端解析到该零件的最新迭代 —— 与 {@code /api/part-doc-links} 同口径。
 *
 * <h3>两个方向共用一套接口</h3>
 * <ul>
 *   <li>零件详情：{@code GET ?partOid=xxx} → 列出该零件版本关联的工程数据；</li>
 *   <li>工程数据详情：{@code GET ?engDocumentOid=xxx} → 列出引用它的零件版本。</li>
 * </ul>
 *
 * <h3>端点</h3>
 * <pre>
 * GET    /api/eng-doc-part-links?partOid=|partIterationOid=     零件 → 工程数据
 * GET    /api/eng-doc-part-links?engDocumentOid=                工程数据 → 零件
 * POST   /api/eng-doc-part-links                                建立关联
 * PUT    /api/eng-doc-part-links/{oid}                          修改关联类型 / 构建规则
 * DELETE /api/eng-doc-part-links/{oid}                          解除关联
 * </pre>
 */
@RestController
@RequestMapping("/api/eng-doc-part-links")
public class EngDocPartLinkController {

    private final EngDocPartLinkService linkService;
    private final PartIterationMapper partIterationMapper;
    private final PartMapper partMapper;
    private final EngineeringDocumentMapper engDocumentMapper;
    private final EngineeringDocumentIterationMapper engIterationMapper;

    public EngDocPartLinkController(EngDocPartLinkService linkService,
                                    PartIterationMapper partIterationMapper,
                                    PartMapper partMapper,
                                    EngineeringDocumentMapper engDocumentMapper,
                                    EngineeringDocumentIterationMapper engIterationMapper) {
        this.linkService = linkService;
        this.partIterationMapper = partIterationMapper;
        this.partMapper = partMapper;
        this.engDocumentMapper = engDocumentMapper;
        this.engIterationMapper = engIterationMapper;
    }

    // ==================== 查询 ====================

    @GetMapping
    public ApiResponse<List<EngDocPartLinkVO>> list(@RequestParam(required = false) String partIterationOid,
                                                    @RequestParam(required = false) String partOid,
                                                    @RequestParam(required = false) String engDocumentOid,
                                                    @RequestParam(required = false) String engDocIterationOid) {
        try {
            List<EngDocPartLink> links;
            if (engDocumentOid != null && !engDocumentOid.trim().isEmpty()) {
                links = linkService.findByEngDocument(engDocumentOid.trim());
            } else if (engDocIterationOid != null && !engDocIterationOid.trim().isEmpty()) {
                links = linkService.findByEngDocIteration(engDocIterationOid.trim());
            } else {
                String iterOid = resolvePartIterationOid(partIterationOid, partOid);
                if (iterOid == null) return ApiResponse.ok(new ArrayList<>());
                links = linkService.findByPartIteration(iterOid);
            }
            List<EngDocPartLinkVO> result = new ArrayList<>(links.size());
            for (EngDocPartLink link : links) {
                result.add(toVO(link));
            }
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询工程数据关联失败: " + e.getMessage());
        }
    }

    // ==================== 写入 ====================

    /**
     * 建立关联。
     *
     * <p>body: { partIterationOid | partOid, engDocumentOid | engDocIterationOid,
     * engDocIterationOid?, assocType?, buildStructure?, buildAttribute?, buildRepresentation?,
     * primary?, sortOrder?, assocSource? }
     */
    @PostMapping
    public ApiResponse<EngDocPartLinkVO> create(@RequestBody Map<String, Object> body) {
        try {
            String partIterationOid = resolvePartIterationOid(
                    str(body.get("partIterationOid")), str(body.get("partOid")));
            if (partIterationOid == null) {
                return ApiResponse.fail(400, "缺少零件版本信息（partIterationOid 或 partOid）");
            }
            String engDocIterationOid = str(body.get("engDocIterationOid"));
            String engDocumentOid = str(body.get("engDocumentOid"));
            // 只给了迭代也能反推主对象
            if ((engDocumentOid == null || engDocumentOid.isEmpty()) && engDocIterationOid != null) {
                EngineeringDocumentIteration iteration = engIterationMapper.selectByOid(engDocIterationOid);
                if (iteration != null) engDocumentOid = iteration.getMasterOid();
            }
            if (engDocumentOid == null || engDocumentOid.isEmpty()) {
                return ApiResponse.fail(400, "缺少工程数据（engDocumentOid）");
            }

            EngDocPartLink link = new EngDocPartLink();
            link.setPartIterationOid(partIterationOid);
            link.setEngDocumentOid(engDocumentOid);
            link.setEngDocIterationOid(engDocIterationOid);
            link.setAssocType(str(body.get("assocType")));
            link.setBuildStructure(bool(body.get("buildStructure")));
            link.setBuildAttribute(bool(body.get("buildAttribute")));
            link.setBuildRepresentation(bool(body.get("buildRepresentation")));
            link.setPrimary(bool(body.get("primary")));
            link.setSortOrder(intOrZero(body.get("sortOrder")));
            link.setAssocSource(str(body.get("assocSource")));

            return ApiResponse.ok(toVO(linkService.create(link)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "建立关联失败: " + e.getMessage());
        }
    }

    /** 修改关联类型 / 构建规则 / 锁定的工程数据版本 */
    @PutMapping("/{oid}")
    public ApiResponse<EngDocPartLinkVO> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            EngDocPartLink link = new EngDocPartLink();
            link.setOid(oid);
            link.setAssocType(str(body.get("assocType")));
            link.setEngDocIterationOid(str(body.get("engDocIterationOid")));
            link.setBuildStructure(bool(body.get("buildStructure")));
            link.setBuildAttribute(bool(body.get("buildAttribute")));
            link.setBuildRepresentation(bool(body.get("buildRepresentation")));
            link.setPrimary(bool(body.get("primary")));
            link.setSortOrder(intOrZero(body.get("sortOrder")));
            link.setAssocSource(str(body.get("assocSource")));
            return ApiResponse.ok(toVO(linkService.update(link)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "修改关联失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            linkService.delete(oid);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "解除关联失败: " + e.getMessage());
        }
    }

    // ==================== 内部工具 ====================

    /** 补齐两端可读信息，避免前端 N+1 查询 */
    private EngDocPartLinkVO toVO(EngDocPartLink link) {
        EngDocPartLinkVO vo = new EngDocPartLinkVO();
        vo.setOid(link.getOid());
        vo.setPartIterationOid(link.getPartIterationOid());
        vo.setEngDocumentOid(link.getEngDocumentOid());
        vo.setEngDocIterationOid(link.getEngDocIterationOid());
        vo.setAssocType(link.getAssocType());
        vo.setBuildStructure(link.isBuildStructure());
        vo.setBuildAttribute(link.isBuildAttribute());
        vo.setBuildRepresentation(link.isBuildRepresentation());
        vo.setAssocSource(link.getAssocSource());
        vo.setPrimary(link.isPrimary());
        vo.setSortOrder(link.getSortOrder());
        vo.setPinned(link.isPinned());
        vo.setCreatedAt(link.getCreatedAt());

        // 零件侧
        PartIteration partIteration = link.getPartIterationOid() == null
                ? null : partIterationMapper.selectByOid(link.getPartIterationOid());
        if (partIteration != null) {
            vo.setPartVersion(partIteration.getDisplayVersion());
            Part part = partIteration.getMasterOid() == null
                    ? null : partMapper.selectByOid(partIteration.getMasterOid());
            if (part != null) {
                vo.setPartOid(part.getOid());
                vo.setPartNumber(part.getNumber());
                vo.setPartName(part.getName());
            }
        }

        // 工程数据侧
        EngineeringDocument document = link.getEngDocumentOid() == null
                ? null : engDocumentMapper.selectByOid(link.getEngDocumentOid());
        if (document != null) {
            vo.setEngDocNumber(document.getNumber());
            vo.setEngDocName(document.getName());
            vo.setEngDocType(document.getTypeDefinitionCode());
        }
        // 跟随最新时把解析到的实际版本也回填，前端才能显示"当前指向哪一版"
        EngineeringDocumentIteration engIteration = link.isPinned()
                ? engIterationMapper.selectByOid(link.getEngDocIterationOid())
                : engIterationMapper.selectLatestByMasterOid(link.getEngDocumentOid());
        if (engIteration != null) {
            vo.setEngDocIterationOid(engIteration.getOid());
            vo.setEngDocVersion(engIteration.getDisplayVersion());
        }
        return vo;
    }

    /** 解析零件迭代 oid：优先 partIterationOid，否则按 partOid 取最新迭代 */
    private String resolvePartIterationOid(String partIterationOid, String partOid) {
        if (partIterationOid != null && !partIterationOid.trim().isEmpty()) {
            return partIterationOid.trim();
        }
        if (partOid == null || partOid.trim().isEmpty()) return null;
        PartIteration latest = partIterationMapper.selectLatestByMasterOid(partOid.trim());
        return latest == null ? null : latest.getOid();
    }

    private String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private boolean bool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private int intOrZero(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
