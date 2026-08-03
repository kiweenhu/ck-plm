/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.entity.RoleMember;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.mapper.RoleMemberMapper;
import cn.ck.plm.iam.mapper.UserMapper;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.iam.util.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link UserService} 的数据库实现。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final RoleMemberMapper roleMemberMapper;
    private final RoleMapper roleMapper;

    public UserServiceImpl(UserMapper userMapper, RoleMemberMapper roleMemberMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMemberMapper = roleMemberMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional
    public User create(User user, String rawPassword) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        String username = user.getUsername().trim();
        if (userMapper.existsByUsername(username) > 0) {
            throw new IllegalArgumentException("用户名 '" + username + "' 已存在");
        }
        user.setPassword(PasswordEncoder.encode(rawPassword));
        userMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public User createWithEncodedPassword(User user, String encodedPassword) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (encodedPassword == null || encodedPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        String username = user.getUsername().trim();
        if (userMapper.existsByUsername(username) > 0) {
            throw new IllegalArgumentException("用户名 '" + username + "' 已存在");
        }
        user.setPassword(encodedPassword); // 直接使用已编码密码
        userMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public User update(User user) {
        if (user == null || user.getOid() == null) {
            throw new IllegalArgumentException("用户 oid 不能为空");
        }
        User existing = userMapper.selectByOid(user.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 用现有值填充 null 字段，防止前端部分更新时把数据库值置空
        if (user.getDisplayName() == null) user.setDisplayName(existing.getDisplayName());
        if (user.getEmail() == null) user.setEmail(existing.getEmail());
        if (user.getPhone() == null) user.setPhone(existing.getPhone());
        if (user.getOrgOid() == null) user.setOrgOid(existing.getOrgOid());
        if (user.getTenantOid() == null) user.setTenantOid(existing.getTenantOid());
        userMapper.update(user);
        return user;
    }

    @Override
    @Transactional
    public User updateProfile(User user) {
        if (user == null || user.getOid() == null) {
            throw new IllegalArgumentException("用户 oid 不能为空");
        }
        User existing = userMapper.selectByOid(user.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userMapper.updateProfile(user);
        existing.setDisplayName(user.getDisplayName());
        existing.setEmail(user.getEmail());
        existing.setPhone(user.getPhone());
        return existing;
    }

    @Override
    @Transactional
    public void changePassword(String userOid, String oldPassword, String newPassword) {
        if (userOid == null || oldPassword == null || newPassword == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        User user = userMapper.selectByOid(userOid);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!PasswordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        user.setPassword(PasswordEncoder.encode(newPassword));
        userMapper.updatePassword(user);
    }

    @Override
    @Transactional
    public void resetPassword(String userOid, String newPassword) {
        if (userOid == null || newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("参数不能为空");
        }
        User user = userMapper.selectByOid(userOid);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setPassword(PasswordEncoder.encode(newPassword));
        userMapper.updatePassword(user);
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        if (userMapper.selectByOid(oid) == null) {
            return false;
        }
        roleMemberMapper.deleteByUserOid(oid);
        userMapper.deleteByOid(oid);
        return true;
    }

    @Override
    public User findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return userMapper.selectByOid(oid);
    }

    @Override
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return userMapper.selectByUsername(username.trim());
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectAll(TenantContext.get());
    }

    @Override
    public List<User> findByOrg(String orgOid) {
        if (orgOid == null) {
            return findAll();
        }
        String tenantOid = TenantContext.get();
        log.info("findByOrg: orgOid={}, tenantOid={}", orgOid, tenantOid);
        List<User> users = userMapper.selectByOrgOid(orgOid, tenantOid);
        log.info("findByOrg result: {} users found", users != null ? users.size() : 0);
        return users;
    }

    @Override
    public List<User> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return userMapper.search(keyword.trim(), TenantContext.get());
    }

    @Override
    public boolean existsByUsername(String username) {
        return username != null && userMapper.existsByUsername(username.trim()) > 0;
    }

    @Override
    @Transactional
    public void assignRole(String userOid, String roleOid) {
        if (userOid == null || roleOid == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        List<RoleMember> existing = roleMemberMapper.selectByUserOid(userOid);
        if (existing != null) {
            for (RoleMember rm : existing) {
                if (roleOid.equals(rm.getRoleOid())) {
                    return;
                }
            }
        }
        RoleMember roleMember = new RoleMember(userOid, roleOid);
        roleMemberMapper.insert(roleMember);
    }

    @Override
    @Transactional
    public void revokeRole(String userOid, String roleOid) {
        if (userOid == null || roleOid == null) {
            return;
        }
        roleMemberMapper.deleteByUserOidAndRoleOid(userOid, roleOid);
    }

    @Override
    public List<Role> getUserRoles(String userOid) {
        if (userOid == null) {
            return java.util.Collections.emptyList();
        }
        return roleMapper.selectByUserOid(userOid);
    }

    @Override
    public List<User> findUsersByRoleOid(String roleOid) {
        if (roleOid == null || roleOid.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return userMapper.selectByRoleOid(roleOid);
    }
}
