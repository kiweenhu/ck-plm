/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.dto.ProcessActivityVO;
import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.dto.ProcessStartConflictVO;
import cn.ck.plm.process.dto.ProcessStartOptionVO;
import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.process.service.api.ProcessStartOptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例监控 REST API —— 对应前端「流程监控」页面。
 *
 * <pre>
 * GET    /api/workflow/instance/all-running   运行中（本租户全部）
 * GET    /api/workflow/instance/my-running    我发起的
 * GET    /api/workflow/instance/my-involved   我参与的（含已结束）
 * GET    /api/workflow/instance/by-entity     某业务对象关联的流程（详情页「关联流程」两栏用）
 * GET    /api/workflow/instance/running-entities 批量判定哪些业务对象正在流程中（发起弹窗过滤候选）
 * GET    /api/workflow/instance/{id}/activities 节点经路（走到哪了：已办 / 在办 / 未到达）
 * GET    /api/workflow/instance/{id}/node-logs 节点执行日志（某个节点后台跑了什么、报了什么错）
 * GET    /api/workflow/instance/{id}          详情（含流程变量）
 * POST   /api/workflow/instance/{id}/suspend  挂起
 * POST   /api/workflow/instance/{id}/activate 激活
 * POST   /api/workflow/instance/{id}/terminate 终止 {"reason":"..."}
 * DELETE /api/workflow/instance/{id}          删除（运行中先终止，再清理历史）
 * POST   /api/workflow/instance/start         发起流程 {"processKey":"...","businessKey":"...",
 *                                             "entityOid":"...","typeCode":"...","entityCode":"...","variables":{...}}
 * GET    /api/workflow/instance/start-options 发起前解析（业务对象类型+状态 → 该发起哪个流程 + 最新版本信息）
 * </pre>
 *
 * <p>租户与用户身份由 {@code AuthInterceptor} 注入的上下文提供；所有操作均按租户校验归属。
 */
@RestController
@RequestMapping("/api/workflow/instance")
public class ProcessInstanceController {

    private static final Logger log = LoggerFactory.getLogger(ProcessInstanceController.class);

    /**
     * 业务对象集合流程变量名 —— {@code ck_process_entity_set} 在流程里的引用。
     *
     * <p>流程与业务对象的关联记在 {@code ck_process_entity_set}（Flowable 内核之外）：
     * 一个实例可以关联<b>多个</b>对象、每行还带大版本，这是引擎的 businessKey（单个字符串）
     * 表达不了的。这个变量是该关联在流程内的可用形态，供节点表达式 / 通知模板取用。
     */
    private static final String VAR_BUSINESS_OBJECT_SET = "businessObjectSet";

    /**
     * 退役的"业务对象三件套"变量名（{@code businessObjectType / businessObjectOid / businessObjectCode}）。
     *
     * <p>它们是"一个实例只关联一个对象"的扁平表达，与 entitySet（集合 + 大版本）说的不是同一件事，
     * 且由发起方各自拼装（同一个事实两处产出）。发起时这里主动<b>剔除</b>：
     * 老前端（浏览器里缓存的旧构建）仍会带上来，放它写进流程变量就会出现两套说法。
     */
    private static final List<String> RETIRED_BUSINESS_VARIABLES = List.of(
            "businessObjectType", "businessObjectOid", "businessObjectCode");

    private final ProcessService workflowService;
    /** 发起前解析：业务对象（类型 + 状态）该发起哪个流程（所有业务行操作下拉共用） */
    private final ProcessStartOptionService startOptionService;
    /** 发起成功后记录"实例 ↔ 业务实体"关联（ck_process_entity_set） */
    private final ProcessEntitySetService entitySetService;
    /** 节点执行日志：自动服务/通知节点后台跑了什么（详见 /node-logs 端点说明） */
    private final ProcessNodeLogService nodeLogService;

    public ProcessInstanceController(ProcessService workflowService,
                                     ProcessStartOptionService startOptionService,
                                     ProcessEntitySetService entitySetService,
                                     ProcessNodeLogService nodeLogService) {
        this.workflowService = workflowService;
        this.startOptionService = startOptionService;
        this.entitySetService = entitySetService;
        this.nodeLogService = nodeLogService;
    }

    /** 运行中流程实例（本租户全部） */
    @GetMapping("/all-running")
    public ApiResponse<List<ProcessInstanceVO>> allRunning(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return list("all-running", page, size);
    }

    /** 我发起的流程实例 */
    @GetMapping("/my-running")
    public ApiResponse<List<ProcessInstanceVO>> myRunning(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return list("my-running", page, size);
    }

    /** 我参与的流程实例（含已结束） */
    @GetMapping("/my-involved")
    public ApiResponse<List<ProcessInstanceVO>> myInvolved(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return list("my-involved", page, size);
    }

    /**
     * 批量判定：这些业务对象里哪些正在流程中 —— 「发起流程」弹窗用它过滤候选。
     *
     * <pre>
     * GET /api/workflow/instance/running-entities?typeCode=PART&amp;entityOids=a,b,c
     * </pre>
     *
     * <p>为什么必须由后端判：依据是"关联表 + 引擎运行时"，且是<b>大版本粒度</b>
     * （同一对象的 A 版在跑流程，B 版照样能发起）。这套口径的唯一实现就是发起闸门本身
     * （{@code ProcessEntitySetService#resolveStartConflict}）—— 前端自己拿状态猜，
     * 必然出现"列表里显示可选、提交却被拒"。
     *
     * <p>返回"已在流程中"的对象 oid；查不动时返回空列表（候选过滤是体验优化，不是闸门，
     * 不能因为它把弹窗整个卡住；真正的拦截在 start 那一步，那里会给出准确原因）。
     */
    @GetMapping("/running-entities")
    public ApiResponse<List<String>> runningEntities(@RequestParam(required = false) String entityOids,
                                                     @RequestParam(required = false) String typeCode) {
        List<String> oids = new ArrayList<>();
        if (entityOids != null) {
            for (String part : entityOids.split(",")) {
                String oid = part.trim();
                if (!oid.isEmpty()) {
                    oids.add(oid);
                }
            }
        }
        try {
            return ApiResponse.ok(entitySetService.findRunningEntityOids(oids, typeCode));
        } catch (Exception e) {
            log.warn("查询在流程中的业务对象失败: typeCode={}, count={}, error={}",
                    typeCode, oids.size(), e.getMessage());
            return ApiResponse.ok(new ArrayList<>());
        }
    }

    private ApiResponse<List<ProcessInstanceVO>> list(String scope, int page, int size) {
        try {
            return ApiResponse.ok(workflowService.findInstances(scope, page, size));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询流程实例失败: " + e.getMessage());
        }
    }

    /**
     * 某个业务对象关联的流程实例 —— 各业务对象详情页「关联流程」两栏共用。
     *
     * <pre>
     * GET /api/workflow/instance/by-entity?entityOid=...&amp;entityVersion=A
     * </pre>
     *
     * <p>返回里<b>执行中的在前、已执行的在后</b>（各自按时间倒序），调用方按 {@code status}
     * 分两栏即可。{@code entityVersion} 可空 —— 不传即该对象<b>全部大版本</b>的流程，
     * 每行带 {@code entityVersion} 便于按版本区分（大版本之间是两轮工作）。
     */
    @GetMapping("/by-entity")
    public ApiResponse<List<ProcessInstanceVO>> byEntity(@RequestParam String entityOid,
                                                         @RequestParam(required = false) String entityVersion) {
        try {
            return ApiResponse.ok(workflowService.findInstancesByEntity(entityOid, entityVersion));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询对象关联流程失败: " + e.getMessage());
        }
    }

    /**
     * 某流程实例的<b>节点经路</b> —— "走到哪了"（对象详情页「关联流程」展开用）。
     *
     * <pre>
     * GET /api/workflow/instance/{id}/activities
     * </pre>
     *
     * <p>按时间顺序返回节点：已办（含责任人 / 活动类型 / 意见 / 实际走的连线名）、
     * 当前在办、尚未到达。网关与连线不出现在结果里（它们是路由，不是活动）。
     */
    @GetMapping("/{id}/activities")
    public ApiResponse<List<ProcessActivityVO>> activities(@PathVariable String id) {
        try {
            return ApiResponse.ok(workflowService.findInstanceActivities(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询流程节点经路失败: " + e.getMessage());
        }
    }

    /**
     * 某流程实例的<b>节点执行日志</b> —— "这个节点后台到底跑了什么、报了什么错"。
     *
     * <pre>
     * GET /api/workflow/instance/{id}/node-logs                 整个实例的日志（前端按节点分组）
     * GET /api/workflow/instance/{id}/node-logs?activityId=xxx  只看某个节点
     * </pre>
     *
     * <p>给自动服务（设置状态 / 自动服务 / 通知）用：它们是后台跑的，失败时用户只看到
     * "流程卡住了"，原因原本只留在服务器日志里。详情页点开节点即可看到执行情况与错误堆栈。
     *
     * <p>不带 {@code activityId} 时一次返回整个实例的（通常几十条内），
     * 前端点节点不再各发一次请求 —— 时间轴上还要显示"哪个节点有错"。
     */
    @GetMapping("/{id}/node-logs")
    public ApiResponse<List<ProcessNodeLog>> nodeLogs(@PathVariable String id,
                                                     @RequestParam(required = false) String activityId) {
        try {
            return ApiResponse.ok(activityId != null && !activityId.trim().isEmpty()
                    ? nodeLogService.findByActivity(id, activityId.trim())
                    : nodeLogService.findByInstance(id));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询节点执行日志失败: " + e.getMessage());
        }
    }

    /** 流程实例详情（含流程变量） */
    @GetMapping("/{id}")
    public ApiResponse<ProcessInstanceVO> detail(@PathVariable String id) {
        try {
            return ApiResponse.ok(workflowService.findInstanceDetail(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询流程实例详情失败: " + e.getMessage());
        }
    }

    /** 挂起流程实例 */
    @PostMapping("/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable String id) {
        try {
            workflowService.suspendInstance(id);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "挂起失败: " + e.getMessage());
        }
    }

    /** 激活流程实例 */
    @PostMapping("/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable String id) {
        try {
            workflowService.activateInstance(id);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "激活失败: " + e.getMessage());
        }
    }

    /** 终止流程实例 */
    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        try {
            Object reason = body == null ? null : body.get("reason");
            workflowService.terminateInstance(id, reason == null ? null : reason.toString());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "终止失败: " + e.getMessage());
        }
    }

    /** 删除流程实例 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            workflowService.deleteInstance(id);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 发起流程实例（业务侧调用，由业务页面的「发起流程」弹框使用）。
     *
     * <pre>
     * POST /api/workflow/instance/start
     * { "processKey": "plm-change-review", "businessKey": "&lt;主对象 oid&gt;",
     *   // 形态一（多对象，本次新增）：entities 第一条即主对象
     *   "entities": [ { "entityOid": "...", "typeCode": "ELECTRONIC",
     *                   "entityCode": "PART-202609-0020", "entityVersion": "A" }, … ],
     *   // 形态二（老形态，单对象）：顶层给 entityOid / typeCode / entityCode / entityVersion
     *   "entityOid": "&lt;业务对象 oid&gt;",
     *   "entityVersion": "&lt;业务对象大版本，如 A&gt;", // 带版本对象才有；不传则后端按对象当前大版本解析
     *   "typeCode": "ELECTRONIC", "rootTypeCode": "PART",   // 宿主可不传，后端按类型补
     *   "entityCode": "PART-202609-0020",          // 业务对象编码（显示用，供 businessObjectSet.code）
     *   "variables": { "reason": "..." } }
     * </pre>
     *
     * <p>发起成功后按实体信息写入 {@code ck_process_entity_set}（一行 = 该实例关联的一个业务实体，
     * <b>多对象就写多行</b>），供"流程详情看关联对象 / 对象反查参与过的流程"使用；
     * 同一份数据同时作为流程变量 {@code businessObjectSet} 交给引擎（见 {@link #businessObjectSetOf}）。
     *
     * <p><b>发起闸门</b>：<b>逐个</b>业务对象判定 —— 任一对象已有流程在执行
     * （本表关联或 businessKey 命中，且引擎运行时里仍未结束）时返回 {@code code=409}
     * 及可读原因（多对象时指明是哪一个），整单不放行。
     *
     * @return 流程实例 id
     */
    @PostMapping("/start")
    public ApiResponse<String> start(@RequestBody Map<String, Object> body) {
        try {
            String processKey = str(body, "processKey");
            String businessKey = str(body, "businessKey");
            Object raw = body == null ? null : body.get("variables");
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (raw instanceof Map)
                    ? new HashMap<>((Map<String, Object>) raw)
                    : new HashMap<>();
            // 剔除退役的三件套：业务对象信息改由服务端按关联表统一产出（businessObjectSet），
            // 留着调用方自带的那份就会出现"两套说法"
            RETIRED_BUSINESS_VARIABLES.forEach(variables::remove);

            List<Map<String, Object>> entityInputs = entityInputsOf(body);
            List<ProcessEntitySet> links = new ArrayList<>(entityInputs.size());
            List<Map<String, Object>> entries = new ArrayList<>(entityInputs.size());
            for (Map<String, Object> input : entityInputs) {
                // 闸门：同一业务对象的【同一个大版本】同时只允许一个流程在执行（大版本之间独立）。
                // 弹框已按 start-options 的结果预先禁用，这里是服务端兜底 —— 前端可绕过，
                // 且"连点两次""两人同时点"这类竞态只有服务端拦得住（并发窗口很小，未加分布式锁）。
                ProcessStartConflictVO conflict = entitySetService.resolveStartConflict(
                        str(input, "entityOid"), str(input, "typeCode"), str(input, "entityVersion"));
                if (conflict.isBlocked()) {
                    return ApiResponse.fail(409,
                            conflictMessageFor(input, conflict, entityInputs.size()));
                }
                // 关联行先算好（版本用闸门解析出的那一个），再拿同一行产出流程变量：
                // "流程里说的对象"与"表里记的对象"因此永远一致，不会出现表里记 A、流程里说 B
                ProcessEntitySet link = buildEntityLink(businessKey, input, conflict.getEntityVersion());
                links.add(link);
                entries.add(businessObjectEntryOf(link, str(input, "entityCode")));
            }
            if (!entries.isEmpty()) {
                variables.put(VAR_BUSINESS_OBJECT_SET, businessObjectSetOf(entries));
            }
            String instanceId = workflowService.startProcess(processKey, businessKey, variables);
            recordEntityLinks(instanceId, links);
            return ApiResponse.ok(instanceId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "发起流程失败: " + e.getMessage());
        }
    }

    /**
     * 本次要关联的业务对象清单（顺序即调用方给的顺序，<b>第一条是主对象</b>）。
     *
     * <p>两种入参形态取其一：
     * <ol>
     *   <li>多对象：{@code entities: [{entityOid, typeCode, entityCode?, entityVersion?, rootTypeCode?}, …]}；</li>
     *   <li>单对象（老形态）：顶层 {@code entityOid} / {@code typeCode} / … 。</li>
     * </ol>
     *
     * <p>没有 {@code entityOid} 的项直接跳过：本表是"实体 ↔ 实例"的关联，没有实体就没有可记的东西
     * （{@code businessKey} 是业务标识、未必等于实体 oid，不去猜）。整份清单为空时
     * 退化成"不记关联、只有流程变量"的老行为。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> entityInputsOf(Map<String, Object> body) {
        List<Map<String, Object>> inputs = new ArrayList<>();
        Object raw = body == null ? null : body.get("entities");
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item instanceof Map) {
                    Map<String, Object> entry = (Map<String, Object>) item;
                    if (!isEmpty(str(entry, "entityOid"))) {
                        inputs.add(entry);
                    }
                }
            }
            return inputs;
        }
        if (!isEmpty(str(body, "entityOid"))) {
            inputs.add(body);
        }
        return inputs;
    }

    /**
     * 把"是哪一个对象被闸门挡住"说清楚。
     *
     * <p>单对象时保持原来的整句（那里上下文只有它自己，加前缀是废话）；
     * 多对象时必须点名 —— 否则用户面对五条对象，不知道卡在哪一条上。
     */
    private String conflictMessageFor(Map<String, Object> input, ProcessStartConflictVO conflict, int total) {
        String base = ProcessEntitySetService.conflictMessage(conflict);
        if (total <= 1) {
            return base;
        }
        String label = isEmpty(str(input, "entityCode")) ? str(input, "entityOid") : str(input, "entityCode");
        return "本次要关联的对象「" + label + "」不能参与：" + base;
    }

    /**
     * 组装"实例 ↔ 业务实体"关联行（不含 {@code processInstanceId} —— 它要等流程起来才有）。
     *
     * <p>{@code businessKey} 是<b>整个流程实例</b>的业务标识（多对象时即主对象 oid）：
     * 一行一个业务对象，所以每行的业务标识相同、而 {@code entityOid} 各不相同。
     *
     * <p>发起时先算好这些行，是为了让流程变量 {@code businessObjectSet} 与落库的那几行
     * 共用同一份取值（含同一个大版本）。
     */
    private ProcessEntitySet buildEntityLink(String businessKey, Map<String, Object> body, String resolvedVersion) {
        ProcessEntitySet row = new ProcessEntitySet();
        row.setBusinessKey(businessKey);
        row.setEntityOid(str(body, "entityOid"));
        // 业务对象的大版本（如 A）：优先用闸门判定时解析出的那个（一次判定、同一个值），
        // 否则用请求里显式给的；都没有则服务层按"该对象当前的大版本"解析。
        // 无版本对象解析不到 → 落 NULL（不写空串）
        row.setEntityVersion(resolvedVersion != null ? resolvedVersion : str(body, "entityVersion"));
        row.setTypeCode(str(body, "typeCode"));
        row.setRootTypeCode(str(body, "rootTypeCode"));
        return row;
    }

    /**
     * 落库"流程实例 ↔ 业务实体"关联（多对象逐个记，{@code record} 自身幂等）。
     *
     * <p>写失败<b>不影响发起结果</b>：流程已经在引擎里跑起来了，这时返回"发起失败"会误导用户重试
     * （重试 = 又起一个实例），故只记 ERROR 日志、由数据侧核对补录。
     */
    private void recordEntityLinks(String instanceId, List<ProcessEntitySet> links) {
        for (ProcessEntitySet link : links) {
            try {
                link.setProcessInstanceId(instanceId);
                entitySetService.record(link);
            } catch (Exception e) {
                log.error("流程实体关联写入失败（流程已发起，需人工补录）: instance={} entity={} error={}",
                        instanceId, link.getEntityOid(), e.getMessage(), e);
            }
        }
    }

    /**
     * 业务对象集合流程变量 —— {@code ck_process_entity_set} 在流程里的引用。
     *
     * <pre>
     * businessObjectSet = {
     *   primary:  { typeCode, rootTypeCode, entityOid, entityVersion, businessKey, code },
     *   entities: [ primary, {…第二个对象…}, … ]   // 本次关联的全部对象，第一条即 primary
     * }
     * </pre>
     *
     * <p><b>为什么是一个对象，而不是三个标量变量</b>（原先的
     * {@code businessObjectType / businessObjectOid / businessObjectCode}）：
     * 关联本身是<b>集合</b>（一行一个对象、每行带大版本），三个平铺的变量把
     * "一个实例只关联一个对象"写死进了变量名 —— 加第二个对象时它们无法表达，
     * 加一个字段（大版本、编码…）就得再加一个变量名。合成一个对象后，
     * 模板里只需记一个名字，扩展字段也不必新增变量；"一起走审批"的那几个对象
     * 就落在 {@code entities} 数组里。
     *
     * <p>{@code primary} 与 {@code entities[0]} 是<b>同一个对象</b>：前者是"主对象"这个
     * 常用访问点的直达入口（{@code ${businessObjectSet.primary.entityOid}}），
     * 后者是完整集合。两条路径同源，不会各说各话。
     *
     * <p>{@code code} 是显示编码（如 PART-202609-0020），由发起方带入：
     * 流程模块不回查业务表（见 {@code ProcessServiceImpl#entityRefsOf}），
     * 它只负责把"流程关联了哪些对象"如实交出去。
     */
    private Map<String, Object> businessObjectSetOf(List<Map<String, Object>> entries) {
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("primary", entries.get(0));
        set.put("entities", new ArrayList<>(entries));
        return set;
    }

    /** 单个业务对象在 {@code businessObjectSet} 里的形态 */
    private Map<String, Object> businessObjectEntryOf(ProcessEntitySet link, String entityCode) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("typeCode", link.getTypeCode());
        entry.put("rootTypeCode", link.getRootTypeCode());
        entry.put("entityOid", link.getEntityOid());
        entry.put("entityVersion", link.getEntityVersion());
        entry.put("businessKey", link.getBusinessKey());
        entry.put("code", entityCode);
        return entry;
    }

    /** 空串与 null 都算"没给" */
    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * 发起前解析：业务对象（类型编码 + 状态）该发起哪个流程，并给出该流程的最新版本信息。
     *
     * <pre>
     * GET /api/workflow/instance/start-options?typeDefinitionCode=PART&amp;statusCode=DRAFT
     *     [&amp;iterationOid=...][&amp;entityOid=...]
     * </pre>
     *
     * <p>各业务对象页面的「发起流程」弹框都调这一个接口（弹框组件也共用），
     * 解析规则（类型 + 状态 → 流程模板）在类型模块，本接口只负责取配置 + 组装展示信息。
     *
     * <p>未配置 / 模板已删 / 尚未部署 / <b>该对象已有流程在执行</b>时同样返回 200，
     * 但 {@code startable=false} 且带 {@code reason}，让弹框能<b>说清为什么不能发起</b>，
     * 而不是点了才报错。
     */
    @GetMapping("/start-options")
    public ApiResponse<ProcessStartOptionVO> startOptions(@RequestParam String typeDefinitionCode,
                                                         @RequestParam String statusCode,
                                                         @RequestParam(required = false) String iterationOid,
                                                         @RequestParam(required = false) String entityOid) {
        try {
            return ApiResponse.ok(startOptionService.resolve(typeDefinitionCode, statusCode, iterationOid, entityOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "解析发起流程信息失败: " + e.getMessage());
        }
    }

    private String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
