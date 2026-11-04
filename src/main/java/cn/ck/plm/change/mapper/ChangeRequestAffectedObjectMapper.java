/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeRequestAffectedObject;

import java.util.List;

/**
 * 变更请求（ECR）预估受影响对象 Mapper。
 *
 * <p>表 {@code ck_change_request_affected_object} 不在租户拦截器的共享名单里，
 * 因此每条语句都<b>显式</b>书写 {@code tenant_oid}。
 */
public interface ChangeRequestAffectedObjectMapper {

    int insert(ChangeRequestAffectedObject row);

    /** 整单替换时先清空（仅草稿可改，服务层保证） */
    int deleteByRequestOid(String requestOid, String tenantOid);

    int deleteByOid(String oid, String tenantOid);

    ChangeRequestAffectedObject selectByOid(String oid, String tenantOid);

    /** 某张变更请求下的预估对象（按创建时间正序，带容器名） */
    List<ChangeRequestAffectedObject> selectByRequest(String requestOid, String tenantOid);

    /** 同一请求里该对象是否已存在（服务层前置校验，避免撞唯一索引） */
    int countByRequestAndEntity(String requestOid, String entityType, String entityOid, String tenantOid);
}
