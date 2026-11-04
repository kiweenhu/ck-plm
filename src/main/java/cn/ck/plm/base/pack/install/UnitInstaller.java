/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.Unit;
import cn.ck.plm.base.mapper.UnitMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计量单位安装器（{@code UNIT}）—— 读包内 {@code units.json}。
 *
 * <p>覆盖 QuantityType 的 10 个量纲共 42 个单位（离散计数 / 质量 / 长度 / 面积 / 体积 /
 * 时间 / 温度 / 角度 / 电流 / 发光强度），并含带偏移换算的摄氏、华氏。
 *
 * <p><b>数据来源变迁</b>：原先写在 {@code UnitInitializer#run} 的 42 行 {@code init(...)} 调用里，
 * 现随 {@code platform-base} 包播种 —— 换行业若要增删常用单位，改包不改代码。
 *
 * <h3>租户归属（2026-10-10 起由"全租户共享"改为"按租户隔离"）</h3>
 * 原先 {@code ck_unit} 无租户列、{@code pack.json} 声明 {@code reusable:true}，于是租户安装时
 * 引擎**整条 SKIP**（复用平台那份）；现在改为<b>每个租户各一份</b>：
 * {@code ck_unit.tenant_oid} + 唯一键 {@code (name, tenant_oid)}，本包 {@code pack.json} 的
 * UNIT 声明同步把 {@code reusable} 改为 {@code false} —— 租户安装（引导）时引擎会真正调到这里，
 * 把 42 个单位写进<b>本租户</b>。目标租户取自 {@code context.getTenantOid()}
 * （引擎在调用 Installer 期间已把 {@code TenantContext} 切到它）；取不到就如实记 FAILED，
 * <b>绝不写无租户的行</b>。
 *
 * <h3>换算与幂等</h3>
 * 基准值 = 当前值 × factor + offset（offset 落库列名是 {@code unit_shift}，offset 为 PostgreSQL 保留字）。
 * 幂等键 = {@code (name, 租户)}：本租户已有该 name 即跳过，<b>不覆盖</b>管理员后续的自定义调整；
 * 跨租户同名互不影响 —— 各租户装各租户的那一份。
 */
@Component
public class UnitInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(UnitInstaller.class);

    private final UnitMapper unitMapper;

    public UnitInstaller(UnitMapper unitMapper) {
        this.unitMapper = unitMapper;
    }

    @Override
    public String resourceType() {
        return "UNIT";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();

        // 单位按租户隔离：目标租户由引擎经 InstallContext 传入（调用期间 TenantContext 也已切到它）。
        // 取不到就如实失败 —— 宁可不装，也不能写出无租户的行（那种行任何租户都读不到，等于脏数据）。
        String tenantOid = context.getTenantOid();
        if (tenantOid == null || tenantOid.isBlank()) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "安装上下文未给出目标租户，无法落单位（ck_unit 已按租户隔离）"));
            return results;
        }

        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }

        Object units = json.get("units");
        if (!(units instanceof List)) {
            return results;
        }
        for (Object element : (List<?>) units) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String name = text(row.get("name"));
            if (name == null) {
                continue;
            }
            try {
                if (unitMapper.existsByName(name, tenantOid) > 0) {
                    results.add(InstallResult.skipped(resourceType(), name, "本租户已有该单位"));
                    continue;
                }
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), name)
                            .why("预演：将新增计量单位「" + text(row.get("display")) + "」"));
                    continue;
                }
                Unit unit = new Unit();
                unit.setName(name);
                unit.setDisplay(text(row.get("display")));
                unit.setQuantityType(text(row.get("quantityType")));
                unit.setIsSI(asBoolean(row.get("isSI")));
                unit.setBaseUnitName(text(row.get("baseUnitName")));
                unit.setFactor(asDouble(row.get("factor"), 1.0));
                unit.setOffset(asDouble(row.get("offset"), 0.0));
                unit.setSortOrder(asInt(row.get("sortOrder"), 0));
                unit.setDescription(text(row.get("description")));
                unit.setTenantOid(tenantOid);
                unit.setCreator("system");
                unit.setUpdater("system");
                LocalDateTime now = LocalDateTime.now();
                unit.setCreatedAt(now);
                unit.setUpdatedAt(now);
                unitMapper.insert(unit);
                results.add(InstallResult.inserted(resourceType(), name)
                        .why("计量单位：" + unit.getDisplay() + "（" + unit.getQuantityType() + "）")
                        .on("ck_unit", unit.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), name,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("计量单位 {} 安装失败: {}", name, e.getMessage());
            }
        }
        log.debug("计量单位安装完成: {} 条明细", results.size());
        return results;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean ? (Boolean) value
                : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
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
