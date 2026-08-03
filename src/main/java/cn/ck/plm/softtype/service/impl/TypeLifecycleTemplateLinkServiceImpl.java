/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.mapper.TypeLifecycleTemplateLinkMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.softtype.service.api.TypeLifecycleTemplateLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 类型-生命周期模板关联服务实现。
 * <p>不同租户可为同一平台级 Type 绑定不同生命周期模板。
 */
@Service
public class TypeLifecycleTemplateLinkServiceImpl implements TypeLifecycleTemplateLinkService {

    @Autowired
    private TypeLifecycleTemplateLinkMapper mapper;

    @Autowired
    private LifecycleTemplateMapper lifecycleTemplateMapper;

    @Override
    @Transactional
    public TypeLifecycleTemplateLink bindTemplate(String typeOid, String lifecycleTemplateCode) {
        if (lifecycleTemplateMapper.selectByCode(lifecycleTemplateCode) == null) {
            throw new IllegalArgumentException("生命周期模板不存在: " + lifecycleTemplateCode);
        }
        String tenantOid = TenantContext.get();

        TypeLifecycleTemplateLink existing = mapper.selectByTypeOid(typeOid);
        if (existing != null) {
            existing.setLifecycleTemplateCode(lifecycleTemplateCode);
            mapper.update(existing);
            return existing;
        }

        TypeLifecycleTemplateLink link = new TypeLifecycleTemplateLink(typeOid, lifecycleTemplateCode);
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenantOid);
        mapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public void unbindTemplate(String typeOid) {
        mapper.deleteByTypeOid(typeOid);
    }

    @Override
    public TypeLifecycleTemplateLink getByTypeOid(String typeOid) {
        return mapper.selectByTypeOid(typeOid);
    }

    @Override
    public List<TypeLifecycleTemplateLink> listByTemplateCode(String lifecycleTemplateCode) {
        return mapper.selectByTemplateCode(lifecycleTemplateCode);
    }

    @Override
    public List<TypeLifecycleTemplateLink> listAll() {
        return mapper.selectAll();
    }
}
