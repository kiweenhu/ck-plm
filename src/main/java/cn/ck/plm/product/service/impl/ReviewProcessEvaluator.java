/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.product.entity.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评审实例的「流程进展」现算器：把 {@code ck_review} 的一行对到流程引擎里那条实例上。
 *
 * <h3>为什么需要它</h3>
 * <p>评审行的 {@code status} 是快照时的 {@code PENDING}，发起流程后没有任何人写它 ——
 * 于是「评审」页签里点了「发起」、流程都跑到核对节点了，行上还是「未开始 / 未评审」
 * （2026-10-26 用户反馈："流程发起后，评审清单上没有变化，用户体验不好"）。
 *
 * <p>与交付件同一套口径（见 {@link DeliverableStatusEvaluator}）：<b>看的时候算，不写回库</b>。
 * 写回要靠流程结束回调，漏发一次（终止、异常、历史被清理）就永远卡在「进行中」；
 * 现算则永远与引擎一致。字段都是 {@link Review} 上的传输字段（不落库）。
 *
 * <h3>怎么认出"这条实例属于哪条评审"</h3>
 * <ol>
 *   <li>实例来源：{@code ck_process_entity_set} 按业务对象反查（{@link ProcessService#findInstancesByEntity}），
 *       与对象详情页「关联流程」同一个来源；</li>
 *   <li>按<b>流程 key</b> 粗筛：评审行自带 {@code processKey}（TR* → {@code technical-review}），
 *       变更 / 问题等其它流程直接排除；</li>
 *   <li>再按<b>流程变量</b>认领：发起时表单写的 {@code ckplmTechReview_reviewTemplate}
 *       （= 评审码，如 {@code TR1_CONCEPT}）精确匹配；变量缺失时退回
 *       {@code ckplmTechReview_gate}（门码）—— 但只在该门下<b>只有一条评审</b>时采用，
 *       宁可显示「未开始」也不把 A 评审的进度挂到 B 上。</li>
 * </ol>
 *
 * <h3>状态口径</h3>
 * <ul>
 *   <li>有 running / suspended 的实例 → {@code IN_PROGRESS}（带当前节点与办理人）；</li>
 *   <li>实例正常走完 → {@code DONE}，结束时间进 {@code reviewedAt}；结论优先取「结论登记」写的
 *       {@code ckplmTechReview_finalVerdict}，没有才退回检查表的 {@code ckplmTechReview_verdict}；
 *       两者都取不到就留空 —— 前端显示「未评定」，不臆造 PASS/FAIL；</li>
 *   <li>实例被终止 → 退回 {@code PENDING}：这条评审没被评审过，应当可以重新发起。</li>
 * </ul>
 *
 * <p>查询失败一律<b>吞掉</b>：评审列表不能因为流程引擎查不到而整页报红（与交付件口径一致）。
 */
@Component
public class ReviewProcessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ReviewProcessEvaluator.class);

    /** 与面板状态列同一套码（PENDING / IN_PROGRESS / DONE / NA） */
    private static final String PENDING = "PENDING";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String DONE = "DONE";

    /** 技术评审流程发起时写的变量（见 TechnicalReviewApplyForm#collect） */
    private static final String VAR_REVIEW_TEMPLATE = "ckplmTechReview_reviewTemplate";
    private static final String VAR_GATE = "ckplmTechReview_gate";
    /** 检查表节点写下的检查结论 */
    private static final String VAR_VERDICT = "ckplmTechReview_verdict";
    /** 「结论登记」节点写下的最终结论 —— 有它就以它为准（检查表的结论是过程值） */
    private static final String VAR_FINAL_VERDICT = "ckplmTechReview_finalVerdict";

    /**
     * 延迟取流程服务。
     *
     * <p>不能构造器直接注入：{@code MethodologyInstanceServiceImpl ← ProductModelServiceIMP ← … ←
     * SoftTypeInstanceRegistry ← ProcessEntitySetServiceImpl ← ProcessServiceImpl} —— 直连会形成
     * 启动期循环依赖（实测 {@code Requested bean is currently in creation}）。本类只在"查询评审列表"
     * 那一刻才需要流程服务，彼时容器早已就绪，延迟取既解开环，也不改变调用时机。
     */
    private final ObjectProvider<ProcessService> processServiceProvider;

    public ReviewProcessEvaluator(ObjectProvider<ProcessService> processServiceProvider) {
        this.processServiceProvider = processServiceProvider;
    }

    /**
     * 给一组同归属单元的评审行补上流程进展（就地改对象，不落库）。
     *
     * @param ownerOid 产品系列 / 型号 oid
     * @param reviews  该归属单元的评审实例（{@code ck_review}）
     */
    public void enrich(String ownerOid, List<Review> reviews) {
        Map<String, Claim> claims = claimsOf(ownerOid, reviews);
        if (claims.isEmpty()) {
            return;
        }
        for (Review r : reviews) {
            Claim claim = r == null ? null : claims.get(r.getOid());
            if (claim != null) {
                apply(r, claim);
            }
        }
    }

    /**
     * 这条评审是否<b>正在流程中</b>（running / suspended）。
     *
     * <p>裁剪（标记「本项目不适用」）之前要拦：评审正走在流程里，把它停用等于把在跑的评审
     * 从清单里抹掉 —— 办到一半的人回头看列表，自己那条不见了
     * （2026-10-26 用户口径："已经在进行中的不可以裁剪"）。
     */
    public boolean isRunning(String ownerOid, Review review) {
        if (review == null || review.getOid() == null) {
            return false;
        }
        Claim claim = claimsOf(ownerOid, List.of(review)).get(review.getOid());
        return claim != null && claim.running();
    }

    /** 认领：评审 oid → 它当前对应的流程实例（在跑的优先；同类取开始更晚的） */
    private Map<String, Claim> claimsOf(String ownerOid, List<Review> reviews) {
        Map<String, Claim> claims = new LinkedHashMap<>();
        if (ownerOid == null || ownerOid.trim().isEmpty() || reviews == null || reviews.isEmpty()) {
            return claims;
        }
        Set<String> keys = new HashSet<>();
        for (Review r : reviews) {
            if (r != null && r.getProcessKey() != null && !r.getProcessKey().trim().isEmpty()) {
                keys.add(r.getProcessKey().trim());
            }
        }
        if (keys.isEmpty()) {
            return claims;
        }

        ProcessService processService;
        List<ProcessInstanceVO> instances;
        try {
            processService = processServiceProvider.getObject();
            instances = processService.findInstancesByEntity(ownerOid.trim(), null);
        } catch (Exception e) {
            log.warn("读取归属单元 {} 的流程实例失败，评审状态按库内快照展示: {}", ownerOid, e.getMessage());
            return claims;
        }
        if (instances == null || instances.isEmpty()) {
            return claims;
        }

        for (ProcessInstanceVO vo : instances) {
            if (vo == null || vo.getId() == null) {
                continue;
            }
            String defKey = vo.getProcessDefinitionKey();
            if (defKey == null || !keys.contains(defKey)) {
                continue;
            }
            Map<String, Object> vars = variablesOf(processService, vo.getId());
            Review target = match(reviews, defKey, vars);
            if (target == null || target.getOid() == null) {
                continue;
            }
            Claim claim = new Claim(vo, vars);
            Claim prev = claims.get(target.getOid());
            if (prev == null || claim.betterThan(prev)) {
                claims.put(target.getOid(), claim);
            }
        }
        return claims;
    }

    /** 把实例状态写回评审行（只改传输字段与展示用状态，不落库） */
    private void apply(Review review, Claim claim) {
        ProcessInstanceVO vo = claim.instance;
        review.setProcessInstanceId(vo.getId());
        review.setProcessStatus(vo.getStatus());
        review.setCurrentActivityName(vo.getCurrentActivityName());
        review.setCurrentAssignees(vo.getCurrentAssignees());
        review.setProcessStartUserName(vo.getStartUserName());
        review.setProcessStartedAt(toLocal(vo.getStartTime()));
        review.setProcessEndedAt(toLocal(vo.getEndTime()));

        String status = vo.getStatus();
        if ("running".equals(status) || "suspended".equals(status)) {
            review.setStatus(IN_PROGRESS);
            return;
        }
        if ("completed".equals(status)) {
            review.setStatus(DONE);
            if (vo.getEndTime() != null) {
                review.setReviewedAt(toLocal(vo.getEndTime()));
            }
            // 优先「结论登记」的最终结论：检查表的 verdict 只是过程值，后面的复评/结论可能改判
            String verdict = str(claim.variables.get(VAR_FINAL_VERDICT));
            if (verdict == null) {
                verdict = str(claim.variables.get(VAR_VERDICT));
            }
            if (verdict != null) {
                review.setVerdict(verdict);
            }
            return;
        }
        // terminated：这次评审没评成，退回「未开始」—— 否则用户看着"进行中"却再也不能发起
        review.setStatus(PENDING);
    }

    /**
     * 认领：哪条评审属于这个实例。
     *
     * <p>先按评审码精确匹配（发起表单必填），再按门码兜底 —— 兜底要求"该门下只有一条评审"，
     * 一门多评审时无法判断，宁可不认领。
     */
    private Review match(List<Review> reviews, String processKey, Map<String, Object> vars) {
        String code = str(vars.get(VAR_REVIEW_TEMPLATE));
        if (code != null) {
            // 先按评审码认；再按评审名称认 —— 历史数据里这个变量被"检查表"节点写成了展示名
            // （2026-10-29 用户反馈：流程走完了，清单上还是「未开始 / 未评审」），
            // 只认码的话那条实例永远认领不上。
            for (Review r : reviews) {
                if (r != null && code.equals(r.getCode()) && sameKey(r, processKey)) {
                    return r;
                }
            }
            for (Review r : reviews) {
                if (r != null && code.equals(r.getName()) && sameKey(r, processKey)) {
                    return r;
                }
            }
            return null;
        }
        String gate = str(vars.get(VAR_GATE));
        if (gate == null) {
            return null;
        }
        List<Review> hits = new ArrayList<>();
        for (Review r : reviews) {
            if (r != null && gate.equals(r.getGate()) && sameKey(r, processKey)) {
                hits.add(r);
            }
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    /** 评审行绑的流程 key 与实例定义 key 一致（老行没配 processKey 时不做限制） */
    private boolean sameKey(Review r, String processKey) {
        String key = r.getProcessKey();
        return key == null || key.trim().isEmpty() || key.trim().equals(processKey);
    }

    /**
     * 该实例的用户表单变量。
     *
     * <p>用详情接口取（运行中取运行期变量，已结束取历史变量）—— 认领依据只可能是表单填的字段，
     * 而列表接口刻意不带变量。候选实例已按流程 key 粗筛过，这里的次数是个位数。
     */
    private Map<String, Object> variablesOf(ProcessService processService, String instanceId) {
        try {
            ProcessInstanceVO detail = processService.findInstanceDetail(instanceId);
            Map<String, Object> vars = detail == null ? null : detail.getVariables();
            return vars == null ? new HashMap<>() : vars;
        } catch (Exception e) {
            log.warn("读取流程变量失败，该实例不参与评审认领: instance={} error={}", instanceId, e.getMessage());
            return new HashMap<>();
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /** 一条实例 + 它的变量快照 */
    private static final class Claim {
        final ProcessInstanceVO instance;
        final Map<String, Object> variables;

        Claim(ProcessInstanceVO instance, Map<String, Object> variables) {
            this.instance = instance;
            this.variables = variables;
        }

        /** 在跑的优先；同类里开始更晚的优先（重复发起时取最近那次） */
        boolean betterThan(Claim other) {
            boolean mine = running();
            boolean theirs = other.running();
            if (mine != theirs) {
                return mine;
            }
            Date a = instance.getStartTime();
            Date b = other.instance.getStartTime();
            if (a == null) {
                return false;
            }
            return b == null || a.after(b);
        }

        private boolean running() {
            String s = instance.getStatus();
            return "running".equals(s) || "suspended".equals(s);
        }
    }
}
