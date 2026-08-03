package cn.ck.plm.cls.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 分类 IBA 页面布局定义 —— 对应 ck_cls_page_layout 表。
 */
public class ClsPageLayout extends BaseEntity implements TenantEntity {

    private String clsOid;
    private String operationCode;
    private String operationName;
    private String layoutJson;
    private String tenantOid;

    public ClsPageLayout() {}

    public ClsPageLayout(String clsOid, String operationCode, String operationName, String layoutJson) {
        this.clsOid = clsOid;
        this.operationCode = operationCode;
        this.operationName = operationName;
        this.layoutJson = layoutJson;
    }

    public String getClsOid() { return clsOid; }
    public void setClsOid(String clsOid) { this.clsOid = clsOid; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getLayoutJson() { return layoutJson; }
    public void setLayoutJson(String layoutJson) { this.layoutJson = layoutJson; }
    @Override public String getTenantOid() { return tenantOid; }
    @Override public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ClsPageLayout{clsOid='" + clsOid + "', operationCode='" + operationCode + "'}";
    }
}
