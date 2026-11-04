/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.base.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 安装上下文 —— Installer 干活时需要的全部环境。
 *
 * <h3>为什么要有它</h3>
 * Installer 不该关心"包从哪来、装给谁、是不是预演、参数被改成了什么" —— 这些都要传，
 * 但签名里塞五六个参数会立刻失控。上下文把它们收成一组只读事实，Installer 只读不写。
 *
 * <h3>租户与预演</h3>
 * <ul>
 *   <li>{@link #getTenantOid()}：安装目标租户 —— 平台级包=平台租户，行业包=引导时选定的那个租户。
 *       引擎会在调用 Installer 期间把 {@link TenantContext} 切到它，Installer 内用现有
 *       Service/Mapper 写入即可自动落对该租户；</li>
 *   <li>{@link #isDryRun()}：预演模式（引导页第 3 步"你将会得到什么"）—— Installer 只算不写，
 *       把"如果真装会做什么"写进结果里；</li>
 *   <li>{@link #getChainDeclaredCodes(String)}：本链（父包在前）**已声明**会装出的各资源 code ——
 *       预演时 Installer 判"前置依赖是否就绪"的依据（如软类型判父类型、规则判被引用的阶段/门/交付件）。</li>
 * </ul>
 */
public class InstallContext {

    private final PackSource source;
    private final String tenantOid;
    private final boolean dryRun;
    private final Map<String, Object> parameters;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<String>> chainDeclaredCodes;

    /** 不带链上声明的构造器（等价于空集合） */
    public InstallContext(PackSource source, String tenantOid, boolean dryRun,
                          Map<String, Object> parameters, ObjectMapper objectMapper) {
        this(source, tenantOid, dryRun, parameters, objectMapper, Collections.emptyMap());
    }

    public InstallContext(PackSource source, String tenantOid, boolean dryRun,
                          Map<String, Object> parameters, ObjectMapper objectMapper,
                          Map<String, Set<String>> chainDeclaredCodes) {
        this.source = source;
        this.tenantOid = tenantOid;
        this.dryRun = dryRun;
        this.parameters = parameters == null ? Collections.emptyMap() : parameters;
        this.objectMapper = objectMapper;
        this.chainDeclaredCodes = chainDeclaredCodes == null ? Collections.emptyMap() : chainDeclaredCodes;
    }

    public PackSource getSource() { return source; }

    /** 安装目标租户 oid */
    public String getTenantOid() { return tenantOid; }

    /**
     * 本链（父包在前）**已声明**会装出的各资源 code —— 预演时判"前置依赖是否就绪"的依据。
     *
     * <p>key = 资源类型大写（{@code SOFT_TYPE} / {@code STAGE} / {@code GATE} / {@code DELIVERABLE} …），
     * 由引擎按"父包在前"累积各包声明（见 {@code IndustryPackEngine#collectDeclaredCodes}）。
     *
     * <p><b>为什么用它、而不是"查平台租户那份"</b>：平台级不注册业务对象（将来更不会），
     * 拿"平台恰好有"当前提迟早失效；而"父包声明了这条 code"是**包元数据**层面的事实，
     * 与库状态、与平台租户都无关。真装时不看它 —— 那时以"前置是否真落进本租户"为准。
     */
    public Set<String> getChainDeclaredCodes(String resourceType) {
        if (resourceType == null) {
            return Collections.emptySet();
        }
        Set<String> set = chainDeclaredCodes.get(resourceType.toUpperCase());
        return set == null ? Collections.emptySet() : set;
    }

    /** 兼容入口：等价于 {@code getChainDeclaredCodes("SOFT_TYPE")}（软类型判父类型用） */
    public Set<String> getChainTypeCodes() { return getChainDeclaredCodes("SOFT_TYPE"); }

    /** 是否平台级安装（平台租户） */
    public boolean isPlatformScope() {
        return TenantContext.PLATFORM_TENANT_OID.equals(tenantOid);
    }

    /** 预演：只算不写 */
    public boolean isDryRun() { return dryRun; }

    /** 参数终值（包默认 + 引导页覆写）；键用点路径，如 {@code genPartThreshold.minModelCount} */
    public Map<String, Object> getParameters() { return parameters; }

    /** 取参数（点路径），缺省返回 defaultValue */
    public Object param(String path, Object defaultValue) {
        if (path == null || path.isEmpty()) {
            return defaultValue;
        }
        Object current = parameters;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return defaultValue;
            }
            current = ((Map<?, ?>) current).get(segment);
            if (current == null) {
                return defaultValue;
            }
        }
        return current;
    }

    /** 取整数参数 */
    public int intParam(String path, int defaultValue) {
        Object value = param(path, null);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /** JSON 读写工具（包内模板文件都是 JSON） */
    public ObjectMapper getObjectMapper() { return objectMapper; }

    @Override
    public String toString() {
        return "InstallContext{" + source.getId() + ", tenant=" + tenantOid
                + (dryRun ? ", DRY-RUN" : "") + "}";
    }
}
