/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.mapper.ViewMapper;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 数据库初始化：确保默认视图（Design / Manufacturing / Service）存在。
 *
 * <p>Part 等版本控制实体在创建迭代时需要默认视图（Design），否则 view 字段为空。
 * 幂等操作，多次启动安全。
 */
@Component
@Order(5)
public class ViewInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ViewInitializer.class);

    private final ViewMapper viewMapper;

    public ViewInitializer(ViewMapper viewMapper) {
        this.viewMapper = viewMapper;
    }

    @Override
    public void run(String... args) {
        ensureView("Design", "设计视图", "展示最新工作中的迭代", 1);
        ensureView("Manufacturing", "制造视图", "展示已发布的制造数据", 2);
        ensureView("Service", "服务视图", "展示售后服务数据", 3);
    }

    private void ensureView(String code, String name, String description, int sortOrder) {
        try {
            if (viewMapper.existsByCode(code) > 0) {
                log.debug("默认视图 {} 已存在，跳过", code);
                return;
            }
            View view = new View();
            view.setOid(UUID.randomUUID().toString());
            view.setCode(code);
            view.setName(name);
            view.setDescription(description);
            view.setSortOrder(sortOrder);
            view.setEnabled(true);
            view.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
            view.setCreatedAt(LocalDateTime.now());
            view.setUpdatedAt(LocalDateTime.now());
            viewMapper.insert(view);
            log.info("默认视图已创建: {} ({})", code, name);
        } catch (Exception e) {
            log.error("创建默认视图 {} 失败: {}", code, e.getMessage(), e);
        }
    }
}
