/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

/**
 * 「发起流程」入口的解析结果 —— 业务对象（类型 + 状态）该发起哪个流程、以及该流程的最新版本信息。
 *
 * <p>来源链路：业务对象的 {@code typeDefinitionCode} → 类型 oid →
 * （类型 oid + 状态）在 {@code ck_type_lifecycle_state_process_link} 命中的流程模板 → 该模板的最新版本。
 *
 * <p>{@link #startable} 为 {@code false} 时 {@link #reason} 给出可读原因
 * （未配置 / 模板已删 / 尚未部署），前端据此<b>禁用「发起」并展示说明</b>，
 * 而不是让用户点了才吃报错。
 */
public class ProcessStartOptionVO {

    /** 业务对象的类型编码（入参回显） */
    private String typeDefinitionCode;

    /** 类型名称（展示用） */
    private String typeDefinitionName;

    /** 类型 oid（配置页跳转用） */
    private String typeOid;

    /** 业务对象当前状态（入参回显） */
    private String statusCode;

    /** 绑定的流程模板（null = 该状态未配置流程） */
    private ProcessTemplateInfo processTemplate;

    /** 流程模板的最新版本信息（{@code processTemplate} 为 null 时为 null） */
    private ProcessVersionInfo processVersion;

    /** 是否可发起（未配置 / 模板已删 / 尚未部署时为 false） */
    private boolean startable;

    /** 不可发起的原因（{@code startable = true} 时为 null） */
    private String reason;

    // ==================== Getter / Setter ====================

    public String getTypeDefinitionCode() {
        return typeDefinitionCode;
    }

    public void setTypeDefinitionCode(String typeDefinitionCode) {
        this.typeDefinitionCode = typeDefinitionCode;
    }

    public String getTypeDefinitionName() {
        return typeDefinitionName;
    }

    public void setTypeDefinitionName(String typeDefinitionName) {
        this.typeDefinitionName = typeDefinitionName;
    }

    public String getTypeOid() {
        return typeOid;
    }

    public void setTypeOid(String typeOid) {
        this.typeOid = typeOid;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public ProcessTemplateInfo getProcessTemplate() {
        return processTemplate;
    }

    public void setProcessTemplate(ProcessTemplateInfo processTemplate) {
        this.processTemplate = processTemplate;
    }

    public ProcessVersionInfo getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(ProcessVersionInfo processVersion) {
        this.processVersion = processVersion;
    }

    public boolean isStartable() {
        return startable;
    }

    public void setStartable(boolean startable) {
        this.startable = startable;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /** 绑定的流程模板（清单页身份信息） */
    public static class ProcessTemplateInfo {

        private String oid;
        private String key;
        private String name;
        private String displayName;
        private String description;
        /** 是否允许再部署（停止部署的模板仍可按 key 发起，故不参与 startable 判定） */
        private Boolean enabled;
        /** 模板当前最新版本号 */
        private Integer latestVersion;
        /** 已成功部署的版本号（null = 从未部署） */
        private Integer deployedVersion;
        /**
         * 当前租户下是否存在该流程的部署。
         *
         * <p>流程定义是<b>按租户</b>部署的（{@code ProcessDeploymentSupport} / 设计器部署都写当前租户），
         * 而发起用的是当前租户 —— 所以"模板部署过"不等于"当前租户能发起"。
         */
        private Boolean deployedInCurrentTenant;

        public String getOid() {
            return oid;
        }

        public void setOid(String oid) {
            this.oid = oid;
        }

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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getLatestVersion() {
            return latestVersion;
        }

        public void setLatestVersion(Integer latestVersion) {
            this.latestVersion = latestVersion;
        }

        public Integer getDeployedVersion() {
            return deployedVersion;
        }

        public void setDeployedVersion(Integer deployedVersion) {
            this.deployedVersion = deployedVersion;
        }

        public Boolean getDeployedInCurrentTenant() {
            return deployedInCurrentTenant;
        }

        public void setDeployedInCurrentTenant(Boolean deployedInCurrentTenant) {
            this.deployedInCurrentTenant = deployedInCurrentTenant;
        }
    }

    /** 流程模板的最新版本信息 */
    public static class ProcessVersionInfo {

        private Integer version;
        /** 该版本的变更说明 */
        private String changeNote;
        /** 该版本是否已部署 */
        private Boolean deployed;
        /** 版本创建时间（BaseEntity） */
        private java.time.LocalDateTime createdAt;

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
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

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
