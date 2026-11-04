/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;
import cn.ck.plm.improve.flow.IssueFlow;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 问题实体 —— 持续改进的工作面：一条 = 一个问题单。
 *
 * <h3>为什么独立于流程引擎</h3>
 * 问题多数来自现场 / 审核 / 测试（没有 BPMN 可挂），需要"轻量闭环"：
 * 固定五态状态机（{@link IssueFlow}）+ 每次动作留痕（{@link IssueActivity}），
 * 而不是让每个企业先画一张流程图才能记问题。
 *
 * <h3>与问题类型模板的关系</h3>
 * {@link #issueType} 指向本租户的 {@code ck_issue_type_template.code}（随包装入、企业可裁剪）——
 * 类型给出默认严重度，以及"是否必须根因分析 / 必须复测"的约束（服务层按类型校验，
 * 本实体不冗余这些开关）。
 *
 * <h3>闭环判据</h3>
 * 一条问题算"已闭环"当且仅当 {@link #status} = {@code CLOSED}（经"提交验证 → 验证通过"两步，
 * 不许一步关闭）；"闭环率" = CLOSED /（全部 - CANCELLED）。
 */
public class Issue extends WithoutVersionEntity implements TenantEntity {

    /** 问题类型 code（→ ck_issue_type_template.code）；空 = 未分类 */
    private String issueType;

    /** 严重度：{@code BLOCK}（阻塞）/ {@code WARN}（警告）—— 与检查规则、阶段门同一口径 */
    private String severity;

    /** 来源：{@code AUDIT} / {@code CUSTOMER} / {@code PRODUCTION} / {@code TEST} / {@code OTHER}；空 = 未填 */
    private String source;

    /** 状态：{@code OPEN} / {@code IN_PROGRESS} / {@code VERIFYING} / {@code CLOSED} / {@code CANCELLED}（见 IssueFlow） */
    private String status;

    /** 责任人（用户名；中文名由前端按用户表映射展示） */
    private String owner;

    /** 提出人（用户名，创建时自动 = 当前用户） */
    private String reporter;

    /** 计划完成日期 */
    private LocalDate dueDate;

    /** 关联产品系列/型号 oid（IPD 上下文；服务端必填 —— 无归属的问题统计不出来） */
    private String productOid;

    /** 产品类型：{@code PRODUCT_LINE} / {@code PRODUCT_MODEL}（与 /instances 的 ownerType 同口径） */
    private String productType;

    /** 关联阶段门（→ ck_gate.oid；可选 —— 不是每个问题都来自门评审） */
    private String gateOid;

    /**
     * 阶段门的显示标签（如 {@code TR3 详细设计评审（投板门）}）——
     * <b>响应装配字段，不入库</b>：列表 / 详情由服务层按 gateOid 补齐，前端不必再取门清单。
     */
    private String gateLabel;

    /** 关联对象类型（如 {@code PART} / {@code DOCUMENT}；空 = 不关联） */
    private String relatedType;

    /** 关联对象编码 */
    private String relatedCode;

    /** 根因分析 */
    private String rootCause;

    /** 处理措施 */
    private String resolution;

    /** 验证结论（验证通过时必填） */
    private String verifyResult;

    /** 关闭时间（CLOSED 时填；重开清空） */
    private LocalDateTime closedAt;

    private String tenantOid;

    public Issue() {}

    public Issue(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getProductOid() { return productOid; }
    public void setProductOid(String productOid) { this.productOid = productOid; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getGateOid() { return gateOid; }
    public void setGateOid(String gateOid) { this.gateOid = gateOid; }

    public String getGateLabel() { return gateLabel; }
    public void setGateLabel(String gateLabel) { this.gateLabel = gateLabel; }

    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }

    public String getRelatedCode() { return relatedCode; }
    public void setRelatedCode(String relatedCode) { this.relatedCode = relatedCode; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getVerifyResult() { return verifyResult; }
    public void setVerifyResult(String verifyResult) { this.verifyResult = verifyResult; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
