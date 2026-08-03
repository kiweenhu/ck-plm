/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.mapper.CKFileMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * {@link CKFileMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 * 支持 LOCAL 和 URL 两种来源类型。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlCKFileMapper extends CKFileMapper {

    @Override
    @Insert("INSERT INTO ck_file (oid, source_type, source_url, file_name, file_size, storage_path, mime_type, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{sourceType}, #{sourceUrl}, #{fileName}, #{fileSize}, #{storagePath}, #{mimeType}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(CKFile file);

    @Override
    @Update("UPDATE ck_file SET source_type = #{sourceType}, source_url = #{sourceUrl}, " +
            "file_name = #{fileName}, file_size = #{fileSize}, " +
            "storage_path = #{storagePath}, mime_type = #{mimeType}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(CKFile file);

    @Override
    @Delete("DELETE FROM ck_file WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, source_type, source_url, file_name, file_size, storage_path, mime_type, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_file ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "ckFileResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "sourceType",  column = "source_type"),
            @Result(property = "sourceUrl",   column = "source_url"),
            @Result(property = "fileName",    column = "file_name"),
            @Result(property = "fileSize",    column = "file_size"),
            @Result(property = "storagePath", column = "storage_path"),
            @Result(property = "mimeType",    column = "mime_type"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    CKFile selectByOid(@Param("oid") String oid);
}
