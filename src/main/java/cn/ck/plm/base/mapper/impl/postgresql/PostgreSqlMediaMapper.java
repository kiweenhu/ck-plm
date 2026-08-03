/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.Media;
import cn.ck.plm.base.mapper.MediaMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link MediaMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlMediaMapper extends MediaMapper {

    @Override
    @Insert("INSERT INTO ck_media (oid, original_name, file_name, file_size, mime_type, "
            + "storage_path, description, width, height, tenant_oid, "
            + "creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{originalName}, #{fileName}, #{fileSize}, #{mimeType}, "
            + "#{storagePath}, #{description}, #{width}, #{height}, #{tenantOid}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Media media);

    @Override
    @Update("UPDATE ck_media SET original_name = #{originalName}, description = #{description}, "
            + "updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(Media media);

    @Override
    @Delete("DELETE FROM ck_media WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    @Select("SELECT oid, original_name, file_name, file_size, mime_type, "
            + "storage_path, description, width, height, tenant_oid, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_media WHERE oid = #{oid}")
    @Results(id = "mediaResult", value = {
            @Result(property = "oid",          column = "oid"),
            @Result(property = "originalName", column = "original_name"),
            @Result(property = "fileName",     column = "file_name"),
            @Result(property = "fileSize",     column = "file_size"),
            @Result(property = "mimeType",     column = "mime_type"),
            @Result(property = "storagePath",  column = "storage_path"),
            @Result(property = "description",  column = "description"),
            @Result(property = "width",        column = "width"),
            @Result(property = "height",       column = "height"),
            @Result(property = "tenantOid",    column = "tenant_oid"),
            @Result(property = "creator",      column = "creator"),
            @Result(property = "createdAt",    column = "created_at"),
            @Result(property = "updater",      column = "updater"),
            @Result(property = "updatedAt",    column = "updated_at")
    })
    @Override
    Media selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, original_name, file_name, file_size, mime_type, "
            + "storage_path, description, width, height, tenant_oid, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_media ORDER BY created_at DESC")
    @ResultMap("mediaResult")
    @Override
    List<Media> selectAll();

    @Select("SELECT oid, original_name, file_name, file_size, mime_type, "
            + "storage_path, description, width, height, tenant_oid, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_media "
            + "WHERE LOWER(original_name) LIKE LOWER('%' || #{keyword} || '%') "
            + "OR LOWER(description) LIKE LOWER('%' || #{keyword} || '%') "
            + "ORDER BY created_at DESC")
    @ResultMap("mediaResult")
    @Override
    List<Media> search(@Param("keyword") String keyword);

    @Override
    @Select("<script>"
            + "SELECT m.oid FROM ck_media m "
            + "WHERE m.oid IN "
            + "<foreach collection='oids' item='oid' open='(' separator=',' close=')'>"
            + "#{oid}"
            + "</foreach>"
            + " AND EXISTS ("
            + "  SELECT 1 FROM ck_product_line pl WHERE pl.thumbnail = m.storage_path"
            + ")"
            + "</script>")
    List<String> findUsedOids(@Param("oids") List<String> oids);
}
