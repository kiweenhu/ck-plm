/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.service.impl;

import cn.ck.plm.base.entity.IssueTypeTemplate;
import cn.ck.plm.base.mapper.IssueTypeTemplateMapper;
import cn.ck.plm.base.service.api.CapabilityService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.improve.entity.Issue;
import cn.ck.plm.improve.entity.IssueActivity;
import cn.ck.plm.improve.flow.IssueFlow;
import cn.ck.plm.improve.mapper.IssueActivityMapper;
import cn.ck.plm.improve.mapper.IssueMapper;
import cn.ck.plm.improve.mapper.IssueObjectLookupMapper;
import cn.ck.plm.improve.service.api.IssueService;
import cn.ck.plm.product.entity.Deliverable;
import cn.ck.plm.product.entity.Gate;
import cn.ck.plm.product.mapper.DeliverableMapper;
import cn.ck.plm.product.mapper.GateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 问题服务实现 —— 闭环规则的执行层。
 *
 * <h3>两条不让步的口径</h3>
 * <ol>
 *   <li><b>新建恒为 OPEN</b>：入参里的 status 一律忽略（状态只能经 {@link #transition} 走规则表）；</li>
 *   <li><b>关闭必须两步</b>：SUBMIT_VERIFY（提交验证）→ VERIFY_PASS（验证通过）；
 *       非法流转由 {@link IssueFlow#resolve} 拦下，消息面向用户。</li>
 * </ol>
 *
 * <h3>每一步都留痕</h3>
 * create / update / 流转 / 备注，凡改变问题状态或信息的动作都往 {@code ck_issue_activity}
 * 追加一条 —— 时间线拼出来就是"跟踪"。
 */
@Service
public class IssueServiceImpl implements IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueServiceImpl.class);

    /** 严重度（与检查规则、阶段门同一口径） */
    private static final Set<String> SEVERITIES = new LinkedHashSet<>(List.of("BLOCK", "WARN"));

    /** 来源白名单 */
    private static final Set<String> SOURCES = new LinkedHashSet<>(List.of(
            "AUDIT", "CUSTOMER", "PRODUCTION", "TEST", "OTHER"));

    /** 产品类型白名单（与 /instances 的 ownerType 同口径） */
    private static final Set<String> PRODUCT_TYPES = new LinkedHashSet<>(List.of(
            "PRODUCT_LINE", "PRODUCT_MODEL"));

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    /** 阶段门能力码（由 smart-hardware-ipd 之类的包声明；租户装了才有门可关联） */
    private static final String CAP_GATE_REVIEW = "GATE_REVIEW";

    /** 交付件的"已完成"状态码（ck_deliverable.status；由 DeliverableStatusEvaluator 重算写回） */
    private static final String DELIVERABLE_DONE = "DONE";

    private final IssueMapper mapper;
    private final IssueActivityMapper activityMapper;
    private final IssueTypeTemplateMapper typeMapper;
    private final GateMapper gateMapper;
    private final DeliverableMapper deliverableMapper;
    private final CapabilityService capabilityService;
    private final IssueObjectLookupMapper objectLookupMapper;
    private final ObjectMapper objectMapper;

    public IssueServiceImpl(IssueMapper mapper,
                            IssueActivityMapper activityMapper,
                            IssueTypeTemplateMapper typeMapper,
                            GateMapper gateMapper,
                            DeliverableMapper deliverableMapper,
                            CapabilityService capabilityService,
                            IssueObjectLookupMapper objectLookupMapper,
                            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.activityMapper = activityMapper;
        this.typeMapper = typeMapper;
        this.gateMapper = gateMapper;
        this.deliverableMapper = deliverableMapper;
        this.capabilityService = capabilityService;
        this.objectLookupMapper = objectLookupMapper;
        this.objectMapper = objectMapper;
    }

    private String tenantOid() { return TenantContext.get(); }

    // ==================== 创建 ====================

    @Override
    @Transactional
    public Issue create(Issue issue) {
        if (issue == null || isBlank(issue.getName())) {
            throw new IllegalArgumentException("问题标题不能为空");
        }
        issue.setName(issue.getName().trim());
        issue.setSeverity(normalizeSeverity(issue.getSeverity()));
        issue.setSource(normalizeSource(issue.getSource()));
        validateIssueType(issue.getIssueType());
        validateProduct(issue.getProductOid(), issue.getProductType());
        validateGate(issue.getGateOid(), issue.getProductOid());

        issue.setCode(nextCode());
        issue.setStatus(IssueFlow.INITIAL);            // 入参 status 一律忽略
        issue.setReporter(UserContext.get());          // 提出人 = 当前用户
        issue.setClosedAt(null);
        if (issue.getTenantOid() == null) issue.setTenantOid(tenantOid());
        normalizeBlanks(issue);

        mapper.insert(issue);
        record(issue.getOid(), "CREATE", null, IssueFlow.INITIAL, "问题已提出", null);
        log.info("问题已创建: {}（{}）严重度={}", issue.getCode(), issue.getName(), issue.getSeverity());
        return decorate(mapper.selectByOid(issue.getOid()));
    }

    // ==================== 编辑（patch 语义） ====================

    @Override
    @Transactional
    public Issue update(Issue patch) {
        if (patch == null || isBlank(patch.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        Issue existing = mapper.selectByOid(patch.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("问题不存在");
        }
        // 终态也允许编辑（2026-10-15 用户反馈）：闭环后经常要补填根因 / 处理措施 / 关联对象 ——
        // 这些是"记录"不是"状态"，堵住反而逼人去重开。状态 / 关闭时间 / 验证结论仍不可经此改动。
        if (patch.getSeverity() != null) patch.setSeverity(normalizeSeverity(patch.getSeverity()));
        if (patch.getSource() != null) patch.setSource(normalizeSource(patch.getSource()));
        if (patch.getIssueType() != null) validateIssueType(patch.getIssueType());

        // 逐字段兜底：缺席（null）= 不改
        if (patch.getName() == null) patch.setName(existing.getName());
        if (patch.getDescription() == null) patch.setDescription(existing.getDescription());
        if (patch.getIssueType() == null) patch.setIssueType(existing.getIssueType());
        // severity 是 NOT NULL 列：UPDATE 显式传 null 不会走 DEFAULT，会让整行更新失败 —— 必须兜底
        if (patch.getSeverity() == null) patch.setSeverity(existing.getSeverity());
        if (patch.getSource() == null) patch.setSource(existing.getSource());
        if (patch.getOwner() == null) patch.setOwner(existing.getOwner());
        if (patch.getDueDate() == null) patch.setDueDate(existing.getDueDate());
        if (patch.getProductOid() == null) patch.setProductOid(existing.getProductOid());
        if (patch.getProductType() == null) patch.setProductType(existing.getProductType());
        if (patch.getGateOid() == null) patch.setGateOid(existing.getGateOid());
        if (patch.getRelatedType() == null) patch.setRelatedType(existing.getRelatedType());
        if (patch.getRelatedCode() == null) patch.setRelatedCode(existing.getRelatedCode());
        if (patch.getRootCause() == null) patch.setRootCause(existing.getRootCause());
        if (patch.getResolution() == null) patch.setResolution(existing.getResolution());
        // 不可经此改动：编号 / 状态 / 提出人 / 关闭时间 / 验证结论 / 租户（UPDATE 语句也不含这几列）
        patch.setCode(existing.getCode());
        patch.setStatus(existing.getStatus());
        patch.setReporter(existing.getReporter());
        patch.setClosedAt(existing.getClosedAt());
        patch.setVerifyResult(existing.getVerifyResult());
        patch.setTenantOid(existing.getTenantOid());
        normalizeBlanks(patch);
        // 改动后再校验一次：产品是必填、门必须仍属于所选产品（可能刚换了产品）
        validateProduct(patch.getProductOid(), patch.getProductType());
        validateGate(patch.getGateOid(), patch.getProductOid());

        mapper.update(patch);

        String summary = changeSummary(existing, patch);
        if (isBlank(summary)) {
            summary = "问题信息已更新";
        }
        boolean ownerChanged = !equalsNullable(existing.getOwner(), patch.getOwner());
        record(patch.getOid(), ownerChanged ? "ASSIGN" : "UPDATE", null, null, summary, null);
        return decorate(mapper.selectByOid(patch.getOid()));
    }

    // ==================== 状态流转（闭环核心） ====================

    @Override
    @Transactional
    public Issue transition(String oid, String action, String comment, String verifyResult,
                            String rootCause, String resolution) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        Issue existing = mapper.selectByOid(oid);
        if (existing == null) throw new IllegalArgumentException("问题不存在");

        String normalized = isBlank(action) ? "" : action.trim().toUpperCase();
        String to = IssueFlow.resolve(normalized, existing.getStatus());   // 非法流转在这里拦下

        // 责任人类动作（开始处理 / 提交验证）只有责任人本人能点：别人代推，"谁在处理"就落不到人头上
        if (IssueFlow.requiresOwner(normalized)
                && !equalsNullable(existing.getOwner(), UserContext.get())) {
            throw new IllegalStateException("只有责任人"
                    + (isBlank(existing.getOwner()) ? "（当前未指派）" : "「" + existing.getOwner() + "」")
                    + "才能执行「" + IssueFlow.label(normalized) + "」");
        }

        if (IssueFlow.requiresComment(normalized) && isBlank(comment)) {
            throw new IllegalArgumentException(actionCommentHint(normalized));
        }
        if (IssueFlow.requiresVerifyResult(normalized) && isBlank(verifyResult)) {
            throw new IllegalArgumentException("验证通过必须填写验证结论");
        }
        if (IssueFlow.requiresRootCause(normalized) && isBlank(rootCause)) {
            throw new IllegalArgumentException("提交验证必须填写根因分析");
        }
        if (IssueFlow.requiresResolution(normalized) && isBlank(resolution)) {
            throw new IllegalArgumentException("提交验证必须填写处理措施");
        }

        Issue update = new Issue();
        update.setOid(oid);
        update.setStatus(to);
        // 其余字段原值兜底，避免 UPDATE 把没提交的字段冲掉
        update.setName(existing.getName());
        update.setDescription(existing.getDescription());
        update.setIssueType(existing.getIssueType());
        update.setSeverity(existing.getSeverity());
        update.setSource(existing.getSource());
        update.setOwner(existing.getOwner());
        update.setReporter(existing.getReporter());
        update.setDueDate(existing.getDueDate());
        update.setProductOid(existing.getProductOid());
        update.setProductType(existing.getProductType());
        update.setGateOid(existing.getGateOid());
        update.setRelatedType(existing.getRelatedType());
        update.setRelatedCode(existing.getRelatedCode());
        update.setRootCause(existing.getRootCause());
        update.setResolution(existing.getResolution());
        update.setVerifyResult(existing.getVerifyResult());
        update.setClosedAt(existing.getClosedAt());
        update.setTenantOid(existing.getTenantOid());

        if (IssueFlow.SUBMIT_VERIFY.equals(normalized)) {
            // 「提交验证」= 处理完成、请人来验 —— 正是填"根因 / 处理措施"的环节：
            // 这两项必填（校验见上），随动作一起记入，不必先去编辑
            update.setRootCause(rootCause.trim());
            update.setResolution(resolution.trim());
        }

        if (IssueFlow.VERIFY_PASS.equals(normalized)) {
            update.setVerifyResult(verifyResult.trim());
            update.setClosedAt(LocalDateTime.now());
        } else if (IssueFlow.REOPEN.equals(normalized)) {
            // 重开：关闭时间与验证结论一并清空（原结论仍在时间线里）
            update.setClosedAt(null);
            update.setVerifyResult(null);
        }
        mapper.update(update);

        // 验证通过时若没写说明，用验证结论兜底，保证时间线这条有内容可读
        String recorded = comment;
        if (isBlank(recorded) && IssueFlow.VERIFY_PASS.equals(normalized)) {
            recorded = "验证结论：" + verifyResult.trim();
        }
        record(oid, normalized, existing.getStatus(), to, recorded, null);

        if (IssueFlow.VERIFY_PASS.equals(normalized)) {
            log.info("问题已闭环关闭: {}（{}）", existing.getCode(), existing.getName());
        } else {
            log.info("问题流转: {} {} → {}（动作 {}）", existing.getCode(), existing.getStatus(), to, normalized);
        }
        return decorate(mapper.selectByOid(oid));
    }

    // ==================== 备注 ====================

    @Override
    @Transactional
    public IssueActivity addComment(String oid, String comment) {
        if (isBlank(comment)) throw new IllegalArgumentException("备注内容不能为空");
        Issue existing = mapper.selectByOid(oid);
        if (existing == null) throw new IllegalArgumentException("问题不存在");
        return record(oid, "COMMENT", null, null, comment.trim(), null);
    }

    // ==================== 查询 ====================

    @Override
    public Issue detail(String oid) {
        if (isBlank(oid)) return null;
        return decorate(mapper.selectByOid(oid));
    }

    @Override
    public List<IssueActivity> activities(String oid) {
        if (isBlank(oid)) return List.of();
        return activityMapper.selectByIssue(oid, tenantOid());
    }

    @Override
    public Map<String, Object> list(String status, String severity, String owner, String keyword,
                                    String relatedType, String relatedCode, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String tenant = tenantOid();
        String st = isBlank(status) ? null : status.trim().toUpperCase();
        String sv = isBlank(severity) ? null : severity.trim().toUpperCase();
        String ow = isBlank(owner) ? null : owner.trim();
        String kw = isBlank(keyword) ? null : keyword.trim();
        // 关联对象：类型统一大写（前端传 PART / DOCUMENT…），编码按库里原样比对
        String rt = isBlank(relatedType) ? null : relatedType.trim().toUpperCase();
        String rc = isBlank(relatedCode) ? null : relatedCode.trim();

        List<Issue> rows = mapper.selectList(tenant, st, sv, ow, kw, rt, rc, safeSize, (safePage - 1) * safeSize);
        int total = mapper.countList(tenant, st, sv, ow, kw, rt, rc);
        decorateAll(rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    @Override
    public Map<String, Object> stats() {
        String tenant = tenantOid();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : IssueFlow.statuses()) {
            counts.put(status, 0L);
        }
        long total = 0L;
        for (Map<String, Object> row : mapper.countByStatus(tenant)) {
            String status = row.get("status") == null ? null : String.valueOf(row.get("status"));
            long cnt = row.get("cnt") instanceof Number ? ((Number) row.get("cnt")).longValue() : 0L;
            if (status != null && counts.containsKey(status)) {
                counts.put(status, cnt);
            }
            total += cnt;
        }
        long closed = counts.get(IssueFlow.CLOSED);
        long cancelled = counts.get(IssueFlow.CANCELLED);
        long denominator = total - cancelled;
        Double closeRate = denominator == 0 ? null
                : Math.round(closed * 10000.0 / denominator) / 10000.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.putAll(counts);
        result.put("closeRate", closeRate);
        return result;
    }

    @Override
    public Map<String, Object> gateCheck(String ownerOid) {
        if (isBlank(ownerOid)) {
            throw new IllegalArgumentException("ownerOid 不能为空");
        }
        String owner = ownerOid.trim();
        List<Issue> open = mapper.selectOpenByProduct(tenantOid(), owner);
        decorateAll(open);

        // 交付件一次取全：每道门的 DELIVERABLE_RATE 都在内存里按「本阶段 + 未裁剪」算（一个项目约 45 条）
        List<Deliverable> deliverables = deliverableMapper.selectByOwnerOid(owner);

        List<Map<String, Object>> gates = new ArrayList<>();
        for (Gate gate : gateMapper.selectByOwnerOid(owner)) {
            List<Map<String, Object>> checks = new ArrayList<>();
            // ① ISSUE_OPEN（未闭环问题）—— 只算**挂在这道门上**的问题。
            // 登记时没选阶段门的问题（gate_oid 为空）不参与任何门的自动检查：
            // 那道门并没有被这个问题选中，算到它头上等于替人做了决定（2026-10-29 用户口径）。
            List<Issue> gateOpen = new ArrayList<>();
            for (Issue issue : open) {
                if (gate.getOid() != null && gate.getOid().equals(issue.getGateOid())) {
                    gateOpen.add(issue);
                }
            }
            long gateBlock = gateOpen.stream().filter((i) -> "BLOCK".equals(i.getSeverity())).count();
            long gateWarn = gateOpen.size() - gateBlock;
            if (issueOpenDeclared(gate)) {
                Map<String, Object> check = issueOpenCheckOf(gate);
                int maxCount = (Integer) check.get("maxCount");
                boolean ok = gateOpen.size() <= maxCount;
                check.put("actual", gateOpen.size());
                check.put("blockCount", gateBlock);
                check.put("warnCount", gateWarn);
                check.put("passed", ok);
                check.put("message", ok
                        ? "本门无未闭环问题（要求 ≤ " + maxCount + "）"
                        : "本门还有 " + gateOpen.size() + " 个未闭环问题（含 " + gateBlock + " 个阻塞），要求 ≤ " + maxCount);
                checks.add(check);
            }
            // ② DELIVERABLE_RATE（本阶段交付件完成率 —— 与交付件清单同一份状态，不另立口径）
            Map<String, Object> rateCheck = deliverableRateCheckOf(gate, deliverables);
            if (rateCheck != null) {
                checks.add(rateCheck);
            }
            if (checks.isEmpty()) {
                continue;   // 该门没声明任何"已实现"的自动检查 —— 不进结果，前端也不显示该行
            }
            boolean passed = checks.stream().allMatch((c) -> Boolean.TRUE.equals(c.get("passed")));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gateOid", gate.getOid());
            item.put("gateCode", gate.getCode());
            item.put("gateName", gate.getName());
            item.put("passed", passed);
            item.put("checks", checks);
            gates.add(item);
        }

        // 产品级汇总：给"这个产品还有多少未闭环"用（系列页列显示 / 门决策申请单带出），
        // 与逐门的判定不是同一个口径 —— 门只看挂在自己身上的，这里看全产品的。
        long blockOpen = open.stream().filter((i) -> "BLOCK".equals(i.getSeverity())).count();
        long warnOpen = open.size() - blockOpen;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerOid", owner);
        result.put("openTotal", open.size());
        result.put("blockOpen", blockOpen);
        result.put("warnOpen", warnOpen);
        // 各阶段的交付件完成率：表单按"所选门的阶段"取用（门决策申请单的自动带出）
        result.put("stageRates", stageRatesOf(deliverables));
        result.put("gates", gates);
        result.put("items", open);
        return result;
    }

    /**
     * 关联对象候选（「关联对象编码」选择器用）。
     *
     * <p>口径：只搜<b>能被关联的五类实体</b>（零组件 / 文档 / 工程数据 / 产品系列 / 产品型号）；
     * 表单里选了「关联对象类型」就只查那一类，没选就五类都查。
     * 类型/编码之外的东西（BOM / 其他）本来就只能是手写文本，不在这里凑候选。
     */
    @Override
    public List<Map<String, Object>> objectCandidates(String keyword, String type, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        String kw = isBlank(keyword) ? null : keyword.trim();
        String want = isBlank(type) ? null : type.trim().toUpperCase();

        List<Map<String, Object>> rows = new ArrayList<>();
        if (want == null || "PART".equals(want)) {
            rows.addAll(objectLookupMapper.searchParts(kw, size));
        }
        if (want == null || "DOCUMENT".equals(want)) {
            rows.addAll(objectLookupMapper.searchDocuments(kw, size));
        }
        if (want == null || "ENG_DOCUMENT".equals(want)) {
            rows.addAll(objectLookupMapper.searchEngDocuments(kw, size));
        }
        if (want == null || "PRODUCT_LINE".equals(want)) {
            rows.addAll(objectLookupMapper.searchProductLines(kw, size));
        }
        if (want == null || "PRODUCT_MODEL".equals(want)) {
            rows.addAll(objectLookupMapper.searchProductModels(kw, size));
        }
        return rows;
    }

    /**
     * 从门的 {@code auto_checks}（随包快照来的 JSON 数组）里找 ISSUE_OPEN 声明。
     *
     * @return 可变 Map（gateOid / gateCode / gateName / severity / maxCount）；
     *         没声明、或 JSON 解析失败（如实记日志，不假装有）返回 null
     */
    private Map<String, Object> issueOpenCheckOf(Gate gate) {
        JsonNode node = checkNodeOf(gate, "ISSUE_OPEN");
        if (node == null) {
            return null;
        }
        Map<String, Object> check = baseCheckOf(gate, node);
        check.put("maxCount", node.path("maxCount").asInt(0));
        return check;
    }

    /** 从门的 {@code auto_checks} 里找某类检查声明；没声明、或 JSON 解析失败（如实记日志）返回 null */
    private JsonNode checkNodeOf(Gate gate, String type) {
        if (isBlank(gate.getAutoChecks())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(gate.getAutoChecks());
            if (!root.isArray()) {
                return null;
            }
            for (JsonNode node : root) {
                if (type.equals(node.path("type").asText())) {
                    return node;
                }
            }
        } catch (Exception e) {
            log.warn("门的自动检查解析失败（gate={}）：{}", gate.getCode(), e.getMessage());
        }
        return null;
    }

    /** 该门是否声明了 ISSUE_OPEN */
    private boolean issueOpenDeclared(Gate gate) {
        return checkNodeOf(gate, "ISSUE_OPEN") != null;
    }

    /** 检查项的公共字段（门信息 + 类型 + 严重度） */
    private Map<String, Object> baseCheckOf(Gate gate, JsonNode node) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("gateOid", gate.getOid());
        check.put("gateCode", gate.getCode());
        check.put("gateName", gate.getName());
        check.put("type", node.path("type").asText());
        check.put("severity", node.path("severity").asText("WARN"));
        return check;
    }

    /**
     * DELIVERABLE_RATE（本阶段交付件完成率）求值。
     *
     * <p>口径与交付件面板**完全一致**（status 由 {@code DeliverableStatusEvaluator} 重算后写回，
     * 这里只读不算）；分母排除「本项目不适用」（{@code disabled}）与 NA —— 裁掉的条目不该算进完成率。
     * 某阶段一条都不剩时按 100% 处理：没有要交的东西，不该拿 0% 卡住门。
     */
    private Map<String, Object> deliverableRateCheckOf(Gate gate, List<Deliverable> deliverables) {
        JsonNode node = checkNodeOf(gate, "DELIVERABLE_RATE");
        if (node == null) {
            return null;
        }
        String stage = node.path("stage").asText("");
        int minPercent = node.path("minPercent").asInt(100);
        long done = 0;
        long total = 0;
        for (Deliverable d : deliverables) {
            if (!countedDeliverable(d)) continue;
            if (!stage.isEmpty() && !stage.equals(d.getStageKey())) continue;
            total++;
            if (DELIVERABLE_DONE.equals(d.getStatus())) done++;
        }
        int percent = total == 0 ? 100 : (int) Math.round(done * 100.0 / total);
        boolean passed = percent >= minPercent;
        Map<String, Object> check = baseCheckOf(gate, node);
        check.put("stage", stage);
        check.put("minPercent", minPercent);
        check.put("actual", percent);
        check.put("doneCount", done);
        check.put("totalCount", total);
        check.put("passed", passed);
        check.put("message", (passed ? "本阶段交付件完成率 " : "本阶段交付件完成率仅 ")
                + percent + "%（" + done + "/" + total + "），要求 ≥ " + minPercent + "%");
        return check;
    }

    /** 各阶段的交付件完成率（门决策申请单按"所选门的阶段"取用） */
    private List<Map<String, Object>> stageRatesOf(List<Deliverable> deliverables) {
        Map<String, long[]> acc = new LinkedHashMap<>();
        for (Deliverable d : deliverables) {
            if (!countedDeliverable(d)) continue;
            String stage = d.getStageKey() == null ? "" : d.getStageKey();
            long[] cell = acc.computeIfAbsent(stage, (k) -> new long[2]);
            cell[1]++;
            if (DELIVERABLE_DONE.equals(d.getStatus())) cell[0]++;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, long[]> e : acc.entrySet()) {
            long done = e.getValue()[0];
            long total = e.getValue()[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stageKey", e.getKey());
            row.put("percent", total == 0 ? 100 : (int) Math.round(done * 100.0 / total));
            row.put("doneCount", done);
            row.put("totalCount", total);
            list.add(row);
        }
        return list;
    }

    /** 分母口径：未裁剪、且不是 NA */
    private boolean countedDeliverable(Deliverable d) {
        return !Boolean.TRUE.equals(d.getDisabled()) && !"NA".equals(d.getStatus());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) return false;
        Issue existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        mapper.deleteByOid(oid);
        log.info("问题已删除: {}（{}）—— 提醒：时间线随记录一并删除，关闭问题请用流转而非删除",
                existing.getCode(), existing.getName());
        return true;
    }

    // ==================== 内部 ====================

    /** 编号：ISS-####（租户内递增，最大序号 +1；删除后不复用已用过的号） */
    private String nextCode() {
        return String.format("ISS-%04d", mapper.maxCodeSeq(tenantOid()) + 1);
    }

    /** 统一落一条处理记录（操作人 = 当前用户，租户随行） */
    private IssueActivity record(String issueOid, String action, String from, String to,
                                 String comment, String operator) {
        IssueActivity activity = new IssueActivity(issueOid, action);
        activity.setFromStatus(from);
        activity.setToStatus(to);
        activity.setComment(isBlank(comment) ? null : comment.trim());
        activity.setOperator(operator != null ? operator : UserContext.get());
        activity.setTenantOid(tenantOid());
        activityMapper.insert(activity);
        return activity;
    }

    /** 编辑动作的中文摘要（只写真正变了的字段，便于时间线一眼看懂） */
    private String changeSummary(Issue before, Issue after) {
        List<String> changed = new ArrayList<>();
        if (!equalsNullable(before.getName(), after.getName())) changed.add("标题");
        if (!equalsNullable(before.getDescription(), after.getDescription())) changed.add("描述");
        if (!equalsNullable(before.getIssueType(), after.getIssueType())) changed.add("类型");
        if (!equalsNullable(before.getSeverity(), after.getSeverity())) changed.add("严重度");
        if (!equalsNullable(before.getSource(), after.getSource())) changed.add("来源");
        if (!equalsNullable(before.getOwner(), after.getOwner())) changed.add("责任人");
        if (!equalsNullable(before.getDueDate(), after.getDueDate())) changed.add("计划完成日期");
        if (!equalsNullable(before.getProductOid(), after.getProductOid())
                || !equalsNullable(before.getProductType(), after.getProductType())) changed.add("关联产品");
        if (!equalsNullable(before.getGateOid(), after.getGateOid())) changed.add("关联阶段门");
        if (!equalsNullable(before.getRelatedType(), after.getRelatedType())
                || !equalsNullable(before.getRelatedCode(), after.getRelatedCode())) changed.add("关联对象");
        if (!equalsNullable(before.getRootCause(), after.getRootCause())) changed.add("根因分析");
        if (!equalsNullable(before.getResolution(), after.getResolution())) changed.add("处理措施");
        return changed.isEmpty() ? null : "更新了：" + String.join("、", changed);
    }

    /**
     * 校验关联产品：<b>必填</b> + 类型白名单。
     *
     * <p>IPD 口径：问题必须挂在某个产品系列/型号上 —— 无归属的问题统计不出"哪个项目在拖后腿"，
     * 与"问题必须结构化"是同一个理由（见 IssueTypeTemplate 类注释）。
     */
    private void validateProduct(String productOid, String productType) {
        if (isBlank(productOid)) {
            throw new IllegalArgumentException("请选择关联的产品系列或型号（问题要能按项目统计）");
        }
        if (isBlank(productType) || !PRODUCT_TYPES.contains(productType.trim().toUpperCase())) {
            throw new IllegalArgumentException(
                    "产品类型 '" + productType + "' 非法，取值只能是 " + PRODUCT_TYPES);
        }
    }

    /**
     * 校验关联阶段门：可选；给了就必须存在，且<b>属于所选产品</b> ——
     * 串数据（把 A 型号的问题挂到 B 产品的门上）在这里拦下，而不是等统计时才发现。
     * （GateMapper 的语句不显式写租户条件，由拦截器注入 —— 跨租户的门查不到，天然被挡。）
     */
    private void validateGate(String gateOid, String productOid) {
        if (isBlank(gateOid)) return;
        if (isBlank(productOid)) {
            throw new IllegalArgumentException("关联阶段门前需先选择产品系列或型号");
        }
        // 阶段门是 IPD / 门径体系的产物：能力未启用的租户（账本里没有 GATE_REVIEW，
        // 等于没装 IPD 类行业包）不该能关联门 —— 前端也据此隐藏入口，这里是真正的闸门。
        if (!capabilityService.has(CAP_GATE_REVIEW)) {
            throw new IllegalArgumentException(
                    "本租户未启用「阶段门与评审」能力（需安装 IPD 类行业包），问题不能关联阶段门");
        }
        Gate gate = gateMapper.selectByOid(gateOid.trim());
        if (gate == null) {
            throw new IllegalArgumentException("阶段门不存在");
        }
        if (!productOid.trim().equals(gate.getOwnerOid())) {
            throw new IllegalArgumentException("所选阶段门不属于该产品，请重新选择");
        }
    }

    /** 装配阶段门标签（响应字段，不入库）：前端不必再取门清单 */
    private Issue decorate(Issue issue) {
        if (issue == null || isBlank(issue.getGateOid())) return issue;
        Gate gate = gateMapper.selectByOid(issue.getGateOid());
        if (gate != null) {
            issue.setGateLabel(gate.getCode() + " " + gate.getName());
        }
        return issue;
    }

    /** 列表批量装配（页内数据量小；同门去重，避免重复查询） */
    private void decorateAll(List<Issue> issues) {
        if (issues == null || issues.isEmpty()) return;
        Map<String, String> cache = new LinkedHashMap<>();
        for (Issue issue : issues) {
            if (isBlank(issue.getGateOid())) continue;
            String label = cache.get(issue.getGateOid());
            if (label == null && !cache.containsKey(issue.getGateOid())) {
                Gate gate = gateMapper.selectByOid(issue.getGateOid());
                label = gate == null ? "" : gate.getCode() + " " + gate.getName();
                cache.put(issue.getGateOid(), label);
            }
            if (label != null && !label.isEmpty()) {
                issue.setGateLabel(label);
            }
        }
    }

    private void validateIssueType(String issueType) {
        if (isBlank(issueType)) return;
        IssueTypeTemplate template = typeMapper.selectByCode(
                issueType.trim(), tenantOid(), TenantContext.PLATFORM_TENANT_OID);
        if (template == null) {
            throw new IllegalArgumentException("问题类型 '" + issueType + "' 不存在（请先在问题类型配置里启用）");
        }
    }

    /** 严重度归一（空 → WARN；越界即拒，消息带允许取值） */
    private String normalizeSeverity(String value) {
        if (isBlank(value)) return "WARN";
        String normalized = value.trim().toUpperCase();
        if (!SEVERITIES.contains(normalized)) {
            throw new IllegalArgumentException("严重度 '" + value + "' 非法，取值只能是 " + SEVERITIES);
        }
        return normalized;
    }

    /** 来源归一（空 → null；越界即拒） */
    private String normalizeSource(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!SOURCES.contains(normalized)) {
            throw new IllegalArgumentException("来源 '" + value + "' 非法，取值只能是 " + SOURCES);
        }
        return normalized;
    }

    private String actionCommentHint(String action) {
        switch (action) {
            case IssueFlow.VERIFY_FAIL: return "验证退回必须填写退回原因";
            case IssueFlow.CANCEL: return "取消问题必须填写取消原因";
            case IssueFlow.REOPEN: return "重开问题必须填写重开原因";
            default: return "该动作必须填写说明";
        }
    }

    /** 空串 → NULL（必须在"按原值兜底"之后调用，才能区分"缺席"与"空串清空"） */
    private void normalizeBlanks(Issue issue) {
        issue.setDescription(blankToNull(issue.getDescription()));
        issue.setIssueType(blankToNull(issue.getIssueType()));
        issue.setSource(blankToNull(issue.getSource()));
        issue.setOwner(blankToNull(issue.getOwner()));
        issue.setGateOid(blankToNull(issue.getGateOid()));
        issue.setRelatedType(blankToNull(issue.getRelatedType()));
        issue.setRelatedCode(blankToNull(issue.getRelatedCode()));
        issue.setRootCause(blankToNull(issue.getRootCause()));
        issue.setResolution(blankToNull(issue.getResolution()));
        issue.setVerifyResult(blankToNull(issue.getVerifyResult()));
    }

    private static boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
