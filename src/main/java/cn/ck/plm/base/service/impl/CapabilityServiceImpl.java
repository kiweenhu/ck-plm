/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.TenantCapability;
import cn.ck.plm.base.mapper.TenantCapabilityMapper;
import cn.ck.plm.base.service.api.CapabilityService;
import cn.ck.plm.base.util.TenantContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link CapabilityService} 的实现。
 *
 * <p>租户显式传入（与 {@code TenantCapabilityMapper} 的口径一致）：账本的写入方就写明
 * "不必依赖自动注入"，读侧也照此办理，避免"当前上下文租户"与"目标租户"不一致时的意外。
 */
@Service
public class CapabilityServiceImpl implements CapabilityService {

    private final TenantCapabilityMapper capabilityMapper;

    public CapabilityServiceImpl(TenantCapabilityMapper capabilityMapper) {
        this.capabilityMapper = capabilityMapper;
    }

    @Override
    public List<TenantCapability> listEnabled() {
        return capabilityMapper.selectByTenant(TenantContext.get());
    }

    @Override
    public Set<String> enabledCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (TenantCapability c : listEnabled()) {
            // enabled 是原始 boolean（getter 为 isEnabled，落库默认 true）：
            // 管理员手动关闭的行不算启用 —— 行保留着，读侧要认这个字段
            if (c.getCapabilityCode() != null && c.isEnabled()) {
                codes.add(c.getCapabilityCode());
            }
        }
        return codes;
    }

    @Override
    public boolean has(String capabilityCode) {
        return capabilityCode != null && enabledCodes().contains(capabilityCode);
    }
}
