/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.mapper.ViewMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 视图安装器（{@code VIEW}）—— 读包内 {@code views.json}。
 *
 * <p>默认三个视图：{@code Design}（设计视图）/ {@code Manufacturing}（制造视图）/
 * {@code Service}（服务视图）。Part 等版本控制实体创建迭代时要取 Design 视图
 * （见 {@code PartServiceImpl#defaultView}），缺了会导致 view 字段为空。
 *
 * <p><b>数据来源变迁</b>：原先写在 {@code ViewInitializer#run}，现随 {@code platform-base} 包播种
 * —— 增设视图（如"质量视图"）改包即可，不必改代码重编译。
 *
 * <h3>租户归属</h3>
 * 视图行归属<b>安装目标租户</b>（{@code context.getTenantOid()}）：平台安装即平台租户
 * （{@code 00000000…}），与搬迁前 {@code TenantContext.PLATFORM_TENANT_OID} 写法一致。
 * {@code ck_view} 在租户拦截器的表名单里（读侧按租户过滤）—— 这是既有行为，本次只做搬迁不动语义。
 *
 * <h3>幂等</h3>
 * 幂等键 = code：已存在即跳过，不覆盖管理员后续修改（名称 / 排序 / 启用状态）。
 */
@Component
public class ViewInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(ViewInstaller.class);

    private final ViewMapper viewMapper;

    public ViewInstaller(ViewMapper viewMapper) {
        this.viewMapper = viewMapper;
    }

    @Override
    public String resourceType() {
        return "VIEW";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }

        Object views = json.get("views");
        if (!(views instanceof List)) {
            return results;
        }
        for (Object element : (List<?>) views) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            try {
                if (viewMapper.existsByCode(code) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code, "视图已存在"));
                    continue;
                }
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增视图「" + text(row.get("name")) + "」"));
                    continue;
                }
                View view = new View();
                view.setOid(UUID.randomUUID().toString());
                view.setCode(code);
                view.setName(text(row.get("name")));
                view.setDescription(text(row.get("description")));
                view.setSortOrder(asInt(row.get("sortOrder"), 0));
                view.setEnabled(true);
                view.setTenantOid(context.getTenantOid());
                view.setCreatedAt(LocalDateTime.now());
                view.setUpdatedAt(LocalDateTime.now());
                viewMapper.insert(view);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("视图：" + view.getName())
                        .on("ck_view", view.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("视图 {} 安装失败: {}", code, e.getMessage());
            }
        }
        log.debug("视图安装完成: {} 条明细", results.size());
        return results;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
