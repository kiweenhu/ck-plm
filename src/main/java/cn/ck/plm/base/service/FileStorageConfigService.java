/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.service;

import cn.ck.plm.base.dto.FileStorageSummaryVO;
import cn.ck.plm.base.entity.FileStorageConfig;
import cn.ck.plm.base.mapper.FileStorageConfigMapper;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.*;

/**
 * 文件存储配置服务 —— 配置管理 + 分类统计 + 空间告警。
 * <p>租户隔离：租户管理员只能看到和管理本租户的存储配置；
 * 平台管理员可管理所有租户的配置。
 */
@Service
public class FileStorageConfigService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfigService.class);

    private final FileStorageConfigMapper mapper;
    private final FileStorageService fileStorage;

    public FileStorageConfigService(FileStorageConfigMapper mapper, FileStorageService fileStorage) {
        this.mapper = mapper;
        this.fileStorage = fileStorage;
    }

    /** 根据当前用户身份返回配置列表（运行时填充 storagePath） */
    public List<FileStorageConfig> listAll() {
        List<FileStorageConfig> list = TenantContext.isCurrentPlatform()
                ? mapper.selectAll() : mapper.selectByTenant(TenantContext.get());
        fillRuntimePath(list);
        return list;
    }

    public FileStorageConfig getByOid(String oid) {
        FileStorageConfig cfg = mapper.selectByOid(oid);
        if (cfg != null) {
            cfg.setStoragePath(fileStorage.getCategoryPathString(
                    cfg.getTenantOid() != null ? cfg.getTenantOid() : TenantContext.get(),
                    cfg.getCategoryCode()));
        }
        return cfg;
    }

    /** 为配置列表运行时填充 storagePath */
    private void fillRuntimePath(List<FileStorageConfig> list) {
        for (FileStorageConfig cfg : list) {
            String tenantOid = cfg.getTenantOid() != null ? cfg.getTenantOid() : TenantContext.get();
            cfg.setStoragePath(fileStorage.getCategoryPathString(tenantOid, cfg.getCategoryCode()));
        }
    }

    @Transactional
    public FileStorageConfig create(FileStorageConfig config) {
        if (config.getTenantOid() == null) {
            config.setTenantOid(TenantContext.get());
        }
        config.setOid(UUID.randomUUID().toString());
        mapper.insert(config);
        return config;
    }

    @Transactional
    public FileStorageConfig update(FileStorageConfig config) {
        mapper.update(config);
        return config;
    }

    @Transactional
    public void delete(String oid) {
        mapper.deleteByOid(oid);
    }

    /** 获取各分类存储统计（按当前租户隔离） */
    public List<FileStorageSummaryVO> getSummary() {
        String tenantOid = TenantContext.get();
        List<FileStorageSummaryVO> list = new ArrayList<>();
        List<FileStorageConfig> configs = TenantContext.isCurrentPlatform()
                ? mapper.selectAll() : mapper.selectByTenant(tenantOid);

        // 按租户过滤统计
        long galleryBytes = mapper.sumGallerySize(tenantOid);
        int galleryCount = mapper.countGallery(tenantOid);
        long fileBytes = mapper.sumMainDocSize(tenantOid);
        int fileCount = mapper.countMainDoc(tenantOid);
        long attBytes = mapper.sumAttachmentSize(tenantOid);
        int attCount = mapper.countAttachment(tenantOid);

        for (FileStorageConfig cfg : configs) {
            FileStorageSummaryVO vo = new FileStorageSummaryVO();
            vo.setCategoryCode(cfg.getCategoryCode());
            vo.setCategoryName(cfg.getCategoryName());
            vo.setStorageType(cfg.getStorageType());

            // 运行时动态生成路径（兼容 Windows/Linux）
            String ownerTenantOid = cfg.getTenantOid() != null ? cfg.getTenantOid() : tenantOid;
            String runtimePath = fileStorage.getCategoryPathString(ownerTenantOid, cfg.getCategoryCode());
            vo.setStoragePath(runtimePath);

            String sourceTable = resolveSourceTable(cfg.getCategoryCode());
            switch (sourceTable) {
                case "ck_media":
                    vo.setFileCount(galleryCount);
                    vo.setTotalSizeBytes(galleryBytes);
                    break;
                case "ck_file":
                    vo.setFileCount(fileCount);
                    vo.setTotalSizeBytes(fileBytes);
                    break;
                case "ck_attachment":
                    vo.setFileCount(attCount);
                    vo.setTotalSizeBytes(attBytes);
                    break;
                default:
                    vo.setFileCount(0);
                    vo.setTotalSizeBytes(0L);
            }

            // LOCAL/NAS 存储：磁盘扫描覆盖 DB 统计（使用运行时路径）
            if ("LOCAL".equals(cfg.getStorageType()) || "NAS".equals(cfg.getStorageType())) {
                int diskCount = countDiskFiles(runtimePath);
                if (diskCount > 0) {
                    vo.setFileCount(diskCount);
                    long diskSize = scanDiskSize(runtimePath);
                    if (diskSize > 0) {
                        vo.setTotalSizeBytes(diskSize);
                        vo.setTotalSizeDisplay(formatSize(diskSize));
                    }
                }
            }

            vo.setTotalSizeDisplay(formatSize(vo.getTotalSizeBytes()));

            // 可用空间
            if ("LOCAL".equals(cfg.getStorageType()) || "NAS".equals(cfg.getStorageType())) {
                vo.setFreeBytes(queryDiskFree(runtimePath));
                vo.setFreeDisplay(formatSize(vo.getFreeBytes()));
                if (cfg.getMaxCapacityMb() != null && cfg.getMaxCapacityMb() > 0) {
                    vo.setTotalCapacityBytes(cfg.getMaxCapacityMb() * 1024L * 1024);
                    vo.setTotalCapacityDisplay(formatSize(vo.getTotalCapacityBytes()));
                    long used = (vo.getTotalSizeBytes() != null) ? vo.getTotalSizeBytes() : 0;
                    vo.setUsagePercent((int) Math.round(used * 100.0 / vo.getTotalCapacityBytes()));
                }
            } else {
                vo.setFreeBytes(0L);
                vo.setFreeDisplay("—");
            }

            list.add(vo);
        }

        // 总计
        long totalBytes = 0;
        int totalCount = 0;
        for (FileStorageSummaryVO vo : list) {
            totalCount += vo.getFileCount() != null ? vo.getFileCount() : 0;
            totalBytes += vo.getTotalSizeBytes() != null ? vo.getTotalSizeBytes() : 0;
        }

        FileStorageSummaryVO total = new FileStorageSummaryVO();
        total.setCategoryCode("TOTAL");
        total.setCategoryName("总计");
        total.setFileCount(totalCount);
        total.setTotalSizeBytes(totalBytes);
        total.setTotalSizeDisplay(formatSize(totalBytes));

        long totalFree = 0;
        for (FileStorageConfig cfg : configs) {
            if ("LOCAL".equals(cfg.getStorageType()) || "NAS".equals(cfg.getStorageType())) {
                totalFree += queryDiskFree(cfg.getStoragePath());
            }
        }
        total.setFreeBytes(totalFree);
        total.setFreeDisplay(formatSize(totalFree));
        list.add(total);

        return list;
    }

    private long queryDiskFree(String path) {
        try {
            File dir = new File(path);
            if (dir.exists()) return dir.getFreeSpace();
            File parent = dir;
            while (parent != null && !parent.exists()) parent = parent.getParentFile();
            return parent != null ? parent.getFreeSpace() : 0L;
        } catch (Exception e) { return 0L; }
    }

    private int countDiskFiles(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) return 0;
            int count = 0;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) count++;
                    else if (f.isDirectory()) count += countDiskFiles(f.getAbsolutePath());
                }
            }
            return count;
        } catch (Exception e) { return 0; }
    }

    private long scanDiskSize(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) return 0;
            long size = 0;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) size += f.length();
                    else if (f.isDirectory()) size += scanDiskSize(f.getAbsolutePath());
                }
            }
            return size;
        } catch (Exception e) { return 0; }
    }

    private String resolveSourceTable(String categoryCode) {
        switch (categoryCode) {
            case "GALLERY":    return "ck_media";
            case "MAIN_DOC":
            case "CAD_MODEL":  return "ck_file";
            case "ATTACHMENT": return "ck_attachment";
            default:           return null;
        }
    }

    /** 检查存储告警（按租户过滤） */
    public List<String> checkStorageAlerts() {
        String currentTenantOid = TenantContext.get();
        List<String> alerts = new ArrayList<>();
        List<FileStorageConfig> configs = TenantContext.isCurrentPlatform()
                ? mapper.selectAll() : mapper.selectByTenant(currentTenantOid);
        for (FileStorageConfig cfg : configs) {
            if (cfg.getEnabled() == null || !cfg.getEnabled()) continue;
            if (cfg.getMaxCapacityMb() == null || cfg.getMaxCapacityMb() <= 0) continue;
            if (cfg.getAlertThresholdPercent() == null || cfg.getAlertThresholdPercent() <= 0) continue;

            String ownerTenantOid = cfg.getTenantOid() != null ? cfg.getTenantOid() : currentTenantOid;
            String runtimePath = fileStorage.getCategoryPathString(ownerTenantOid, cfg.getCategoryCode());
            long free = queryDiskFree(runtimePath);
            long capacity = cfg.getMaxCapacityMb() * 1024L * 1024;
            int usagePercent = (int) ((capacity - free) * 100 / capacity);
            int threshold = cfg.getAlertThresholdPercent();

            if (usagePercent >= threshold) {
                String msg = String.format("[%s] 存储利用率已达 %d%%（阈值 %d%%），可用: %s / 总容量: %s",
                        cfg.getCategoryName(), usagePercent, threshold,
                        formatSize(free), formatSize(capacity));
                alerts.add(msg);
                log.warn("存储告警: {}", msg);
            }
        }
        return alerts;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
