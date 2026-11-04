/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.product.entity.Deliverable;
import cn.ck.plm.product.entity.DeliverableArtifactLink;
import cn.ck.plm.product.mapper.DeliverableArtifactLinkMapper;
import cn.ck.plm.product.mapper.DeliverableMapper;
import cn.ck.plm.product.service.impl.DeliverableStatusEvaluator;
import cn.ck.plm.product.service.impl.ProductTeamRoleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 交付件 ↔ 产出物 关联 REST 接口（2026-10-11 新增）。
 *
 * <h3>为什么不是"交付件-文档"关联</h3>
 * IPD 交付件的产出物<b>不都是文档</b>：原理图（SCHEMATIC）/ PCB（PCB_LAYOUT）是工程数据，
 * 结构件 / 软件 / 元器件是零组件 —— 三类在本系统同构（oid + 版本 + 生命周期），故统一为
 * "产出物引用"，完成判定（BOUND / LIFECYCLE）对三类走同一套逻辑。
 *
 * <h3>硬约束（用户要求：只允许关联对象类别与模板一致）</h3>
 * <ul>
 *   <li>{@code objectType} 非空（模板给了 object_type）→ 只允许 type_definition_code 等于它的对象；
 *       其宿主（查 ck_type_definition.root_type_code）决定候选查哪张表。</li>
 *   <li>{@code objectType} 为空 → 按 {@code bindMode}：DOCUMENT → 文档宿主；PART_SET → 零组件宿主；
 *       其它 → 三类都可（兜底，不静默放宽类型）。</li>
 * </ul>
 *
 * <h3>跨容器</h3>
 * 允许引用资源库 / 其它型号的对象（用户明确要求）—— 候选查询不按容器过滤，
 * 返回 container_oid 供前端标注"本型号 / 其它容器"；创建时把容器快照落库。
 *
 * <h3>入口收敛</h3>
 * 关联的唯一写入口是交付件管理页（MethodologyInstancePanel → 产出物弹窗）；
 * 文档 / 对象详情只做只读回显（后续接）。
 *
 * <pre>
 * GET    /api/deliverable-artifact-links?deliverableOid=   关联列表（含产出物编码/名称）
 * GET    /api/deliverable-artifact-links/summary?ownerOid=  每个交付件的已关联数
 * GET    /api/deliverable-artifact-links/progress?deliverableOid=  产出物进展（状态 + 是否在流程中/当前节点/办理人）
 * GET    /api/deliverable-artifact-links/candidates?deliverableOid=&keyword=  候选（硬约束过滤）
 * POST   /api/deliverable-artifact-links                    { deliverableOid, entityType, entityOid }
 * DELETE /api/deliverable-artifact-links/{oid}
 * </pre>
 */
@RestController
@RequestMapping("/api/deliverable-artifact-links")
public class DeliverableArtifactLinkController {

    private static final Logger log = LoggerFactory.getLogger(DeliverableArtifactLinkController.class);

    private static final Set<String> ENTITY_TYPES = Set.of("DOCUMENT", "ENG_DOCUMENT", "PART");
    private static final String ROLE_PRIMARY = "PRIMARY";

    private final DeliverableArtifactLinkMapper linkMapper;
    private final DeliverableMapper deliverableMapper;
    /** 关联变更后立即重算交付件状态（2026-10-11：绑定/解除要马上反映到状态列，不必等下次打开面板触发的惰性重算） */
    private final DeliverableStatusEvaluator statusEvaluator;
    /**
     * 项目级权限（2026-10-14 用户要求：按"我在系列/型号团队里的角色"判能否关联交付项）。
     */
    private final ProductTeamRoleResolver roleResolver;
    /** 产出物在流程中 / 当前节点 / 办理人（与对象详情页「关联流程」同一份数据） */
    private final ProcessService workflowService;
    /** 生命周期状态 code → 显示名（「REVIEW」→「评审中」） */
    private final LifecycleStatusService lifecycleStatusService;

    public DeliverableArtifactLinkController(DeliverableArtifactLinkMapper linkMapper,
                                             DeliverableMapper deliverableMapper,
                                             DeliverableStatusEvaluator statusEvaluator,
                                             ProductTeamRoleResolver roleResolver,
                                             ProcessService workflowService,
                                             LifecycleStatusService lifecycleStatusService) {
        this.linkMapper = linkMapper;
        this.deliverableMapper = deliverableMapper;
        this.statusEvaluator = statusEvaluator;
        this.roleResolver = roleResolver;
        this.workflowService = workflowService;
        this.lifecycleStatusService = lifecycleStatusService;
    }

    /**
     * 关联/解除的项目级权限：产品经理（该系列/型号团队里），或本交付件的**责任人本人**；
     * 管理员豁免。返回 null = 通过，否则返回给用户的中文原因。
     */
    private String linkPermissionProblem(Deliverable deliverable) {
        if (deliverable == null) {
            return "交付件不存在";
        }
        if (roleResolver.canLinkArtifactUnder(deliverable.getOwnerOid(), deliverable.getOwnerRole())) {
            return null;
        }
        String role = deliverable.getOwnerRole() == null || deliverable.getOwnerRole().trim().isEmpty()
                ? "未设置" : deliverable.getOwnerRole();
        return "没有关联权：本操作限该系列 / 型号团队里的「产品经理」，或本交付件的责任人（" + role + "）";
    }

    /** 重算交付件状态：失败不阻断关联操作本身（状态下次打开面板还会再算一次） */
    private void refreshStatus(String ownerOid) {
        try {
            statusEvaluator.reevaluate(ownerOid);
        } catch (Exception e) {
            log.warn("关联后重算交付件状态失败: owner={} err={}", ownerOid, e.getMessage());
        }
    }

    // ==================== 查询 ====================

    /** 某交付件下的关联列表（含产出物的编码 / 名称，前端直出） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam String deliverableOid) {
        return ApiResponse.ok(linkMapper.selectByDeliverableOid(deliverableOid));
    }

    /** 某归属单元（系列 / 型号）下每个交付件的已关联数：{ deliverableOid: cnt } */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam String ownerOid) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : linkMapper.countByOwnerOid(ownerOid)) {
            result.put(String.valueOf(row.get("deliverableOid")), row.get("cnt"));
        }
        return ApiResponse.ok(result);
    }

    /**
     * 交付件的「产出物进展」——「状态」列悬停浮层的数据源（2026-10-25）。
     *
     * <pre>
     * GET /api/deliverable-artifact-links/progress?deliverableOid=...
     * </pre>
     *
     * <p><b>为什么需要它</b>：状态列只有一枚「进行中」，而"为什么进行中、卡在哪一步"全在产出物身上
     * （判定规则见 {@link DeliverableStatusEvaluator}：绑定产出物即进行中；关联对象在流程中不允许判完成）。
     * 这里把每个产出物的<b>最新迭代状态</b>（含显示名）与<b>是否在流程中 / 当前节点 / 办理人</b>
     * 一并给出 —— 用户不必再跳到对象详情页逐条查。
     *
     * <p><b>为什么按交付件单条取、不随列表预加载</b>：46 条交付件 × N 个产出物 × 流程查询，
     * 代价与"只想看一眼"的收益不对等；浮层是悬停才拉的（与 BOM 替代明细同一做法）。
     */
    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> progress(@RequestParam String deliverableOid) {
        Deliverable deliverable = deliverableMapper.selectByOid(deliverableOid);
        if (deliverable == null) {
            return ApiResponse.fail(404, "交付件不存在");
        }
        List<Map<String, Object>> links = linkMapper.selectByDeliverableOid(deliverableOid);
        Map<String, Map<String, String>> statusOf = latestStatuses(links);

        List<Map<String, Object>> items = new ArrayList<>();
        // 判定要用的东西：产出物最新状态码 + 所用生命周期模板 + 哪些产出物正在流程中（都在下面的循环里顺手收集）
        Map<String, String> statusCodes = new HashMap<>();
        Map<String, String> templateOf = new HashMap<>();
        Set<String> runningOids = new LinkedHashSet<>();
        for (Map<String, Object> link : links == null ? List.<Map<String, Object>>of() : links) {
            String entityOid = trim(str(link.get("entityOid")));
            if (entityOid == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entityType", link.get("entityType"));
            item.put("typeCode", link.get("typeCode"));
            item.put("entityOid", entityOid);
            item.put("entityCode", link.get("entityCode"));
            item.put("entityName", link.get("entityName"));

            // 生命周期状态：code 用于配色，name 给人看（模板里的 display_name 优先，见 LifecycleStatusService）
            Map<String, String> state = statusOf.get(entityOid);
            String statusCode = state == null ? null : state.get("status");
            if (statusCode != null) {
                statusCodes.put(entityOid, statusCode);
            }
            String templateOid = state == null ? null : trim(str(state.get("lifecycleTemplateIterationOid")));
            if (templateOid != null) {
                templateOf.put(entityOid, templateOid);
            }
            item.put("statusCode", statusCode);
            item.put("statusName", statusCode == null ? null
                    : lifecycleStatusService.displayName(state.get("lifecycleTemplateIterationOid"), statusCode));

            // 流程：查不到（或没有在跑）就如实标 false —— 不猜、不编造"在流程中"
            ProcessInstanceVO running = runningInstanceOf(entityOid);
            item.put("inProcess", running != null);
            if (running != null) {
                runningOids.add(entityOid);
                item.put("processInstanceId", running.getId());
                item.put("processName", running.getProcessDefinitionName() != null
                        ? running.getProcessDefinitionName() : running.getProcessDefinitionKey());
                item.put("currentNode", running.getCurrentActivityName());
                item.put("currentAssignees", running.getCurrentAssignees());
                item.put("startUserName", running.getStartUserName());
            }
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deliverableOid", deliverableOid);
        result.put("status", deliverable.getStatus());
        result.put("items", items);
        // 逐条判定依据：告诉用户"为什么还不是已完成"（与列表状态同源，见 DeliverableStatusEvaluator#judge）
        DeliverableStatusEvaluator.Facts facts = new DeliverableStatusEvaluator.Facts();
        facts.links = links;
        facts.latestStatus = statusCodes;
        facts.templateOf = templateOf;
        facts.templates = statusEvaluator.templateStatesOf(templateOf);
        facts.runningOids = runningOids;
        facts.openIssues = openIssuesOf(deliverable.getOwnerOid(), deliverableOid);
        result.put("completion", statusEvaluator.judge(deliverable, facts).toMap());
        return ApiResponse.ok(result);
    }

    /**
     * 该交付件的产出物上未闭环的问题（ISSUE_CLOSED 判定用）。
     * 读不到时返回 <b>null</b> —— 判定器据此标"不可判定"，而不是当成"没有问题"。
     */
    private List<Map<String, Object>> openIssuesOf(String ownerOid, String deliverableOid) {
        if (ownerOid == null || deliverableOid == null) {
            return null;
        }
        try {
            List<Map<String, Object>> all = linkMapper.selectOpenIssuesOfOwnerArtifacts(ownerOid);
            List<Map<String, Object>> mine = new ArrayList<>();
            for (Map<String, Object> row : all == null ? List.<Map<String, Object>>of() : all) {
                if (deliverableOid.equals(trim(str(row.get("deliverableOid"))))) {
                    mine.add(row);
                }
            }
            return mine;
        } catch (Exception e) {
            log.warn("查询交付件产出物的未闭环问题失败（ISSUE_CLOSED 按不可判定处理）: deliverable={} err={}",
                    deliverableOid, e.getMessage());
            return null;
        }
    }

    /**
     * 候选产出物：按交付件的硬约束决定查哪张表、是否按类型过滤。
     * 跨容器（不按 container_oid 过滤），前端用 containerOid 标注归属。
     */
    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates(@RequestParam String deliverableOid,
                                                             @RequestParam(required = false) String keyword) {
        Deliverable deliverable = deliverableMapper.selectByOid(deliverableOid);
        if (deliverable == null) {
            return ApiResponse.fail(404, "交付件不存在");
        }
        String kw = trim(keyword);
        String objectType = trim(deliverable.getObjectType());
        List<Map<String, Object>> result = new ArrayList<>();

        if (objectType != null) {
            // 硬约束：只认 objectType 对应的类型；宿主决定查哪张表
            String root = linkMapper.selectRootTypeCode(objectType);
            if ("ENG_DOCUMENT".equals(root)) {
                result.addAll(linkMapper.searchEngDocCandidates(objectType, kw));
            } else if ("PART".equals(root)) {
                result.addAll(linkMapper.searchPartCandidates(objectType, kw));
            } else if ("DOCUMENT".equals(root)) {
                result.addAll(linkMapper.searchDocumentCandidates(kw));
            } else {
                // 未登记的类型 code（自定义）：三类都查、按类型过滤，至少不会查空
                result.addAll(linkMapper.searchDocumentCandidates(kw));
                result.addAll(linkMapper.searchEngDocCandidates(objectType, kw));
                result.addAll(linkMapper.searchPartCandidates(objectType, kw));
            }
        } else {
            // 无 objectType：按 bindMode 落到宿主级；都空则三类全查
            String bindMode = trim(deliverable.getBindMode());
            if ("DOCUMENT".equalsIgnoreCase(bindMode)) {
                result.addAll(linkMapper.searchDocumentCandidates(kw));
            } else if ("PART_SET".equalsIgnoreCase(bindMode)) {
                result.addAll(linkMapper.searchPartCandidates(null, kw));
            } else {
                result.addAll(linkMapper.searchDocumentCandidates(kw));
                result.addAll(linkMapper.searchEngDocCandidates(null, kw));
                result.addAll(linkMapper.searchPartCandidates(null, kw));
            }
        }
        return ApiResponse.ok(result);
    }

    // ==================== 创建 ====================

    /** 创建关联：body { deliverableOid, entityType, entityOid }（服务端复核存在性与硬约束） */
    @PostMapping
    public ApiResponse<DeliverableArtifactLink> create(@RequestBody Map<String, Object> body) {
        String deliverableOid = trim(str(body.get("deliverableOid")));
        String entityType = trim(str(body.get("entityType")));
        String entityOid = trim(str(body.get("entityOid")));
        if (deliverableOid == null) return ApiResponse.fail(400, "缺少交付件（deliverableOid）");
        if (entityType == null || !ENTITY_TYPES.contains(entityType)) {
            return ApiResponse.fail(400, "缺少或非法的产出物类型（entityType：DOCUMENT / ENG_DOCUMENT / PART）");
        }
        if (entityOid == null) return ApiResponse.fail(400, "缺少产出物（entityOid）");

        Deliverable deliverable = deliverableMapper.selectByOid(deliverableOid);
        if (deliverable == null) return ApiResponse.fail(404, "交付件不存在");

        // 项目级权限：谁负责谁关联（责任人本人），或团队里的产品经理；管理员豁免
        String permissionProblem = linkPermissionProblem(deliverable);
        if (permissionProblem != null) return ApiResponse.fail(403, permissionProblem);

        // 服务端复核产出物存在性，并取其类型 / 容器快照（不信任前端传来的类别）
        Map<String, Object> artifact = switch (entityType) {
            case "DOCUMENT" -> linkMapper.selectDocumentArtifact(entityOid);
            case "ENG_DOCUMENT" -> linkMapper.selectEngDocArtifact(entityOid);
            default -> linkMapper.selectPartArtifact(entityOid);
        };
        if (artifact == null) return ApiResponse.fail(400, "产出物不存在或已删除");

        String typeCode = str(artifact.get("typeCode"));
        String containerOid = str(artifact.get("containerOid"));

        String violation = validateArtifact(deliverable, entityType, typeCode);
        if (violation != null) return ApiResponse.fail(400, violation);

        // 查重：一个产出物只属于一个交付件（2026-10-29 用户口径）。
        // 候选清单已把"已被别的交付件占用"的对象排除掉，这里兜住并发与绕过前端的调用。
        String occupiedBy = linkMapper.selectDeliverableOidByEntity(entityType, entityOid);
        if (occupiedBy != null) {
            return ApiResponse.fail(400, occupiedBy.equals(deliverableOid)
                    ? "该产出物已关联到本交付件"
                    : "该产出物已关联到其它交付件，请先在那边解除关联");
        }

        DeliverableArtifactLink link = new DeliverableArtifactLink();
        link.setOid(UUID.randomUUID().toString());
        link.setDeliverableOid(deliverableOid);
        link.setEntityType(entityType);
        link.setTypeCode(typeCode);
        link.setEntityOid(entityOid);
        link.setRole(ROLE_PRIMARY);
        link.setContainerOid(containerOid);
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        try {
            linkMapper.insert(link);
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建关联失败: " + e.getMessage());
        }
        // 用户口径："如果绑定了对象修改更新状态" —— 绑定当下就重算（未开始 → 进行中）
        refreshStatus(deliverable.getOwnerOid());
        return ApiResponse.ok(link);
    }

    // ==================== 删除 ====================

    /** 解除关联（只删关系，不动产出物）；解除同样重算状态（可能从"进行中"退回"未开始"） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            DeliverableArtifactLink link = linkMapper.selectByOid(oid);
            if (link == null) return ApiResponse.fail(404, "关联不存在");
            // 解除与创建同权限口径（谁负责谁关联，也就谁可以解除）
            Deliverable deliverable = deliverableMapper.selectByOid(link.getDeliverableOid());
            String permissionProblem = linkPermissionProblem(deliverable);
            if (permissionProblem != null) return ApiResponse.fail(403, permissionProblem);
            String ownerOid = deliverable == null ? null : deliverable.getOwnerOid();
            int n = linkMapper.deleteByOid(oid);
            if (n == 0) return ApiResponse.fail(404, "关联不存在");
            if (ownerOid != null) refreshStatus(ownerOid);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "解除关联失败: " + e.getMessage());
        }
    }

    /** 交付件 → 归属单元（系列 / 型号）oid */
    private String ownerOidOf(String deliverableOid) {
        if (deliverableOid == null) return null;
        Deliverable d = deliverableMapper.selectByOid(deliverableOid);
        return d == null ? null : d.getOwnerOid();
    }

    // ==================== 产出物进展（状态列浮层） ====================

    /**
     * 产出物的最新迭代状态：{@code entityOid → { status, lifecycleTemplateIterationOid }}。
     * 三类产出物各查一次（与判定器同一套查询，只是多取一列模板子版本用于显示名）。
     */
    private Map<String, Map<String, String>> latestStatuses(List<Map<String, Object>> links) {
        List<String> docOids = new ArrayList<>();
        List<String> engOids = new ArrayList<>();
        List<String> partOids = new ArrayList<>();
        for (Map<String, Object> link : links == null ? List.<Map<String, Object>>of() : links) {
            String oid = trim(str(link.get("entityOid")));
            if (oid == null) continue;
            switch (String.valueOf(link.get("entityType"))) {
                case "DOCUMENT" -> docOids.add(oid);
                case "ENG_DOCUMENT" -> engOids.add(oid);
                case "PART" -> partOids.add(oid);
                default -> { /* 未知类型：不给状态（界面上显示「无状态」比编一个更诚实） */ }
            }
        }
        Map<String, Map<String, String>> out = new HashMap<>();
        collectStatuses(docOids.isEmpty() ? null : linkMapper.selectLatestDocStatus(docOids), out);
        collectStatuses(engOids.isEmpty() ? null : linkMapper.selectLatestEngDocStatus(engOids), out);
        collectStatuses(partOids.isEmpty() ? null : linkMapper.selectLatestPartStatus(partOids), out);
        return out;
    }

    private void collectStatuses(List<Map<String, Object>> rows, Map<String, Map<String, String>> into) {
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String oid = trim(str(row.get("entityOid")));
            if (oid == null) continue;
            Map<String, String> state = new HashMap<>();
            state.put("status", trim(str(row.get("status"))));
            state.put("lifecycleTemplateIterationOid", trim(str(row.get("lifecycleTemplateIterationOid"))));
            into.put(oid, state);
        }
    }

    /**
     * 该产出物当前在跑的流程（执行中 / 已挂起）；没有则 null。
     * 查询失败只告警 —— 浮层其余信息（状态、编码）仍然有用，不该整块失败。
     */
    private ProcessInstanceVO runningInstanceOf(String entityOid) {
        try {
            for (ProcessInstanceVO vo : workflowService.findInstancesByEntity(entityOid, null)) {
                if ("running".equals(vo.getStatus()) || "suspended".equals(vo.getStatus())) {
                    return vo;
                }
            }
        } catch (Exception e) {
            log.warn("查询产出物流程状态失败（该产出物按未在流程显示）: entityOid={} err={}", entityOid, e.getMessage());
        }
        return null;
    }

    // ==================== 硬约束 ====================

    /**
     * 校验产出物类别是否满足交付件的硬约束（用户明确要求硬约束，不放宽档）。
     *
     * @return null = 通过；否则返回给用户的中文错误
     */
    private String validateArtifact(Deliverable deliverable, String entityType, String typeCode) {
        String objectType = trim(deliverable.getObjectType());
        if (objectType != null) {
            if (!objectType.equalsIgnoreCase(typeCode)) {
                return "该交付件的产出物必须是「" + objectType + "」类型的对象（当前对象类型为："
                        + (typeCode == null ? "未知" : typeCode) + "）";
            }
            return null;
        }
        String bindMode = trim(deliverable.getBindMode());
        if ("DOCUMENT".equalsIgnoreCase(bindMode) && !"DOCUMENT".equals(entityType)) {
            return "该交付件的产出物必须是文档";
        }
        if ("PART_SET".equalsIgnoreCase(bindMode) && !"PART".equals(entityType)) {
            return "该交付件的产出物必须是零组件";
        }
        return null;
    }

    // ==================== 小工具 ====================

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
