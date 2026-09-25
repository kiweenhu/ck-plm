/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.mapper.TypeLifecycleTemplateLinkMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateProcessService;
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

    /** 状态 → 流程模板 关联（换模板 / 解绑时要清掉该类型的旧绑定） */
    @Autowired
    private TypeLifecycleStateProcessService typeLifecycleStateProcessService;

    @Override
    @Transactional
    public TypeLifecycleTemplateLink bindTemplate(String typeOid, String lifecycleTemplateCode) {
        if (lifecycleTemplateMapper.selectByCode(lifecycleTemplateCode) == null) {
            throw new IllegalArgumentException("生命周期模板不存在: " + lifecycleTemplateCode);
        }
        String tenantOid = TenantContext.get();

        TypeLifecycleTemplateLink existing = mapper.selectByTypeOid(typeOid);
        if (existing != null) {
            boolean templateChanged = !lifecycleTemplateCode.equals(existing.getLifecycleTemplateCode());
            existing.setLifecycleTemplateCode(lifecycleTemplateCode);
            mapper.update(existing);
            if (templateChanged) {
                // 换模板了：旧模板下的「状态 → 流程」对该类型已无意义，留着会变成
                // "界面上看不到、却仍挡着流程模板删除"的幽灵引用
                typeLifecycleStateProcessService.clearByType(typeOid);
            }
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
        // 模板都不绑了，状态 → 流程 的绑定一并清掉（同上：不留幽灵引用）
        typeLifecycleStateProcessService.clearByType(typeOid);
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
