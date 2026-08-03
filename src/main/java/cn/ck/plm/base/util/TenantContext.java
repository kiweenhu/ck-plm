/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.util;

/**
 * 租户上下文 —— 基于 ThreadLocal 传递当前请求的租户 oid。
 *
 * <p>与 {@link UserContext} 配合使用：
 * <pre>
 *   AuthInterceptor  →  UserContext.set(username)  +  TenantContext.set(tenantOid)
 *   TenantInterceptor →  从 TenantContext 读取租户 oid 注入 SQL
 *   afterCompletion   →  clear()
 * </pre>
 *
 * <p>注意：上下文中存储的是 {@code ck_tenant.oid}（UUID），而非 {@code tenant_id}（业务标识）。
 * tenant_id 可能因企业更名而修改，oid 作为主键永不改变。
 *
 * <p><b>Phase 2/3 扩展：</b>
 * <ul>
 *   <li>Phase 2 (Schema): tenantOid 映射到 PostgreSQL schema 名</li>
 *   <li>Phase 3 (实例): tenantOid 路由到不同 DataSource</li>
 * </ul>
 */
public final class TenantContext {

    /** 默认租户 oid */
    public static final String DEFAULT_TENANT_OID = "00000000-0000-0000-0000-000000000001";
    /** 平台层租户 oid */
    public static final String PLATFORM_TENANT_OID = "00000000-0000-0000-0000-000000000000";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 设置当前租户 oid */
    public static void set(String tenantOid) {
        CURRENT_TENANT.set(tenantOid);
    }

    /** 获取当前租户 oid，未设置返回默认租户 oid */
    public static String get() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT_OID;
    }

    /** 获取当前租户 oid，未设置返回 null（区分"未设置"和"默认"的场景） */
    public static String getOrNull() {
        return CURRENT_TENANT.get();
    }

    /** 请求结束清理 */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * 检查给定的 tenantOid 是否属于平台租户。
     * 平台数据所有租户可读，但不可编辑或删除。
     */
    public static boolean isPlatform(String tenantOid) {
        return PLATFORM_TENANT_OID.equals(tenantOid);
    }

    /**
     * 检查当前租户是否是平台租户（sysadmin 登录时）。
     */
    public static boolean isCurrentPlatform() {
        return PLATFORM_TENANT_OID.equals(get());
    }

    /**
     * 验证租户是否有权编辑/删除：平台级数据只有平台租户可以操作。
     * 非平台租户尝试操作平台数据时抛出异常。
     *
     * @param entityTenantOid 实体的 tenant_oid
     * @param entityName      实体名称（用于错误消息）
     * @throws IllegalStateException 如果当前租户无权操作
     */
    public static void requireEditPermission(String entityTenantOid, String entityName) {
        if (isPlatform(entityTenantOid) && !isCurrentPlatform()) {
            throw new IllegalStateException("平台级" + entityName + "不可编辑或删除，如需自定义请先克隆到本租户");
        }
    }
}
