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
 * 企业资源库-封装·图符库（PACKAGE_SYMBOL）数据访问接口。
 *
 * <p>封装（FOOTPRINT）与图符（SYMBOL）均为<b>工程数据 ENG_DOCUMENT</b> 的软类型
 * （EDA 设计数据，非物料）：主数据存于 {@code ck_eng_document}（版本存
 * {@code ck_eng_document_iteration}），专业字段分别存于 {@code ck_ecad_footprint_ext} /
 * {@code ck_ecad_symbol_ext}（1:1 挂宿主 oid）。
 */
public interface PackageSymbolMapper {

    /**
     * 查询封装 / 图符清单。
     *
     * @param folderOid 文件夹 oid（为空表示不限文件夹）
     * @param typeCode  类型过滤：FOOTPRINT / SYMBOL（为空表示两者都查）
     * @param keyword   名称或编码模糊匹配（为空表示不过滤）
     */
    List<Map<String, Object>> selectPackageSymbolItems(@Param("folderOid") String folderOid,
                                                       @Param("typeCode") String typeCode,
                                                       @Param("keyword") String keyword);

    /** 查询资源子库归属阶段 oid（ck_stage，owner_type='LINE'） */
    String selectResourceStageOid(@Param("ownerOid") String ownerOid);

    /** 兜底补建资源子库归属阶段 */
    int insertResourceStage(@Param("oid") String oid,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("ownerOid") String ownerOid,
                            @Param("now") LocalDateTime now);
}
