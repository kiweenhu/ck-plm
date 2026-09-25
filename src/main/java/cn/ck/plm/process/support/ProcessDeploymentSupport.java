/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import cn.ck.plm.base.util.TenantContext;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程部署支撑 —— 按租户部署 CK-PLM 内置 BPMN 流程。
 *
 * <h3>为什么不能依赖 Spring Boot 自动部署</h3>
 * <p>Flowable 的 {@code check-process-definitions} 自动部署是<b>全局（无租户）</b>的
 * （deployment.tenant_id_ = ''）。而本项目的运行时查询一律带 {@code tenantId}
 * （见 {@link ProcessIdentitySupport#currentTenantId()}），Flowable 的租户标记<b>不会自动继承</b>——
 * 用租户 oid 去查/启动，永远匹配不到无租户的定义。
 *
 * <p>因此本项目关闭自动部署（{@code flowable.check-process-definitions: false}），
 * 改为<b>按租户各部署一份</b>内置流程，从而让：
 * <ul>
 *   <li>每个租户可独立定制/升级自己的流程版本（编辑其租户下的定义，互不影响）；</li>
 *   <li>运行时的 {@code tenantId} 过滤天然生效，无需用流程变量做业务侧隔离。</li>
 * </ul>
 *
 * <p>部署是<b>幂等</b>的：先按「流程 key + 租户」查是否已存在，存在则跳过。
 */
@Component
public class ProcessDeploymentSupport {

    private static final Logger log = LoggerFactory.getLogger(ProcessDeploymentSupport.class);

    /** 内置流程：processDefinitionKey → classpath 资源路径 */
    private static final Map<String, String> BUILT_IN_PROCESSES = new LinkedHashMap<>();

    static {
        BUILT_IN_PROCESSES.put("plm-change-review", "processes/plm-change-review.bpmn20.xml");
    }

    private final RepositoryService repositoryService;
    private final JdbcTemplate jdbcTemplate;

    public ProcessDeploymentSupport(RepositoryService repositoryService, JdbcTemplate jdbcTemplate) {
        this.repositoryService = repositoryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动后为「平台租户 + 全部已注册租户」确保内置流程已部署。
     *
     * <p>使用 {@link ApplicationReadyEvent}（而非 CommandLineRunner）以确保流程引擎与服务层都已就绪。
     * 新注册的租户不在此列——由 {@link #ensureBuiltInDeployed(String)} 在首次发起流程时惰性补齐。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void deployForAllTenants() {
        int ok = 0, fail = 0;
        // 平台租户（sysadmin 所在租户，其发起的流程归属平台层）
        if (ensureBuiltInDeployed(TenantContext.PLATFORM_TENANT_OID)) ok++; else fail++;
        // 业务租户
        try {
            List<String> tenantOids = jdbcTemplate.queryForList(
                    "SELECT oid FROM ck_tenant WHERE oid IS NOT NULL", String.class);
            for (String oid : tenantOids) {
                if (oid == null || oid.trim().isEmpty()) continue;
                if (ensureBuiltInDeployed(oid.trim())) ok++; else fail++;
            }
        } catch (Exception e) {
            log.warn("按租户部署内置流程时读取 ck_tenant 失败: {}", e.getMessage());
        }
        log.info("内置流程按租户部署完成: 成功 {} 个租户, 失败 {} 个", ok, fail);
    }

    /**
     * 确保指定租户下已部署全部内置流程（幂等）。
     *
     * @param tenantId 租户 oid；为空时按「无租户」处理
     * @return true 表示全部就绪（本次新增或此前已存在）
     */
    public boolean ensureBuiltInDeployed(String tenantId) {
        String tid = normalizeTenant(tenantId);
        boolean allOk = true;
        for (Map.Entry<String, String> entry : BUILT_IN_PROCESSES.entrySet()) {
            String key = entry.getKey();
            String resource = entry.getValue();
            try {
                if (existsForTenant(key, tid)) {
                    continue;
                }
                Deployment deployment = repositoryService.createDeployment()
                        .name("PLM内置流程:" + key)
                        .key(key)
                        .category(BUILT_IN_CATEGORY)
                        .tenantId(tid)
                        .addClasspathResource(resource)
                        .deploy();
                // 与「流程模板部署」同一口径：定义上的 category 才是查询/展示读的地方，这里对齐一次
                ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                        .deploymentId(deployment.getId())
                        .singleResult();
                if (definition != null && !BUILT_IN_CATEGORY.equals(definition.getCategory())) {
                    repositoryService.setProcessDefinitionCategory(definition.getId(), BUILT_IN_CATEGORY);
                }
                log.info("已为租户 {} 部署内置流程 {} (deploymentId={})", displayTenant(tid), key, deployment.getId());
            } catch (Exception e) {
                allOk = false;
                log.error("为租户 {} 部署内置流程 {} 失败: {}", displayTenant(tid), key, e.getMessage(), e);
            }
        }
        return allOk;
    }

    /**
     * 内置流程的分组名。
     *
     * <p>内置流程没有模板分组，给一个固定且可读的分组名 —— 否则
     * {@code act_re_procdef.category_} 会落到 BPMN 的 targetNamespace（一串 URL），
     * 和「按模板分组部署」的流程放在一起看就不一致了。
     */
    private static final String BUILT_IN_CATEGORY = "内置流程";

    /** 内置流程 key 列表（供启动期对齐分组等维护动作复用，避免 key 列表出现第二份） */
    public static List<String> builtInKeys() {
        return List.copyOf(BUILT_IN_PROCESSES.keySet());
    }

    /** 内置流程的分组名（部署时写在定义/部署上；历史数据由启动期对齐补齐） */
    public static String builtInCategory() {
        return BUILT_IN_CATEGORY;
    }

    /** 该租户下是否已存在指定 key 的流程定义 */
    private boolean existsForTenant(String processKey, String tid) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .processDefinitionTenantId(tid)
                .count() > 0;
    }

    /** Flowable 以空串表示「无租户」 */
    private String normalizeTenant(String tenantId) {
        return (tenantId == null || tenantId.trim().isEmpty()) ? "" : tenantId.trim();
    }

    private String displayTenant(String tid) {
        return tid.isEmpty() ? "<无租户>" : tid;
    }
}
