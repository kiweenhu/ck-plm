/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.ecad.entity.EcadProject;
import cn.ck.plm.ecad.entity.EcadSchematic;
import cn.ck.plm.ecad.mapper.EcadProjectMapper;
import cn.ck.plm.ecad.service.api.EcadSchematicService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 电子设计项目 REST 接口（Cadence 集成读取面）。
 *
 * <p>项目的创建/编辑走软类型体系（SoftTypeInstanceCapability.ECAD_PROJECT），
 * 本控制器只暴露桥接 CLI / 插件需要的读取接口。
 */
@RestController
@RequestMapping("/api/ecad/projects")
public class EcadProjectController {

    private static final Logger log = LoggerFactory.getLogger(EcadProjectController.class);

    private final EcadProjectMapper ecadProjectMapper;
    private final EcadSchematicService schematicService;

    public EcadProjectController(EcadProjectMapper ecadProjectMapper,
                                 EcadSchematicService schematicService) {
        this.ecadProjectMapper = ecadProjectMapper;
        this.schematicService = schematicService;
    }

    /** 项目列表（按域过滤可选） */
    @GetMapping
    public ApiResponse<List<EcadProject>> list(@RequestParam(required = false) String domainOid) {
        try {
            List<EcadProject> projects = domainOid != null && !domainOid.isBlank()
                    ? ecadProjectMapper.selectByDomainOid(domainOid)
                    : List.of();
            return ApiResponse.ok(projects);
        } catch (Exception e) {
            log.error("查询电子设计项目失败", e);
            return ApiResponse.fail(500, "查询电子设计项目失败: " + e.getMessage());
        }
    }

    /** 按编码查询（桥接 CLI / 插件的入口查找） */
    @GetMapping("/by-code/{code}")
    public ApiResponse<EcadProject> getByCode(@PathVariable String code) {
        try {
            EcadProject project = ecadProjectMapper.selectByCode(code);
            if (project == null) {
                return ApiResponse.fail(404, "电子设计项目不存在: " + code);
            }
            return ApiResponse.ok(project);
        } catch (Exception e) {
            log.error("查询电子设计项目失败: {}", code, e);
            return ApiResponse.fail(500, "查询电子设计项目失败: " + e.getMessage());
        }
    }

    /** 项目详情 */
    @GetMapping("/{oid}")
    public ApiResponse<EcadProject> get(@PathVariable String oid) {
        try {
            EcadProject project = ecadProjectMapper.selectByOid(oid);
            if (project == null) {
                return ApiResponse.fail(404, "电子设计项目不存在: " + oid);
            }
            return ApiResponse.ok(project);
        } catch (Exception e) {
            log.error("查询电子设计项目失败: {}", oid, e);
            return ApiResponse.fail(500, "查询电子设计项目失败: " + e.getMessage());
        }
    }

    /** 项目下的原理图列表 */
    @GetMapping("/{oid}/schematics")
    public ApiResponse<List<EcadSchematic>> schematics(@PathVariable String oid) {
        try {
            return ApiResponse.ok(schematicService.listByProjectOid(oid));
        } catch (Exception e) {
            log.error("查询项目原理图失败: {}", oid, e);
            return ApiResponse.fail(500, "查询项目原理图失败: " + e.getMessage());
        }
    }
}
