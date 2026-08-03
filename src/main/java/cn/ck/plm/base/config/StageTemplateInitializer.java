/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.service.api.StageTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动时初始化平台级研发阶段模板。
 *
 * <p>6 个默认阶段：市场验证、需求论证、方案设计、详细设计、工艺规划、试产。
 */
@Component
@Order(5)
public class StageTemplateInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StageTemplateInitializer.class);

    private final StageTemplateService service;

    public StageTemplateInitializer(StageTemplateService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        log.info("开始初始化平台级研发阶段模板...");
        int count = service.initPlatformDefaults();
        log.info("平台级研发阶段模板初始化完成: {} 个", count);
    }
}
