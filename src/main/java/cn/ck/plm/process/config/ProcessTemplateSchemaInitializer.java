/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.config;

import cn.ck.plm.process.support.ProcessDeploymentSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 流程定义分组的启动期对齐（数据自愈，幂等）。
 *
 * <p><b>本类只做这一件事。</b>流程模板模块的四张表
 * （{@code ck_process_category} / {@code ck_process_template} /
 * {@code ck_process_template_version} / {@code ck_process_entity_set}）及其索引，
 * 结构定义都在 {@code src/main/resources/schema.sql} —— 本类原先自带的建表、
 * 加列、改名、回填等 DDL 与老库迁移已全部退场（口径：结构只留一处来源，且不做老库迁移）。
 *
 * <h3>为什么保留这一段，且必须放在启动期</h3>
 * <p>引擎表 {@code act_re_procdef.category_} / {@code act_re_deployment.category_} 的
 * 默认值是 BPMN 的 targetNamespace（一串 URL）—— 那是设计期的 XML 命名空间，不是给人看的分组。
 * 新部署已显式写对（见 {@code ProcessTemplateServiceImpl#deploy} 与
 * {@link ProcessDeploymentSupport}），这里是给<b>历史定义</b>补齐：
 * <ul>
 *   <li>能对上模板的定义 → 该模板的分组<b>名称</b>（按 {@code key + tenant} 对上）；</li>
 *   <li>内置流程 → 固定分组名（{@link ProcessDeploymentSupport#builtInCategory()}）。</li>
 * </ul>
 * 放在启动期而不是只给一份手工脚本：任何一套库都靠一次启动自愈；且这里执行得早 ——
 * 引擎还没查过这些定义，定义缓存里不会留下改之前的旧值（否则要再重启一次才生效）。
 *
 * <p>幂等：只更新与目标值不一致的行；对不上模板的定义（例如测试遗留）保持原样，不猜它属于哪个分组。
 */
@Component
public class ProcessTemplateSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessTemplateSchemaInitializer.class);

    private final DataSource dataSource;

    public ProcessTemplateSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            alignProcessDefinitionCategory(stmt);
        } catch (Exception e) {
            log.error("流程定义分组对齐失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 对齐流程定义 / 部署的分组（{@code act_re_procdef.category_}、{@code act_re_deployment.category_}）。
     *
     * <p>引擎的 category 默认会落到 BPMN 的 targetNamespace（一串 URL）—— 那是设计期的 XML
     * 命名空间，不是给人看的分组。新部署已显式设置（见 {@code ProcessTemplateServiceImpl#deploy}
     * 与 {@code ProcessDeploymentSupport#ensureBuiltInDeployed}），这里是给<b>历史定义</b>补齐：
     * <ul>
     *   <li>能对上模板的定义 → 模板分组的<b>名称</b>（按 {@code key + tenant} 对上，
     *       与模板引用分组 oid 的口径一致）；</li>
     *   <li>内置流程 → 固定分组名（见 {@code ProcessDeploymentSupport#builtInCategory()}）。</li>
     * </ul>
     *
     * <p>放在启动期而不是只给一份手工脚本：任何一套库都靠一次启动自愈；且这里执行得早 ——
     * 引擎还没查过这些定义，定义缓存里不会留下改之前的旧值（否则要再重启一次才生效）。
     *
     * <p>幂等：只更新与目标值不一致的行。对不上模板的定义（例如测试遗留）保持原样 ——
     * 不猜它属于哪个分组。
     */
    private void alignProcessDefinitionCategory(Statement stmt) throws Exception {
        int definitions = stmt.executeUpdate(
                "UPDATE act_re_procdef d SET category_ = c.name "
                        + "FROM ck_process_template t "
                        + "JOIN ck_process_category c ON c.oid = t.category_oid "
                        + "WHERE d.key_ = t.key AND d.tenant_id_ = t.tenant_oid "
                        + "  AND d.category_ IS DISTINCT FROM c.name");
        int deployments = stmt.executeUpdate(
                "UPDATE act_re_deployment dep SET category_ = c.name "
                        + "FROM ck_process_template t "
                        + "JOIN ck_process_category c ON c.oid = t.category_oid "
                        + "WHERE dep.key_ = t.key AND dep.tenant_id_ = t.tenant_oid "
                        + "  AND dep.category_ IS DISTINCT FROM c.name");
        String builtIn = ProcessDeploymentSupport.builtInCategory();
        for (String key : ProcessDeploymentSupport.builtInKeys()) {
            definitions += stmt.executeUpdate(
                    "UPDATE act_re_procdef SET category_ = '" + builtIn + "' WHERE key_ = '" + key + "'"
                            + "  AND category_ IS DISTINCT FROM '" + builtIn + "'");
            // 更早的内置部署没写 key_，按「定义 → 部署」反查它所属的部署
            deployments += stmt.executeUpdate(
                    "UPDATE act_re_deployment SET category_ = '" + builtIn + "' "
                            + "WHERE id_ IN (SELECT deployment_id_ FROM act_re_procdef WHERE key_ = '" + key + "') "
                            + "  AND category_ IS DISTINCT FROM '" + builtIn + "'");
        }
        if (definitions + deployments > 0) {
            log.info("流程定义分组已对齐: 定义 {} 行、部署 {} 行", definitions, deployments);
        }
    }
}
