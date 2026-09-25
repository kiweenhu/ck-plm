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
 * 电子域（ECAD Domain）—— 电子域的「超级域对象」/域锚点实例。
 *
 * <p>本类对应类型定义（{@code ck_type_definition}）中注册的域锚点类型 {@code ECAD_DOMAIN}
 * （typeKind=DOMAIN）的<b>实例</b>：{@code ck_ecad_domain} 表存储具体的域实例
 * （如 ECAD 电子设计域，未来可扩展 MCAD 结构域、SOFTWARE 软件域等），
 * 每个域实例可独立治理、独立授权。
 *
 * <p>电子域下的对象类型（封装 FOOTPRINT、原理图图符 SYMBOL、电子元器件 ELECTRONIC、
 * PCBA、原理图 SCHEMATIC、PCB 设计 PCB_LAYOUT，以及未来新增的类型）作为 SOFT_TYPE
 * <b>直接注册在域锚点类型之下</b>，并通过显式 {@code rootTypeCode}
 * （PART / DOCUMENT）追溯能力宿主，从而：
 * <ul>
 *   <li>域归属由类型树 {@code parentOid} 表达，导航/权限/统计可按域聚合</li>
 *   <li>平台能力（编码规则、版本规则、生命周期、宿主表）由 {@code rootTypeCode} 保证，不受域中间层影响</li>
 *   <li>未来新增电子域对象类型只需在域锚点下新增 softtype，零改表</li>
 * </ul>
 */
public class EcadDomain extends BaseEntity implements TenantEntity {

    /** 域编码（业务唯一，如 ECAD） */
    private String code;

    /** 域名称，如 电子设计域 */
    private String name;

    /** 域描述 */
    private String description;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 oid */
    private String tenantOid;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
