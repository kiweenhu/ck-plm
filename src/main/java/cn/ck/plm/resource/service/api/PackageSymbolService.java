/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.api;

import java.util.List;
import java.util.Map;

/**
 * 企业资源库-封装·图符库（PACKAGE_SYMBOL）服务接口。
 *
 * <p>封装（FOOTPRINT）与图符（SYMBOL）均为 PART 的软类型：创建 / 编辑走 {@code /api/parts}，
 * 本服务负责资源库归属上下文与按文件夹的清单查询。
 */
public interface PackageSymbolService {

    /**
     * 封装·图符归属上下文：containerOid / containerType / containerCode / containerName / stageOid。
     */
    Map<String, Object> getResourceContext();

    /**
     * 查询封装 / 图符清单。
     *
     * @param folderOid 文件夹 oid（为空表示不限文件夹）
     * @param typeCode  FOOTPRINT / SYMBOL（为空表示两者都查）
     * @param keyword   名称或编码关键字（为空表示不过滤）
     */
    List<Map<String, Object>> findItems(String folderOid, String typeCode, String keyword);
}
