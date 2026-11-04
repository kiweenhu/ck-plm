/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.mapper.AttributeDefinitionMapper;
import cn.ck.plm.softtype.service.api.AttributeDefinitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AttributeDefinitionService} 的实现。
 */
@Service
public class AttributeDefinitionServiceImpl implements AttributeDefinitionService {

    private final AttributeDefinitionMapper mapper;

    public AttributeDefinitionServiceImpl(AttributeDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AttributeDefinition> findByEntityName(String entityName) {
        // 拦截器给本表注入的是「平台租户 or 当前租户」→ 同一字段可能返回两行，租户覆写优先
        return dedupePreferTenant(mapper.selectByEntityName(entityName));
    }

    /**
     * 同名字段去重：<b>租户覆写优先</b>。
     *
     * <p>行业包按租户装了某个字段的行业定义后，平台上可能还有一份同名字段
     * （例如"单位"这种平台字段被行业包重新定义了显示名/组件）。查询侧按
     * "平台 or 当前租户"取回两行，这里保留租户那一行 —— 否则界面上会出现两个同名字段。
     */
    private List<AttributeDefinition> dedupePreferTenant(List<AttributeDefinition> rows) {
        Map<String, AttributeDefinition> byField = new LinkedHashMap<>();
        for (AttributeDefinition row : rows) {
            AttributeDefinition existing = byField.get(row.getFieldName());
            if (existing == null || isTenantScoped(row)) {
                byField.put(row.getFieldName(), row);
            }
        }
        return new ArrayList<>(byField.values());
    }

    /** 该行是否属于具体租户（非平台元数据） */
    private boolean isTenantScoped(AttributeDefinition def) {
        return def.getTenantOid() != null
                && !TenantContext.PLATFORM_TENANT_OID.equals(def.getTenantOid());
    }

    /** 归属租户：显式指定优先，其次当前上下文，最后落平台租户（启动期无上下文） */
    private String resolveTenant(String explicit) {
        if (explicit != null && !explicit.trim().isEmpty()) {
            return explicit;
        }
        String current = TenantContext.getOrNull();
        return (current == null || current.trim().isEmpty())
                ? TenantContext.PLATFORM_TENANT_OID : current;
    }

    @Override
    public List<AttributeDefinition> findByEntity(String entityCode, String entityOid, String entityType) {
        // 只从 ck_attribute_definition 获取已注册属性，不包含 IBA
        // IBA 属性由 ck_type_iba 独立管理，通过 /api/ibas/mappings 接口单独查询
        return new ArrayList<>(findByEntityName(entityCode));
    }

    @Override
    @Transactional
    public AttributeDefinition register(AttributeDefinition def) {
        String tenant = resolveTenant(def.getTenantOid());
        def.setTenantOid(tenant);
        if (mapper.existsByEntityAndField(def.getEntityName(), def.getFieldName(), tenant) > 0) {
            return null; // 同一租户下已存在，幂等跳过
        }
        mapper.insert(def);
        return def;
    }

    @Override
    @Transactional
    public int registerSystemAttributes(String entityName, List<AttributeDefinition> defs) {
        int count = 0;
        for (AttributeDefinition def : defs) {
            def.setEntityName(entityName);
            def.setSource("SYSTEM");
            // SYSTEM 属性＝平台元数据（实体类字段的映射），显式落平台租户：
            // 不写这一行会落到"当前上下文租户"，启动期是默认租户 —— 于是平台元数据与平台种子错位。
            def.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
            if (register(def) != null) count++;
        }
        return count;
    }

    @Override
    @Transactional
    public int reinitSystemAttributes(String entityName, List<AttributeDefinition> defs) {
        // 先清空该实体**平台那一份** SYSTEM 属性（不能连租户的行业属性一起清 ——
        // 包给租户装的行业定义不在本次重建范围内）
        String tenant = TenantContext.PLATFORM_TENANT_OID;
        mapper.deleteSystemByEntityAndTenant(entityName, tenant);
        // 再重新注册
        int count = 0;
        for (AttributeDefinition def : defs) {
            def.setEntityName(entityName);
            def.setSource("SYSTEM");
            def.setTenantOid(tenant);
            def.setOid(java.util.UUID.randomUUID().toString());
            mapper.insert(def);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public AttributeDefinition registerFromIba(IBA iba, String entityName) {
        String fieldName = iba.getCode().toLowerCase();
        // 归属跟随 IBA 本身：行业包装给租户的 IBA，其属性定义也归该租户
        String tenant = resolveTenant(iba.getTenantOid());
        if (mapper.existsByEntityAndField(entityName, fieldName, tenant) > 0) {
            return null;
        }
        AttributeDefinition def = new AttributeDefinition(entityName, fieldName, iba.getDisplayName(), iba.getDataType());
        if (def.getDisplayName() == null || def.getDisplayName().isEmpty()) {
            def.setDisplayName(iba.getName());
        }
        def.setSource("IBA");
        def.setIbaOid(iba.getOid());
        def.setTenantOid(tenant);
        def.setRequired(iba.isRequired());
        def.setDefaultValue(iba.getDefaultValue());
        def.setConstraintsJson(iba.getConstraintsJson());
        def.setSortOrder(iba.getSortOrder());
        def.setEnabled(iba.isEnabled());
        // IBA 属性默认配置：可搜索、可列表、可编辑
        def.setSearchable(true);
        def.setListable(true);
        def.setEditable(true);
        def.setUiComponent(resolveUiComponent(iba.getDataType()));
        mapper.insert(def);
        return def;
    }

    @Override
    @Transactional
    public void removeByIbaOid(String ibaOid) {
        mapper.deleteByIbaOid(ibaOid);
    }

    @Override
    @Transactional
    public AttributeDefinition update(AttributeDefinition def) {
        // 仅允许更新 UI 布局相关字段
        AttributeDefinition existing = mapper.selectByOid(def.getOid());
        if (existing == null) return null;
        existing.setDisplayName(def.getDisplayName() != null ? def.getDisplayName() : existing.getDisplayName());
        existing.setUiComponent(def.getUiComponent() != null ? def.getUiComponent() : existing.getUiComponent());
        existing.setSearchable(def.isSearchable());
        existing.setListable(def.isListable());
        existing.setEditable(def.isEditable());
        existing.setSortOrder(def.getSortOrder());
        existing.setEnabled(def.isEnabled());
        existing.setRequired(def.isRequired());
        existing.setDefaultValue(def.getDefaultValue());
        mapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public int batchUpdateLayout(List<AttributeDefinition> defs) {
        int count = 0;
        for (AttributeDefinition def : defs) {
            if (update(def) != null) count++;
        }
        return count;
    }

    /** 根据数据类型推荐 UI 组件 */
    private String resolveUiComponent(String dataType) {
        if (dataType == null) return "input";
        switch (dataType.toUpperCase()) {
            case "TEXT":
            case "STRING":   return "input";
            case "BOOLEAN":  return "switch";
            case "INTEGER":
            case "FLOAT":    return "input-number";
            case "DATE":     return "datepicker";
            case "DATETIME": return "datepicker";
            case "ENUM":     return "select";
            case "URL":      return "input";
            default:         return "input";
        }
    }
}
