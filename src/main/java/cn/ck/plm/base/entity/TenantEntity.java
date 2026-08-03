/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 租户实体标记接口 —— 实现此接口的实体表明其数据需要按租户隔离。
 *
 * <p><b>使用规则：</b>
 * <ul>
 *   <li>业务实体（Document, ProductLine, User, Team 等）→ 实现此接口，拥有独立 tenantOid 字段</li>
 *   <li>系统配置实体（LifecycleTemplate, IBA, Number 等）→ <b>不实现</b>此接口</li>
 * </ul>
 *
 * <p>{@link cn.ck.plm.base.config.AuditInterceptor} 会在 INSERT 时自动填充 tenantOid;
 * {@link cn.ck.plm.base.config.TenantInterceptor} 会在 SELECT/UPDATE/DELETE 时自动注入
 * {@code WHERE tenant_oid = ?} 过滤条件。
 *
 * <h3>设计说明</h3>
 * <p>隔离列使用 {@code tenant_oid CHAR(36)} 引用 {@code ck_tenant.oid}，
 * 而非 {@code tenant_id VARCHAR(50)}。原因：
 * <ul>
 *   <li>tenant_id 是业务标识，随企业发展可能修改</li>
 *   <li>oid 是数据库主键，永不改变，适合作为外键隔离列</li>
 *   <li>需要显示租户标识时，通过 JOIN ck_tenant 获取最新 tenant_id</li>
 * </ul>
 *
 * <h3>多阶段扩展</h3>
 * <ul>
 *   <li>Phase 1 (当前): 列隔离 — tenant_oid CHAR(36) 引用 ck_tenant.oid</li>
 *   <li>Phase 2 (未来): Schema 隔离 — tenant_oid 映射到 PostgreSQL schema</li>
 *   <li>Phase 3 (未来): 实例隔离 — tenant_oid 路由到不同 DataSource</li>
 * </ul>
 */
public interface TenantEntity {

    /** 获取租户 oid（引用 ck_tenant.oid） */
    String getTenantOid();

    /** 设置租户 oid（引用 ck_tenant.oid） */
    void setTenantOid(String tenantOid);
}
