/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper;

import cn.ck.plm.process.entity.ProcessNodeLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 流程节点执行日志 Mapper（表 {@code ck_process_node_log}）。
 *
 * <p>租户隔离由租户拦截器按 {@code tenant_oid} 注入（本实体实现
 * {@code base.entity.TenantEntity}，与其它业务表同一套机制）。
 */
public interface ProcessNodeLogMapper {

    /** 追加一条日志（不更新、不删除：执行痕迹只增不改） */
    void insertLog(ProcessNodeLog row);

    /** 某实例的全部日志（按时间正序） */
    List<ProcessNodeLog> selectByInstance(@Param("processInstanceId") String processInstanceId);

    /** 某实例某个节点的日志（按时间正序）—— 点开一个节点要的就是这一条查询 */
    List<ProcessNodeLog> selectByActivity(@Param("processInstanceId") String processInstanceId,
                                          @Param("activityId") String activityId);

    /**
     * 某实例下"每个节点有几条日志、其中几条是错误"。
     *
     * <p>给节点经路画角标用：不用把日志全捞回来再在前端统计 —— 详情页只想知道
     * "哪个节点有错"，把明细一次性传过去既浪费又要前端写聚合。
     *
     * @return 每行 { activity_id, level, cnt }
     */
    List<Map<String, Object>> selectCountsByInstance(@Param("processInstanceId") String processInstanceId);

    /**
     * 删除某实例的全部日志（流程实例被删除时一并清理）。
     *
     * <p>与业务实体关联同一口径：实例没了，它的执行痕迹就成了查不到出处的孤儿行。
     *
     * @return 删除行数
     */
    int deleteByInstance(@Param("processInstanceId") String processInstanceId);
}
