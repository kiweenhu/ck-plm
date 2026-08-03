/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IBA 可扩展实体接口 —— 标记实体支持 IBA 动态属性。
 *
 * <h3>设计意图</h3>
 * <p>任何需要承载 IBA 动态属性的实体类实现此接口，通过
 * {@link IBADataService} 统一进行 IBA 属性值的存取，无需
 * 各实体表单独添加 ext_attrs JSONB 列。
 *
 * <h3>实体实现指南</h3>
 * <ol>
 *   <li>实现 {@link #getEntityType()} — 返回实体类型字符串（如 "ck_product_line"）</li>
 *   <li>实现 {@link #getEntityOid()} — 返回实体 oid</li>
 *   <li>声明 {@code Map<String, Object> extAttrs} 字段</li>
 *   <li>实现 {@link #getExtAttrs()} / {@link #setExtAttrs(Map)}</li>
 *   <li>必须提供 {@code @JsonAnySetter} / {@code @JsonAnyGetter} 方法
 *       （见下方示例，Jackson 接口注解支持有限，需在实体类中显式声明）</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class ProductLine extends WithoutVersionEntity implements IBAExtensible {
 *
 *     // 实体固定字段...
 *
 *     &#64;JsonIgnore
 *     private Map<String, Object> extAttrs;
 *
 *     &#64;Override public String getEntityType() { return "ck_product_line"; }
 *     &#64;Override public String getEntityOid()  { return getOid(); }
 *     &#64;Override public Map<String, Object> getExtAttrs() { return extAttrs; }
 *     &#64;Override public void setExtAttrs(Map<String, Object> attrs) { this.extAttrs = attrs; }
 *
 *     &#64;JsonAnySetter
 *     public void setDynamicField(String key, Object value) {
 *         if (value == null) return;
 *         if (extAttrs == null) extAttrs = new LinkedHashMap<>();
 *         extAttrs.put(key, value);
 *     }
 *     &#64;JsonAnyGetter
 *     public Map<String, Object> getDynamicFields() { return extAttrs; }
 * }
 * }</pre>
 *
 * <h3>Service 层调用</h3>
 * <pre>{@code
 * // 创建实体后保存 IBA 数据
 * productLineMapper.insert(productLine);
 * ibaDataService.save(productLine);
 *
 * // 查询实体后还原 IBA 数据
 * ProductLine pl = productLineMapper.selectByOid(oid);
 * ibaDataService.restore(pl);
 *
 * // 更新实体后同步 IBA 数据
 * productLineMapper.update(productLine);
 * ibaDataService.save(productLine);
 *
 * // 删除实体时级联删除 IBA 数据
 * productLineMapper.deleteByOid(oid);
 * ibaDataService.deleteByEntity("ck_product_line", oid);
 * }</pre>
 *
 * @see IBADataService
 */
public interface IBAExtensible {

    /** 全局 ObjectMapper（线程安全） */
    ObjectMapper MAPPER = new ObjectMapper();

    /** 实体类型标识（如 ck_product_line, team, document 等） */
    String getEntityType();

    /** 实体 oid */
    String getEntityOid();

    /** 获取 IBA 动态属性 Map */
    @JsonIgnore
    Map<String, Object> getExtAttrs();

    /** 设置 IBA 动态属性 Map */
    void setExtAttrs(Map<String, Object> extAttrs);

    /**
     * 将扩展属性序列化为 JSON 字符串（IBADataService 持久化用）。
     * @return JSON 字符串，无数据时返回 {@code null}
     */
    @JsonIgnore
    default String getExtAttrsJson() {
        Map<String, Object> attrs = getExtAttrs();
        if (attrs == null || attrs.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attrs);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为扩展属性 Map（IBADataService 还原用）。
     * @param json JSON 字符串，{@code null} 或空字符串则清空
     */
    @JsonIgnore
    default void setExtAttrsJson(String json) {
        if (json == null || json.isEmpty()) {
            setExtAttrs(null);
            return;
        }
        try {
            Map<String, Object> attrs = MAPPER.readValue(
                    json, new TypeReference<LinkedHashMap<String, Object>>() {});
            setExtAttrs(attrs);
        } catch (Exception e) {
            setExtAttrs(null);
        }
    }

    /**
     * 从 Map 批量注入 IBA 属性值。
     * 通常由 IBADataService.restore() 调用。
     */
    default void injectExtAttrs(Map<String, Object> ibaValues) {
        if (ibaValues == null || ibaValues.isEmpty()) {
            setExtAttrs(null);
            return;
        }
        Map<String, Object> attrs = getExtAttrs();
        if (attrs == null) {
            attrs = new LinkedHashMap<>();
            setExtAttrs(attrs);
        }
        attrs.putAll(ibaValues);
    }
}
