/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.change.entity.ChangeConfig;
import cn.ck.plm.change.mapper.ChangeConfigMapper;
import cn.ck.plm.change.service.api.ChangeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 变更租户配置服务实现。
 *
 * <h3>为什么读不落库</h3>
 * <p>每个租户第一次打开变更模块时不该产生一条写操作：
 * 默认值本身就是策略（必须审批、禁止免审改已发布对象），把默认值写进库只是重复它。
 * 只有管理员真正改过配置才落行。
 */
@Service
public class ChangeConfigServiceImpl implements ChangeConfigService {

    private static final Logger log = LoggerFactory.getLogger(ChangeConfigServiceImpl.class);

    /** 内置变更请求流程 key（随 platform-base 的 processes/ 目录安装） */
    private static final String DEFAULT_REQUEST_KEY = "change-request";
    /** 内置变更通告流程 key */
    private static final String DEFAULT_NOTICE_KEY = "change-notice";
    /** 内置变更任务流程 key */
    private static final String DEFAULT_TASK_KEY = "change-task";

    private final ChangeConfigMapper mapper;

    public ChangeConfigServiceImpl(ChangeConfigMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }

    @Override
    public ChangeConfig get() {
        ChangeConfig config = mapper.selectByTenant(tenantOid());
        if (config != null) return config;
        ChangeConfig defaults = new ChangeConfig();
        defaults.setTenantOid(tenantOid());
        return defaults;
    }

    @Override
    @Transactional
    public ChangeConfig update(Boolean approvalRequired, Boolean bypassForReleased,
                               String requestProcessKey, String approvalProcessKey, String taskProcessKey,
                               Boolean requestEnabled) {
        String tenant = tenantOid();
        ChangeConfig existing = mapper.selectByTenant(tenant);

        ChangeConfig target = existing != null ? existing : new ChangeConfig();
        target.setTenantOid(tenant);
        if (approvalRequired != null) target.setApprovalRequired(approvalRequired);
        if (bypassForReleased != null) target.setBypassForReleased(bypassForReleased);
        if (requestProcessKey != null) {
            target.setRequestProcessKey(normalizeKey(requestProcessKey, DEFAULT_REQUEST_KEY));
        }
        if (approvalProcessKey != null) {
            target.setApprovalProcessKey(normalizeKey(approvalProcessKey, DEFAULT_NOTICE_KEY));
        }
        if (taskProcessKey != null) {
            target.setTaskProcessKey(normalizeKey(taskProcessKey, DEFAULT_TASK_KEY));
        }
        if (requestEnabled != null) {
            target.setRequestEnabled(requestEnabled);
        }
        target.setUpdater(UserContext.get());
        target.setUpdatedAt(LocalDateTime.now());

        if (existing == null) {
            mapper.insert(target);
        } else {
            mapper.update(target);
        }

        if (!target.isApprovalRequired()) {
            log.warn("租户 {} 已关闭变更审批（免审）：{}", tenant, target.isBypassForReleased()
                    ? "允许修订已发布对象" : "仍禁止修订已发布对象");
        }
        return target;
    }

    /**
     * 流程 key 归一：去空白；空串回落到该单据的默认内置流程。
     *
     * <p>空串当作"改回默认"而不是"允许为空"：配置页把输入框清空后保存，
     * 意图通常是恢复内置流程，而不是让「发起审批」在运行期抛"未配置 key"。
     */
    private static String normalizeKey(String raw, String fallback) {
        String key = raw == null ? "" : raw.trim();
        return key.isEmpty() ? fallback : key;
    }
}
