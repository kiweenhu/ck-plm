/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.mapper.CKFileMapper;
import cn.ck.plm.base.service.api.CKFileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CKFile 服务实现。
 */
@Service
public class CKFileServiceImpl implements CKFileService {

    private final CKFileMapper ckFileMapper;

    public CKFileServiceImpl(CKFileMapper ckFileMapper) {
        this.ckFileMapper = ckFileMapper;
    }

    @Override
    @Transactional
    public CKFile create(CKFile file) {
        ckFileMapper.insert(file);
        return file;
    }

    @Override
    public CKFile findByOid(String oid) {
        if (oid == null) return null;
        return ckFileMapper.selectByOid(oid);
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null) return false;
        CKFile existing = ckFileMapper.selectByOid(oid);
        if (existing == null) return false;
        ckFileMapper.deleteByOid(oid);
        return true;
    }
}
