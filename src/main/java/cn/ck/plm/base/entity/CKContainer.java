/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 容器基类（CKContainer）—— 所有"可承载业务对象"的容器节点的公共父类。
 *
 * <p>参考 Windchill WTContainer / PDMLinkProduct：容器是业务对象的归属上下文，
 * Part / Document / Functional 通过 {@link MasterEntity#getContainerOid()} 指向容器节点。
 *
 * <h3>继承体系（分表策略不变，本基类仅消除 Java 层字段重复）</h3>
 * <pre>
 * BaseEntity → WithoutVersionEntity → CKContainer(this)
 *                                        ├── ProductLine        (ck_product_line)
 *                                        │     └── ProductModel (ck_product_model)
 *                                        └── ResourceContainer  (ck_resource_container)
 * </pre>
 *
 * <h3>containerType 取值</h3>
 * <pre>
 * PRODUCT_LINE   产品系列
 * PRODUCT_MODEL  产品型号
 * CORP_RESOURCE  企业级资源库（根节点及其子资源库节点）
 * </pre>
 */
public abstract class CKContainer extends WithoutVersionEntity implements TenantEntity {

    /** 容器类型：PRODUCT_LINE / PRODUCT_MODEL / CORP_RESOURCE */
    private String containerType;

    /** 缩略图（图片路径或 URL） */
    private String thumbnail;

    /** 关联团队 oid */
    private String teamOid;

    /** 父级容器 oid（自引用，支持多级树形结构，null 表示根节点） */
    private String parentOid;

    /** 子节点列表（仅用于树形查询返回，不持久化） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private transient List<? extends CKContainer> children;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    /** 逻辑删除标记（true 表示已删除，进入回收站，可恢复） */
    private Boolean deleteMark;

    protected CKContainer() {
        super();
    }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public String getTeamOid() { return teamOid; }
    public void setTeamOid(String teamOid) { this.teamOid = teamOid; }

    public String getParentOid() { return parentOid; }
    public void setParentOid(String parentOid) { this.parentOid = parentOid; }

    @SuppressWarnings("unchecked")
    public <T extends CKContainer> List<T> getChildren() { return (List<T>) children; }
    public void setChildren(List<? extends CKContainer> children) { this.children = children; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public Boolean getDeleteMark() { return deleteMark; }
    public void setDeleteMark(Boolean deleteMark) { this.deleteMark = deleteMark; }

    /** 是否为逻辑删除状态 */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleteMark);
    }
}
