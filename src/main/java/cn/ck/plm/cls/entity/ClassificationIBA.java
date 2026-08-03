package cn.ck.plm.cls.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 分类-IBA属性关联实体 —— 对应 ck_cls_iba 表。
 *
 * <p>建立分类与 IBA 的多对多关联关系，并可在映射层覆写
 * {@code required} 和 {@code defaultValue}。
 *
 * <h3>与 TypeIBA 的区别</h3>
 * <ul>
 *   <li>分类没有类型继承链，无需 entityCode 和递归查询</li>
 *   <li>分类是纯业务配置，分类之间无父子 IBA 继承关系</li>
 * </ul>
 */
public class ClassificationIBA extends BaseEntity implements TenantEntity {

    /** 所属分类 oid（关联 ck_classification.oid） */
    private String classificationOid;

    /** IBA oid */
    private String ibaOid;

    /** 是否必填（映射层可覆写 IBA 本身的 required） */
    private boolean required;

    /** 默认值（映射层可覆写 IBA 本身的 defaultValue） */
    private String defaultValue;

    /** 排序序号 */
    private int sortOrder;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ===== 非持久化字段（查询联表填充） =====

    private String ibaCode;
    private String ibaName;
    private String ibaDisplayName;
    private String ibaDataType;

    // ==================== 构造方法 ====================

    public ClassificationIBA() {}

    public ClassificationIBA(String classificationOid, String ibaOid) {
        this.classificationOid = classificationOid;
        this.ibaOid = ibaOid;
    }

    // ==================== Getter / Setter ====================

    public String getClassificationOid() { return classificationOid; }
    public void setClassificationOid(String classificationOid) { this.classificationOid = classificationOid; }

    public String getIbaOid() { return ibaOid; }
    public void setIbaOid(String ibaOid) { this.ibaOid = ibaOid; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    @Override public String getTenantOid() { return tenantOid; }
    @Override public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getIbaCode() { return ibaCode; }
    public void setIbaCode(String ibaCode) { this.ibaCode = ibaCode; }

    public String getIbaName() { return ibaName; }
    public void setIbaName(String ibaName) { this.ibaName = ibaName; }

    public String getIbaDisplayName() { return ibaDisplayName; }
    public void setIbaDisplayName(String ibaDisplayName) { this.ibaDisplayName = ibaDisplayName; }

    public String getIbaDataType() { return ibaDataType; }
    public void setIbaDataType(String ibaDataType) { this.ibaDataType = ibaDataType; }

    @Override
    public String toString() {
        return "ClassificationIBA{classificationOid='" + classificationOid
                + "', ibaOid='" + ibaOid + "'}";
    }
}
