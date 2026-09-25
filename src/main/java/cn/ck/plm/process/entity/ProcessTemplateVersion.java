/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 流程模板版本 —— 每次保存生成一版，构成可回看的版本历史（spec §4-H P0）。
 *
 * <p>{@code dslJson} 是该版本的唯一事实源；{@code bpmnXml} 是<b>部署时的编译产物快照</b>
 * （未部署则为 null），用于「这个版本当时到底部署了什么」的可追溯性
 * —— 因为 DSL→BPMN 的编译逻辑会随版本演进，事后重编译未必得到同样的 XML。
 */
public class ProcessTemplateVersion extends BaseEntity implements TenantEntity {

    /** 所属模板 oid */
    private String templateOid;

    /** 版本号（同一模板内自增，从 1 开始） */
    private Integer version;

    /** 该版本的 DSL JSON（唯一事实源） */
    private String dslJson;

    /** 部署时的 BPMN XML 快照（未部署为 null） */
    private String bpmnXml;

    /** 变更说明 */
    private String changeNote;

    /** 该版本是否已部署 */
    private Boolean deployed;

    /** Flowable 部署 id（该版本部署后写入） */
    private String deploymentId;

    /** 租户 oid */
    private String tenantOid;

    public String getTemplateOid() {
        return templateOid;
    }

    public void setTemplateOid(String templateOid) {
        this.templateOid = templateOid;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getDslJson() {
        return dslJson;
    }

    public void setDslJson(String dslJson) {
        this.dslJson = dslJson;
    }

    public String getBpmnXml() {
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml) {
        this.bpmnXml = bpmnXml;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public Boolean getDeployed() {
        return deployed;
    }

    public void setDeployed(Boolean deployed) {
        this.deployed = deployed;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }
}
