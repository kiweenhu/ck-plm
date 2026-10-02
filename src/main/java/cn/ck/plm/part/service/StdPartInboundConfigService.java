/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.part.entity.StdPartInboundConfig;
import cn.ck.plm.part.mapper.StdPartInboundConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 标准件入库流程配置读写。
 *
 * <p>口径与「通用件阈值配置」一致：<b>读缺省</b>（没配过返回默认值、不落库，界面进来能看到
 * 默认口径而不是空白）、<b>一租户一行</b>（有则更新、无则插入，不提供删除）。
 *
 * <p>额外一条校验：<b>启用入库流程必须绑定流程模板</b> —— 否则"要审批但不知道走哪条流程"，
 * 真到入库那一步只能报错，不如保存时就拦下来。
 */
@Service
public class StdPartInboundConfigService {

    private static final Logger log = LoggerFactory.getLogger(StdPartInboundConfigService.class);

    @Autowired
    private StdPartInboundConfigMapper mapper;

    /** 当前租户的配置（没配过返回默认值） */
    public StdPartInboundConfig get() {
        StdPartInboundConfig existing = mapper.selectByTenant(TenantContext.get());
        return existing != null ? existing : StdPartInboundConfig.defaults();
    }

    /** 保存配置（一租户一行） */
    @Transactional
    public StdPartInboundConfig save(StdPartInboundConfig input) {
        if (input == null) {
            throw new IllegalArgumentException("缺少配置内容");
        }
        StdPartInboundConfig config = input;
        if (config.getEnabled() == null) {
            config.setEnabled(Boolean.FALSE);
        }
        if (Boolean.TRUE.equals(config.getEnabled()) && isBlank(config.getProcessTemplateOid())) {
            throw new IllegalArgumentException("启用入库流程时必须选择一个流程模板（否则入库时不知道走哪条流程）");
        }
        if (isBlank(config.getProcessTemplateOid())) {
            config.setProcessTemplateOid(null);
        }

        String tenant = TenantContext.get();
        String user = UserContext.get();
        LocalDateTime now = LocalDateTime.now();

        StdPartInboundConfig existing = mapper.selectByTenant(tenant);
        if (existing != null) {
            config.setOid(existing.getOid());
            config.setTenantOid(existing.getTenantOid());
            config.setUpdater(user);
            config.setUpdatedAt(now);
            mapper.update(config);
            log.info("标准件入库流程配置已更新: tenant={} 启用={} 流程模板={}",
                    tenant, config.getEnabled(), config.getProcessTemplateOid());
        } else {
            config.setOid(UUID.randomUUID().toString());
            config.setTenantOid(tenant);
            config.setCreator(user);
            config.setCreatedAt(now);
            config.setUpdater(user);
            config.setUpdatedAt(now);
            mapper.insert(config);
            log.info("标准件入库流程配置已创建: tenant={} 启用={} 流程模板={}",
                    tenant, config.getEnabled(), config.getProcessTemplateOid());
        }
        return mapper.selectByTenant(tenant);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
