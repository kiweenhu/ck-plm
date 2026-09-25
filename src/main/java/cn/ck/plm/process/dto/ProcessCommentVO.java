/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.Date;

/**
 * 流程任务评论视图对象 —— 对应前端评论弹窗字段。
 *
 * <pre>
 * 前端取值：c.id / c.userId / c.time / c.message
 * </pre>
 *
 * <p>数据来源为 Flowable 的 {@code act_hi_comment}（taskService.addComment 写入）。
 */
public class ProcessCommentVO {

    /** 评论 id */
    private String id;

    /** 评论人（username），系统写入时为 null */
    private String userId;

    /** 评论时间 */
    private Date time;

    /** 评论内容 */
    private String message;

    /** 评论类型（Flowable comment type，如 comment / event） */
    private String type;

    // ==================== Getter / Setter ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Date getTime() { return time; }
    public void setTime(Date time) { this.time = time; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
