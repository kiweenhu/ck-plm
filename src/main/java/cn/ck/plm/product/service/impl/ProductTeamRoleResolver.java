/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.entity.TeamMember;
import cn.ck.plm.product.mapper.ProductLineMapper;
import cn.ck.plm.product.mapper.ProductModelMapper;
import cn.ck.plm.product.mapper.TeamMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「我在这个系列 / 型号里的团队角色」解析器（2026-10-14）。
 *
 * <h3>为什么需要它</h3>
 * <p>用户要求："普通用户进来后，根据用户在系列/型号下的<b>角色</b>来判断是否可以具备
 * 关联交付项、发起流程的权限"。此前这些动作的权限判的是<b>全局角色</b>
 * （{@code ck_role_member} 里的 PRODUCT_MANAGER / TENANT_ADMIN）—— 但一个平台管理员
 * 可能在 A 系列是产品经理、在 B 系列什么都不是；权限本就该是**项目级**的。
 *
 * <h3>规则</h3>
 * <ol>
 *   <li><b>角色来源</b>：{@code ck_team_member}（{@code user_id} = 用户名，{@code role_name} = 中文角色名），
 *       团队由 {@code ck_product_line.team_oid} / {@code ck_product_model.team_oid} 指向；</li>
 *   <li><b>型号继承</b>：型号团队 + 逐级父系列团队（与 {@link ProductTeamVisibility} 同一口径；</li>
 *   <li><b>两套码对齐</b>：团队角色是中文名（如「产品经理」），交付件的责任人是英文码
 *       （如 {@code PRODUCT_MANAGER}）—— 用 {@code ck_role}（code ↔ name）作桥；
 *       两边都对得上才算命中（名字直接相等也算，容错）；</li>
 *   <li><b>管理员豁免</b>：{@code PLATFORM_ADMIN / TENANT_ADMIN / ADMIN} 一律可做
 *       （复用 {@link ProductTeamVisibility#isAdmin()}）；</li>
 *   <li><b>宁严勿松</b>：用户取不到 / 团队查不到 / 角色映射失败，一律按"没有该权限"处理。</li>
 * </ol>
 *
 * <h3>三件事的角色口径（用户选定）</h3>
 * <ul>
 *   <li>裁剪（标记不适用 / 改强制 / 改责任人）：团队角色 = <b>产品经理</b>；</li>
 *   <li>关联交付项：<b>产品经理</b>，或我的团队角色 = 该交付件的<b>责任人</b>（谁负责谁关联）；</li>
 *   <li>发起流程（阶段门 / 评审）：<b>产品经理 / 项目经理 / IPDT 组长</b>。</li>
 * </ul>
 */
@Component
public class ProductTeamRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(ProductTeamRoleResolver.class);

    /** 可裁剪的团队角色 */
    private static final Set<String> TAILOR_ROLES = Set.of("PRODUCT_MANAGER");
    /**
     * 可发起流程（阶段门 / 评审）的团队角色。
     *
     * <p>{@code PM} 与 {@code PROJECT_MANAGER} 都要写：{@code ck_role} 里同名「项目经理」
     * 存在两条不同编码的记录，name → code 映射取到哪一条取决于查询顺序（2026-11-01 实测取到 {@code PM}），
     * 只写 {@code PROJECT_MANAGER} 时项目经理会被判成"没有发起权"。
     */
    private static final Set<String> START_ROLES =
            Set.of("PRODUCT_MANAGER", "PROJECT_MANAGER", "PM", "IPDT_LEADER");
    /**
     * 可查看「本项目不适用」（被裁剪）条目的团队角色（2026-11-01 用户要求：
     * 「产品经理和项目经理，以及租户管理员」）。
     *
     * <p>只看不改：裁剪（标记 / 恢复 / 改强制）仍限产品经理 —— 项目经理需要知道哪些交付件本项目不做，
     * 但不该替产品经理改口径。
     *
     * <p>{@code PM} 的理由同 {@link #START_ROLES}：库里有两条「项目经理」角色记录。
     */
    private static final Set<String> TAILORED_VIEW_ROLES =
            Set.of("PRODUCT_MANAGER", "PROJECT_MANAGER", "PM");

    private final TeamMemberMapper teamMemberMapper;
    private final ProductLineMapper productLineMapper;
    private final ProductModelMapper productModelMapper;
    private final RoleMapper roleMapper;
    private final ProductTeamVisibility teamVisibility;

    public ProductTeamRoleResolver(TeamMemberMapper teamMemberMapper,
                                   ProductLineMapper productLineMapper,
                                   ProductModelMapper productModelMapper,
                                   RoleMapper roleMapper,
                                   ProductTeamVisibility teamVisibility) {
        this.teamMemberMapper = teamMemberMapper;
        this.productLineMapper = productLineMapper;
        this.productModelMapper = productModelMapper;
        this.roleMapper = roleMapper;
        this.teamVisibility = teamVisibility;
    }

    // ==================== 基础查询 ====================

    /** 当前用户是否管理员（豁免一切项目级权限判定） */
    public boolean isAdmin() {
        return teamVisibility.isAdmin();
    }

    /**
     * 该 oid 是不是产品系列 / 型号（"项目对象"）。
     *
     * <p>分流用：只有项目对象的动作（裁剪 / 发起门与评审 / 关联交付项）才做项目级权限判定；
     * 器件、文档等其它业务对象走进来时不该被"你不在团队里"误拦。
     */
    public boolean isProductOwner(String ownerOid) {
        if (ownerOid == null || ownerOid.trim().isEmpty()) {
            return false;
        }
        String oid = ownerOid.trim();
        return safeModel(oid) != null || safeLine(oid) != null;
    }

    /** 当前用户名（未登录 / 取不到返回 null） */
    private String currentUsername() {
        String username = UserContext.get();
        return username == null || username.trim().isEmpty() ? null : username.trim();
    }

    /**
     * 我在该归属单元（系列 / 型号）下的团队角色**中文名**集合（未登录 / 不在团队 → 空集）。
     */
    public Set<String> myRoleNamesUnder(String ownerOid) {
        String username = currentUsername();
        if (username == null || ownerOid == null || ownerOid.trim().isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String teamOid : teamOidsOfOwner(ownerOid.trim())) {
            for (TeamMember member : safeMembers(teamOid)) {
                if (member != null && username.equals(member.getUserId())
                        && member.getRoleName() != null && !member.getRoleName().trim().isEmpty()) {
                    names.add(member.getRoleName().trim());
                }
            }
        }
        return names;
    }

    /**
     * 我在该归属单元下的团队角色**编码**集合（中文名经 {@code ck_role} 映射；映射不到的丢弃）。
     */
    public Set<String> myRoleCodesUnder(String ownerOid) {
        Set<String> names = myRoleNamesUnder(ownerOid);
        if (names.isEmpty()) {
            return Set.of();
        }
        Map<String, String> nameToCode = roleNameToCode();
        Set<String> codes = new LinkedHashSet<>();
        for (String name : names) {
            String code = nameToCode.get(name);
            if (code != null && !code.isEmpty()) {
                codes.add(code);
            }
        }
        return codes;
    }

    // ==================== 三件事的权限判定 ====================

    /** 裁剪：团队角色 = 产品经理（或管理员） */
    public boolean canTailorUnder(String ownerOid) {
        if (isAdmin()) {
            return true;
        }
        return !Collections.disjoint(myRoleCodesUnder(ownerOid), TAILOR_ROLES);
    }

    /**
     * 查看「本项目不适用」的条目：产品经理 / 项目经理（或管理员）。
     *
     * <p>与 {@link #canTailorUnder(String)} 分开：可看 ≠ 可改。前端据此决定
     * 列表是否带出被裁剪行、"适宜 / 不适宜 / 所有"过滤是否出现。
     */
    public boolean canViewTailoredUnder(String ownerOid) {
        if (isAdmin()) {
            return true;
        }
        return !Collections.disjoint(myRoleCodesUnder(ownerOid), TAILORED_VIEW_ROLES);
    }

    /** 发起流程（阶段门 / 评审）：产品经理 / 项目经理 / IPDT 组长（或管理员） */
    public boolean canStartProcessUnder(String ownerOid) {
        if (isAdmin()) {
            return true;
        }
        return !Collections.disjoint(myRoleCodesUnder(ownerOid), START_ROLES);
    }

    /**
     * 维护系列 / 型号本体（阶段 / 团队 / 编辑 / 删除）：团队角色 = 产品经理（或管理员）。
     *
     * <p>2026-10-17 用户要求："只有产品经理具备系列/型号的阶段、团队、编辑和删除权限"。
     * 判定与 {@link #canTailorUnder(String)} 同一口径（产品经理 + 管理员豁免），
     * 单独命名是为了让调用点语义可读 —— 这里维护的是项目对象本身，不是方法论实例。
     */
    public boolean canManageUnder(String ownerOid) {
        return canTailorUnder(ownerOid);
    }

    /**
     * 关联交付项：产品经理，或我的团队角色 = 该交付件的责任人。
     *
     * <p>两套码对齐后再比：我的中文角色名会先映射成 code（如「产品经理」→ {@code PRODUCT_MANAGER}），
     * 与交付件的 {@code owner_role} 比；名字直接相等也算命中（容错那些直接写中文的存量数据）。
     */
    public boolean canLinkArtifactUnder(String ownerOid, String expectedRole) {
        if (isAdmin() || canTailorUnder(ownerOid)) {
            return true;
        }
        if (expectedRole == null || expectedRole.trim().isEmpty()) {
            // 没写责任人 = 没人负责 → 只有产品经理/管理员能关联（上面已判过）
            return false;
        }
        String want = expectedRole.trim();
        Set<String> names = myRoleNamesUnder(ownerOid);
        if (names.isEmpty()) {
            return false;
        }
        return names.contains(want) || myRoleCodesUnder(ownerOid).contains(want);
    }

    // ==================== 内部 ====================

    /**
     * 归属单元（系列 / 型号）关联的团队 oid 集合。
     *
     * <p>型号：本型号团队 + 逐级父系列团队（继承口径与 {@link ProductTeamVisibility} 一致）；
     * 系列：本系列团队（父系列不参与 —— 与可见性规则对齐）。
     */
    private Set<String> teamOidsOfOwner(String ownerOid) {
        Set<String> result = new LinkedHashSet<>();
        ProductModel model = safeModel(ownerOid);
        if (model != null) {
            if (model.getTeamOid() != null) {
                result.add(model.getTeamOid());
            }
            String cursor = model.getParentOid();
            int guard = 0;
            while (cursor != null && guard++ < 20) {
                ProductLine line = safeLine(cursor);
                if (line == null) {
                    break;
                }
                if (line.getTeamOid() != null) {
                    result.add(line.getTeamOid());
                }
                cursor = line.getParentOid();
            }
            return result;
        }
        ProductLine line = safeLine(ownerOid);
        if (line != null && line.getTeamOid() != null) {
            result.add(line.getTeamOid());
        }
        return result;
    }

    /** {@code ck_role} 的 name → code 映射（取不到返回空表，调用方按"映射不到"处理） */
    private Map<String, String> roleNameToCode() {
        Map<String, String> map = new HashMap<>();
        try {
            List<Role> roles = roleMapper.selectAll();
            for (Role role : roles == null ? List.<Role>of() : roles) {
                if (role != null && role.getName() != null && role.getCode() != null) {
                    map.putIfAbsent(role.getName().trim(), role.getCode());
                }
            }
        } catch (Exception e) {
            log.warn("读取角色表失败（角色名→码映射为空）: {}", e.getMessage());
        }
        return map;
    }

    private List<TeamMember> safeMembers(String teamOid) {
        try {
            List<TeamMember> list = teamMemberMapper.selectByTeamOid(teamOid);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("查询团队成员失败（按无成员处理）: team={} err={}", teamOid, e.getMessage());
            return List.of();
        }
    }

    private ProductModel safeModel(String oid) {
        try {
            return productModelMapper.selectByOid(oid);
        } catch (Exception e) {
            return null;
        }
    }

    private ProductLine safeLine(String oid) {
        try {
            return productLineMapper.selectByOid(oid);
        } catch (Exception e) {
            return null;
        }
    }
}
