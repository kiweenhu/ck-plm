/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

/**
 * 流程模板部署结果。
 *
 * <p>部署语义：同 key 再次部署会在 Flowable 中生成新的<b>流程定义版本</b>
 * （{@code processDefinitionVersion} 递增），在途实例继续跑旧版本，新发起的实例走新版本
 * —— 这正是「模板版本化 + 部署开关（允许部署 / 停止部署）」所依赖的引擎行为。
 */
public class ProcessTemplateDeployResult {

    /** 模板 oid */
    private String templateOid;

    /** 模板 key */
    private String key;

    /** 本次部署的模板版本号 */
    private Integer version;

    /** Flowable 部署 id */
    private String deploymentId;

    /** Flowable 流程定义 id（形如 key:1:1234） */
    private String processDefinitionId;

    /** Flowable 流程定义版本号（同 key 每次部署递增） */
    private Integer processDefinitionVersion;

    public String getTemplateOid() {
        return templateOid;
    }

    public void setTemplateOid(String templateOid) {
        this.templateOid = templateOid;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Integer getProcessDefinitionVersion() {
        return processDefinitionVersion;
    }

    public void setProcessDefinitionVersion(Integer processDefinitionVersion) {
        this.processDefinitionVersion = processDefinitionVersion;
    }
}
