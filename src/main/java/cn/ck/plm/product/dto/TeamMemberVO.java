/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.dto;

/**
 * 团队成员视图对象，合并 {@link cn.ck.plm.iam.entity.User} 基础信息
 * 与 {@link cn.ck.plm.product.entity.TeamMember} 中的角色名称。
 */
public class TeamMemberVO {

    /** 用户名 */
    private String username;
    /** 显示名 */
    private String displayName;
    /** 邮箱 */
    private String email;
    /** 是否启用 */
    private boolean enabled = true;
    /** 是否锁定 */
    private boolean locked;
    /** 团队角色名称 */
    private String roleName;
    /** 是否从父级产品线继承 */
    private boolean inherited;
    /** 继承来源产品线名称（仅 inherited=true 时有值） */
    private String sourceProductLineName;

    public TeamMemberVO() {}

    // ==================== Getter/Setter ====================

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public boolean isInherited() { return inherited; }
    public void setInherited(boolean inherited) { this.inherited = inherited; }

    public String getSourceProductLineName() { return sourceProductLineName; }
    public void setSourceProductLineName(String sourceProductLineName) { this.sourceProductLineName = sourceProductLineName; }
}
