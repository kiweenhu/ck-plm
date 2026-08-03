/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.User;

import java.util.List;

/**
 * 用户数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>由 {@code mapper.impl.PostgreSqlUserMapper} 对接 PostgreSQL。
 */
public interface UserMapper {

    int insert(User user);

    int update(User user);

    /**
     * 仅更新个人资料字段（displayName / email / phone），不影响 enabled/locked 等管理字段。
     */
    int updateProfile(User user);

    int updatePassword(User user);

    int deleteByOid(String oid);

    User selectByOid(String oid);

    User selectByUsername(String username);

    List<User> selectAll(String tenantOid);

    List<User> selectByOrgOid(String orgOid, String tenantOid);

    List<User> search(String keyword, String tenantOid);

    int existsByUsername(String username);

    /** 根据角色 oid 查询已分配该角色的用户列表 */
    List<User> selectByRoleOid(String roleOid);
}
