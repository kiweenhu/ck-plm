/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.api;

import java.util.List;
import java.util.Map;

/**
 * 企业资源库-技术文档知识库（TECH_DOC 资源子库）服务。
 *
 * <p>业务对象是<b>通用文档 Document</b>（{@code ck_document}），不是工程数据 EPMDocument。
 * 文档的创建/编辑仍走统一的软类型入口（{@code /api/softtype-instances}），
 * 本服务只负责：子库归属上下文、按文件夹列文档。
 */
public interface TechDocService {

    /**
     * 技术文档库归属上下文：
     * {@code { containerType, containerOid, containerCode, containerName, stageOid }}。
     *
     * <p>本租户还没装出该子库时 {@code containerOid / stageOid} 为 null，前端按空态处理。
     */
    Map<String, Object> getResourceContext();

    /**
     * 技术文档清单。
     *
     * @param folderOid 文件夹 oid（可空 = 该库全部）
     * @param keyword   名称 / 编码 / 主文件名关键字（可空）
     */
    List<Map<String, Object>> findItems(String folderOid, String keyword);
}
