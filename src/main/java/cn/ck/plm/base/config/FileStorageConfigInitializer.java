/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.FileStorageConfig;
import cn.ck.plm.base.mapper.FileStorageConfigMapper;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 文件存储配置初始化 —— 为每个租户初始化默认存储分类。
 *
 * <p>数据库仅存储分类元数据（编码、名称、类型、容量限制等），
 * 存储路径由 {@link cn.ck.plm.base.service.FileStorageService} 运行时根据
 * plm.storage.base-path + tenantOid + category 动态拼接，兼容 Windows/Linux。
 */
@Component
public class FileStorageConfigInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfigInitializer.class);
    private static final String PLATFORM_OID = TenantContext.PLATFORM_TENANT_OID;

    private final FileStorageConfigMapper mapper;
    private final JdbcTemplate jdbc;

    public FileStorageConfigInitializer(FileStorageConfigMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 1. 确保平台级存储配置存在（作为模板参考）
        List<FileStorageConfig> platformConfigs = mapper.selectByTenant(PLATFORM_OID);
        if (platformConfigs.isEmpty()) {
            insertDefaults(PLATFORM_OID);
            log.info("平台级文件存储配置初始化完成");
        }

        // 2. 为已有租户初始化配置（若不存在）
        try {
            List<String> tenantOids = jdbc.queryForList(
                    "SELECT oid FROM ck_tenant WHERE oid != ?", String.class, PLATFORM_OID);
            for (String tenantOid : tenantOids) {
                List<FileStorageConfig> tenantConfigs = mapper.selectByTenant(tenantOid);
                if (tenantConfigs.isEmpty()) {
                    insertDefaults(tenantOid);
                    log.info("租户 {} 文件存储配置初始化完成", tenantOid);
                }
            }
        } catch (Exception e) {
            log.warn("为已有租户初始化存储配置失败: {}", e.getMessage());
        }
    }

    private void insertDefaults(String tenantOid) {
        // 路径不存入数据库，由 FileStorageService 运行时动态拼接
        insert(tenantOid, "GALLERY",    "图册",   "LOCAL", 50,   1, "产品图册、效果图等图片文件");
        insert(tenantOid, "MAIN_DOC",   "主文档", "LOCAL", 500,  2, "产品主文档文件（Word、PDF等）");
        insert(tenantOid, "CAD_MODEL",  "数模",   "LOCAL", 1000, 3, "CAD三维数模文件（STEP/IGES等）");
        insert(tenantOid, "ATTACHMENT", "附件",   "LOCAL", 100,  4, "产品相关附件文件");
    }

    private void insert(String tenantOid, String code, String name,
                         String type, int maxMb, int order, String desc) {
        FileStorageConfig cfg = new FileStorageConfig();
        cfg.setOid(UUID.randomUUID().toString());
        cfg.setCategoryCode(code);
        cfg.setCategoryName(name);
        cfg.setStorageType(type);
        cfg.setMaxFileSizeMb(maxMb);
        cfg.setSortOrder(order);
        cfg.setEnabled(true);
        cfg.setDescription(desc);
        cfg.setTenantOid(tenantOid);
        mapper.insert(cfg);
    }
}
