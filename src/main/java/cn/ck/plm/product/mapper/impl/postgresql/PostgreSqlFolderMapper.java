/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Folder;
import cn.ck.plm.product.mapper.FolderMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link FolderMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlFolderMapper extends FolderMapper {

    @Override
    @Insert("INSERT INTO ck_folder (oid, owner_oid, stage_oid, parent_folder_oid, name, type, sort_order, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{ownerOid}, #{stageOid}, #{parentFolderOid}, #{name}, #{type}, #{sortOrder}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Folder folder);

    @Override
    @Update("UPDATE ck_folder SET name = #{name}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(Folder folder);

    @Override
    @Delete("DELETE FROM ck_folder WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================
    String SELECT_COLUMNS = "SELECT oid, owner_oid, stage_oid, parent_folder_oid, name, type, sort_order, " +
            " creator, created_at, updater, updated_at FROM ck_folder ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "folderResult", value = {
            @Result(property = "oid",              column = "oid"),
            @Result(property = "ownerOid",         column = "owner_oid"),
            @Result(property = "stageOid",         column = "stage_oid"),
            @Result(property = "parentFolderOid",  column = "parent_folder_oid"),
            @Result(property = "name",             column = "name"),
            @Result(property = "type",             column = "type"),
            @Result(property = "sortOrder",        column = "sort_order"),
            @Result(property = "creator",          column = "creator"),
            @Result(property = "createdAt",        column = "created_at"),
            @Result(property = "updater",          column = "updater"),
            @Result(property = "updatedAt",        column = "updated_at")
    })
    @Override
    Folder selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE owner_oid = #{ownerOid} AND stage_oid = #{stageOid} " +
            "AND parent_folder_oid IS NULL ORDER BY sort_order ASC, name ASC")
    @ResultMap("folderResult")
    List<Folder> selectRoots(@Param("ownerOid") String ownerOid,
                              @Param("stageOid") String stageOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_folder_oid = #{parentFolderOid} ORDER BY sort_order ASC, name ASC")
    @ResultMap("folderResult")
    List<Folder> selectByParentOid(@Param("parentFolderOid") String parentFolderOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE owner_oid = #{ownerOid} AND stage_oid = #{stageOid} " +
            "ORDER BY sort_order ASC, name ASC")
    @ResultMap("folderResult")
    List<Folder> selectByOwnerAndStage(@Param("ownerOid") String ownerOid,
                                        @Param("stageOid") String stageOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_folder WHERE parent_folder_oid = #{parentFolderOid}")
    int countByParentOid(@Param("parentFolderOid") String parentFolderOid);

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_folder WHERE parent_folder_oid " +
            "<if test='parentFolderOid != null'> = #{parentFolderOid} </if>" +
            "<if test='parentFolderOid == null'> IS NULL </if>" +
            "AND name = #{name}" +
            " AND owner_oid = #{ownerOid}" +
            "<if test='excludeOid != null'> AND oid != #{excludeOid} </if>" +
            "</script>")
    int existsByName(@Param("parentFolderOid") String parentFolderOid,
                     @Param("name") String name,
                     @Param("excludeOid") String excludeOid,
                     @Param("ownerOid") String ownerOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_folder_oid IS NULL ORDER BY owner_oid ASC, stage_oid ASC, sort_order ASC, name ASC")
    @ResultMap("folderResult")
    List<Folder> selectAllRoots();
}
