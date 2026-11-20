/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.mapper.impl.postgresql;

import cn.ck.plm.drawing.entity.DrawingVisualization;
import cn.ck.plm.drawing.mapper.DrawingVisualizationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;

/** PostgreSQL 实现（注解式，与 PostgreSqlCadVisualizationMapper 同风格）。 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDrawingVisualizationMapper extends DrawingVisualizationMapper {

    @Override
    @Insert("INSERT INTO ck_drawing_visualization (oid, tenant_oid, eng_doc_iteration_oid, " +
            "source_ckfile_oid, source_sha1, source_file_name, source_size, status, progress, " +
            "attempt_count, creator, created_at, updater, updated_at, submitted_at) " +
            "VALUES (#{oid}, #{tenantOid}, #{engDocIterationOid}, #{sourceCkfileOid}, #{sourceSha1}, " +
            "#{sourceFileName}, #{sourceSize}, #{status}, #{progress}, #{attemptCount}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt}, #{submittedAt})")
    int insert(DrawingVisualization record);

    /**
     * 全字段更新。产物 oid 与指标用 COALESCE：回调可能只带部分产物
     * （如缩略图降级失败但 SVG 成功），直接覆盖会抹掉已有引用。
     */
    @Override
    @Update("UPDATE ck_drawing_visualization SET " +
            "status = #{status}, progress = #{progress}, stage = #{stage}, " +
            "forge_job_id = COALESCE(#{forgeJobId}, forge_job_id), " +
            "svg_ckfile_oid = COALESCE(#{svgCkfileOid}, svg_ckfile_oid), " +
            "thumbnail_ckfile_oid = COALESCE(#{thumbnailCkfileOid}, thumbnail_ckfile_oid), " +
            "structure_ckfile_oid = COALESCE(#{structureCkfileOid}, structure_ckfile_oid), " +
            "dxf_ckfile_oid = COALESCE(#{dxfCkfileOid}, dxf_ckfile_oid), " +
            "dwg_version = COALESCE(#{dwgVersion}, dwg_version), " +
            "entity_count = COALESCE(#{entityCount}, entity_count), " +
            "layer_count = COALESCE(#{layerCount}, layer_count), " +
            "skipped_entity_types = COALESCE(#{skippedEntityTypes}, skipped_entity_types), " +
            "fidelity_warnings = COALESCE(#{fidelityWarnings}, fidelity_warnings), " +
            "degraded_kinds = COALESCE(#{degradedKinds}, degraded_kinds), " +
            "output_size = COALESCE(#{outputSize}, output_size), " +
            "duration_ms = COALESCE(#{durationMs}, duration_ms), " +
            "converter_version = COALESCE(#{converterVersion}, converter_version), " +
            "error_code = #{errorCode}, error_message = #{errorMessage}, " +
            "attempt_count = #{attemptCount}, converted_at = #{convertedAt}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(DrawingVisualization record);

    String COLUMNS = "SELECT oid, tenant_oid, eng_doc_iteration_oid, source_ckfile_oid, source_sha1, " +
            "source_file_name, source_size, status, progress, stage, forge_job_id, " +
            "svg_ckfile_oid, thumbnail_ckfile_oid, structure_ckfile_oid, dxf_ckfile_oid, " +
            "dwg_version, entity_count, layer_count, skipped_entity_types, fidelity_warnings, " +
            "degraded_kinds, output_size, duration_ms, converter_version, error_code, error_message, " +
            "attempt_count, submitted_at, converted_at, creator, created_at, updater, updated_at " +
            "FROM ck_drawing_visualization ";

    @Select(COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "drawVisResult", value = {
            @Result(property = "oid", column = "oid"),
            @Result(property = "tenantOid", column = "tenant_oid"),
            @Result(property = "engDocIterationOid", column = "eng_doc_iteration_oid"),
            @Result(property = "sourceCkfileOid", column = "source_ckfile_oid"),
            @Result(property = "sourceSha1", column = "source_sha1"),
            @Result(property = "sourceFileName", column = "source_file_name"),
            @Result(property = "sourceSize", column = "source_size"),
            @Result(property = "status", column = "status"),
            @Result(property = "progress", column = "progress"),
            @Result(property = "stage", column = "stage"),
            @Result(property = "forgeJobId", column = "forge_job_id"),
            @Result(property = "svgCkfileOid", column = "svg_ckfile_oid"),
            @Result(property = "thumbnailCkfileOid", column = "thumbnail_ckfile_oid"),
            @Result(property = "structureCkfileOid", column = "structure_ckfile_oid"),
            @Result(property = "dxfCkfileOid", column = "dxf_ckfile_oid"),
            @Result(property = "dwgVersion", column = "dwg_version"),
            @Result(property = "entityCount", column = "entity_count"),
            @Result(property = "layerCount", column = "layer_count"),
            @Result(property = "skippedEntityTypes", column = "skipped_entity_types"),
            @Result(property = "fidelityWarnings", column = "fidelity_warnings"),
            @Result(property = "degradedKinds", column = "degraded_kinds"),
            @Result(property = "outputSize", column = "output_size"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "converterVersion", column = "converter_version"),
            @Result(property = "errorCode", column = "error_code"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "attemptCount", column = "attempt_count"),
            @Result(property = "submittedAt", column = "submitted_at"),
            @Result(property = "convertedAt", column = "converted_at"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    DrawingVisualization selectByOid(@Param("oid") String oid);

    @Override
    @Select(COLUMNS + "WHERE eng_doc_iteration_oid = #{engDocIterationOid} " +
            "AND source_sha1 = #{sourceSha1} LIMIT 1")
    @ResultMap("drawVisResult")
    DrawingVisualization selectByIterationAndSha1(@Param("engDocIterationOid") String engDocIterationOid,
                                                  @Param("sourceSha1") String sourceSha1);

    @Override
    @Select(COLUMNS + "WHERE eng_doc_iteration_oid = #{engDocIterationOid} " +
            "ORDER BY created_at DESC LIMIT 1")
    @ResultMap("drawVisResult")
    DrawingVisualization selectLatestByIteration(@Param("engDocIterationOid") String engDocIterationOid);

    @Override
    @Select(COLUMNS + "WHERE forge_job_id = #{forgeJobId} LIMIT 1")
    @ResultMap("drawVisResult")
    DrawingVisualization selectByForgeJobId(@Param("forgeJobId") String forgeJobId);

    @Override
    @Select(COLUMNS + "WHERE status IN ('QUEUED','RUNNING') AND submitted_at < #{cutoff} " +
            "ORDER BY submitted_at ASC LIMIT #{limit}")
    @ResultMap("drawVisResult")
    List<DrawingVisualization> selectStaleInFlight(@Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);
}
