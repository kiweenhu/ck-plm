/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.entity.ProcessNodeLog;

import java.util.List;
import java.util.Map;

/**
 * 流程节点执行日志 —— 记录并读取「某个节点后台执行了什么」。
 *
 * <p>存在意义：自动服务（设置状态 / 自动服务 / 通知）是后台跑的，失败时用户只看到
 * "流程卡住了"，原因只留在服务器日志里。这里把它按「实例 + 节点」落到库里，
 * 流程详情页点开节点即可看到执行情况与错误原因。
 *
 * <p><b>写日志绝不能影响流程</b>（见实现类的独立事务与吞异常）：记录失败最多是"少一条线索"，
 * 而因为写日志失败让流程走不下去，是把可观测性问题升级成了业务故障。
 */
public interface ProcessNodeLogService {

    /**
     * 写一条节点执行日志。
     *
     * <p>调用方只填它知道的字段；{@code oid} / {@code tenantOid}（缺省时取当前租户）/ {@code createdAt} 由实现补齐。
     */
    void record(ProcessNodeLog row);

    /** 正常信息（如"自动服务开始/完成"） */
    void info(String instanceId, String activityId, String activityName, String source, String message);

    /** 失败：把异常类名、消息与堆栈一并留下 —— 排查时要看的就是这些 */
    void error(String instanceId, String activityId, String activityName, String source,
               String message, Throwable error);

    /** 某实例的全部日志（时间正序） */
    List<ProcessNodeLog> findByInstance(String instanceId);

    /** 某实例某个节点的日志（时间正序） */
    List<ProcessNodeLog> findByActivity(String instanceId, String activityId);

    /**
     * 节点经路的角标数据：activityId → [日志总数, 错误数]。
     *
     * <p>让"哪个节点出过错"在时间轴上一眼可见，不必逐条点进去才发现。
     */
    Map<String, int[]> countsByActivity(String instanceId);

    /** 删除某实例的全部日志（流程实例被删除时一并清理，避免留下查不到出处的孤儿行） */
    void deleteByInstance(String instanceId);
}
