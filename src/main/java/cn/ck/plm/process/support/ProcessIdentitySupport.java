/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流程身份与租户映射支撑 —— 在 CK-PLM 的用户体系与 Flowable 之间做转换。
 *
 * <h3>为什么需要</h3>
 * <p>本项目<b>不启用 Flowable 的 IDM 引擎</b>（用户/角色已有 {@code ck_user} / {@code ck_role}），
 * 因此 Flowable 的 {@code assignee} / {@code candidateGroups} 需要用本项目既有的标识填充：
 *
 * <pre>
 * Flowable userId   ←  ck_user.username      （登录名，全局唯一，天然适合作为引擎用户标识）
 * Flowable groupId  ←  ck_role.code          （角色编码，BPMN 中 flowable:candidateGroups="manager"）
 * Flowable tenantId ←  TenantContext.get()   （即 token 中携带的租户 oid）
 * </pre>
 *
 * <p>所有 Flowable 调用点都必须显式传入 {@code tenantId}——Flowable 的租户是「标记 + 显式查询条件」，
 * 不会自动按租户过滤数据。
 *
 * <p><b>人员标识可能有两种</b>：注入用户名（{@code ${initiator}} 求值结果、认领/转办写入的都是用户名）
 * 与人员 oid（「设置审批人」按 oid 指派下游）。所以查"我的任务"要用
 * {@link #currentUserIdentifiers()}，把两种都算上 —— 详见该方法的说明。
 */
@Component
public class ProcessIdentitySupport {

    private static final Logger log = LoggerFactory.getLogger(ProcessIdentitySupport.class);

    private final UserService userService;

    public ProcessIdentitySupport(UserService userService) {
        this.userService = userService;
    }

    /** 当前登录用户（用于 taskAssignee 查询、claim/complete 等写操作） */
    public String currentUserId() {
        return UserContext.get();
    }

    /**
     * 当前用户在 Flowable 里<b>可能出现的全部标识</b>：用户名 + 人员 oid。
     *
     * <p><b>为什么两种都要认</b>：本类的口径是 {@code Flowable userId = ck_user.username}，
     * 但「设置审批人」活动是<b>按人员 oid</b> 指派下游的（见 {@code ProcessActivityVO} 的说明），
     * 历史数据里 assignee 确实存的是 oid。只按用户名去查，这类任务在待办里
     * <b>压根不出现</b>、也不能认领 —— 任务就那么卡着，谁都看不见（现象就是"我没收到待办"）。
     *
     * <p>写入侧已按口径统一为用户名（前端选人选项的 value 改回 username），
     * 这里是给历史数据与仍在跑的实例兜底，让两种标识都能被认出来。
     *
     * @return 至少含用户名；能查到用户时再补一个 oid（去重、去空）
     */
    public List<String> currentUserIdentifiers() {
        String username = currentUserId();
        List<String> identifiers = new ArrayList<>(2);
        if (username != null && !username.trim().isEmpty()) {
            identifiers.add(username.trim());
        }
        try {
            User user = userService.findByUsername(username);
            if (user != null && user.getOid() != null && !user.getOid().trim().isEmpty()
                    && !identifiers.contains(user.getOid().trim())) {
                identifiers.add(user.getOid().trim());
            }
        } catch (Exception e) {
            // 查不到 oid 不影响按用户名匹配
            log.debug("流程用户标识解析失败 username={}: {}", username, e.getMessage());
        }
        return identifiers;
    }

    /**
     * 由 Flowable 里的人员标识（用户名 <b>或</b> 人员 oid）找到用户。
     *
     * <p>通知、展示名这类"要把标识还原成人"的地方共用一处：两种标识各试一次，
     * 免得每个调用点各写一遍、各漏一种。
     *
     * @return 用户；识别不出时返回 null
     */
    public User findUserByFlowableId(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return null;
        }
        String value = identifier.trim();
        try {
            User user = userService.findByOid(value);
            if (user == null) {
                user = userService.findByUsername(value);
            }
            return user;
        } catch (Exception e) {
            log.debug("解析流程人员标识失败 value={}: {}", value, e.getMessage());
            return null;
        }
    }

    /** 当前租户 oid（用于 Flowable tenantId） */
    public String currentTenantId() {
        return TenantContext.get();
    }

    /**
     * 当前用户所属的角色编码列表 —— 作为 Flowable 的候选组（candidate group）。
     *
     * <p>用于查询「可认领任务」与「待办任务」中的候选任务：
     * {@code taskCandidateUser(user, groups)}。
     */
    public List<String> currentUserGroups() {
        return userGroups(currentUserId());
    }

    /**
     * 查询指定用户名的角色编码列表。
     *
     * @param username 登录名（对应 ck_user.username）
     * @return 角色编码列表；用户不存在或无角色时返回空列表
     */
    public List<String> userGroups(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            User user = userService.findByUsername(username.trim());
            if (user == null) {
                log.debug("流程组解析：用户不存在 username={}", username);
                return Collections.emptyList();
            }
            List<Role> roles = userService.getUserRoles(user.getOid());
            if (roles == null || roles.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> codes = new ArrayList<>(roles.size());
            for (Role role : roles) {
                if (role != null && role.getCode() != null && !role.getCode().trim().isEmpty()) {
                    codes.add(role.getCode().trim());
                }
            }
            return codes;
        } catch (Exception e) {
            // 身份解析失败不应阻断待办查询；退化为「无候选组」
            log.warn("流程组解析失败 username={}: {}", username, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 校验用户名是否存在（委派 / 转办时防止填错人）。
     *
     * @throws IllegalArgumentException 用户不存在
     */
    public String requireExistingUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("目标用户不能为空");
        }
        String name = username.trim();
        if (userService.findByUsername(name) == null) {
            throw new IllegalArgumentException("用户不存在: " + name);
        }
        return name;
    }
}
