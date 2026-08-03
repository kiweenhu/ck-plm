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
 * 用户管理服务接口，提供用户 CRUD、密码管理和角色分配能力。
 */
public interface UserService {

    User create(User user, String rawPassword);

    /**
     * 创建用户（使用已编码的密码），用于租户审核通过后恢复管理员账号。
     */
    User createWithEncodedPassword(User user, String encodedPassword);

    User update(User user);

    /**
     * 更新个人资料（仅 displayName / email / phone 字段）。
     */
    User updateProfile(User user);

    void changePassword(String userOid, String oldPassword, String newPassword);

    void resetPassword(String userOid, String newPassword);

    boolean delete(String oid);

    User findByOid(String oid);

    User findByUsername(String username);

    List<User> findAll();

    List<User> findByOrg(String orgOid);

    List<User> search(String keyword);

    boolean existsByUsername(String username);

    void assignRole(String userOid, String roleOid);

    void revokeRole(String userOid, String roleOid);

    List<Role> getUserRoles(String userOid);

    /** 根据角色 oid 查询已分配该角色的用户列表 */
    List<User> findUsersByRoleOid(String roleOid);
}
