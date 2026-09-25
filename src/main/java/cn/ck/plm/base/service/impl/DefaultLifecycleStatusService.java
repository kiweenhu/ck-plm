/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.mapper.LifecycleStatusMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.util.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link LifecycleStatusService} 的 PostgreSQL 数据库实现。
 *
 * <p>基于 MyBatis 持久化，核心状态仍受保护不允许删除。
 */
@Service
public class DefaultLifecycleStatusService implements LifecycleStatusService {

    private final LifecycleStatusMapper mapper;
    /** 模板状态表：code → 显示名的权威来源（见 {@link #displayName}） */
    private final LifecycleTemplateMapper templateMapper;

    public DefaultLifecycleStatusService(LifecycleStatusMapper mapper, LifecycleTemplateMapper templateMapper) {
        this.mapper = mapper;
        this.templateMapper = templateMapper;
    }

    @Override
    @Transactional
    public LifecycleStatus create(LifecycleStatus status) {
        if (status == null || status.getCode() == null || status.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("状态编码不能为空");
        }
        String code = status.getCode().trim();
        if (mapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("状态编码 '" + code + "' 已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        status.setCreatedAt(now);
        status.setUpdatedAt(now);
        mapper.insert(status);
        return status;
    }

    @Override
    @Transactional
    public LifecycleStatus update(LifecycleStatus status) {
        if (status == null || status.getCode() == null || status.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("状态编码不能为空");
        }
        String code = status.getCode().trim();
        LifecycleStatus existing = mapper.selectByCode(code);
        if (existing == null) {
            throw new IllegalArgumentException("状态编码 '" + code + "' 不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "生命周期状态");
        status.setUpdatedAt(LocalDateTime.now());
        mapper.update(status);
        existing.setName(status.getName());
        existing.setDisplayName(status.getDisplayName());
        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String normalized = code.trim();
        LifecycleStatus existing = mapper.selectByCode(normalized);
        if (existing == null) {
            return false;
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "生命周期状态");
        if (isCoreStatus(normalized)) {
            throw new IllegalStateException("核心状态 '" + normalized + "' 不允许删除");
        }
        mapper.deleteByCode(normalized);
        return true;
    }

    @Override
    public LifecycleStatus findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return mapper.selectByCode(code.trim());
    }

    @Override
    public List<LifecycleStatus> findAll() {
        return mapper.selectAll();
    }

    @Override
    public List<LifecycleStatus> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return mapper.search(keyword.trim());
    }

    @Override
    public boolean exists(String code) {
        return code != null && mapper.existsByCode(code.trim()) > 0;
    }

    // ==================== code → 显示名 ====================

    @Override
    public Map<String, String> displayNames(String templateIterationOid) {
        Map<String, String> names = new LinkedHashMap<>();
        if (templateIterationOid == null || templateIterationOid.trim().isEmpty()) {
            return names;
        }
        List<LifecycleTemplateStatusRef> refs =
                templateMapper.selectStateRefsByIterationOid(templateIterationOid.trim());
        for (LifecycleTemplateStatusRef ref : refs != null ? refs : List.<LifecycleTemplateStatusRef>of()) {
            String code = ref.getStatusCode();
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            String display = ref.getStatusDisplayName();
            names.put(code.trim(), display != null && !display.trim().isEmpty() ? display.trim() : code.trim());
        }
        return names;
    }

    @Override
    public String displayName(String templateIterationOid, String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String normalized = code.trim();
        String fromTemplate = displayNames(templateIterationOid).get(normalized);
        if (fromTemplate != null) {
            return fromTemplate;
        }
        // 模板里没有（对象未绑模板 / 模板里删过该状态）→ 退回全局状态字典
        LifecycleStatus status = findByCode(normalized);
        if (status != null) {
            String name = status.getDisplayName();
            if (name == null || name.trim().isEmpty()) {
                name = status.getName();
            }
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return normalized;
    }

    private boolean isCoreStatus(String code) {
        return "WORKING".equals(code)
                || "APPROVING".equals(code)
                || "PUBLISHED".equals(code)
                || "OFFLINE".equals(code)
                || "ARCHIVED".equals(code);
    }
}
