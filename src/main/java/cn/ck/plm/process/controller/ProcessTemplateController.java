/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.dto.ProcessTemplateDeployResult;
import cn.ck.plm.process.dto.ProcessVersionDeleteResult;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.entity.ProcessTemplateVersion;
import cn.ck.plm.process.service.api.ProcessTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程模板 API —— 前端流程设计器的后端入口（spec §2.3）。
 *
 * <pre>
 * GET    /api/plm/process-templates                   列表（keyword / categoryOid / enabled）
 * POST   /api/plm/process-templates                   新建（body: key,name,…,dslJson）
 * GET    /api/plm/process-templates/{oid}             详情（含最新 DSL）
 * PUT    /api/plm/process-templates/{oid}             保存（body: { dslJson, changeNote }）→ 生成新版本
 * POST   /api/plm/process-templates/{oid}/versions/delete  按版本删除（body: { versions: [1,3] }）
 * POST   /api/plm/process-templates/{oid}/copy        复制/另存（body: { key, name }）
 * POST   /api/plm/process-templates/{oid}/category    移动到分组（body: { category }）
 * POST   /api/plm/process-templates/{oid}/enable      允许部署（原「启用」）
 * POST   /api/plm/process-templates/{oid}/disable     停止部署（原「禁用」）
 * GET    /api/plm/process-templates/{oid}/versions    版本历史
 * GET    /api/plm/process-templates/{oid}/versions/{v} 指定版本（含 DSL 与部署快照）
 * POST   /api/plm/process-templates/{oid}/deploy      部署（body: { version?, bpmnXml }）
 * </pre>
 *
 * <p>错误码约定：入参问题 → 400；状态问题（如已停止部署仍要发布）→ 409；部署被引擎拒绝 → 409。
 */
@RestController
@RequestMapping("/api/plm/process-templates")
public class ProcessTemplateController {

    private final ProcessTemplateService service;

    public ProcessTemplateController(ProcessTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProcessTemplate>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryOid,
            @RequestParam(required = false) Boolean enabled) {
        try {
            return ApiResponse.ok(service.list(keyword, categoryOid, enabled));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @GetMapping("/{oid}")
    public ApiResponse<ProcessTemplate> get(@PathVariable String oid) {
        try {
            return ApiResponse.ok(service.get(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<ProcessTemplate> create(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.create(toTemplate(body), str(body, "dslJson")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新建流程模板失败: " + e.getMessage());
        }
    }

    /** 保存 → 生成新版本 */
    @PutMapping("/{oid}")
    public ApiResponse<ProcessTemplate> save(@PathVariable String oid,
                                             @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.save(oid, str(body, "dslJson"), str(body, "changeNote")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "保存流程模板失败: " + e.getMessage());
        }
    }

    /**
     * 按版本删除 —— <b>流程删除的唯一方式</b>。
     *
     * <pre>{ "versions": [2, 3] }</pre>
     *
     * <p>只允许删未部署的版本（已部署的整批拒绝）；删完最后一个版本 → 该流程整体消失。
     * 刻意<b>不提供"删除模板"接口</b>：删除必须按版本进行，"哪些能删"由服务层统一把关。
     */
    @PostMapping("/{oid}/versions/delete")
    public ApiResponse<ProcessVersionDeleteResult> deleteVersions(@PathVariable String oid,
                                                                  @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.deleteVersions(oid, intList(body, "versions")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            // 状态问题（选中的版本里有已部署的）→ 409，与部署被拒同一口径
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除流程版本失败: " + e.getMessage());
        }
    }

    @PostMapping("/{oid}/copy")
    public ApiResponse<ProcessTemplate> copy(@PathVariable String oid,
                                             @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.copy(oid, str(body, "key"), str(body, "name")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "复制流程模板失败: " + e.getMessage());
        }
    }

    /** 允许部署（原「启用」）：只是打开发布闸门，不影响已部署定义与在途实例 */
    @PostMapping("/{oid}/enable")
    public ApiResponse<ProcessTemplate> enable(@PathVariable String oid) {
        return toggle(oid, true);
    }

    /**
     * 移动到指定分组（body: { categoryOid }）。
     *
     * <p>分类的<b>唯一修改入口</b>：设计器里不提供分类字段（避免"设计器改了、保存不生效"
     * 这类两处真相），换组在清单页完成。入参是分组 <b>oid</b>（引用字典，不是分组名）。
     */
    @PostMapping("/{oid}/category")
    public ApiResponse<ProcessTemplate> moveCategory(@PathVariable String oid,
                                                     @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.moveToCategory(oid, str(body, "categoryOid")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "移动分组失败: " + e.getMessage());
        }
    }

    /** 停止部署（原「禁用」）：关掉发布闸门；已部署定义照旧运行，也不阻止按 key 发起 */
    @PostMapping("/{oid}/disable")
    public ApiResponse<ProcessTemplate> disable(@PathVariable String oid) {
        return toggle(oid, false);
    }

    @GetMapping("/{oid}/versions")
    public ApiResponse<List<ProcessTemplateVersion>> versions(@PathVariable String oid) {
        try {
            return ApiResponse.ok(service.versions(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    @GetMapping("/{oid}/versions/{version}")
    public ApiResponse<ProcessTemplateVersion> version(@PathVariable String oid,
                                                       @PathVariable Integer version) {
        try {
            return ApiResponse.ok(service.version(oid, version));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    /**
     * 部署：body 为前端 dsl-core → bpmn-compiler 编译出的 BPMN 2.0 XML。
     *
     * <pre>{ "version": 3, "bpmnXml": "&lt;?xml …&gt;" }</pre>
     */
    @PostMapping("/{oid}/deploy")
    public ApiResponse<ProcessTemplateDeployResult> deploy(@PathVariable String oid,
                                                           @RequestBody Map<String, Object> body) {
        try {
            Integer version = intOrNull(body, "version");
            return ApiResponse.ok(service.deploy(oid, version, str(body, "bpmnXml")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "部署流程失败: " + e.getMessage());
        }
    }

    // ==================== 私有工具 ====================

    private ApiResponse<ProcessTemplate> toggle(String oid, boolean enabled) {
        try {
            return ApiResponse.ok(service.setEnabled(oid, enabled));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新部署开关失败: " + e.getMessage());
        }
    }

    private static ProcessTemplate toTemplate(Map<String, Object> body) {
        ProcessTemplate template = new ProcessTemplate();
        template.setKey(str(body, "key"));
        template.setName(str(body, "name"));
        template.setDisplayName(str(body, "displayName"));
        template.setCategoryOid(str(body, "categoryOid"));
        template.setDescription(str(body, "description"));
        return template;
    }

    /** body 里的版本号数组（`"versions": [1,3]`）；非数组或含非整数即报错 */
    private static List<Integer> intList(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (!(value instanceof List)) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item == null) {
                continue;
            }
            try {
                result.add(Integer.valueOf(item.toString().trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " 必须是整数数组");
            }
        }
        return result;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value != null ? value.toString() : null;
    }

    private static Integer intOrNull(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
    }
}
