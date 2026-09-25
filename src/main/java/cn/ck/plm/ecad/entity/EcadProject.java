/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ecad.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 电子设计项目（ECAD Project）—— 电子设计任务的容器对象（内置实体）。
 *
 * <p>对应类型定义中注册在电子设计域（{@code ECAD_DOMAIN}）锚点之下的独立对象类型
 * {@code ECAD_PROJECT}：拥有独立实体表 {@code ck_ecad_project}，
 * 并非 PART / DOCUMENT 的软类型（其 rootTypeCode 为自身）。
 *
 * <h3>定位</h3>
 * <ul>
 *   <li>作为电子设计任务的<b>容器</b>：归集原理图、PCB 设计等设计成果</li>
 *   <li>通过 {@code domainOid} 归属到某个电子域实例</li>
 *   <li>通过 {@code relatedProduct} 关联到所属产品（产品系列 / 型号）</li>
 *   <li>通过 {@code projectPhase} 跟踪项目阶段（计划 / 设计 / 评审 / 发布等）</li>
 *   <li>通过 {@code parentOid} 支持项目分层（父项目 / 子项目）</li>
 * </ul>
 *
 * <h3>关联关系</h3>
 * <pre>
 * EcadDomain 1 ── N  EcadProject       （域 → 项目，domain_oid）
 * EcadProject 1 ── N  EcadProject      （父子项目，parent_oid）
 * EcadProject 1 ── N  设计成果          （经 ck_ecad_project_design_link 关联原理图 / PCB 设计）
 * </pre>
 *
 * <p>主键与审计字段（oid / creator / createdAt / updater / updatedAt）继承 {@link BaseEntity}。
 */
public class EcadProject extends BaseEntity implements TenantEntity {

    /** 项目编码（业务唯一键，如 ECAD-PRJ-2026-001） */
    private String code;

    /** 项目名称 */
    private String name;

    /** 项目描述 */
    private String description;

    /** 容器类型（默认 ECAD_PROJECT，预留扩展其它电子容器） */
    private String containerType;

    /** 父项目 oid（自引用，支持项目分层；顶层项目为 null） */
    private String parentOid;

    /** 所属电子域 oid（引用 ck_ecad_domain.oid） */
    private String domainOid;

    /** 关联产品 oid（产品系列 / 产品型号） */
    private String relatedProduct;

    /**
     * 项目阶段：
     * {@code PLAN} 计划 / {@code DESIGN} 设计 / {@code REVIEW} 评审 /
     * {@code RELEASED} 已发布 / {@code ARCHIVED} 已归档（默认 PLAN）
     */
    private String projectPhase;

    /** 项目负责人 */
    private String owner;

    /** 租户 oid */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public EcadProject() {
    }

    public EcadProject(String code, String name) {
        this.code = code;
        this.name = name;
    }

    // ==================== Getter / Setter ====================

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public String getParentOid() {
        return parentOid;
    }

    public void setParentOid(String parentOid) {
        this.parentOid = parentOid;
    }

    public String getDomainOid() {
        return domainOid;
    }

    public void setDomainOid(String domainOid) {
        this.domainOid = domainOid;
    }

    public String getRelatedProduct() {
        return relatedProduct;
    }

    public void setRelatedProduct(String relatedProduct) {
        this.relatedProduct = relatedProduct;
    }

    public String getProjectPhase() {
        return projectPhase;
    }

    public void setProjectPhase(String projectPhase) {
        this.projectPhase = projectPhase;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }
}
