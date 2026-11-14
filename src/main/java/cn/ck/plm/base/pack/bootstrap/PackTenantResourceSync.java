/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.bootstrap;

import cn.ck.plm.base.pack.install.InstallContext;
import cn.ck.plm.base.pack.install.InstallResult;
import cn.ck.plm.base.pack.install.ResourceInstaller;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.base.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置资源的「按租户补齐」—— 让随版本新增的<b>流程模板与流程表单</b>进到已经装过包的租户里。
 *
 * <h3>为什么需要它</h3>
 * <p>{@link IndustryPackBootstrap} 只把内置包装到<b>平台租户</b>；业务租户的包是在引导式初始化
 * （{@code OnboardingController#complete}）里按租户装一次，而那个入口对已完成引导的租户是关闭的
 * （"一经选定不可修改"）。于是出现一个空档：<b>包升级后新增的流程/表单，老租户永远拿不到</b> ——
 * 平台租户有、业务租户没有，界面上既没有"重装"按钮，也没有二次引导入口。
 *
 * <h3>为什么白名单只有这两类</h3>
 * <p>判据是「{@code reusable=false} 且按 key 天然幂等」：
 * <ul>
 *   <li><b>PROCESS</b>：流程模板每家一份（租户可改自己的），按 key 判重；</li>
 *   <li><b>FORM</b>：表单模板同样是每租户一份（{@code ck_process_form_template} 按租户隔离），
 *       按 code 判重 —— 少了它，新流程节点引用的 formRef 在本租户查不到，办理页只能落兜底表单，
 *       表现为"流程对了、界面不对"。</li>
 * </ul>
 * <p>其它类型（分类树 / 属性 / 资源库 / 视图）刻意不在这里补：它们与租户既有数据强耦合，
 * 重跑一遍可能产生重复行，收益远不及风险。
 *
 * <h3>幂等</h3>
 * <p>完全由安装器保证：模板按 key、表单按 code 判重（已存在即跳过、<b>不覆盖</b>管理员改过的那份），
 * 部署按 {@code processDefinitionKey + tenantId} 判重。因此这里<b>只会补缺失的条目</b>，
 * 不会冲掉任何租户既有定制。
 *
 * <h3>失败不阻断启动</h3>
 * <p>逐个租户 try/catch：一个租户装不上（例如 Flowable 部署冲突）只告警，
 * 不影响其它租户与后续启动 —— 下次启动会再试一次。
 */
@Component
@Order(2)
public class PackTenantResourceSync implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PackTenantResourceSync.class);

    /** 需要按租户补齐的资源类型（理由见类注释） */
    private static final Set<String> SYNC_TYPES = new LinkedHashSet<>(List.of("PROCESS", "FORM"));

    private final IndustryPackLoader loader;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    /** 资源类型 → 安装器（Spring 收集全部实现，键大写；与 {@code IndustryPackEngine} 同一套分派） */
    private final Map<String, ResourceInstaller> installers = new java.util.HashMap<>();

    public PackTenantResourceSync(IndustryPackLoader loader,
                                  List<ResourceInstaller> installerList,
                                  JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.loader = loader;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        if (installerList != null) {
            for (ResourceInstaller installer : installerList) {
                installers.put(installer.resourceType().toUpperCase(), installer);
            }
        }
    }

    @Override
    public void run(String... args) {
        Map<String, List<PackRow>> byTenant = installedPacks();
        if (byTenant.isEmpty()) {
            return;
        }

        String previousTenant = TenantContext.getOrNull();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        try {
            for (Map.Entry<String, List<PackRow>> entry : byTenant.entrySet()) {
                String tenantOid = entry.getKey();
                TenantContext.set(tenantOid);
                for (PackRow row : entry.getValue()) {
                    int[] counts = syncOne(tenantOid, row);
                    created += counts[0];
                    skipped += counts[1];
                    failed += counts[2];
                }
            }
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }

        // 常见情况是"没有新东西"（全 SKIP），那种安静即可 —— 只有真补了东西或有失败才值得一行 INFO
        if (created > 0 || failed > 0) {
            log.info("内置流程/表单按租户补齐: 新增 {} 条, 跳过 {} 条, 失败 {} 条（涉及 {} 个租户）",
                    created, skipped, failed, byTenant.size());
        } else {
            log.debug("内置流程/表单按租户检查完成: {} 个租户均无新增", byTenant.size());
        }
    }

    /**
     * 给一个租户补一个包里需要同步的资源。
     *
     * @return {@code [新增, 跳过, 失败]} 三个计数
     */
    private int[] syncOne(String tenantOid, PackRow row) {
        PackSource pack = loader.find(row.packId());
        if (pack == null || pack.getManifest() == null) {
            // 包文件被移除但安装记录还在（换了发布版本）：不是错误，跳过
            return new int[]{0, 0, 0};
        }
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (PackManifest.Resource resource : pack.getManifest().getResources()) {
            String type = resource.getType();
            if (type == null || !SYNC_TYPES.contains(type.toUpperCase())) {
                continue;
            }
            ResourceInstaller installer = installers.get(type.toUpperCase());
            if (installer == null) {
                continue;
            }
            try {
                List<InstallResult> results = installer.install(
                        new InstallContext(pack, tenantOid, false, Map.of(), objectMapper), resource);
                for (InstallResult result : results) {
                    InstallResult.Action action = result.getAction();
                    if (action == InstallResult.Action.INSERT) {
                        created++;
                        log.info("租户 {} 补装内置资源: {}（{} / {}）",
                                tenantOid, result.getResourceKey(), row.packId(), type);
                    } else if (action == InstallResult.Action.FAILED) {
                        failed++;
                        log.warn("租户 {} 补装内置资源失败: {}（{}）— {}",
                                tenantOid, result.getResourceKey(), type, result.getMessage());
                    } else {
                        skipped++;
                    }
                }
            } catch (Exception e) {
                failed++;
                log.warn("为租户 {} 补齐 {} 的 {} 资源失败: {}", tenantOid, row.packId(), type, e.getMessage());
            }
        }
        return new int[]{created, skipped, failed};
    }

    /**
     * 读安装记录：{@code 租户 → 已装的包}。
     *
     * <p>只看 {@code INSTALLED} / {@code PARTIAL}：{@code PARTIAL} 表示上次装到一半
     * （正是需要补的场景），{@code FAILED} 说明这个包对该租户本来就没装成，
     * 不该在这里替它补一半内容。
     *
     * <p>平台租户也在记录里（{@link IndustryPackBootstrap} 装的就记在它名下），
     * 所以不需要单独处理 —— 重复装一遍是幂等的。
     */
    private Map<String, List<PackRow>> installedPacks() {
        Map<String, List<PackRow>> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT tenant_oid, pack_code, pack_version FROM ck_industry_pack "
                            + "WHERE install_state IN ('INSTALLED', 'PARTIAL') "
                            + "AND tenant_oid IS NOT NULL AND pack_code IS NOT NULL "
                            + "ORDER BY tenant_oid, pack_code");
            for (Map<String, Object> row : rows) {
                String tenantOid = text(row.get("tenant_oid"));
                String code = text(row.get("pack_code"));
                String version = text(row.get("pack_version"));
                if (tenantOid == null || code == null || version == null) {
                    continue;
                }
                result.computeIfAbsent(tenantOid, k -> new ArrayList<>())
                        .add(new PackRow(code, version));
            }
        } catch (Exception e) {
            log.warn("读取行业包安装记录失败，跳过内置资源补齐: {}", e.getMessage());
        }
        return result;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 一条安装记录（包 code + 版本 → {@code code@version}） */
    private record PackRow(String code, String version) {
        String packId() {
            return code + "@" + version;
        }
    }
}
