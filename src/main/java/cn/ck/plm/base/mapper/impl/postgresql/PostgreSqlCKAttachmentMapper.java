/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.CKAttachment;
import cn.ck.plm.base.mapper.CKAttachmentMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link CKAttachmentMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlCKAttachmentMapper extends CKAttachmentMapper {

    @Override
    @Insert("INSERT INTO ck_attachment (oid, owner_oid, file_name, file_size, storage_path, mime_type, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{ownerOid}, #{fileName}, #{fileSize}, #{storagePath}, #{mimeType}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(CKAttachment attachment);

    @Override
    @Update("UPDATE ck_attachment SET file_name = #{fileName}, file_size = #{fileSize}, " +
            "storage_path = #{storagePath}, mime_type = #{mimeType}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(CKAttachment attachment);

    @Override
    @Delete("DELETE FROM ck_attachment WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, owner_oid, file_name, file_size, storage_path, mime_type, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_attachment ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "ckAttachmentResult", value = {
            @Result(property = "oid",           column = "oid"),
            @Result(property = "ownerOid",     column = "owner_oid"),
            @Result(property = "fileName",      column = "file_name"),
            @Result(property = "fileSize",      column = "file_size"),
            @Result(property = "storagePath",   column = "storage_path"),
            @Result(property = "mimeType",      column = "mime_type"),
            @Result(property = "tenantOid",    column = "tenant_oid"),
            @Result(property = "creator",       column = "creator"),
            @Result(property = "createdAt",     column = "created_at"),
            @Result(property = "updater",       column = "updater"),
            @Result(property = "updatedAt",     column = "updated_at")
    })
    @Override
    CKAttachment selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE owner_oid = #{ownerOid} ORDER BY created_at ASC")
    @ResultMap("ckAttachmentResult")
    List<CKAttachment> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_attachment WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);
}
