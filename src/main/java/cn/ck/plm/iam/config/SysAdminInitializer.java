/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.config;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.service.api.UserService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 应用启动时初始化平台级角色及平台管理员账号到数据库。
 * 仅在数据不存在时插入，已存在的账号和角色不会覆盖。
 *
 * <p>平台级角色（role_type='PLATFORM'）：
 * <ul>
 *   <li>PLATFORM_ADMIN — 平台管理员，负责审核租户注册</li>
 *   <li>ADMIN — 系统管理员</li>
 *   <li>AUDIT_ADMIN — 审计管理员</li>
 *   <li>SECURITY_ADMIN — 安全管理员</li>
 * </ul>
 * 平台级角色不可通过前端编辑或删除。
 *
 * <p>密码通过 {@link cn.ck.plm.iam.util.PasswordEncoder} 加密存储。
 * 审计字段（creator/createdAt）由 {@link cn.ck.plm.base.config.AuditInterceptor} 自动填充，
 * 启动阶段无登录用户，creator 为 null。
 */
@Component
public class SysAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SysAdminInitializer.class);

    private static final String SYSADMIN_USERNAME = "sysadmin";
    private static final String SYSADMIN_PASSWORD = "sysadmin";
    private static final String PLATFORM_ADMIN_ROLE_CODE = "PLATFORM_ADMIN";
    /** 平台管理员角色名称 */
    public static final String PLATFORM_ADMIN_ROLE_NAME = "平台管理员";

    private final UserService userService;
    private final RoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;

    public SysAdminInitializer(UserService userService, RoleMapper roleMapper, JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.roleMapper = roleMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("开始初始化平台级角色及平台管理员账号...");

        // 0. 先确保所有业务表都有 tenant_oid 列（在任何 MyBatis 操作之前，用 JDBC 直接执行）
        ensureTenantOidColumnsExist();

        // 初始化阶段不设置 TenantContext（null），让 TenantStatementInterceptor 跳过 SQL 改写
        try {
            // 1. 初始化平台级角色
            initPlatformRole("PLATFORM_ADMIN",  "平台管理员", "平台超级管理员，负责审核租户注册申请");
            initPlatformRole("TENANT_ADMIN",    "租户管理员", "租户级管理员，管理本租户的用户、角色、部门等（等同于系统管理员）");
            initPlatformRole("CATEGORY_ADMIN",  "分类管理员", "流程分类管理员，负责创建和管理流程分类");
            initPlatformRole("AUDIT_ADMIN",     "审计管理员", "负责系统操作日志审计与合规检查");
            initPlatformRole("SECURITY_ADMIN",  "安全管理员", "负责系统安全策略、权限分配与风险管控");

            // 2. sysadmin 用户初始化
            User sysadmin = userService.findByUsername(SYSADMIN_USERNAME);
            if (sysadmin != null) {
                sysadmin.setDisplayName("平台管理员");
                sysadmin.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                userService.update(sysadmin);
                userService.resetPassword(sysadmin.getOid(), SYSADMIN_PASSWORD);
                log.info("  sysadmin 密码已重置为: sysadmin");
                ensureRole(sysadmin, PLATFORM_ADMIN_ROLE_CODE);
            } else {
                sysadmin = new User(SYSADMIN_USERNAME, "平台管理员");
                sysadmin.setEmail("sysadmin@ck-plm.com");
                sysadmin.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                userService.create(sysadmin, SYSADMIN_PASSWORD);
                Role platformAdminRole = roleMapper.selectByCode(PLATFORM_ADMIN_ROLE_CODE);
                if (platformAdminRole != null) {
                    userService.assignRole(sysadmin.getOid(), platformAdminRole.getOid());
                }
                log.info("  初始化用户: sysadmin (已分配平台管理员角色)");
            }
        } finally {
            TenantContext.clear();
        }

        // 3. 确保平台角色和角色成员记录的 tenant_oid 正确（绕过 MyBatis 拦截器直接用 JDBC）
        fixTenantOidForPlatformData();

        log.info("平台级角色及平台管理员初始化完成");
    }

    /**
     * 直接通过 JDBC 修正平台级角色和角色成员的 tenant_oid，
     * 避免多租户拦截器导致 tenant_oid 为 NULL 或默认值。
     */
    private void ensureTenantOidColumnsExist() {
        String[] tables = {"ck_role", "ck_role_member", "ck_user", "ck_token",
                "ck_organization", "ck_product_line", "ck_product_model", "ck_stage",
                "ck_folder", "ck_team", "ck_team_member", "ck_document",
                "ck_document_iteration", "ck_file", "ck_attachment", "ck_media",
                "ck_workflow_category", "ck_user_activity", "ck_type_iba_data",
                "ck_type_page_layout", "ck_type_definition", "ck_cls_page_layout",
                "ck_number", "ck_version_rule", "ck_lifecycle_status",
                "ck_lifecycle_template", "ck_lifecycle_template_iteration",
                "ck_lifecycle_template_state", "ck_lifecycle_template_transition",
                "ck_view", "ck_view_transition", "ck_stage_template"};
        for (String table : tables) {
            try { jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN tenant_oid CHAR(36)"); } catch (Exception ignored) {}
        }
        try { jdbcTemplate.execute("ALTER TABLE ck_token ADD COLUMN tenant_name VARCHAR(100)"); } catch (Exception ignored) {}
        log.info("  tenant_oid 列检查完成");
    }

    private void fixTenantOidForPlatformData() {
        try {
            // 平台级角色归属平台租户
            jdbcTemplate.update(
                    "UPDATE ck_role SET tenant_oid = ? WHERE tenant_oid IS NULL OR tenant_oid = ?",
                    TenantContext.PLATFORM_TENANT_OID, TenantContext.DEFAULT_TENANT_OID);
            // 角色成员记录归属平台租户
            jdbcTemplate.update(
                    "UPDATE ck_role_member SET tenant_oid = ? WHERE tenant_oid IS NULL OR tenant_oid = ?",
                    TenantContext.PLATFORM_TENANT_OID, TenantContext.DEFAULT_TENANT_OID);
            // sysadmin 用户归属平台租户
            jdbcTemplate.update(
                    "UPDATE ck_user SET tenant_oid = ? WHERE username = ?",
                    TenantContext.PLATFORM_TENANT_OID, SYSADMIN_USERNAME);
            // 其他 tenant_oid 为 NULL 的用户设为默认租户（补偿数据不完整的情况）
            jdbcTemplate.update(
                    "UPDATE ck_user SET tenant_oid = ? WHERE tenant_oid IS NULL AND username != ?",
                    TenantContext.DEFAULT_TENANT_OID, SYSADMIN_USERNAME);

            // 强制确保 sysadmin 拥有 PLATFORM_ADMIN 角色（幂等）
            String sql = "INSERT INTO ck_role_member (oid, user_oid, role_oid, tenant_oid, creator, created_at, updater, updated_at) "
                    + "SELECT ?, u.oid, r.oid, ?, 'system', NOW(), 'system', NOW() "
                    + "FROM ck_user u, ck_role r "
                    + "WHERE u.username = ? AND r.code = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM ck_role_member rm WHERE rm.user_oid = u.oid AND rm.role_oid = r.oid)";
            jdbcTemplate.update(sql,
                    java.util.UUID.randomUUID().toString(),
                    TenantContext.PLATFORM_TENANT_OID,
                    SYSADMIN_USERNAME,
                    PLATFORM_ADMIN_ROLE_CODE);

            // 修正 OOTB 类型定义和页面布局的 tenant_oid 为平台租户
            jdbcTemplate.update(
                    "UPDATE ck_type_definition SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_type_page_layout SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_cls_page_layout SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            // 修正编码规则、版本规则、生命周期状态/模板的 tenant_oid 为平台租户
            jdbcTemplate.update(
                    "UPDATE ck_number SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_version_rule SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_lifecycle_status SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_lifecycle_template SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_lifecycle_template_iteration SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_lifecycle_template_state SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_lifecycle_template_transition SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            // 修正视图和视图切换规则的 tenant_oid 为平台租户
            jdbcTemplate.update(
                    "UPDATE ck_view SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_view_transition SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);
            jdbcTemplate.update(
                    "UPDATE ck_stage_template SET tenant_oid = ? WHERE tenant_oid IS NULL",
                    TenantContext.PLATFORM_TENANT_OID);

            log.info("  平台数据 tenant_oid 已修正，sysadmin 角色已确保");
        } catch (Exception e) {
            log.warn("  修正 tenant_oid 失败: {}", e.getMessage());
        }
    }

    /**
     * 确保用户拥有指定角色（如果尚未分配）。
     */
    private void ensureRole(User user, String roleCode) {
        Role role = roleMapper.selectByCode(roleCode);
        if (role == null) {
            log.warn("  角色 {} 不存在，跳过分配", roleCode);
            return;
        }
        List<Role> existingRoles = userService.getUserRoles(user.getOid());
        boolean hasRole = existingRoles != null && existingRoles.stream()
                .anyMatch(r -> roleCode.equals(r.getCode()));
        if (!hasRole) {
            userService.assignRole(user.getOid(), role.getOid());
            log.info("  已为 sysadmin 分配 {} 角色", roleCode);
        }
    }

    /**
     * 初始化单个平台级角色（幂等，仅当 code 不存在时才插入）。
     */
    private void initPlatformRole(String code, String name, String description) {
        try {
            Role role = roleMapper.selectByCode(code);
            if (role == null) {
                role = new Role(code, name);
                role.setDescription(description);
                role.setRoleType("PLATFORM");
                roleMapper.insert(role);
                log.info("  初始化平台角色: {} ({})", code, name);
            } else {
                // 存量角色若未设置 roleType，自动升级为 PLATFORM
                if (role.getRoleType() == null || role.getRoleType().isEmpty()) {
                    role.setRoleType("PLATFORM");
                    roleMapper.update(role);
                    log.info("  升级存量角色为平台级: {} ({})", code, name);
                } else {
                    log.debug("  平台角色 {} ({}) 已存在，跳过", code, name);
                }
            }
        } catch (Exception e) {
            // 多租户模式下 selectByCode 可能被拦截器过滤导致查不到已有角色
            // 捕获 DuplicateKeyException 等异常，确保幂等
            log.warn("  初始化平台角色 {} 异常(可能已存在): {}", code, e.getMessage());
        }
    }
}
