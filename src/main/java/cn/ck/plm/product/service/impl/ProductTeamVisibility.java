/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.UserMapper;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.mapper.ProductLineMapper;
import cn.ck.plm.product.mapper.TeamMemberMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产品系列 / 型号的团队可见性（2026-10-12 新增）。
 *
 * <h3>需求（用户口径）</h3>
 * "如果用户不在系列/型号的团队中，不应该可见对应的系列和型号记录" —— 列表 / 树 / 下拉
 * 全部按此过滤（同一批接口，前端无需改动）。
 *
 * <h3>可见性规则</h3>
 * <ol>
 *   <li><b>管理员豁免</b>：{@code PLATFORM_ADMIN / TENANT_ADMIN / ADMIN} 看全部 ——
 *       平台与企业管理员的工作就是管理这些对象，把他们一起挡掉会让"没人能加成员"死锁。
 *       （普通用户是否管理员由角色码判定；取不到用户/角色时按<b>非管理员</b>处理 —— 宁严勿松。）</li>
 *   <li><b>系列</b>：我是该系列团队的成员 → 可见。</li>
 *   <li><b>型号</b>：我是该型号团队的成员，<b>或</b>我是其父系列 / 祖先系列的团队成员 → 可见
 *       （与 {@code ProductModelServiceImpl#getTeamMembers} 的"继承父级团队"口径一致）。</li>
 *   <li><b>祖先补齐（仅用于树）</b>：子系列可见但父系列团队里没有我时，父系列仍要出现在
 *       {@link #visibleLineOids()} 里 —— 否则树/列表里冒出一个"没有父节点"的孤儿，导航会断。</li>
 *   <li><b>空团队</b>：团队里没有我 → 不可见（严格口径，与用户要求一致）。创建者会自动入团队
 *       （见 ProductModelServiceImpl#ensureTeam），所以正常创建的系列/型号不会因为空团队而消失。</li>
 * </ol>
 *
 * <h3>为什么放在这里做</h3>
 * 过滤发生在 Service 读方法上（findAll / search / findTree / 型号列表），而不是各 Controller ——
 * 这样"谁能看到什么"只有一个真源；下游（阶段页、下拉、树）只要走同一批接口就自动一致。
 */
@Component
public class ProductTeamVisibility {

    private static final Logger log = LoggerFactory.getLogger(ProductTeamVisibility.class);

    /** 管理员角色码（历史 ADMIN 一并认下） */
    private static final Set<String> ADMIN_ROLES = Set.of("PLATFORM_ADMIN", "TENANT_ADMIN", "ADMIN");

    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final ProductLineMapper productLineMapper;

    public ProductTeamVisibility(TeamMemberMapper teamMemberMapper,
                                 UserMapper userMapper,
                                 UserService userService,
                                 ProductLineMapper productLineMapper) {
        this.teamMemberMapper = teamMemberMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.productLineMapper = productLineMapper;
    }

    // ==================== 基础判定 ====================

    /** 当前用户是否管理员（读不到用户 / 角色异常一律按非管理员处理） */
    public boolean isAdmin() {
        try {
            String username = UserContext.get();
            if (username == null || username.trim().isEmpty()) {
                return false;
            }
            User user = userMapper.selectByUsername(username.trim());
            if (user == null) {
                return false;
            }
            List<Role> roles = userService.getUserRoles(user.getOid());
            for (Role role : roles == null ? List.<Role>of() : roles) {
                if (role.getCode() != null && ADMIN_ROLES.contains(role.getCode())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("判定管理员角色失败（按非管理员处理）: {}", e.getMessage());
        }
        return false;
    }

    /** 当前用户作为成员加入的团队 oid 集合（未登录返回空集） */
    public Set<String> myTeamOids() {
        Set<String> result = new HashSet<>();
        try {
            String username = UserContext.get();
            if (username == null || username.trim().isEmpty()) {
                return result;
            }
            List<String> teams = teamMemberMapper.selectTeamOidsByUserId(username.trim());
            if (teams != null) {
                result.addAll(teams);
            }
        } catch (Exception e) {
            log.warn("查询我的团队失败（按无团队处理）: {}", e.getMessage());
        }
        return result;
    }

    // ==================== 系列 ====================

    /**
     * 可见的系列 oid 集合；返回 {@code null} 表示不限制（管理员）。
     *
     * <p>含两部分：团队命中的系列 + 它们的全部祖先（树完整性，见类注释第 4 条）。
     */
    public Set<String> visibleLineOids() {
        if (isAdmin()) {
            return null;
        }
        Set<String> myTeams = myTeamOids();
        Set<String> visible = new LinkedHashSet<>();
        List<ProductLine> all = safeSelectAllLines();
        Map<String, ProductLine> byOid = new HashMap<>();
        for (ProductLine pl : all) {
            byOid.put(pl.getOid(), pl);
        }
        // 1) 团队命中
        for (ProductLine pl : all) {
            if (pl.getTeamOid() != null && myTeams.contains(pl.getTeamOid())) {
                visible.add(pl.getOid());
            }
        }
        // 2) 祖先补齐（成员命中的节点往上挂，避免树断裂）
        for (String oid : new ArrayList<>(visible)) {
            ProductLine current = byOid.get(oid);
            int guard = 0;
            while (current != null && current.getParentOid() != null && guard++ < 20) {
                ProductLine parent = byOid.get(current.getParentOid());
                if (parent == null || !visible.add(parent.getOid())) {
                    break;
                }
                current = parent;
            }
        }
        return visible;
    }

    /** 过滤系列列表（管理员原样返回） */
    public List<ProductLine> filterLines(List<ProductLine> lines) {
        Set<String> visible = visibleLineOids();
        if (visible == null || lines == null) {
            return lines;
        }
        List<ProductLine> result = new ArrayList<>();
        for (ProductLine pl : lines) {
            if (visible.contains(pl.getOid())) {
                result.add(pl);
            }
        }
        return result;
    }

    // ==================== 型号 ====================

    /** 过滤型号列表：我是型号团队 / 父系列（或祖先系列）团队的成员才可见（管理员原样返回） */
    public List<ProductModel> filterModels(List<ProductModel> models) {
        if (isAdmin() || models == null || models.isEmpty()) {
            return models;
        }
        Set<String> myTeams = myTeamOids();
        Map<String, ProductLine> lineCache = new HashMap<>();
        List<ProductModel> result = new ArrayList<>();
        for (ProductModel model : models) {
            if (model.getTeamOid() != null && myTeams.contains(model.getTeamOid())) {
                result.add(model); // 我是本型号团队成员
                continue;
            }
            if (canSeeByParentLine(model.getParentOid(), myTeams, lineCache)) {
                result.add(model); // 继承父系列 / 祖先系列的团队
            }
        }
        return result;
    }

    private boolean canSeeByParentLine(String parentLineOid, Set<String> myTeams, Map<String, ProductLine> cache) {
        String cursor = parentLineOid;
        int guard = 0;
        while (cursor != null && guard++ < 20) {
            ProductLine line = cache.computeIfAbsent(cursor, oid -> productLineMapper.selectByOid(oid));
            if (line == null) {
                return false;
            }
            if (line.getTeamOid() != null && myTeams.contains(line.getTeamOid())) {
                return true;
            }
            cursor = line.getParentOid();
        }
        return false;
    }

    private List<ProductLine> safeSelectAllLines() {
        try {
            List<ProductLine> all = productLineMapper.selectAll();
            return all == null ? List.of() : all;
        } catch (Exception e) {
            log.warn("读取产品系列全集失败（按空处理）: {}", e.getMessage());
            return List.of();
        }
    }
}
