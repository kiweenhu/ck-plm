/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.controller;

import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.cls.service.impl.ClsIbaDataSupport;
import cn.ck.plm.document.dto.EngineeringDocumentVO;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;
import cn.ck.plm.document.service.api.EngineeringDocumentService;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.service.api.IBADataService;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工程数据（EngineeringDocument）REST API 控制器 —— 参照 Windchill {@code EPMDocument}。
 *
 * <p>与 {@link DocumentController}（通用文档）并列：工程数据是 CAD 设计数据（3D 数模 / 2D 工程图 /
 * 封装 FOOTPRINT / 图符 SYMBOL 等），拥有独立表 {@code ck_eng_document} + {@code ck_eng_document_iteration}。
 *
 * <p><b>创建不在本控制器</b>：统一走 {@code POST /api/softtype-instances}（按
 * {@code type_definition.root_type_code} 路由到 {@code EngineeringDocumentInstanceCreator}）。
 * 本控制器提供<b>其余全部生命周期操作</b>，与 {@link DocumentController} 端点一一对应。
 *
 * <pre>
 * PUT    /api/eng-documents/{oid}                    更新（含分类 IBA + 实体 IBA + CAD 属性）
 * PUT    /api/eng-documents/{oid}/rename             重命名
 * PUT    /api/eng-documents/{oid}/move               移动（容器/阶段/文件夹）
 * DELETE /api/eng-documents/{oid}                    删除（含全部子版本）
 * DELETE /api/eng-documents/{oid}/latest-iteration   删除最新小版本
 * POST   /api/eng-documents/{oid}/new-view-version   新建视图版本
 * GET    /api/eng-documents/{oid}                    详情（指定/最新迭代 + 分类 IBA）
 * GET    /api/eng-documents                          列表（folderOid / containerOid+stageOid）
 * GET    /api/eng-documents/folder-details           文件夹下 VO 列表（含迭代/生命周期/类型名）
 * GET    /api/eng-documents/{oid}/iterations         历史版本 + 实体 IBA + 迭代级分类 IBA
 * </pre>
 *
 * <p>检出 / 检入 / 取消检出走通用入口 {@code /api/checkout/*}，
 * 由 {@code EngineeringDocumentCheckoutProvider}（entityType={@code ENG_DOCUMENT}）提供能力。
 */
@RestController
@RequestMapping("/api/eng-documents")
public class EngineeringDocumentController {

    /** 实体 IBA（ck_type_iba_data.entity_type）与检出路由使用的实体类型编码 */
    private static final String IBA_ENTITY_TYPE = "ENG_DOCUMENT";

    /** 能力宿主 code（= type_definition.root_type_code），用于统一入口的读写路由 */
    private static final String HOST = "ENG_DOCUMENT";

    private final EngineeringDocumentService engDocumentService;
    private final IBADataService ibaDataService;
    private final ClsIbaDataService clsIbaDataService;
    private final IbaDataSupport ibaDataSupport;
    private final ClsIbaDataSupport clsIbaDataSupport;
    private final ObjectMapper objectMapper;
    private final SoftTypeInstanceService softTypeInstanceService;

    public EngineeringDocumentController(EngineeringDocumentService engDocumentService,
                                         IBADataService ibaDataService,
                                         ClsIbaDataService clsIbaDataService,
                                         IbaDataSupport ibaDataSupport,
                                         ClsIbaDataSupport clsIbaDataSupport,
                                         ObjectMapper objectMapper,
                                         SoftTypeInstanceService softTypeInstanceService) {
        this.engDocumentService = engDocumentService;
        this.ibaDataService = ibaDataService;
        this.clsIbaDataService = clsIbaDataService;
        this.ibaDataSupport = ibaDataSupport;
        this.clsIbaDataSupport = clsIbaDataSupport;
        this.objectMapper = objectMapper;
        this.softTypeInstanceService = softTypeInstanceService;
    }

    // ==================== 更新 ====================

    /** 更新工程数据（主数据 + 迭代级 CAD 属性 + 分类 IBA + 实体 IBA） */
    @PutMapping("/{oid}")
    public ApiResponse<EngineeringDocument> update(@PathVariable String oid,
                                                   @RequestBody Map<String, Object> body) {
        try {
            // 更新编排已收敛到 EngineeringDocumentServiceImpl#updateInstance，与统一入口共用同一实现
            Object entity = softTypeInstanceService.updateForHost(HOST, oid, body);
            return ApiResponse.ok((EngineeringDocument) entity);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新工程数据失败: " + e.getMessage());
        }
    }

    /** 重命名工程数据 */
    @PutMapping("/{oid}/rename")
    public ApiResponse<EngineeringDocument> rename(@PathVariable String oid,
                                                   @RequestBody Map<String, Object> body) {
        try {
            Object name = body.get("name");
            if (name == null || name.toString().trim().isEmpty()) {
                return ApiResponse.fail(400, "名称不能为空");
            }
            return ApiResponse.ok(engDocumentService.rename(oid, name.toString().trim()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "重命名失败: " + e.getMessage());
        }
    }

    /** 移动工程数据到新的容器/阶段/文件夹位置 */
    @PutMapping("/{oid}/move")
    public ApiResponse<EngineeringDocument> move(@PathVariable String oid,
                                                 @RequestBody Map<String, Object> body) {
        try {
            String containerOid = ibaDataSupport.getString(body, "containerOid");
            String containerType = ibaDataSupport.getString(body, "containerType");
            String folderOid = ibaDataSupport.getString(body, "folderOid");
            String stageOid = ibaDataSupport.getString(body, "stageOid");
            return ApiResponse.ok(engDocumentService.move(oid, containerOid, containerType, folderOid, stageOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "移动失败: " + e.getMessage());
        }
    }

    // ==================== 删除 ====================

    /** 删除工程数据（含全部子版本） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            engDocumentService.delete(oid);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除工程数据失败: " + e.getMessage());
        }
    }

    /** 删除工程数据的最新小版本（仅删除最新 iteration） */
    @DeleteMapping("/{oid}/latest-iteration")
    public ApiResponse<Void> deleteLatestIteration(@PathVariable String oid) {
        try {
            engDocumentService.deleteLatestIteration(oid);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除最新版本失败: " + e.getMessage());
        }
    }

    /** 新建视图版本（创建新的大版本 revision+1，iteration=1） */
    @PostMapping("/{oid}/new-view-version")
    public ApiResponse<Void> newViewVersion(@PathVariable String oid) {
        try {
            engDocumentService.newViewVersion(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新建视图版本失败: " + e.getMessage());
        }
    }

    // ==================== 查询 ====================

    /**
     * 按 OID 查询（附加指定迭代或最新迭代的版本/生命周期/CAD 属性，以及分类 IBA 值）。
     *
     * <p>读取编排已收敛到 {@code EngineeringDocumentInstanceCreator#get}：本端点与统一入口
     * {@code GET /api/softtype-instances/{oid}?typeDefinitionCode=FOOTPRINT} 共用同一实现，
     * 避免「封装/图符列表点详情」与「统一入口读详情」出现字段差异。
     */
    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> getByOid(@PathVariable String oid,
                                                     @RequestParam(required = false) String iterationOid) {
        Map<String, Object> params = new HashMap<>();
        if (iterationOid != null) {
            params.put("iterationOid", iterationOid);
        }
        Object entity = softTypeInstanceService.getForHost(HOST, oid, params);
        if (entity == null) {
            return ApiResponse.fail(404, "工程数据不存在: " + oid);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) entity;
        return ApiResponse.ok(result);
    }

    /** 查询工程数据列表（支持 containerOid / stageOid / folderOid 过滤） */
    @GetMapping
    public ApiResponse<List<EngineeringDocument>> list(
            @RequestParam(required = false) String containerOid,
            @RequestParam(required = false) String stageOid,
            @RequestParam(required = false) String folderOid) {
        try {
            List<EngineeringDocument> docs;
            if (folderOid != null) {
                docs = engDocumentService.findByFolder(folderOid);
            } else if (containerOid != null && stageOid != null) {
                docs = engDocumentService.findByContainerAndStage(containerOid, stageOid);
            } else if (containerOid != null) {
                docs = engDocumentService.findByContainerOid(containerOid);
            } else {
                return ApiResponse.fail(400, "请提供 containerOid 或 folderOid 参数");
            }
            return ApiResponse.ok(docs);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询工程数据失败: " + e.getMessage());
        }
    }

    /** 查询文件夹下的工程数据 VO（含迭代、生命周期、类型名与 CAD 属性），用于列表展示 */
    @GetMapping("/folder-details")
    public ApiResponse<List<EngineeringDocumentVO>> listFolderDetails(@RequestParam String folderOid) {
        try {
            return ApiResponse.ok(engDocumentService.findVOsByFolder(folderOid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询工程数据详情失败: " + e.getMessage());
        }
    }

    /** 查询历史版本（所有子版本），附加主对象 clsOid、实体 IBA 与迭代级分类 IBA 属性值 */
    @GetMapping("/{oid}/iterations")
    public ApiResponse<List<Map<String, Object>>> iterations(@PathVariable String oid) {
        List<EngineeringDocumentIteration> iterations = engDocumentService.findIterationsByMaster(oid);
        EngineeringDocument master = engDocumentService.findByOid(oid);
        String clsOid = master != null ? master.getClsOid() : null;
        Map<String, Object> entityIba = ibaDataService.getValues(IBA_ENTITY_TYPE, oid);

        List<Map<String, Object>> result = new ArrayList<>();
        for (EngineeringDocumentIteration iter : iterations) {
            Map<String, Object> map = objectMapper.convertValue(iter,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Map<String, Object> clsIba = clsOid != null
                    ? clsIbaDataService.getValues(iter.getOid(), clsOid) : null;
            map.put("clsOid", clsOid);
            map.put("entityIba", entityIba);
            map.put("clsIba", clsIba);
            result.add(map);
        }
        return ApiResponse.ok(result);
    }
}
