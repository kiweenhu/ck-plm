/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeNumberRuleLink;
import cn.ck.plm.softtype.mapper.TypeNumberRuleLinkMapper;
import cn.ck.plm.base.mapper.NumberMapper;
import cn.ck.plm.softtype.service.api.TypeNumberRuleLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 类型-编码规则关联服务实现。
 * <p>不同租户可为同一平台级 Type 绑定不同编码规则。
 */
@Service
public class TypeNumberRuleLinkServiceImpl implements TypeNumberRuleLinkService {

    @Autowired
    private TypeNumberRuleLinkMapper mapper;

    @Autowired
    private NumberMapper numberMapper;

    @Override
    @Transactional
    public TypeNumberRuleLink bindRule(String typeOid, String numberRuleCode) {
        if (numberMapper.selectByCode(numberRuleCode) == null) {
            throw new IllegalArgumentException("编码规则不存在: " + numberRuleCode);
        }
        String tenantOid = TenantContext.get();

        TypeNumberRuleLink existing = mapper.selectByTypeOid(typeOid);
        if (existing != null) {
            existing.setNumberRuleCode(numberRuleCode);
            mapper.update(existing);
            return existing;
        }

        TypeNumberRuleLink link = new TypeNumberRuleLink(typeOid, numberRuleCode);
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
    public TypeNumberRuleLink getByTypeOid(String typeOid) {
        return mapper.selectByTypeOid(typeOid);
    }

    @Override
    public List<TypeNumberRuleLink> listByNumberRuleCode(String numberRuleCode) {
        return mapper.selectByNumberRuleCode(numberRuleCode);
    }

    @Override
    public List<TypeNumberRuleLink> listAll() {
        return mapper.selectAll();
    }
}
