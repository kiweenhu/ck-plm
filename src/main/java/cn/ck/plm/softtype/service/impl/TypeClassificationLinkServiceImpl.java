/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import cn.ck.plm.softtype.entity.TypeClassificationLink;
import cn.ck.plm.softtype.mapper.TypeClassificationLinkMapper;
import cn.ck.plm.softtype.service.api.TypeClassificationLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 类型-分类关联服务实现。
 */
@Service
public class TypeClassificationLinkServiceImpl implements TypeClassificationLinkService {

    @Autowired
    private TypeClassificationLinkMapper mapper;

    @Autowired
    private ClassificationMapper classificationMapper;

    @Override
    @Transactional
    public TypeClassificationLink bindClassification(String typeOid, String classificationOid) {
        Classification cls = classificationMapper.selectByOid(classificationOid);
        if (cls == null) {
            throw new IllegalArgumentException("分类不存在: " + classificationOid);
        }
        String tenantOid = TenantContext.get();

        TypeClassificationLink existing = mapper.selectByTypeOid(typeOid);
        if (existing != null) {
            existing.setClassificationOid(classificationOid);
            mapper.update(existing);
            return existing;
        }

        TypeClassificationLink link = new TypeClassificationLink(typeOid, classificationOid);
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenantOid);
        mapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public void unbindClassification(String typeOid) {
        mapper.deleteByTypeOid(typeOid);
    }

    @Override
    public TypeClassificationLink getByTypeOid(String typeOid) {
        return mapper.selectByTypeOid(typeOid);
    }

    @Override
    public List<TypeClassificationLink> listByClassificationOid(String classificationOid) {
        return mapper.selectByClassificationOid(classificationOid);
    }

    @Override
    public List<TypeClassificationLink> listAll() {
        return mapper.selectAll();
    }
}
