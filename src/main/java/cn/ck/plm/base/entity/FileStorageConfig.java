/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.entity;

/**
 * 文件存储配置 —— 定义不同类别文件的分布式存储策略。
 *
 * <p>支持 LOCAL / NAS / S3(MinIO) 存储类型，通过 storageType 区分。
 *
 * <h3>多租户</h3>
 * 实现 {@link TenantEntity} 接口，每个租户可独立配置存储策略。
 * 平台管理员可为所有租户设置统一的存储配置，租户管理员只能管理本租户的配置。
 */
public class FileStorageConfig extends BaseEntity implements TenantEntity {

    /** 类别编码（如 MAIN_DOC、GALLERY、CAD_MODEL、ATTACHMENT） */
    private String categoryCode;

    /** 类别名称 */
    private String categoryName;

    /** 存储路径（分布式路径，如 /data/plm/documents 或 bucket-name） */
    private String storagePath;

    /** 存储类型（LOCAL / NAS / S3） */
    private String storageType;

    /** 单文件最大大小（MB） */
    private Integer maxFileSizeMb;

    /** 总容量上限（MB）—— 用于计算使用率 */
    private Integer maxCapacityMb;

    /** 告警阈值（百分比，如 80 表示使用率达 80% 时告警） */
    private Integer alertThresholdPercent;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序 */
    private Integer sortOrder;

    /** 描述 */
    private String description;

    // ===== S3/MinIO 连接配置 =====

    /** MinIO/S3 服务端点（如 http://192.168.1.100:9000） */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥（加密存储，前端不展示原始值） */
    private String secretKey;

    /** 桶名称（若未填则使用 storagePath） */
    private String bucketName;

    /** 文件访问基础 URL（用于前端预览，如 http://minio.example.com） */
    private String baseUrl;

    /** 租户 oid（多租户隔离，平台级为全零UUID） */
    private String tenantOid;

    // ==================== 构造 ====================
    public FileStorageConfig() { }

    // ==================== Getter/Setter ====================
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public Integer getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(Integer maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    public Integer getMaxCapacityMb() { return maxCapacityMb; }
    public void setMaxCapacityMb(Integer maxCapacityMb) { this.maxCapacityMb = maxCapacityMb; }
    public Integer getAlertThresholdPercent() { return alertThresholdPercent; }
    public void setAlertThresholdPercent(Integer alertThresholdPercent) { this.alertThresholdPercent = alertThresholdPercent; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /** 获取有效的桶名称（优先 bucketName，回退到 storagePath） */
    public String getEffectiveBucket() {
        return (bucketName != null && !bucketName.isEmpty()) ? bucketName : storagePath;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
