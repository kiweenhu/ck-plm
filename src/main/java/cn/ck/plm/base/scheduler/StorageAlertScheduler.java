/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.scheduler;

import cn.ck.plm.base.service.FileStorageConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存储空间定时检查 —— 每 30 分钟检测一次，低于阈值时写入系统日志。
 *
 * <p>告警信息记录到 ERROR 级别日志，可接入 ELK/Prometheus 等监控系统。
 * 后续可扩展为入库通知表 ck_notification，由 sysadmin 在个人中心查看。
 */
@Component
public class StorageAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(StorageAlertScheduler.class);

    private final FileStorageConfigService storageService;

    public StorageAlertScheduler(FileStorageConfigService storageService) {
        this.storageService = storageService;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)  // 30 分钟
    public void checkStorage() {
        try {
            List<String> alerts = storageService.checkStorageAlerts();
            if (!alerts.isEmpty()) {
                log.error("===== 存储空间告警 ({}) =====", alerts.size());
                for (String alert : alerts) {
                    log.error(alert);
                }
                log.error("===== 存储空间告警结束 =====");
            }
        } catch (Exception e) {
            log.error("存储检查异常", e);
        }
    }
}
