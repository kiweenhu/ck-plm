/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.dto;

/**
 * 变更请求关联到的一个问题（只取追溯与展示需要的四列）。
 *
 * <p>为什么不直接返回 {@code Issue}：变更单的问题列表是"引用"，不是问题的副本 ——
 * 返回整条问题（含根因、处理措施、验证结论）会让详情响应膨胀十倍，
 * 而前端在这一屏只想显示"编号 + 标题 + 状态"并跳到问题详情。
 */
public class ChangeIssueRef {

    /** 问题 oid（ck_issue.oid） */
    private String oid;

    /** 问题编号（ISS-0001） */
    private String code;

    /** 问题标题 */
    private String name;

    /** 问题状态（OPEN / IN_PROGRESS / VERIFYING / CLOSED / CANCELLED） */
    private String status;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
