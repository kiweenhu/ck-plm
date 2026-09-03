package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.TenantEntity;

public class Document extends MasterEntity implements TenantEntity {

    private String typeDefinitionCode;
    private String folderOid;
    private String stageOid;
    /** 分类 oid（关联 ck_classification.oid） */
    private String clsOid;
    private String tenantOid;

    public Document() {
        super();
    }

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getFolderOid() { return folderOid; }
    public void setFolderOid(String folderOid) { this.folderOid = folderOid; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    public String getClsOid() { return clsOid; }
    public void setClsOid(String clsOid) { this.clsOid = clsOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "Document{name='" + getName() + "', number='" + getNumber()
                + "', typeDefCode='" + typeDefinitionCode + "'}";
    }
}