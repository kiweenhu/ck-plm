/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 标准件入库流程配置（<b>一个租户一份</b>）。
 *
 * <p>用途：标准件（国标/行标件）入「标准件库」时，是否要经过一道入库流程
 * （标准化岗确认标准号/规格、确认不与库内已有件重复）再正式入库。
 * 走哪条流程由 {@code processTemplateOid} 绑定 —— <b>跨模块 oid 软引用</b>
 * （指向 {@code ck_process_template}，不加外键），与「类型-状态绑流程」「通用件认定流程」同一做法。
 *
 * <p>为什么这也要配置化：有的企业标准件直接引用标准号即可、无需审批；有的企业要求
 * 标准化岗逐个确认后入库。同一套代码服务两种管理强度，靠配置而不是发版。
 */
public class StdPartInboundConfig extends BaseEntity implements TenantEntity {

    /** 是否启用入库流程（关掉 = 标准件可直接入库，不发起流程） */
    private Boolean enabled;

    /** 入库流程模板 oid（软引用 ck_process_template.oid；启用时必填才有意义） */
    private String processTemplateOid;

    /** 说明（比如"哪些标准件可以免走流程"，写给下一个看配置的人） */
    private String description;

    /** 租户 oid */
    private String tenantOid;

    /** 未配置时的默认：不启用流程（保持既有行为，不给存量数据凭空加一道审批） */
    public static StdPartInboundConfig defaults() {
        StdPartInboundConfig config = new StdPartInboundConfig();
        config.setEnabled(Boolean.FALSE);
        return config;
    }

    // ==================== Getter / Setter ====================

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getProcessTemplateOid() { return processTemplateOid; }
    public void setProcessTemplateOid(String processTemplateOid) { this.processTemplateOid = processTemplateOid; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
