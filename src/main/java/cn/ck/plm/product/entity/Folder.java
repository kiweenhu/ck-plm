/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 文件夹实体，用于在某个业务对象（如产品线/产品型号）的特定研发阶段下组织文档。
 *
 * <p>支持树形嵌套结构（通过 {@code parentFolderOid} 自引用），
 * 不同文件夹可以存储不同的市场验证/需求论证等阶段文档。
 *
 * <h3>关系</h3>
 * <pre>
 * Folder  N ── 1  业务对象   (ownerOid)
 * Folder  N ── 1  Folder    (parentFolderOid 自引用)
 * Folder  1 ── N  Document  (通过 folderOid 关联)
 * </pre>
 */
public class Folder extends BaseEntity implements TenantEntity {

    /** 系统创建的文件夹，不可删除 */
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** 用户创建的文件夹 */
    public static final String TYPE_USER = "USER";

    /** 所属业务对象 oid（可以是产品线、产品型号等） */
    private String ownerOid;

    /** 所属阶段 oid（关联 ck_stage.oid） */
    private String stageOid;

    /** 父文件夹 oid（null 表示根文件夹） */
    private String parentFolderOid;

    /** 文件夹名称 */
    private String name;

    /** 文件夹类型：SYSTEM（系统创建，不可删除） / USER（用户创建） */
    private String type = TYPE_USER;

    /** 排序序号 */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    /** 子文件夹列表（仅用于树形查询返回，不持久化） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private transient List<Folder> children;

    // ==================== 构造方法 ====================

    public Folder() {
        super();
    }

    // ==================== 便捷方法 ====================

    /** 是否为系统文件夹（不可删除） */
    public boolean isSystem() {
        return TYPE_SYSTEM.equals(this.type);
    }

    // ==================== Getter / Setter ====================

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    public String getParentFolderOid() { return parentFolderOid; }
    public void setParentFolderOid(String parentFolderOid) { this.parentFolderOid = parentFolderOid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public List<Folder> getChildren() { return children; }
    public void setChildren(List<Folder> children) { this.children = children; }

    @Override
    public String toString() {
        return "Folder{name='" + name + "', type='" + type + "', stageOid='" + stageOid
                + "', ownerOid='" + ownerOid + "', parentFolderOid='" + parentFolderOid + "'}";
    }
}
