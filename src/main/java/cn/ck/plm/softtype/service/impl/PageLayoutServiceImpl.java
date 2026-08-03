/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.mapper.PageLayoutMapper;
import cn.ck.plm.softtype.service.api.PageLayoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * {@link PageLayoutService} 实现。
 */
@Service
public class PageLayoutServiceImpl implements PageLayoutService {

    /** 系统预置操作：code → 名称 */
    private static final Map<String, String> SYSTEM_OPERATIONS = new LinkedHashMap<>();
    static {
        SYSTEM_OPERATIONS.put("list",   "列表页");
        SYSTEM_OPERATIONS.put("create", "新建页");
        SYSTEM_OPERATIONS.put("update", "编辑页");
        SYSTEM_OPERATIONS.put("detail", "详情页");
    }

    private final PageLayoutMapper mapper;

    public PageLayoutServiceImpl(PageLayoutMapper mapper) {
        this.mapper = mapper;
    }

    private String currentTenantOid() {
        return TenantContext.get();
    }

    private String platformOid() {
        return TenantContext.PLATFORM_TENANT_OID;
    }

    @Override
    @Transactional
    public PageLayout saveOrUpdate(PageLayout layout) {
        if (layout.getTenantOid() == null) {
            layout.setTenantOid(currentTenantOid());
        }
        PageLayout existing = mapper.selectByEntityAndOperation(
                layout.getEntityOid(), layout.getOperationCode(),
                currentTenantOid(), platformOid());
        if (existing != null) {
            TenantContext.requireEditPermission(existing.getTenantOid(), "页面布局");
            existing.setEntityCode(layout.getEntityCode());
            existing.setOperationName(layout.getOperationName());
            existing.setLayoutJson(layout.getLayoutJson());
            existing.setTenantOid(layout.getTenantOid());
            mapper.update(existing);
            return existing;
        } else {
            mapper.insert(layout);
            return layout;
        }
    }

    @Override
    public PageLayout findByEntityAndOperation(String entityOid, String operationCode) {
        return mapper.selectByEntityAndOperation(entityOid, operationCode,
                currentTenantOid(), platformOid());
    }

    @Override
    public List<PageLayout> findAllByEntity(String entityOid, String entityCode) {
        return mapper.selectAllByEntity(entityOid, entityCode,
                currentTenantOid(), platformOid());
    }

    @Override
    @Transactional
    public void deleteByEntityAndOperation(String entityOid, String operationCode) {
        if (SYSTEM_OPERATIONS.containsKey(operationCode)) {
            throw new IllegalArgumentException("系统预置操作不可删除: " + operationCode);
        }
        PageLayout existing = mapper.selectByEntityAndOperation(entityOid, operationCode,
                currentTenantOid(), platformOid());
        if (existing != null) {
            TenantContext.requireEditPermission(existing.getTenantOid(), "页面布局");
        }
        mapper.deleteByEntityAndOperation(entityOid, operationCode, currentTenantOid());
    }

    @Override
    public PageLayout findByEntityCodeAndOperation(String entityCode, String operationCode) {
        return mapper.selectByEntityCodeAndOperation(entityCode, operationCode,
                currentTenantOid(), platformOid());
    }

    @Override
    @Transactional
    public PageLayout cloneFromPlatform(String entityOid, String entityCode, String operationCode) {
        String tenantOid = currentTenantOid();
        if (platformOid().equals(tenantOid)) {
            throw new IllegalArgumentException("平台租户无需克隆");
        }
        PageLayout existing = mapper.selectByTenant(entityOid, entityCode, operationCode, tenantOid);
        if (existing != null) {
            throw new IllegalStateException("本租户已有该操作的页面布局，克隆功能不可用。如需重新克隆，请先删除本租户的布局");
        }
        PageLayout platformLayout = mapper.selectByTenant(entityOid, entityCode, operationCode, platformOid());
        if (platformLayout == null) {
            throw new IllegalArgumentException("平台级页面布局不存在，无法克隆");
        }
        PageLayout clone = new PageLayout();
        clone.setOid(UUID.randomUUID().toString());
        clone.setEntityOid(platformLayout.getEntityOid());
        clone.setEntityCode(platformLayout.getEntityCode());
        clone.setOperationCode(platformLayout.getOperationCode());
        clone.setOperationName(platformLayout.getOperationName());
        clone.setLayoutJson(platformLayout.getLayoutJson());
        clone.setTenantOid(tenantOid);
        mapper.insert(clone);
        return clone;
    }

    @Override
    public List<Map<String, String>> getOperationSummary(String entityOid, String entityCode) {
        String tenantOid = currentTenantOid();
        boolean isPlatformAdmin = platformOid().equals(tenantOid);
        List<PageLayout> saved = mapper.selectAllByEntity(entityOid, entityCode, tenantOid, platformOid());

        Map<String, PageLayout> layoutMap = new LinkedHashMap<>();
        for (PageLayout pl : saved) {
            String code = pl.getOperationCode();
            PageLayout existing = layoutMap.get(code);
            if (existing == null || tenantOid.equals(pl.getTenantOid())) {
                layoutMap.put(code, pl);
            }
        }

        List<Map<String, String>> result = new ArrayList<>();

        for (Map.Entry<String, String> sysOp : SYSTEM_OPERATIONS.entrySet()) {
            String code = sysOp.getKey();
            PageLayout pl = layoutMap.get(code);
            boolean hasSaved = pl != null;
            boolean isCurrentTenant = hasSaved && tenantOid.equals(pl.getTenantOid());

            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", sysOp.getValue());
            item.put("builtin", "true");
            item.put("saved", String.valueOf(hasSaved));
            // 平台管理员登录时，平台级布局显示为"平台"而非"本租户"
            if (isPlatformAdmin) {
                item.put("owner", hasSaved ? "platform" : "none");
            } else {
                item.put("owner", hasSaved ? (isCurrentTenant ? "tenant" : "platform") : "none");
            }
            result.add(item);
        }

        for (PageLayout pl : layoutMap.values()) {
            String code = pl.getOperationCode();
            if (SYSTEM_OPERATIONS.containsKey(code)) {
                continue;
            }
            boolean isCurrentTenant = tenantOid.equals(pl.getTenantOid());

            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", pl.getOperationName() != null ? pl.getOperationName() : code);
            item.put("builtin", "false");
            item.put("saved", "true");
            if (isPlatformAdmin) {
                item.put("owner", "platform");
            } else {
                item.put("owner", isCurrentTenant ? "tenant" : "platform");
            }
            result.add(item);
        }

        return result;
    }
}
