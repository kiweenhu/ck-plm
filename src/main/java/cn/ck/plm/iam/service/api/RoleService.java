/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.api;

import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.User;

import java.util.List;

/**
 * 角色管理服务接口，提供角色的 CRUD 能力与角色成员管理。
 */
public interface RoleService {

    Role create(Role role);

    Role update(Role role);

    boolean delete(String oid);

    Role findByOid(String oid);

    Role findByCode(String code);

    List<Role> findAll();

    List<Role> findByUser(String userOid);

    List<Role> search(String keyword);

    boolean existsByCode(String code);

    /** 查询全部平台级角色（roleType=PLATFORM） */
    List<Role> findPlatformRoles();

    /** 查询某角色的成员用户列表 */
    List<User> findRoleMembers(String roleOid);

    /** 添加角色成员（用户/角色须存在，重复添加幂等跳过） */
    void addRoleMember(String roleOid, String userOid);

    /** 移除角色成员 */
    void removeRoleMember(String roleOid, String userOid);

    /** 查询当前租户的管理员角色（TENANT_ADMIN）及其成员，返回 { roleOid, roleName, members } */
    java.util.Map<String, Object> findAdminMembers();
}
