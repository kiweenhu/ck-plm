/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.config.NotificationProperties;
import cn.ck.plm.iam.dto.NotificationChannelVO;
import cn.ck.plm.iam.service.api.NotificationChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NotificationChannelService} 的实现：把 {@code plm.notification} 翻译成别的模块好用的形态。
 *
 * <p>这里只做"查询与判断"，不做发送 —— 真正的发送在各渠道实现里（当前只有站内信，
 * 见 {@code NotificationService}）；邮件 / OA / 飞书等渠道的发送实现尚未落地，
 * 所以本服务会如实把它们标成"未启用/未配好"，而不是假装能用。
 */
@Service
public class NotificationChannelServiceImpl implements NotificationChannelService {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelServiceImpl.class);

    private final NotificationProperties properties;

    public NotificationChannelServiceImpl(NotificationProperties properties) {
        this.properties = properties;
        log.info("通知模式: enabled={}, 启用渠道={}", properties.isMasterEnabled(), properties.selectedChannels());
    }

    @Override
    public boolean enabled() {
        return properties.isMasterEnabled();
    }

    @Override
    public List<String> usableChannels() {
        if (!properties.isMasterEnabled()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String channel : properties.selectedChannels()) {
            if (properties.missingConfigOf(channel) == null) {
                result.add(channel);
            }
        }
        return result;
    }

    @Override
    public boolean isUsable(String channel) {
        String code = channel == null ? "" : channel.trim().toUpperCase();
        if (!properties.isMasterEnabled() || !properties.isChannelSelected(code)) {
            return false;
        }
        String missing = properties.missingConfigOf(code);
        if (missing != null) {
            // 配了但没配齐：说清楚缺什么，否则只会看到"通知没发出去"
            log.warn("通知渠道 {} 已启用但凭据不完整（{}），本次跳过发送", code, missing);
            return false;
        }
        return true;
    }

    @Override
    public List<NotificationChannelVO> channelViews() {
        List<NotificationChannelVO> views = new ArrayList<>();
        for (String code : NotificationProperties.allChannels()) {
            boolean selected = properties.isChannelSelected(code);
            String missing = properties.missingConfigOf(code);
            views.add(new NotificationChannelVO(
                    code,
                    NotificationProperties.labelOf(code),
                    selected,
                    properties.isMasterEnabled() && selected && missing == null,
                    missing));
        }
        return views;
    }

    @Override
    public String describeUsable() {
        if (!properties.isMasterEnabled()) {
            return "通知总开关已关闭（plm.notification.enabled=false）";
        }
        List<String> usable = usableChannels();
        if (usable.isEmpty()) {
            return "没有任何可用渠道（plm.notification.channels 为空，或已启用渠道的凭据未配齐）";
        }
        List<String> labels = new ArrayList<>();
        for (String code : usable) {
            labels.add(NotificationProperties.labelOf(code));
        }
        return String.join("、", labels);
    }
}
