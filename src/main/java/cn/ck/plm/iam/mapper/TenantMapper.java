/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.Tenant;
import java.util.List;

/**
 * 租户数据访问接口。
 */
public interface TenantMapper {

    int insert(Tenant tenant);

    int update(Tenant tenant);

    /**
     * 只更新企业基本信息（名称 / 联系人 / 联系邮箱），不触碰状态、审核与行业引导字段。
     *
     * <p>{@link #update(Tenant)} 是审核与引导流程专用的，刻意不含这三列；企业信息编辑走本方法，
     * 避免把行业、onboarding 等状态用"只带三个字段的对象"覆盖成空值。
     */
    int updateProfile(Tenant tenant);

    Tenant selectByOid(String oid);

    Tenant selectByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    List<Tenant> selectPending();

    int countPending();

    /** 查询所有已激活的租户 */
    List<Tenant> selectActive();

    /**
     * 已审核过的租户（审核台账）。
     *
     * <p>刻意排除平台租户与默认租户：它们是系统内置的，从来没有"注册申请 → 审核"这回事，
     * 混进台账只会让人以为平台租户也被谁批过（{@code approved_by} 还是空的）。
     */
    List<Tenant> selectReviewed();
}
