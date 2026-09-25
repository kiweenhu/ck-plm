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
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
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
    private final SoftTypeInstanceService softTypeInstanceService;

    public PartController(PartService partService, IBADataService ibaDataService,
                          ClsIbaDataService clsIbaDataService,
                          IbaDataSupport ibaDataSupport,
                          ClsIbaDataSupport clsIbaDataSupport,
                          ObjectMapper objectMapper,
                          SoftTypeInstanceService softTypeInstanceService) {
        this.partService = partService;
        this.ibaDataService = ibaDataService;
        this.clsIbaDataService = clsIbaDataService;
        this.ibaDataSupport = ibaDataSupport;
        this.clsIbaDataSupport = clsIbaDataSupport;
        this.objectMapper = objectMapper;
        this.softTypeInstanceService = softTypeInstanceService;
    }

    /**
     * 创建零组件。
     *
     * <p>创建编排（实体落库 + 分类 IBA + 实体 IBA）已收敛到 {@code PartInstanceCreator}，
     * 本方法通过 {@code createForHost("PART", …)} 复用同一实现，不再重复编排 ——
     * 避免「统一入口」与「实体端点」两处逻辑随迭代分叉。
     *
     * <p>宿主强校验：{@code /parts} 只创建 PART 宿主对象。若请求中的类型宿主不是 PART
     * （例如误把封装 FOOTPRINT 提交到本端点），将显式返回 400，而非静默写入 ck_part。
     */
    @PostMapping
    public ApiResponse<Part> create(@RequestBody Map<String, Object> body) {
        try {
            String typeCode = ibaDataSupport.getString(body, "typeDefinitionCode");
            SoftTypeInstanceResult result = softTypeInstanceService.createForHost(
                    IBA_ENTITY_TYPE, typeCode != null ? typeCode : IBA_ENTITY_TYPE, body);
            return ApiResponse.ok((Part) result.getEntity());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建零组件失败: " + e.getMessage());
        }
    }

    /**
     * 更新零组件。
     *
     * <p>更新编排已收敛到 {@code PartServiceImpl#updateInstance}（能力宿主策略）：
     * 本端点与统一入口 {@code PUT /api/softtype-instances/{oid}} 共用同一实现，不会分叉。
     */
    @PutMapping("/{oid}")
    public ApiResponse<Part> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            Object entity = softTypeInstanceService.updateForHost(IBA_ENTITY_TYPE, oid, body);
            return ApiResponse.ok((Part) entity);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
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
            // 分类节点（元器件库/标准件库/通用件库按分类归档）；未传 = 不涉及分类
            String clsOid = ibaDataSupport.getString(body, "clsOid");
            if (containerOid == null || containerType == null) {
                return ApiResponse.fail(400, "产品系列/型号/资源库不能为空");
            }
            Part moved = partService.move(oid, containerOid, containerType, folderOid, stageOid, clsOid);
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

    /**
     * 按 OID 查询（附加最新迭代或指定迭代的版本/unit/source/视图/生命周期及分类 IBA 值）。
     *
     * <p>读取编排已收敛到 {@code PartInstanceCreator#get}：本端点与统一入口
     * {@code GET /api/softtype-instances/{oid}?typeDefinitionCode=…} 共用同一实现，
     * 避免「原生端点」与「统一入口」两处读取逻辑随迭代分叉。
     */
    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> getByOid(@PathVariable String oid,
                                                      @RequestParam(required = false) String iterationOid) {
        Map<String, Object> params = new HashMap<>();
        if (iterationOid != null) {
            params.put("iterationOid", iterationOid);
        }
        Object entity = softTypeInstanceService.getForHost(IBA_ENTITY_TYPE, oid, params);
        if (entity == null) {
            return ApiResponse.fail(404, "零组件不存在: " + oid);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) entity;
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

    /** 按容器查询（支持名称/编码关键字模糊过滤） */
    @GetMapping("/by-container")
    public ApiResponse<List<Part>> listByContainer(@RequestParam String containerOid,
                                                    @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(partService.findByContainerAndKeyword(containerOid, keyword.trim()));
        }
        return ApiResponse.ok(partService.findByContainerOid(containerOid));
    }
}
