/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.service.impl;

import cn.ck.plm.base.entity.CKAttachment;
import cn.ck.plm.base.service.api.CKAttachmentService;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.entity.ChangeRequestAffectedObject;
import cn.ck.plm.change.flow.ChangeRequestFlow;
import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeRequestIssue;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeIssueLookupMapper;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.mapper.ChangeObjectLookupMapper;
import cn.ck.plm.change.mapper.ChangeRequestAffectedObjectMapper;
import cn.ck.plm.change.mapper.ChangeRequestIssueMapper;
import cn.ck.plm.change.mapper.ChangeRequestMapper;
import cn.ck.plm.change.service.api.ChangeConfigService;
import cn.ck.plm.change.service.api.ChangeRequestService;
import cn.ck.plm.process.service.api.ProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 变更请求服务实现 —— 只做"台账与流转"，不触碰任何业务对象。
 *
 * <h3>为什么这里没有处置逻辑</h3>
 * <p>ECR 只回答"为什么改"。真正改对象的是 ECN 下的变更任务，
 * 执行统一走 {@code ChangeAffectedObject} → {@code ReviseProvider}。
 * 在这里加任何修订动作，都会让"请求"与"实施"的边界重新模糊。
 */
@Service
public class ChangeRequestServiceImpl implements ChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestServiceImpl.class);

    /** 变更类型白名单（与受影响对象的 entityType 不同：这里描述"改什么"） */
    private static final Set<String> CHANGE_TYPES = new LinkedHashSet<>(List.of(
            "DESIGN", "DOCUMENT", "BOM", "PROCESS", "MATERIAL"));

    /** 优先级白名单 */
    private static final Set<String> PRIORITIES = new LinkedHashSet<>(List.of("HIGH", "MEDIUM", "LOW"));

    /** 变更分级白名单（决定审签路径：单人审批 / 会签+产品经理 / 会签+CCB） */
    private static final Set<String> CHANGE_LEVELS = new LinkedHashSet<>(List.of("QUICK", "STANDARD", "MAJOR"));

    /** 认证合规影响：无 / 需评估重测 / 需重新认证 */
    private static final Set<String> CERT_IMPACTS = new LinkedHashSet<>(List.of("NONE", "REVIEW", "YES"));

    /** 客户合同影响：无 / 需知会 / 需客户批准 */
    private static final Set<String> CUSTOMER_IMPACTS = new LinkedHashSet<>(List.of("NONE", "NOTIFY", "APPROVAL"));

    /** 在制与库存影响：无 / 待在制盘点 / 需报废返工 */
    private static final Set<String> INVENTORY_IMPACTS = new LinkedHashSet<>(List.of("NONE", "PENDING", "DISPOSAL"));

    /** 预估受影响对象类型白名单（与 ReviseProvider 同口径） */
    private static final Set<String> OBJECT_TYPES = new LinkedHashSet<>(List.of(
            "PART", "DOCUMENT", "ENG_DOCUMENT"));

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ChangeRequestMapper mapper;
    private final ChangeActivityMapper activityMapper;
    private final ChangeRequestIssueMapper issueMapper;
    private final ChangeIssueLookupMapper issueLookupMapper;
    /** 子流程查询：ECR → ECN 的关闭门槛与详情展示 */
    private final ChangeNoticeMapper noticeMapper;
    /** 编号生成：按 CHANGE_REQUEST 类型绑定的编码规则（随 platform-base 包安装） */
    private final NumberService numberService;
    /** 预估受影响对象（评审输入，不参与修订执行） */
    private final ChangeRequestAffectedObjectMapper requestAffectedMapper;
    /** 对象快照：编码 / 名称 / 所属容器一律服务端回查，不接受前端传值 */
    private final ChangeObjectLookupMapper objectLookupMapper;
    /** 免审开关与 request 流程 key */
    private final ChangeConfigService configService;
    /** 发起评审流程；流程结束的回写走 {@code ChangeRequestReviewOutcomeHandler}，不在这里 */
    private final ProcessService processService;
    /** 补充材料：表单里先上传（此时还没有 owner），保存时把附件挂到本单 */
    private final CKAttachmentService attachmentService;

    public ChangeRequestServiceImpl(                                    ChangeRequestMapper mapper, ChangeActivityMapper activityMapper,
                                   ChangeRequestIssueMapper issueMapper,
                                   ChangeIssueLookupMapper issueLookupMapper,
                                   ChangeNoticeMapper noticeMapper,
                                   NumberService numberService,
                                   ChangeConfigService configService,
                                   ProcessService processService,
                                   ChangeRequestAffectedObjectMapper requestAffectedMapper,
                                   ChangeObjectLookupMapper objectLookupMapper,
                                   CKAttachmentService attachmentService) {
        this.mapper = mapper;
        this.activityMapper = activityMapper;
        this.issueMapper = issueMapper;
        this.issueLookupMapper = issueLookupMapper;
        this.noticeMapper = noticeMapper;
        this.numberService = numberService;
        this.configService = configService;
        this.processService = processService;
        this.requestAffectedMapper = requestAffectedMapper;
        this.objectLookupMapper = objectLookupMapper;
        this.attachmentService = attachmentService;
    }

    private String tenantOid() { return TenantContext.get(); }

    // ==================== 创建 ====================

    @Override
    @Transactional
    public ChangeRequest create(ChangeRequest request) {
        if (request == null || isBlank(request.getName())) {
            throw new IllegalArgumentException("变更请求标题不能为空");
        }
        request.setName(request.getName().trim());
        request.setChangeType(normalizeChangeType(request.getChangeType()));
        request.setPriority(normalizePriority(request.getPriority()));
        // 分级决定审签路径：空值一律落到 STANDARD（有会签、但不惊动 CCB）
        request.setChangeLevel(normalizeChangeLevel(request.getChangeLevel()));
        request.setCertImpact(normalizeImpact("认证/合规影响", request.getCertImpact(), CERT_IMPACTS));
        request.setCustomerImpact(normalizeImpact("客户/合同影响", request.getCustomerImpact(), CUSTOMER_IMPACTS));
        request.setInventoryImpact(normalizeImpact("在制/库存影响", request.getInventoryImpact(), INVENTORY_IMPACTS));

        request.setOid(UUID.randomUUID().toString());
        request.setCode(nextCode());
        request.setStatus(ChangeRequestFlow.INITIAL);       // 入参 status 一律忽略
        request.setRequester(UserContext.get());            // 提出人 = 当前用户
        if (isBlank(request.getOwner())) {
            request.setOwner(request.getRequester());       // 默认责任人 = 提出人，避免"无人认领"
        }
        request.setAcceptedAt(null);
        request.setClosedAt(null);
        request.setProcessInstanceId(null);   // 入参的实例 id 一律忽略：只由「发起评审」写入
        if (request.getTenantOid() == null) request.setTenantOid(tenantOid());

        // 关联问题：去重后把第一个写进 source_issue_oid —— 那列还有索引在，
        // 不能因为有了关联表就让它空着（反查"某问题引出的变更单"仍要走它）。
        List<String> issueOids = normalizeIssueOids(request.getIssueOids(), request.getSourceIssueOid());
        // 只允许关联未关闭的问题：已闭环/已作废的问题不该再驱动一次变更
        rejectClosedIssues(issueOids, request.getTenantOid());
        request.setSourceIssueOid(issueOids.isEmpty() ? null : issueOids.get(0));
        normalizeBlanks(request);

        // 预估受影响对象：只存快照，不碰修订执行链（真正的执行清单在 ECN）
        List<ChangeRequestAffectedObject> estimates =
                resolveAffectedObjects(request.getAffectedObjectOids(), request.getTenantOid());

        mapper.insert(request);
        bindAttachment(request.getAttachmentOid(), request.getOid());
        saveIssueLinks(request.getOid(), issueOids, request.getTenantOid());
        saveAffectedObjects(request.getOid(), estimates, request.getTenantOid());
        record(request.getOid(), "CREATE", null, ChangeRequestFlow.INITIAL, "变更请求已提出");
        log.info("变更请求已创建: {}（{}）", request.getCode(), request.getName());
        return mapper.selectByOid(request.getOid());
    }

    // ==================== 编辑（patch 语义） ====================

    @Override
    @Transactional
    public ChangeRequest update(ChangeRequest patch) {
        if (patch == null || isBlank(patch.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        ChangeRequest existing = require(patch.getOid());
        if (!ChangeRequestFlow.isEditable(existing.getStatus())) {
            throw new IllegalStateException("已关闭或已取消的变更请求不可编辑");
        }
        assertOwnerOf(existing, "编辑变更请求");
        if (patch.getChangeType() != null) patch.setChangeType(normalizeChangeType(patch.getChangeType()));
        if (patch.getPriority() != null) patch.setPriority(normalizePriority(patch.getPriority()));
        if (patch.getChangeLevel() != null) {
            String level = normalizeChangeLevel(patch.getChangeLevel());
            // 审签路径由分级决定：已经发起评审就锁住，否则"在途实例走旧路径、单据写新分级"
            if (existing.getProcessInstanceId() != null && !level.equals(existing.getChangeLevel())) {
                throw new IllegalStateException("已发起评审，变更分级不可再修改");
            }
            patch.setChangeLevel(level);
        }
        if (patch.getCertImpact() != null) {
            patch.setCertImpact(normalizeImpact("认证/合规影响", patch.getCertImpact(), CERT_IMPACTS));
        }
        if (patch.getCustomerImpact() != null) {
            patch.setCustomerImpact(normalizeImpact("客户/合同影响", patch.getCustomerImpact(), CUSTOMER_IMPACTS));
        }
        if (patch.getInventoryImpact() != null) {
            patch.setInventoryImpact(normalizeImpact("在制/库存影响", patch.getInventoryImpact(), INVENTORY_IMPACTS));
        }

        // 逐字段兜底：缺席（null）= 不改
        if (patch.getName() == null) patch.setName(existing.getName());
        if (patch.getDescription() == null) patch.setDescription(existing.getDescription());
        if (patch.getAttachmentOid() == null) patch.setAttachmentOid(existing.getAttachmentOid());
        if (patch.getChangeType() == null) patch.setChangeType(existing.getChangeType());
        if (patch.getPriority() == null) patch.setPriority(existing.getPriority());
        if (patch.getReason() == null) patch.setReason(existing.getReason());
        if (patch.getProposal() == null) patch.setProposal(existing.getProposal());
        if (patch.getSourceIssueOid() == null) patch.setSourceIssueOid(existing.getSourceIssueOid());
        if (patch.getProductOid() == null) patch.setProductOid(existing.getProductOid());
        if (patch.getProductType() == null) patch.setProductType(existing.getProductType());
        if (patch.getOwner() == null) patch.setOwner(existing.getOwner());
        if (patch.getDueDate() == null) patch.setDueDate(existing.getDueDate());
        if (patch.getChangeLevel() == null) patch.setChangeLevel(existing.getChangeLevel());
        if (patch.getCertImpact() == null) patch.setCertImpact(existing.getCertImpact());
        if (patch.getCustomerImpact() == null) patch.setCustomerImpact(existing.getCustomerImpact());
        if (patch.getInventoryImpact() == null) patch.setInventoryImpact(existing.getInventoryImpact());
        // 不可经此改动：编号 / 状态 / 提出人 / 受理与关闭时间 / 流程实例 / 租户
        patch.setCode(existing.getCode());
        patch.setStatus(existing.getStatus());
        patch.setRequester(existing.getRequester());
        patch.setAcceptedAt(existing.getAcceptedAt());
        patch.setClosedAt(existing.getClosedAt());
        patch.setProcessInstanceId(existing.getProcessInstanceId());
        patch.setTenantOid(existing.getTenantOid());

        // 关联问题遵循同一套 patch 语义：null = 本次不改，空列表 = 清空
        List<String> issueOids = patch.getIssueOids() == null ? null
                : normalizeIssueOids(patch.getIssueOids(), null);
        if (issueOids != null) {
            rejectClosedIssues(issueOids, existing.getTenantOid());
            patch.setSourceIssueOid(issueOids.isEmpty() ? null : issueOids.get(0));
        }
        // 预估受影响对象同一套 patch 语义
        List<ChangeRequestAffectedObject> estimates = patch.getAffectedObjectOids() == null ? null
                : resolveAffectedObjects(patch.getAffectedObjectOids(), existing.getTenantOid());
        normalizeBlanks(patch);

        mapper.update(patch);
        bindAttachment(patch.getAttachmentOid(), patch.getOid());
        if (issueOids != null) {
            issueMapper.deleteByRequestOid(patch.getOid(), existing.getTenantOid());
            saveIssueLinks(patch.getOid(), issueOids, existing.getTenantOid());
        }
        if (estimates != null) {
            requestAffectedMapper.deleteByRequestOid(patch.getOid(), existing.getTenantOid());
            saveAffectedObjects(patch.getOid(), estimates, existing.getTenantOid());
            record(patch.getOid(), "UPDATE", null, null, "预估受影响对象已更新（" + estimates.size() + " 个）");
        }
        record(patch.getOid(), "UPDATE", null, null, "变更请求信息已更新");
        return mapper.selectByOid(patch.getOid());
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional
    public ChangeRequest transition(String oid, String action, String comment) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        ChangeRequest existing = require(oid);

        String normalized = isBlank(action) ? "" : action.trim().toUpperCase();
        String to = ChangeRequestFlow.resolve(normalized, existing.getStatus());  // 非法流转在这里拦下

        // 责任人闸门放在最前面：非责任人连"关闭门槛/必填说明"都不该看到，
        // 否则会先收到"还有 1 个变更通告未关闭"这种与他无关的提示。
        assertOwner(existing, normalized);

        // 开启审批时，影响分析是评审流程里的一步（影响分析会签），单据上不再提供人工入口 ——
        // 两条路都能把单据推到「影响分析中」，就分不清这张单到底是流程评估的，还是人点的。
        if (ChangeRequestFlow.ANALYZE.equals(normalized) && configService.get().isApprovalRequired()) {
            throw new IllegalStateException("已开启变更审批：影响分析随评审流程进行（发起评审后由流程推进），"
                    + "不需要在单据上单独开始影响分析");
        }

        // 关闭门槛：先有子流程（变更通告），再等子流程全部关闭。
        // 父单先关、子单还在改，账面上就再也看不出"这次变更到底做完没有"。
        if (ChangeRequestFlow.CLOSE.equals(normalized)) {
            // 用当前租户而不是行上的 tenant_oid：老数据的行上字段可能是空，空值会让 SQL 永远匹配不到
            // 门槛①：一个通告都没派生过 = "怎么改"从未落到执行单上，关闭等于跳过整个实施过程
            if (noticeMapper.countByRequest(oid, tenantOid()) == 0) {
                throw new IllegalStateException("尚未派发变更通告，不能关闭变更请求；请先派生变更通告并完成实施");
            }
            // 门槛②：已派生的通告必须全部关闭（CLOSED / CANCELLED）
            List<ChangeNotice> openNotices = noticeMapper.selectOpenByRequest(oid, tenantOid());
            if (!openNotices.isEmpty()) {
                throw new IllegalStateException("还有 " + openNotices.size() + " 个变更通告未关闭（"
                        + summarizeNoticeCodes(openNotices, 5) + "），不能关闭变更请求");
            }
        }

        if (ChangeRequestFlow.requiresReason(normalized) && isBlank(existing.getReason())) {
            throw new IllegalArgumentException("发起评审前必须填写变更原因（为什么改）");
        }
        if (ChangeRequestFlow.requiresComment(normalized) && isBlank(comment)) {
            throw new IllegalArgumentException("「" + ChangeRequestFlow.label(normalized) + "」必须填写说明");
        }

        // 两条支路（2026-11-02）：
        //   ① 配置了「变更需要审批」→「发起评审」拉起 change-request 流程，
        //      单据状态由流程里的「设置状态」（change.setRequestStatus）节点写：
        //      影响分析中 → 评审中 → 已受理 / 已否决；
        //   ② 免审 → 状态只由单据上的动作流转（开始影响分析 / 发起评审 / 受理 / 否决）。
        boolean processDriven = ChangeRequestFlow.SUBMIT.equals(normalized)
                && configService.get().isApprovalRequired();
        if (processDriven) {
            if (!isBlank(existing.getProcessInstanceId())) {
                throw new IllegalStateException("该变更请求已发起过评审流程（实例 "
                        + existing.getProcessInstanceId() + "），不能重复发起");
            }
        } else if (ChangeRequestFlow.SUBMIT.equals(normalized)) {
            // 免审没有流程承载影响分析，只能靠人填：重大变更必须先给出认证 / 客户结论，
            // 否则评审结论建立在猜测上。走流程时这一步由「影响分析会签」承担，不再前置卡。
            assertMajorImpactAssessed(existing);
        }

        ChangeRequest update = new ChangeRequest();
        update.setOid(oid);
        // 流程驱动的单：状态先沿用当前值 —— 下面先把单据落库、再拉起流程，
        // 流程跑到第一个等待点时「设置状态」节点已经把它改成「影响分析中 / 评审中」了；
        // 这里若照旧写 REVIEWING，整行 UPDATE 会把流程刚写的状态又冲回去。
        update.setStatus(processDriven ? existing.getStatus() : to);
        update.setAcceptedAt(ChangeRequestFlow.ACCEPT.equals(normalized)
                ? LocalDateTime.now() : existing.getAcceptedAt());
        update.setClosedAt(ChangeRequestFlow.CLOSE.equals(normalized)
                ? LocalDateTime.now() : existing.getClosedAt());
        // 其余字段原值兜底，避免 UPDATE 把没提交的字段冲掉
        update.setName(existing.getName());
        update.setDescription(existing.getDescription());
        update.setAttachmentOid(existing.getAttachmentOid());
        update.setChangeType(existing.getChangeType());
        update.setPriority(existing.getPriority());
        update.setReason(existing.getReason());
        update.setProposal(existing.getProposal());
        update.setSourceIssueOid(existing.getSourceIssueOid());
        update.setProductOid(existing.getProductOid());
        update.setProductType(existing.getProductType());
        update.setOwner(existing.getOwner());
        update.setRequester(existing.getRequester());
        update.setDueDate(existing.getDueDate());
        update.setChangeLevel(existing.getChangeLevel());
        update.setCertImpact(existing.getCertImpact());
        update.setCustomerImpact(existing.getCustomerImpact());
        update.setInventoryImpact(existing.getInventoryImpact());
        // 重新起草 = 这张单要再走一遍评审：旧实例已结束，把 id 留着会让下一次「发起评审」
        // 撞上重复发起校验（见上面的 processDriven 分支），单据从此推不动
        update.setProcessInstanceId(ChangeRequestFlow.REOPEN.equals(normalized)
                ? null : existing.getProcessInstanceId());
        update.setTenantOid(existing.getTenantOid());

        mapper.update(update);

        // 单据先落库、流程后拉起：流程里的「设置状态」节点会直接改这一行，
        // 若反过来（先跑流程再整行 UPDATE），流程写好的状态会被旧值覆盖。
        String processInstanceId = null;
        if (processDriven) {
            processInstanceId = startReviewProcess(existing);
            mapper.updateProcessInstanceId(oid, processInstanceId, UserContext.get());
        }

        ChangeRequest result = mapper.selectByOid(oid);
        // 流程驱动的单，时间线由「设置状态」节点自己记（它知道是哪个节点改的），
        // 这里再记一条就成了同一动作两条 —— 例如"发起评审"会跟着"进入评审"节点各写一次。
        if (!processDriven) {
            String activityComment = isBlank(comment) ? null : comment.trim();
            record(oid, normalized, existing.getStatus(), result != null ? result.getStatus() : to, activityComment);
        }
        log.info("变更请求 {} 流转: {} -> {}（{}）{}", existing.getCode(), existing.getStatus(),
                result != null ? result.getStatus() : to, normalized,
                processInstanceId == null ? "" : "，流程实例 " + processInstanceId);
        return result;
    }

    /**
     * 责任人闸门：除「详情」外，变更请求的所有动作只认单据上的责任人。
     *
     * <p>抛 {@link IllegalStateException} 而不是 {@link IllegalArgumentException} ——
     * Controller 把前者映射成 403、后者映射成 400，这里属于"没权限"而不是"参数错"。
     *
     * <p><b>owner 为空时退回 requester</b>：表单里责任人可以不填，若只认 owner，
     * 这类单据就再也没人能推进（死锁）。提出人至少是"对这张单负责的人"，
     * 让他兜底比让单子烂在草稿里好。
     */
    private void assertOwner(ChangeRequest request, String action) {
        if (!ChangeRequestFlow.requiresOwner(action)) {
            return;
        }
        assertOwnerOf(request, "执行「" + ChangeRequestFlow.label(action) + "」");
    }

    /**
     * 责任人口径的唯一实现：{@code owner} 为空退回 {@code requester}。
     *
     * <p>编辑 / 删除与流转共用这一处，避免三条入口各写一份判断、日后漏改。
     */
    private void assertOwnerOf(ChangeRequest request, String actionLabel) {
        String owner = isBlank(request.getOwner()) ? request.getRequester() : request.getOwner();
        String operator = UserContext.get();
        if (isBlank(owner) || !owner.equals(operator)) {
            throw new IllegalStateException("只有责任人「" + (isBlank(owner) ? "未指派" : owner)
                    + "」才能" + actionLabel + "，当前登录人「"
                    + (isBlank(operator) ? "未知" : operator) + "」只能查看详情");
        }
    }

    /**
     * 拉起变更请求（ECR）评审流程，返回流程实例 id。
     *
     * <p>{@code changeNoticeList} 显式传空集合：内置流程的「派发变更通告」是多实例子流程，
     * 集合为 {@code null} 时引擎会当成缺失变量直接失败；空集合表示本次评审不派发通告，
     * 评审通过后请求直接收口到「已受理」（通告仍可在变更通告页手工建）。
     */
    private String startReviewProcess(ChangeRequest existing) {
        String processKey = configService.get().getRequestProcessKey();
        if (isBlank(processKey)) {
            throw new IllegalStateException(
                    "未配置变更请求流程 key：请在「变更控制 → 配置」中填写，或开启免审模式");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestOid", existing.getOid());
        variables.put("requestCode", existing.getCode());
        variables.put("requestTitle", existing.getName());
        variables.put("requestReason", existing.getReason());
        // 审签路径由分级分流（流程里的排他网关读它）；影响评估一并带进去，
        // 审批人在任务详情里不必再回单据翻"认证要不要重报"
        variables.put("changeLevel", normalizeChangeLevel(existing.getChangeLevel()));
        variables.put("certImpact", existing.getCertImpact());
        variables.put("customerImpact", existing.getCustomerImpact());
        variables.put("inventoryImpact", existing.getInventoryImpact());
        variables.put("changeNoticeList", new ArrayList<>());
        try {
            return processService.startProcess(processKey, existing.getOid(), variables);
        } catch (Exception e) {
            // 常见原因是"包没装 / 流程没部署"：把 key 与下一步动作写进消息里
            throw new IllegalStateException("发起评审流程失败（key=" + processKey + "）：" + e.getMessage()
                    + "；请确认 platform-base 包已安装并部署了该流程，或在配置中开启免审模式", e);
        }
    }

    // ==================== 查询 ====================

    @Override
    public ChangeRequest detail(String oid) {
        return isBlank(oid) ? null : mapper.selectByOid(oid);
    }

    @Override
    public List<ChangeActivity> activities(String oid) {
        return activityMapper.selectByOwner("REQUEST", oid);
    }

    @Override
    public List<ChangeIssueRef> issues(String oid) {
        return isBlank(oid) ? List.of() : issueMapper.selectIssues(oid, tenantOid());
    }

    @Override
    public List<ChangeRequestAffectedObject> affectedObjects(String oid) {
        return isBlank(oid) ? List.of() : requestAffectedMapper.selectByRequest(oid, tenantOid());
    }

    /**
     * 对象候选（选择器用）。三类各取 {@code limit} 条，合并返回（≤ 3×limit）。
     *
     * <p>刻意不做跨类型统一排序：候选只用于选择，结果是"最近更新的优先"即可；
     * 真正的排序键在三张表里不统一，硬排反而会让人困惑。
     *
     * <p>{@code changeCode} 由 SQL 的 LATERAL 一并带出（该对象已在一张在途变更通告里时为那张单的
     * 编号）—— 选择器据此置灰，"处在变更中的对象不能被第二张单挑走"这条规则只在这里表达一次，
     * 前端不再自己查一遍。
     */
    @Override
    public List<Map<String, Object>> objectCandidates(String keyword, int limit, String containerOid) {
        int size = Math.min(Math.max(limit, 1), 50);
        String kw = isBlank(keyword) ? null : keyword.trim();
        // 空串一律收敛成 null：SQL 里靠 null 判断"不按产品范围过滤"
        String scope = isBlank(containerOid) ? null : containerOid.trim();
        String tenant = tenantOid();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(objectLookupMapper.searchParts(kw, tenant, scope, size));
        rows.addAll(objectLookupMapper.searchDocuments(kw, tenant, scope, size));
        rows.addAll(objectLookupMapper.searchEngDocuments(kw, tenant, scope, size));
        return rows;
    }

    /**
     * 收集关联对象：零件 → 关联文档（DESCRIBES + REFERENCE）+ 替代件。
     *
     * <p>只对零件收集：文档侧没有"关联零件"这层语义（工程文档的结构是另一套）。
     * 结果按 oid 去重 —— 同一份文档可能在多个迭代上各挂过一条、也可能既是替代件又被别的
     * 入参对象关联到，重复项在下拉里只会变成视觉噪音。
     */
    @Override
    public List<Map<String, Object>> relatedObjects(List<String> entityOids) {
        if (entityOids == null || entityOids.isEmpty()) {
            return List.of();
        }
        String tenant = tenantOid();
        Map<String, Map<String, Object>> collected = new LinkedHashMap<>();
        for (String raw : entityOids) {
            if (isBlank(raw)) {
                continue;
            }
            String oid = raw.trim();
            if (objectLookupMapper.selectPartSnapshot(oid, tenant) == null) {
                continue;
            }
            collectInto(collected, objectLookupMapper.selectRelatedDocuments(oid, tenant));
            collectInto(collected, objectLookupMapper.selectAlternateParts(oid, tenant));
        }
        return new ArrayList<>(collected.values());
    }

    /** 按 oid 去重并入结果（先到先得，relation 以先命中的那条为准） */
    private void collectInto(Map<String, Map<String, Object>> bag, List<Map<String, Object>> rows) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String oid = stringOf(row.get("oid"));
            if (!isBlank(oid)) {
                bag.putIfAbsent(oid, row);
            }
        }
    }

    // ==================== 预估受影响对象（评审输入，不参与执行） ====================

    @Override
    @Transactional
    public ChangeRequestAffectedObject addAffected(String requestOid, ChangeRequestAffectedObject affected) {
        ChangeRequest request = require(requestOid);
        if (!ChangeRequestFlow.isEditable(request.getStatus())) {
            throw new IllegalStateException("当前状态不允许修改预估受影响对象（已关闭 / 已取消）");
        }
        if (affected == null || isBlank(affected.getEntityOid())) {
            throw new IllegalArgumentException("必须选择受影响对象");
        }
        String entityOid = affected.getEntityOid().trim();
        Map<String, Object> snapshot = snapshotByOid(entityOid, request.getTenantOid());
        if (snapshot == null) {
            throw new IllegalArgumentException("对象不存在或不属于当前租户：" + entityOid);
        }
        String entityType = stringOf(snapshot.get("entityType"));
        if (!isBlank(affected.getEntityType())
                && !entityType.equalsIgnoreCase(affected.getEntityType().trim())) {
            // 客户端传了类型却与真实对象不符：说明前端的候选项已过期，直接拒绝
            throw new IllegalArgumentException("对象类型不匹配：该 oid 实际是 " + entityType);
        }
        if (requestAffectedMapper.countByRequestAndEntity(requestOid, entityType, entityOid, request.getTenantOid()) > 0) {
            throw new IllegalArgumentException("该对象已在本变更请求中，不能重复添加");
        }

        ChangeRequestAffectedObject row = toEstimate(snapshot, blankToNull(affected.getNote()));
        row.setOid(UUID.randomUUID().toString());
        row.setRequestOid(requestOid);
        row.setTenantOid(request.getTenantOid());
        row.setCreator(UserContext.get());
        requestAffectedMapper.insert(row);
        record(requestOid, "ADD_AFFECTED", null, null,
                "添加预估受影响对象：" + entityType + " " + nullToEmpty(row.getEntityCode()));
        return requestAffectedMapper.selectByOid(row.getOid(), request.getTenantOid());
    }

    @Override
    @Transactional
    public boolean removeAffected(String requestOid, String affectedOid) {
        ChangeRequest request = require(requestOid);
        if (!ChangeRequestFlow.isEditable(request.getStatus())) {
            throw new IllegalStateException("当前状态不允许修改预估受影响对象（已关闭 / 已取消）");
        }
        ChangeRequestAffectedObject row = requestAffectedMapper.selectByOid(affectedOid, request.getTenantOid());
        if (row == null || !requestOid.equals(row.getRequestOid())) {
            return false;
        }
        requestAffectedMapper.deleteByOid(affectedOid, request.getTenantOid());
        record(requestOid, "REMOVE_AFFECTED", null, null,
                "移除预估受影响对象：" + row.getEntityType() + " " + nullToEmpty(row.getEntityCode()));
        return true;
    }

    /**
     * 把 oid 列表解析为预估对象行（去重 + 快照回查）。
     *
     * <p>只认三类对象（与 ReviseProvider 同口径）；查不到直接报错，
     * 不做"跳过未知 oid"的宽松处理 —— 静默丢对象会让评审看到的影响面失真。
     */
    private List<ChangeRequestAffectedObject> resolveAffectedObjects(List<String> oids, String tenantOid) {
        List<ChangeRequestAffectedObject> rows = new ArrayList<>();
        if (oids == null || oids.isEmpty()) {
            return rows;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : oids) {
            String oid = raw == null ? "" : raw.trim();
            if (oid.isEmpty() || !seen.add(oid)) {
                continue;
            }
            Map<String, Object> snapshot = snapshotByOid(oid, tenantOid);
            if (snapshot == null) {
                throw new IllegalArgumentException("受影响对象不存在或不属于当前租户：" + oid);
            }
            rows.add(toEstimate(snapshot, null));
        }
        return rows;
    }

    /** 按 oid 在零件 / 文档 / 工程文档三张主表里找快照；不存在返回 null */
    private Map<String, Object> snapshotByOid(String oid, String tenantOid) {
        Map<String, Object> snapshot = objectLookupMapper.selectPartSnapshot(oid, tenantOid);
        if (snapshot == null) {
            snapshot = objectLookupMapper.selectDocumentSnapshot(oid, tenantOid);
        }
        if (snapshot == null) {
            snapshot = objectLookupMapper.selectEngDocumentSnapshot(oid, tenantOid);
        }
        if (snapshot == null) {
            return null;
        }
        String entityType = stringOf(snapshot.get("entityType"));
        if (!OBJECT_TYPES.contains(entityType)) {
            return null;
        }
        return snapshot;
    }

    private ChangeRequestAffectedObject toEstimate(Map<String, Object> snapshot, String note) {
        ChangeRequestAffectedObject row = new ChangeRequestAffectedObject();
        row.setEntityType(stringOf(snapshot.get("entityType")));
        row.setEntityOid(stringOf(snapshot.get("oid")));
        row.setEntityCode(stringOf(snapshot.get("code")));
        row.setEntityName(stringOf(snapshot.get("name")));
        row.setContainerOid(stringOf(snapshot.get("containerOid")));
        row.setNote(note);
        return row;
    }

    /** 批量写预估对象行；空列表直接跳过 */
    private void saveAffectedObjects(String requestOid, List<ChangeRequestAffectedObject> rows, String tenantOid) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String creator = UserContext.get();
        for (ChangeRequestAffectedObject row : rows) {
            row.setOid(UUID.randomUUID().toString());
            row.setRequestOid(requestOid);
            row.setTenantOid(tenantOid);
            row.setCreator(creator);
            requestAffectedMapper.insert(row);
        }
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public List<ChangeNotice> childNotices(String oid) {
        if (isBlank(oid)) {
            return List.of();
        }
        // 复用列表查询（未关闭优先、创建时间倒序）：子单通常个位数，展示用 200 条足够
        return noticeMapper.selectList(tenantOid(), null, oid, null, MAX_PAGE_SIZE, 0);
    }

    @Override
    public Map<String, Object> list(String status, String keyword, int page, int pageSize) {
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = isBlank(keyword) ? null : keyword.trim();
        int size = normalizePageSize(pageSize);
        int current = Math.max(page, 1);
        int offset = (current - 1) * size;

        List<ChangeRequest> rows = mapper.selectList(tenantOid(), normalizedStatus, normalizedKeyword, size, offset);
        int total = mapper.countList(tenantOid(), normalizedStatus, normalizedKeyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", current);
        result.put("pageSize", size);
        return result;
    }

    @Override
    public Map<String, Object> stats() {
        // 先把流程里定义过的状态全部铺成 0：前端要的是"每一项都在"，不是"有单的才出现"
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String status : ChangeRequestFlow.statuses()) counts.put(status, 0);
        int total = 0;
        for (Map<String, Object> row : mapper.countByStatus(tenantOid())) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            int value = cnt instanceof Number ? ((Number) cnt).intValue() : 0;
            if (status != null) counts.put(status.toString(), value);
            total += value;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("counts", counts);
        return result;
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        ChangeRequest existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        // 只有草稿能删：已经进入评审的请求是决策依据，删掉就说不清"为什么会有这次变更"
        if (!ChangeRequestFlow.DRAFT.equals(existing.getStatus())) {
            throw new IllegalStateException("只有草稿状态的变更请求可以删除（当前 " + existing.getStatus() + "）");
        }
        assertOwnerOf(existing, "删除变更请求");
        activityMapper.deleteByOwner("REQUEST", oid);
        return mapper.deleteByOid(oid) > 0;
    }

    // ==================== 内部 ====================

    /**
     * 关联问题去重并稳定排序；{@code sourceIssueOid} 非空时排到最前（兼容只传来源问题的调用方）。
     *
     * <p>顺序有意义：第一个会被写进 {@code source_issue_oid}，"来源问题"就是它。
     */
    private List<String> normalizeIssueOids(List<String> issueOids, String sourceIssueOid) {
        Set<String> unique = new LinkedHashSet<>();
        if (sourceIssueOid != null && !sourceIssueOid.trim().isEmpty()) {
            unique.add(sourceIssueOid.trim());
        }
        if (issueOids != null) {
            for (String oid : issueOids) {
                if (oid != null && !oid.trim().isEmpty()) {
                    unique.add(oid.trim());
                }
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * 手工选择的问题里若含已关闭 / 已取消的，直接拒绝。
     *
     * <p>错误消息里带编号：用户看到「ISS-0007 已关闭」才知道该去掉哪一个，
     * 只说"有问题已关闭"等于让人一个个试。
     */
    private void rejectClosedIssues(List<String> issueOids, String tenantOid) {
        if (issueOids == null || issueOids.isEmpty() || tenantOid == null) {
            return;
        }
        List<ChangeIssueRef> closed = issueLookupMapper.selectClosedIssues(issueOids, tenantOid);
        if (closed.isEmpty()) {
            return;
        }
        StringBuilder codes = new StringBuilder();
        for (ChangeIssueRef ref : closed) {
            if (codes.length() > 0) {
                codes.append("、");
            }
            codes.append(ref.getCode());
        }
        throw new IllegalArgumentException("问题 " + codes + " 已关闭或已取消，不能再关联到变更单");
    }

    /** 批量写关联行；空列表直接跳过（不产生 SQL） */
    private void saveIssueLinks(String requestOid, List<String> issueOids, String tenantOid) {
        if (issueOids == null || issueOids.isEmpty()) {
            return;
        }
        String creator = UserContext.get();
        List<ChangeRequestIssue> rows = new ArrayList<>(issueOids.size());
        for (String issueOid : issueOids) {
            rows.add(new ChangeRequestIssue(UUID.randomUUID().toString(), requestOid, issueOid, tenantOid, creator));
        }
        issueMapper.insertBatch(rows);
    }

    /** 未关闭子通告的编号摘要：最多点名 5 个，其余折成「等 N 个」—— 消息要能定位，也要能读完 */
    private static String summarizeNoticeCodes(List<ChangeNotice> notices, int max) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (ChangeNotice notice : notices) {
            if (shown >= max) {
                sb.append(" 等 ").append(notices.size()).append(" 个");
                break;
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(notice.getCode());
            shown++;
        }
        return sb.toString();
    }

    private ChangeRequest require(String oid) {
        ChangeRequest existing = mapper.selectByOid(oid);
        if (existing == null) throw new IllegalArgumentException("变更请求不存在");
        return existing;
    }

    /**
     * 取下一个编码：优先走类型绑定的编码规则（{@code CHANGE_REQUEST} → ECR-0001），
     * 规则未安装或未绑定时回退本地序列。
     *
     * <p>回退不是"兜底凑数"：编码规则随 platform-base 包装载，租户还没重装包时库中没有
     * 这条规则，此时若直接失败会让建单功能整体不可用 —— 格式与规则完全同构
     * （{@code ECR-%04d}），两者可无缝切换。
     */
    private String nextCode() {
        try {
            String generated = numberService.generateNumberForType("CHANGE_REQUEST");
            if (generated != null && !generated.trim().isEmpty()) {
                return generated;
            }
        } catch (Exception e) {
            log.warn("按类型生成变更请求编号失败，回退本地序列: {}", e.getMessage());
        }
        return String.format("ECR-%04d", mapper.maxCodeSeq(tenantOid()) + 1);
    }

    /** 统一落一条操作记录（操作人 = 当前用户，租户随行） */
    private void record(String oid, String action, String from, String to, String comment) {
        ChangeActivity activity = new ChangeActivity();
        activity.setOid(UUID.randomUUID().toString());
        activity.setChangeType("REQUEST");
        activity.setChangeOid(oid);
        activity.setAction(action);
        activity.setFromStatus(from);
        activity.setToStatus(to);
        activity.setComment(comment);
        activity.setOperator(UserContext.get());
        activity.setTenantOid(tenantOid());
        activityMapper.insert(activity);
    }

    private String normalizeChangeType(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!CHANGE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("变更类型只能是 " + CHANGE_TYPES);
        }
        return normalized;
    }

    private String normalizePriority(String value) {
        if (isBlank(value)) return "MEDIUM";
        String normalized = value.trim().toUpperCase();
        if (!PRIORITIES.contains(normalized)) {
            throw new IllegalArgumentException("优先级只能是 " + PRIORITIES);
        }
        return normalized;
    }

    /** 分级归一：空 = STANDARD（默认路径：会签但不进 CCB） */
    private String normalizeChangeLevel(String value) {
        if (isBlank(value)) return "STANDARD";
        String normalized = value.trim().toUpperCase();
        if (!CHANGE_LEVELS.contains(normalized)) {
            throw new IllegalArgumentException("变更分级只能是 " + CHANGE_LEVELS);
        }
        return normalized;
    }

    /**
     * 影响评估项归一：空 = 未评估（保留 null，界面显示"未评估"比默认成"无影响"诚实）。
     *
     * @param label 字段中文名，用于错误消息
     */
    private String normalizeImpact(String label, String value, Set<String> allowed) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(label + "只能是 " + allowed);
        }
        return normalized;
    }

    /**
     * 重大变更的影响评估门槛：认证与客户两项必须给出结论，库存可选。
     *
     * <p>为什么只卡 MAJOR：快速/标准变更通常不触及认证与客户合同，强行必填只会
     * 让人随手选个"无"，反而污染数据。
     */
    private void assertMajorImpactAssessed(ChangeRequest request) {
        if (!"MAJOR".equals(normalizeChangeLevel(request.getChangeLevel()))) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(request.getCertImpact())) missing.add("认证/合规影响");
        if (isBlank(request.getCustomerImpact())) missing.add("客户/合同影响");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "重大变更发起评审前必须完成影响分析：" + String.join("、", missing) + " 未填写");
        }
    }

    private String normalizeStatusFilter(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!ChangeRequestFlow.isValidStatus(normalized)) {
            throw new IllegalArgumentException("未知状态 '" + value + "'，取值只能是 " + ChangeRequestFlow.statuses());
        }
        return normalized;
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void normalizeBlanks(ChangeRequest request) {
        request.setDescription(blankToNull(request.getDescription()));
        request.setAttachmentOid(blankToNull(request.getAttachmentOid()));
        request.setChangeType(blankToNull(request.getChangeType()));
        request.setReason(blankToNull(request.getReason()));
        request.setProposal(blankToNull(request.getProposal()));
        request.setSourceIssueOid(blankToNull(request.getSourceIssueOid()));
        request.setProductOid(blankToNull(request.getProductOid()));
        request.setProductType(blankToNull(request.getProductType()));
        request.setOwner(blankToNull(request.getOwner()));
    }

    /**
     * 把「补充材料」这个附件挂到本单上。
     *
     * <p>上传发生在建单之前（{@code CKAttachmentController#upload} 拿不到单据 oid），那时
     * {@code ck_attachment.owner_oid} 为空；保存时补上，{@code /api/attachments/by-owner/{oid}}
     * 才能按单据反查。
     *
     * <p>只挂"本租户 + 还没有主"的附件：attachmentOid 是前端传上来的，不做这层校验的话，
     * 传一个别单的 oid 就能把别人的附件挂到自己单上。
     */
    private void bindAttachment(String attachmentOid, String requestOid) {
        if (isBlank(attachmentOid)) return;
        CKAttachment attachment = attachmentService.findByOid(attachmentOid);
        if (attachment == null || attachment.getOwnerOid() != null) return;
        if (!java.util.Objects.equals(attachment.getTenantOid(), tenantOid())) return;
        attachmentService.bindToOwner(attachmentOid, requestOid);
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
