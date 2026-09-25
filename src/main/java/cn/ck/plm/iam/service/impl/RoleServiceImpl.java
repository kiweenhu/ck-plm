/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.RoleMember;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.mapper.RoleMemberMapper;
import cn.ck.plm.iam.mapper.UserMapper;
import cn.ck.plm.iam.service.api.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link RoleService} 的数据库实现。
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper mapper;
    private final UserMapper userMapper;
    private final RoleMemberMapper roleMemberMapper;

    public RoleServiceImpl(RoleMapper mapper, UserMapper userMapper, RoleMemberMapper roleMemberMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.roleMemberMapper = roleMemberMapper;
    }

    @Override
    @Transactional
    public Role create(Role role) {
        if (role == null || role.getCode() == null || role.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        String code = role.getCode().trim();
        if (mapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("角色编码 '" + code + "' 已存在");
        }
        mapper.insert(role);
        return role;
    }

    @Override
    @Transactional
    public Role update(Role role) {
        if (role == null || role.getOid() == null) {
            throw new IllegalArgumentException("角色 oid 不能为空");
        }
        Role existing = mapper.selectByOid(role.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        mapper.update(role);
        existing.setName(role.getName());
        existing.setDescription(role.getDescription());
        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        if (mapper.selectByOid(oid) == null) {
            return false;
        }
        mapper.deleteByOid(oid);
        return true;
    }

    @Override
    public Role findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return mapper.selectByOid(oid);
    }

    @Override
    public Role findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return mapper.selectByCode(code.trim());
    }

    @Override
    public List<Role> findAll() {
        return mapper.selectAll();
    }

    @Override
    public List<Role> findByUser(String userOid) {
        if (userOid == null) {
            return java.util.Collections.emptyList();
        }
        return mapper.selectByUserOid(userOid);
    }

    @Override
    public List<Role> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return mapper.search(keyword.trim());
    }

    @Override
    public boolean existsByCode(String code) {
        return code != null && mapper.existsByCode(code.trim()) > 0;
    }

    // ==================== 平台角色与角色成员 ====================

    @Override
    public List<Role> findPlatformRoles() {
        return findAll().stream()
                .filter(Role::isPlatform)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findRoleMembers(String roleOid) {
        if (roleOid == null || roleOid.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return userMapper.selectByRoleOid(roleOid);
    }

    @Override
    @Transactional
    public void addRoleMember(String roleOid, String userOid) {
        if (roleOid == null || roleOid.trim().isEmpty()) {
            throw new IllegalArgumentException("角色 oid 不能为空");
        }
        if (userOid == null || userOid.trim().isEmpty()) {
            throw new IllegalArgumentException("用户 oid 不能为空");
        }
        Role role = mapper.selectByOid(roleOid);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        User user = userMapper.selectByOid(userOid);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 防重复：该用户已在角色中则幂等跳过
        boolean exists = roleMemberMapper.selectByUserOid(userOid).stream()
                .anyMatch(rm -> roleOid.equals(rm.getRoleOid()));
        if (exists) {
            return;
        }
        RoleMember rm = new RoleMember(userOid, roleOid);
        rm.setTenantOid(role.getTenantOid());
        rm.setCreatedAt(LocalDateTime.now());
        rm.setUpdatedAt(LocalDateTime.now());
        roleMemberMapper.insert(rm);
    }

    @Override
    @Transactional
    public void removeRoleMember(String roleOid, String userOid) {
        if (roleOid == null || userOid == null) {
            throw new IllegalArgumentException("角色与用户 oid 不能为空");
        }
        roleMemberMapper.deleteByUserOidAndRoleOid(userOid, roleOid);
    }

    @Override
    public java.util.Map<String, Object> findAdminMembers() {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        Role adminRole = mapper.selectByCode("TENANT_ADMIN");
        if (adminRole == null) {
            result.put("roleOid", null);
            result.put("roleName", null);
            result.put("members", java.util.Collections.emptyList());
            return result;
        }
        result.put("roleOid", adminRole.getOid());
        result.put("roleName", adminRole.getName());
        result.put("members", userMapper.selectByRoleOid(adminRole.getOid()));
        return result;
    }
}
