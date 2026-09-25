/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.softtype.service.api.IBADataService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 实体 IBA 数据保存支持组件 —— 封装「请求体 → 实体 IBA 数据」的通用提取与持久化逻辑。
 *
 * <p>几乎所有 IBAExtensible 实体（Part / Document / ProductLine / Functional / Resource ...）
 * 在创建/更新时都需要：从请求体提取 IBA 字段，全量或合并保存到 {@code ck_type_iba_data}。
 * 这些逻辑原本散落在各 Controller 中重复实现，本组件将其收敛为统一入口，供各 Controller 注入复用。
 *
 * <p>分类 IBA（{@code ck_cls_iba_data}）的保存请使用 {@link cn.ck.plm.cls.service.impl.ClsIbaDataSupport}。
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>请求体中除「实体固定字段」与 {@code clsIbaValues} 之外的其余字段，视为实体 IBA 属性，
 *       由 {@link #extractIbaFromBody(Map)} 提取。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建
 * Entity created = service.create(entity);
 * ibaDataSupport.saveIbaValues("PART", created.getOid(), body);
 *
 * // 更新
 * Entity updated = service.update(entity);
 * ibaDataSupport.mergeIbaValues("PART", oid, body);
 * }</pre>
 */
@Component
public class IbaDataSupport {

    private final IBADataService ibaDataService;

    /**
     * 通用实体固定字段集合（BaseEntity / MasterEntity / IterationEntity 及
     * Part / Document / ProductLine 等实体的固定字段）。这些字段不会被当作 IBA 属性存储。
     * 新增实体字段时应同步在此登记，避免被误存为 IBA。
     */
    private static final Set<String> ENTITY_FIELDS = Set.of(
            // BaseEntity
            "oid", "creator", "createdAt", "updater", "updatedAt",
            // MasterEntity
            "name", "number", "description", "containerOid", "containerType",
            // IterationEntity
            "masterOid", "revision", "iteration", "displayVersion",
            "checkedOut", "checkedOutBy", "checkedOutComment", "checkedOutAt",
            "latest", "derivedFromOid", "derivedAt", "view",
            "status", "statusCode", "statusName", "lifecycleTemplateIterationOid",
            "versionSort", "branchId", "deleteMark",
            // 类型 / 分类 / 文件
            "typeDefinitionCode", "typeDefinitionName", "clsOid", "classificationOid",
            "ckfileOid", "attachmentOid",
            // 容器 / 位置 / 属性
            "folderOid", "stageOid", "unit", "source", "location",
            // EngineeringDocumentIteration 的 CAD 专有属性与 2D 制图属性（真实列，勿误存为 IBA）
            "cadName", "cadType", "cadTool",
            "sheetSize", "scale", "sheetNumber", "sheetCount", "projection",
            "author", "material", "weight",
            // EcadProject 专有属性（真实列，勿误存为 IBA）
            "domainOid", "relatedProduct", "projectPhase", "owner",
            // ProductLine / 树形展示特有
            "code", "thumbnail", "teamOid", "parentOid", "children", "nodeType", "icon", "extAttrs",
            // 通用标识 / 持久化状态
            "entityType", "iterationOid", "tenantOid", "new", "persisted",
            // IBA 数据载体（查询返回 / 请求提交）
            "clsIbaValues", "entityIba", "clsIba"
    );

    public IbaDataSupport(IBADataService ibaDataService) {
        this.ibaDataService = ibaDataService;
    }

    // ==================== 通用工具 ====================

    /** 提取请求体中的字符串字段（空字符串归一化为 null） */
    public String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null && !"".equals(val) ? val.toString() : null;
    }

    /** 判断字段名是否为实体固定字段（不会当作 IBA 属性存储） */
    public boolean isEntityField(String key) {
        return ENTITY_FIELDS.contains(key);
    }

    // ==================== 实体 IBA（ck_type_iba_data） ====================

    /**
     * 提取请求体中的 IBA 动态属性值（排除实体固定字段与 {@code clsIbaValues}）。
     */
    public Map<String, Object> extractIbaFromBody(Map<String, Object> body) {
        Map<String, Object> ibaValues = new LinkedHashMap<>();
        if (body == null) return ibaValues;
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null || "".equals(val)) continue;
            if (isEntityField(key)) continue;
            ibaValues.put(key, val);
        }
        return ibaValues;
    }

    /** 新建时全量保存实体 IBA 属性值（delete-all + insert-all） */
    public void saveIbaValues(String entityType, String entityOid, Map<String, Object> body) {
        Map<String, Object> ibaValues = extractIbaFromBody(body);
        if (!ibaValues.isEmpty()) {
            ibaDataService.saveValues(entityType, entityOid, ibaValues);
        }
    }

    /** 更新时合并保存实体 IBA 属性值（保留已持久化但本次未提交的字段） */
    public void mergeIbaValues(String entityType, String entityOid, Map<String, Object> body) {
        Map<String, Object> ibaValues = extractIbaFromBody(body);
        if (!ibaValues.isEmpty()) {
            ibaDataService.mergeValues(entityType, entityOid, ibaValues);
        }
    }
}
