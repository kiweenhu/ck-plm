/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.controller;

import cn.ck.plm.document.dto.DocumentVO;
import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.service.api.DocumentService;
import cn.ck.plm.iam.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Document 文档 REST API 控制器。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentService documentService, ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    /** 创建文档 */
    @PostMapping
    public ApiResponse<Document> create(@RequestBody Map<String, Object> body) {
        try {
            Document document = objectMapper.convertValue(body, Document.class);
            // 提取 CKFile.oid 和 CKAttachment.oid（不属于 Document 实体字段）
            String ckfileOid = getString(body, "ckfileOid");
            String attachmentOid = getString(body, "attachmentOid");
            Document created = documentService.create(document, ckfileOid, attachmentOid);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建文档失败: " + e.getMessage());
        }
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null && !"".equals(val) ? val.toString() : null;
    }

    /** 更新文档 */
    @PutMapping("/{oid}")
    public ApiResponse<Document> update(@PathVariable String oid, @RequestBody Document document) {
        try {
            document.setOid(oid);
            Document updated = documentService.update(document);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新文档失败: " + e.getMessage());
        }
    }

    /** 删除文档 */
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

    /** 查询单个文档 */
    @GetMapping("/{oid}")
    public ApiResponse<Document> getByOid(@PathVariable String oid) {
        Document doc = documentService.findByOid(oid);
        if (doc == null) {
            return ApiResponse.fail(404, "文档不存在: " + oid);
        }
        return ApiResponse.ok(doc);
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
}
