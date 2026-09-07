/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 电子元器件分类，企业资源库-电子元器件库的左侧分类树节点。
 *
 * <p>通过 {@code parentCategoryOid} 自引用支持多层分类（如 电阻 → 贴片电阻）。
 */
public class ComponentCategory extends BaseEntity implements TenantEntity {

    /** 分类名称 */
    private String name;

    /** 父分类 oid（null 表示根分类） */
    private String parentCategoryOid;

    /** 排序序号 */
    private Integer sortOrder;

    /** 租户 oid */
    private String tenantOid;

    /** 子分类列表（仅用于树形查询返回，不持久化） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private transient List<ComponentCategory> children;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParentCategoryOid() { return parentCategoryOid; }
    public void setParentCategoryOid(String parentCategoryOid) { this.parentCategoryOid = parentCategoryOid; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public List<ComponentCategory> getChildren() { return children; }
    public void setChildren(List<ComponentCategory> children) { this.children = children; }
}
