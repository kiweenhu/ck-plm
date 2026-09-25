/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

/**
 * 自动服务节点的<b>作用对象</b> —— 流程实例关联的一个业务实体（{@code ck_process_entity_set} 一行）。
 *
 * <p>一次流程可以带一批对象走审批，所以服务节点的作用对象是个<b>集合</b>而不是单个：
 * 「设置状态」要把这批对象一起设到目标状态，而不是只改"主对象"。
 *
 * <p>{@code entityVersion} 是大版本（如 A）；需要落到具体版本时统一解析为
 * "该大版本当前的最新小版本"（见 ProcessEntitySet 的说明）。
 */
public class PlmServiceTarget {

    /** 业务对象类型编码（如 ELECTRONIC）：统一入口按它路由到宿主能力 */
    private final String typeCode;

    /** 能力宿主（如 PART / DOCUMENT）：检出·检入按它分派 provider */
    private final String rootTypeCode;

    /** 主对象 oid */
    private final String entityOid;

    /** 业务对象大版本（如 A）；可能为空（无版本对象） */
    private final String entityVersion;

    /** 流程实例的业务标识（发起上下文那个对象） */
    private final String businessKey;

    public PlmServiceTarget(String typeCode, String rootTypeCode, String entityOid,
                           String entityVersion, String businessKey) {
        this.typeCode = typeCode;
        this.rootTypeCode = rootTypeCode;
        this.entityOid = entityOid;
        this.entityVersion = entityVersion;
        this.businessKey = businessKey;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getRootTypeCode() {
        return rootTypeCode;
    }

    public String getEntityOid() {
        return entityOid;
    }

    public String getEntityVersion() {
        return entityVersion;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    /** 出错时能说清"是哪一个对象"（界面上的名字由前端回查，这里给得住的是类型 + oid） */
    public String label() {
        String type = typeCode != null ? typeCode : rootTypeCode;
        String version = entityVersion != null && !entityVersion.isEmpty() ? " 版本 " + entityVersion : "";
        return (type != null ? type : "对象") + " " + entityOid + version;
    }
}
