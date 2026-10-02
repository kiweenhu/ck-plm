/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.part.entity.GenPartThresholdConfig;
import cn.ck.plm.part.mapper.GenPartThresholdConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通用件阈值配置读写。
 *
 * <p>两条口径：
 * <ol>
 *   <li><b>读缺省</b>：租户没配过就返回一份默认值（不落库）—— 界面进来能直接看到"默认口径"，
 *       而不是一片空白让人以为坏了；点保存才落库。</li>
 *   <li><b>一租户一行</b>：保存时"有则更新、无则插入"（同资源库分类绑定的套路），
 *       不对外开放删除 —— 配置不该有"删掉"这个状态，关掉用 {@code enabled}。</li>
 * </ol>
 */
@Service
public class GenPartThresholdConfigService {

    private static final Logger log = LoggerFactory.getLogger(GenPartThresholdConfigService.class);

    /** 阈值下限：低于 1 的"阈值"没有意义（0 = 所有零件都是候选） */
    private static final int MIN_THRESHOLD = 1;

    @Autowired
    private GenPartThresholdConfigMapper mapper;

    /** 当前租户的配置（没配过返回默认值） */
    public GenPartThresholdConfig get() {
        GenPartThresholdConfig existing = mapper.selectByTenant(TenantContext.get());
        return existing != null ? existing : GenPartThresholdConfig.defaults();
    }

    /** 保存配置（一租户一行） */
    @Transactional
    public GenPartThresholdConfig save(GenPartThresholdConfig input) {
        if (input == null) {
            throw new IllegalArgumentException("缺少配置内容");
        }
        GenPartThresholdConfig config = input;
        config.setMinModelCount(requireThreshold(config.getMinModelCount(), "跨型号数"));
        config.setMinUsageCount(requireThreshold(config.getMinUsageCount(), "引用次数"));
        // 统计窗口 0 = 不限时间；为空按 12 个月
        if (config.getStatWindowMonths() == null) {
            config.setStatWindowMonths(12);
        }
        if (config.getStatWindowMonths() < 0) {
            throw new IllegalArgumentException("统计窗口不能为负数（0 表示不限时间）");
        }
        if (config.getEnabled() == null) {
            config.setEnabled(Boolean.TRUE);
        }
        if (isBlank(config.getScopeTypeCode())) {
            config.setScopeTypeCode("STRUCTURAL");
        }

        String tenant = TenantContext.get();
        String user = UserContext.get();
        LocalDateTime now = LocalDateTime.now();

        GenPartThresholdConfig existing = mapper.selectByTenant(tenant);
        if (existing != null) {
            config.setOid(existing.getOid());
            config.setTenantOid(existing.getTenantOid());
            config.setUpdater(user);
            config.setUpdatedAt(now);
            mapper.update(config);
            log.info("通用件阈值配置已更新: tenant={} 跨型号>={} 引用>={} 窗口={}月 流程模板={}",
                    tenant, config.getMinModelCount(), config.getMinUsageCount(),
                    config.getStatWindowMonths(), config.getProcessTemplateOid());
        } else {
            config.setOid(UUID.randomUUID().toString());
            config.setTenantOid(tenant);
            config.setCreator(user);
            config.setCreatedAt(now);
            config.setUpdater(user);
            config.setUpdatedAt(now);
            mapper.insert(config);
            log.info("通用件阈值配置已创建: tenant={} 跨型号>={} 引用>={} 窗口={}月 流程模板={}",
                    tenant, config.getMinModelCount(), config.getMinUsageCount(),
                    config.getStatWindowMonths(), config.getProcessTemplateOid());
        }
        return mapper.selectByTenant(tenant);
    }

    private static int requireThreshold(Integer value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (value < MIN_THRESHOLD) {
            throw new IllegalArgumentException(label + "至少为 " + MIN_THRESHOLD);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
