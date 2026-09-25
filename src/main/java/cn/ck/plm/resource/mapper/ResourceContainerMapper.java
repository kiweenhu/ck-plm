/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper;

import cn.ck.plm.resource.entity.ResourceContainer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业级资源库容器数据访问接口（表 {@code ck_resource_container}）。
 *
 * <p>资源体系为平台级共享：根节点与各资源子库节点对所有租户可见，
 * 故 SQL 显式携带平台租户条件，避免被租户拦截器按当前租户过滤。
 */
public interface ResourceContainerMapper {

    /** 按 code 查询资源库节点（限定平台租户 + 未删除） */
    ResourceContainer selectByCode(@Param("code") String code);

    /** 查询资源库根节点的全部子资源库（按 sort_order / code 排序） */
    List<ResourceContainer> selectChildren(@Param("parentOid") String parentOid);

    /** 查询资源库根节点（CORP_RESOURCE） */
    ResourceContainer selectRoot();

    /** 创建资源库节点（tenant_oid 由 SQL 显式写平台租户） */
    int insert(ResourceContainer node);
}
