/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.entity.DashboardTemplate;
import cn.ck.plm.base.entity.DeliverableTemplate;
import cn.ck.plm.base.entity.GateTemplate;
import cn.ck.plm.base.entity.MetricTemplate;
import cn.ck.plm.base.entity.ReviewTemplate;
import cn.ck.plm.base.entity.RuleTemplate;
import cn.ck.plm.base.service.api.DashboardTemplateService;
import cn.ck.plm.base.service.api.DeliverableTemplateService;
import cn.ck.plm.base.service.api.GateTemplateService;
import cn.ck.plm.base.service.api.MetricTemplateService;
import cn.ck.plm.base.service.api.ReviewTemplateService;
import cn.ck.plm.base.service.api.RuleTemplateService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.mapper.TenantMapper;
import cn.ck.plm.product.dto.InstancePermissions;
import cn.ck.plm.product.dto.MethodologyInstances;
import cn.ck.plm.product.entity.Dashboard;
import cn.ck.plm.product.entity.Deliverable;
import cn.ck.plm.product.entity.Gate;
import cn.ck.plm.product.entity.Metric;
import cn.ck.plm.product.entity.Review;
import cn.ck.plm.product.entity.Rule;
import cn.ck.plm.product.mapper.DashboardMapper;
import cn.ck.plm.product.mapper.DeliverableMapper;
import cn.ck.plm.product.mapper.GateMapper;
import cn.ck.plm.product.mapper.MetricMapper;
import cn.ck.plm.product.mapper.ReviewMapper;
import cn.ck.plm.product.mapper.RuleMapper;
import cn.ck.plm.product.service.api.MethodologyInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link MethodologyInstanceService} 的实现。
 *
 * <h3>三处刻意的选择</h3>
 * <ol>
 *   <li><b>行业一次解析、六类共用</b>：读 {@code ck_tenant.dev_method_code}（引导页选定、平台权威），
 *       不用"本租户有 TRADITIONAL 模板就用它"那种猜法 —— 那会让"模板是 IPD、实例却按 TRADITIONAL 建"。
 *       读不到（未引导）才留空，各模板服务会返回全部模板；再不行回退平台模板。</li>
 *   <li><b>企业已停用的模板不发给新项目</b>：模板层的 {@code disabled} 就是"这条不再用于新建"，
 *       故快照时跳过并计数 —— 已建实例不受影响（它们本来就没包含被停用的条目）。</li>
 *   <li><b>幂等以"该类是否已有实例"为守卫</b>：与 {@code initDefaultStages} 同口径，
 *       重复调用不会翻倍；也便于将来对存量系列/型号做一次性补齐。</li>
 * </ol>
 */
@Service
public class MethodologyInstanceServiceImpl implements MethodologyInstanceService {

    private static final Logger log = LoggerFactory.getLogger(MethodologyInstanceServiceImpl.class);

    private final DeliverableTemplateService deliverableTemplateService;
    private final GateTemplateService gateTemplateService;
    private final ReviewTemplateService reviewTemplateService;
    private final RuleTemplateService ruleTemplateService;
    private final MetricTemplateService metricTemplateService;
    private final DashboardTemplateService dashboardTemplateService;

    private final DeliverableMapper deliverableMapper;
    private final GateMapper gateMapper;
    private final ReviewMapper reviewMapper;
    private final RuleMapper ruleMapper;
    private final MetricMapper metricMapper;
    private final DashboardMapper dashboardMapper;

    private final TenantMapper tenantMapper;
    /** 角色字典：交付件的责任人库里只存 code，展示时要换成 name（见 {@link #fillOwnerRoleNames}） */
    private final RoleMapper roleDictMapper;
    /**
     * 项目级权限判定（"我在这个系列/型号团队里是什么角色"）——
     * 2026-10-14 起裁剪 / 发起 / 关联三件事都走它，不再看全局角色。
     */
    private final ProductTeamRoleResolver roleResolver;
    /**
     * 评审的「流程进展」现算器：评审行的 status 是快照值，发起流程后不会自己变
     * （见 {@link ReviewProcessEvaluator} 的说明）。不动库，只在返回前补。
     */
    private final ReviewProcessEvaluator reviewProcessEvaluator;
    /**
     * 阶段的「流程进展」现算器：同评审那一套 —— 门的 status 是快照值，
     * 发起决策流程后不会自己变（见 {@link GateProcessEvaluator}）。
     */
    private final GateProcessEvaluator gateProcessEvaluator;

    public MethodologyInstanceServiceImpl(DeliverableTemplateService deliverableTemplateService,
                                          GateTemplateService gateTemplateService,
                                          ReviewTemplateService reviewTemplateService,
                                          RuleTemplateService ruleTemplateService,
                                          MetricTemplateService metricTemplateService,
                                          DashboardTemplateService dashboardTemplateService,
                                          DeliverableMapper deliverableMapper,
                                          GateMapper gateMapper,
                                          ReviewMapper reviewMapper,
                                          RuleMapper ruleMapper,
                                          MetricMapper metricMapper,
                                          DashboardMapper dashboardMapper,
                                          TenantMapper tenantMapper,
                                          RoleMapper roleDictMapper,
                                          ProductTeamRoleResolver roleResolver,
                                          ReviewProcessEvaluator reviewProcessEvaluator,
                                          GateProcessEvaluator gateProcessEvaluator) {
        this.deliverableTemplateService = deliverableTemplateService;
        this.gateTemplateService = gateTemplateService;
        this.reviewTemplateService = reviewTemplateService;
        this.ruleTemplateService = ruleTemplateService;
        this.metricTemplateService = metricTemplateService;
        this.dashboardTemplateService = dashboardTemplateService;
        this.deliverableMapper = deliverableMapper;
        this.gateMapper = gateMapper;
        this.reviewMapper = reviewMapper;
        this.ruleMapper = ruleMapper;
        this.metricMapper = metricMapper;
        this.dashboardMapper = dashboardMapper;
        this.tenantMapper = tenantMapper;
        this.roleDictMapper = roleDictMapper;
        this.roleResolver = roleResolver;
        this.reviewProcessEvaluator = reviewProcessEvaluator;
        this.gateProcessEvaluator = gateProcessEvaluator;
    }

    @Override
    @Transactional
    public Map<String, Integer> initFor(String ownerOid, String ownerType) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        String industry = resolveIndustry();
        summary.put("deliverable", initDeliverables(ownerOid, ownerType, industry));
        summary.put("gate",        initGates(ownerOid, ownerType, industry));
        summary.put("review",      initReviews(ownerOid, ownerType, industry));
        summary.put("rule",        initRules(ownerOid, ownerType, industry));
        summary.put("metric",      initMetrics(ownerOid, ownerType, industry));
        summary.put("dashboard",   initDashboards(ownerOid, ownerType, industry));
        log.info("为归属单元 {} (type={}) 初始化方法论实例: {}", ownerOid, ownerType, summary);
        return summary;
    }

    // ==================== 读（页面展示）====================

    @Override
    public MethodologyInstances findByOwner(String ownerOid) {
        MethodologyInstances bundle = new MethodologyInstances();
        // 归属 oid 为空 → 六个空列表（页面还没选中系列/型号时不该报红）
        if (ownerOid == null || ownerOid.trim().isEmpty()) {
            bundle.setDeliverables(List.of());
            bundle.setGates(List.of());
            bundle.setReviews(List.of());
            bundle.setRules(List.of());
            bundle.setMetrics(List.of());
            bundle.setDashboards(List.of());
            bundle.setPermissions(emptyPermissions());
            return bundle;
        }
        String oid = ownerOid.trim();
        List<Deliverable> deliverables = deliverableMapper.selectByOwnerOid(oid);
        // 责任人库里只有角色 code，这里补上角色名（界面显示「系统工程师」而不是 SYSTEM_ENGINEER）
        fillOwnerRoleNames(deliverables);
        bundle.setDeliverables(deliverables);
        // 阶段门：同评审一样现算 —— 库里的 status 是快照值，决策流程发起后变化只在流程侧
        // （否则点了「发起」、流程都走到材料核对节点了，行上还是「未开始」）
        List<Gate> gates = gateMapper.selectByOwnerOid(oid);
        gateProcessEvaluator.enrich(oid, gates);
        bundle.setGates(gates);
        // 评审：库里的 status 是快照值，发起流程后变化只在流程侧 —— 返回前现算一次
        // （否则点了「发起」、流程都跑到核对节点了，行上还是「未开始 / 未评审」）
        List<Review> reviews = reviewMapper.selectByOwnerOid(oid);
        reviewProcessEvaluator.enrich(oid, reviews);
        bundle.setReviews(reviews);
        bundle.setRules(ruleMapper.selectByOwnerOid(oid));
        bundle.setMetrics(metricMapper.selectByOwnerOid(oid));
        bundle.setDashboards(dashboardMapper.selectByOwnerOid(oid));
        // 「我在这个项目下能做什么」随包返回（按团队角色判）—— 前端按钮显隐的唯一依据
        bundle.setPermissions(resolvePermissions(oid));
        return bundle;
    }

    /**
     * 交付件「责任人」：库里存角色 code，界面要显示角色名（2026-10-26 用户反馈"显示的是 code"）。
     *
     * <p><b>为什么不写进 SQL</b>：{@code ck_role} 的 code 在各租户各有一份（同一 code 多行），
     * 直接 JOIN 会把交付件行翻倍；而租户拦截器只按主表别名注入条件，JOIN 进来的那份不受控。
     * 这里一次取本租户可见的角色字典（平台 + 本租户），按 code 建表再回填，O(n)。
     *
     * <p>取字典失败不影响列表：名字留空，前端退回显示 code —— 宁可显示 code，也不整页报错。
     */
    private void fillOwnerRoleNames(List<Deliverable> deliverables) {
        if (deliverables == null || deliverables.isEmpty()) {
            return;
        }
        Map<String, String> nameByCode = new HashMap<>();
        try {
            for (Role role : roleDictMapper.selectAll()) {
                if (role == null || role.getCode() == null) {
                    continue;
                }
                // 同一 code 可能既有平台副本又有租户副本：保留先到的那个（显示名一致）
                nameByCode.putIfAbsent(role.getCode(), role.getName());
            }
        } catch (Exception e) {
            log.warn("读取角色字典失败，责任人退回显示角色码: {}", e.getMessage());
        }
        for (Deliverable d : deliverables) {
            String code = d.getOwnerRole();
            if (code != null && !code.isBlank()) {
                d.setOwnerRoleName(nameByCode.get(code));
            }
        }
    }

    /** 我在该归属单元下的权限摘要（团队角色；管理员豁免） */
    private InstancePermissions resolvePermissions(String ownerOid) {
        InstancePermissions p = new InstancePermissions();
        p.setAdmin(roleResolver.isAdmin());
        p.setCanTailor(roleResolver.canTailorUnder(ownerOid));
        p.setCanStartProcess(roleResolver.canStartProcessUnder(ownerOid));
        // 看裁剪行的人与能裁剪的人分开：项目经理可以看「本项目不适用」，但不能改
        p.setCanViewTailored(roleResolver.canViewTailoredUnder(ownerOid));
        p.setMyRoleNames(new ArrayList<>(roleResolver.myRoleNamesUnder(ownerOid)));
        p.setMyRoleCodes(new ArrayList<>(roleResolver.myRoleCodesUnder(ownerOid)));
        return p;
    }

    /** 无归属时的空权限（页面还没选中系列/型号，什么都不给） */
    private InstancePermissions emptyPermissions() {
        InstancePermissions p = new InstancePermissions();
        p.setAdmin(false);
        p.setCanTailor(false);
        p.setCanStartProcess(false);
        p.setCanViewTailored(false);
        p.setMyRoleNames(List.of());
        p.setMyRoleCodes(List.of());
        return p;
    }

    // ==================== 裁剪（实例层）====================

    @Override
    @Transactional
    public void tailor(String kind, String oid, Boolean disabled, Boolean mandatory, String ownerRole) {
        if (oid == null || oid.trim().isEmpty()) {
            throw new IllegalArgumentException("实例 oid 不能为空");
        }
        if (disabled == null && mandatory == null && ownerRole == null) {
            throw new IllegalArgumentException(
                    "至少要给 disabled（停用/恢复）/ mandatory（改强制项）/ ownerRole（改责任人）之一");
        }
        String target = oid.trim();
        String k = kind == null ? "" : kind.trim();

        // 先定位实例拿归属单元 —— 权限是**项目级**的（我在这个系列/型号团队里是不是产品经理），
        // 顺带也确认了 oid 有效（不存在则 ownerOid 为空）
        String ownerOid = ownerOidOf(k, target);
        if (ownerOid == null) {
            throw new IllegalArgumentException("实例不存在或不属于当前租户: " + k + "/" + target);
        }
        if (!roleResolver.canTailorUnder(ownerOid)) {
            throw new SecurityException(
                    "没有裁剪权：本操作限该系列 / 型号团队里的「产品经理」（或管理员）");
        }
        // 已经"动起来"的不能标记「本项目不适用」：停用等于把在跑的 / 已留痕的记录从清单里抹掉
        // —— 办到一半的人回头看列表，自己那条不见了（2026-10-26 用户口径："已经在进行中的不可以裁剪"）。
        // 2026-10-29 追加：**已完成**的评审、以及已进入流程的决策门同样不许标（结论还在，条目没了＝无法追溯）。
        // 前端也会把按钮置灰，这里是真正拦得住的那一道。
        if (Boolean.TRUE.equals(disabled)) {
            blockTailorIfProgressed(k, target, ownerOid);
        }

        switch (k) {
            case "deliverable":
                if (disabled != null) {
                    requireAffected(deliverableMapper.updateDisabled(target, disabled), k, target);
                }
                if (mandatory != null) {
                    requireAffected(deliverableMapper.updateMandatory(target, mandatory), k, target);
                }
                if (ownerRole != null) {
                    requireAffected(deliverableMapper.updateOwnerRole(target, ownerRole.trim()), k, target);
                }
                break;
            case "gate":
            case "review":
            case "rule":
                if (mandatory != null) {
                    throw new IllegalArgumentException(
                            "只有交付件支持改「强制 ↔ 可选」；" + k + " 的裁剪只有停用/恢复");
                }
                if (ownerRole != null) {
                    throw new IllegalArgumentException(
                            "只有交付件有「责任人」；" + k + " 的裁剪只有停用/恢复");
                }
                // 到这里 disabled 必非空（三字段都空的情形已在上面拦下）
                int affected = switch (k) {
                    case "gate" -> gateMapper.updateDisabled(target, disabled);
                    case "review" -> reviewMapper.updateDisabled(target, disabled);
                    default -> ruleMapper.updateDisabled(target, disabled);
                };
                requireAffected(affected, k, target);
                break;
            default:
                throw new IllegalArgumentException(
                        "不支持裁剪的类别: " + kind + "（只支持 deliverable / gate / review / rule）");
        }
        log.info("实例裁剪: kind={} oid={} owner={} disabled={} mandatory={} ownerRole={}",
                k, target, ownerOid, disabled, mandatory, ownerRole);
    }

    /**
     * 已产生进度（进行中 / 已完成）的评审、阶段门一律不许标记「本项目不适用」。
     *
     * <p>为什么评审要看现算结果：`ck_review.status` 是快照值，流程走完后库里仍是 PENDING，
     * 只有 {@link ReviewProcessEvaluator#enrich} 才会按流程实例算出 DONE —— 直接读库会漏掉
     * 刚刚跑完的那条（2026-10-29 用户反馈：评审已完成还能点「不适用」）。
     *
     * <p>阶段门同理（2026-10-29 接上）：`ck_gate.status` 只有模板初始化时的 PENDING，
     * 真实进度由 {@link GateProcessEvaluator} 按决策流程实例现算 —— 直接读库会漏掉
     * 正在决策、或已决策完的那条。
     */
    private void blockTailorIfProgressed(String kind, String oid, String ownerOid) {
        if ("review".equals(kind)) {
            Review review = reviewMapper.selectByOid(oid);
            if (review == null) {
                return;
            }
            if (reviewProcessEvaluator.isRunning(ownerOid, review)) {
                throw new IllegalArgumentException("「" + review.getName()
                        + "」正在评审中，不能标记「本项目不适用」；请先完成或终止该流程");
            }
            List<Review> one = new ArrayList<>(List.of(review));
            reviewProcessEvaluator.enrich(ownerOid, one);
            if ("DONE".equals(one.get(0).getStatus())) {
                throw new IllegalArgumentException("「" + review.getName()
                        + "」评审已完成（结论已留痕），不能再标记「本项目不适用」"
                        + "；如需作废请走变更，清单里不能凭空少一条");
            }
            return;
        }
        if ("gate".equals(kind)) {
            Gate gate = gateMapper.selectByOid(oid);
            if (gate == null) {
                return;
            }
            // 库里的 status 只有模板快照的 PENDING —— 真实进度必须按流程实例现算（2026-10-29 接上）
            List<Gate> one = new ArrayList<>(List.of(gate));
            gateProcessEvaluator.enrich(ownerOid, one);
            Gate current = one.get(0);
            if (current.getStatus() == null || "PENDING".equals(current.getStatus())) {
                return;
            }
            boolean finished = !"IN_PROGRESS".equals(current.getStatus());
            throw new IllegalArgumentException("「" + current.getName()
                    + "」决策流程" + (finished ? "已完成（决策留痕在流程里）" : "正在进行中")
                    + "，不能标记「本项目不适用」；请先结束该决策流程");
        }
    }

    /**
     * 实例 → 归属单元（系列 / 型号）oid；查不到（不存在 / 非本租户）返回 {@code null}。
     */
    private String ownerOidOf(String kind, String oid) {
        switch (kind) {
            case "deliverable": {
                Deliverable d = deliverableMapper.selectByOid(oid);
                return d == null ? null : d.getOwnerOid();
            }
            case "gate": {
                Gate g = gateMapper.selectByOid(oid);
                return g == null ? null : g.getOwnerOid();
            }
            case "review": {
                Review r = reviewMapper.selectByOid(oid);
                return r == null ? null : r.getOwnerOid();
            }
            case "rule": {
                Rule r = ruleMapper.selectByOid(oid);
                return r == null ? null : r.getOwnerOid();
            }
            default:
                return null;
        }
    }

    /** 更新影响 0 行 = oid 不存在（或不属于本租户）→ 明确报错，而不是假装成功 */
    private void requireAffected(int affected, String kind, String oid) {
        if (affected <= 0) {
            throw new IllegalArgumentException("实例不存在或不属于当前租户: " + kind + "/" + oid);
        }
    }

    /**
     * 行业 / 方法论：取自本租户引导时选定的方法论（ck_tenant.dev_method_code，如 IPD）。
     * 未引导或读不到时返回 null —— 各模板服务的 findAll(null) 语义是"不过滤"。
     */
    private String resolveIndustry() {
        try {
            Tenant tenant = tenantMapper.selectByOid(TenantContext.get());
            return tenant == null ? null : tenant.getDevMethodCode();
        } catch (Exception e) {
            log.warn("读取租户方法论失败，按'不过滤'处理: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 交付件 ====================

    private int initDeliverables(String ownerOid, String ownerType, String industry) {
        int existing = deliverableMapper.countByOwnerOid(ownerOid);
        if (existing > 0) { skip("交付件", ownerOid, existing); return existing; }
        List<DeliverableTemplate> templates = deliverableTemplateService.findAll(industry);
        if (templates.isEmpty()) templates = deliverableTemplateService.findPlatformTemplates(industry);
        int n = 0;
        for (DeliverableTemplate t : templates) {
            if (Boolean.TRUE.equals(t.getDisabled())) { skippedDisabled("交付件", t.getCode()); continue; }
            deliverableMapper.insert(snapshotDeliverable(t, ownerOid, ownerType));
            n++;
        }
        log.info("交付件实例: 归属单元 {} 新增 {} 条（industry={}）", ownerOid, n, industry);
        return n;
    }

    private Deliverable snapshotDeliverable(DeliverableTemplate t, String ownerOid, String ownerType) {
        Deliverable d = new Deliverable();
        d.setOwnerOid(ownerOid);
        d.setOwnerType(ownerType);
        d.setCode(t.getCode());
        d.setName(t.getName());
        d.setDescription(t.getDescription());
        d.setStageKey(t.getStageKey());
        d.setDiscipline(t.getDiscipline());
        d.setMandatory(Boolean.TRUE.equals(t.getMandatory()));
        d.setOwnerRole(t.getOwnerRole());
        d.setObjectType(t.getObjectType());
        d.setBindMode(t.getBindMode());
        d.setCompletionRule(t.getCompletionRule());
        d.setDueOffsetDays(t.getDueOffsetDays());
        d.setGate(t.getGate());
        d.setIndustryNote(t.getIndustryNote());
        d.setTemplateCode(t.getCode());
        d.setStatus("PENDING");
        d.setDisabled(false);
        d.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : 0);
        return d;
    }

    // ==================== 阶段门 ====================

    private int initGates(String ownerOid, String ownerType, String industry) {
        int existing = gateMapper.countByOwnerOid(ownerOid);
        if (existing > 0) { skip("阶段门", ownerOid, existing); return existing; }
        List<GateTemplate> templates = gateTemplateService.findAll(industry);
        if (templates.isEmpty()) templates = gateTemplateService.findPlatformTemplates(industry);
        int n = 0;
        for (GateTemplate t : templates) {
            if (Boolean.TRUE.equals(t.getDisabled())) { skippedDisabled("阶段门", t.getCode()); continue; }
            Gate g = new Gate();
            g.setOwnerOid(ownerOid);
            g.setOwnerType(ownerType);
            g.setCode(t.getCode());
            g.setName(t.getName());
            g.setDescription(t.getDescription());
            g.setStageKey(t.getStageKey());
            g.setType(t.getType());
            g.setProcessKey(t.getProcessKey());
            g.setDecisionOptions(t.getDecisionOptions());
            g.setAutoChecks(t.getAutoChecks());
            g.setRequiredParticipants(t.getRequiredParticipants());
            g.setRecordFields(t.getRecordFields());
            g.setEvidenceRequired(t.getEvidenceRequired());
            g.setIndustryNote(t.getIndustryNote());
            g.setTemplateCode(t.getCode());
            g.setStatus("PENDING");
            g.setDisabled(false);
            g.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : 0);
            gateMapper.insert(g);
            n++;
        }
        log.info("阶段门实例: 归属单元 {} 新增 {} 条（industry={}）", ownerOid, n, industry);
        return n;
    }

    // ==================== 评审 ====================

    private int initReviews(String ownerOid, String ownerType, String industry) {
        int existing = reviewMapper.countByOwnerOid(ownerOid);
        if (existing > 0) { skip("评审", ownerOid, existing); return existing; }
        List<ReviewTemplate> templates = reviewTemplateService.findAll(industry);
        if (templates.isEmpty()) templates = reviewTemplateService.findPlatformTemplates(industry);
        int n = 0;
        for (ReviewTemplate t : templates) {
            if (Boolean.TRUE.equals(t.getDisabled())) { skippedDisabled("评审", t.getCode()); continue; }
            Review r = new Review();
            r.setOwnerOid(ownerOid);
            r.setOwnerType(ownerType);
            r.setCode(t.getCode());
            r.setName(t.getName());
            r.setDescription(t.getDescription());
            r.setStageKey(t.getStageKey());
            r.setGate(t.getGate());
            r.setType(t.getType());
            r.setProcessKey(t.getProcessKey());
            r.setCheckItems(t.getCheckItems());
            r.setOutputTypes(t.getOutputTypes());
            r.setVerdictOptions(t.getVerdictOptions());
            r.setIndustryNote(t.getIndustryNote());
            r.setTemplateCode(t.getCode());
            r.setStatus("PENDING");
            r.setDisabled(false);
            r.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : 0);
            reviewMapper.insert(r);
            n++;
        }
        log.info("评审实例: 归属单元 {} 新增 {} 条（industry={}）", ownerOid, n, industry);
        return n;
    }

    // ==================== 检查规则 ====================

    private int initRules(String ownerOid, String ownerType, String industry) {
        int existing = ruleMapper.countByOwnerOid(ownerOid);
        if (existing > 0) { skip("检查规则", ownerOid, existing); return existing; }
        List<RuleTemplate> templates = ruleTemplateService.findAll(industry);
        if (templates.isEmpty()) templates = ruleTemplateService.findPlatformTemplates(industry);
        int n = 0;
        for (RuleTemplate t : templates) {
            if (Boolean.TRUE.equals(t.getDisabled())) { skippedDisabled("检查规则", t.getCode()); continue; }
            Rule r = new Rule();
            r.setOwnerOid(ownerOid);
            r.setOwnerType(ownerType);
            r.setCode(t.getCode());
            r.setName(t.getName());
            r.setDescription(t.getDescription());
            r.setTriggerType(t.getTriggerType());
            r.setScope(t.getScope());
            r.setCategory(t.getCategory());
            r.setStageKey(t.getStageKey());
            r.setGate(t.getGate());
            r.setCondition(t.getCondition());
            r.setSeverity(t.getSeverity());
            r.setMessage(t.getMessage());
            r.setDefaultOverride(t.getDefaultOverride());
            r.setFixAction(t.getFixAction());
            r.setIndustryNote(t.getIndustryNote());
            r.setTemplateCode(t.getCode());
            r.setStatus("PENDING");
            r.setDisabled(false);
            r.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : 0);
            ruleMapper.insert(r);
            n++;
        }
        log.info("检查规则实例: 归属单元 {} 新增 {} 条（industry={}）", ownerOid, n, industry);
        return n;
    }

    // ==================== 指标 ====================

    private int initMetrics(String ownerOid, String ownerType, String industry) {
        int existing = metricMapper.countByOwnerOid(ownerOid);
        if (existing > 0) { skip("指标", ownerOid, existing); return existing; }
        List<MetricTemplate> templates = metricTemplateService.findAll(industry);
        if (templates.isEmpty()) templates = metricTemplateService.findPlatformTemplates(industry);
        int n = 0;
        for (MetricTemplate t : templates) {
            if (Boolean.TRUE.equals(t.getDisabled())) { skippedDisabled("指标", t.getCode()); continue; }
            Metric m = new Metric();
            m.setOwnerOid(ownerOid);
            m.setOwnerType(ownerType);
            m.setCode(t.getCode());
            m.setName(t.getName());
            m.setDescription(t.getDescription());
            m.setLayer(t.getLayer());
            m.setLevel(t.getLevel());
            m.setSource(t.getSource());
            m.setIndustryNote(t.getIndustryNote());
            m.setTemplateCode(t.getCode());
            // 口径随模板快照；数值与就绪度留待运行期评估（此时为空 = 尚未评估，界面不得编造数值）
            m.setDisabled(false);
            m.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : 0);
            metricMapper.insert(m);
            n++;
        }
        log.info("指标实例: 归属单元 {} 新增 {} 条（industry={}）", ownerOid, n, industry);
        return n;
    }

    // ==================== 驾驶舱 ====================

    private int initDashboards(String ownerOid, String ownerType, String industry) {
        int existing = dashboardMapper.countByOwnerOid(ownerOid);
        if (existing > 0) { skip("驾驶舱", ownerOid, existing); return existing; }
        List<DashboardTemplate> templates = dashboardTemplateService.findAll(industry);
        if (templates.isEmpty()) templates = dashboardTemplateService.findPlatformTemplates(industry);
        int n = 0;
        for (DashboardTemplate t : templates) {
            if (Boolean.TRUE.equals(t.getDisabled())) { skippedDisabled("驾驶舱", t.getCode()); continue; }
            Dashboard d = new Dashboard();
            d.setOwnerOid(ownerOid);
            d.setOwnerType(ownerType);
            d.setCode(t.getCode());
            d.setName(t.getName());
            d.setDescription(t.getDescription());
            d.setAudience(t.getAudience());
            d.setPageCount(t.getPageCount());
            d.setWidgets(t.getWidgets());
            d.setGeneratePolicy(t.getGeneratePolicy());
            d.setPrimaryScope(Boolean.TRUE.equals(t.getPrimaryScope()));
            // 注意：驾驶舱模板是八类里唯一没有 industry_note 列的（列在实例上保留，暂不填）
            d.setTemplateCode(t.getCode());
            d.setDisabled(false);
            d.setSortOrder(t.getSortOrder() != null ? t.getSortOrder() : 0);
            dashboardMapper.insert(d);
            n++;
        }
        log.info("驾驶舱实例: 归属单元 {} 新增 {} 条（industry={}）", ownerOid, n, industry);
        return n;
    }

    // ==================== 公共 ====================

    private void skip(String kind, String ownerOid, int existing) {
        log.debug("{} 实例已存在（{} 条），归属单元 {} 跳过初始化", kind, existing, ownerOid);
    }

    private void skippedDisabled(String kind, String code) {
        log.debug("{} 模板 {} 已被企业停用，不发给新项目", kind, code);
    }
}
