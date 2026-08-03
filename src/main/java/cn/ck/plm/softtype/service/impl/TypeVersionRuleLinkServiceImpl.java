/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeVersionRuleLink;
import cn.ck.plm.softtype.mapper.TypeVersionRuleLinkMapper;
import cn.ck.plm.base.mapper.VersionRuleMapper;
import cn.ck.plm.softtype.service.api.TypeVersionRuleLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 类型-版本规则关联服务实现。
 * <p>不同租户可为同一平台级 Type 绑定不同版本规则。
 */
@Service
public class TypeVersionRuleLinkServiceImpl implements TypeVersionRuleLinkService {

    @Autowired
    private TypeVersionRuleLinkMapper mapper;

    @Autowired
    private VersionRuleMapper versionRuleMapper;

    @Override
    @Transactional
    public TypeVersionRuleLink bindRule(String typeOid, String versionRuleCode) {
        if (versionRuleMapper.selectByCode(versionRuleCode) == null) {
            throw new IllegalArgumentException("版本规则不存在: " + versionRuleCode);
        }
        String tenantOid = TenantContext.get();

        // 检查本租户是否已有绑定，有则更新
        TypeVersionRuleLink existing = mapper.selectByTypeOid(typeOid);
        if (existing != null) {
            existing.setVersionRuleCode(versionRuleCode);
            mapper.update(existing);
            return existing;
        }

        TypeVersionRuleLink link = new TypeVersionRuleLink(typeOid, versionRuleCode);
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenantOid);
        mapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public void unbindRule(String typeOid) {
        mapper.deleteByTypeOid(typeOid);
    }

    @Override
    public TypeVersionRuleLink getByTypeOid(String typeOid) {
        return mapper.selectByTypeOid(typeOid);
    }

    @Override
    public List<TypeVersionRuleLink> listByVersionRuleCode(String versionRuleCode) {
        return mapper.selectByVersionRuleCode(versionRuleCode);
    }

    @Override
    public List<TypeVersionRuleLink> listAll() {
        return mapper.selectAll();
    }
}
