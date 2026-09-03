/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.controller;

import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.cls.service.impl.ClsIbaDataSupport;
import cn.ck.plm.document.dto.DocumentVO;
import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.entity.DocumentIteration;
import cn.ck.plm.document.service.api.DocumentService;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.service.api.IBADataService;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Document 文档 REST API 控制器。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final String IBA_ENTITY_TYPE = "DOCUMENT";

    private final DocumentService documentService;
    private final IBADataService ibaDataService;
    private final ClsIbaDataService clsIbaDataService;
    private final IbaDataSupport ibaDataSupport;
    private final ClsIbaDataSupport clsIbaDataSupport;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentService documentService, IBADataService ibaDataService,
                              ClsIbaDataService clsIbaDataService,
                              IbaDataSupport ibaDataSupport,
                              ClsIbaDataSupport clsIbaDataSupport,
                              ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.ibaDataService = ibaDataService;
        this.clsIbaDataService = clsIbaDataService;
        this.ibaDataSupport = ibaDataSupport;
        this.clsIbaDataSupport = clsIbaDataSupport;
        this.objectMapper = objectMapper;
    }

    /** 创建文档 */
    @PostMapping
    public ApiResponse<Document> create(@RequestBody Map<String, Object> body) {
        try {
            Document document = objectMapper.convertValue(body, Document.class);
            // 提取 CKFile.oid 和 CKAttachment.oid（不属于 Document 实体字段）
            String ckfileOid = ibaDataSupport.getString(body, "ckfileOid");
            String attachmentOid = ibaDataSupport.getString(body, "attachmentOid");
            Document created = documentService.create(document, ckfileOid, attachmentOid);
            // 保存分类 IBA 属性值（迭代级，ck_cls_iba_data，entity_oid = 最新迭代 oid）
            DocumentIteration latestIter = documentService.findLatestIteration(created.getOid());
            String iterOid = latestIter != null ? latestIter.getOid() : created.getOid();
            clsIbaDataSupport.saveClsIbaValues(iterOid, created.getClsOid(), body);
            // 保存实体 IBA 动态属性值（实体级，ck_type_iba_data）
            ibaDataSupport.saveIbaValues(IBA_ENTITY_TYPE, created.getOid(), body);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建文档失败: " + e.getMessage());
        }
    }

    /** 更新文档 */
    @PutMapping("/{oid}")
    public ApiResponse<Document> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            Document document = objectMapper.convertValue(body, Document.class);
            document.setOid(oid);
            Document updated = documentService.update(document);
            // 保存分类 IBA 属性值（迭代级，ck_cls_iba_data，entity_oid = 最新迭代 oid）
            DocumentIteration latestIter = documentService.findLatestIteration(oid);
            String iterOid = latestIter != null ? latestIter.getOid() : oid;
            clsIbaDataSupport.saveClsIbaValues(iterOid, updated.getClsOid(), body);
            // 合并保存实体 IBA 动态属性值（保留未提交的字段）
            ibaDataSupport.mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新文档失败: " + e.getMessage());
        }
    }

    /** 重命名文档 */
    @PutMapping("/{oid}/rename")
    public ApiResponse<Document> rename(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            String name = body.get("name") != null ? body.get("name").toString() : null;
            if (name == null || name.trim().isEmpty()) {
                return ApiResponse.fail(400, "名称不能为空");
            }
            Document renamed = documentService.rename(oid, name.trim());
            return ApiResponse.ok(renamed);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "重命名失败: " + e.getMessage());
        }
    }

    /** 移动文档到新的容器/阶段/文件夹位置 */
    @PutMapping("/{oid}/move")
    public ApiResponse<Document> move(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            String containerOid = body.get("containerOid") != null ? body.get("containerOid").toString() : null;
            String containerType = body.get("containerType") != null ? body.get("containerType").toString() : null;
            String folderOid = body.get("folderOid") != null ? body.get("folderOid").toString() : null;
            String stageOid = body.get("stageOid") != null ? body.get("stageOid").toString() : null;
            if (containerOid == null || containerOid.trim().isEmpty() || containerType == null) {
                return ApiResponse.fail(400, "产品系列/型号不能为空");
            }
            Document moved = documentService.move(oid, containerOid, containerType, folderOid, stageOid);
            return ApiResponse.ok(moved);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "移动失败: " + e.getMessage());
        }
    }

    /** 删除文档（含全部子版本） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            documentService.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除文档失败: " + e.getMessage());
        }
    }

    /** 删除文档的最新小版本（仅删除最新 iteration） */
    @DeleteMapping("/{oid}/latest-iteration")
    public ApiResponse<Void> deleteLatestIteration(@PathVariable String oid) {
        try {
            documentService.deleteLatestIteration(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除最新小版本失败: " + e.getMessage());
        }
    }

    /** 新建视图版本（创建新的大版本 revision+1，iteration=1） */
    @PostMapping("/{oid}/new-view-version")
    public ApiResponse<Void> newViewVersion(@PathVariable String oid) {
        try {
            documentService.newViewVersion(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新建视图版本失败: " + e.getMessage());
        }
    }

    /** 查询单个文档（附加对象实例的分类 IBA 值，便于编辑回填） */
    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> getByOid(@PathVariable String oid) {
        Document doc = documentService.findByOid(oid);
        if (doc == null) {
            return ApiResponse.fail(404, "文档不存在: " + oid);
        }
        Map<String, Object> result = objectMapper.convertValue(doc,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        // 附加最新迭代的分类 IBA 属性值（entity_oid = 最新迭代 oid）
        if (doc.getClsOid() != null) {
            DocumentIteration latest = documentService.findLatestIteration(oid);
            if (latest != null) {
                Map<String, Object> clsIba = clsIbaDataService.getValues(latest.getOid(), doc.getClsOid());
                if (clsIba != null) {
                    result.putAll(clsIba);
                }
            }
        }
        return ApiResponse.ok(result);
    }

    /** 查询文档列表（支持 containerOid / stageOid / folderOid 过滤） */
    @GetMapping
    public ApiResponse<List<Document>> list(
            @RequestParam(required = false) String containerOid,
            @RequestParam(required = false) String stageOid,
            @RequestParam(required = false) String folderOid) {
        try {
            List<Document> docs;
            if (folderOid != null) {
                docs = documentService.findByFolder(folderOid);
            } else if (containerOid != null && stageOid != null) {
                docs = documentService.findByContainerAndStage(containerOid, stageOid);
            } else if (containerOid != null) {
                docs = documentService.findByContainerOid(containerOid);
            } else {
                return ApiResponse.fail(400, "请提供 containerOid 或 folderOid 参数");
            }
            return ApiResponse.ok(docs);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询文档失败: " + e.getMessage());
        }
    }

    /** 查询文件夹下的文档（含迭代、生命周期、类型名等详情），用于阶段页面 DataTable 展示 */
    @GetMapping("/folder-details")
    public ApiResponse<List<DocumentVO>> listFolderDetails(@RequestParam String folderOid) {
        try {
            List<DocumentVO> vos = documentService.findVOsByFolder(folderOid);
            return ApiResponse.ok(vos);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询文档详情失败: " + e.getMessage());
        }
    }

    /** 查询文档历史版本（所有子版本），附加主对象 clsOid、实体 IBA 属性值（ck_type_iba_data）与迭代级分类 IBA 属性值（ck_cls_iba_data，entity_oid = 迭代 oid） */
    @GetMapping("/{oid}/iterations")
    public ApiResponse<List<Map<String, Object>>> iterations(@PathVariable String oid) {
        List<DocumentIteration> iterations = documentService.findIterationsByMaster(oid);
        Document master = documentService.findByOid(oid);
        String clsOid = master != null ? master.getClsOid() : null;
        Map<String, Object> entityIba = ibaDataService.getValues(IBA_ENTITY_TYPE, oid);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentIteration iter : iterations) {
            Map<String, Object> map = objectMapper.convertValue(iter, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Map<String, Object> clsIba = clsOid != null ? clsIbaDataService.getValues(iter.getOid(), clsOid) : null;
            map.put("clsOid", clsOid);
            map.put("entityIba", entityIba);
            map.put("clsIba", clsIba);
            result.add(map);
        }
        return ApiResponse.ok(result);
    }
}
