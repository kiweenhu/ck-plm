/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.mapper.AttributeDefinitionMapper;
import cn.ck.plm.softtype.service.api.AttributeDefinitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
        return mapper.selectByEntityName(entityName);
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
        if (mapper.existsByEntityAndField(def.getEntityName(), def.getFieldName()) > 0) {
            return null; // 已存在，幂等跳过
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
            if (register(def) != null) count++;
        }
        return count;
    }

    @Override
    @Transactional
    public int reinitSystemAttributes(String entityName, List<AttributeDefinition> defs) {
        // 先清空该实体所有 SYSTEM 属性
        mapper.deleteByEntityName(entityName);
        // 再重新注册
        int count = 0;
        for (AttributeDefinition def : defs) {
            def.setEntityName(entityName);
            def.setSource("SYSTEM");
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
        if (mapper.existsByEntityAndField(entityName, fieldName) > 0) {
            return null;
        }
        AttributeDefinition def = new AttributeDefinition(entityName, fieldName, iba.getDisplayName(), iba.getDataType());
        if (def.getDisplayName() == null || def.getDisplayName().isEmpty()) {
            def.setDisplayName(iba.getName());
        }
        def.setSource("IBA");
        def.setIbaOid(iba.getOid());
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
