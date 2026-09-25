/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.List;

/**
 * 「发起流程」闸门的判定结果 —— 按【业务对象 + 大版本】看该版本是否已有流程在执行。
 *
 * <p>判定口径（与 {@code ProcessEntitySet#getEntityVersion()} 的语义一致）：
 * <ul>
 *   <li><b>粒度是「业务对象 + 大版本」</b>：同一对象的不同大版本彼此独立 —— B 版在跑流程时，
 *       不能让 A 版的收尾工作也被挡在门外（大版本之间本就是两轮工作）；</li>
 *   <li>是否"在跑"以流程引擎<b>运行时</b>为准：已结束的不算，<b>挂起仍算</b>（它没有结束）；</li>
 *   <li>{@code version} 为空时按对象<b>当前大版本</b>解析 —— 与写关联时同一套口径，
 *       否则"闸门放行的版本"和"记进关联的版本"会各说各话。</li>
 * </ul>
 *
 * <p>{@link #displayVersion} / {@link #statusCode} 是<b>该大版本当前最新小版本</b>及其生命周期状态，
 * 只用于把拒绝原因说清楚（「版本 A 当前 A.2 · DRAFT 已有流程在执行」），<b>不参与判定</b>
 * —— 在跑的流程可能是在上一个状态发起的，状态已经推进过，用它做判定会漏挡。
 */
public class ProcessStartConflictVO {

    /** 业务对象主 oid */
    private String entityOid;

    /** 能力宿主（{@code type_definition.root_type_code}） */
    private String rootTypeCode;

    /** 判定所用的大版本（调用方没给时为解析出的"当前大版本"） */
    private String entityVersion;

    /** 该大版本当前最新小版本的显示版本（如 A.2），仅用于提示；拿不到为 null */
    private String displayVersion;

    /** 该最新小版本的当前状态 code，仅用于提示；拿不到为 null */
    private String statusCode;

    /** 该最新小版本的当前状态显示名，仅用于提示；拿不到为 null */
    private String statusName;

    /** 该大版本下仍在执行的流程实例 id；空 = 可以发起 */
    private List<String> runningInstanceIds = List.of();

    /** 是否被闸门拦住 */
    public boolean isBlocked() {
        return runningInstanceIds != null && !runningInstanceIds.isEmpty();
    }

    // ==================== Getter / Setter ====================

    public String getEntityOid() { return entityOid; }
    public void setEntityOid(String entityOid) { this.entityOid = entityOid; }

    public String getRootTypeCode() { return rootTypeCode; }
    public void setRootTypeCode(String rootTypeCode) { this.rootTypeCode = rootTypeCode; }

    public String getEntityVersion() { return entityVersion; }
    public void setEntityVersion(String entityVersion) { this.entityVersion = entityVersion; }

    public String getDisplayVersion() { return displayVersion; }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public List<String> getRunningInstanceIds() { return runningInstanceIds; }
    public void setRunningInstanceIds(List<String> runningInstanceIds) {
        this.runningInstanceIds = runningInstanceIds == null ? List.of() : runningInstanceIds;
    }
}
