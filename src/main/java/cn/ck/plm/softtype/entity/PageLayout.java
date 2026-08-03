/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 页面布局定义 —— 对应 ck_type_page_layout 表。
 *
 * <p>为 ModelClass / SoftType 的每个操作（create / update / detail / list / 自定义）
 * 存储低代码页面的完整布局配置。
 *
 * <h3>租户隔离策略</h3>
 * <ul>
 *   <li>平台租户 (PLATFORM_TENANT_OID) 持有默认布局</li>
 *   <li>普通租户可自定义覆盖，查询时优先取本租户，找不到回退到平台租户</li>
 * </ul>
 *
 * <h3>系统预置 operationCode</h3>
 * <ul>
 *   <li>list    — 列表页（搜索区 + 表格列）</li>
 *   <li>create  — 新建页（表单）</li>
 *   <li>update  — 编辑页（表单）</li>
 *   <li>detail  — 详情页（只读展示）</li>
 * </ul>
 *
 * <p>用户可新增自定义 operationCode（如 approve、import 等）。
 *
 * <h3>layoutJson 结构</h3>
 * <pre>
 * {
 *   "search": { ... },
 *   "table":  { ... },
 *   "form":   { ... }
 * }
 * </pre>
 */
public class PageLayout extends BaseEntity implements TenantEntity {

    /** 关联实体 OID（TypeDefinition.oid） */
    private String entityOid;

    /** 实体类型编码（TypeDefinition.code，如 ck_product_line） */
    private String entityCode;

    /** 操作编码：list | create | update | detail | 用户自定义 */
    private String operationCode;

    /** 操作显示名称 */
    private String operationName;

    /** 布局 JSON 配置（完整页面定义） */
    private String layoutJson;

    /** 租户 oid（平台租户=默认布局，普通租户=自定义布局） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PageLayout() {
    }

    public PageLayout(String entityOid, String entityCode, String operationCode,
                      String operationName, String layoutJson) {
        this.entityOid = entityOid;
        this.entityCode = entityCode;
        this.operationCode = operationCode;
        this.operationName = operationName;
        this.layoutJson = layoutJson;
    }

    // ==================== Getter / Setter ====================

    public String getEntityOid() { return entityOid; }
    public void setEntityOid(String entityOid) { this.entityOid = entityOid; }

    public String getEntityCode() { return entityCode; }
    public void setEntityCode(String entityCode) { this.entityCode = entityCode; }

    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public String getLayoutJson() { return layoutJson; }
    public void setLayoutJson(String layoutJson) { this.layoutJson = layoutJson; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "PageLayout{" +
                "entityOid='" + entityOid + '\'' +
                ", entityCode='" + entityCode + '\'' +
                ", operationCode='" + operationCode + '\'' +
                '}';
    }
}
