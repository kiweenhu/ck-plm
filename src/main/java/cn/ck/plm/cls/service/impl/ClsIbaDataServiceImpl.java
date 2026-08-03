package cn.ck.plm.cls.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cls.mapper.ClsIbaDataMapper;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class ClsIbaDataServiceImpl implements ClsIbaDataService {

    private static final Logger log = LoggerFactory.getLogger(ClsIbaDataServiceImpl.class);
    private final ClsIbaDataMapper mapper;

    public ClsIbaDataServiceImpl(ClsIbaDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void saveValues(String classificationOid, Map<String, Object> values) {
        mapper.deleteByClassificationOid(classificationOid);
        if (values == null || values.isEmpty()) return;
        String tenantOid = TenantContext.get();
        String operator = UserContext.get();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String attrCode = entry.getKey();
            String attrValue = toJsonString(entry.getValue());
            mapper.insert(classificationOid, attrCode, attrValue, tenantOid, operator, operator);
        }
    }

    @Override
    @Transactional
    public void mergeValues(String classificationOid, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;
        Map<String, Object> existing = getValues(classificationOid);
        if (existing != null) {
            existing.putAll(values);
        } else {
            existing = values;
        }
        saveValues(classificationOid, existing);
    }

    @Override
    public Map<String, Object> getValues(String classificationOid) {
        Map<String, Object> raw = mapper.selectByClassificationOid(classificationOid);
        if (raw == null) return new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String attrCode = entry.getKey();
            Object attrValue = entry.getValue();
            if (attrValue instanceof String) {
                attrValue = parseJsonValue((String) attrValue);
            }
            result.put(attrCode, attrValue);
        }
        return result;
    }

    @Override
    public String getValue(String classificationOid, String attrCode) {
        String raw = mapper.selectAttrValue(classificationOid, attrCode);
        if (raw == null) return null;
        Object parsed = parseJsonValue(raw);
        return parsed != null ? parsed.toString() : null;
    }

    @Override
    @Transactional
    public void deleteByClassificationOid(String classificationOid) {
        mapper.deleteByClassificationOid(classificationOid);
    }

    // ==================== JSON 值处理 ====================

    private String toJsonString(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJsonString((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        try {
            return "\"" + escapeJsonString(value.toString()) + "\"";
        } catch (Exception e) {
            log.warn("IBA值序列化失败: {}", e.getMessage());
            return "null";
        }
    }

    private Object parseJsonValue(String jsonStr) {
        if (jsonStr == null || "null".equals(jsonStr)) return null;
        try {
            if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                return jsonStr.substring(1, jsonStr.length() - 1);
            }
            if ("true".equals(jsonStr)) return true;
            if ("false".equals(jsonStr)) return false;
            if (jsonStr.contains(".")) return Double.parseDouble(jsonStr);
            return Long.parseLong(jsonStr);
        } catch (Exception e) {
            return jsonStr;
        }
    }

    private String escapeJsonString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
