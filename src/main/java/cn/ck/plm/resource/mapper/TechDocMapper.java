/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
 * 技术文档知识库（TECH_DOC 资源子库）数据访问。
 *
 * <p>与 {@link PackageSymbolMapper} 的结构对齐（同样的归属阶段、同样的 Map 返回约定），
 * 差别只在<b>业务对象</b>：封装·图符是 EPMDocument（{@code ck_eng_document}），
 * 技术文档是通用文档（{@code ck_document}）—— 两类对象的表、软类型、扩展属性都不同，
 * 所以单独一套而不是给封装库加参数。
 */
public interface TechDocMapper {

    /**
     * 某资源子库容器下的技术文档清单。
     *
     * <p><b>必须按 container_oid 收口</b>：Document 是通用类型，产品系列/型号下也有大量文档，
     * 只按类型查会把整个系统的文档都列出来。封装库那边不需要这一步，
     * 因为 FOOTPRINT / SYMBOL 本身就是只属于封装库的软类型。
     *
     * @param containerOid TECH_DOC 资源子库容器 oid（必填）
     * @param folderOid    文件夹 oid（可空 = 该库全部）
     * @param keyword      名称 / 编码 / 主文件名关键字（可空）
     */
    List<Map<String, Object>> selectTechDocItems(@Param("containerOid") String containerOid,
                                                 @Param("folderOid") String folderOid,
                                                 @Param("keyword") String keyword);

    /** 资源子库的归属阶段（owner_type='LINE'，按租户隔离） */
    String selectResourceStageOid(@Param("ownerOid") String ownerOid, @Param("tenantOid") String tenantOid);

    /** 归属阶段缺失时补建（与资源节点同租户） */
    int insertResourceStage(@Param("oid") String oid,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("ownerOid") String ownerOid,
                            @Param("tenantOid") String tenantOid,
                            @Param("now") LocalDateTime now);
}
