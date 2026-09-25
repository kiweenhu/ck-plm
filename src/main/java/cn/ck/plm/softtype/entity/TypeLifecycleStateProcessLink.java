/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 类型-生命周期状态-流程模板 关联实体（1:1）。
 *
 * <p>与 {@link TypeLifecycleTemplateLink} 同族：那条关联回答"这个类型用哪套生命周期"，
 * 本条关联回答"<b>该类型在其生命周期模板的<b>某个版本</b>下，某个状态用哪个流程模板</b>"。
 *
 * <p><b>两个维度都不能省</b>：
 * <ul>
 *   <li><b>类型</b>：同一个生命周期模板（如 STANDARD）会被多个类型复用
 *       （{@code ck_type_lifecycle_template_link} 里 STANDARD 挂着 20+ 个类型），
 *       不带类型就退化成"该模板的某状态全局只能用一个流程"；</li>
 *   <li><b>版本（子版本 oid）</b>：业务对象迭代固化的是
 *       {@code lifecycle_template_iteration_oid}（见 {@code IterationEntity} +
 *       {@code LifecycleTemplateService#initLifecycle}），即实例"出生"时用的是哪一版模板。
 *       关联也挂同一层，运行期才能用实例自带的子版本 oid <b>精确</b>命中当时那一版配置，
 *       而不是被后来的改配置"追溯性改写"。</li>
 * </ul>
 *
 * <p><b>1:1</b>：由唯一索引
 * {@code uk_tlspl_type_iteration_status(type_oid, lifecycle_template_iteration_oid, status_code)} 保证。
 *
 * <p><b>改配置与改模板的配合</b>：生命周期模板被编辑（产生新子版本）时，
 * 由 {@code LifecycleTemplateService} 回调本模块把配置<b>继承</b>到新子版本
 * （只保留新版本里仍然存在的状态）—— 否则用户一编辑模板，配置就成了"挂在旧版本上、
 * 界面上看不到"的孤儿。
 *
 * <p><b>跨模块软引用</b>：{@link #processTemplateOid} 指向 {@code ck_process_template.oid}，
 * 不加外键（process 模块依赖本模块）。悬空引用由"删除流程模板前检查引用"兜底
 * （{@code ProcessTemplateServiceImpl#deleteVersions}）。
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>{@code typeOid} → {@code ck_type_definition.oid}（与 {@link TypeLifecycleTemplateLink} 同口径）</li>
 *   <li>{@code lifecycleTemplateIterationOid} → {@code ck_lifecycle_template_iteration.oid}
 *       （与该模板的 master 之 {@code code} 组合，即"哪个模板的哪一版"）</li>
 *   <li>{@code statusCode} → 该子版本里的状态 code</li>
 *   <li>{@code processTemplateOid} → {@code ck_process_template.oid}（跨模块软引用）</li>
 * </ul>
 */
public class TypeLifecycleStateProcessLink extends BaseEntity implements TenantEntity {

    /** 类型 OID，关联 ck_type_definition.oid */
    private String typeOid;

    /**
     * 生命周期模板【子版本】OID，关联 ck_lifecycle_template_iteration.oid。
     *
     * <p>与业务对象迭代的 {@code lifecycle_template_iteration_oid} 同一口径：
     * 记录"哪一版生命周期模板"，而不是模板 code（code 不带版本，无法表达"按版本解析"）。
     */
    private String lifecycleTemplateIterationOid;

    /** 状态编码（对应状态字典 code，如 DRAFT / IN_WORK / RELEASED） */
    private String statusCode;

    /** 该状态使用的流程模板 oid（ck_process_template.oid） */
    private String processTemplateOid;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public TypeLifecycleStateProcessLink() {
    }

    public TypeLifecycleStateProcessLink(String typeOid, String lifecycleTemplateIterationOid, String statusCode,
                                         String processTemplateOid) {
        this.typeOid = typeOid;
        this.lifecycleTemplateIterationOid = lifecycleTemplateIterationOid;
        this.statusCode = statusCode;
        this.processTemplateOid = processTemplateOid;
    }

    // ==================== Getter / Setter ====================

    public String getTypeOid() {
        return typeOid;
    }

    public void setTypeOid(String typeOid) {
        this.typeOid = typeOid;
    }

    public String getLifecycleTemplateIterationOid() {
        return lifecycleTemplateIterationOid;
    }

    public void setLifecycleTemplateIterationOid(String lifecycleTemplateIterationOid) {
        this.lifecycleTemplateIterationOid = lifecycleTemplateIterationOid;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getProcessTemplateOid() {
        return processTemplateOid;
    }

    public void setProcessTemplateOid(String processTemplateOid) {
        this.processTemplateOid = processTemplateOid;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }

    @Override
    public String toString() {
        return "TypeLifecycleStateProcessLink{" +
                "typeOid='" + typeOid + '\'' +
                ", lifecycleTemplateIterationOid='" + lifecycleTemplateIterationOid + '\'' +
                ", statusCode='" + statusCode + '\'' +
                ", processTemplateOid='" + processTemplateOid + '\'' +
                '}';
    }
}
