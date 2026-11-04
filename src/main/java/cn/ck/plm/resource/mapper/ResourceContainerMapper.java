/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper;

import cn.ck.plm.resource.entity.ResourceContainer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业资源库容器（{@code ck_resource_container}）数据访问接口。
 *
 * <p>资源库**按租户隔离**（2026-10-11 起）：容器随 {@code platform-base} 包按租户各装一份，
 * 租户之间互相不可见；平台租户那份是母版。所有查询/插入一律**显式**带 {@code tenantOid} ——
 * 与 {@code ck_classification} / {@code ck_unit} 同款口径，不依赖
 * {@code TenantStatementInterceptor} 注入（上下文为空时拦截器不加条件；显式传参则漏传会
 * 直接报错，而不是静默地跨租户读写）。
 *
 * <p>容器随包安装：{@code ResourceLibraryInstaller} 在租户上下文里建根 + 子库；
 * 平台租户那份由平台级安装建立。
 */
public interface ResourceContainerMapper {

    /** 按 code 查询**本租户**的资源库节点（未删除） */
    ResourceContainer selectByCode(@Param("code") String code, @Param("tenantOid") String tenantOid);

    /** 查询**本租户**资源库根节点下的全部子资源库（按 sort_order / code 排序） */
    List<ResourceContainer> selectChildren(@Param("parentOid") String parentOid, @Param("tenantOid") String tenantOid);

    /** 查询**本租户**的资源库根节点（CORP_RESOURCE） */
    ResourceContainer selectRoot(@Param("tenantOid") String tenantOid);

    /** 创建资源库节点（{@code tenant_oid} 取自 {@code node}，须由调用方按目标租户设置） */
    int insert(ResourceContainer node);
}
