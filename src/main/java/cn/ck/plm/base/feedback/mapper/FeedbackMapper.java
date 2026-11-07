/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.mapper;

import cn.ck.plm.base.feedback.entity.Feedback;

import java.util.List;

/**
 * 使用反馈数据访问接口。
 *
 * <p>ck_feedback 不参与租户隔离（平台跨租户查看），SQL 里不要写 tenant_oid 条件。
 */
public interface FeedbackMapper {

    int insert(Feedback feedback);

    /** 单条详情（含富文本正文） */
    Feedback selectByOid(String oid);

    /**
     * 最近的反馈（平台管理员查看用）。
     *
     * <p><b>刻意不查 content</b>：正文可能内嵌 base64 截图，列表一次取几百条会把响应撑到几十 MB。
     * 列表只要标题与状态，正文走 {@link #selectByOid}。
     *
     * @param status 为空则不限状态
     */
    List<Feedback> selectRecent(int limit, String status);

    /**
     * 某个提交人的反馈列表（本人看「我的反馈」，含状态与回复）。
     *
     * <p>同样不查 content —— 列表不需要正文，省掉内嵌截图。
     */
    List<Feedback> selectByReporter(String reporterOid, int limit);

    /** 未处理条数（角标用） */
    int countUnprocessed();

    /** 写入回复（reply / reply_at / replier_name / status） */
    int updateReply(Feedback feedback);
}
