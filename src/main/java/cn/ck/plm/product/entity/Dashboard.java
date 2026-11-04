/*
 * Copyright (c) 2026~2028 恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

/**
 * 驾驶舱实例：某个产品系列 / 型号自己的战报视角（经营战报 / 系列战况简报 / 型号作战地图）。
 *
 * <p>创建时从驾驶舱模板（{@code ck_dashboard_template}）快照而来：
 * {@code primaryScope} 标记的是"产品经理 / 研发的日常工作面"，原样从模板带过来；
 * {@code widgets} 是分组件码的 JSON 数组。
 */
public class Dashboard extends WithoutVersionEntity implements TenantEntity {

    /** 所属归属单元 oid（产品系列或产品型号） */
    private String ownerOid;

    /** 归属类型：LINE / MODEL（见 {@link Stage#OWNER_TYPE_LINE}） */
    private String ownerType;

    /** 受众（GM / GM+RDD / RD/PM） */
    private String audience;

    /** 页数 */
    private Integer pageCount;

    /** 分组件码（JSON 数组字符串） */
    private String widgets;

    /** 生成节奏（MONTHLY_ALWAYS / MONTHLY_IF_ACTIVE / ON_DEMAND_OR_THRESHOLD） */
    private String generatePolicy;

    /** 是否主视角（= 模板的 primaryScope，原样快照） */
    private Boolean primaryScope;

    private String industryNote;

    /** 溯源：从哪条模板快照而来 */
    private String templateCode;

    /** 最近一次生成时间（P4 起由生成任务回填） */
    private java.time.LocalDateTime lastGeneratedAt;

    /** 本项目裁剪：停用 */
    private Boolean disabled;

    private Integer sortOrder;

    private String tenantOid;

    public Dashboard() {}

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public String getWidgets() { return widgets; }
    public void setWidgets(String widgets) { this.widgets = widgets; }

    public String getGeneratePolicy() { return generatePolicy; }
    public void setGeneratePolicy(String generatePolicy) { this.generatePolicy = generatePolicy; }

    public Boolean getPrimaryScope() { return primaryScope; }
    public void setPrimaryScope(Boolean primaryScope) { this.primaryScope = primaryScope; }

    public String getIndustryNote() { return industryNote; }
    public void setIndustryNote(String industryNote) { this.industryNote = industryNote; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public java.time.LocalDateTime getLastGeneratedAt() { return lastGeneratedAt; }
    public void setLastGeneratedAt(java.time.LocalDateTime lastGeneratedAt) { this.lastGeneratedAt = lastGeneratedAt; }

    public Boolean getDisabled() { return disabled; }
    public void setDisabled(Boolean disabled) { this.disabled = disabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
