/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.FileStorageConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 文件存储配置 Mapper —— 支持多租户隔离。
 */
@Mapper
public interface FileStorageConfigMapper {

    @Insert("INSERT INTO ck_file_storage_config (oid, category_code, category_name, storage_path, storage_type, " +
            "max_file_size_mb, max_capacity_mb, alert_threshold_percent, enabled, sort_order, description, " +
            "endpoint, access_key, secret_key, bucket_name, base_url, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{categoryCode}, #{categoryName}, #{storagePath}, #{storageType}, " +
            "#{maxFileSizeMb}, #{maxCapacityMb}, #{alertThresholdPercent}, #{enabled}, #{sortOrder}, #{description}, " +
            "#{endpoint}, #{accessKey}, #{secretKey}, #{bucketName}, #{baseUrl}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    void insert(FileStorageConfig config);

    @Update("UPDATE ck_file_storage_config SET category_code=#{categoryCode}, category_name=#{categoryName}, " +
            "storage_path=#{storagePath}, storage_type=#{storageType}, max_file_size_mb=#{maxFileSizeMb}, " +
            "max_capacity_mb=#{maxCapacityMb}, alert_threshold_percent=#{alertThresholdPercent}, " +
            "enabled=#{enabled}, sort_order=#{sortOrder}, description=#{description}, " +
            "endpoint=#{endpoint}, access_key=#{accessKey}, secret_key=#{secretKey}, " +
            "bucket_name=#{bucketName}, base_url=#{baseUrl}, tenant_oid=#{tenantOid}, " +
            "updater=#{updater}, updated_at=#{updatedAt} WHERE oid=#{oid}")
    void update(FileStorageConfig config);

    @Delete("DELETE FROM ck_file_storage_config WHERE oid=#{oid}")
    void deleteByOid(String oid);

    @Select("SELECT * FROM ck_file_storage_config WHERE oid=#{oid}")
    @Results(id = "fscMap", value = {
        @Result(property = "categoryCode", column = "category_code"),
        @Result(property = "categoryName", column = "category_name"),
        @Result(property = "storagePath", column = "storage_path"),
        @Result(property = "storageType", column = "storage_type"),
        @Result(property = "maxFileSizeMb", column = "max_file_size_mb"),
        @Result(property = "maxCapacityMb", column = "max_capacity_mb"),
        @Result(property = "alertThresholdPercent", column = "alert_threshold_percent"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "endpoint", column = "endpoint"),
        @Result(property = "accessKey", column = "access_key"),
        @Result(property = "secretKey", column = "secret_key"),
        @Result(property = "bucketName", column = "bucket_name"),
        @Result(property = "baseUrl", column = "base_url"),
        @Result(property = "tenantOid", column = "tenant_oid"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
    })
    FileStorageConfig selectByOid(String oid);

    /** 查询所有配置（平台管理员使用） */
    @Select("SELECT * FROM ck_file_storage_config ORDER BY sort_order, created_at")
    @ResultMap("fscMap")
    List<FileStorageConfig> selectAll();

    /** 按租户查询存储配置 */
    @Select("SELECT * FROM ck_file_storage_config WHERE tenant_oid = #{tenantOid} ORDER BY sort_order, created_at")
    @ResultMap("fscMap")
    List<FileStorageConfig> selectByTenant(@Param("tenantOid") String tenantOid);

    // ==================== 统计查询（按租户过滤） ====================

    /** 图册文件数 */
    @Select("SELECT COUNT(*) FROM ck_media WHERE file_size > 0 AND tenant_oid = #{tenantOid}")
    int countGallery(@Param("tenantOid") String tenantOid);
    @Select("SELECT COALESCE(SUM(file_size), 0) FROM ck_media WHERE file_size > 0 AND tenant_oid = #{tenantOid}")
    long sumGallerySize(@Param("tenantOid") String tenantOid);

    /** 主文档/数模文件数 */
    @Select("SELECT COUNT(*) FROM ck_file WHERE file_size > 0 AND tenant_oid = #{tenantOid}")
    int countMainDoc(@Param("tenantOid") String tenantOid);
    @Select("SELECT COALESCE(SUM(file_size), 0) FROM ck_file WHERE file_size > 0 AND tenant_oid = #{tenantOid}")
    long sumMainDocSize(@Param("tenantOid") String tenantOid);

    /** 附件文件数 */
    @Select("SELECT COUNT(*) FROM ck_attachment WHERE file_size > 0 AND tenant_oid = #{tenantOid}")
    int countAttachment(@Param("tenantOid") String tenantOid);
    @Select("SELECT COALESCE(SUM(file_size), 0) FROM ck_attachment WHERE file_size > 0 AND tenant_oid = #{tenantOid}")
    long sumAttachmentSize(@Param("tenantOid") String tenantOid);
}
