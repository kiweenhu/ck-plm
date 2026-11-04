/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.product.entity.Gate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阶段门实例的「流程进展」现算器：把 {@code ck_gate} 的一行对到流程引擎里那条决策流程实例上。
 *
 * <h3>为什么需要它</h3>
 * <p>门的 {@code status} 是模板实例化时的快照（{@code PENDING}），发起决策流程后没有任何人写它 ——
 * 于是「阶段门」页签里点了「发起」、流程都走到材料核对节点了，行上还是「未开始」
 * （2026-10-29 用户反馈："决策评审已发起，但状态还是未开始，参考技术评审的状态更新与提示"）。
 *
 * <p>与技术评审<b>完全同一套口径</b>（见 {@link ReviewProcessEvaluator}）：<b>看的时候算，不写回库</b>。
 * 写回要靠流程结束回调，漏发一次（终止、异常、历史被清理）就永远卡在「进行中」；现算则永远与引擎一致。
 * 补的字段都是 {@link Gate} 上的传输字段（不落库）。
 *
 * <h3>怎么认出"这条实例属于哪道门"</h3>
 * <ol>
 *   <li>实例来源：{@code ck_process_entity_set} 按业务对象反查（{@link ProcessService#findInstancesByEntity}）；</li>
 *   <li>按<b>流程 key</b> 粗筛：门行自带 {@code processKey}（决策门 → {@code gate-decision}），
 *       变更 / 评审等其它流程直接排除；</li>
 *   <li>再按<b>流程变量</b>认领：发起时「决策申请与自检结果」节点写的
 *       {@code ckplmGateDecision_gate}（= 门码，如 {@code CHARTER}）精确匹配门行的 {@code code}。
 *       变量缺失（老实例没走申请单）就不认领 —— 宁可显示「未开始」也不把 A 门的进度挂到 B 上。</li>
 * </ol>
 *
 * <h3>状态口径</h3>
 * <ul>
 *   <li>running / suspended → {@code IN_PROGRESS}（带当前节点与办理人）；</li>
 *   <li>正常走完 → 按「结论留痕」写的 {@code ckplmGateRecord_decision} 落
 *       {@code PASSED}（GO / GO_WITH_CONDITION）/ {@code REJECTED}（HOLD / TERMINATE / RECYCLE）；
 *       没有决策变量就落 {@code DONE}（走完了但没留结论）—— 不臆造通过；</li>
 *   <li>被终止 → 退回 {@code PENDING}：这条门没被决策过，应当可以重新发起。</li>
 * </ul>
 *
 * <p>查询失败一律<b>吞掉</b>：门列表不能因为流程引擎查不到而整页报红（与评审口径一致）。
 *
 * <p>依赖用 {@link ObjectProvider} 延迟取，原因同 {@link ReviewProcessEvaluator}：直连
 * {@code ProcessService} 会形成启动期循环依赖。
 */
@Component
public class GateProcessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GateProcessEvaluator.class);

    /** 与面板状态列同一套码（PENDING / IN_PROGRESS / DONE / PASSED / REJECTED） */
    private static final String PENDING = "PENDING";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String DONE = "DONE";
    private static final String PASSED = "PASSED";
    private static final String REJECTED = "REJECTED";

    /** 决策申请节点写的门码（见 GateDecisionApplyForm#collect） */
    private static final String VAR_GATE = "ckplmGateDecision_gate";
    /** 「结论留痕」节点写的决策（见 GateDecisionRecordForm#collect） */
    private static final String VAR_DECISION = "ckplmGateRecord_decision";

    /** 驳回类决策：登记这些决策时表单提交的是 reject（流程退回），门上就该是「已否决」 */
    private static final Set<String> REJECT_DECISIONS = Set.of("HOLD", "TERMINATE", "RECYCLE");

    private final ObjectProvider<ProcessService> processServiceProvider;

    public GateProcessEvaluator(ObjectProvider<ProcessService> processServiceProvider) {
        this.processServiceProvider = processServiceProvider;
    }

    /**
     * 给一组同归属单元的门补上流程进展（就地改对象，不落库）。
     *
     * @param ownerOid 产品系列 / 型号 oid
     * @param gates    该归属单元的门实例（{@code ck_gate}）
     */
    public void enrich(String ownerOid, List<Gate> gates) {
        Map<String, Claim> claims = claimsOf(ownerOid, gates);
        if (claims.isEmpty()) {
            return;
        }
        for (Gate g : gates) {
            Claim claim = g == null ? null : claims.get(g.getOid());
            if (claim != null) {
                apply(g, claim);
            }
        }
    }

    /**
     * 这道门的决策流程是否<b>正在跑</b>（running / suspended）。
     *
     * <p>裁剪（标记「本项目不适用」）之前要拦：决策走在流程里，把它停用等于把在决策的门
     * 从清单里抹掉 —— 决策留痕跟着条目一起消失，审厂时无法追溯。
     */
    public boolean isRunning(String ownerOid, Gate gate) {
        if (gate == null || gate.getOid() == null) {
            return false;
        }
        Claim claim = claimsOf(ownerOid, List.of(gate)).get(gate.getOid());
        return claim != null && claim.running();
    }

    /** 认领：门 oid → 它当前对应的流程实例（在跑的优先；同类取开始更晚的） */
    private Map<String, Claim> claimsOf(String ownerOid, List<Gate> gates) {
        Map<String, Claim> claims = new LinkedHashMap<>();
        if (ownerOid == null || ownerOid.trim().isEmpty() || gates == null || gates.isEmpty()) {
            return claims;
        }
        Set<String> keys = new HashSet<>();
        for (Gate g : gates) {
            if (g != null && g.getProcessKey() != null && !g.getProcessKey().trim().isEmpty()) {
                keys.add(g.getProcessKey().trim());
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
            log.warn("读取归属单元 {} 的流程实例失败，门状态按库内快照展示: {}", ownerOid, e.getMessage());
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
            Gate target = match(gates, defKey, vars);
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

    /** 把实例状态写回门行（只改传输字段与展示用状态，不落库） */
    private void apply(Gate gate, Claim claim) {
        ProcessInstanceVO vo = claim.instance;
        gate.setProcessInstanceId(vo.getId());
        gate.setProcessStatus(vo.getStatus());
        gate.setCurrentActivityName(vo.getCurrentActivityName());
        gate.setCurrentAssignees(vo.getCurrentAssignees());
        gate.setProcessStartUserName(vo.getStartUserName());
        gate.setProcessStartedAt(toLocal(vo.getStartTime()));
        gate.setProcessEndedAt(toLocal(vo.getEndTime()));

        // 决策结论一旦登记就带出来（哪怕实例还在收尾）——「决策」列不必等流程结束才有内容
        String decision = str(claim.variables.get(VAR_DECISION));
        if (decision != null) {
            gate.setDecision(decision);
        }

        String status = vo.getStatus();
        if ("running".equals(status) || "suspended".equals(status)) {
            gate.setStatus(IN_PROGRESS);
            return;
        }
        if ("completed".equals(status)) {
            if (vo.getEndTime() != null) {
                gate.setDecidedAt(toLocal(vo.getEndTime()));
            }
            gate.setStatus(decision == null ? DONE
                    : (REJECT_DECISIONS.contains(decision) ? REJECTED : PASSED));
            return;
        }
        // terminated：这次决策没做成，退回「未开始」—— 否则用户看着"进行中"却再也不能发起
        gate.setStatus(PENDING);
    }

    /**
     * 认领：哪道门属于这个实例 —— 只认决策申请节点写下的门码。
     *
     * <p>不做"该阶段只有一道门就认它"的兜底：门码是发起表单必填项，缺失说明这条实例
     * 根本没走申请单；猜错会把 CHARTER 的进度挂到 PDCP 上，比显示"未开始"糟得多。
     */
    private Gate match(List<Gate> gates, String processKey, Map<String, Object> vars) {
        String code = str(vars.get(VAR_GATE));
        if (code == null) {
            return null;
        }
        for (Gate g : gates) {
            if (g != null && code.equals(g.getCode()) && sameKey(g, processKey)) {
                return g;
            }
        }
        return null;
    }

    /** 门行绑的流程 key 与实例定义 key 一致（老行没配 processKey 时不做限制） */
    private boolean sameKey(Gate g, String processKey) {
        String key = g.getProcessKey();
        return key == null || key.trim().isEmpty() || key.trim().equals(processKey);
    }

    /**
     * 该实例的用户表单变量。
     *
     * <p>与评审同一手法：列表接口刻意不带变量，认领依据只可能来自表单填的字段，
     * 而候选实例已按流程 key 粗筛过，这里的次数是个位数。
     */
    private Map<String, Object> variablesOf(ProcessService processService, String instanceId) {
        try {
            ProcessInstanceVO detail = processService.findInstanceDetail(instanceId);
            Map<String, Object> vars = detail == null ? null : detail.getVariables();
            return vars == null ? new HashMap<>() : vars;
        } catch (Exception e) {
            log.warn("读取流程变量失败，该实例不参与门认领: instance={} error={}", instanceId, e.getMessage());
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
