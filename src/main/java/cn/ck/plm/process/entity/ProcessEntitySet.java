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
 * 流程实例关联的业务实体集合（{@code ck_process_entity_set}）—— <b>一行 = 集合里的一个成员</b>。
 *
 * <p>它取代了流程模板上原来的 {@code primary_object_type}（单一 code 表达不了一个流程
 * 关联多个业务实体，见 {@code ProcessTemplateSchemaInitializer#dropLegacyTemplateColumns}）：
 * 一个流程实例可以关联多个业务实体，反向也能回答"这个业务对象参与过哪些流程"。
 *
 * <h3>实体引用（无版本 / 带版本）</h3>
 * <ul>
 *   <li><b>无版本对象</b>：只填 {@link #entityOid}，{@link #entityVersion} 为 {@code null}；</li>
 *   <li><b>带版本对象</b>：{@link #entityVersion} 填业务实体的<b>大版本</b>（{@code revision}，
 *       如 A / B），同时保留 {@link #entityOid}（主对象 oid）—— 只留版本的话，
 *       "某对象参与过的全部流程"就得先把它的所有大版本查出来才能反问。</li>
 * </ul>
 *
 * <h3>为什么把 typeCode / rootTypeCode 冗余存一份</h3>
 * <ul>
 *   <li><b>免 join</b>：列表/监控页要按类型名、能力宿主分组展示，不必每次回查
 *       {@code ck_type_definition}；</li>
 *   <li><b>快照语义</b>：类型改名、改挂父类型（{@code root_type_code} 变了）不该改写历史记录
 *       —— 这条记录要回答的是"当时关联的是什么类型的对象"。</li>
 * </ul>
 *
 * <p>{@link #rootTypeCode} 是<b>能力宿主</b>（{@code type_definition.root_type_code}，
 * 如 PART / DOCUMENT / ENG_DOCUMENT），决定该实体落在哪张业务表；
 * 调用方没填时由服务层按 {@link #typeCode} 补齐。
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>{@code processInstanceId} → Flowable 流程实例（{@code act_ru_execution.id_} / 历史表同 id）</li>
 *   <li>{@code entityOid} → 业务对象 oid（软引用，按 {@code rootTypeCode} 决定落在哪张表）</li>
 *   <li>{@code version} → 业务对象的<b>大版本</b>（{@code revision}，软引用）</li>
 * </ul>
 */
public class ProcessEntitySet extends BaseEntity implements TenantEntity {

    /** 发起流程时传入的业务标识（一般就是业务对象 oid） */
    private String businessKey;

    /** Flowable 流程实例 id */
    private String processInstanceId;

    /** 业务对象 oid（带版本对象时为<b>主对象</b> oid） */
    private String entityOid;

    /**
     * 业务对象的<b>大版本</b>（{@code revision}，如 A / B）；无版本对象为 {@code null}。
     *
     * <p>刻意不叫 {@code version}：本模块里还有"流程模板版本 / 流程定义版本"，光写 version
     * 分不清是谁的版本；{@code entityVersion} 一眼是<b>业务对象的版本</b>。
     *
     * <p><b>为什么记大版本而不是迭代 oid</b>：流程是针对"某个大版本"发起的，而大版本下还会继续
     * 产出小版本（检出 → A.2 → 检入 → A.3 …）。若记的是发起那一刻的迭代 oid，对象每推进一个
     * 小版本，这份关联就指向了一个<b>已经过时</b>的版本；记大版本则天然稳定。
     *
     * <p>需要落到具体版本的操作，统一解析为"该大版本<b>当前的最新小版本</b>"，
     * 而不是回头去记发起那一刻的迭代 oid。
     */
    private String entityVersion;

    /** 业务对象类型编码（{@code ck_type_definition.code}） */
    private String typeCode;

    /** 能力宿主编码（{@code ck_type_definition.root_type_code}，如 PART / DOCUMENT / ENG_DOCUMENT） */
    private String rootTypeCode;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public ProcessEntitySet() {
    }

    public ProcessEntitySet(String processInstanceId, String entityOid, String typeCode) {
        this.processInstanceId = processInstanceId;
        this.entityOid = entityOid;
        this.typeCode = typeCode;
    }

    // ==================== Getter / Setter ====================

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getEntityOid() {
        return entityOid;
    }

    public void setEntityOid(String entityOid) {
        this.entityOid = entityOid;
    }

    public String getEntityVersion() {
        return entityVersion;
    }

    public void setEntityVersion(String entityVersion) {
        this.entityVersion = entityVersion;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getRootTypeCode() {
        return rootTypeCode;
    }

    public void setRootTypeCode(String rootTypeCode) {
        this.rootTypeCode = rootTypeCode;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }

    @Override
    public String toString() {
        return "ProcessEntitySet{" +
                "processInstanceId='" + processInstanceId + '\'' +
                ", businessKey='" + businessKey + '\'' +
                ", typeCode='" + typeCode + '\'' +
                ", rootTypeCode='" + rootTypeCode + '\'' +
                ", entityOid='" + entityOid + '\'' +
                ", entityVersion='" + entityVersion + '\'' +
                '}';
    }
}
