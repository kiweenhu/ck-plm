/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.entity.Organization;
import cn.ck.plm.iam.mapper.OrganizationMapper;
import cn.ck.plm.iam.mapper.UserMapper;
import cn.ck.plm.iam.service.api.OrganizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link OrganizationService} 的数据库实现。
 */
@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationMapper mapper;
    private final UserMapper userMapper;

    public OrganizationServiceImpl(OrganizationMapper mapper, UserMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public Organization create(Organization org) {
        if (org == null || org.getCode() == null || org.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("组织编码不能为空");
        }
        String code = org.getCode().trim();
        if (mapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("组织编码 '" + code + "' 已存在");
        }
        if (org.getParentOid() != null && mapper.selectByOid(org.getParentOid()) == null) {
            throw new IllegalArgumentException("父组织不存在");
        }
        mapper.insert(org);
        return org;
    }

    @Override
    @Transactional
    public Organization update(Organization org) {
        if (org == null || org.getOid() == null) {
            throw new IllegalArgumentException("组织 oid 不能为空");
        }
        Organization existing = mapper.selectByOid(org.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("组织不存在");
        }
        mapper.update(org);
        existing.setName(org.getName());
        existing.setParentOid(org.getParentOid());
        existing.setDescription(org.getDescription());
        existing.setEnabled(org.isEnabled());
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
        List<Organization> children = mapper.selectByParentOid(oid);
        if (children != null && !children.isEmpty()) {
            throw new IllegalStateException("该组织下存在 " + children.size() + " 个子组织，请先删除子组织");
        }
        // 检查部门下是否有用户
        List<?> users = userMapper.selectByOrgOid(oid, cn.ck.plm.base.util.TenantContext.get());
        if (users != null && !users.isEmpty()) {
            throw new IllegalStateException("该部门下存在 " + users.size() + " 个用户，请先将用户移出或删除");
        }
        mapper.deleteByOid(oid);
        return true;
    }

    @Override
    public Organization findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return mapper.selectByOid(oid);
    }

    @Override
    public Organization findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return mapper.selectByCode(code.trim());
    }

    @Override
    public List<Organization> findAll() {
        return mapper.selectAll();
    }

    @Override
    public List<Organization> findChildren(String parentOid) {
        if (parentOid == null) {
            return findRoots();
        }
        return mapper.selectByParentOid(parentOid);
    }

    @Override
    public List<Organization> findRoots() {
        return mapper.selectRoots();
    }

    @Override
    public List<Organization> findTree() {
        List<Organization> roots = mapper.selectRoots();
        for (Organization root : roots) {
            buildTree(root);
        }
        return roots;
    }

    private void buildTree(Organization parent) {
        List<Organization> children = mapper.selectByParentOid(parent.getOid());
        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            for (Organization child : children) {
                buildTree(child);
            }
        }
    }

    @Override
    public List<Organization> search(String keyword) {
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
