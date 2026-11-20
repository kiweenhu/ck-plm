/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.ecad.dto.BomSyncItem;
import cn.ck.plm.ecad.dto.BomSyncResult;
import cn.ck.plm.ecad.entity.EcadDesignInstance;
import cn.ck.plm.ecad.entity.EcadSchematic;
import cn.ck.plm.ecad.entity.EcadSchematicIteration;
import cn.ck.plm.ecad.service.api.EcadSchematicService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * ECAD 原理图 REST 接口（Cadence 集成主链路）。
 *
 * <h3>桥接 CLI / Capture 插件的调用序列</h3>
 * <pre>
 * 1. POST /api/auth/login                    拿 token
 * 2. POST /api/ecad/schematics?projectId=... 创建原理图对象（或用已有对象）
 * 3. POST /{oid}/checkout                    检出
 * 4. POST /{oid}/dsn                         上传 .DSN（可选 sheetCount/componentCount）
 * 5. POST /{oid}/bom-sync                    上传 BOM 行，返回差异
 * 6. POST /{oid}/checkin                     检入
 * </pre>
 */
@RestController
@RequestMapping("/api/ecad/schematics")
public class EcadSchematicController {

    private static final Logger log = LoggerFactory.getLogger(EcadSchematicController.class);

    private final EcadSchematicService schematicService;

    public EcadSchematicController(EcadSchematicService schematicService) {
        this.schematicService = schematicService;
    }

    /** 创建原理图对象（挂在设计项目下；number 为空自动生成） */
    @PostMapping
    public ApiResponse<EcadSchematic> create(@RequestBody EcadSchematic schematic,
                                             @RequestParam(required = false) String projectId) {
        try {
            if (projectId != null && !projectId.isBlank()) {
                schematic.setProjectOid(projectId);
            }
            return ApiResponse.ok(schematicService.create(schematic, UserContext.get()));
        } catch (Exception e) {
            log.error("创建原理图失败", e);
            return ApiResponse.fail(500, "创建原理图失败: " + e.getMessage());
        }
    }

    /** 原理图详情 */
    @GetMapping("/{oid}")
    public ApiResponse<EcadSchematic> get(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.findByOid(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("查询原理图失败: {}", oid, e);
            return ApiResponse.fail(500, "查询原理图失败: " + e.getMessage());
        }
    }

    /** 按编号查询（插件入口） */
    @GetMapping("/by-number/{number}")
    public ApiResponse<EcadSchematic> getByNumber(@PathVariable String number) {
        try {
            EcadSchematic found = schematicService.findByNumber(number);
            if (found == null) {
                return ApiResponse.fail(404, "原理图不存在: " + number);
            }
            return ApiResponse.ok(found);
        } catch (Exception e) {
            log.error("查询原理图失败: {}", number, e);
            return ApiResponse.fail(500, "查询原理图失败: " + e.getMessage());
        }
    }

    /** 最新迭代 */
    @GetMapping("/{oid}/iterations/latest")
    public ApiResponse<EcadSchematicIteration> latestIteration(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.latestIteration(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("查询最新迭代失败: {}", oid, e);
            return ApiResponse.fail(500, "查询最新迭代失败: " + e.getMessage());
        }
    }

    /** 版本历史 */
    @GetMapping("/{oid}/iterations")
    public ApiResponse<List<EcadSchematicIteration>> iterations(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.listIterations(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("查询版本历史失败: {}", oid, e);
            return ApiResponse.fail(500, "查询版本历史失败: " + e.getMessage());
        }
    }

    /** 检出 */
    @PostMapping("/{oid}/checkout")
    public ApiResponse<EcadSchematicIteration> checkout(@PathVariable String oid,
                                                        @RequestBody(required = false) CheckoutBody body) {
        try {
            String comment = body != null ? body.comment : null;
            return ApiResponse.ok(schematicService.checkout(oid, comment, UserContext.get()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            log.error("检出原理图失败: {}", oid, e);
            return ApiResponse.fail(500, "检出原理图失败: " + e.getMessage());
        }
    }

    /** 检入 */
    @PostMapping("/{oid}/checkin")
    public ApiResponse<EcadSchematicIteration> checkin(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.checkin(oid, UserContext.get()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            log.error("检入原理图失败: {}", oid, e);
            return ApiResponse.fail(500, "检入原理图失败: " + e.getMessage());
        }
    }

    /** 取消检出 */
    @PostMapping("/{oid}/undo-checkout")
    public ApiResponse<Void> undoCheckout(@PathVariable String oid) {
        try {
            schematicService.undoCheckout(oid, UserContext.get());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            log.error("取消检出失败: {}", oid, e);
            return ApiResponse.fail(500, "取消检出失败: " + e.getMessage());
        }
    }

    /** 新建大版本 */
    @PostMapping("/{oid}/revisions")
    public ApiResponse<EcadSchematicIteration> newRevision(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.newRevision(oid, UserContext.get()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            log.error("新建大版本失败: {}", oid, e);
            return ApiResponse.fail(500, "新建大版本失败: " + e.getMessage());
        }
    }

    /** 上传 .DSN 设计文件（须已检出） */
    @PostMapping("/{oid}/dsn")
    public ApiResponse<EcadSchematicIteration> uploadDsn(@PathVariable String oid,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) Integer sheetCount,
                                                         @RequestParam(required = false) Integer componentCount) {
        try {
            return ApiResponse.ok(schematicService.uploadDsn(oid, file, sheetCount, componentCount,
                    UserContext.get()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (IOException e) {
            log.error("上传 .DSN 失败: {}", oid, e);
            return ApiResponse.fail(500, "上传 .DSN 失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("上传 .DSN 失败: {}", oid, e);
            return ApiResponse.fail(500, "上传 .DSN 失败: " + e.getMessage());
        }
    }

    /** BOM 同步（Capture BOM 行 → 设计实例，返回差异） */
    @PostMapping("/{oid}/bom-sync")
    public ApiResponse<BomSyncResult> bomSync(@PathVariable String oid,
                                              @RequestBody List<BomSyncItem> items) {
        try {
            return ApiResponse.ok(schematicService.syncBom(oid, items, UserContext.get()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("BOM 同步失败: {}", oid, e);
            return ApiResponse.fail(500, "BOM 同步失败: " + e.getMessage());
        }
    }

    /** 设计实例（当前同步状态） */
    @GetMapping("/{oid}/instances")
    public ApiResponse<List<EcadDesignInstance>> instances(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.listInstances(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("查询设计实例失败: {}", oid, e);
            return ApiResponse.fail(500, "查询设计实例失败: " + e.getMessage());
        }
    }

    /** 检出请求体 */
    public static class CheckoutBody {
        private String comment;

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
