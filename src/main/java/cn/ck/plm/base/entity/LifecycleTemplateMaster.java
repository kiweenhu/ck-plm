/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 生命周期模板 Master 实体 —— 复合实体的 Master 端。
 *
 * <p>继承 {@link MasterEntity} 获得 name / number / description 标准字段及审计能力。
 * 作为模板定义的持久化载体，与 {@link LifecycleTemplateIteration}（Iteration）构成 Master-Iteration 复合实体。
 *
 * <p>{@link #toDomainDef()} 可将持久化定义转换为领域模型 {@link LifecycleTemplateDef}。
 */
public class LifecycleTemplateMaster extends MasterEntity implements TenantEntity {

    /** 模板编码（业务唯一键，对应数据库 code 列） */
    private String code;

    /** 是否启用 */
    private boolean active;

    /** 初始状态编码 */
    private String initialStateCode;

    /** 模板包含的状态列表（含排序） */
    private List<LifecycleTemplateStatusRef> states = new ArrayList<>();

    /** 状态流转规则（升版） */
    private List<LifecycleTemplateTransitionRef> transitions = new ArrayList<>();

    /** 状态驳回规则 */
    private List<LifecycleTemplateTransitionRef> rejections = new ArrayList<>();

    /** 最新迭代版本 */
    private LifecycleTemplateIteration latestIteration;

    /** 租户 oid */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public LifecycleTemplateMaster() {
    }

    // ==================== 转换为领域模型 ====================

    /**
     * 将持久化模板定义转换为领域模型 {@link LifecycleTemplateDef}，供 FSM 状态流转使用。
     */
    public LifecycleTemplateDef toDomainDef() {
        LifecycleTemplateDef def = new LifecycleTemplateDef(getName());
        for (LifecycleTemplateStatusRef ref : states) {
            def.addStatus(new LifecycleStatus(ref.getStatusCode(), ref.getStatusDisplayName()));
        }
        for (LifecycleTemplateTransitionRef ref : transitions) {
            def.addTransition(ref.getFromStatusCode(), ref.getToStatusCode());
        }
        for (LifecycleTemplateTransitionRef ref : rejections) {
            def.addRejection(ref.getFromStatusCode(), ref.getToStatusCode());
        }
        return def;
    }

    // ==================== Getter / Setter ====================

    /** 模板编码（business key） */
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getInitialStateCode() {
        return initialStateCode;
    }

    public void setInitialStateCode(String initialStateCode) {
        this.initialStateCode = initialStateCode;
    }

    public List<LifecycleTemplateStatusRef> getStates() {
        return states;
    }

    public void setStates(List<LifecycleTemplateStatusRef> states) {
        this.states = states;
    }

    public List<LifecycleTemplateTransitionRef> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<LifecycleTemplateTransitionRef> transitions) {
        this.transitions = transitions;
    }

    public List<LifecycleTemplateTransitionRef> getRejections() {
        return rejections;
    }

    public void setRejections(List<LifecycleTemplateTransitionRef> rejections) {
        this.rejections = rejections;
    }

    public LifecycleTemplateIteration getLatestIteration() {
        return latestIteration;
    }

    public void setLatestIteration(LifecycleTemplateIteration latestIteration) {
        this.latestIteration = latestIteration;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
