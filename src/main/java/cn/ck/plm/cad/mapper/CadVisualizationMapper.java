/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.mapper;

import cn.ck.plm.cad.dto.CadConvertJobVO;
import cn.ck.plm.cad.dto.CadConvertStatsVO;
import cn.ck.plm.cad.dto.ContainerCadModelVO;
import cn.ck.plm.cad.entity.CadVisualization;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CAD 可视化转换记录读写（方言无关接口，实现按数据库分包）。
 */
public interface CadVisualizationMapper {

    int insert(CadVisualization record);

    int update(CadVisualization record);

    CadVisualization selectByOid(@Param("oid") String oid);

    /**
     * 按「迭代 + 内容指纹」查 —— 缓存命中的判据。
     * 内容没变就复用上次产物，不再提交转换（转换是这个系统里最贵的资源）。
     */
    CadVisualization selectByIterationAndSha1(@Param("engDocIterationOid") String engDocIterationOid,
                                              @Param("sourceSha1") String sourceSha1);

    /** 取该迭代最近一次转换记录（用于前端展示当前状态） */
    CadVisualization selectLatestByIteration(@Param("engDocIterationOid") String engDocIterationOid);

    /** 按转换器任务号查（回调路由用） */
    CadVisualization selectByForgeJobId(@Param("forgeJobId") String forgeJobId);

    /**
     * 查询"卡住"的任务：仍处在 QUEUED/RUNNING、且提交时间早于 cutoff。
     * 看门狗靠它做补偿 —— 回调丢了、转换器重启了，任务不能永远停在 RUNNING。
     */
    List<CadVisualization> selectStaleInFlight(@Param("cutoff") LocalDateTime cutoff,
                                               @Param("limit") int limit);

    /**
     * 容器（产品系列 / 型号）下配套的三维数模清单 —— 「产品数模」页签的数据源。
     *
     * <p>与 {@link #selectLatestByIteration(String)} 的区别：那个按单个迭代查状态，
     * 这个从容器出发把"这个产品有哪些数模、各自转到哪一步了"一次捞全，避免前端 N+1。
     *
     * <p>口径：{@code ck_eng_document}（工程数据 / EPMDocument）的最新迭代主文件后缀属于
     * STEP/IGES/STL 白名单，且工程数据归属该容器（自身 / 父系列 / 下属型号）。
     * 不是通用文档 {@code ck_document} —— 数模必须挂在 EPMDocument 上才带 CAD 专有属性。
     *
     * <p>没有转换记录的行也要返回（状态为空 = 未转换），
     * 否则用户看到的是"这个产品没有数模"，而不是"有数模但还没生成预览"。
     */
    List<ContainerCadModelVO> selectModelsByContainer(@Param("containerOid") String containerOid);

    /**
     * 零件版本 → 它关联的工程数据上可轻量化的数模 + 最近一次转换状态
     * （零件详情 / BOM 右侧「轻量化」页签的数据源）。
     *
     * <p>与 {@link #selectModelsByContainer(String)} 的分工：那个按容器列全部数模，
     * 这个回答"<b>这一个零件版本</b>的三维长什么样、能不能预览"。
     *
     * <p><b>为什么按零件主对象兜底</b>：工程数据关联记在<b>零件迭代</b>上（EPMBuildRule），
     * 检出会新建一个小版本迭代（A-Z.1 → A-Z.2）而关联不会跟着复制 ——
     * 只按迭代精确匹配的话，一检出"轻量化"页签就空掉。所以放宽到同一零件的所有迭代，
     * 但仍<b>优先精确命中当前迭代</b>（见 {@code ORDER BY (l.part_iteration_oid = ?) DESC}）。
     *
     * <p>没有关联工程数据、或主文件不是数模后缀时返回 {@code null}。
     */
    ContainerCadModelVO selectModelByPartIteration(@Param("partIterationOid") String partIterationOid);

    /**
     * 零件版本 → 其关联工程数据上可用的缩略图（「物料清单」列表用，2026-10-26）。
     *
     * <p>链条：{@code ck_eng_doc_part_link}（一个零件版本只有一份）→ 工程数据迭代
     * （关联时未指定迭代 = 跟随最新，取 {@code latest = TRUE}）→
     * {@code ck_cad_visualization.thumbnail_ckfile_oid}。
     *
     * <p><b>工程数据被检出也要给图</b>：检出会新建迭代 +1 并继承同一个 {@code ckfile_oid}，
     * 而转换记录挂在被检出的那一版上 —— 只按迭代精确匹配的话，一检出物料清单的缩略图就空了。
     * 所以与 {@link #selectModelsByContainer(String)} 同口径：先取本迭代的记录，
     * 没有才沿 {@code derived_from_oid} 复用，且要求两版 {@code ckfile_oid} 相同（换了文件必须重新转换）。
     *
     * <p>返回 {@code [{partIterationOid, thumbnailCkfileOid}]}；一个迭代可能有多条转换记录
     * （不同内容指纹各一条），用 {@code DISTINCT ON} 取最近一条**有缩略图**的，
     * 免得同一零件在清单里被复制成多行。
     *
     * <p>批量查（不是逐行）：物料清单动辄上百行，N+1 会把一次列表请求拖成上百次往返。
     */
    List<Map<String, Object>> selectThumbnailsByPartIterations(
            @Param("partIterationOids") List<String> partIterationOids);

    int deleteByOid(@Param("oid") String oid);

    /**
     * 「数模转换中心」分页查询 —— <b>跨容器、跨人</b>的全局队列视图。
     *
     * <p>与 {@link #selectModelsByContainer(String)} 的分工：那个回答"这个产品有哪些数模"，
     * 这个回答"全公司现在有多少张图在排队/在转/昨天那批转完了没"。
     * 批量提交之后，后者才是操作员真正盯着的页面。
     *
     * <p>排序：进行中优先（否则一页 20 条全被历史成功记录占满，看不见队列），
     * 进行中内部按提交时间正序（先来先服务，与转换器的实际执行顺序一致），
     * 其余按最后更新时间倒序。
     *
     * @param statuses 状态过滤（null / 空 = 全部）；由服务层把页签翻译成具体状态集合
     * @param keyword  文件名 / 工程数据编码 / 名称 模糊匹配；空表示不过滤
     */
    List<CadConvertJobVO> selectConvertJobs(@Param("statuses") List<String> statuses,
                                            @Param("keyword") String keyword,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /** 与 {@link #selectConvertJobs} 同一套过滤条件，只数条数（分页器用） */
    int countConvertJobs(@Param("statuses") List<String> statuses,
                         @Param("keyword") String keyword);

    /** 转换中心顶部计数（本租户） */
    CadConvertStatsVO selectConvertStats();
}
