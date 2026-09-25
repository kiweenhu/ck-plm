/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper.impl.postgresql;

import cn.ck.plm.process.entity.ProcessTemplateVersion;
import cn.ck.plm.process.mapper.ProcessTemplateVersionMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * {@link ProcessTemplateVersionMapper} 的 PostgreSQL 实现。
 */
@Mapper
public interface PostgreSqlProcessTemplateVersionMapper extends ProcessTemplateVersionMapper {

    String TABLE = "ck_process_template_version";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, template_oid, version, dsl_json, bpmn_xml, "
            + "change_note, deployed, deployment_id, tenant_oid, creator, created_at) "
            + "VALUES (#{oid}, #{templateOid}, #{version}, #{dslJson}, #{bpmnXml}, "
            + "#{changeNote}, #{deployed}, #{deploymentId}, #{tenantOid}, #{creator}, #{createdAt})")
    int insert(ProcessTemplateVersion version);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE template_oid = #{templateOid} ORDER BY version DESC")
    List<ProcessTemplateVersion> selectByTemplateOid(@Param("templateOid") String templateOid);

    @Override
    @Select("SELECT * FROM " + TABLE
            + " WHERE template_oid = #{templateOid} AND version = #{version}")
    ProcessTemplateVersion selectByTemplateAndVersion(@Param("templateOid") String templateOid,
                                                      @Param("version") Integer version);

    @Override
    @Select("SELECT COALESCE(MAX(version), 0) FROM " + TABLE + " WHERE template_oid = #{templateOid}")
    int selectMaxVersion(@Param("templateOid") String templateOid);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE deployment_id = #{deploymentId} ORDER BY version DESC LIMIT 1")
    ProcessTemplateVersion selectByDeploymentId(@Param("deploymentId") String deploymentId);

    @Override
    @Update("UPDATE " + TABLE + " SET bpmn_xml = #{bpmnXml}, deployed = TRUE, "
            + "deployment_id = #{deploymentId} "
            + "WHERE template_oid = #{templateOid} AND version = #{version}")
    int markDeployed(@Param("templateOid") String templateOid,
                     @Param("version") Integer version,
                     @Param("bpmnXml") String bpmnXml,
                     @Param("deploymentId") String deploymentId);

    @Override
    @Delete("<script>DELETE FROM " + TABLE + " WHERE template_oid = #{templateOid} AND version IN "
            + "<foreach item='v' collection='versions' open='(' separator=',' close=')'>#{v}</foreach>"
            + "</script>")
    int deleteVersions(@Param("templateOid") String templateOid,
                       @Param("versions") List<Integer> versions);
}
