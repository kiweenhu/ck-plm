/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.IBAExtensible;
import cn.ck.plm.softtype.mapper.IBADataMapper;
import cn.ck.plm.softtype.service.api.IBADataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link IBADataService} 的数据库实现。
 *
 * <p>采用 delete-all + insert-all 策略保证 IBA 属性集合与
 * 前端提交完全一致（自动处理移除的属性）。
 */
@Service
public class IBADataServiceImpl implements IBADataService {

    private static final Logger log = LoggerFactory.getLogger(IBADataServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IBADataMapper ibaDataMapper;

    public IBADataServiceImpl(IBADataMapper ibaDataMapper) {
        this.ibaDataMapper = ibaDataMapper;
    }

    @Override
    @Transactional
    public void save(IBAExtensible entity) {
        if (entity == null) return;
        String entityType = entity.getEntityType();
        String entityOid = entity.getEntityOid();
        if (entityType == null || entityOid == null) {
            log.warn("IBADataService.save 跳过: entityType={}, entityOid={}", entityType, entityOid);
            return;
        }

        Map<String, Object> extAttrs = entity.getExtAttrs();
        if (extAttrs == null || extAttrs.isEmpty()) {
            // 无 IBA 数据时清空旧记录
            ibaDataMapper.deleteByEntity(entityType, entityOid);
            return;
        }

        // 先删后插，确保数据完全一致
        ibaDataMapper.deleteByEntity(entityType, entityOid);

        String creator = null;
        String updater = null;
        // 尝试从 extAttrs 元数据中获取审计信息（由 Controller 层注入）
        // creator/updater 由调用方 Service 保证一致性

        String tenantOid = TenantContext.get();

        for (Map.Entry<String, Object> entry : extAttrs.entrySet()) {
            String code = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            String jsonValue;
            try {
                // 标量值直接转字符串，复杂对象序列化为 JSON
                if (value instanceof String) {
                    jsonValue = escapeJsonString((String) value);
                } else if (value instanceof Number || value instanceof Boolean) {
                    jsonValue = String.valueOf(value);
                } else {
                    jsonValue = MAPPER.writeValueAsString(value);
                }
            } catch (Exception e) {
                log.warn("IBA 值序列化失败 attrCode={} entityOid={}: {}", code, entityOid, e.getMessage());
                continue;
            }

            try {
                ibaDataMapper.insert(entityType, entityOid, code, jsonValue, tenantOid, creator, updater);
            } catch (Exception e) {
                log.error("IBA 数据插入失败 attrCode={} entityOid={}: {}", code, entityOid, e.getMessage());
                throw e;
            }
        }

        log.debug("IBADataService.save 完成: entityType={} entityOid={} 属性数={}",
                entityType, entityOid, extAttrs.size());
    }

    @Override
    @Transactional
    public void saveValues(String entityType, String entityOid, Map<String, Object> values) {
        if (entityType == null || entityOid == null) return;
        if (values == null || values.isEmpty()) {
            ibaDataMapper.deleteByEntity(entityType, entityOid);
            return;
        }

        // 先删后插（适用于新建场景，更新场景请使用 mergeValues）
        ibaDataMapper.deleteByEntity(entityType, entityOid);
        doInsertAll(entityType, entityOid, values);

        log.debug("IBADataService.saveValues 完成: entityType={} entityOid={} 属性数={}", entityType, entityOid, values.size());
    }

    @Override
    @Transactional
    public void mergeValues(String entityType, String entityOid, Map<String, Object> values) {
        if (entityType == null || entityOid == null) return;
        if (values == null || values.isEmpty()) return; // 无变更则跳过

        // 1. 加载已持久化的 IBA 数据
        Map<String, Object> existing = getValues(entityType, entityOid);

        // 2. 用本次提交的值覆盖/新增
        existing.putAll(values);

        // 3. 全量保存（existing 包含 old + new，避免误删未提交的字段）
        ibaDataMapper.deleteByEntity(entityType, entityOid);
        doInsertAll(entityType, entityOid, existing);

        log.debug("IBADataService.mergeValues 完成: entityType={} entityOid={} 合并后属性数={}", entityType, entityOid, existing.size());
    }

    /** 逐条插入 IBA 属性值（内部公用方法） */
    private void doInsertAll(String entityType, String entityOid, Map<String, Object> values) {
        String tenantOid = TenantContext.get();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String code = entry.getKey();
            Object val = entry.getValue();
            if (val == null) continue;

            String jsonValue;
            try {
                if (val instanceof String) jsonValue = escapeJsonString((String) val);
                else if (val instanceof Number || val instanceof Boolean) jsonValue = String.valueOf(val);
                else jsonValue = MAPPER.writeValueAsString(val);
            } catch (Exception e) {
                log.warn("IBA 值序列化失败 attrCode={} entityOid={}: {}", code, entityOid, e.getMessage());
                continue;
            }

            try {
                ibaDataMapper.insert(entityType, entityOid, code, jsonValue, tenantOid, null, null);
            } catch (Exception e) {
                log.error("IBA 数据插入失败 attrCode={} entityOid={}: {}", code, entityOid, e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public void restore(IBAExtensible entity) {
        if (entity == null) return;
        Map<String, Object> values = getValues(entity.getEntityType(), entity.getEntityOid());
        entity.injectExtAttrs(values);
    }

    @Override
    public Map<String, Object> getValues(String entityType, String entityOid) {
        if (entityType == null || entityOid == null) return new LinkedHashMap<>();
        List<Map<String, Object>> rows = ibaDataMapper.selectByEntity(entityType, entityOid);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("attr_code");
            String rawValue = (String) row.get("attr_value");
            if (code == null || rawValue == null) continue;
            Object parsed = parseJsonValue(rawValue);
            result.put(code, parsed);
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteByEntity(String entityType, String entityOid) {
        if (entityType == null || entityOid == null) return;
        ibaDataMapper.deleteByEntity(entityType, entityOid);
        log.debug("IBADataService.deleteByEntity: entityType={} entityOid={}", entityType, entityOid);
    }

    // ==================== 内部工具 ====================

    /**
     * 解析 JSONB 值，尝试还原为原始类型。
     * <p>JSONB::text 返回的是 JSON 字符串表示，需要反解析。
     * 例如: "hello" → hello, 42 → 42, "42" → "42", {"a":1} → Map
     */
    private Object parseJsonValue(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return MAPPER.readValue(raw, Object.class);
        } catch (Exception e) {
            // 非 JSON 格式，当作普通字符串
            return raw;
        }
    }

    /**
     * 将字符串值包装为合法的 JSONB 字符串字面量。
     */
    private String escapeJsonString(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
