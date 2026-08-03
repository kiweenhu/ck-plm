package cn.ck.plm.cls.entity;

import cn.ck.plm.base.entity.WithoutVersionEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 分类管理实体。
 * <p>支持树形层级结构（自引用 parentOid），用于产品分类、资源分类等场景。
 */
public class Classification extends WithoutVersionEntity implements TenantEntity {

    /** 分类标识（业务唯一键，用于 API 路由） */
    private String identifier;

    /** 显示名称 */
    private String displayName;

    /** 缩略图路径 */
    private String thumbnail;

    /** 父分类 oid（自引用树形结构） */
    private String parentOid;

    /** 租户 oid */
    private String tenantOid;

    /** 排序序号 */
    private Integer sortOrder;

    // ===== 非持久化字段 =====
    private transient java.util.List<Classification> children;

    // ===== getter/setter =====

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public String getParentOid() { return parentOid; }
    public void setParentOid(String parentOid) { this.parentOid = parentOid; }

    @Override
    public String getTenantOid() { return tenantOid; }
    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public java.util.List<Classification> getChildren() { return children; }
    public void setChildren(java.util.List<Classification> children) { this.children = children; }
}
