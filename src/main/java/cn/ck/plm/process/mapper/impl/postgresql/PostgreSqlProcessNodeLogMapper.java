/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper.impl.postgresql;

import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.mapper.ProcessNodeLogMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link ProcessNodeLogMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>{@code tenant_oid} 显式写入（业务表，查询侧由 {@code TenantStatementInterceptor} 注入租户条件）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlProcessNodeLogMapper extends ProcessNodeLogMapper {

    String TABLE = "ck_process_node_log";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, tenant_oid, process_instance_id, activity_id, activity_name, "
            + "level, source, message, detail, created_at) "
            + "VALUES (#{oid}, #{tenantOid}, #{processInstanceId}, #{activityId}, #{activityName}, "
            + "#{level}, #{source}, #{message}, #{detail}, #{createdAt,jdbcType=TIMESTAMP})")
    void insertLog(ProcessNodeLog row);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE process_instance_id = #{processInstanceId} "
            + "ORDER BY created_at ASC")
    List<ProcessNodeLog> selectByInstance(@Param("processInstanceId") String processInstanceId);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE process_instance_id = #{processInstanceId} "
            + "AND activity_id = #{activityId} ORDER BY created_at ASC")
    List<ProcessNodeLog> selectByActivity(@Param("processInstanceId") String processInstanceId,
                                          @Param("activityId") String activityId);

    @Override
    @Select("SELECT activity_id, level, count(*) AS cnt FROM " + TABLE
            + " WHERE process_instance_id = #{processInstanceId} "
            + "GROUP BY activity_id, level")
    List<Map<String, Object>> selectCountsByInstance(@Param("processInstanceId") String processInstanceId);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE process_instance_id = #{processInstanceId}")
    int deleteByInstance(@Param("processInstanceId") String processInstanceId);
}
