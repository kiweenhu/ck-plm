/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.config;

import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 流程表单模板：建表 + 内置模板自动登记。
 *
 * <p>启动时做两件事，都幂等：
 * <ol>
 *   <li><b>建表</b> {@code ck_process_form_template}（{@code CREATE TABLE IF NOT EXISTS}）——
 *       {@code schema.sql} 也有一份完整 DDL，但那是"手工执行全量脚本"的口径；
 *       这里保证<b>老库升级后不用人工跑脚本</b>也能用；</li>
 *   <li><b>登记内置模板</b>：把代码里的内置表单写进表（按 code 判存在）。
 *       内置模板是"运行期真的要用的表单"，注册表留在代码里、清单落进表里 ——
 *       这样业务配置页能看到它们，企业也知道自己有哪些表单可以挂到节点上。</li>
 * </ol>
 *
 * <p>内置模板归属<b>平台租户</b>（表在 {@code TenantStatementInterceptor} 里是 PLATFORM_SHARED）：
 * 平台内置对所有租户可见、不可改不可删，租户要改就自己新建一张自定义模板。
 *
 * <p><b>契约字段以代码为准</b>：内置模板若已存在，name / nodeTypes / component / description
 * 会被代码里的定义刷新一次（这些字段不允许用户改），enabled / sort_order 保留库里的值
 * （平台管理员可能停用过某张内置表单，不能被启动覆盖回去）。
 */
@Component
@Order(7)
public class ProcessFormTemplateInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessFormTemplateInitializer.class);

    private static final String TABLE = "ck_process_form_template";

    /**
     * 内置模板清单 —— 与前端 {@code flow-designer/dsl-core/forms.ts} 的
     * {@code TASK_FORM_TEMPLATES} 是同一份契约的两端：
     * <b>code 必须一字不差</b>（DSL 的 formRef、BPMN 的 formKey、运行期的渲染派发都用它），
     * 前端那份负责"用哪个 Vue 组件渲染"，这份负责"业务配置里能不能看到、能挂到哪些节点类型"。
     *
     * <p>新增内置表单：两边各登记一条（前端加组件映射，这里加一行）。
     */
    private static final BuiltIn[] BUILT_INS = {
            new BuiltIn(
                    "CKPLM_SETUP_ASSIGNEE", "设置流程参与者", "SET_ASSIGNEE",
                    "内置固定表单：列出下游所有审批 / 会签 / 办理活动，由发起人在各自角色成员中挑人",
                    10),
            new BuiltIn(
                    "CKPLM_APPROVAL_OPINION", "审批意见", "APPROVAL",
                    "内置表单：同意 / 驳回两条路由；驳回时显示退回位置（设计期配置），并要求填写意见。"
                            + "审批节点未指定其它表单时默认用它",
                    20),
            new BuiltIn(
                    "CKPLM_COUNTERSIGN", "会签", "COUNTERSIGN_APPROVAL",
                    "内置固定表单：显示会签通过规则与每个参与人的办理情况，本人再给出结论与意见",
                    30),
    };

    private final JdbcTemplate jdbcTemplate;

    public ProcessFormTemplateInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            createTable();
            int inserted = 0, refreshed = 0;
            for (BuiltIn builtIn : BUILT_INS) {
                if (ensureBuiltIn(builtIn)) {
                    inserted++;
                } else {
                    refreshed++;
                }
            }
            log.info("流程表单模板初始化完成: 新增内置 {} 个, 已有 {} 个", inserted, refreshed);
        } catch (Exception e) {
            // 表单模板初始化失败不该拦住应用启动：设计器下拉会退回前端内置注册表
            log.error("流程表单模板初始化失败: {}", e.getMessage(), e);
        }
    }

    private void createTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "oid CHAR(36) PRIMARY KEY, "
                + "code VARCHAR(64) NOT NULL, "
                + "name VARCHAR(128) NOT NULL, "
                + "node_types VARCHAR(256) NOT NULL, "
                + "component VARCHAR(64), "
                + "builtin BOOLEAN NOT NULL DEFAULT FALSE, "
                + "enabled BOOLEAN NOT NULL DEFAULT TRUE, "
                + "sort_order INTEGER NOT NULL DEFAULT 0, "
                + "description VARCHAR(1024), "
                + "tenant_oid CHAR(36) NOT NULL, "
                + "creator VARCHAR(64), created_at TIMESTAMP, "
                + "updater VARCHAR(64), updated_at TIMESTAMP)");
        // 一个租户内 code 唯一：formRef 只有一个字符串，同名会让运行期不知道该用谁的，
        // 因此唯一键包含租户列、并在服务层禁止跨租户重名。
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_ck_process_form_template_code "
                + "ON " + TABLE + " (tenant_oid, code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_pft_sort "
                + "ON " + TABLE + " (builtin, sort_order)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_pft_tenant "
                + "ON " + TABLE + " (tenant_oid)");
    }

    /**
     * 登记一条内置模板。
     *
     * @return true=新插入，false=已存在（契约字段已按代码刷新）
     */
    private boolean ensureBuiltIn(BuiltIn builtIn) {
        List<String> oids = new ArrayList<>();
        try {
            oids.addAll(jdbcTemplate.queryForList(
                    "SELECT oid FROM " + TABLE + " WHERE code = ? AND tenant_oid = ?",
                    String.class, builtIn.code, TenantContext.PLATFORM_TENANT_OID));
        } catch (Exception e) {
            log.warn("查询内置表单失败 code={}: {}", builtIn.code, e.getMessage());
            return false;
        }

        if (oids.isEmpty()) {
            jdbcTemplate.update("INSERT INTO " + TABLE + " (oid, code, name, node_types, component, "
                            + "builtin, enabled, sort_order, description, tenant_oid, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, TRUE, TRUE, ?, ?, ?, NOW())",
                    UUID.randomUUID().toString(), builtIn.code, builtIn.name,
                    builtIn.nodeTypes, builtIn.getComponent(), builtIn.sortOrder, builtIn.description,
                    TenantContext.PLATFORM_TENANT_OID);
            log.info("  √ 登记内置表单 {}({}) 适用节点={}", builtIn.name, builtIn.code, builtIn.nodeTypes);
            return true;
        }

        // 已存在：契约字段以代码为准刷新；enabled / sort_order 保留库里（管理员可能停用过）
        jdbcTemplate.update("UPDATE " + TABLE + " SET name = ?, node_types = ?, component = ?, "
                        + "description = ?, builtin = TRUE, updated_at = NOW() WHERE oid = ?",
                builtIn.name, builtIn.nodeTypes, builtIn.getComponent(), builtIn.description, oids.get(0));
        return false;
    }

    /** 一条内置模板定义 */
    private static final class BuiltIn {
        final String code;
        final String name;
        final String nodeTypes;
        final String description;
        final int sortOrder;

        BuiltIn(String code, String name, String nodeTypes, String description, int sortOrder) {
            this.code = code;
            this.name = name;
            this.nodeTypes = nodeTypes;
            this.description = description;
            this.sortOrder = sortOrder;
        }

        /** 前端渲染器 key 与 code 同值：运行期按 code 派发到对应 Vue 组件 */
        String getComponent() {
            return code;
        }
    }
}
