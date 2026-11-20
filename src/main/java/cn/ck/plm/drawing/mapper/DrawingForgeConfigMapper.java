/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.mapper;

import cn.ck.plm.drawing.entity.DrawingForgeConfig;
import org.apache.ibatis.annotations.*;

/**
 * 二维图纸转换器对接配置（单行表）的读写。
 *
 * <p>没有租户条件：转换器是整个部署共享的基础设施。本表已加入
 * {@code TenantStatementInterceptor} 的完全共享名单。
 */
@Mapper
public interface DrawingForgeConfigMapper {

    @Select("SELECT * FROM ck_drawing_forge_config LIMIT 1")
    @Results(id = "drawingForgeConfigMap", value = {
            @Result(property = "oid", column = "oid", id = true),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "baseUrl", column = "base_url"),
            @Result(property = "callbackToken", column = "callback_token"),
            @Result(property = "callbackUrl", column = "callback_url"),
            @Result(property = "engine", column = "engine"),
            @Result(property = "connectTimeoutMs", column = "connect_timeout_ms"),
            @Result(property = "readTimeoutMs", column = "read_timeout_ms"),
            @Result(property = "artifactReadTimeoutMs", column = "artifact_read_timeout_ms"),
            @Result(property = "maxSourceSizeMb", column = "max_source_size_mb"),
            @Result(property = "supportedExtensions", column = "supported_extensions"),
            @Result(property = "sourceUrlTtlMinutes", column = "source_url_ttl_minutes"),
            @Result(property = "maxAttempts", column = "max_attempts"),
            @Result(property = "watchdogEnabled", column = "watchdog_enabled"),
            @Result(property = "staleAfterMinutes", column = "stale_after_minutes"),
            @Result(property = "watchdogBatchSize", column = "watchdog_batch_size"),
            @Result(property = "watchdogIntervalMs", column = "watchdog_interval_ms"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    DrawingForgeConfig selectOne();

    @Insert("INSERT INTO ck_drawing_forge_config (oid, enabled, base_url, callback_token, callback_url, " +
            "engine, connect_timeout_ms, read_timeout_ms, artifact_read_timeout_ms, max_source_size_mb, " +
            "supported_extensions, source_url_ttl_minutes, max_attempts, watchdog_enabled, " +
            "stale_after_minutes, watchdog_batch_size, watchdog_interval_ms, " +
            "creator, created_at, updater, updated_at) VALUES (" +
            "#{oid}, #{enabled}, #{baseUrl}, #{callbackToken}, #{callbackUrl}, #{engine}, " +
            "#{connectTimeoutMs}, #{readTimeoutMs}, #{artifactReadTimeoutMs}, #{maxSourceSizeMb}, " +
            "#{supportedExtensions}, #{sourceUrlTtlMinutes}, #{maxAttempts}, #{watchdogEnabled}, " +
            "#{staleAfterMinutes}, #{watchdogBatchSize}, #{watchdogIntervalMs}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    void insert(DrawingForgeConfig config);

    @Update("UPDATE ck_drawing_forge_config SET enabled = #{enabled}, base_url = #{baseUrl}, " +
            "callback_token = #{callbackToken}, callback_url = #{callbackUrl}, engine = #{engine}, " +
            "connect_timeout_ms = #{connectTimeoutMs}, read_timeout_ms = #{readTimeoutMs}, " +
            "artifact_read_timeout_ms = #{artifactReadTimeoutMs}, max_source_size_mb = #{maxSourceSizeMb}, " +
            "supported_extensions = #{supportedExtensions}, source_url_ttl_minutes = #{sourceUrlTtlMinutes}, " +
            "max_attempts = #{maxAttempts}, watchdog_enabled = #{watchdogEnabled}, " +
            "stale_after_minutes = #{staleAfterMinutes}, watchdog_batch_size = #{watchdogBatchSize}, " +
            "watchdog_interval_ms = #{watchdogIntervalMs}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    void update(DrawingForgeConfig config);
}
