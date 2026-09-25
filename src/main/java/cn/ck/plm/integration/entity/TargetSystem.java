/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 目标系统注册表 —— 流程里"要调用的外部系统"由管理员在这里维护一次。
 *
 * <h3>为什么要有这张表（而不是把地址与凭据填在流程节点上）</h3>
 * <p>把 baseUrl / 账号 / 密钥填在流程节点上会有三个后果：改一次地址要改所有流程；
 * 凭据随流程 DSL 复制、导出、进版本历史；同一个系统在十个节点里有十份配置，
 * 到底调的是环境里的哪一台谁也说不清。这与通知渠道踩过的坑完全一样
 * （见 {@code NotificationProperties} 的类注释），所以照那条路走：
 * <b>地址与凭据在这里配一次，流程节点只填 systemCode + 路径 + 参数</b>。
 *
 * <h3>多租户</h3>
 * <p>实现 {@link TenantEntity}，由租户拦截器自动注入 {@code tenant_oid} 过滤 ——
 * <b>刻意不做平台共享</b>：目标系统的地址与凭据是租户自己的集成资产，
 * 跨租户可见等于把某家企业的 ERP 地址与账号暴露给别家。
 */
public class TargetSystem extends BaseEntity implements TenantEntity {

    /** 系统编码：流程节点上引用的就是它（租户内唯一，改名不影响已部署流程） */
    private String code;

    /** 系统名称（给人看的，如「集团 ERP」） */
    private String name;

    /** 根地址，不含接口路径（如 https://erp.example.com） */
    private String baseUrl;

    /** 认证方式：NONE / BASIC / BEARER / API_KEY（与系统集成节点同一套取值） */
    private String authType;

    /** Basic 用户名；API_KEY 认证时这里是<b>请求头名称</b>（如 X-API-Key） */
    private String username;

    /** 凭据（Basic 口令 / Bearer Token / API Key 取值）—— 接口不回传，见 Controller */
    private String secret;

    /** 是否启用：停用后引用它的流程在运行期会明确报错，而不是静默跳过 */
    private Boolean enabled;

    /** 排序 */
    private Integer sortOrder;

    /** 描述：这个系统是干什么的、找谁要凭据 */
    private String description;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    public TargetSystem() { }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    /**
     * 是否已配置凭据（回传给前端：不回传明文，但要能显示"已配置"）。
     *
     * <p>刻意用<b>独立字段</b>而不是从 {@code secret} 派生：Controller 会在返回前把 secret
     * 抹成 null，派生写法抹完就永远报 false（冒烟测试里当场暴露：明明建的时候给了凭据，
     * 列表里却显示"未配置"）。
     */
    private Boolean secretSet;

    public Boolean getSecretSet() { return secretSet; }
    public void setSecretSet(Boolean secretSet) { this.secretSet = secretSet; }
}
