/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.service.api;

import cn.ck.plm.drawing.dto.DrawingForgeCallbackPayload;
import cn.ck.plm.drawing.entity.DrawingVisualization;

import java.util.List;
import java.util.Map;

/** 二维图纸转换编排服务。 */
public interface DrawingConvertService {

    /** 提交转换（异步）：命中 sha1 缓存则直接复用，否则落记录并提交给转换器。 */
    DrawingVisualization submit(String engDocIterationOid, String sourceCkfileOid);

    /** 处理转换器回调（终态 + 进度）。 */
    void handleCallback(DrawingForgeCallbackPayload payload);

    DrawingVisualization getByOid(String oid);

    /** 取该迭代最近一次转换记录（前端展示当前状态）。 */
    DrawingVisualization getLatest(String engDocIterationOid);

    /** 重试（重置熔断计数，重新提交）。 */
    DrawingVisualization retry(String oid);

    /** 取消在途任务。 */
    DrawingVisualization cancel(String oid);

    /** 转换器是否已启用（未部署时前端隐藏入口）。 */
    boolean isAvailable();

    /** 健康检查（页面「测试连接」）。 */
    Map<String, Object> health();

    /** 看门狗：扫描超时未回的任务并补偿。 */
    void watchdogScan();

    /** 转换记录列表（按状态过滤）。 */
    List<DrawingVisualization> listJobs(String status, int limit);
}
