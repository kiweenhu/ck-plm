/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import java.time.LocalDateTime;

/**
 * 租户已启用能力（表 {@code ck_tenant_capability}）。
 *
 * <p>装行业包时由安装引擎写入：包里声明了、且平台<b>已实现</b>（见
 * {@code PlatformCapabilities}）的能力，才会在这里留一行 —— 这就是"本租户能用哪些功能"的事实来源。
 * 包里声明但平台未实现的（如 CHANGE_MANAGEMENT），只在安装报告里提示跳过，不落这张表。
 *
 * <p>读侧（菜单 / 路由 / 接口）按这张表判定是否展示功能入口 —— 那一步（③）尚未做，
 * 目前它只是"如实记账"：谁在什么时候、因为装了哪个包而获得了哪项能力。
 */
public class TenantCapability {

    /** 主键 */
    private String oid;
    /** 归属租户（平台级包的能力也记在平台租户下） */
    private String tenantOid;
    /** 能力码，如 {@code COMPONENT_MANAGEMENT} */
    private String capabilityCode;
    /** 能力中文名（取自包内声明，便于直接展示与排查） */
    private String capabilityName;
    /** 是否启用（本期落库的都为 true；留给"管理员手动关闭"用） */
    private boolean enabled = true;
    /** 由哪个包声明（{@code electronics-core@1.0.0}）—— 出问题时要能追到源头 */
    private String sourcePack;
    /** 包的说明原文（如"依赖 change 模块，当前未实现"） */
    private String note;

    private String creator;
    private LocalDateTime createdAt;
    private String updater;
    private LocalDateTime updatedAt;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public String getTenantOid() { return tenantOid; }
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
    public String getCapabilityCode() { return capabilityCode; }
    public void setCapabilityCode(String capabilityCode) { this.capabilityCode = capabilityCode; }
    public String getCapabilityName() { return capabilityName; }
    public void setCapabilityName(String capabilityName) { this.capabilityName = capabilityName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSourcePack() { return sourcePack; }
    public void setSourcePack(String sourcePack) { this.sourcePack = sourcePack; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
