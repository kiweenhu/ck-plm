/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 指标评估用的「事实查询」—— 只读、只聚合，不返回实体。
 *
 * <p>为什么单独一个 Mapper：这些语句服务的是"算数"，不是"读写某个实体"。
 * 塞进 {@code IssueMapper} / {@code PartMapper} 会让那两个类各自多出几条只为指标服务的 SQL，
 * 而指标口径变化（新增一项、改一次分母）时改动本该集中在一个地方。
 *
 * <p><b>粒度约定</b>：所有查询都按 {@code container_oid = ownerOid} 取"该系列/型号自己的东西"——
 * 与物料清单"型号页不混入系列零件"同一条规则。系列不汇总型号的数据，型号也不继承系列的数据。
 */
public interface MetricFactsMapper {

    /**
     * 某产品（系列 / 型号）下问题的状态分布 —— {@code ISSUE_CLOSE_RATE} 的分子分母。
     * 返回行：{@code status, cnt}。
     */
    List<Map<String, Object>> countIssueStatusByProduct(@Param("tenantOid") String tenantOid,
                                                        @Param("productOid") String productOid);

    /** 容器下的器件总数（器件类指标的分母基数） */
    int countPartsByContainer(@Param("containerOid") String containerOid);

    /**
     * 容器下器件填写的指定 IBA 值 —— 返回 {@code part_oid, attr_code, attr_value}，
     * {@code attr_value} 已用 {@code #>> '{}'} 取成标量文本（库里是 JSONB）。
     *
     * <p>只返回"填过的"行：没填的器件不出现在结果里，调用方据此区分"未填"与"填了某个值"。
     */
    List<Map<String, Object>> selectPartIbaValues(@Param("containerOid") String containerOid,
                                                  @Param("attrCodes") List<String> attrCodes);

    /** 容器下有启用替代关系的器件数 —— {@code ALT_COVERAGE} 的分子 */
    int countPartsWithAlternate(@Param("containerOid") String containerOid);

    /**
     * 容器的 BOM 根：{@code ck_part.container_oid = 容器} 且 {@code latest = TRUE}、
     * 并且确实挂了 BOM 行的那个迭代 oid。没有则返回 null。
     *
     * <p>BOM 成本接口只认父件<b>迭代</b> oid，这是唯一能把"容器"翻译成"成本计算的起点"的地方。
     */
    String selectBomRootIteration(@Param("containerOid") String containerOid);

    /**
     * 容器自己登记的<b>目标成本</b>（IBA {@code TARGET_COST}），标量文本；没填返回 null。
     *
     * <p>只认<b>本容器</b>的那一行：型号没填就是没填，不回头找父系列 ——
     * 拿整条产品线的预算当型号目标，会让达成率凭空好看（口径见 {@code MetricEvaluator}）。
     *
     * <p>{@code entity_type} 同时匹配 {@code PRODUCT_LINE} / {@code PRODUCT_MODEL}：
     * 评估器只知道 ownerOid、不知道它落在哪张表，而两者的 oid 不会重合。
     */
    String selectTargetCost(@Param("containerOid") String containerOid,
                            @Param("attrCode") String attrCode);

    /**
     * 容器内<b>可认证件</b>数：{@code ELECTRONIC}（电子元器件）与 {@code PCBA}。
     *
     * <p>结构件 / 电气件不做器件认证，放进分母只会把准入率永久拉低，
     * 所以这里按软类型收口，而不是"容器里所有零件"。
     */
    int countCertifiableParts(@Param("containerOid") String containerOid);

    /**
     * 可认证件里<b>至少有一条 AML 落在给定认证状态</b>的件数（同一器件的多条料号只算一件）。
     *
     * <p>数据来自 {@code ck_part_aml.approved_status}（PREFERRED / APPROVED / DISALLOWED），
     * 即「器件 → 厂商料号」面板与元器件认证流程写的那张表 —— 不再依赖人工填 IBA。
     */
    int countCertifiablePartsWithAmlStatus(@Param("containerOid") String containerOid,
                                           @Param("statuses") java.util.List<String> statuses);

    /**
     * 容器上某道门<b>最近一次通过</b>的日期（{@code yyyy-MM-dd}）；没通过、没这道门、或被裁剪掉 → null。
     *
     * <p>只认"通过"的两种结论（{@code GO} / {@code GO_WITH_CONDITION}）：被 Hold / Terminate /
     * 退回重做的门不算"这件事发生了"，拿它当实际上市日期会算出一个假的偏差。
     */
    String selectGateDecidedDate(@Param("ownerOid") String ownerOid,
                                 @Param("gateCode") String gateCode);
}
