/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 「设置生命周期状态」弹窗的候选项 —— 全部来自<b>该类型绑定的生命周期模板</b>。
 *
 * <p>为什么要后端给：模板里既有状态清单，也有"谁能迁到谁"的规则。前端如果自己拼
 * （比如拉全量状态字典），列出来的选项可能根本迁不过去 —— 用户点了才报错。
 * 这里直接把"到这个状态行不行、为什么不行"一起给出来，界面上不可达的就禁用并说明原因。
 */
public class LifecycleStateOptionsVO {

    /** 本次候选项依据的模板（= 业务配置里的模板；设置状态会先切到它） */
    private String templateCode;
    private String templateName;

    /**
     * 对象<b>自身绑定</b>的模板（迭代里存的 {@code lifecycle_template_iteration_oid} 反查）。
     *
     * <p>与 {@link #configuredTemplateCode} 可能不同：业务配置后来被改过，而对象"出生"时
     * 固化的是旧模板 —— 两者的状态清单/迁移规则都可能不一样，执行侧（moveToState）认的是
     * <b>对象绑定的那一版</b>，所以界面必须把这个差异摆到用户面前，而不是悄悄按配置模板给候选。
     */
    private String boundTemplateCode;
    private String boundTemplateName;

    /** 业务配置里该类型当前绑定的模板（迁移的目标） */
    private String configuredTemplateCode;
    private String configuredTemplateName;

    /** 两者不是同一个模板：界面弹出「迁移到新模板 / 保留旧模板」的选择 */
    private boolean templateChanged;

    /**
     * 对象<b>没有可用的模板绑定</b>（历史对象从未写入；或原模板 / 子版本已被删除），
     * 需要在确定时先绑定到业务配置的模板。
     *
     * <p>与 {@link #templateChanged} 互斥：那是"有旧模板、但和配置不一致"，
     * 这是"根本没有可用的旧模板"。两种情况执行侧都要求迭代上有模板 oid
     * （{@code moveToState / moveToInitialState} 缺失时直接拒绝），
     * 所以界面必须把这次绑定讲清楚，并在提交时带上 {@code migrateTemplate=true}。
     */
    private boolean templateBindRequired;

    /**
     * 迁移到新模板后当前状态是否保留。
     *
     * <p>{@code true} = 新模板里有同 code 的状态，迁移后当前状态不变；
     * {@code false} = 没有，迁移后会落到新模板的初始状态（见 {@link #migrationStatusCode}）。
     * 仅在 {@link #templateChanged} 为真时有意义。
     */
    private Boolean migrationKeepsStatus;

    /** 迁移后的当前状态（{@link #migrationKeepsStatus} 为 false 时是初始状态） */
    private String migrationStatusCode;
    private String migrationStatusName;

    /** 对象当前状态（对象还没有状态时为 null） */
    private StateInfo current;

    /** 模板的初始状态（「设置对象到初始状态」的目标） */
    private StateInfo initial;

    /** 模板里的全部状态（含可达性） */
    private List<StateOption> states = new ArrayList<>();

    /** 没有绑定模板时的说明（前端据此禁用整个弹窗，而不是给一个空下拉） */
    private String reason;

    /**
     * 不允许手工设置状态的原因 —— 目前只有一种：<b>该对象正在流程中</b>
     * （同一对象 + 大版本已有流程实例在跑）。
     *
     * <p>为什么在流程中就不让改：流程里的「设置状态」服务节点会在该到的时候改它，
     * 手工插一脚会让"流程走到哪一步"与"对象是什么状态"对不上（审批记录看着过了，
     * 状态却被人改到了别处）。要改状态就先把流程走完或终止。
     *
     * <p>与 {@link #reason} 分开：{@code reason} 是"这份候选给不出来"，
     * 这里是"候选给得出、但现在不许设"，前端呈现方式不同（后者仍展示模板信息 + 警示）。
     */
    private String blockedReason;

    public static class StateInfo {
        private String code;
        private String name;

        public StateInfo() {
        }

        public StateInfo(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class StateOption extends StateInfo {

        /** 从当前状态<b>一步</b>能否迁到它（已经处于该状态也算可以：重复设置是幂等的） */
        private boolean reachable;

        /** 不可达的原因（可达时为 null）；界面上显示在选项后面 */
        private String reason;

        public boolean isReachable() {
            return reachable;
        }

        public void setReachable(boolean reachable) {
            this.reachable = reachable;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getBoundTemplateCode() {
        return boundTemplateCode;
    }

    public void setBoundTemplateCode(String boundTemplateCode) {
        this.boundTemplateCode = boundTemplateCode;
    }

    public String getBoundTemplateName() {
        return boundTemplateName;
    }

    public void setBoundTemplateName(String boundTemplateName) {
        this.boundTemplateName = boundTemplateName;
    }

    public String getConfiguredTemplateCode() {
        return configuredTemplateCode;
    }

    public void setConfiguredTemplateCode(String configuredTemplateCode) {
        this.configuredTemplateCode = configuredTemplateCode;
    }

    public String getConfiguredTemplateName() {
        return configuredTemplateName;
    }

    public void setConfiguredTemplateName(String configuredTemplateName) {
        this.configuredTemplateName = configuredTemplateName;
    }

    public boolean isTemplateChanged() {
        return templateChanged;
    }

    public void setTemplateChanged(boolean templateChanged) {
        this.templateChanged = templateChanged;
    }

    public boolean isTemplateBindRequired() {
        return templateBindRequired;
    }

    public void setTemplateBindRequired(boolean templateBindRequired) {
        this.templateBindRequired = templateBindRequired;
    }

    public Boolean getMigrationKeepsStatus() {
        return migrationKeepsStatus;
    }

    public void setMigrationKeepsStatus(Boolean migrationKeepsStatus) {
        this.migrationKeepsStatus = migrationKeepsStatus;
    }

    public String getMigrationStatusCode() {
        return migrationStatusCode;
    }

    public void setMigrationStatusCode(String migrationStatusCode) {
        this.migrationStatusCode = migrationStatusCode;
    }

    public String getMigrationStatusName() {
        return migrationStatusName;
    }

    public void setMigrationStatusName(String migrationStatusName) {
        this.migrationStatusName = migrationStatusName;
    }

    public StateInfo getCurrent() {
        return current;
    }

    public void setCurrent(StateInfo current) {
        this.current = current;
    }

    public StateInfo getInitial() {
        return initial;
    }

    public void setInitial(StateInfo initial) {
        this.initial = initial;
    }

    public List<StateOption> getStates() {
        return states;
    }

    public void setStates(List<StateOption> states) {
        this.states = states;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }
}
