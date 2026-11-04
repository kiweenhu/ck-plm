/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.change.entity.ChangeConfig;
import cn.ck.plm.change.service.api.ChangeConfigService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 变更租户配置 REST 控制器（免审开关）。
 *
 * <pre>
 * GET /api/plm/change-config   当前租户配置（未配置时返回默认值，不落库）
 * PUT /api/plm/change-config   { approvalRequired, bypassForReleased, requestEnabled,
 *                                requestProcessKey, approvalProcessKey, taskProcessKey }
 * </pre>
 *
 * <p>三个流程 key 对应三类单据（ECR / ECN / ECT），各自可改 —— 流程模板可被用户在设计器里
 * 自定义，改完之后不一定仍是内置的父子链，按单据分开配才说得清。当前只有变更通告（ECN）
 * 的 key 真正被消费（{@code ChangeNoticeServiceImpl} 的「发起审批」）。
 *
 * <p>默认 {@code approvalRequired = true}：免审必须显式开启，并会写入每条 ECN 的审计字段。
 *
 * <h3>读开放、写限管理员</h3>
 * <p>这是一条**租户级策略**（要不要审批、免审能否改已发布对象），改它等于替全租户做主，
 * 故 PUT 只允许租户管理员。GET 则必须对所有登录用户开放：变更通告面板要靠
 * {@code approvalRequired} 决定提交前的提示语与"免审原因"是否必填 —— 若一并收紧，
 * 普通成员会读到默认值 true，在已开免审的租户里看到错误的"将进入审批"提示。
 */
@RestController
@RequestMapping("/api/plm/change-config")
public class ChangeConfigController {

    private static final Logger log = LoggerFactory.getLogger(ChangeConfigController.class);

    /**
     * 能修改租户级策略的角色 —— 口径与前端 MainLayout 的「租户管理员」一致
     * （TENANT_ADMIN / ADMIN / PLATFORM_ADMIN）。
     */
    private static final String ADMIN_ROLE_FILTER = "('TENANT_ADMIN', 'ADMIN', 'PLATFORM_ADMIN')";

    private final ChangeConfigService service;
    private final JdbcTemplate jdbcTemplate;

    public ChangeConfigController(ChangeConfigService service, JdbcTemplate jdbcTemplate) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<ChangeConfig> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<ChangeConfig> update(@RequestBody(required = false) Map<String, Object> body) {
        if (!isTenantAdmin(UserContext.get())) {
            return ApiResponse.fail(403, "只有租户管理员可以修改变更设置");
        }
        Map<String, Object> params = body == null ? Map.of() : body;
        try {
            return ApiResponse.ok(service.update(
                    asBoolean(params.get("approvalRequired")),
                    asBoolean(params.get("bypassForReleased")),
                    asString(params.get("requestProcessKey")),
                    asString(params.get("approvalProcessKey")),
                    asString(params.get("taskProcessKey")),
                    asBoolean(params.get("requestEnabled"))));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 当前用户是否本租户的管理员。
     *
     * <p><b>为什么用 JDBC 而不是 Mapper</b>：这里要 JOIN 三张表，而 MyBatis 的租户拦截器会改写
     * JOIN 上的租户条件，口径容易与本意漂移 —— 与 {@code OnboardingController#isTenantAdmin}
     * 同一做法。角色按租户隔离（{@code role.code} 各租户各一份），故必须限定
     * {@code r.tenant_oid = rm.tenant_oid}，否则会命中别家租户的同名角色副本。
     */
    private boolean isTenantAdmin(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ck_role_member rm "
                            + "INNER JOIN ck_role r ON r.oid = rm.role_oid "
                            + "INNER JOIN ck_user u ON u.oid = rm.user_oid "
                            + "WHERE u.username = ? AND r.code IN " + ADMIN_ROLE_FILTER
                            + " AND r.tenant_oid = rm.tenant_oid",
                    Integer.class, username);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("租户管理员校验失败（按无权限处理）: username={}, {}", username, e.getMessage());
            return false;
        }
    }

    /** 只认真正的布尔值：字符串 "true" 不做隐式转换，避免前端传错类型被静默吞掉 */
    private static Boolean asBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        throw new IllegalArgumentException("配置项必须是布尔值：" + value);
    }

    /** 流程 key：{@code null} = 本次不改（与空串"改回默认"是两回事，归一由服务层做） */
    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
