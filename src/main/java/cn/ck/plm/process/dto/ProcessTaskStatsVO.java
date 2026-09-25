/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

/**
 * 任务统计视图对象 —— 对应前端「任务中心」顶部徽标。
 *
 * <pre>
 * 前端取值：stats.todoCount / stats.claimableCount / stats.overdueCount / stats.doneCount
 * </pre>
 */
public class ProcessTaskStatsVO {

    /** 待办数量（指派给当前用户且未完成） */
    private int todoCount;

    /** 可认领数量（候选人为当前用户或其角色组，且未签收） */
    private int claimableCount;

    /** 逾期数量（待办中截止时间已过期） */
    private int overdueCount;

    /** 已办数量（当前用户已完成的历史任务） */
    private int doneCount;

    public ProcessTaskStatsVO() {
    }

    public ProcessTaskStatsVO(int todoCount, int claimableCount, int overdueCount, int doneCount) {
        this.todoCount = todoCount;
        this.claimableCount = claimableCount;
        this.overdueCount = overdueCount;
        this.doneCount = doneCount;
    }

    // ==================== Getter / Setter ====================

    public int getTodoCount() { return todoCount; }
    public void setTodoCount(int todoCount) { this.todoCount = todoCount; }

    public int getClaimableCount() { return claimableCount; }
    public void setClaimableCount(int claimableCount) { this.claimableCount = claimableCount; }

    public int getOverdueCount() { return overdueCount; }
    public void setOverdueCount(int overdueCount) { this.overdueCount = overdueCount; }

    public int getDoneCount() { return doneCount; }
    public void setDoneCount(int doneCount) { this.doneCount = doneCount; }
}
