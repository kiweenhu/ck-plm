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
import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeNoticeIssue;
import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.entity.ChangeRequestAffectedObject;
import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.flow.ChangeNoticeFlow;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeAffectedObjectMapper;
import cn.ck.plm.change.mapper.ChangeNoticeIssueMapper;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.mapper.ChangeObjectLookupMapper;
import cn.ck.plm.change.mapper.ChangeRequestAffectedObjectMapper;
import cn.ck.plm.change.mapper.ChangeRequestIssueMapper;
import cn.ck.plm.change.mapper.ChangeRequestMapper;
import cn.ck.plm.change.mapper.ChangeTaskMapper;
import cn.ck.plm.change.mapper.ChangeIssueLookupMapper;
import cn.ck.plm.change.service.api.ChangeConfigService;
import cn.ck.plm.change.service.api.ChangeNoticeService;
import cn.ck.plm.change.service.api.ChangeTaskService;
import cn.ck.plm.process.service.api.ProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * 变更通告服务实现。
 *
 * <h3>免审怎么落</h3>
 * <p>{@code approvalRequired = false} 时，SUBMIT 会连走两步：
 * {@code DRAFT → SUBMITTED → APPROVED}。状态机不因此分叉 —— 所有通告都经过
 * {@code APPROVED}，下游无需判断"这张单有没有审批过"；差别只写在数据上。
 *
 * <h3>审批怎么接</h3>
 * <p>{@code START_APPROVAL} 调 {@link ProcessService#startProcess} 拉起
 * {@code change-notice} 流程，单据进入 {@code APPROVING} 并记下实例 id；
 * 流程结束时由 {@code ChangeNoticeApprovalOutcomeHandler} 按 {@code approved} 变量回写
 * {@code APPROVED / REJECTED}。
 *
 * <p>流程定义随 platform-base 包装载 —— 没装包时发起会失败，错误里直接说明
 * "装包或开启免审"，而不是留下一张永远停在审批中、没人能推的单。
 */
@Service
public class ChangeNoticeServiceImpl implements ChangeNoticeService {

    private static final Logger log = LoggerFactory.getLogger(ChangeNoticeServiceImpl.class);

    /** 受影响对象类型白名单（与 CheckoutProvider / ReviseProvider 同口径） */
    private static final Set<String> ENTITY_TYPES = new LinkedHashSet<>(List.of(
            "PART", "DOCUMENT", "ENG_DOCUMENT"));

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ChangeNoticeMapper mapper;
    private final ChangeRequestMapper requestMapper;
    private final ChangeTaskMapper taskMapper;
    private final ChangeAffectedObjectMapper affectedMapper;
    private final ChangeActivityMapper activityMapper;
    private final ChangeConfigService configService;
    private final ChangeNoticeIssueMapper noticeIssueMapper;
    private final ChangeRequestIssueMapper requestIssueMapper;
    private final ChangeIssueLookupMapper issueLookupMapper;
    /** 发起审批流程。流程结束的回写走 {@code ChangeNoticeApprovalOutcomeHandler}，不在这里 */
    private final ProcessService processService;
    /** 来源请求的预估受影响对象：建单时带入执行清单 */
    private final ChangeRequestAffectedObjectMapper requestAffectedMapper;
    /** 对象快照：编码 / 名称 / 所属容器 / 当前版本一律服务端回查 */
    private final ChangeObjectLookupMapper objectLookupMapper;
    /** 编号生成：按 CHANGE_NOTICE 类型绑定的编码规则（随 platform-base 包安装） */
    private final NumberService numberService;
    /** 补充材料：表单里先上传（此时还没有 owner），保存时把附件挂到本单 */
    private final CKAttachmentService attachmentService;
    /** 更改事项清单的派发：免审通过时由本类直接触发（走流程的由审批回调触发） */
    private final ChangeTaskService taskService;

    public ChangeNoticeServiceImpl(ChangeNoticeMapper mapper,
                                   ChangeRequestMapper requestMapper,
                                   ChangeTaskMapper taskMapper,
                                   ChangeAffectedObjectMapper affectedMapper,
                                   ChangeActivityMapper activityMapper,
                                   ChangeConfigService configService,
                                   ChangeNoticeIssueMapper noticeIssueMapper,
                                   ChangeRequestIssueMapper requestIssueMapper,
                                   ChangeIssueLookupMapper issueLookupMapper,
                                   ProcessService processService,
                                   NumberService numberService,
                                   ChangeRequestAffectedObjectMapper requestAffectedMapper,
                                   ChangeObjectLookupMapper objectLookupMapper,
                                   CKAttachmentService attachmentService,
                                   ChangeTaskService taskService) {
        this.mapper = mapper;
        this.requestMapper = requestMapper;
        this.taskMapper = taskMapper;
        this.affectedMapper = affectedMapper;
        this.activityMapper = activityMapper;
        this.configService = configService;
        this.noticeIssueMapper = noticeIssueMapper;
        this.requestIssueMapper = requestIssueMapper;
        this.issueLookupMapper = issueLookupMapper;
        this.processService = processService;
        this.numberService = numberService;
        this.requestAffectedMapper = requestAffectedMapper;
        this.objectLookupMapper = objectLookupMapper;
        this.attachmentService = attachmentService;
        this.taskService = taskService;
    }

    private String tenantOid() { return TenantContext.get(); }

    // ==================== 创建 / 编辑 ====================

    @Override
    @Transactional
    public ChangeNotice create(ChangeNotice notice) {
        if (notice == null) {
            throw new IllegalArgumentException("变更通告不能为空");
        }
        ChangeRequest source = isBlank(notice.getRequestOid())
                ? null : requestMapper.selectByOid(notice.getRequestOid());
        if (!isBlank(notice.getRequestOid()) && source == null) {
            throw new IllegalArgumentException("来源变更请求不存在");
        }
        // 标题：留空且来源是变更请求时沿用请求标题。
        // 前端选了来源就即时带入（用户能看见、能改），这里兜住 API 直调与前端漏填 ——
        // 派生单的标题本来就和请求一样，让人再打一遍没有意义。
        if (isBlank(notice.getName())) {
            if (source == null || isBlank(source.getName())) {
                throw new IllegalArgumentException("变更通告标题不能为空");
            }
            notice.setName(source.getName().trim());
        } else {
            notice.setName(notice.getName().trim());
        }
        // 来源请求的默认值：定性信息 / 计划完成 / 责任人。前端也会即时带入，这里兜住 API 直调与前端漏填
        if (source != null) {
            // 类型 / 分级 / 优先级 / 三项影响 / 原因 = ECR 的判定结论，派生单继承一份快照。
            // 前端把这几项锁成只读：判定只在 ECR 上做一次，两张单各说一套会让审批路径失去意义。
            if (isBlank(notice.getChangeType())) notice.setChangeType(source.getChangeType());
            if (isBlank(notice.getChangeLevel())) notice.setChangeLevel(source.getChangeLevel());
            if (isBlank(notice.getPriority())) notice.setPriority(source.getPriority());
            if (isBlank(notice.getCertImpact())) notice.setCertImpact(source.getCertImpact());
            if (isBlank(notice.getCustomerImpact())) notice.setCustomerImpact(source.getCustomerImpact());
            if (isBlank(notice.getInventoryImpact())) notice.setInventoryImpact(source.getInventoryImpact());
            if (isBlank(notice.getReason())) notice.setReason(source.getReason());
            // 执行方案不是判定结论：以 ECR 的建议方案为起点，通告上可以改写成"这次具体怎么做"
            if (isBlank(notice.getProposal())) notice.setProposal(source.getProposal());
            // 补充说明：请求里的说明就是"为什么要改"，通告上再打一遍没有意义
            if (isBlank(notice.getDescription())) notice.setDescription(source.getDescription());
            if (notice.getPlanEnd() == null) {
                notice.setPlanEnd(source.getDueDate());
            }
            if (isBlank(notice.getOwner()) && !isBlank(source.getOwner())) {
                notice.setOwner(source.getOwner());
            }
            // 产品归属同样从来源请求继承：ECR 已声明"改哪个产品"，派生的 ECN 不该丢
            // （直接开的 ECN 没有来源，productOid 由表单自己选）
            if (isBlank(notice.getProductOid()) && !isBlank(source.getProductOid())) {
                notice.setProductOid(source.getProductOid());
                if (isBlank(notice.getProductType())) {
                    notice.setProductType(source.getProductType());
                }
            }
        }
        validatePlan(notice.getPlanStart(), notice.getPlanEnd());

        notice.setOid(UUID.randomUUID().toString());
        notice.setCode(nextCode());
        notice.setStatus(ChangeNoticeFlow.INITIAL);          // 入参 status 一律忽略
        if (isBlank(notice.getOwner())) notice.setOwner(UserContext.get());
        notice.setProcessInstanceId(null);
        notice.setApprovedAt(null);
        notice.setCompletedAt(null);
        notice.setClosedAt(null);
        notice.setApprovalBypassed(false);
        if (notice.getTenantOid() == null) notice.setTenantOid(tenantOid());

        // 关联问题：
        //   ① 手工指定 → 只接受未关闭的，含已关闭直接报错（让人当场改，而不是悄悄丢掉）；
        //   ② 没指定但来源是 ECR → 把 ECR 关联的问题带过来，已关闭的在查询里就过滤掉
        //      （请求可能是几个月前提的，期间某个问题闭环很正常，不该卡住新通告）。
        List<String> issueOids;
        if (notice.getIssueOids() != null) {
            issueOids = normalizeIssueOids(notice.getIssueOids());
            rejectClosedIssues(issueOids, notice.getTenantOid());
        } else if (!isBlank(notice.getRequestOid())) {
            issueOids = requestIssueMapper.selectOpenIssueOids(notice.getRequestOid(), notice.getTenantOid());
        } else {
            issueOids = new ArrayList<>();
        }
        normalizeBlanks(notice);

        mapper.insert(notice);
        bindAttachment(notice.getAttachmentOid(), notice.getOid());
        saveIssueLinks(notice.getOid(), issueOids, notice.getTenantOid());
        // 变更对象：显式传了（哪怕空列表）就以传的为准 —— 空列表 = 用户在表单里清空了；
        // 完全没传才按来源请求的预估对象带入（API 直调场景）
        if (notice.getAffectedObjectOids() != null) {
            saveAffectedFromOids(notice, notice.getAffectedObjectOids());
        } else {
            copyEstimatesFromRequest(notice);
        }
        record(notice.getOid(), "CREATE", null, ChangeNoticeFlow.INITIAL, "变更通告已创建");
        log.info("变更通告已创建: {}（{}）", notice.getCode(), notice.getName());
        return mapper.selectByOid(notice.getOid());
    }

    @Override
    @Transactional
    public ChangeNotice update(ChangeNotice patch) {
        if (patch == null || isBlank(patch.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        ChangeNotice existing = require(patch.getOid());
        if (!ChangeNoticeFlow.isEditable(existing.getStatus())) {
            throw new IllegalStateException("只有草稿或已驳回的变更通告可以编辑");
        }
        assertOwnerOf(existing, "编辑变更通告");
        if (!isBlank(patch.getRequestOid()) && requestMapper.selectByOid(patch.getRequestOid()) == null) {
            throw new IllegalArgumentException("来源变更请求不存在");
        }

        ChangeNotice target = copyOf(existing);
        if (patch.getName() != null) {
            if (patch.getName().trim().isEmpty()) throw new IllegalArgumentException("标题不能为空");
            target.setName(patch.getName().trim());
        }
        if (patch.getDescription() != null) target.setDescription(patch.getDescription());
        if (patch.getAttachmentOid() != null) target.setAttachmentOid(patch.getAttachmentOid());
        if (patch.getRequestOid() != null) target.setRequestOid(patch.getRequestOid());
        // 所属容器：null = 本次不改，空串 = 清空（normalizeBlanks 统一转 null）
        if (patch.getProductOid() != null) target.setProductOid(patch.getProductOid());
        if (patch.getProductType() != null) target.setProductType(patch.getProductType());
        if (patch.getPlanStart() != null) target.setPlanStart(patch.getPlanStart());
        if (patch.getPlanEnd() != null) target.setPlanEnd(patch.getPlanEnd());
        if (patch.getOwner() != null) target.setOwner(patch.getOwner());
        // 定性信息：派生单在表单上只读，这里仍按 patch 语义接收 ——
        // 老通告早于这组字段，空值需要允许补录；真正的"判定只做一次"由表单锁定表达
        if (patch.getChangeType() != null) target.setChangeType(patch.getChangeType());
        if (patch.getChangeLevel() != null) target.setChangeLevel(patch.getChangeLevel());
        if (patch.getPriority() != null) target.setPriority(patch.getPriority());
        if (patch.getCertImpact() != null) target.setCertImpact(patch.getCertImpact());
        if (patch.getCustomerImpact() != null) target.setCustomerImpact(patch.getCustomerImpact());
        if (patch.getInventoryImpact() != null) target.setInventoryImpact(patch.getInventoryImpact());
        if (patch.getReason() != null) target.setReason(patch.getReason());
        if (patch.getProposal() != null) target.setProposal(patch.getProposal());
        validatePlan(target.getPlanStart(), target.getPlanEnd());
        normalizeBlanks(target);

        // null = 本次不改关联，空列表 = 清空（与 ECR 侧同一套 patch 语义）
        List<String> issueOids = patch.getIssueOids() == null ? null : normalizeIssueOids(patch.getIssueOids());
        if (issueOids != null) {
            rejectClosedIssues(issueOids, existing.getTenantOid());
        }

        mapper.update(target);
        bindAttachment(target.getAttachmentOid(), target.getOid());
        if (issueOids != null) {
            noticeIssueMapper.deleteByNoticeOid(patch.getOid(), existing.getTenantOid());
            saveIssueLinks(patch.getOid(), issueOids, existing.getTenantOid());
        }
        record(patch.getOid(), "UPDATE", null, null, "变更通告信息已更新");
        return mapper.selectByOid(patch.getOid());
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional
    public ChangeNotice transition(String oid, String action, String comment) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        ChangeNotice existing = require(oid);

        String normalized = isBlank(action) ? "" : action.trim().toUpperCase();
        String to = ChangeNoticeFlow.resolve(normalized, existing.getStatus());   // 非法流转在这里拦下

        // 责任人闸门放在最前：非责任人不必看到"受影响对象未执行完"这类与他无关的门槛提示
        assertOwner(existing, normalized);

        ChangeNotice target = copyOf(existing);

        if (ChangeNoticeFlow.START_APPROVAL.equals(normalized)) {
            return startApproval(existing, normalized, comment);
        }

        if (ChangeNoticeFlow.SUBMIT.equals(normalized)) {
            if (affectedMapper.selectByNotice(oid).isEmpty()) {
                throw new IllegalArgumentException("提交前至少添加一个受影响对象");
            }
            boolean bypass = !configService.get().isApprovalRequired();
            if (bypass) {
                if (isBlank(comment)) {
                    throw new IllegalArgumentException("免审提交必须填写免审原因（会记入审计）");
                }
                target.setStatus(ChangeNoticeFlow.APPROVED);
                target.setApprovedAt(LocalDateTime.now());
                target.setApprovalBypassed(true);
                target.setBypassReason(comment.trim());
                target.setBypassedBy(UserContext.get());
                target.setBypassedAt(LocalDateTime.now());
                mapper.update(target);
                // 两步都留痕：先"已提交"，再"免审通过"
                record(oid, ChangeNoticeFlow.SUBMIT, existing.getStatus(), ChangeNoticeFlow.SUBMITTED, comment);
                record(oid, ChangeNoticeFlow.APPROVE, ChangeNoticeFlow.SUBMITTED, ChangeNoticeFlow.APPROVED,
                        "免审通过：" + comment.trim());
                // 免审不产生分叉：走流程的单在回调里派发，免审的在这里派发
                taskService.dispatchByNotice(oid);
                log.warn("变更通告 {} 免审通过（{}）", existing.getCode(), comment.trim());
                return mapper.selectByOid(oid);
            }
            // 需要审批时，"提交"这一步就是提交审批（2026-11-03 用户口径：
            // *"如果配置了变更需要审批则应该是提交审批"*）：
            // 原来要再点一次「发起审批」才真正起流程，中间那个「已提交」对用户没有决策价值，
            // 只是多点一次，而且很容易停在"已提交"没人管。状态机保留 SUBMITTED
            // 只为兼容存量单据（它们的「发起审批」按钮仍然可用）。
            return startApproval(existing, ChangeNoticeFlow.SUBMIT, comment);
        }

        if (ChangeNoticeFlow.REJECT.equals(normalized)
                || ChangeNoticeFlow.REVISE.equals(normalized)
                || ChangeNoticeFlow.CANCEL.equals(normalized)) {
            if (isBlank(comment)) {
                throw new IllegalArgumentException("「" + ChangeNoticeFlow.label(normalized) + "」必须填写说明");
            }
            target.setStatus(to);
            if (ChangeNoticeFlow.REVISE.equals(normalized)) {
                // 回到草稿是为了改完重新提交，而重新提交要发起**新的**流程实例：
                // 留着旧实例 id，下一次 START_APPROVAL 会被"已发起过审批"拦住。
                target.setProcessInstanceId(null);
            }
        } else if (ChangeNoticeFlow.APPROVE.equals(normalized)) {
            target.setStatus(to);
            target.setApprovedAt(LocalDateTime.now());
        } else if (ChangeNoticeFlow.COMPLETE.equals(normalized)) {
            int pending = affectedMapper.countPending(oid);
            if (pending > 0) {
                throw new IllegalStateException("还有 " + pending + " 个受影响对象未执行完毕，不能标记完成");
            }
            target.setStatus(to);
            target.setCompletedAt(LocalDateTime.now());
        } else if (ChangeNoticeFlow.CLOSE.equals(normalized)) {
            // 关闭门槛：子流程（变更任务）必须先全部完成。
            // 任务代表"具体改哪些对象、谁改、验没验收"，跳过它直接关通告等于把实施过程抹掉。
            List<ChangeTask> openTasks = taskMapper.selectOpenByNotice(oid);
            if (!openTasks.isEmpty()) {
                throw new IllegalStateException("还有 " + openTasks.size() + " 个变更任务未完成（"
                        + summarizeTaskCodes(openTasks, 5) + "），不能关闭变更通告");
            }
            target.setStatus(to);
            target.setClosedAt(LocalDateTime.now());
        } else {
            target.setStatus(to);
        }

        mapper.update(target);
        record(oid, normalized, existing.getStatus(), to, isBlank(comment) ? null : comment.trim());

        if (ChangeNoticeFlow.CANCEL.equals(normalized)) {
            // 取消必须释放占用，否则对象会一直"被这张单锁着"
            affectedMapper.releaseByNotice(oid);
            // 单据作废了，流程不能继续跑：留着实例会接着往下派任务，而它指向的单据已经取消。
            // 状态已先落库，回调看到 CANCELLED 会按幂等跳过，不会把单据改回审批结论。
            terminateProcessQuietly(existing.getProcessInstanceId(), "变更通告已取消");
        }
        log.info("变更通告 {} 流转: {} -> {}（{}）", existing.getCode(), existing.getStatus(), to, normalized);
        return mapper.selectByOid(oid);
    }

    /**
     * 拉起审批流程：状态置「审批中」并记下流程实例。
     *
     * <p>两条路径共用一段（差异只留在时间线的动作名上）：
     * <ul>
     *   <li>{@code SUBMIT}：租户配置为"需要审批"时，提交即提交审批（DRAFT → APPROVING）；</li>
     *   <li>{@code START_APPROVAL}：存量单据停在「已提交」时的补点入口（SUBMITTED → APPROVING）。</li>
     * </ul>
     *
     * <p>失败最常见的是"包没装 / 流程没部署"，异常消息里带上 key 与下一步动作 ——
     * 否则用户只看到一句引擎的英文异常，不知道该找谁。
     */
    private ChangeNotice startApproval(ChangeNotice existing, String action, String comment) {
        if (!isBlank(existing.getProcessInstanceId())) {
            throw new IllegalStateException(
                    "该变更通告已发起过审批（实例 " + existing.getProcessInstanceId() + "），不能重复发起");
        }
        String processKey = configService.get().getApprovalProcessKey();
        if (isBlank(processKey)) {
            throw new IllegalStateException("未配置审批流程 key：请在「变更控制 → 配置」中填写，或开启免审模式");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("noticeOid", existing.getOid());
        variables.put("noticeCode", existing.getCode());
        variables.put("noticeTitle", existing.getName());
        // 审签路径与变更请求同口径（流程里的排他网关读它）：快速 = 单人批实施方案，
        // 标准 = 会签 + 实施方案审批，重大 = 会签 + CCB 决策。派生单的分级继承自来源 ECR，
        // 判定只在 ECR 上做一次 —— 这里只归一，不重新定级。
        variables.put("changeLevel", normalizeChangeLevel(existing.getChangeLevel()));
        // 来源标识（不参与分支：两种企业模式各走一张流程模板，二选一由租户配置决定）。
        // 作用在审批页 —— 派生单能一眼看出"判定结论来自 ECR-0004"，不必再回列表里翻。
        boolean fromRequest = !isBlank(existing.getRequestOid());
        variables.put("fromRequest", fromRequest);
        if (fromRequest) {
            ChangeRequest source = requestMapper.selectByOid(existing.getRequestOid());
            variables.put("requestOid", existing.getRequestOid());
            variables.put("requestCode", source == null ? "" : source.getCode());
        }
        String instanceId;
        try {
            instanceId = processService.startProcess(processKey, existing.getOid(), variables);
        } catch (Exception e) {
            throw new IllegalStateException("发起审批流程失败（key=" + processKey + "）：" + e.getMessage()
                    + "；请确认 platform-base 包已安装并部署了该流程，或在配置中开启免审模式", e);
        }
        ChangeNotice target = copyOf(existing);
        target.setStatus(ChangeNoticeFlow.APPROVING);
        target.setProcessInstanceId(instanceId);
        mapper.update(target);
        record(existing.getOid(), action, existing.getStatus(), ChangeNoticeFlow.APPROVING,
                isBlank(comment) ? "提交审批" : comment.trim());
        log.info("变更通告 {} 已发起审批: instance={} key={} 来源={}", existing.getCode(), instanceId, processKey,
                fromRequest ? "变更请求 " + existing.getRequestOid() : "独立发起");
        return mapper.selectByOid(existing.getOid());
    }

    /**
     * 变更分级归一：空值或脏值一律落到 {@code STANDARD}。
     *
     * <p>为什么脏值不抛：分级是 ECR 上的判定结论，派生单只是继承一份快照
     * （见 {@code ChangeRequestServiceImpl#normalizeChangeLevel} 的口径）。
     * 为一个历史脏值把「提交审批」整条路堵死并不划算，而落 {@code STANDARD}
     * 意味着走「会签 + 实施方案审批」—— 多审一道，不会比设计意图更松。
     */
    private String normalizeChangeLevel(String value) {
        if (isBlank(value)) {
            return "STANDARD";
        }
        String normalized = value.trim().toUpperCase();
        return "QUICK".equals(normalized) || "MAJOR".equals(normalized) ? normalized : "STANDARD";
    }

    // ==================== 管理员设置状态 ====================

    /**
     * 管理员直接设置状态（运维纠错）。
     *
     * <p>什么时候用：单据被卡住而正常流程推不动 —— 审批在外部系统里挂了、流程实例丢了、
     * 审批已经线下完成但回写失败。它<b>跳过</b>流转规则与门槛校验（受影响对象是否执行完、
     * 子任务是否关闭），因此必须留下能追溯的痕迹：谁、从哪到哪、为什么。
     *
     * <p>仍然守两条底线：
     * <ul>
     *   <li>不能设成 {@code APPROVING}：那需要真实流程实例，硬设会让单据等一个永远不会来的回调；</li>
     *   <li>离开审批/通过态时先终止流程实例：否则外部流程继续跑，回调回来会把状态改回审批结论。</li>
     * </ul>
     */
    @Override
    @Transactional
    public ChangeNotice setStatusByAdmin(String oid, String status, String reason) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        if (isBlank(status)) throw new IllegalArgumentException("目标状态不能为空");
        if (isBlank(reason)) throw new IllegalArgumentException("设置状态必须填写原因（会记入处理记录）");

        String target = status.trim().toUpperCase();
        if (!ChangeNoticeFlow.isValidStatus(target)) {
            throw new IllegalArgumentException("未知状态：" + status);
        }
        if (ChangeNoticeFlow.APPROVING.equals(target)) {
            throw new IllegalArgumentException("「审批中」只能由审批流程进入，不能手工设置");
        }
        ChangeNotice existing = require(oid);
        if (target.equals(existing.getStatus())) {
            throw new IllegalArgumentException("单据当前就是 " + target + "，不需要设置");
        }

        ChangeNotice patch = copyOf(existing);
        patch.setStatus(target);
        // 状态被人为改走时，外部流程不能继续跑：回调回来看到的已不是它期待的状态
        terminateProcessQuietly(existing.getProcessInstanceId(), "管理员将变更通告状态改为 " + target);
        // 这两个状态之后还要能重新提交，清掉实例 id 才不会被"已发起过审批"拦住
        if (ChangeNoticeFlow.DRAFT.equals(target) || ChangeNoticeFlow.REJECTED.equals(target)) {
            patch.setProcessInstanceId(null);
        }
        // 时间戳按目标状态补齐：报表与"多久没关"的统计都靠它们
        if (ChangeNoticeFlow.APPROVED.equals(target) && patch.getApprovedAt() == null) {
            patch.setApprovedAt(LocalDateTime.now());
        }
        if (ChangeNoticeFlow.COMPLETED.equals(target) && patch.getCompletedAt() == null) {
            patch.setCompletedAt(LocalDateTime.now());
        }
        if (ChangeNoticeFlow.CLOSED.equals(target) && patch.getClosedAt() == null) {
            patch.setClosedAt(LocalDateTime.now());
        }
        // 目标不再是"进行中"：释放对象占用，别让那些对象一直显示"被这张单占着"
        if (!ChangeNoticeFlow.isActive(target)) {
            affectedMapper.releaseByNotice(oid);
        }

        mapper.update(patch);
        record(oid, ChangeNoticeFlow.ADMIN_SET_STATUS, existing.getStatus(), target, reason.trim());
        log.warn("变更通告 {} 被 {} 强制设置状态: {} -> {}（{}）",
                existing.getCode(), UserContext.get(), existing.getStatus(), target, reason.trim());
        return mapper.selectByOid(oid);
    }

    // ==================== 查询 ====================

    @Override
    public ChangeNotice detail(String oid) {
        return isBlank(oid) ? null : mapper.selectByOid(oid);
    }

    @Override
    public Map<String, Object> detailBundle(String oid) {
        ChangeNotice notice = require(oid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notice", notice);
        result.put("request", isBlank(notice.getRequestOid())
                ? null : requestMapper.selectByOid(notice.getRequestOid()));
        result.put("affectedObjects", affectedMapper.selectByNotice(oid));
        // 来源请求的预估对象：ECN 建单时已默认带入执行清单，这里保留原样供对照（"预估 vs 执行"）
        result.put("requestAffectedObjects", isBlank(notice.getRequestOid())
                ? List.of()
                : requestAffectedMapper.selectByRequest(notice.getRequestOid(), tenantOid()));
        result.put("tasks", taskMapper.selectByNotice(oid));
        // 关联问题：从 ECR 转来的通告会在这里看到带过来的那几条
        result.put("issues", noticeIssueMapper.selectIssues(oid, tenantOid()));
        result.put("activities", activityMapper.selectByOwner("NOTICE", oid));
        return result;
    }

    @Override
    public List<ChangeActivity> activities(String oid) {
        return activityMapper.selectByOwner("NOTICE", oid);
    }

    @Override
    public Map<String, Object> list(String status, String requestOid, String keyword, int page, int pageSize) {
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedRequest = isBlank(requestOid) ? null : requestOid.trim();
        String normalizedKeyword = isBlank(keyword) ? null : keyword.trim();
        int size = normalizePageSize(pageSize);
        int current = Math.max(page, 1);
        int offset = (current - 1) * size;

        List<ChangeNotice> rows = mapper.selectList(tenantOid(), normalizedStatus, normalizedRequest,
                normalizedKeyword, size, offset);
        int total = mapper.countList(tenantOid(), normalizedStatus, normalizedRequest, normalizedKeyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", current);
        result.put("pageSize", size);
        return result;
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String status : ChangeNoticeFlow.statuses()) counts.put(status, 0);
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

    @Override
    public List<ChangeAffectedObject> affectedObjects(String oid) {
        return affectedMapper.selectByNotice(oid);
    }

    @Override
    public List<Map<String, Object>> historyByEntity(String entityType, String entityOid) {
        if (entityType == null || entityType.isBlank() || entityOid == null || entityOid.isBlank()) {
            return List.of();
        }
        String type = entityType.trim().toUpperCase();
        if (!ENTITY_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的对象类型：" + entityType
                    + "（仅 PART / DOCUMENT / ENG_DOCUMENT）");
        }
        return affectedMapper.selectNoticesByEntity(type, entityOid.trim());
    }

    // ==================== 受影响对象 ====================

    @Override
    @Transactional
    public ChangeAffectedObject addAffected(String noticeOid, ChangeAffectedObject affected) {
        ChangeNotice notice = require(noticeOid);
        if (!ChangeNoticeFlow.isAffectedEditable(notice.getStatus())) {
            throw new IllegalStateException("当前状态不允许修改受影响对象（仅草稿或已驳回状态可改）");
        }
        if (affected == null) throw new IllegalArgumentException("受影响对象不能为空");

        String entityType = normalizeEntityType(affected.getEntityType());
        if (isBlank(affected.getEntityOid())) {
            throw new IllegalArgumentException("必须选择受影响对象");
        }
        if (isBlank(affected.getBaseIterationOid())) {
            throw new IllegalArgumentException("必须指定受影响的具体版本（变更作用在版本上，不是对象上）");
        }
        if (isBlank(affected.getBaseVersion())) {
            throw new IllegalArgumentException("受影响版本号不能为空");
        }
        // 编码 / 名称 / 所属容器以对象主表为准：手工录入的可能是旧值或缺容器
        // （对象查不到时保留入参，兼容"选择器接入前"的手工录入）
        Map<String, Object> snapshot = snapshotOf(entityType, affected.getEntityOid().trim(), notice.getTenantOid());
        if (snapshot != null) {
            affected.setEntityOid(stringOf(snapshot.get("oid")));
            affected.setEntityCode(stringOf(snapshot.get("code")));
            affected.setEntityName(stringOf(snapshot.get("name")));
            affected.setContainerOid(stringOf(snapshot.get("containerOid")));
        }
        if (affectedMapper.countByNoticeAndEntity(noticeOid, entityType, affected.getEntityOid()) > 0) {
            throw new IllegalArgumentException("该对象已在本变更中，不能重复添加");
        }

        affected.setOid(UUID.randomUUID().toString());
        affected.setNoticeOid(noticeOid);
        affected.setEntityType(entityType);
        // 归属的更改事项（= 变更任务）：必须属于同一张通告，否则"这个对象谁改"就串单了
        affected.setTaskOid(normalizeTaskOid(noticeOid, affected.getTaskOid()));
        affected.setLocked(false);
        affected.setResultIterationOid(null);
        affected.setResultVersion(null);
        affected.setExecutedAt(null);
        affected.setExecutedBy(null);
        if (affected.getTenantOid() == null) affected.setTenantOid(tenantOid());
        affected.setNote(blankToNull(affected.getNote()));

        affectedMapper.insert(affected);
        record(noticeOid, "ADD_AFFECTED", null, null,
                "添加受影响对象：" + entityType + " " + nullToEmpty(affected.getEntityCode())
                        + " " + affected.getBaseVersion());
        return affectedMapper.selectByOid(affected.getOid());
    }

    @Override
    @Transactional
    public ChangeAffectedObject addAffectedByEntity(String noticeOid, String entityOid) {
        ChangeNotice notice = require(noticeOid);
        if (!ChangeNoticeFlow.isAffectedEditable(notice.getStatus())) {
            throw new IllegalStateException("当前状态不允许修改受影响对象（仅草稿或已驳回状态可改）");
        }
        if (isBlank(entityOid)) {
            throw new IllegalArgumentException("必须选择变更对象");
        }
        String oid = entityOid.trim();
        Map<String, Object> snapshot = snapshotAcrossTypes(oid, notice.getTenantOid());
        if (snapshot == null) {
            throw new IllegalArgumentException("变更对象不存在或不属于当前租户：" + oid);
        }
        String entityType = stringOf(snapshot.get("entityType"));
        String iterationOid = stringOf(snapshot.get("iterationOid"));
        String version = stringOf(snapshot.get("version"));
        if (isBlank(iterationOid) || isBlank(version)) {
            throw new IllegalArgumentException("对象还没有版本，不能加入变更对象："
                    + stringOf(snapshot.get("code")));
        }
        // 幂等：对象已在本通告里就返回既有那行 —— 两张任务先后挑同一个零件是正常操作，
        // 报"重复添加"等于逼前端先查一遍清单再决定要不要 POST
        for (ChangeAffectedObject existing : affectedMapper.selectByNotice(noticeOid)) {
            if (entityType.equals(existing.getEntityType()) && oid.equals(existing.getEntityOid())) {
                return existing;
            }
        }

        ChangeAffectedObject row = new ChangeAffectedObject();
        row.setOid(UUID.randomUUID().toString());
        row.setNoticeOid(noticeOid);
        row.setEntityType(entityType);
        row.setEntityOid(oid);
        row.setEntityCode(stringOf(snapshot.get("code")));
        row.setEntityName(stringOf(snapshot.get("name")));
        row.setContainerOid(stringOf(snapshot.get("containerOid")));
        // 版本取对象当前 latest：变更任务挑对象时还没到"指定被修订版本"这一步，
        // 与建单时 saveAffectedFromOids 的口径一致（之后仍可在详情里改）
        row.setBaseIterationOid(iterationOid);
        row.setBaseVersion(version);
        row.setLocked(false);
        row.setNote("从更改事项带入");
        row.setTenantOid(notice.getTenantOid());
        row.setCreator(UserContext.get());
        affectedMapper.insert(row);
        record(noticeOid, "ADD_AFFECTED", null, null,
                "更改事项选了对象：" + entityType + " " + nullToEmpty(row.getEntityCode()) + " " + version);
        return affectedMapper.selectByOid(row.getOid());
    }

    @Override
    @Transactional
    public ChangeAffectedObject assignAffectedTask(String noticeOid, String affectedOid, String taskOid) {
        ChangeNotice notice = require(noticeOid);
        if (ChangeNoticeFlow.isTerminal(notice.getStatus())) {
            throw new IllegalStateException("变更通告已结束，不能再调整分工");
        }
        ChangeAffectedObject affected = affectedMapper.selectByOid(affectedOid);
        if (affected == null || !noticeOid.equals(affected.getNoticeOid())) {
            throw new IllegalArgumentException("受影响对象不属于这张变更通告");
        }
        if (affected.getExecutedAt() != null) {
            throw new IllegalStateException("该对象已执行修订，不能改派更改事项");
        }
        String target = normalizeTaskOid(noticeOid, taskOid);
        if (target == null ? affected.getTaskOid() == null : target.equals(affected.getTaskOid())) {
            return affected;
        }

        affected.setTaskOid(target);
        affected.setUpdater(UserContext.get());
        affected.setUpdatedAt(LocalDateTime.now());
        affectedMapper.update(affected);

        ChangeTask task = target == null ? null : taskMapper.selectByOid(target);
        record(noticeOid, "ASSIGN_AFFECTED", null, null,
                "受影响对象 " + nullToEmpty(affected.getEntityCode()) + " 改派到 "
                        + (task == null ? "不归属任何更改事项" : task.getCode() + " " + task.getName()));
        return affectedMapper.selectByOid(affectedOid);
    }

    /** 任务必须属于同一张通告；空 = 待分配 */
    private String normalizeTaskOid(String noticeOid, String taskOid) {
        if (isBlank(taskOid)) return null;
        ChangeTask task = taskMapper.selectByOid(taskOid);
        if (task == null || !noticeOid.equals(task.getNoticeOid())) {
            throw new IllegalArgumentException("更改事项（变更任务）不属于这张通告，不能作为对象的归属");
        }
        return taskOid;
    }

    @Override
    @Transactional
    public boolean removeAffected(String noticeOid, String affectedOid) {
        ChangeNotice notice = require(noticeOid);
        if (!ChangeNoticeFlow.isAffectedEditable(notice.getStatus())) {
            throw new IllegalStateException("当前状态不允许修改受影响对象（仅草稿或已驳回状态可改）");
        }
        ChangeAffectedObject affected = affectedMapper.selectByOid(affectedOid);
        if (affected == null || !noticeOid.equals(affected.getNoticeOid())) return false;
        if (affected.getExecutedAt() != null) {
            throw new IllegalStateException("该对象已执行修订，不能移除");
        }
        affectedMapper.deleteByOid(affectedOid);
        record(noticeOid, "REMOVE_AFFECTED", null, null,
                "移除受影响对象：" + affected.getEntityType() + " " + nullToEmpty(affected.getEntityCode()));
        return true;
    }

    /**
     * 把来源变更请求的预估受影响对象带入执行清单。
     *
     * <p>ECR 的预估没有版本：这里按对象当前 {@code latest} 迭代补齐
     * {@code base_iteration_oid / base_version}；查不到版本（对象还没有迭代）就跳过 ——
     * 宁可不带，也不编造版本号。同对象已存在时跳过，保证重复调用幂等。
     *
     * <p>带入后仍是普通受影响对象：可删可改版本，没有"必须来自请求"的约束。
     */
    private void copyEstimatesFromRequest(ChangeNotice notice) {
        if (isBlank(notice.getRequestOid())) {
            return;
        }
        List<ChangeRequestAffectedObject> estimates =
                requestAffectedMapper.selectByRequest(notice.getRequestOid(), notice.getTenantOid());
        if (estimates.isEmpty()) {
            return;
        }
        int copied = 0;
        for (ChangeRequestAffectedObject estimate : estimates) {
            Map<String, Object> snapshot = snapshotOf(estimate.getEntityType(), estimate.getEntityOid(),
                    notice.getTenantOid());
            if (snapshot == null) {
                continue;
            }
            String iterationOid = stringOf(snapshot.get("iterationOid"));
            String version = stringOf(snapshot.get("version"));
            if (isBlank(iterationOid) || isBlank(version)) {
                continue;
            }
            if (affectedMapper.countByNoticeAndEntity(notice.getOid(),
                    estimate.getEntityType(), estimate.getEntityOid()) > 0) {
                continue;
            }
            ChangeAffectedObject row = new ChangeAffectedObject();
            row.setOid(UUID.randomUUID().toString());
            row.setNoticeOid(notice.getOid());
            row.setEntityType(estimate.getEntityType());
            row.setEntityOid(estimate.getEntityOid());
            // 用当前快照而不是 ECR 上的旧快照：编码/名称/容器可能已经改过
            row.setEntityCode(stringOf(snapshot.get("code")));
            row.setEntityName(stringOf(snapshot.get("name")));
            row.setContainerOid(stringOf(snapshot.get("containerOid")));
            row.setBaseIterationOid(iterationOid);
            row.setBaseVersion(version);
            row.setLocked(false);
            row.setNote("从变更请求带入（预估）");
            row.setTenantOid(notice.getTenantOid());
            row.setCreator(UserContext.get());
            affectedMapper.insert(row);
            copied++;
        }
        if (copied > 0) {
            record(notice.getOid(), "ADD_AFFECTED", null, null,
                    "从变更请求带入 " + copied + " 个受影响对象（按对象当前版本）");
        }
    }

    /**
     * 建单时按 oid 列表创建变更对象（受影响版本取对象当前 latest 迭代）。
     *
     * <p>与"从请求带入"不同：这是用户显式选择，查不到对象 / 对象没有版本一律报错，
     * 不做静默跳过 —— 悄悄少一个对象，等到提交校验再报"至少一个变更对象"最费解。
     */
    private void saveAffectedFromOids(ChangeNotice notice, List<String> oids) {
        Set<String> seen = new LinkedHashSet<>();
        int created = 0;
        for (String raw : oids) {
            String oid = raw == null ? "" : raw.trim();
            if (oid.isEmpty() || !seen.add(oid)) {
                continue;
            }
            Map<String, Object> snapshot = snapshotAcrossTypes(oid, notice.getTenantOid());
            if (snapshot == null) {
                throw new IllegalArgumentException("变更对象不存在或不属于当前租户：" + oid);
            }
            String entityType = stringOf(snapshot.get("entityType"));
            String iterationOid = stringOf(snapshot.get("iterationOid"));
            String version = stringOf(snapshot.get("version"));
            if (isBlank(iterationOid) || isBlank(version)) {
                throw new IllegalArgumentException("对象还没有版本，不能加入变更对象："
                        + stringOf(snapshot.get("code")));
            }
            if (affectedMapper.countByNoticeAndEntity(notice.getOid(), entityType, oid) > 0) {
                continue;
            }
            ChangeAffectedObject row = new ChangeAffectedObject();
            row.setOid(UUID.randomUUID().toString());
            row.setNoticeOid(notice.getOid());
            row.setEntityType(entityType);
            row.setEntityOid(oid);
            row.setEntityCode(stringOf(snapshot.get("code")));
            row.setEntityName(stringOf(snapshot.get("name")));
            row.setContainerOid(stringOf(snapshot.get("containerOid")));
            row.setBaseIterationOid(iterationOid);
            row.setBaseVersion(version);
            row.setLocked(false);
            row.setNote("建单时指定");
            row.setTenantOid(notice.getTenantOid());
            row.setCreator(UserContext.get());
            affectedMapper.insert(row);
            created++;
        }
        if (created > 0) {
            record(notice.getOid(), "ADD_AFFECTED", null, null, "建单时指定 " + created + " 个变更对象");
        }
    }

    /** 不指定类型时按 oid 在三张对象主表里找快照 */
    private Map<String, Object> snapshotAcrossTypes(String entityOid, String tenantOid) {
        Map<String, Object> snapshot = objectLookupMapper.selectPartSnapshot(entityOid, tenantOid);
        if (snapshot == null) {
            snapshot = objectLookupMapper.selectDocumentSnapshot(entityOid, tenantOid);
        }
        if (snapshot == null) {
            snapshot = objectLookupMapper.selectEngDocumentSnapshot(entityOid, tenantOid);
        }
        return snapshot;
    }

    /** 指定类型取对象快照；类型不合法或查不到返回 null */
    private Map<String, Object> snapshotOf(String entityType, String entityOid, String tenantOid) {
        if (isBlank(entityOid)) {
            return null;
        }
        String oid = entityOid.trim();
        switch (entityType) {
            case "PART":
                return objectLookupMapper.selectPartSnapshot(oid, tenantOid);
            case "DOCUMENT":
                return objectLookupMapper.selectDocumentSnapshot(oid, tenantOid);
            case "ENG_DOCUMENT":
                return objectLookupMapper.selectEngDocumentSnapshot(oid, tenantOid);
            default:
                return null;
        }
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        ChangeNotice existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        if (!ChangeNoticeFlow.DRAFT.equals(existing.getStatus())) {
            throw new IllegalStateException("只有草稿状态的变更通告可以删除（当前 " + existing.getStatus() + "）");
        }
        assertOwnerOf(existing, "删除变更通告");
        // 任务的 activity 没有外键，得逐个清；受影响对象随通告级联删除
        for (ChangeTask task : taskMapper.selectByNotice(oid)) {
            activityMapper.deleteByOwner("TASK", task.getOid());
        }
        activityMapper.deleteByOwner("NOTICE", oid);
        return mapper.deleteByOid(oid) > 0;
    }

    // ==================== 内部 ====================

    /** 未完成子任务的编号摘要：最多点名 5 个，其余折成「等 N 个」 */
    private static String summarizeTaskCodes(List<ChangeTask> tasks, int max) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (ChangeTask task : tasks) {
            if (shown >= max) {
                sb.append(" 等 ").append(tasks.size()).append(" 个");
                break;
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(task.getCode());
            shown++;
        }
        return sb.toString();
    }

    private ChangeNotice require(String oid) {
        ChangeNotice notice = mapper.selectByOid(oid);
        if (notice == null) throw new IllegalArgumentException("变更通告不存在");
        return notice;
    }

    /**
     * 责任人闸门：除「详情」外，变更通告的推进动作只认单据上的责任人。
     *
     * <p>{@code APPROVE} / {@code REJECT} 不在此校验内（见
     * {@link ChangeNoticeFlow#requiresOwner(String)}）：那是审批人的结论，
     * 由流程回调直接写库。
     *
     * <p><b>owner 为空时退回 creator</b>：变更通告的责任人可由来源请求继承，
     * 也可以留空。只认 owner 会让这类单据永远推不动，创建人兜底更安全。
     */
    private void assertOwner(ChangeNotice notice, String action) {
        if (!ChangeNoticeFlow.requiresOwner(action)) {
            return;
        }
        assertOwnerOf(notice, "执行「" + ChangeNoticeFlow.label(action) + "」");
    }

    /**
     * 责任人口径的唯一实现：{@code owner} 为空退回 {@code creator}。
     *
     * <p>编辑 / 删除与流转共用这一处，避免三条入口各写一份判断、日后漏改。
     */
    private void assertOwnerOf(ChangeNotice notice, String actionLabel) {
        String owner = isBlank(notice.getOwner()) ? notice.getCreator() : notice.getOwner();
        String operator = UserContext.get();
        if (isBlank(owner) || !owner.equals(operator)) {
            throw new IllegalStateException("只有责任人「" + (isBlank(owner) ? "未指派" : owner)
                    + "」才能" + actionLabel + "，当前登录人「"
                    + (isBlank(operator) ? "未知" : operator) + "」只能查看详情");
        }
    }

    /** 全字段复制（UPDATE 语句覆盖所有列，缺失字段会被写成 null —— 必须原值兜底） */
    private ChangeNotice copyOf(ChangeNotice source) {
        ChangeNotice target = new ChangeNotice();
        target.setOid(source.getOid());
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setAttachmentOid(source.getAttachmentOid());
        target.setRequestOid(source.getRequestOid());
        target.setProductOid(source.getProductOid());
        target.setProductType(source.getProductType());
        target.setChangeType(source.getChangeType());
        target.setChangeLevel(source.getChangeLevel());
        target.setPriority(source.getPriority());
        target.setCertImpact(source.getCertImpact());
        target.setCustomerImpact(source.getCustomerImpact());
        target.setInventoryImpact(source.getInventoryImpact());
        target.setReason(source.getReason());
        target.setProposal(source.getProposal());
        target.setStatus(source.getStatus());
        target.setPlanStart(source.getPlanStart());
        target.setPlanEnd(source.getPlanEnd());
        target.setProcessInstanceId(source.getProcessInstanceId());
        target.setApprovedAt(source.getApprovedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setClosedAt(source.getClosedAt());
        target.setOwner(source.getOwner());
        target.setApprovalBypassed(source.isApprovalBypassed());
        target.setBypassReason(source.getBypassReason());
        target.setBypassedBy(source.getBypassedBy());
        target.setBypassedAt(source.getBypassedAt());
        target.setTenantOid(source.getTenantOid());
        return target;
    }

    /**
     * 取下一个编码：优先走类型绑定的编码规则（{@code CHANGE_NOTICE} → ECN-0001），
     * 规则未安装或未绑定时回退本地序列（格式同构，可无缝切换）。
     */
    private String nextCode() {
        try {
            String generated = numberService.generateNumberForType("CHANGE_NOTICE");
            if (generated != null && !generated.trim().isEmpty()) {
                return generated;
            }
        } catch (Exception e) {
            log.warn("按类型生成变更通告编号失败，回退本地序列: {}", e.getMessage());
        }
        return String.format("ECN-%04d", mapper.maxCodeSeq(tenantOid()) + 1);
    }

    private static void validatePlan(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("计划完成日期不能早于开始日期");
        }
    }

    private String normalizeEntityType(String value) {
        if (isBlank(value)) throw new IllegalArgumentException("受影响对象类型不能为空");
        String normalized = value.trim().toUpperCase();
        if (!ENTITY_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("受影响对象类型只能是 " + ENTITY_TYPES);
        }
        return normalized;
    }

    private String normalizeStatusFilter(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!ChangeNoticeFlow.isValidStatus(normalized)) {
            throw new IllegalArgumentException("未知状态 '" + value + "'，取值只能是 " + ChangeNoticeFlow.statuses());
        }
        return normalized;
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 终止该单据的审批流程；实例已结束（审批刚好办完 / 引擎里已经没了）不算错误。
     *
     * <p><b>为什么必须等事务提交后再终止</b>：{@code ProcessService#terminateInstance} 带
     * {@code @Transactional}，实例不存在时抛 {@code IllegalArgumentException} —— 它加入的是
     * <b>当前</b>事务，一抛就把整个事务标成 rollback-only；这里即便 catch 住，提交时仍然会
     * {@code UnexpectedRollbackException}（2026-11-04 用户报「设置状态提示 500」就是这个：
     * 单据早已驳回、流程实例自然不在了）。
     *
     * <p>放到 afterCommit 也更合语义：终止流程是"单据状态已经定下来"之后的副作用，
     * 主事务回滚时它不该发生。afterCommit 里没有活动事务，{@code terminateInstance} 会自己
     * 开新事务；失败只记日志，不影响已经提交的状态变更。
     */
    private void terminateProcessQuietly(String instanceId, String reason) {
        if (isBlank(instanceId)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    terminateNow(instanceId, reason);
                }
            });
            return;
        }
        terminateNow(instanceId, reason);
    }

    private void terminateNow(String instanceId, String reason) {
        try {
            processService.terminateInstance(instanceId, reason);
        } catch (Exception e) {
            log.info("终止变更通告流程跳过: instance={} reason={}", instanceId, e.getMessage());
        }
    }

    private void record(String oid, String action, String from, String to, String comment) {
        ChangeActivity activity = new ChangeActivity();
        activity.setOid(UUID.randomUUID().toString());
        activity.setChangeType("NOTICE");
        activity.setChangeOid(oid);
        activity.setAction(action);
        activity.setFromStatus(from);
        activity.setToStatus(to);
        activity.setComment(comment);
        activity.setOperator(UserContext.get());
        activity.setTenantOid(tenantOid());
        activityMapper.insert(activity);
    }

    /** 关联问题去重（保持调用方给的顺序） */
    private List<String> normalizeIssueOids(List<String> issueOids) {
        Set<String> unique = new LinkedHashSet<>();
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
     * 手工选择的问题里若含已关闭 / 已取消的，直接拒绝，并在消息里点名编号。
     *
     * <p>自动带入（来自 ECR）不走这里 —— 那条路径在 SQL 里就把已关闭的过滤掉了。
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

    /** 批量写关联行；空列表直接跳过 */
    private void saveIssueLinks(String noticeOid, List<String> issueOids, String tenantOid) {
        if (issueOids == null || issueOids.isEmpty()) {
            return;
        }
        String creator = UserContext.get();
        List<ChangeNoticeIssue> rows = new ArrayList<>(issueOids.size());
        for (String issueOid : issueOids) {
            rows.add(new ChangeNoticeIssue(
                    UUID.randomUUID().toString(), noticeOid, issueOid, tenantOid, creator));
        }
        noticeIssueMapper.insertBatch(rows);
    }

    private void normalizeBlanks(ChangeNotice notice) {
        notice.setDescription(blankToNull(notice.getDescription()));
        // 定性信息：清空下拉时前端发的是空串，转 null —— 否则库里留下 ''，
        // "未评估"与"选了空值"分不出来，派生单的继承判断（isBlank）也会被空串混过去
        notice.setChangeType(blankToNull(notice.getChangeType()));
        notice.setChangeLevel(blankToNull(notice.getChangeLevel()));
        notice.setPriority(blankToNull(notice.getPriority()));
        notice.setCertImpact(blankToNull(notice.getCertImpact()));
        notice.setCustomerImpact(blankToNull(notice.getCustomerImpact()));
        notice.setInventoryImpact(blankToNull(notice.getInventoryImpact()));
        notice.setReason(blankToNull(notice.getReason()));
        notice.setProposal(blankToNull(notice.getProposal()));
        notice.setAttachmentOid(blankToNull(notice.getAttachmentOid()));
        notice.setRequestOid(blankToNull(notice.getRequestOid()));
        // 所属容器：前端清空时发的是空串（product-select 的 patch 语义），必须转成 null，
        // 否则库里会留下 product_oid = '' —— 反查名称查不到，按"为空"判断也判不出来
        notice.setProductOid(blankToNull(notice.getProductOid()));
        notice.setProductType(blankToNull(notice.getProductType()));
        notice.setOwner(blankToNull(notice.getOwner()));
        notice.setBypassReason(blankToNull(notice.getBypassReason()));
    }

    /**
     * 把「补充材料」这个附件挂到本单上（与 ECR 侧同一口径，见 ChangeRequestServiceImpl#bindAttachment）。
     *
     * <p>上传发生在建单之前，{@code ck_attachment.owner_oid} 为空；保存时补上，按单据反查才查得到。
     * 只挂"本租户 + 还没有主"的附件，避免拿别单的 oid 把附件挂过来。
     */
    private void bindAttachment(String attachmentOid, String noticeOid) {
        if (isBlank(attachmentOid)) return;
        CKAttachment attachment = attachmentService.findByOid(attachmentOid);
        if (attachment == null || attachment.getOwnerOid() != null) return;
        if (!java.util.Objects.equals(attachment.getTenantOid(), tenantOid())) return;
        attachmentService.bindToOwner(attachmentOid, noticeOid);
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
