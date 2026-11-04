/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.PageLayoutMapper;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
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
    /** 克隆「根类型布局」时解析 root_type_code 用（本租户内查，2026-10-11 起不再读平台那份） */
    private final TypeDefinitionMapper typeDefinitionMapper;

    public PageLayoutServiceImpl(PageLayoutMapper mapper, TypeDefinitionMapper typeDefinitionMapper) {
        this.mapper = mapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
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
        String tenantOid = layout.getTenantOid();
        // ★ 2026-10-12：定位既有行改为「entity_code + operation_code + 本租户」——
        //   与设计器的读取口径一致（按 entityCode 取），并能**自愈存量脏行**：
        //   若某行的 entity_oid 曾指错地方（初始化器跨租户自愈留下的伤痕），按 oid 查会查不到，
        //   于是误插第二份（同租户同操作两行，读取 LIMIT 1 命中哪行看运气）。按 code 定位后，
        //   找到即更新、顺手把 entity_oid 对齐到本租户类型 oid。
        PageLayout existing = layout.getEntityCode() != null
                ? mapper.selectByEntityCodeAndOperation(layout.getEntityCode(), layout.getOperationCode(),
                        tenantOid, platformOid())
                : mapper.selectByEntityAndOperation(layout.getEntityOid(), layout.getOperationCode(),
                        tenantOid, platformOid());
        if (existing != null) {
            TenantContext.requireEditPermission(existing.getTenantOid(), "页面布局");
            // 顺手对齐 entity_oid（存量脏行的自愈点；update SQL 会写回该字段）
            if (layout.getEntityOid() != null) {
                existing.setEntityOid(layout.getEntityOid());
            }
            existing.setEntityCode(layout.getEntityCode() != null
                    ? layout.getEntityCode() : existing.getEntityCode());
            existing.setOperationName(layout.getOperationName());
            existing.setLayoutJson(layout.getLayoutJson());
            existing.setTenantOid(tenantOid);
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
    public PageLayout cloneFromRootType(String entityOid, String entityCode, String operationCode) {
        String tenantOid = currentTenantOid();
        if (platformOid().equals(tenantOid)) {
            throw new IllegalArgumentException("平台租户无需克隆");
        }
        PageLayout existing = mapper.selectByTenant(entityOid, entityCode, operationCode, tenantOid);
        if (existing != null) {
            throw new IllegalStateException("本租户已有该操作的页面布局，克隆功能不可用。如需重新克隆，请先删除本租户的布局");
        }
        // 1) 当前类型 → 根类型（能力宿主 root_type_code，如 电子元器件 → PART）—— 全在本租户内解析
        TypeDefinition self = typeDefinitionMapper.selectByCode(entityCode, tenantOid, tenantOid);
        if (self == null) {
            throw new IllegalArgumentException("类型不存在：" + entityCode);
        }
        String rootCode = self.getRootTypeCode();
        if (rootCode == null || rootCode.equals(entityCode)) {
            throw new IllegalArgumentException("当前类型已是根类型（" + entityCode + "），没有更上层的布局可克隆");
        }
        TypeDefinition root = typeDefinitionMapper.selectByCode(rootCode, tenantOid, tenantOid);
        if (root == null) {
            throw new IllegalArgumentException("根类型不存在：" + rootCode + "（请先创建 / 安装该根类型）");
        }
        // 2) 取根类型在**本租户**的同名操作布局（★ 不再读平台那份 —— 与"按租户隔离、不拿平台兜底"的口径一致）
        PageLayout rootLayout = mapper.selectByTenant(root.getOid(), root.getCode(), operationCode, tenantOid);
        if (rootLayout == null) {
            throw new IllegalArgumentException("根类型「" + root.getName() + "」还没有该操作的页面布局 —— 请先在它的设计器里设计并保存");
        }
        // 3) 复制给当前类型：entity_oid / entity_code 必须用【当前类型自己】的
        //    （旧实现沿用平台布局的 oid —— 那是"同一实体"才成立；根类型 oid 与当前类型不同）
        PageLayout clone = new PageLayout();
        clone.setOid(UUID.randomUUID().toString());
        clone.setEntityOid(entityOid);
        clone.setEntityCode(entityCode);
        clone.setOperationCode(rootLayout.getOperationCode());
        clone.setOperationName(rootLayout.getOperationName());
        clone.setLayoutJson(rootLayout.getLayoutJson());
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
