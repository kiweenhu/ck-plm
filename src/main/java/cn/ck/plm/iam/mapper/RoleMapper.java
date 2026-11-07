/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.Role;

import java.util.List;

/**
 * 角色数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>由 {@code mapper.impl.PostgreSqlRoleMapper} 对接 PostgreSQL。
 */
public interface RoleMapper {

    int insert(Role role);

    int update(Role role);

    int deleteByOid(String oid);

    Role selectByOid(String oid);

    Role selectByCode(String code);

    /**
     * 按「租户 + code」查询，显式限定租户，不依赖租户拦截器注入。
     *
     * <p>{@link #selectByCode(String)} 靠拦截器补 {@code tenant_oid} 条件，只能在有
     * {@code TenantContext} 的请求里用；启动期（如 {@code SysAdminInitializer}）没有上下文，
     * 同码角色跨多个租户各有一行，会抛 TooManyResultsException —— 平台级初始化必须走本方法。
     */
    Role selectByTenantAndCode(String tenantOid, String code);

    List<Role> selectAll();

    List<Role> selectByUserOid(String userOid);

    List<Role> search(String keyword);

    int existsByCode(String code);
}
