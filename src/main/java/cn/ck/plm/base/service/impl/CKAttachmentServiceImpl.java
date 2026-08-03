/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.CKAttachment;
import cn.ck.plm.base.mapper.CKAttachmentMapper;
import cn.ck.plm.base.service.api.CKAttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CKAttachment 服务实现。
 */
@Service
public class CKAttachmentServiceImpl implements CKAttachmentService {

    private final CKAttachmentMapper attachmentMapper;

    public CKAttachmentServiceImpl(CKAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    @Transactional
    public CKAttachment create(CKAttachment attachment) {
        attachmentMapper.insert(attachment);
        return attachment;
    }

    @Override
    public CKAttachment findByOid(String oid) {
        if (oid == null) return null;
        return attachmentMapper.selectByOid(oid);
    }

    @Override
    public List<CKAttachment> findByOwner(String ownerOid) {
        if (ownerOid == null) return java.util.Collections.emptyList();
        return attachmentMapper.selectByOwnerOid(ownerOid);
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null) return false;
        CKAttachment existing = attachmentMapper.selectByOid(oid);
        if (existing == null) return false;
        attachmentMapper.deleteByOid(oid);
        return true;
    }

    @Override
    @Transactional
    public void bindToOwner(String attachmentOid, String ownerOid) {
        if (attachmentOid == null || ownerOid == null) return;
        CKAttachment att = attachmentMapper.selectByOid(attachmentOid);
        if (att != null) {
            att.setOwnerOid(ownerOid);
            attachmentMapper.update(att);
        }
    }
}
