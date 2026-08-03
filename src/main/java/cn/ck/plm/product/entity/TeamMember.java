/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 团队成员实体（TeamMember），每条记录代表一个角色-成员映射。
 *
 * <p>一条 {@link Team} 包含多个 TeamMember，每个 TeamMember 指定角色及对应的
 * 用户。同一用户可在同一团队中担任多个角色。
 *
 * <h3>关系</h3>
 * <pre>
 * Team  1 ── N  TeamMember  (通过 teamOid 关联)
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * TeamMember member = new TeamMember();
 * member.setTeamOid(team.getOid());
 * member.setRoleName("产品经理");
 * member.setUserId("zhangsan");
 * }</pre>
 */
public class TeamMember extends BaseEntity implements TenantEntity {

    /** 关联团队 oid */
    private String teamOid;

    /** 角色名称（如 产品经理、设计负责人、开发工程师） */
    private String roleName;

    /** 成员用户ID */
    private String userId;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public TeamMember() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getTeamOid() { return teamOid; }
    public void setTeamOid(String teamOid) { this.teamOid = teamOid; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "TeamMember{teamOid='" + teamOid + "', role='" + roleName
                + "', user='" + userId + "'}";
    }
}
