/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.Date;
import java.util.List;

/**
 * 流程活动（节点）视图对象 —— 对象详情页「流程情况」里"这个流程走到哪了"的一条记录。
 *
 * <pre>
 * 前端取值：a.activityId / a.name / a.type / a.status / a.assignees / a.assigneeNames
 *          a.startTime / a.endTime / a.comment / a.outcome
 * status 取值：completed（已办）/ running（当前在办）/ pending（尚未到达）
 * </pre>
 *
 * <h3>为什么要"未到达"这一类</h3>
 * <p>用户看进度时不只想知道"走过哪几步"，更想知道"<b>还剩哪几步</b>"—— 只列已完成节点的话，
 * 一个刚发起的流程进度条几乎是空的，看不出后面还有几道关。因此从当前节点沿连线往下找到的
 * 人工/自动活动，会以 {@code pending} 一并给出（不评估网关条件，只做可达性，所以标注为"未开始"）。
 *
 * <h3>责任人为什么有两个字段</h3>
 * <p>Flowable 的 assignee 是<b>原样字符串</b>：可能是用户名（{@code ${initiator}} 求值结果），
 * 也可能是人员 <b>oid</b>（"设置审批人"按 oid 指派下游）。前者能直接显示，后者必须换成人名，
 * 所以 {@code assignees} 保留原值、{@code assigneeNames} 给可直接渲染的显示名。
 */
public class ProcessActivityVO {

    /** 活动 id（BPMN 节点 id，也是"设置审批人"变量 ckplmSetupAssignees_&lt;活动id&gt; 的后缀） */
    private String activityId;

    /** 活动名（BPMN 节点名） */
    private String name;

    /** 活动类型展示名：审批活动 / 设置流程参与者 / 自动活动 / 子流程 / 开始 / 结束 … */
    private String type;

    /** 状态：completed 已办 / running 当前在办 / pending 尚未到达 */
    private String status;

    /** 责任人（原值，可能是用户名或人员 oid；未指派时为空列表） */
    private List<String> assignees;

    /** 责任人显示名（已解析；解析不到时与 assignees 同值，前端优先用它） */
    private List<String> assigneeNames;

    /** 开始时间（pending 为 null） */
    private Date startTime;

    /** 结束时间（未结束 / pending 为 null） */
    private Date endTime;

    /** 办理意见（该活动下所有任务评论，按时间顺序用「；」连接；没有则 null） */
    private String comment;

    /** 该活动之后<b>实际走过</b>的那条连线的名称（如「同意」/「驳回」）；没走过或连线未命名则 null */
    private String outcome;

    /**
     * 这条活动实例对应的<b>任务 id</b>（会签 / 并行多实例时同一活动每人一条，各有一个任务 id）。
     *
     * <p>给会签表单用：要把"哪条是我"从其他人的记录里分出来。
     */
    private String taskId;

    /**
     * 这一票的结论：{@code APPROVE} 同意 / {@code REJECT} 驳回；未办理或存量数据为 null。
     *
     * <p>与 {@link #outcome} 的区别：{@code outcome} 是<b>活动级</b>的（这条活动整体走出的连线名），
     * 会签多人多票时不足以说明"谁投了什么"；本字段来自任务级局部变量，逐人独立。
     * 老实例没有记录 → null，前端显示"已办理"而不是猜一个结论。
     */
    private String decision;

    /**
     * 该节点落下的执行日志条数（后台执行痕迹，见 {@code ProcessNodeLog}）。
     *
     * <p>给时间轴用：有日志才值得点开看；没有日志的节点（如纯人工节点）点开是空的，
     * 界面据此决定"能不能点"。
     */
    private int nodeLogCount;

    /** 其中 ERROR 级条数 —— 时间轴上的红色角标，让"哪个节点出过错"一眼可见 */
    private int nodeErrorCount;

    // ==================== Getter / Setter ====================

    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getAssignees() { return assignees; }
    public void setAssignees(List<String> assignees) { this.assignees = assignees; }

    public List<String> getAssigneeNames() { return assigneeNames; }
    public void setAssigneeNames(List<String> assigneeNames) { this.assigneeNames = assigneeNames; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public int getNodeLogCount() { return nodeLogCount; }
    public void setNodeLogCount(int nodeLogCount) { this.nodeLogCount = nodeLogCount; }

    public int getNodeErrorCount() { return nodeErrorCount; }
    public void setNodeErrorCount(int nodeErrorCount) { this.nodeErrorCount = nodeErrorCount; }
}
