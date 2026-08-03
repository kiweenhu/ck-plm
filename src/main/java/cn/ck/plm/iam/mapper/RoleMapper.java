/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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

    List<Role> selectAll();

    List<Role> selectByUserOid(String userOid);

    List<Role> search(String keyword);

    int existsByCode(String code);
}
