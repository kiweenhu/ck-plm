/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.RoleMember;

import java.util.List;

/**
 * 角色成员关联数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>由 {@code mapper.impl.PostgreSqlRoleMemberMapper} 对接 PostgreSQL。
 */
public interface RoleMemberMapper {

    int insert(RoleMember roleMember);

    int deleteByOid(String oid);

    int deleteByUserOidAndRoleOid(String userOid, String roleOid);

    int deleteByUserOid(String userOid);

    List<RoleMember> selectByUserOid(String userOid);

    List<RoleMember> selectByRoleOid(String roleOid);
}
