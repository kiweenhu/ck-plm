/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.dto;

/**
 * 某零件是否满足「升级为通用件」的阈值口径。
 *
 * <p>同一份结果同时给前端菜单（能不能发起）与后端发起闸门（防绕过）使用，
 * 避免"菜单放得进去、提交又被拒"的口径漂移。
 */
public class GenPartEligibility {

    /** 是否达到阈值；配置未启用时按不拦截处理（true） */
    private boolean eligible;

    /** 统计窗口内引用该零件的 BOM 行数 */
    private int usageCount;

    /** 沿 BOM 向上追溯覆盖到的不同产品型号数 */
    private int modelCount;

    /** 当前配置的 BOM 引用行数门槛 */
    private int minUsageCount;

    /** 当前配置的不同型号数门槛 */
    private int minModelCount;

    /** 统计窗口（月），0 = 不限时间 */
    private int statWindowMonths;

    /** 不达标原因（可直接显示给发起人和审批人） */
    private String reason;

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    public int getModelCount() { return modelCount; }
    public void setModelCount(int modelCount) { this.modelCount = modelCount; }

    public int getMinUsageCount() { return minUsageCount; }
    public void setMinUsageCount(int minUsageCount) { this.minUsageCount = minUsageCount; }

    public int getMinModelCount() { return minModelCount; }
    public void setMinModelCount(int minModelCount) { this.minModelCount = minModelCount; }

    public int getStatWindowMonths() { return statWindowMonths; }
    public void setStatWindowMonths(int statWindowMonths) { this.statWindowMonths = statWindowMonths; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
