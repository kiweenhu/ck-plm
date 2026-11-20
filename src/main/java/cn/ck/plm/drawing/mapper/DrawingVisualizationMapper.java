/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.mapper;

import cn.ck.plm.drawing.entity.DrawingVisualization;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 二维图纸转换记录读写（方言无关接口，实现按数据库分包）。 */
public interface DrawingVisualizationMapper {

    int insert(DrawingVisualization record);

    int update(DrawingVisualization record);

    DrawingVisualization selectByOid(@Param("oid") String oid);

    /** 按「迭代 + 内容指纹」查 —— 缓存命中判据 */
    DrawingVisualization selectByIterationAndSha1(@Param("engDocIterationOid") String engDocIterationOid,
                                                  @Param("sourceSha1") String sourceSha1);

    /** 取该迭代最近一次转换记录（前端展示当前状态） */
    DrawingVisualization selectLatestByIteration(@Param("engDocIterationOid") String engDocIterationOid);

    /** 按转换器任务号查（回调路由用） */
    DrawingVisualization selectByForgeJobId(@Param("forgeJobId") String forgeJobId);

    /** 查询卡住的任务：QUEUED/RUNNING 且提交时间早于 cutoff（看门狗补偿） */
    List<DrawingVisualization> selectStaleInFlight(@Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);
}
