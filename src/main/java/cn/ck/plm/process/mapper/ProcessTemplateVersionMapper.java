/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper;

import cn.ck.plm.process.entity.ProcessTemplateVersion;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板版本（{@code ck_process_template_version}）数据访问接口。
 */
public interface ProcessTemplateVersionMapper {

    int insert(ProcessTemplateVersion version);

    /** 按模板查询全部版本（新版本在前） */
    List<ProcessTemplateVersion> selectByTemplateOid(@Param("templateOid") String templateOid);

    /** 查询指定版本 */
    ProcessTemplateVersion selectByTemplateAndVersion(@Param("templateOid") String templateOid,
                                                      @Param("version") Integer version);

    /** 当前最大版本号（无版本返回 0） */
    int selectMaxVersion(@Param("templateOid") String templateOid);

    /**
     * 按 Flowable 部署 id 反查版本行。
     *
     * <p><b>为什么需要它</b>：Flowable 的"定义版本"是<b>同 key 第几次部署</b>，与模板的"版本号"
     * 不是同一个序列（模板 v3 可能只部署过一次 → 定义版本是 1）。要定位"在跑的实例用的是哪一版
     * 模板内容"，只能靠部署时写下的 {@code deployment_id} 这个唯一对应关系。
     */
    ProcessTemplateVersion selectByDeploymentId(@Param("deploymentId") String deploymentId);

    /** 写入部署产物（BPMN XML 快照 + 部署标识） */
    int markDeployed(@Param("templateOid") String templateOid,
                     @Param("version") Integer version,
                     @Param("bpmnXml") String bpmnXml,
                     @Param("deploymentId") String deploymentId);

    /**
     * 按版本号批量删除。
     *
     * <p>刻意<b>不提供"删除模板全部版本"</b>的方法：删除只能按版本进行，
     * 「哪些能删」的判断在服务层（已部署的版本不可删），SQL 层不给出绕过的口子。
     */
    int deleteVersions(@Param("templateOid") String templateOid,
                       @Param("versions") List<Integer> versions);
}
