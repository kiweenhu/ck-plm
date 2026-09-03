/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cls.service.impl;

import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.service.api.ClassificationService;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类 IBA 数据保存支持组件 —— 封装「请求体 → 分类 IBA 数据」的通用提取与持久化逻辑。
 *
 * <p>分类 IBA 值以 {@code clsIbaValues} 为 key 单独提交，本组件从请求体提取并保存到
 * {@code ck_cls_iba_data}（{@code entity_oid} 为迭代 oid 或分类节点默认空串）。
 *
 * <p>实体 IBA（{@code ck_type_iba_data}）的保存请使用
 * {@link cn.ck.plm.softtype.service.impl.IbaDataSupport}。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * clsIbaDataSupport.saveClsIbaValues(iterOid, created.getClsOid(), body);
 * }</pre>
 */
@Component
public class ClsIbaDataSupport {

    private final ClsIbaDataService clsIbaDataService;
    private final ClassificationService classificationService;

    public ClsIbaDataSupport(ClsIbaDataService clsIbaDataService,
                             ClassificationService classificationService) {
        this.clsIbaDataService = clsIbaDataService;
        this.classificationService = classificationService;
    }

    /**
     * 保存分类 IBA 属性值到对象实例/迭代（entity_oid = 迭代 oid 或分类节点默认空串）。
     *
     * <p>从请求体的 {@code clsIbaValues} 提取，并将字段名（布局 fieldName 为小写）
     * 归一化为分类绑定的原始 IBA code（如 {@code iba_cap} → {@code IBA_CAP}）。
     */
    public void saveClsIbaValues(String entityOid, String clsOid, Map<String, Object> body) {
        if (clsOid == null || body == null) return;
        Object raw = body.get("clsIbaValues");
        if (!(raw instanceof Map)) return;
        Map<?, ?> rawMap = (Map<?, ?>) raw;

        // 构建分类绑定 IBA 的 code 映射（小写 → 原始 code），用于归一化布局 fieldName 的大小写
        Map<String, String> codeMap = new HashMap<>();
        try {
            List<ClassificationIBA> ibas = classificationService.findIBAsByClassificationOid(clsOid);
            for (ClassificationIBA iba : ibas) {
                String code = iba.getIbaCode();
                if (code != null && !code.isEmpty()) {
                    codeMap.put(code.toLowerCase(), code);
                }
            }
        } catch (Exception e) {
            // 查询失败则不做归一化，按原始 key 保存
        }

        Map<String, Object> clsIbaValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : rawMap.entrySet()) {
            Object v = e.getValue();
            if (v == null || "".equals(v)) continue;
            String key = String.valueOf(e.getKey());
            String normalizedKey = codeMap.getOrDefault(key.toLowerCase(), key);
            clsIbaValues.put(normalizedKey, v);
        }
        if (!clsIbaValues.isEmpty()) {
            clsIbaDataService.saveValues(entityOid, clsOid, clsIbaValues);
        }
    }
}
