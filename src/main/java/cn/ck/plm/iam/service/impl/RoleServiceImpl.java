/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.service.api.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link RoleService} 的数据库实现。
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper mapper;

    public RoleServiceImpl(RoleMapper mapper) {
        this.mapper = mapper;
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
}
