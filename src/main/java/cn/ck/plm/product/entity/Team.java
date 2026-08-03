/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

/**
 * 团队实体（Team），无需版本控制，继承 {@link WithoutVersionEntity}。
 *
 * <p>Team 承载多个角色及角色对应的成员信息，一条 Team 对应多条 {@link TeamMember}。
 * 产品系列通过 teamOid 关联团队，实现产品线的团队管理。
 *
 * <h3>关系</h3>
 * <pre>
 * ProductLine  1 ── 1  Team        (通过 teamOid 关联)
 * Team         1 ── N  TeamMember  (角色-成员映射)
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Team team = new Team();
 * team.setCode("TEAM-001");
 * team.setName("产品A组");
 * }</pre>
 */
public class Team extends WithoutVersionEntity implements TenantEntity {

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Team() {
        super();
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== toString ====================

    @Override
    public String toString() {
        return "Team{code='" + getCode() + "', name='" + getName() + "'}";
    }
}
