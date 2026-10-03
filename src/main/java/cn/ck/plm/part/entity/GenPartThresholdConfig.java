/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 通用件阈值配置（<b>一个租户一行</b>）。
 *
 * <p>用途：把"企业自研的结构件"按<b>使用度</b>追认为可跨产品系列/型号重用的<b>通用件</b>——
 * 定时统计零件被多少个型号、多少条 BOM 行引用，达到本配置的阈值就把候选通知管理员，
 * 由管理员按绑定的<b>流程模板</b>发起认定，流程结束再落库改类型、迁通用件库。
 *
 * <p>为什么阈值放配置表而不是写死在代码里：不同企业的"用得多"标准不一样，且阈值一旦调整，
 * 候选口径要跟着变；写死就得改代码发版。
 *
 * <p>{@code processTemplateOid} 是<b>跨模块 oid 软引用</b>（指向 {@code ck_process_template}，
 * 不加外键）—— 与「类型-生命周期状态-流程模板」绑定表的既有做法一致。
 */
public class GenPartThresholdConfig extends BaseEntity implements TenantEntity {

    /** 是否启用统计（关掉只留配置、不触发候选与通知） */
    private Boolean enabled;

    /** 跨型号数阈值：被多少个<b>不同型号</b>引用才算候选（达到即算） */
    private Integer minModelCount;

    /** 引用次数阈值：被多少条 BOM 行引用才算候选（达到即算） */
    private Integer minUsageCount;

    /** 统计窗口（月）：只看近 N 个月的引用；0 或空 = 不限时间 */
    private Integer statWindowMonths;

    /** 适用类型 code（默认 STRUCTURAL：认定只针对企业自研结构件，不动电子件/标准件） */
    private String scopeTypeCode;

    /** 认定流程模板 oid（软引用 ck_process_template.oid；空 = 只通知不自动发起） */
    private String processTemplateOid;

    /** 说明（写给下一个看配置的人） */
    private String description;

    /** 租户 oid */
    private String tenantOid;

    // ==================== 默认值（未配置时读出来的兜底口径）====================

    /** 未配置时的默认：启用、跨 3 个型号、引用 5 次、近 12 个月、只针对结构件 */
    public static GenPartThresholdConfig defaults() {
        GenPartThresholdConfig config = new GenPartThresholdConfig();
        config.setEnabled(Boolean.TRUE);
        config.setMinModelCount(3);
        config.setMinUsageCount(5);
        config.setStatWindowMonths(12);
        config.setScopeTypeCode("STRUCTURAL");
        return config;
    }

    // ==================== Getter / Setter ====================

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getMinModelCount() { return minModelCount; }
    public void setMinModelCount(Integer minModelCount) { this.minModelCount = minModelCount; }

    public Integer getMinUsageCount() { return minUsageCount; }
    public void setMinUsageCount(Integer minUsageCount) { this.minUsageCount = minUsageCount; }

    public Integer getStatWindowMonths() { return statWindowMonths; }
    public void setStatWindowMonths(Integer statWindowMonths) { this.statWindowMonths = statWindowMonths; }

    public String getScopeTypeCode() { return scopeTypeCode; }
    public void setScopeTypeCode(String scopeTypeCode) { this.scopeTypeCode = scopeTypeCode; }

    public String getProcessTemplateOid() { return processTemplateOid; }
    public void setProcessTemplateOid(String processTemplateOid) { this.processTemplateOid = processTemplateOid; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
