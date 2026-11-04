/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.service.api;

import cn.ck.plm.cad.dto.CadConvertBatchItem;
import cn.ck.plm.cad.dto.CadConvertBatchResultVO;
import cn.ck.plm.cad.dto.CadConvertJobPageVO;
import cn.ck.plm.cad.dto.ContainerCadModelVO;
import cn.ck.plm.cad.dto.ForgeCallbackPayload;
import cn.ck.plm.cad.entity.CadVisualization;

import java.util.List;

/**
 * CAD 轻量化转换编排。
 *
 * <h3>异步语义</h3>
 * 提交后<b>立刻返回</b>（状态 QUEUED），转换在转换器侧跑，结果通过回调打回来。
 * 调用方（前端）轮询 {@link #getLatest(String)} 看进展，不阻塞任何线程。
 *
 * <h3>三条结果通道</h3>
 * <ol>
 *   <li>回调 —— 最快，转换器一完成就打回来；</li>
 *   <li>看门狗 —— 回调丢了/转换器重启，定时主动拉状态补偿；
 *       只靠回调不够：丢一次前端就永远转圈；</li>
 *   <li>内容指纹缓存 —— 同一份文件不再转换，直接复用上次产物。</li>
 * </ol>
 */
public interface CadConvertService {

    /**
     * 触发一次转换（幂等：内容未变则复用上次结果）。
     *
     * @param engDocIterationOid 工程文档迭代 oid（产物归属）
     * @param sourceCkfileOid    源 STEP 文件（ck_file.oid）
     * @return 转换记录（状态可能是 QUEUED / SUCCESS / SKIPPED）
     */
    CadVisualization submit(String engDocIterationOid, String sourceCkfileOid);

    /** 按 oid 取记录 */
    CadVisualization getByOid(String oid);

    /** 取某迭代最近一次转换记录（不存在返回 null） */
    CadVisualization getLatest(String engDocIterationOid);

    /**
     * 容器（产品系列 / 型号）下配套的三维数模清单 —— 「产品数模」页签的数据源。
     *
     * <p>一次查全，避免前端按物料清单逐行调 {@link #getLatest(String)} 造成 N+1。
     * 返回项里 {@code status} 为空表示还没转换过（不是失败）。
     */
    List<ContainerCadModelVO> listModelsByContainer(String containerOid);

    /**
     * 单个<b>零件版本</b>的数模与转换状态 —— 零件详情 / BOM 右侧「轻量化」页签。
     *
     * <p>口径：零件迭代 →（EPMBuildRule）→ 工程数据迭代 → 主文件 → 最近一次转换记录。
     * 返回 {@code null} 表示该零件版本没关联工程数据、或主文件不是可轻量化的数模
     * （原生数模只归档、不转换）。
     *
     * @param partIterationOid 零件迭代 oid
     */
    ContainerCadModelVO getModelByPartIteration(String partIterationOid);

    /**
     * 打包下载某容器下的<b>全部</b>数模源文件（zip）—— 「下载装配数模」。
     *
     * <p>口径：列表里一行只是<b>一个文件</b>，整机装配要的是本容器下所有零件 / 装配数模。
     * 让用户逐个点下载再自己归类不现实，一次打包带走。
     *
     * @return zip 字节；没有任何可下载文件时返回 {@code null}（调用方转 404）
     */
    byte[] packageModels(String containerOid);

    /** 处理转换器回调 */
    void handleCallback(ForgeCallbackPayload payload);

    /** 手动重试（失败后用户点"重新转换"） */
    CadVisualization retry(String oid);

    /**
     * 批量提交转换 —— 勾选多份数模一次性入队。
     *
     * <p>为什么不做成前端循环调 {@link #submit}：那会产生 N 次 HTTP、N 次提示，
     * 而且中途失败没法汇总。这里一次请求拿回"哪几条进去了、哪几条被跳过/命中了缓存"。
     *
     * <p>逐条独立处理：某一条源文件缺失不影响其余条目。
     */
    CadConvertBatchResultVO submitBatch(List<CadConvertBatchItem> items);

    /**
     * 取消排队中 / 转换中的任务。
     *
     * <p>批量提交之后这是唯一的止损手段：误勾 30 张图，总不能一张张等它转完。
     * 先尽力通知转换器停手，再以本地状态为准（见 {@code CadForgeClient#cancelJob}）。
     */
    CadVisualization cancel(String oid);

    /** 看门狗：扫描并补偿卡住的任务 */
    void watchdogScan();

    /** 转换器是否可用（未启用/未配置时前端应隐藏 3D 入口，而不是显示一个永远转不完的圈） */
    boolean isAvailable();

    /**
     * 「数模转换中心」：跨容器、跨人的全局转换队列（分页）。
     *
     * <p>批量提交之后，"我这张转完了吗"要靠翻自己的产品页签来回答是不够的 ——
     * 操作员需要一眼看到整个租户的排队 / 转换中 / 失败。
     *
     * @param filter  active（排队+转换中）/ success / failed（失败+超时）/ skipped / 其它值 = 全部
     * @param keyword 文件名 / 工程数据编码 / 名称 模糊匹配
     */
    CadConvertJobPageVO listConvertJobs(String filter, String keyword, int page, int size);
}
