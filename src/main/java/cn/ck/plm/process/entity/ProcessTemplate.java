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
 * 流程模板主档 —— 前端流程设计器的持久化对象。
 *
 * <p>对应 docs/ck-plm-flow-designer-spec.md §2.3：模板主存 <b>DSL JSON</b>（{@code dslJson}），
 * 每次保存生成一条 {@link ProcessTemplateVersion}；部署时把前端编译出的 BPMN XML
 * 存档到版本行（{@code bpmn_xml}）并记录 Flowable 部署标识。
 *
 * <p>与引擎的关系：本对象只是设计态的模板，部署后由 Flowable 的
 * {@code ck_process_template.process_definition_id} 指向具体流程定义版本。
 *
 * <p><b>业务实体关联</b>：本类刻意<b>不再持有"主业务对象"字段</b>（原 {@code primaryObjectType}
 * 已连数据库列一并移除）。一个流程可能关联多个业务实体，单一 code 表达不了 —— 该关联由
 * {@code ProcessEntitySet}（流程关联的业务实体集合）承担，不在模板主档上表达。
 * 节点级的业务对象绑定（如审批节点的 {@code binding}）不受影响，仍随 DSL 保存。
 */
public class ProcessTemplate extends BaseEntity implements TenantEntity {

    /** 流程定义 key（部署到 Flowable 的 processDefinitionKey，租户内唯一） */
    private String key;

    /** 模板名称（同时作为部署名） */
    private String name;

    /** 显示名（列表展示用，缺省回退 name） */
    private String displayName;

    /**
     * 所属分组 oid（引用 {@code ck_process_category.oid}）。
     *
     * <p>存 oid 而不是分组名：分组是清单页的导航维度、将来还可能承载权限与外部引用，
     * 引用必须<b>稳定</b> —— 改个显示名不该牵动任何一张流程模板。分组名一律通过字典解析，
     * 模板表里不再保留名称冗余（避免"改字典忘了同步模板"这类两处真相）。
     */
    private String categoryOid;

    /** 描述 */
    private String description;

    /** 最新版本的 DSL JSON（与 latestVersion 对应） */
    private String dslJson;

    /** 最新版本号（从 1 开始） */
    private Integer latestVersion;

    /**
     * 是否允许<b>再部署</b>该模板（发布闸门）。
     *
     * <p>{@code false} = 停止部署：不能发布新版本，但<b>已部署的流程定义与在途实例照旧运行</b>，
     * 也不影响编辑与保存（设计态与运行态本就分离）。
     *
     * <p><b>它不阻止"按 key 发起新实例"</b>：{@code ProcessServiceImpl#startProcess} 只认
     * {@code processKey}，不查模板 —— 也就是说 spec §4-H 的「模板上下架」目前只实现了
     * "停止部署"这一半。若要让停止部署真正等于"下架"，需要在发起入口加一道检查。
     */
    private Boolean enabled;

    /** 已成功部署的版本号 */
    private Integer deployedVersion;

    /** Flowable 部署 id */
    private String deploymentId;

    /** Flowable 流程定义 id（含版本，如 key:1:1234） */
    private String processDefinitionId;

    /** 部署时间 */
    private java.time.LocalDateTime deployedAt;

    /** 租户 oid */
    private String tenantOid;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCategoryOid() {
        return categoryOid;
    }

    public void setCategoryOid(String categoryOid) {
        this.categoryOid = categoryOid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDslJson() {
        return dslJson;
    }

    public void setDslJson(String dslJson) {
        this.dslJson = dslJson;
    }

    public Integer getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(Integer latestVersion) {
        this.latestVersion = latestVersion;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getDeployedVersion() {
        return deployedVersion;
    }

    public void setDeployedVersion(Integer deployedVersion) {
        this.deployedVersion = deployedVersion;
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

    public java.time.LocalDateTime getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(java.time.LocalDateTime deployedAt) {
        this.deployedAt = deployedAt;
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
