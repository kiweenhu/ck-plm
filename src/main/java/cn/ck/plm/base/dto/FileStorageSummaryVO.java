/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.dto;

/**
 * 文件存储汇总 —— 每个类别的文件数量、占用空间、可用空间、使用率。
 */
public class FileStorageSummaryVO {

    private String categoryCode;
    private String categoryName;
    private Integer fileCount;
    private Long totalSizeBytes;
    private String totalSizeDisplay;
    private String storagePath;
    private String storageType;
    private Long freeBytes;
    private String freeDisplay;
    private Long totalCapacityBytes;
    private String totalCapacityDisplay;
    private Integer usagePercent;

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public Long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(Long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
    public String getTotalSizeDisplay() { return totalSizeDisplay; }
    public void setTotalSizeDisplay(String totalSizeDisplay) { this.totalSizeDisplay = totalSizeDisplay; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public Long getFreeBytes() { return freeBytes; }
    public void setFreeBytes(Long freeBytes) { this.freeBytes = freeBytes; }
    public String getFreeDisplay() { return freeDisplay; }
    public void setFreeDisplay(String freeDisplay) { this.freeDisplay = freeDisplay; }
    public Long getTotalCapacityBytes() { return totalCapacityBytes; }
    public void setTotalCapacityBytes(Long totalCapacityBytes) { this.totalCapacityBytes = totalCapacityBytes; }
    public String getTotalCapacityDisplay() { return totalCapacityDisplay; }
    public void setTotalCapacityDisplay(String totalCapacityDisplay) { this.totalCapacityDisplay = totalCapacityDisplay; }
    public Integer getUsagePercent() { return usagePercent; }
    public void setUsagePercent(Integer usagePercent) { this.usagePercent = usagePercent; }
}
