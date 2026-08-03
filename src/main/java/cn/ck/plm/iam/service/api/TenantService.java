/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.api;

import cn.ck.plm.iam.dto.TenantRegistrationRequest;
import cn.ck.plm.iam.entity.Tenant;
import java.util.List;

/**
 * 租户管理服务接口。
 */
public interface TenantService {

    /**
     * 提交租户注册申请 —— 状态为 PENDING，需管理员审核。
     */
    void register(TenantRegistrationRequest request);

    /** 查询待审核租户列表 */
    List<Tenant> listPending();

    /** 待审核数量 */
    int countPending();

    /**
     * 审核通过 —— 激活租户，创建管理员账号并分配 ADMIN 角色。
     *
     * @param tenantOid 租户 oid
     * @param approvedBy 审核人用户名
     */
    void approve(String tenantOid, String approvedBy);

    /**
     * 驳回申请。
     *
     * @param tenantOid 租户 oid
     * @param rejectedBy 驳回人用户名
     * @param reason 驳回原因
     */
    void reject(String tenantOid, String rejectedBy, String reason);

    /**
     * 根据 oid 获取租户信息。
     */
    Tenant getByOid(String oid);

    /**
     * 根据 tenantId（业务标识）获取租户信息。
     */
    Tenant getByTenantId(String tenantId);

    /** 查询所有已激活的租户 */
    List<Tenant> listActive();
}
