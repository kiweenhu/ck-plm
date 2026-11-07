/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.service.api;

import cn.ck.plm.base.feedback.dto.FeedbackReplyRequest;
import cn.ck.plm.base.feedback.dto.FeedbackRequest;
import cn.ck.plm.base.feedback.entity.Feedback;

import java.util.List;

/**
 * 使用反馈服务。
 */
public interface FeedbackService {

    /**
     * 提交一条反馈，并给平台管理员发一条站内通知。
     *
     * @return 反馈 oid
     * @throws IllegalArgumentException 入参不合法（空标题 / 未知类型 / 超长）
     */
    String submit(FeedbackRequest request);

    /**
     * 最近反馈列表（平台管理员查看）。
     *
     * <p>不含富文本正文（可能内嵌 base64 截图），正文用 {@link #detail} 单条取。
     *
     * @param status NEW / PROCESSING / DONE / REJECTED，为空则不限
     */
    List<Feedback> listRecent(int limit, String status);

    /** 单条反馈详情（含正文） */
    Feedback detail(String oid);

    /**
     * 当前登录用户自己提交过的反馈（含状态与回复）。
     *
     * <p>列表不含正文；提交人账号查不到时返回空列表，不抛异常 —— 这只是个"我的历史"入口。
     */
    List<Feedback> listMine(int limit);

    /**
     * 回复反馈：落库回复内容，并把回复作为站内通知发给提交人。
     *
     * @return 是否找到了提交人账号（false = 回复已保存，但提交人账号不在了，无处可发）
     * @throws IllegalArgumentException 反馈不存在 / 回复为空或超长 / 状态不合法
     */
    boolean reply(String oid, FeedbackReplyRequest request);

    /** 未处理条数 */
    int countUnprocessed();
}
