/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cls.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.cls.entity.ClsPageLayout;
import cn.ck.plm.cls.mapper.ClsPageLayoutMapper;
import cn.ck.plm.cls.service.api.ClsPageLayoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * {@link ClsPageLayoutService} 实现。
 */
@Service
public class ClsPageLayoutServiceImpl implements ClsPageLayoutService {

    /** 系统预置操作：code → 名称 */
    private static final Map<String, String> SYSTEM_OPERATIONS = new LinkedHashMap<>();
    static {
        SYSTEM_OPERATIONS.put("create", "新建页");
        SYSTEM_OPERATIONS.put("update", "编辑页");
        SYSTEM_OPERATIONS.put("detail", "详情页");
    }

    private final ClsPageLayoutMapper mapper;

    public ClsPageLayoutServiceImpl(ClsPageLayoutMapper mapper) {
        this.mapper = mapper;
    }

    private String currentTenantOid() {
        return TenantContext.get();
    }

    @Override
    @Transactional
    public ClsPageLayout saveOrUpdate(ClsPageLayout layout) {
        if (layout.getTenantOid() == null) {
            layout.setTenantOid(currentTenantOid());
        }
        ClsPageLayout existing = mapper.selectByClsAndOperation(
                layout.getClsOid(), layout.getOperationCode(), currentTenantOid());
        if (existing != null) {
            TenantContext.requireEditPermission(existing.getTenantOid(), "分类页面布局");
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
    public ClsPageLayout findByClsAndOperation(String clsOid, String operationCode) {
        return mapper.selectByClsAndOperation(clsOid, operationCode, currentTenantOid());
    }

    @Override
    public List<ClsPageLayout> findAllByClsOid(String clsOid) {
        return mapper.selectAllByClsOid(clsOid, currentTenantOid());
    }

    @Override
    @Transactional
    public void deleteByClsAndOperation(String clsOid, String operationCode) {
        if (SYSTEM_OPERATIONS.containsKey(operationCode)) {
            throw new IllegalArgumentException("系统预置操作不可删除: " + operationCode);
        }
        ClsPageLayout existing = mapper.selectByClsAndOperation(clsOid, operationCode, currentTenantOid());
        if (existing != null) {
            TenantContext.requireEditPermission(existing.getTenantOid(), "分类页面布局");
        }
        mapper.deleteByClsAndOperation(clsOid, operationCode, currentTenantOid());
    }

    @Override
    @Transactional
    public void deleteAllByClsOid(String clsOid) {
        mapper.deleteAllByClsOid(clsOid);
    }

    @Override
    public List<Map<String, String>> getOperationSummary(String clsOid) {
        String tenantOid = currentTenantOid();
        List<ClsPageLayout> saved = mapper.selectAllByClsOid(clsOid, tenantOid);

        Map<String, ClsPageLayout> layoutMap = new LinkedHashMap<>();
        for (ClsPageLayout pl : saved) {
            layoutMap.put(pl.getOperationCode(), pl);
        }

        List<Map<String, String>> result = new ArrayList<>();

        for (Map.Entry<String, String> sysOp : SYSTEM_OPERATIONS.entrySet()) {
            String code = sysOp.getKey();
            ClsPageLayout pl = layoutMap.get(code);
            boolean hasSaved = pl != null;

            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", sysOp.getValue());
            item.put("builtin", "true");
            item.put("saved", String.valueOf(hasSaved));
            item.put("owner", hasSaved ? "tenant" : "none");
            result.add(item);
        }

        for (ClsPageLayout pl : layoutMap.values()) {
            String code = pl.getOperationCode();
            if (SYSTEM_OPERATIONS.containsKey(code)) {
                continue;
            }

            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", pl.getOperationName() != null ? pl.getOperationName() : code);
            item.put("builtin", "false");
            item.put("saved", "true");
            item.put("owner", "tenant");
            result.add(item);
        }

        return result;
    }
}
