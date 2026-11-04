/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.DeliverableArtifactLink;

import java.util.List;
import java.util.Map;

/**
 * 交付件 ↔ 产出物 关联 Mapper。
 *
 * <p>2026-10-11：交付件的产出物可能是文档 / 工程数据 / 零组件三类，统一以
 * {@code entityType + entityOid} 关联；候选查询按交付件的 object_type 硬约束落到对应宿主表。
 */
public interface DeliverableArtifactLinkMapper {

    /** 新增关联（oid / creator / createdAt 由调用方生成） */
    int insert(DeliverableArtifactLink link);

    /** 按 oid 查一条 */
    DeliverableArtifactLink selectByOid(String oid);

    /** 删除 */
    int deleteByOid(String oid);

    /**
     * 某交付件下的全部关联，含产出物的编码 / 名称（LEFT JOIN 三类产出物表）。
     * 返回键为驼峰（SQL 用双引号别名）：oid / entityType / typeCode / entityOid /
     * iterationOid / role / containerOid / createdAt / entityCode / entityName。
     */
    List<Map<String, Object>> selectByDeliverableOid(String deliverableOid);

    /**
     * 某归属单元（系列 / 型号）下每个交付件的已关联数：
     * 返回 [{ deliverableOid, cnt }]（交付件面板列"产出物"用）。
     */
    List<Map<String, Object>> countByOwnerOid(String ownerOid);

    /** 类型 code → 宿主 code（ck_type_definition.root_type_code），用于候选查询路由 */
    String selectRootTypeCode(String code);

    /** 候选：文档（keyword 可空） */
    List<Map<String, Object>> searchDocumentCandidates(String keyword);

    /** 候选：工程数据（typeCode 可空 = 该宿主全部类型；keyword 可空） */
    List<Map<String, Object>> searchEngDocCandidates(String typeCode, String keyword);

    /** 候选：零组件（typeCode 可空；keyword 可空） */
    List<Map<String, Object>> searchPartCandidates(String typeCode, String keyword);

    /**
     * 该产出物已关联到的交付件 oid（没有关联返回 null）。
     * 一个对象只能属于一个交付件：候选里已排除，创建时再兜一刀，避免并发 / 绕过前端。
     */
    String selectDeliverableOidByEntity(String entityType, String entityOid);

    /** 按 oid 定位产出物（创建时服务端复核存在性与类别）—— 三类各一 */
    Map<String, Object> selectDocumentArtifact(String oid);
    Map<String, Object> selectEngDocArtifact(String oid);
    Map<String, Object> selectPartArtifact(String oid);

    // ==================== 状态判定用（2026-10-11） ====================

    /**
     * 某归属单元（系列 / 型号）下的全部关联（轻量列，供 {@code DeliverableStatusEvaluator} 批量判定）。
     * 返回 [{ deliverableOid, entityType, typeCode, entityOid }]。
     */
    List<Map<String, Object>> selectByOwnerOid(String ownerOid);

    /**
     * 某归属单元（系列 / 型号）下、挂在**产出物实例**上的**未闭环问题**（{@code ISSUE_CLOSED} 判定用）。
     *
     * <p>问题与对象的关联只存了类型 + 编码（{@code ck_issue.related_type / related_code}，没有 oid），
     * 所以这里按<b>产出物的编码</b>与 {@code related_code} 对齐 —— 正是界面上"关联对象"选中的那个编码。
     * {@code CLOSED} / {@code CANCELLED} 视为已闭环（取消掉的问题不该继续挡人）。
     *
     * <p>返回 [{ deliverableOid, entityType, entityCode, issueCode, issueName, severity, issueStatus }]。
     */
    List<Map<String, Object>> selectOpenIssuesOfOwnerArtifacts(String ownerOid);

    /**
     * 各产出物的**最新迭代**状态（返回 [{ entityOid, status, lifecycleTemplateIterationOid }]）—— 三类各一。
     *
     * <p>{@code lifecycleTemplateIterationOid} 供状态显示名解析（{@code LifecycleStatusService#displayName}）：
     * 判定只需 code，界面需要「评审中」这类人话。
     */
    List<Map<String, Object>> selectLatestDocStatus(List<String> masterOids);
    List<Map<String, Object>> selectLatestEngDocStatus(List<String> masterOids);
    List<Map<String, Object>> selectLatestPartStatus(List<String> masterOids);

    /**
     * 生命周期模板子版本的状态序列（{@code LIFECYCLE} 达标判定用）。
     *
     * <p>为什么不能拿一套写死的高低顺序去比：状态码是**各租户模板自己定义**的 ——
     * 同一套 IPD 交付件写的是「已评审」，而本租户文档模板里是
     * {@code DRAFT / APPROVING(审核中) / PUBLISHED(已发布) / OFFLINE / ARCHIVED}，
     * 写死的顺序表里既没有 {@code APPROVING} 也没有 {@code PUBLISHED}，于是"已发布"被判成"未达已评审"。
     * 权威的大小关系就在这张表的 {@code sort_order} 里。
     *
     * <p>返回 [{ templateIterationOid, statusCode, statusDisplayName, sortOrder }]。
     */
    List<Map<String, Object>> selectTemplateStates(List<String> templateIterationOids);
}
