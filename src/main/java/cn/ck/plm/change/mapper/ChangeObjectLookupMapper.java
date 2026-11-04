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
     * 该零件的替代件（{@code ck_part_alternate_link}，双向：A 端 B 端都算）。
     *
     * @return {@code oid / entityType(PART) / code / name / containerOid / relation(ALTERNATE) / alternateType}
     */
    List<Map<String, Object>> selectAlternateParts(@Param("partOid") String partOid,
                                                   @Param("tenantOid") String tenantOid);
}
