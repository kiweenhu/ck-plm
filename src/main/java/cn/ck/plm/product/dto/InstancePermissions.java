/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.dto;

import java.util.List;

/**
 * 「我在这个系列 / 型号下能做什么」—— 随 {@code GET /api/instances} 一起返回的权限摘要
 * （2026-10-14 起：这些动作的权限按**项目级团队角色**判，不再是全局角色）。
 *
 * <p>前端用它决定按钮显隐与禁用；**真正的闸门在后端**（tailor / 关联 / 发起三处都会独立校验），
 * 前端判断错了也不会造成越权。
 *
 * <h3>口径（用户 2026-10-14 选定）</h3>
 * <pre>
 *   裁剪（标记不适用 / 改强制 / 改责任人）：团队角色 = 产品经理
 *   关联交付项：产品经理，或我的团队角色 = 该交付件的责任人（前端用 myRoleCodes 逐条判）
 *   发起流程（阶段门 / 评审）：产品经理 / 项目经理 / IPDT 组长
 *   查看被裁剪条目（本项目不适用）：产品经理 / 项目经理（可看不可改）
 *   管理员（PLATFORM_ADMIN / TENANT_ADMIN）：一律可做
 * </pre>
 */
public class InstancePermissions {

    /** 是否管理员（true 时下面两项必为 true） */
    private boolean admin;

    /** 可裁剪（标记不适用 / 恢复 / 改强制项 / 改责任人） */
    private boolean canTailor;

    /** 可发起流程（阶段门 / 评审） */
    private boolean canStartProcess;

    /**
     * 可查看被裁剪（本项目不适用）的条目 —— 产品经理 / 项目经理 / 管理员。
     *
     * <p>与 {@link #canTailor} 分开：可看 ≠ 可改。前端用它决定列表是否带出被裁剪行、
     * 以及「适宜 / 不适宜 / 所有」过滤是否出现。
     */
    private boolean canViewTailored;

    /** 我在本归属单元团队里的角色名（中文，如「产品经理」；型号含父系列团队） */
    private List<String> myRoleNames;

    /** 对应角色码（如 PRODUCT_MANAGER）—— 前端据此逐条判断"我是不是这条交付件的责任人" */
    private List<String> myRoleCodes;

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }

    public boolean isCanTailor() { return canTailor; }
    public void setCanTailor(boolean canTailor) { this.canTailor = canTailor; }

    public boolean isCanStartProcess() { return canStartProcess; }
    public void setCanStartProcess(boolean canStartProcess) { this.canStartProcess = canStartProcess; }

    public boolean isCanViewTailored() { return canViewTailored; }
    public void setCanViewTailored(boolean canViewTailored) { this.canViewTailored = canViewTailored; }

    public List<String> getMyRoleNames() { return myRoleNames; }
    public void setMyRoleNames(List<String> myRoleNames) { this.myRoleNames = myRoleNames; }

    public List<String> getMyRoleCodes() { return myRoleCodes; }
    public void setMyRoleCodes(List<String> myRoleCodes) { this.myRoleCodes = myRoleCodes; }
}
