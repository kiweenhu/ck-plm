/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 变更模块的<b>对象快照 / 候选</b>查询（只读）。
 *
 * <h3>为什么放在变更模块</h3>
 * <p>变更单要记录"对象是谁、在哪、当前是第几版"的快照，但不该依赖零件 / 文档服务的
 * 领域接口（那会把服务层依赖绕成一团）。这里只做三张主表 + 最新迭代的只读查询。
 *
 * <h3>返回字段（统一别名，前端直接可用）</h3>
 * <pre>
 * oid / entityType / code / name / containerOid / iterationOid / version
 * 候选另带 changeCode —— 该对象已在一张在途变更通告里时给出那张单的编号
 * </pre>
 *
 * <p>对象类型白名单与 {@code ReviseProvider} 同口径：PART / DOCUMENT / ENG_DOCUMENT。
 */
public interface ChangeObjectLookupMapper {

    // ==================== 单对象快照（录入受影响对象时） ====================

    Map<String, Object> selectPartSnapshot(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    Map<String, Object> selectDocumentSnapshot(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    Map<String, Object> selectEngDocumentSnapshot(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    // ==================== 修订产物所在大版本的最新小版本 ====================

    /**
     * 给定一条结果迭代 oid（修订那一刻的 B.1），返回<b>同一主对象、同一大版本</b>下
     * 迭代号最大的 display_version（如 B.2）。查不到返回 {@code null}。
     *
     * <p>变更执行人在修订后还会检出 / 修改 / 检入，同大版本下继续出 B.2、B.3 ——
     * 审批与执行表单要显示"改完现在是哪一版"，而不是结果表里那条历史快照。
     * 按 {@code (master_oid, revision)} 限定，避免把下一次大版本（C.1）算成这次的产物。
     *
     * <p>SQL 里带 {@code tenant_oid =} 条件：租户拦截器见到已有条件便不会二次改写。
     */
    String selectPartVersionOfRevision(@Param("iterationOid") String iterationOid,
                                       @Param("tenantOid") String tenantOid);

    String selectDocumentVersionOfRevision(@Param("iterationOid") String iterationOid,
                                           @Param("tenantOid") String tenantOid);

    String selectEngDocumentVersionOfRevision(@Param("iterationOid") String iterationOid,
                                              @Param("tenantOid") String tenantOid);

    // ==================== 迭代 → 大版本 ====================

    /**
     * 给定一条迭代 oid，返回它所属的大版本 {@code revision}（如 B.1 那行 → {@code "B"}）。
     *
     * <p>写 {@code ck_process_entity_set.entity_version} 用：那一列记的是大版本，
     * 不是 {@code display_version}（B.1）；塞 B.1 会让宿主按 revision 查不到这一版。
     */
    String selectPartRevisionOfIteration(@Param("iterationOid") String iterationOid,
                                         @Param("tenantOid") String tenantOid);

    String selectDocumentRevisionOfIteration(@Param("iterationOid") String iterationOid,
                                             @Param("tenantOid") String tenantOid);

    String selectEngDocumentRevisionOfIteration(@Param("iterationOid") String iterationOid,
                                                @Param("tenantOid") String tenantOid);

    // ==================== 关键字候选（对象选择器） ====================

    /** 候选：零件（{@code containerOid} 为空 = 不限产品范围；非空 = 限定该系列/型号子树） */
    List<Map<String, Object>> searchParts(@Param("keyword") String keyword,
                                          @Param("tenantOid") String tenantOid,
                                          @Param("containerOid") String containerOid,
                                          @Param("limit") int limit);

    /** 候选：文档（{@code containerOid} 语义同上） */
    List<Map<String, Object>> searchDocuments(@Param("keyword") String keyword,
                                              @Param("tenantOid") String tenantOid,
                                              @Param("containerOid") String containerOid,
                                              @Param("limit") int limit);

    /** 候选：工程文档（{@code containerOid} 语义同上） */
    List<Map<String, Object>> searchEngDocuments(@Param("keyword") String keyword,
                                                 @Param("tenantOid") String tenantOid,
                                                 @Param("containerOid") String containerOid,
                                                 @Param("limit") int limit);

    // ==================== 关联对象收集（变更任务挑对象时） ====================

    /**
     * 该零件关联的文档（{@code ck_doc_part_link} 的 DESCRIBES + REFERENCE 两类）。
     *
     * <p>按<b>全部迭代</b>取：链接挂在零件迭代上，只在最新迭代里找会漏掉早先挂的文档
     * （迭代升版后链接是否带过去取决于零件模块，这里不赌）。结果按编码去重。
     *
     * @return {@code oid / entityType(PART) / code / name / containerOid / relation(DOCUMENT)}
     */
    List<Map<String, Object>> selectRelatedDocuments(@Param("partOid") String partOid,
                                                     @Param("tenantOid") String tenantOid);

    /**
     * 该零件关联的<b>工程数据</b>（{@code ck_eng_doc_part_link}，即 EPMBuildRule）。
     *
     * <p>取<b>最新迭代</b>的那一份 —— 工程数据是"一个零件版本恰好一份数模"，旧迭代上的是被取代的
     * 上一版，收进变更范围会让任务去改一份已经作废的数模（与文档侧"跨迭代累积"口径不同）。
     * 最新迭代没有任何关联时（刚检出）退回收全部迭代，不空手而归。
     *
     * <p>只按 {@code part_iteration_oid} 连：这张表的 {@code part_oid} 列实际不写值
     * （全部关联都由 {@code EngDocPartLink} 写迭代侧），拿它做条件会一行都查不到。
     *
     * @return {@code oid / entityType(ENG_DOCUMENT) / code / name / containerOid / relation(ENG_DOCUMENT)}
     */
    List<Map<String, Object>> selectRelatedEngDocuments(@Param("partOid") String partOid,
                                                        @Param("tenantOid") String tenantOid);

    /**
     * 该零件的替代件（{@code ck_part_alternate_link}，双向：A 端 B 端都算）。
     *
     * @return {@code oid / entityType(PART) / code / name / containerOid / relation(ALTERNATE) / alternateType}
     */
    List<Map<String, Object>> selectAlternateParts(@Param("partOid") String partOid,
                                                   @Param("tenantOid") String tenantOid);
}
