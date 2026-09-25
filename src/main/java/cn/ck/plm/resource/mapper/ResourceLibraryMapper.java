/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 企业资源库（元器件库 / 标准件库 / 通用件库）数据访问接口。
 *
 * <p>各资源库管理的对象是 Part 主数据的软类型（ck_part），本 Mapper 负责：
 * <ul>
 *   <li>资源库分类绑定配置（ck_library_cls_config，按 租户 + 资源库节点）</li>
 *   <li>资源库节点归属阶段（ck_stage，平台级共享）</li>
 *   <li>按分类集合查询各资源库对应软类型的 Part 清单</li>
 * </ul>
 */
public interface ResourceLibraryMapper {

    // ==================== 分类绑定配置（ck_library_cls_config） ====================

    /** 查询当前租户 + 指定资源库的绑定根节点 oid */
    String selectBindingRootOid(@Param("tenantOid") String tenantOid,
                                @Param("resourceCode") String resourceCode);

    /**
     * 一次性查询当前租户所有资源库的资源库 code → 绑定根分类 oid 列表。
     * 用于「资源库分类绑定」页面加载（替代按 code 单条 N 次调用）。
     * 列表元素：[{ resource_code, root_classification_oid }]
     */
    List<Map<String, Object>> selectAllBindingRootOids(@Param("tenantOid") String tenantOid);

    /** 当前租户 + 指定资源库是否已存在绑定配置 */
    int countBinding(@Param("tenantOid") String tenantOid,
                     @Param("resourceCode") String resourceCode);

    /** 插入绑定配置 */
    int insertBinding(@Param("oid") String oid,
                      @Param("tenantOid") String tenantOid,
                      @Param("resourceCode") String resourceCode,
                      @Param("resourceNodeOid") String resourceNodeOid,
                      @Param("rootClassificationOid") String rootClassificationOid,
                      @Param("now") LocalDateTime now);

    /** 更新绑定配置 */
    int updateBinding(@Param("tenantOid") String tenantOid,
                      @Param("resourceCode") String resourceCode,
                      @Param("rootClassificationOid") String rootClassificationOid,
                      @Param("now") LocalDateTime now);

    // ==================== 资源库节点归属阶段（ck_stage，平台级共享） ====================

    /** 查询资源子库节点下的默认阶段 oid（ck_stage，按 sort_order 取第一个） */
    String selectResourceStageOid(@Param("ownerOid") String ownerOid);

    /** 创建资源子库归属阶段行（tenant_oid 由 SQL 显式写平台租户） */
    int insertResourceStage(@Param("oid") String oid,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("ownerOid") String ownerOid,
                            @Param("now") LocalDateTime now);

    // ==================== 元器件清单（ELECTRONIC 软类型的 Part） ====================

    /**
     * 查询指定分类集合下的 ELECTRONIC Part。
     * @param clsOids  分类 oid 集合（空 = 不限分类）
     * @param keyword  关键字（名称/编码模糊，可空）
     */
    /**
     * 资源库清单查询：按对象软类型 + 分类集合。
     *
     * @param typeCode 对象软类型 code（ELECTRONIC / STD_PART / GEN_PART）
     */
    List<Map<String, Object>> selectLibraryItems(@Param("clsOids") List<String> clsOids,
                                                 @Param("keyword") String keyword,
                                                 @Param("typeCode") String typeCode);
}
