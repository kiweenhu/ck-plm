/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.cls.service.api.ClsIbaDataService;
import cn.ck.plm.cls.service.impl.ClsIbaDataSupport;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.dto.PartVO;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.service.api.PartService;
import cn.ck.plm.softtype.service.api.IBADataService;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Part 零组件 REST API 控制器。
 */
@RestController
@RequestMapping("/api/parts")
public class PartController {

    private static final String IBA_ENTITY_TYPE = "PART";

    private final PartService partService;
    private final IBADataService ibaDataService;
    private final ClsIbaDataService clsIbaDataService;
    private final IbaDataSupport ibaDataSupport;
    private final ClsIbaDataSupport clsIbaDataSupport;
    private final ObjectMapper objectMapper;

    public PartController(PartService partService, IBADataService ibaDataService,
                          ClsIbaDataService clsIbaDataService,
                          IbaDataSupport ibaDataSupport,
                          ClsIbaDataSupport clsIbaDataSupport,
                          ObjectMapper objectMapper) {
        this.partService = partService;
        this.ibaDataService = ibaDataService;
        this.clsIbaDataService = clsIbaDataService;
        this.ibaDataSupport = ibaDataSupport;
        this.clsIbaDataSupport = clsIbaDataSupport;
        this.objectMapper = objectMapper;
    }

    /** 创建零组件 */
    @PostMapping
    public ApiResponse<Part> create(@RequestBody Map<String, Object> body) {
        try {
            Part part = objectMapper.convertValue(body, Part.class);
            String ckfileOid = ibaDataSupport.getString(body, "ckfileOid");
            String attachmentOid = ibaDataSupport.getString(body, "attachmentOid");
            String unit = ibaDataSupport.getString(body, "unit");
            String source = ibaDataSupport.getString(body, "source");
            Part created = partService.create(part, ckfileOid, attachmentOid, unit, source);
            // 保存分类 IBA 属性值（迭代级，ck_cls_iba_data，entity_oid = 最新迭代 oid）
            PartIteration latestIter = partService.findLatestIteration(created.getOid());
            String iterOid = latestIter != null ? latestIter.getOid() : created.getOid();
            clsIbaDataSupport.saveClsIbaValues(iterOid, created.getClsOid(), body);
            // 保存实体 IBA 动态属性值（实体级，ck_type_iba_data）
            ibaDataSupport.saveIbaValues(IBA_ENTITY_TYPE, created.getOid(), body);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建零组件失败: " + e.getMessage());
        }
    }

    /** 更新零组件 */
    @PutMapping("/{oid}")
    public ApiResponse<Part> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            Part part = objectMapper.convertValue(body, Part.class);
            part.setOid(oid);
            Part updated = partService.update(part);
            // 更新最新迭代的 unit/source（迭代级字段）
            String unit = ibaDataSupport.getString(body, "unit");
            String source = ibaDataSupport.getString(body, "source");
            partService.updateLatestIterationAttributes(oid, unit, source);
            // 保存分类 IBA 属性值（迭代级，ck_cls_iba_data，entity_oid = 最新迭代 oid）
            PartIteration latestIter = partService.findLatestIteration(oid);
            String iterOid = latestIter != null ? latestIter.getOid() : oid;
            clsIbaDataSupport.saveClsIbaValues(iterOid, updated.getClsOid(), body);
            // 合并保存实体 IBA 动态属性值（保留未提交的字段）
            ibaDataSupport.mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新零组件失败: " + e.getMessage());
        }
    }

    /** 重命名零组件 */
    @PutMapping("/{oid}/rename")
    public ApiResponse<Part> rename(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            String name = ibaDataSupport.getString(body, "name");
            if (name == null || name.trim().isEmpty()) {
                return ApiResponse.fail(400, "名称不能为空");
            }
            Part renamed = partService.rename(oid, name.trim());
            return ApiResponse.ok(renamed);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "重命名失败: " + e.getMessage());
        }
    }

    /** 另存为：复制零组件为新对象 */
    @PostMapping("/{oid}/save-as")
    public ApiResponse<Part> saveAs(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            String name = ibaDataSupport.getString(body, "name");
            if (name == null || name.trim().isEmpty()) {
                return ApiResponse.fail(400, "名称不能为空");
            }
            Part source = partService.findByOid(oid);
            if (source == null) {
                return ApiResponse.fail(404, "零组件不存在: " + oid);
            }
            Part copy = partService.saveAs(oid, name.trim());

            // 复制实体 IBA 值（ck_type_iba_data）
            Map<String, Object> entityIba = ibaDataService.getValues(IBA_ENTITY_TYPE, oid);
            if (entityIba != null && !entityIba.isEmpty()) {
                ibaDataService.saveValues(IBA_ENTITY_TYPE, copy.getOid(), entityIba);
            }
            // 复制分类 IBA 值（ck_cls_iba_data，迭代级：从源最新迭代复制到新最新迭代）
            if (source.getClsOid() != null) {
                PartIteration srcIter = partService.findLatestIteration(oid);
                PartIteration newIter = partService.findLatestIteration(copy.getOid());
                if (srcIter != null && newIter != null) {
                    Map<String, Object> clsIba = clsIbaDataService.getValues(srcIter.getOid(), source.getClsOid());
                    if (clsIba != null && !clsIba.isEmpty()) {
                        clsIbaDataService.saveValues(newIter.getOid(), source.getClsOid(), clsIba);
                    }
                }
            }
            return ApiResponse.ok(copy);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "另存为失败: " + e.getMessage());
        }
    }

    /** 移动零组件到新的容器/阶段/文件夹位置 */
    @PutMapping("/{oid}/move")
    public ApiResponse<Part> move(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            String containerOid = ibaDataSupport.getString(body, "containerOid");
            String containerType = ibaDataSupport.getString(body, "containerType");
            String folderOid = ibaDataSupport.getString(body, "folderOid");
            String stageOid = ibaDataSupport.getString(body, "stageOid");
            if (containerOid == null || containerType == null) {
                return ApiResponse.fail(400, "产品系列/型号不能为空");
            }
            Part moved = partService.move(oid, containerOid, containerType, folderOid, stageOid);
            return ApiResponse.ok(moved);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "移动失败: " + e.getMessage());
        }
    }

    /** 删除零组件（含全部子版本） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        partService.delete(oid);
        return ApiResponse.ok();
    }

    /** 删除零组件的最新小版本（仅删除最新 iteration） */
    @DeleteMapping("/{oid}/latest-iteration")
    public ApiResponse<Void> deleteLatestIteration(@PathVariable String oid) {
        partService.deleteLatestIteration(oid);
        return ApiResponse.ok();
    }

    /** 新建视图版本（创建新的大版本 revision+1，iteration=1） */
    @PostMapping("/{oid}/new-view-version")
    public ApiResponse<Void> newViewVersion(@PathVariable String oid) {
        partService.newViewVersion(oid);
        return ApiResponse.ok();
    }

    /** 按 OID 查询（附加最新迭代的版本/unit/source/视图/生命周期及分类 IBA 值，便于详情展示与编辑回填） */
    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> getByOid(@PathVariable String oid) {
        Part part = partService.findByOid(oid);
        if (part == null) {
            return ApiResponse.fail(404, "零组件不存在: " + oid);
        }
        Map<String, Object> result = objectMapper.convertValue(part,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        PartIteration latest = partService.findLatestIteration(oid);
        if (latest != null) {
            result.put("revision", latest.getRevision());
            result.put("iteration", latest.getIteration());
            result.put("displayVersion", latest.getDisplayVersion());
            result.put("checkedOut", latest.isCheckedOut());
            result.put("checkedOutBy", latest.getCheckedOutBy());
            result.put("checkedOutComment", latest.getCheckedOutComment());
            result.put("unit", latest.getUnit());
            result.put("source", latest.getSource());
            result.put("view", latest.getView() != null ? latest.getView().getCode() : null);
            if (latest.getStatus() != null) {
                result.put("statusCode", latest.getStatus().getCode());
                result.put("statusName", latest.getStatus().getDisplayName());
            }
        }
        // 附加最新迭代的分类 IBA 属性值（entity_oid = 最新迭代 oid）
        if (part.getClsOid() != null && latest != null) {
            Map<String, Object> clsIba = clsIbaDataService.getValues(latest.getOid(), part.getClsOid());
            if (clsIba != null) {
                result.putAll(clsIba);
            }
        }
        return ApiResponse.ok(result);
    }

    /** 按文件夹查询 VO */
    @GetMapping("/by-folder")
    public ApiResponse<List<PartVO>> listByFolder(@RequestParam String folderOid) {
        return ApiResponse.ok(partService.findVOsByFolder(folderOid));
    }

    /** 查询零组件历史版本（所有子版本），附加主对象 clsOid、实体 IBA 属性值（ck_type_iba_data）与迭代级分类 IBA 属性值（ck_cls_iba_data，entity_oid = 迭代 oid） */
    @GetMapping("/{oid}/iterations")
    public ApiResponse<List<Map<String, Object>>> iterations(@PathVariable String oid) {
        List<PartIteration> iterations = partService.findIterationsByMaster(oid);
        Part master = partService.findByOid(oid);
        String clsOid = master != null ? master.getClsOid() : null;
        Map<String, Object> entityIba = ibaDataService.getValues(IBA_ENTITY_TYPE, oid);

        List<Map<String, Object>> result = new ArrayList<>();
        for (PartIteration iter : iterations) {
            Map<String, Object> map = objectMapper.convertValue(iter, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Map<String, Object> clsIba = clsOid != null ? clsIbaDataService.getValues(iter.getOid(), clsOid) : null;
            map.put("clsOid", clsOid);
            map.put("entityIba", entityIba);
            map.put("clsIba", clsIba);
            result.add(map);
        }
        return ApiResponse.ok(result);
    }

    /** 按容器查询 */
    @GetMapping("/by-container")
    public ApiResponse<List<Part>> listByContainer(@RequestParam String containerOid) {
        return ApiResponse.ok(partService.findByContainerOid(containerOid));
    }
}
