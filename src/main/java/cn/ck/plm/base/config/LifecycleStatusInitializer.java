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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public LifecycleStatusInitializer(LifecycleStatusMapper mapper, JdbcTemplate jdbcTemplate) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("开始初始化标准生命周期状态...");
        // DRAFT 必须存在：内置模板 STANDARD / SIMPLE 的 initial_state_code 均为 DRAFT。
        // 该状态此前缺失，导致 initLifecycle 查不到真实对象而静默跳过，新建对象 status 恒为 NULL。
        initStatus("DRAFT", "草稿");
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
        // 回填历史迭代中 status 为 NULL 的记录（两个内置模板的初始状态均为 DRAFT）
        fixNullIterationStatus();
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

    /**
     * 回填各实体迭代表中 {@code status} 为 NULL 的历史记录。
     *
     * <p>成因链：
     * <ol>
     *   <li>{@code ck_lifecycle_status} 字典缺少 {@code DRAFT}；</li>
     *   <li>但内置模板 {@code STANDARD} / {@code SIMPLE} 的 {@code initial_state_code} 均为 {@code DRAFT}；</li>
     *   <li>{@code DefaultLifecycleTemplateService.initLifecycle} 因查不到该状态而静默跳过，
     *       导致新建对象的 {@code status} 列为 NULL（列表状态列显示空白）。</li>
     * </ol>
     *
     * <p>字典已在 {@link #run} 中补全 DRAFT，此处回填历史 NULL 数据为 DRAFT
     * （两模板的初始状态）。注意 {@code status} 列经
     * {@code LifecycleStatusTypeHandler} 存储的是 <b>code</b>（VARCHAR），不是 oid。
     */
    private void fixNullIterationStatus() {
        backfillNullStatus("ck_part_iteration");
        backfillNullStatus("ck_document_iteration");
        backfillNullStatus("ck_eng_document_iteration");
        backfillNullStatus("ck_functional_iteration");
    }

    /** 将指定迭代表中 status 为 NULL/空串的行回填为初始状态 DRAFT（表不存在时仅告警） */
    private void backfillNullStatus(String table) {
        try {
            int fixed = jdbcTemplate.update(
                "UPDATE " + table + " SET status = 'DRAFT' WHERE status IS NULL OR status = ''"
            );
            if (fixed > 0) {
                log.info("已为 {} 回填 {} 条迭代的初始状态（DRAFT）", table, fixed);
            }
        } catch (Exception e) {
            log.warn("回填 {} 的迭代 status 失败: {}", table, e.getMessage());
        }
    }
}
