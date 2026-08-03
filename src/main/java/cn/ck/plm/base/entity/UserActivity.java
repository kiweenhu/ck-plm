/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.entity;

/**
 * 用户活动记录 —— 追踪访问、操作、登录、注销等行为。
 *
 * <h3>活动类型</h3>
 * <ul>
 *   <li>ACCESS —— 页面访问</li>
 *   <li>OPERATION —— 业务操作（检出、检入、创建、删除等）</li>
 *   <li>LOGIN —— 登录</li>
 *   <li>LOGOUT —— 注销</li>
 * </ul>
 */
public class UserActivity extends BaseEntity implements TenantEntity {

    /** 操作用户 */
    private String userOid;

    /** 活动类型：ACCESS / OPERATION / LOGIN / LOGOUT */
    private String activityType;

    /** 目标名称（如产品系列名、文档名） */
    private String targetName;

    /** 目标类型（如 产品系列、文档、变更单） */
    private String targetType;

    /** 目标跳转路径 */
    private String targetPath;

    /** 操作描述（如"检出文档"、"用户登录"） */
    private String actionDesc;

    // ============ 扩展维度 ============

    /** 操作来源 IP */
    private String operatorIp;

    /** 浏览器 User-Agent */
    private String userAgent;

    /** 操作结果：SUCCESS / FAIL */
    private String result;

    /** 操作耗时（毫秒） */
    private Integer durationMs;

    /** 错误信息（失败时记录） */
    private String errorMessage;

    /** 变更详情快照（JSON） */
    private String detailJson;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造 ====================
    public UserActivity() { }

    // ==================== Getter/Setter ====================
    public String getUserOid() { return userOid; }
    public void setUserOid(String userOid) { this.userOid = userOid; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getActionDesc() { return actionDesc; }
    public void setActionDesc(String actionDesc) { this.actionDesc = actionDesc; }

    public String getOperatorIp() { return operatorIp; }
    public void setOperatorIp(String operatorIp) { this.operatorIp = operatorIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
