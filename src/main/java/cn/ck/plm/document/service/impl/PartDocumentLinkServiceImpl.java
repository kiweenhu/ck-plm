/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.impl;

import cn.ck.plm.document.entity.PartDocumentLink;
import cn.ck.plm.document.mapper.PartDocumentLinkMapper;
import cn.ck.plm.document.service.api.PartDocumentLinkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PartDocumentLink 业务服务实现。
 */
@Service
public class PartDocumentLinkServiceImpl implements PartDocumentLinkService {

    private final PartDocumentLinkMapper mapper;

    public PartDocumentLinkServiceImpl(PartDocumentLinkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PartDocumentLink create(PartDocumentLink link) {
        if (link.getOid() == null || link.getOid().isEmpty()) {
            link.setOid(UUID.randomUUID().toString());
        }
        if (link.getLinkType() == null || link.getLinkType().isEmpty()) {
            link.setLinkType(PartDocumentLink.TYPE_REFERENCE);
        }
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(LocalDateTime.now());
        }
        if (link.getUpdatedAt() == null) {
            link.setUpdatedAt(LocalDateTime.now());
        }
        // 防重复：同一部件 + 文档 + 类型只允许一条关联
        List<PartDocumentLink> existing = mapper.selectByPartOid(link.getPartOid(), link.getLinkType());
        boolean duplicated = existing.stream().anyMatch(l -> link.getDocumentOid().equals(l.getDocumentOid()));
        if (duplicated) {
            throw new IllegalArgumentException("该文档已关联（类型重复）");
        }
        mapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public PartDocumentLink update(PartDocumentLink link) {
        PartDocumentLink existing = mapper.selectByOid(link.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("关联关系不存在: " + link.getOid());
        }
        existing.setCode(link.getCode());
        existing.setName(link.getName());
        existing.setDescription(link.getDescription());
        existing.setPartOid(link.getPartOid());
        existing.setDocumentOid(link.getDocumentOid());
        existing.setLinkType(link.getLinkType());
        existing.setEnabled(link.getEnabled());
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
    public PartDocumentLink findByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    @Override
    public List<PartDocumentLink> findByPart(String partOid, String linkType) {
        return mapper.selectByPartOid(partOid, linkType);
    }

    @Override
    public List<PartDocumentLink> listAll() {
        return mapper.selectAll();
    }
}
