/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.dto.ProcessStartConflictVO;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.softtype.dto.LifecycleStateOptionsVO;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 软类型实例统一入口 —— 创建 / 读取 / 更新 / 重命名 / 移动 / 另存为 / 删除 / 迭代 等全操作。
 *
 * <p>调用方<b>只需给出 {@code typeDefinitionCode}</b>，由后端依据
 * {@code type_definition.root_type_code} 路由到宿主自己的能力策略，
 * 取代此前「前端 {@code ENTITY_API_PATH} 硬编码端点」的双真相来源做法。
 *
 * <pre>
 * POST   /api/softtype-instances                       创建（body 含 typeDefinitionCode）
 * GET    /api/softtype-instances/{oid}?typeDefinitionCode=…   详情（可带 iterationOid）
 * PUT    /api/softtype-instances/{oid}                 更新（body 含 typeDefinitionCode）
 * PUT    /api/softtype-instances/{oid}/rename          重命名（body: { typeDefinitionCode, name })
 * PUT    /api/softtype-instances/{oid}/move            移动（容器/阶段/文件夹）
 * POST   /api/softtype-instances/{oid}/save-as         另存为
 * DELETE /api/softtype-instances/{oid}                 删除（含全部子版本）
 * DELETE /api/softtype-instances/{oid}/latest-iteration 删除最新小版本
 * POST   /api/softtype-instances/{oid}/new-view-version 新建视图版本
 * GET    /api/softtype-instances/{oid}/iterations      历史版本
 * GET    /api/softtype-instances/{oid}/lifecycle-states 可设置的生命周期状态候选（含可达性）
 * POST   /api/softtype-instances/{oid}/lifecycle-state  设置状态（INITIAL 退回初始 / SPECIFIED 指定）
 *
 * GET    /api/softtype-instances/capabilities?typeDefinitionCode=…  支持的操作集合（前端菜单依据）
 * GET    /api/softtype-instances/host?typeDefinitionCode=…          路由诊断
 * GET    /api/softtype-instances/hosts                              已注册宿主
 * </pre>
 *
 * <p>路由失败（类型不存在 / 域锚点 / 宿主未实现该能力）返回明确的错误码与原因，
 * 不会静默落到错误的表；宿主不支持的操作返回 <b>501</b>。
 */
@RestController
@RequestMapping("/api/softtype-instances")
public class SoftTypeInstanceController {

    private static final Logger log = LoggerFactory.getLogger(SoftTypeInstanceController.class);

    private final SoftTypeInstanceService softTypeInstanceService;
    /** 「设置生命周期状态」的状态候选（依据类型绑定的生命周期模板） */
    private final TypeLifecycleStateService typeLifecycleStateService;
    /**
     * 流程实例 ↔ 业务实体关联 —— 只为一道闸门：对象正在流程中时不允许手工改状态。
     *
     * <p>放在入口控制器而不是软类型服务里：本服务是共享框架，不该依赖流程模块；
     * 而"对象是否在流程中"是流程侧的事实（运行时判定），由入口按需查询最自然。
     */
    private final ProcessEntitySetService entitySetService;
    private final ObjectMapper objectMapper;

    public SoftTypeInstanceController(SoftTypeInstanceService softTypeInstanceService,
                                      TypeLifecycleStateService typeLifecycleStateService,
                                      ProcessEntitySetService entitySetService,
                                      ObjectMapper objectMapper) {
        this.softTypeInstanceService = softTypeInstanceService;
        this.typeLifecycleStateService = typeLifecycleStateService;
        this.entitySetService = entitySetService;
        this.objectMapper = objectMapper;
    }

    // ==================== 创建 / 读取 ====================

    /** 创建实例（统一入口，按 root_type_code 自动分发） */
    @PostMapping
    public ApiResponse<SoftTypeInstanceResult> create(@RequestBody Map<String, Object> body) {
        try {
            SoftTypeInstanceResult result =
                    softTypeInstanceService.create(typeCode(body), body);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建失败: " + e.getMessage());
        }
    }

    /**
     * 读取实例详情。
     *
     * @param params 其余查询参数（如 {@code iterationOid}），原样透传给能力策略
     */
    @GetMapping("/{oid}")
    public ApiResponse<Object> get(@PathVariable String oid,
                                   @RequestParam(required = false) String typeDefinitionCode,
                                   @RequestParam(required = false) Map<String, String> params) {
        return exec(() -> {
            Object entity = softTypeInstanceService.get(typeDefinitionCode, oid, stringParams(params));
            if (entity == null) {
                return ApiResponse.fail(404, "实例不存在: " + oid);
            }
            return ApiResponse.ok(entity);
        }, "读取失败");
    }

    // ==================== 写操作 ====================

    /** 更新实例 */
    @PutMapping("/{oid}")
    public ApiResponse<Object> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        return execResult(() -> softTypeInstanceService.update(typeCode(body), oid, body), "更新失败");
    }

    /** 重命名（body: { typeDefinitionCode, name }） */
    @PutMapping("/{oid}/rename")
    public ApiResponse<Object> rename(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        String name = body != null && body.get("name") != null ? body.get("name").toString() : null;
        return execResult(() -> softTypeInstanceService.rename(typeCode(body), oid, name), "重命名失败");
    }

    /** 移动（body: { typeDefinitionCode, containerOid, containerType, folderOid, stageOid }） */
    @PutMapping("/{oid}/move")
    public ApiResponse<Object> move(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        return execResult(() -> softTypeInstanceService.move(typeCode(body), oid, body), "移动失败");
    }

    /** 另存为（body: { typeDefinitionCode, name }） */
    @PostMapping("/{oid}/save-as")
    public ApiResponse<Object> saveAs(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        return execResult(() -> softTypeInstanceService.saveAs(typeCode(body), oid, body), "另存为失败");
    }

    /** 新建视图版本（revision+1，iteration=1） */
    @PostMapping("/{oid}/new-view-version")
    public ApiResponse<Void> newViewVersion(@PathVariable String oid,
                                           @RequestBody(required = false) Map<String, Object> body) {
        return execVoid(() -> softTypeInstanceService.newViewVersion(typeCode(body), oid),
                "新建视图版本失败");
    }

    /** 删除实例及其全部子版本 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid,
                                    @RequestParam(required = false) String typeDefinitionCode) {
        return execVoid(() -> softTypeInstanceService.delete(typeDefinitionCode, oid), "删除失败");
    }

    /** 删除最新小版本 */
    @DeleteMapping("/{oid}/latest-iteration")
    public ApiResponse<Void> deleteLatestIteration(@PathVariable String oid,
                                                   @RequestParam(required = false) String typeDefinitionCode) {
        return execVoid(() -> softTypeInstanceService.deleteLatestIteration(typeDefinitionCode, oid),
                "删除最新小版本失败");
    }

    /** 查询历史版本 */
    @GetMapping("/{oid}/iterations")
    public ApiResponse<List<Map<String, Object>>> iterations(@PathVariable String oid,
                                                             @RequestParam String typeDefinitionCode) {
        try {
            List<Map<String, Object>> list =
                    softTypeInstanceService.iterations(typeDefinitionCode, oid);
            return ApiResponse.ok(list != null ? list : new ArrayList<>());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询历史版本失败: " + e.getMessage());
        }
    }

    // ==================== 生命周期状态（行操作「设置生命周期状态」）====================

    /**
     * 该实例可设置的生命周期状态候选。
     *
     * <pre>
     * GET /api/softtype-instances/{oid}/lifecycle-states?typeDefinitionCode=ELECTRONIC
     * → { templateCode, templateName, current:{code,name}, initial:{code,name},
     *     states:[{code, name, reachable, reason}], reason }
     * </pre>
     *
     * <p>依据是<b>该类型绑定的生命周期模板</b>：状态清单、初始状态、以及"从当前状态一步能迁到谁"。
     * 界面据此把迁不过去的选项禁用并说明原因，而不是让用户点了才知道。
     */
    @GetMapping("/{oid}/lifecycle-states")
    public ApiResponse<LifecycleStateOptionsVO> lifecycleStates(@PathVariable String oid,
                                                               @RequestParam String typeDefinitionCode) {
        try {
            LifecycleStateOptionsVO options = typeLifecycleStateService.options(
                    typeDefinitionCode, currentStatusCode(typeDefinitionCode, oid));
            // 对象正在流程中 → 不允许手工改状态（与服务端执行时的判定同一处，见 runtimeBlockedReason）
            String blocked = runtimeBlockedReason(oid, typeDefinitionCode);
            if (blocked != null) {
                options.setBlockedReason(blocked);
            }
            return ApiResponse.ok(options);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "读取生命周期状态失败: " + e.getMessage());
        }
    }

    /**
     * 设置生命周期状态。
     *
     * <pre>
     * POST /api/softtype-instances/{oid}/lifecycle-state
     * { "typeDefinitionCode": "ELECTRONIC", "mode": "INITIAL" }                       // 退回模板初始状态
     * { "typeDefinitionCode": "ELECTRONIC", "mode": "SPECIFIED", "targetStateCode": "IN_WORK" }
     * </pre>
     *
     * <p>两种模式都按模板执行：指定状态要求当前状态能<b>一步</b>迁到目标；退回初始状态则
     * 沿模板的回退规则<b>逐跳</b>退回（多跳在模板里没有直接规则，不允许一步跳过去）。
     *
     * <p><b>对象正在流程中时一律拒绝</b>（409）：流程里的「设置状态」服务节点会在该到的时候改它，
     * 手工插一脚会让"流程走到哪一步"与"对象是什么状态"对不上。界面已按同一条件禁用，
     * 这里再挡一道 —— 绕过界面直接调接口同样改不了。
     */
    @PostMapping("/{oid}/lifecycle-state")
    public ApiResponse<Void> setLifecycleState(@PathVariable String oid,
                                               @RequestBody Map<String, Object> body) {
        String typeDefinitionCode = typeCode(body);
        String mode = str(body, "mode");
        boolean initial = "INITIAL".equalsIgnoreCase(mode);
        String targetStateCode = str(body, "targetStateCode");
        if (!initial && targetStateCode == null) {
            return ApiResponse.fail(400, "请选择要设置的目标状态");
        }
        String blocked = runtimeBlockedReason(oid, typeDefinitionCode);
        if (blocked != null) {
            return ApiResponse.fail(409, blocked);
        }
        return execVoid(() -> {
            if (initial) {
                softTypeInstanceService.resetLifecycleState(typeDefinitionCode, oid, str(body, "entityVersion"));
            } else {
                softTypeInstanceService.setLifecycleState(
                        typeDefinitionCode, oid, str(body, "entityVersion"), targetStateCode);
            }
        }, "设置生命周期状态失败");
    }

    /**
     * 该对象此刻能否手工设置状态 —— 不能就给出原因（可读），能则返回 null。
     *
     * <p>判据是<b>流程运行时</b>：同一「业务对象 + 大版本」已有流程实例在跑（挂起也算，它没结束）
     * 就不允许。复用发起流程那道闸门（{@code ProcessEntitySetService#resolveStartConflict}），
     * 两处口径天然一致 —— 不会再出现"能发起却不能改状态"或反之。
     *
     * <p><b>为什么这道闸只在统一入口（行操作）上，而不在能力层</b>：流程里的
     * 「设置状态」服务节点走的是 {@code SoftTypeInstanceService#setLifecycleState}，
     * 它<b>必须</b>能在流程执行中改状态 —— 那是流程本身要干的事。挡的是"人手工改"。
     */
    private String runtimeBlockedReason(String oid, String typeDefinitionCode) {
        try {
            ProcessStartConflictVO conflict =
                    entitySetService.resolveStartConflict(oid, typeDefinitionCode, null);
            return conflict.isBlocked()
                    ? ProcessEntitySetService.conflictMessage(conflict, "设置生命周期状态")
                    : null;
        } catch (RuntimeException e) {
            // 判定不出来（宿主/类型解析失败等）不在这里挡：让后续真实操作去报它自己的原因
            log.warn("流程中判定失败，按未在流程中处理: oid={}, type={}, error={}",
                    oid, typeDefinitionCode, e.getMessage());
            return null;
        }
    }

    /** 实例当前状态 code（宿主形态不同，统一按 Map 读详情里的 statusCode） */
    private String currentStatusCode(String typeDefinitionCode, String oid) {
        try {
            Object detail = softTypeInstanceService.get(typeDefinitionCode, oid, new LinkedHashMap<>());
            if (detail == null) {
                return null;
            }
            Map<String, Object> map = objectMapper.convertValue(detail,
                    new TypeReference<Map<String, Object>>() { });
            Object code = map.get("statusCode");
            return code == null ? null : code.toString();
        } catch (RuntimeException e) {
            // 读不到当前状态不阻断弹窗：候选项照给，只是可达性判断退化为"未知"
            log.warn("读取实例当前状态失败，按无状态处理: type={}, oid={}, error={}",
                    typeDefinitionCode, oid, e.getMessage());
            return null;
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body != null ? body.get(key) : null;
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ==================== 能力发现 / 诊断 ====================

    /**
     * 某类型支持的操作集合（小写下划线标识）。
     *
     * <pre>
     * GET /api/softtype-instances/capabilities?typeDefinitionCode=FOOTPRINT
     * → { "code": 200, "data": { "hostCode": "ENG_DOCUMENT",
     *                            "operations": ["create","read","update"] } }
     * </pre>
     *
     * <p>前端行菜单<b>据此渲染</b>：支持则显示、不支持则不显示 ——
     * 从而彻底退役「类型 → 端点」映射表，新增宿主/软类型前端零改动。
     */
    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities(@RequestParam String typeDefinitionCode) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("typeDefinitionCode", typeDefinitionCode);
            data.put("hostCode", softTypeInstanceService.resolveHost(typeDefinitionCode));
            data.put("operations", softTypeInstanceService.supportedOperations(typeDefinitionCode));
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 解析类型所属能力宿主（诊断入口） */
    @GetMapping("/host")
    public ApiResponse<Map<String, Object>> resolveHost(@RequestParam String typeDefinitionCode) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("typeDefinitionCode", typeDefinitionCode);
            data.put("hostCode", softTypeInstanceService.resolveHost(typeDefinitionCode));
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 当前已实现能力策略的宿主列表 */
    @GetMapping("/hosts")
    public ApiResponse<List<String>> hosts() {
        return ApiResponse.ok(new ArrayList<>(softTypeInstanceService.supportedHosts()));
    }

    // ==================== 私有工具 ====================

    private static String typeCode(Map<String, Object> body) {
        Object code = body != null ? body.get("typeDefinitionCode") : null;
        return code != null ? code.toString() : null;
    }

    private static Map<String, Object> stringParams(Map<String, String> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                // typeDefinitionCode 已单独接收，不再作为业务参数透传
                if (!"typeDefinitionCode".equals(k)) {
                    result.put(k, v);
                }
            });
        }
        return result;
    }

    /** 返回实体类结果的统一异常映射 */
    private ApiResponse<Object> execResult(Supplier<Object> action, String failMessage) {
        return exec(() -> {
            Object entity = action.get();
            if (entity == null) {
                return ApiResponse.fail(404, "实例不存在");
            }
            return ApiResponse.ok(entity);
        }, failMessage);
    }

    /** 无返回值的统一异常映射 */
    private ApiResponse<Void> execVoid(Runnable action, String failMessage) {
        try {
            action.run();
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, failMessage + ": " + e.getMessage());
        }
    }

    /** 统一异常映射（action 自行构造 ApiResponse，以便区分 404） */
    private ApiResponse<Object> exec(Supplier<ApiResponse<Object>> action, String failMessage) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, failMessage + ": " + e.getMessage());
        }
    }
}
