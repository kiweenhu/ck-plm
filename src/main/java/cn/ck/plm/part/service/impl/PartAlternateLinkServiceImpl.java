/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.impl;

import cn.ck.plm.part.entity.PartAlternateLink;
import cn.ck.plm.part.mapper.PartAlternateLinkMapper;
import cn.ck.plm.part.service.api.PartAlternateLinkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PartAlternateLink 业务服务实现。
 */
@Service
public class PartAlternateLinkServiceImpl implements PartAlternateLinkService {

    private final PartAlternateLinkMapper mapper;

    public PartAlternateLinkServiceImpl(PartAlternateLinkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PartAlternateLink create(PartAlternateLink link) {
        if (link.getOid() == null || link.getOid().isEmpty()) {
            link.setOid(UUID.randomUUID().toString());
        }
        // 规范化：roleA 与 roleB 按字典序排序（约定 roleA < roleB），保证有序对唯一约束
        normalizeOrderedPair(link);
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(LocalDateTime.now());
        }
        if (link.getUpdatedAt() == null) {
            link.setUpdatedAt(LocalDateTime.now());
        }
        mapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public PartAlternateLink update(PartAlternateLink link) {
        PartAlternateLink existing = mapper.selectByOid(link.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("替代关系不存在: " + link.getOid());
        }
        existing.setCode(link.getCode());
        existing.setName(link.getName());
        existing.setDescription(link.getDescription());
        existing.setRoleAPartOid(link.getRoleAPartOid());
        existing.setRoleBPartOid(link.getRoleBPartOid());
        existing.setAlternateType(link.getAlternateType());
        existing.setAlternateQuantity(link.getAlternateQuantity());
        existing.setAlternateUnit(link.getAlternateUnit());
        existing.setEnabled(link.getEnabled());
        existing.setEffectivityJson(link.getEffectivityJson());
        normalizeOrderedPair(existing);
        existing.setUpdatedAt(LocalDateTime.now());
        mapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        mapper.deleteByOid(oid);
    }

    @Override
    public PartAlternateLink findByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    @Override
    public List<PartAlternateLink> findByRoleAPart(String roleAPartOid) {
        return mapper.selectByRoleAPartOid(roleAPartOid);
    }

    @Override
    public List<PartAlternateLink> findByRoleBPart(String roleBPartOid) {
        return mapper.selectByRoleBPartOid(roleBPartOid);
    }

    @Override
    public List<PartAlternateLink> findByPart(String partOid) {
        return mapper.selectByPartOid(partOid);
    }

    @Override
    public List<PartAlternateLink> listAll() {
        return mapper.selectAll();
    }

    /** 规范化有序对：确保 roleA 字典序小于 roleB，避免「两条记录表达一个事实」 */
    private void normalizeOrderedPair(PartAlternateLink link) {
        String a = link.getRoleAPartOid();
        String b = link.getRoleBPartOid();
        if (a != null && b != null && a.compareTo(b) > 0) {
            link.setRoleAPartOid(b);
            link.setRoleBPartOid(a);
        }
    }
}
