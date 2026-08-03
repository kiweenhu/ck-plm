/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.mapper.LifecycleStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动时初始化预置的 5 个标准生命周期状态到数据库。
 * 仅在数据不存在时插入，已存在的状态不会覆盖。
 * 同时使用数据库 now() 函数修复历史数据中 created_at / updated_at 为 NULL 的记录。
 */
@Component
public class LifecycleStatusInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LifecycleStatusInitializer.class);

    private final LifecycleStatusMapper mapper;

    public LifecycleStatusInitializer(LifecycleStatusMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) {
        log.info("开始初始化标准生命周期状态...");
        initStatus("WORKING", "工作中");
        initStatus("APPROVING", "审核中");
        initStatus("PUBLISHED", "已发布");
        initStatus("OFFLINE", "已下线");
        initStatus("ARCHIVED", "已归档");
        log.info("标准生命周期状态初始化完成");

        // 修复存量数据：name 和 display_name 为空时用已知值填充
        fixMissingDisplayData();
        // 修复历史数据中 created_at / updated_at 为 NULL 的记录
        fixNullTimestamps();
    }

    private void initStatus(String code, String name) {
        if (mapper.existsByCode(code) == 0) {
            LifecycleStatus status = new LifecycleStatus(code, name);
            status.setDisplayName(name);
            LocalDateTime now = LocalDateTime.now();
            status.setCreatedAt(now);
            status.setUpdatedAt(now);
            mapper.insert(status);
            log.info("  新增状态: {} ({})", code, name);
        } else {
            log.debug("  状态已存在: {}", code);
        }
    }

    /**
     * 使用原生 SQL 修复存量数据中 name / display_name 为 NULL 的记录，
     * 避免通过实体对象 mapper.update() 触发 MyBatis LocalDateTime 类型转换问题。
     */
    private void fixMissingDisplayData() {
        int fixed = mapper.fixMissingDisplayName();
        if (fixed > 0) {
            log.info("已修复 {} 条生命周期状态的 name/display_name 字段", fixed);
        }
    }

    /**
     * 使用原生 SQL 修复历史数据中审计时间字段为 NULL 的记录。
     */
    private void fixNullTimestamps() {
        int fixed = mapper.fixAllNullTimestamps();
        if (fixed > 0) {
            log.info("已修复 {} 条历史生命周期状态的审计时间字段", fixed);
        }
    }
}
