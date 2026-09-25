/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.api;

import cn.ck.plm.iam.dto.NotificationChannelVO;

import java.util.List;

/**
 * 通知渠道 —— 「系统当前用什么方式发通知」的唯一答案（读 {@code plm.notification} 配置）。
 *
 * <p>存在的意义：通知的渠道与凭据是<b>系统级配置</b>（application.yml），
 * 但需要它的人分布在三处，且都不该直接碰 {@code @ConfigurationProperties}：
 * <ul>
 *   <li><b>发通知的人</b>（流程侧、公告）—— 发之前先问"这个渠道现在能用吗"；</li>
 *   <li><b>流程运行期</b>（通知节点）—— 失败信息要说清"系统启用了哪些、缺什么"，而不是笼统一句"未实现"；</li>
 *   <li><b>设计器 / 管理界面</b>—— 只能从系统已启用的渠道里挑，不能凭空写一个发不出去的。</li>
 * </ul>
 */
public interface NotificationChannelService {

    /** 通知总开关（{@code plm.notification.enabled}） */
    boolean enabled();

    /** 已启用<b>且凭据齐备</b>的渠道编码（真正能发出去的） */
    List<String> usableChannels();

    /**
     * 该渠道现在能不能用：总开关开着、在启用列表里、凭据齐备。
     *
     * <p>发通知前用它判断 —— 不能用就跳过并记一条日志，而不是"以为发了"。
     */
    boolean isUsable(String channel);

    /**
     * 全部渠道的视图（含未启用的）：编码、显示名、是否启用、是否已配好、还缺什么。
     *
     * <p>界面要能显示"系统里有哪些渠道、哪些没启用"，所以这里给全量而不是只给可用的。
     */
    List<NotificationChannelVO> channelViews();

    /** 一句话描述当前可用的通知方式（给界面与失败信息用），如「CK-PLM（站内）、邮件系统」 */
    String describeUsable();
}
