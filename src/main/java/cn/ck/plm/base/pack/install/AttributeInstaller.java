/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeIBA;
import cn.ck.plm.softtype.service.api.AttributeDefinitionService;
import cn.ck.plm.softtype.service.api.IBAService;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性安装器（{@code ATTRIBUTE}）—— 业务对象的属性有两类，各走各的路：
 * <ul>
 *   <li>{@code ibaGroups[]} —— <b>IBA 扩展属性</b>：写成 IBA 并绑到指定类型上（本类的原有职责，见下三节）；</li>
 *   <li>{@code entityFields[]} —— <b>实体字段声明</b>：那是"实体本来就有的列"（真源是 Java 实体类 + schema.sql）。
 *       2026-10-11 起本安装器把它<b>按包声明写入本租户</b>的属性定义 —— 属性定义已按租户隔离：
 *       平台那份由 {@code AttributeInitializer} 反射注册（「先清后建」只动平台行），不会覆盖租户行；
 *       写入后仍做双向对齐校验。见 {@link #verifyBaseGroups}。</li>
 * </ul>
 *
 * <h3>键名的正名（2026-10）</h3>
 * 原为 {@code groups[]} / {@code baseGroups[]} —— 前者是**裸名**（看不出是 IBA），后者要靠"相对什么的基础"
 * 才能读懂，两个键**不对称**。现改为 {@code ibaGroups[]} / {@code entityFields[]}：各自直说"装的是什么"。
 * 旧键名仍**兼容读取**（新键优先），但装包报告会提示迁移 —— 迁移完可删除兼容分支。
 *
 * <h3>为什么落在 IBA 而不是 ck_attribute_definition</h3>
 * 平台有两条属性通路：
 * <ul>
 *   <li>{@code ck_attribute_definition} —— 由 {@code AttributeInitializer} <b>反射实体字段</b>自动登记
 *       （code / name / revision … 那类是"实体本来就有的列"）；</li>
 *   <li>{@code ck_iba} + {@code ck_type_iba} —— <b>租户可自定义的扩展属性</b>（IBA），
 *       可分配给多个软类型，并可在映射层覆写 required / 默认值。</li>
 * </ul>
 * 包里的 24 个器件属性（manufacturer / mpn / preferredLevel / rohsCompliant …）是行业知识，
 * 不是实体字段 —— 所以走 IBA。这也正是文档第一节说的"IBA 机制已可用、无需开发"。
 *
 * <h3>三条容易踩的语义（都按平台现状来）</h3>
 * <ol>
 *   <li><b>IBA 按租户独立</b>：{@code IBAService.findByCode} 只按<b>当前租户</b>查 →
 *       平台装的 IBA 别的租户看不见。所以本资源在 pack.json 声明 {@code reusable:false}：
 *       每个租户安装时各建一份自己的 IBA（这正是租户可自定义属性的本意）。</li>
 *   <li><b>code 大写存储</b>：服务内部对 code 做 trim + toUpperCase 查找 —— 包里的
 *       camelCase 写法（qualificationStatus）在库里是大写（QUALIFICATIONSTATUS）；
 *       比对时按大小写不敏感处理。</li>
 *   <li><b>映射的 entityCode 填类型自身的 code</b>：平台 {@code batchAssign} 与类型复制
 *       都是 {@code setEntityCode(td.getCode())}（不是宿主 PART），照抄它，不自创。</li>
 * </ol>
 *
 * <h3>幂等</h3>
 * IBA 按 code 判存在（存在即复用，不覆盖）；类型映射按 (typeOid, ibaOid) 判存在（已绑即跳过）。
 * 预演只算不写。枚举值写进 {@code constraintsJson} 的 {@code enumValues}（IBA 的约定形状）。
 */
@Component
public class AttributeInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(AttributeInstaller.class);

    /** 属性在类型属性页里的排序基数（避开实体字段 1–103 的区间） */
    private static final int SORT_BASE = 200;

    private final IBAService ibaService;
    private final TypeDefinitionService typeDefinitionService;
    /** 实体字段声明落库用（2026-10-11 起装包把 entityFields 真正写入**本租户**的那份属性定义） */
    private final AttributeDefinitionService attributeDefinitionService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AttributeInstaller(IBAService ibaService,
                              TypeDefinitionService typeDefinitionService,
                              AttributeDefinitionService attributeDefinitionService,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper) {
        this.ibaService = ibaService;
        this.typeDefinitionService = typeDefinitionService;
        this.attributeDefinitionService = attributeDefinitionService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String resourceType() {
        return "ATTRIBUTE";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取属性文件失败: " + e.getMessage()));
            return results;
        }
        // entityFields[]（实体字段声明）与 ibaGroups[]（IBA 扩展属性）是两码事：
        // 处理必须放在 ibaGroups 的早退**之前** —— 否则"只声明实体字段、没有 IBA"的包
        // （如 platform-base）会被"没有 ibaGroups[] 数组"提前返回，字段声明静默跳过
        // （首版就踩过这个坑：调用写在方法末尾，日志里只剩一句"没有 groups[]"）。
        // ★ 2026-10-11 起**预演也跑**：entityFields 是"按租户"初始化的属性定义（真装写入本租户），
        //   预演必须如实报出"将登记 N 条"，不能只留一句"跳过"。
        verifyBaseGroups(context, resource, json, results);

        // 新键优先，旧键（groups）兼容读取一次；读到旧键就在报告里提示迁移
        Object groups = json.get("ibaGroups") instanceof List ? json.get("ibaGroups") : json.get("groups");
        if (json.get("ibaGroups") == null && groups instanceof List) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "检测到旧键名 groups[] —— 本次已兼容处理，建议迁移为 ibaGroups[]（迁移后可删除兼容分支）"));
        }
        if (!(groups instanceof List)) {
            // 没有 IBA 组：若该文件带了实体字段声明，上面 verifyBaseGroups 已按租户处理并报告，
            // 这里不再重复报"跳过"；两者都没有才算真的无事可做（预期内）。
            if (!(json.get("entityFields") instanceof List) && !(json.get("baseGroups") instanceof List)) {
                results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                        "本文件既没有 IBA 扩展属性（ibaGroups[]）也没有实体字段声明（entityFields[]）"));
            }
            return results;
        }

        int sort = SORT_BASE;
        for (Object groupElement : (List<?>) groups) {
            if (!(groupElement instanceof Map)) {
                continue;
            }
            Map<?, ?> group = (Map<?, ?>) groupElement;
            List<String> bindTo = textList(group.get("bindTo"));
            // ★ 交叉校验：IBA 组必须绑定类型 —— bindTo 为空时原先会静默创建出一批
            //   "没有任何类型能用到"的孤儿属性（installOne 里 missing/bound 都是空，报告看不出问题）
            if (bindTo.isEmpty()) {
                String gc = text(group.get("code"));
                results.add(InstallResult.failed(resourceType(),
                        gc == null ? resource.getFile() : gc,
                        "IBA 组缺少 bindTo[] —— IBA 必须绑定类型才有意义，本组已跳过（属性未创建）"));
                continue;
            }
            Object attributes = group.get("attributes");
            if (!(attributes instanceof List)) {
                continue;
            }
            for (Object attrElement : (List<?>) attributes) {
                if (!(attrElement instanceof Map)) {
                    continue;
                }
                Map<?, ?> attr = (Map<?, ?>) attrElement;
                String code = text(attr.get("code"));
                if (code == null) {
                    continue;
                }
                sort += 1;
                try {
                    installOne(context, results, attr, code, bindTo, text(group.get("name")), sort);
                } catch (Exception e) {
                    results.add(InstallResult.failed(resourceType(), code,
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                    log.warn("属性 {} 安装失败: {}", code, e.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * {@code entityFields[]} 对齐：把包声明的"实体字段"**写入本租户**的属性定义，然后校验一致性。
     *
     * <p><b>2026-10-11 起语义变更（原注释的理由已不成立）</b>：属性定义已按租户隔离 ——
     * 平台那份由 {@code AttributeInitializer} 反射注册（启动时「先清后建」，且只删建**平台行**，
     * {@code deleteSystemByEntityAndTenant(…, 平台)}）；**租户那份由此处按包声明写入**，
     * 幂等（同 (entity, field, 租户) 已存在即跳过、不覆盖）。因此不再有"写了会被清建覆盖"的问题。
     *
     * <p>写入后仍保留原有的双向对齐校验：声明了库里没有 → 如实报（并提示"IBA 请放进 ibaGroups[]"）；
     * 实体侧有、声明里没有 → 只提醒（实体类改字段时同步 entityFields[]）。
     */
    private void verifyBaseGroups(InstallContext context, PackManifest.Resource resource,
                                  Map<String, Object> json, List<InstallResult> results) {
        // 新键 entityFields 优先，旧键 baseGroups 兼容
        Object baseGroups = json.get("entityFields") instanceof List
                ? json.get("entityFields") : json.get("baseGroups");
        if (!(baseGroups instanceof List)) {
            return;
        }
        String tenant = context.getTenantOid();
        boolean dryRun = context.isDryRun();
        for (Object element : (List<?>) baseGroups) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> group = (Map<?, ?>) element;
            String groupCode = text(group.get("code"));
            List<String> bindTo = textList(group.get("bindTo"));
            Object attributes = group.get("attributes");
            if (!(attributes instanceof List) || bindTo.isEmpty()) {
                continue;
            }
            String typeCode = bindTo.get(0);
            String key = groupCode == null ? typeCode : groupCode;
            try {
                // 1) 直查本租户已有字段（JdbcTemplate 不走 MyBatis 的租户拦截器，必须显式按本租户过滤）
                List<String> present = jdbcTemplate.queryForList(
                        "SELECT DISTINCT field_name FROM ck_attribute_definition "
                                + "WHERE entity_name = ? AND tenant_oid = ? AND UPPER(source) = 'SYSTEM'",
                        String.class, typeCode, tenant);

                // 2) 声明 vs 现状：真装把"本租户还没有的"写进去（幂等），预演只统计不写
                List<String> missing = new ArrayList<>();
                List<String> declared = new ArrayList<>();
                int registered = 0;
                for (Object attrElement : (List<?>) attributes) {
                    if (!(attrElement instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> attr = (Map<?, ?>) attrElement;
                    String field = text(attr.get("code"));
                    if (field == null) {
                        continue;
                    }
                    declared.add(field);
                    if (present.contains(field)) {
                        continue;
                    }
                    missing.add(field);
                    if (dryRun) {
                        continue;
                    }
                    String displayName = text(attr.get("name"));
                    String dataType = text(attr.get("dataType"));
                    AttributeDefinition def = new AttributeDefinition(
                            typeCode, field, displayName == null ? field : displayName,
                            dataType == null ? "STRING" : dataType.toUpperCase());
                    def.setEntityName(typeCode);
                    def.setSource("SYSTEM");
                    def.setTenantOid(tenant);
                    def.setRequired(Boolean.TRUE.equals(attr.get("required")));
                    def.setSearchable(Boolean.TRUE.equals(attr.get("searchable")));
                    def.setListable(Boolean.TRUE.equals(attr.get("listable")));
                    def.setEditable(Boolean.TRUE.equals(attr.get("editable")));
                    String ui = text(attr.get("uiComponent"));
                    if (ui != null) {
                        def.setUiComponent(ui);
                    }
                    Object defaultValue = attr.get("default");
                    if (defaultValue != null) {
                        def.setDefaultValue(String.valueOf(defaultValue));
                    }
                    Object sortOrder = attr.get("sortOrder");
                    if (sortOrder instanceof Number) {
                        def.setSortOrder(((Number) sortOrder).intValue());
                    }
                    def.setConstraintsJson(constraintsJsonOf(attr));
                    def.setEnabled(true);
                    if (attributeDefinitionService.register(def) != null) {
                        registered++;
                    }
                }

                // 3) 报告：预演＝"将登记 N 条到本租户"（这才是"按租户初始化导入"的如实预告）；
                //    真装＝"已登记 N 条 / 已存在"。写入失败会抛异常 → 外层 catch 记 FAILED，不在这里兜。
                if (dryRun) {
                    results.add(missing.isEmpty()
                            ? InstallResult.skipped(resourceType(), key,
                                    "实体字段声明已存在（" + typeCode + " 共 " + declared.size() + " 条）")
                            : InstallResult.inserted(resourceType(), key)
                                    .why("预演：将把 " + typeCode + " 的 " + missing.size()
                                            + " 条实体字段声明登记到本租户（共声明 " + declared.size() + " 条）"));
                } else if (registered > 0) {
                    results.add(InstallResult.inserted(resourceType(), key)
                            .why("实体字段声明已登记到本租户（" + typeCode + " 共 " + declared.size()
                                    + " 条，本次新增 " + registered + " 条，其余已存在）"));
                } else {
                    results.add(InstallResult.skipped(resourceType(), key,
                            "实体字段声明已存在（" + typeCode + " 共 " + declared.size() + " 条）"));
                }
                // ★ 反向校验：实体侧有、声明里没有 —— 实体类新增字段后声明容易悄悄落后，
                //   这里如实报出（只提醒，不算失败），让人去同步 entityFields[]
                List<String> undeclared = new ArrayList<>();
                for (String f : present) {
                    if (!declared.contains(f)) {
                        undeclared.add(f);
                    }
                }
                if (!undeclared.isEmpty()) {
                    results.add(InstallResult.skipped(resourceType(), key,
                            "实体侧另有 " + undeclared.size() + " 条字段未在声明中 —— "
                                    + String.join("、", undeclared.size() > 8 ? undeclared.subList(0, 8) : undeclared)
                                    + (undeclared.size() > 8 ? " …" : "")
                                    + "（提示：实体类字段有变更时请同步更新本文件的 entityFields[]，保持声明与实体一致）"));
                }
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), key,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private void installOne(InstallContext context, List<InstallResult> results, Map<?, ?> attr,
                            String code, List<String> bindTo, String groupName, int sortOrder) throws Exception {
        String name = text(attr.get("name")) == null ? code : text(attr.get("name"));
        String dataType = text(attr.get("dataType")) == null ? "STRING" : text(attr.get("dataType")).toUpperCase();
        boolean required = Boolean.TRUE.equals(attr.get("required"));
        String defaultValue = attr.get("default") == null ? null : String.valueOf(attr.get("default"));
        String description = text(attr.get("note"));
        String constraints = constraintsJsonOf(attr);

        if (context.isDryRun()) {
            results.add(InstallResult.inserted(resourceType(), code)
                    .why("预演：将新增属性「" + name + "」（" + dataType + (required ? "，必填" : "")
                            + "，组「" + groupName + "」，绑定 " + String.join("/", bindTo) + "）"));
            return;
        }

        // 1) IBA 本体：按 code 判存在（服务内部大写化查找 → 大小写不敏感）
        IBA iba = ibaService.findByCode(code);
        boolean created = false;
        if (iba == null) {
            iba = new IBA(code, name, dataType);
            iba.setDisplayName(name);
            iba.setRequired(required);
            iba.setDefaultValue(defaultValue);
            iba.setConstraintsJson(constraints);
            iba.setDescription(description);
            iba.setSortOrder(sortOrder);
            iba.setEnabled(true);
            iba = ibaService.create(iba);
            created = true;
        }

        // 2) 绑到类型：映射按 (typeOid, ibaOid) 判存在
        List<String> bound = new ArrayList<>();
        List<String> already = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String typeCode : bindTo) {
            TypeDefinition td = typeDefinitionService.findByCode(typeCode);
            if (td == null) {
                missing.add(typeCode);
                continue;
            }
            boolean exists = false;
            for (TypeIBA mapping : ibaService.findMappingsByTypeOid(td.getOid())) {
                if (iba.getOid() != null && iba.getOid().equals(mapping.getIbaOid())) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                already.add(typeCode);
                continue;
            }
            TypeIBA mapping = new TypeIBA(td.getOid(), iba.getOid());
            // 照平台 batchAssign 的写法：entityCode 填类型自身的 code
            mapping.setEntityCode(td.getCode());
            mapping.setRequired(required);
            mapping.setDefaultValue(defaultValue);
            mapping.setSortOrder(sortOrder);
            ibaService.assignIba(mapping);
            bound.add(typeCode);
        }

        StringBuilder why = new StringBuilder();
        why.append(created ? "新增属性「" : "属性已存在，复用「").append(name).append("」（").append(dataType).append("）");
        if (!bound.isEmpty()) {
            why.append("；已绑定 ").append(String.join("/", bound));
        }
        if (!already.isEmpty()) {
            why.append("；已绑定过 ").append(String.join("/", already));
        }
        if (!missing.isEmpty()) {
            why.append("；类型不存在，未绑定 ").append(String.join("/", missing));
        }
        InstallResult result = created ? InstallResult.inserted(resourceType(), code)
                : InstallResult.skipped(resourceType(), code, why.toString());
        if (created) {
            result.why(why.toString());
        }
        result.on("ck_iba", iba == null ? null : iba.getOid());
        results.add(result);
    }

    /** 枚举属性 → IBA 约定的 constraintsJson：{@code {"enumValues": [...]}}（options 支持对象与纯字符串两种写法） */
    private String constraintsJsonOf(Map<?, ?> attr) throws Exception {
        Object options = attr.get("options");
        if (!(options instanceof List) || ((List<?>) options).isEmpty()) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        for (Object option : (List<?>) options) {
            if (option instanceof Map) {
                Object value = ((Map<?, ?>) option).get("value");
                if (value != null) {
                    values.add(value);
                }
            } else if (option != null) {
                values.add(option);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("enumValues", values);
        return objectMapper.writeValueAsString(constraints);
    }

    private static List<String> textList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String s = text(item);
                if (s != null) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
