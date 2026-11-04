/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.LifecycleTemplateIteration;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.mapper.LifecycleTemplateIterationMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.softtype.entity.BusinessDomain;
import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.entity.TypeClassificationLink;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeLifecycleStateProcessLink;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.entity.TypeNumberRuleLink;
import cn.ck.plm.softtype.entity.TypeVersionRuleLink;
import cn.ck.plm.softtype.mapper.BusinessDomainMapper;
import cn.ck.plm.softtype.mapper.PageLayoutMapper;
import cn.ck.plm.softtype.mapper.TypeClassificationLinkMapper;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.mapper.TypeLifecycleStateProcessMapper;
import cn.ck.plm.softtype.mapper.TypeLifecycleTemplateLinkMapper;
import cn.ck.plm.softtype.mapper.TypeNumberRuleLinkMapper;
import cn.ck.plm.softtype.mapper.TypeVersionRuleLinkMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 类型定义安装器（{@code SOFT_TYPE}）—— 读包内 {@code softtypes.json} 的 {@code types[]}。
 *
 * <p><b>数据来源变迁</b>：原先 21 个类型散在 {@code TypeDefinitionInitializer} 的五个静态 map 里
 * （{@code ENTITY_META} / {@code FUNCTIONAL_SOFT_TYPES} / {@code PART_SOFT_TYPES} /
 * {@code STRUCTURAL_SOFT_TYPES} / {@code ECAD_CHILD_TYPES} / {@code DOMAIN_OOTB_TYPES}）
 * 加上一张 {@code TYPE_DOMAIN} 域归属表。现按归属拆开：平台通用 14 个随 {@code platform-base}，
 * 电子域 7 个随 {@code electronics-core}。
 *
 * <h3>一条类型要落五件事</h3>
 * <ol>
 *   <li><b>类型行</b>（{@code ck_type_definition}）：kind / icon / source / 描述 / 排序 / 父 /
 *       能力宿主 {@code root_type_code}；</li>
 *   <li><b>编码规则绑定</b>（{@code ck_type_number_rule_link}）；</li>
 *   <li><b>版本规则绑定</b>（{@code ck_type_version_rule_link}）；</li>
 *   <li><b>生命周期模板绑定</b>（{@code ck_type_lifecycle_template_link}）+ <b>域归属</b>
 *       （{@code domain_oid} → {@code ck_business_domain}）；</li>
 *   <li><b>分类节点绑定</b>（{@code ck_type_classification_link}，声明字段 {@code clsIdentifier}）——
 *       为该类型创建 Part 实例时自动继承此分类。★ 因此<b>分类树所在包必须排在类型定义之前</b>
 *       （见各 pack.json 的 resources[]：CLASSIFICATION → SOFT_TYPE → ATTRIBUTE）。</li>
 * </ol>
 *
 * <h3>父类型可能跨包、可能晚到</h3>
 * 电子域类型挂在平台类型下（封装 / 图符 → 工程数据，电子元器件 → 部件），同一份文件里
 * 子类型也可能排在父类型之前。故安装按"多轮扫描"推进：父类型还没就绪的类型先放着，
 * 等本轮有进展再扫一遍；全部就绪后仍无父的才报失败。
 *
 * <p><b>就绪判据分两种模式，都不看平台租户</b>：
 * <ul>
 *   <li><b>真装</b>：以"父类型是否真落进本租户"为准（父包由 {@code installChain} 先装）；
 *       查不到就留待下一轮，收尾仍无则如实报"父类型未就绪"—— 那说明依赖链真有问题；</li>
 *   <li><b>预演</b>：不写库，本租户必然为空，故以<b>包声明</b>为准 —— 本文件内声明过
 *       ({@code declaredCodes})，或<b>本链前面的包</b>声明过（{@code InstallContext#getChainTypeCodes()}，
 *       由引擎按"父包在前"累积），即视为会就绪。</li>
 * </ul>
 * 判据必须落在"包声明"上：平台级不注册业务对象（将来更不会），拿"平台恰好有一份"当前提迟早失效
 * —— 2026-10-11 修过一版这样的错误兜底，当天即被纠正。
 *
 * <p><b>幂等键</b>：code + 租户（{@code existsByCode}）。已存在的类型只跳过、不覆盖 ——
 * 与原先行为一致：平台上管理员改过的图标/描述不会被下次启动冲掉。
 */
@Component
public class SoftTypeInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(SoftTypeInstaller.class);

    private final TypeDefinitionMapper mapper;
    private final TypeNumberRuleLinkMapper numberRuleLinkMapper;
    private final TypeVersionRuleLinkMapper versionRuleLinkMapper;
    private final TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper;
    private final BusinessDomainMapper businessDomainMapper;
    /** 页面布局：装包时按宿主套用 softtypes.json 的 pageLayouts 基准（原先只有"建类型时从父类型克隆"一条路） */
    private final PageLayoutMapper pageLayoutMapper;
    /** 分类绑定（第四绑）：声明字段 {@code clsIdentifier} → {@code ck_type_classification_link} */
    private final ClassificationMapper classificationMapper;
    private final TypeClassificationLinkMapper clsLinkMapper;
    /** 状态-流程绑定（第五绑）：声明字段 {@code lifecycleStateProcesses} → {@code ck_type_lifecycle_state_process_link} */
    private final TypeLifecycleStateProcessMapper stateProcessMapper;
    /** 解析「类型 → 所绑生命周期模板」的当前子版本（绑定键含子版本） */
    private final LifecycleTemplateMapper lifecycleTemplateMapper;
    private final LifecycleTemplateIterationMapper lifecycleTemplateIterationMapper;
    /** 按 key 解析流程模板（PROCESS 资源装在本资源之后 —— 取不到时记 pending，重装本包补齐） */
    private final ProcessTemplateMapper processTemplateMapper;

    public SoftTypeInstaller(TypeDefinitionMapper mapper,
                             TypeNumberRuleLinkMapper numberRuleLinkMapper,
                             TypeVersionRuleLinkMapper versionRuleLinkMapper,
                             TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper,
                             BusinessDomainMapper businessDomainMapper,
                             PageLayoutMapper pageLayoutMapper,
                             ClassificationMapper classificationMapper,
                             TypeClassificationLinkMapper clsLinkMapper,
                             TypeLifecycleStateProcessMapper stateProcessMapper,
                             LifecycleTemplateMapper lifecycleTemplateMapper,
                             LifecycleTemplateIterationMapper lifecycleTemplateIterationMapper,
                             ProcessTemplateMapper processTemplateMapper) {
        this.mapper = mapper;
        this.numberRuleLinkMapper = numberRuleLinkMapper;
        this.versionRuleLinkMapper = versionRuleLinkMapper;
        this.lifecycleTemplateLinkMapper = lifecycleTemplateLinkMapper;
        this.businessDomainMapper = businessDomainMapper;
        this.pageLayoutMapper = pageLayoutMapper;
        this.classificationMapper = classificationMapper;
        this.clsLinkMapper = clsLinkMapper;
        this.stateProcessMapper = stateProcessMapper;
        this.lifecycleTemplateMapper = lifecycleTemplateMapper;
        this.lifecycleTemplateIterationMapper = lifecycleTemplateIterationMapper;
        this.processTemplateMapper = processTemplateMapper;
    }

    @Override
    public String resourceType() {
        return "SOFT_TYPE";
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
        Object typeNodes = json.get("types");
        if (!(typeNodes instanceof List)) {
            // 没有 types 段也可能带 pageLayouts（给库里已存在的类型补布局），故先补一次再返回
            installPageLayouts(context, resource, json, results);
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "模板里没有 types 段（本包不含类型声明）"));
            return results;
        }

        String tenant = context.getTenantOid();
        List<Map<?, ?>> pending = new ArrayList<>();
        Set<String> declaredCodes = new HashSet<>();
        for (Object element : (List<?>) typeNodes) {
            if (element instanceof Map) {
                pending.add((Map<?, ?>) element);
                String code = text(((Map<?, ?>) element).get("code"));
                if (code != null) {
                    declaredCodes.add(code);
                }
            }
        }

        boolean progressed = true;
        while (!pending.isEmpty() && progressed) {
            progressed = false;
            for (Iterator<Map<?, ?>> it = pending.iterator(); it.hasNext(); ) {
                Map<?, ?> row = it.next();
                String code = text(row.get("code"));
                if (code == null) {
                    it.remove();
                    continue;
                }
                try {
                    // 判重与父类型解析都**只看本租户**（2026-10-10 起类型定义按租户隔离）：
                    //   · 每个租户随包各装一份自己的类型（platform-base 的 SOFT_TYPE 已改 reusable:false），
                    //     所以本租户"看不见"平台那份是**正确**的，不该再去平台兜底 —— 否则租户新建的对象
                    //     会指向平台类型、而类型树里又看不到它（半隔离）；
                    //   · 父类型同样必须来自**本租户**：这样类型的 parent_oid 才指向自己家的行，树才连得上。
                    //     代价是本租户必须先装基础包 —— 由 installChain 按 basePack 链保证（基础包在前），
                    //     这也是本文件"多轮扫描 + 父类型未就绪则留待下一轮"存在的原因。
                    //     ⚠ 若将来出现"行业包单独装到租户"的入口，必须先装 platform-base，否则整包类型会因
                    //     父类型未就绪而全部失败（历史上曾把一个租户包的 3 个类型全装丢）。
                    if (mapper.existsByCode(code, tenant, tenant) > 0) {
                        // 类型已存在：内容不覆盖（管理员改过的图标 / 描述保持原样），但**分类绑定要补** ——
                        // 否则"先装过类型、后加分类树（或后加 clsIdentifier 声明）"的库里，绑定永远不会发生。
                        // 幂等：已绑不覆盖；未声明分类则无事发生。
                        String extra = "";
                        if (!context.isDryRun()) {
                            String note = bindClassification(mapper.selectByCode(code, tenant, tenant),
                                    text(row.get("clsIdentifier")), tenant);
                            if (note != null) {
                                extra = "；" + note;
                            }
                        }
                        results.add(InstallResult.skipped(resourceType(), code, "本租户已有该类型" + extra));
                        it.remove();
                        progressed = true;
                        continue;
                    }
                    String parentCode = text(row.get("parentCode"));
                    TypeDefinition parent = null;
                    if (parentCode != null) {
                        parent = mapper.selectByCode(parentCode, tenant, tenant);
                        // 父类型"就绪"的判据分两种模式，都不看平台租户：
                        //   · 真装：以**父类型是否真落进本租户**为准（上面的 selectByCode 已是本租户）；
                        //     没查到就留待下一轮，收尾仍无则如实报"父类型未就绪"——那说明依赖链真有问题；
                        //   · 预演：不写库，本租户必然为空，故以**包声明**为准 —— 同文件内声明过
                        //     (declaredCodes)，或**本链前面的包**声明过 (chainTypeCodes) 即视为会就绪。
                        //     判据必须落在"包声明"上而不是"平台恰好有一份"：平台级不注册业务对象，
                        //     拿它当前提迟早失效（2026-10-11 修正过一版错误兜底）。
                        boolean declaredInChain = context.isDryRun()
                                && (declaredCodes.contains(parentCode)
                                    || context.getChainTypeCodes().contains(parentCode));
                        if (parent == null && !declaredInChain) {
                            // 父类型还没就绪：留到下一轮（同包内顺序错位，或父在别的包里）
                            continue;
                        }
                    }
                    if (context.isDryRun()) {
                        String clsIdentifier = text(row.get("clsIdentifier"));
                        results.add(InstallResult.inserted(resourceType(), code)
                                .why("预演：将新增类型「" + text(row.get("name")) + "」（"
                                        + text(row.get("kind")) + "，宿主 " + text(row.get("hostCode"))
                                        + (clsIdentifier == null ? "" : "，绑定分类 " + clsIdentifier) + "）"));
                        it.remove();
                        progressed = true;
                        continue;
                    }
                    results.add(insertType(row, parent, tenant));
                    it.remove();
                    progressed = true;
                } catch (Exception e) {
                    results.add(InstallResult.failed(resourceType(), code,
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                    it.remove();
                    progressed = true;
                }
            }
        }
        for (Map<?, ?> row : pending) {
            results.add(InstallResult.failed(resourceType(), String.valueOf(row.get("code")),
                    "父类型未就绪（" + row.get("parentCode") + "），请确认基础包已先安装"));
        }

        // ★ pageLayouts：按宿主给该宿主下的类型补标准页面布局。
        //   必须放在【类型装完之后】—— 布局挂在类型 oid 上，本包这次新建的类型（如 STRUCTURAL / STD_PART / GEN_PART）
        //   也要轮到；若放在 types 之前，就只能补到"库里早就存在"的类型（首版正是写在早退之前，属设计错误）。
        //   幂等：该类型该操作已有布局即跳过 —— 不覆盖管理员在低代码设计器里改过的。
        installPageLayouts(context, resource, json, results);

        // ★ lifecycleStateProcesses（第五绑）：给类型的生命周期状态补「默认发起流程」。
        //   同 pageLayouts 的道理放在【类型装完之后】；按 code 从库里回查类型 —— 本次新建与
        //   "库里早就存在"的类型都能轮到（重装本包即补齐）。
        installStateProcesses(context, resource, json, results);

        log.debug("类型定义安装完成: {} 条明细", results.size());
        return results;
    }

    /**
     * 装 {@code lifecycleStateProcesses} —— 按类型补「生命周期状态 → 流程模板」默认绑定
     * （对应 {@code ck_type_lifecycle_state_process_link}，声明在 softtypes.json 的类型条目上）：
     *
     * <pre>"lifecycleStateProcesses": { "DRAFT": "component-introduction" }</pre>
     *
     * <p>键 = 生命周期状态码（必须在该类型所绑模板的**当前子版本**里，否则该条记 failed）；
     * 值 = 流程模板 {@code key}（宽松匹配：忽略大小写、{@code _} 与 {@code -} 等价 ——
     * 声明里写 {@code Component_Introduction} 也能命中 {@code component-introduction}）。
     *
     * <p><b>与 PROCESS 资源的顺序</b>：pack.json 里 PROCESS 声明在本资源**之后** →
     * **首次装包**时流程模板尚未入库，这里记 {@code skippedPending}（不假装已就绪）；
     * **重装本包**（引擎口径：幂等、只补缺失项）即绑定成功。
     *
     * <p><b>幂等且不覆盖</b>：按 (类型, 模板子版本, 状态) 查**本租户**已有行 —— 有则跳过；
     * 管理员在「模型定义 → 状态-流程」里改过的配置不会被装包冲掉。
     */
    private void installStateProcesses(InstallContext context, PackManifest.Resource resource,
                                       Map<String, Object> json, List<InstallResult> results) {
        Object types = json.get("types");
        if (!(types instanceof List)) {
            return;
        }
        String tenant = context.getTenantOid();
        String operator = UserContext.get();
        for (Object item : (List<?>) types) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) item;
            Object declared = row.get("lifecycleStateProcesses");
            if (!(declared instanceof Map) || ((Map<?, ?>) declared).isEmpty()) {
                continue;
            }
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            // 类型从库里回查（本租户）：本次新建与早已存在都能命中；没建成时 types 段已报过原因，这里不重复
            TypeDefinition td = mapper.selectByCode(code, tenant, tenant);
            if (td == null) {
                continue;
            }
            String tplCode = text(row.get("lifecycleTemplate"));
            LifecycleTemplateMaster master = tplCode == null ? null
                    : lifecycleTemplateMapper.selectByCode(tplCode);
            LifecycleTemplateIteration iteration = master == null ? null
                    : lifecycleTemplateIterationMapper.selectLatestByMasterOid(master.getOid());
            if (iteration == null) {
                results.add(InstallResult.skippedPending(resourceType(), code + "/stateProcess",
                        "生命周期模板「" + tplCode + "」尚未就绪（或还没有子版本），状态→流程绑定跳过"));
                continue;
            }
            Set<String> validStatuses = new HashSet<>();
            for (LifecycleTemplateStatusRef ref : lifecycleTemplateMapper.selectStateRefsByIterationOid(iteration.getOid())) {
                if (ref.getStatusCode() != null && !ref.getStatusCode().trim().isEmpty()) {
                    validStatuses.add(ref.getStatusCode().trim().toUpperCase());
                }
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) declared).entrySet()) {
                String status = String.valueOf(entry.getKey()).trim();
                String rawKey = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
                if (status.isEmpty() || rawKey == null || rawKey.isEmpty()) {
                    continue;
                }
                if (!validStatuses.contains(status.toUpperCase())) {
                    results.add(InstallResult.failed(resourceType(), code + "/" + status,
                            "状态 " + status + " 不在模板「" + tplCode + "」的当前子版本中，绑定跳过"));
                    continue;
                }
                // 先解析、后判重，最后才分"预演 / 真装"：
                // 预演也走完整解析链 —— 这样"流程 key 拼错 / 流程未装"在预演报告里就暴露（只读，不写库）
                String processKey = normalizeProcessKey(rawKey);
                ProcessTemplate pt = processTemplateMapper.selectByKey(processKey);
                if (pt == null || (pt.getTenantOid() != null && !pt.getTenantOid().equals(tenant))) {
                    results.add(InstallResult.skippedPending(resourceType(), code + "/" + status,
                            "流程模板 " + processKey + " 尚未装入本租户（PROCESS 资源在本资源之后）—— 重装本包即补齐"));
                    continue;
                }
                boolean exists = false;
                for (TypeLifecycleStateProcessLink existing : stateProcessMapper.selectByTypeIterationStatus(
                        td.getOid(), iteration.getOid(), status)) {
                    // 同键可能同时有平台行与本租户行：只认自己租户的行（与 DefaultTypeLifecycleStateProcessService 同口径）
                    if (tenant.equals(existing.getTenantOid())) {
                        exists = true;
                        break;
                    }
                }
                if (context.isDryRun()) {
                    // action 要如实：已存在时预演就是"将跳过"（不能标 INSERT —— 报告是给用户看的）
                    if (exists) {
                        results.add(InstallResult.skipped(resourceType(), code + "/" + status,
                                "预演：本租户已有该状态→流程配置，真装将跳过（不覆盖）"));
                    } else {
                        results.add(InstallResult.inserted(resourceType(), code + "/" + status)
                                .why("预演：将为 " + code + " 的 " + status + " 状态绑定流程 " + processKey));
                    }
                    continue;
                }
                if (exists) {
                    results.add(InstallResult.skipped(resourceType(), code + "/" + status,
                            "本租户已有该状态→流程配置，跳过（不覆盖）"));
                    continue;
                }
                try {
                    TypeLifecycleStateProcessLink link = new TypeLifecycleStateProcessLink(
                            td.getOid(), iteration.getOid(), status, pt.getOid());
                    link.setOid(UUID.randomUUID().toString());
                    link.setTenantOid(tenant);
                    link.setCreator(operator);
                    link.setCreatedAt(LocalDateTime.now());
                    link.setUpdater(operator);
                    link.setUpdatedAt(LocalDateTime.now());
                    stateProcessMapper.insert(link);
                    results.add(InstallResult.inserted(resourceType(), code + "/" + status)
                            .why(code + " 的 " + status + " 状态 → 流程 " + processKey)
                            .on("ck_type_lifecycle_state_process_link", link.getOid()));
                } catch (Exception e) {
                    results.add(InstallResult.failed(resourceType(), code + "/" + status,
                            "状态→流程绑定失败: " + e.getMessage()));
                }
            }
        }
    }

    /** 流程 key 宽松匹配：忽略大小写，{@code _} 与 {@code -} 等价（声明可写 Component_Introduction） */
    private static String normalizeProcessKey(String raw) {
        return raw.trim().toLowerCase().replace('_', '-');
    }

    /**
     * 装 {@code pageLayouts} —— 按能力宿主把标准布局套用给该宿主下的类型。
     *
     * <p><b>为什么要它</b>：在此之前，页面布局只有一条来路 —— 建软类型时从父类型**克隆**
     * （{@code TypeDefinitionServiceImpl#inheritPageLayouts}）。父链上若都没有布局，新类型就没有任何页面
     * （而且那段克隆曾因自递归把"新建类型"打成 500）。现在基准写在包声明里，装包按 {@code root_type_code} 补齐。
     *
     * <p><b>幂等且不覆盖</b>：按 (类型 oid, 操作码) 判存在，已有布局一律跳过 ——
     * 管理员在低代码设计器里改过的布局不会被下次装包冲掉；库里已有布局的 14 个类型保持原样。
     */
    private void installPageLayouts(InstallContext context, PackManifest.Resource resource,
                                    Map<String, Object> json, List<InstallResult> results) {
        Object section = json.get("pageLayouts");
        if (!(section instanceof Map)) {
            return;
        }
        Object byHost = ((Map<?, ?>) section).get("byHost");
        if (!(byHost instanceof Map)) {
            return;
        }
        String tenant = context.getTenantOid();
        // 类型总量只有几十条：一次取回、在内存里按宿主分组（不值得为它新增 Mapper 方法）
        // 注：预演时本租户还没有类型（不写库），这里会走到下面的"该宿主下没有已注册的类型"跳过 ——
        // 那是预期内的（跳过不是错），不去借平台那份算：布局明细在真装报告里逐条给，预演不假装精确。
        List<TypeDefinition> allTypes = mapper.selectAll(tenant, tenant);

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) byHost).entrySet()) {
            String host = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof List)) {
                continue;
            }
            List<?> layouts = (List<?>) entry.getValue();
            List<TypeDefinition> targets = new ArrayList<>();
            for (TypeDefinition td : allTypes) {
                String root = td.getRootTypeCode() != null && !td.getRootTypeCode().trim().isEmpty()
                        ? td.getRootTypeCode() : td.getCode();
                if (host.equalsIgnoreCase(root)) {
                    targets.add(td);
                }
            }
            if (targets.isEmpty()) {
                // 预演时本租户还没有类型（不写库）→ 没有可套布局的对象，属预期内跳过；
                // 用 skippedPending 显示"跳过"，不冒充"已就绪"（真装报告里布局明细逐条给）
                results.add(InstallResult.skippedPending(resourceType(), host,
                        "该宿主下没有已注册的类型，页面布局跳过（宿主 " + host + "）"));
                continue;
            }
            int created = 0;
            int existed = 0;
            int patchedFields = 0;
            List<String> failed = new ArrayList<>();
            for (TypeDefinition td : targets) {
                for (Object item : layouts) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> one = (Map<?, ?>) item;
                    String op = text(one.get("operationCode"));
                    if (op == null) {
                        continue;
                    }
                    if (context.isDryRun()) {
                        created += 1;
                        continue;
                    }
                    try {
                        PageLayout existing = pageLayoutMapper.selectByEntityAndOperation(td.getOid(), op, tenant, tenant);
                        if (existing != null) {
                            // 已有布局不重建，但**包声明里新增的字段要补进去** ——
                            // 否则像「所属容器」这种后加的字段，老租户永远等不到：
                            // 布局已存在 → 整条跳过 → 页面设计器里也拖不出来（2026-10-22 反馈）。
                            // 只追加缺失字段，已有字段的配置（label / 顺序 / 只读…）一律不动。
                            int patched = appendMissingFormFields(context, existing, one);
                            if (patched > 0) {
                                patchedFields += patched;
                            } else {
                                existed += 1;
                            }
                            continue;
                        }
                        PageLayout layout = new PageLayout();
                        layout.setOid(UUID.randomUUID().toString());
                        layout.setEntityOid(td.getOid());
                        layout.setEntityCode(td.getCode());
                        layout.setOperationCode(op);
                        layout.setOperationName(text(one.get("operationName")));
                        layout.setLayoutJson(context.getObjectMapper().writeValueAsString(one.get("layoutJson")));
                        layout.setTenantOid(tenant);
                        layout.setCreator(cn.ck.plm.base.util.UserContext.get());
                        pageLayoutMapper.insert(layout);
                        created += 1;
                    } catch (Exception e) {
                        failed.add(td.getCode() + "/" + op);
                    }
                }
            }
            StringBuilder why = new StringBuilder();
            why.append(context.isDryRun() ? "预演：将为 " : "已为 ")
                    .append(targets.size()).append(" 个类型（").append(host).append(" 宿主）")
                    .append(context.isDryRun() ? "补 " : "补齐 ").append(created).append(" 条页面布局");
            if (existed > 0) {
                why.append("；跳过已有 ").append(existed).append(" 条（不覆盖）");
            }
            if (patchedFields > 0) {
                why.append("；给已有布局补入 ").append(patchedFields).append(" 个新字段");
            }
            if (!failed.isEmpty()) {
                why.append("；失败 ").append(String.join("、", failed));
            }
            results.add(InstallResult.skipped(resourceType(), host, why.toString()));
        }
    }

    /**
     * 把包声明里新增、库里还没有的<b>表单字段</b>追加进已有布局。
     *
     * <p>为什么需要：布局安装是"有则跳过"，而新增字段（如变更单据的「所属容器」）
     * 只改包文件对老租户永远不生效 —— 布局行早就存在、整条被跳过，页面设计器里
     * 也就一直看不到这个字段。
     *
     * <p>只增不删：已有字段的 label / 顺序 / 只读 / 必填等配置一律保持原样；
     * 布局 JSON 解析失败时<b>不动它</b>（宁可少补，不可写坏）。
     *
     * <p>不跨容器搬移：声明里的分组 / 布局行只在库里存在<b>同名容器</b>时才往里补子字段，
     * 库里的容器结构被管理员改过就整体跳过（放错地方比不放更糟）。
     *
     * @return 实际追加的字段数（0 = 无需改动）
     */
    private int appendMissingFormFields(InstallContext context, PageLayout existing, Map<?, ?> declared) {
        Object declaredLayout = declared.get("layoutJson");
        if (!(declaredLayout instanceof Map)) {
            return 0;
        }
        Object declaredForm = ((Map<?, ?>) declaredLayout).get("form");
        if (!(declaredForm instanceof Map)) {
            return 0;
        }
        Object declaredFields = ((Map<?, ?>) declaredForm).get("fields");
        if (!(declaredFields instanceof List)) {
            return 0;
        }

        ObjectMapper om = context.getObjectMapper();
        JsonNode root;
        try {
            root = om.readTree(existing.getLayoutJson());
        } catch (Exception e) {
            log.warn("布局 JSON 解析失败，跳过字段补齐: {}/{}: {}",
                    existing.getEntityCode(), existing.getOperationCode(), e.getMessage());
            return 0;
        }
        if (root == null || !root.isObject()) {
            return 0;
        }
        ObjectNode rootObj = (ObjectNode) root;

        JsonNode form = rootObj.get("form");
        if (form == null || !form.isObject()) {
            form = om.createObjectNode();
            rootObj.set("form", form);
        }
        JsonNode fieldsNode = form.get("fields");
        ArrayNode fields;
        if (fieldsNode != null && fieldsNode.isArray()) {
            fields = (ArrayNode) fieldsNode;
        } else {
            fields = om.createArrayNode();
            ((ObjectNode) form).set("fields", fields);
        }

        Set<String> existingNames = new HashSet<>();
        collectFieldNames(fields, existingNames);

        int added = addMissingFields(om, fields, (List<?>) declaredFields, existingNames);
        if (added == 0) {
            return 0;
        }
        try {
            // 用整行 update 而不是 updateLayout(oid, json, name)：后者的 SQL 还引用了
            // 方法签名里不存在的 #{updater}，一调用就抛 Parameter 'updater' not found。
            existing.setLayoutJson(om.writeValueAsString(rootObj));
            pageLayoutMapper.update(existing);
        } catch (Exception e) {
            log.warn("布局字段补齐写库失败: {}/{}: {}",
                    existing.getEntityCode(), existing.getOperationCode(), e.toString());
            return 0;
        }
        log.info("  √ 布局字段补齐: {}/{} 新增 {} 个字段",
                existing.getEntityCode(), existing.getOperationCode(), added);
        return added;
    }

    /**
     * 把声明里有、库里没有的字段补进既有 fields，<b>保持声明顺序</b>。
     *
     * <p>插入点取"声明里前一个字段在库里的位置 + 1"，这样补齐后编辑表单的字段顺序
     * 与新建表单一致，而不是把缺的字段一股脑堆到末尾。
     *
     * <p>容器（group / layout-row）不跨容器搬移，但<b>同名容器内</b>会继续递归补齐 ——
     * 变更通告、文档这类布局的字段全包在 layout-row 的 children 里，
     * 不进容器等于一个字段也补不到（老租户的编辑表单就永远缺那几个字段）。
     *
     * @return 追加的字段数
     */
    private int addMissingFields(ObjectMapper om, ArrayNode fields, List<?> declaredFields,
                                 Set<String> existingNames) {
        int added = 0;
        int cursor = 0;
        for (Object item : declaredFields) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> declaredField = (Map<?, ?>) item;
            String type = text(declaredField.get("type"));
            String fieldName = text(declaredField.get("fieldName"));

            if ("group".equals(type) || "layout-row".equals(type)) {
                Object children = declaredField.get("children");
                JsonNode container = findContainer(fields, fieldName);
                if (container != null && children instanceof List) {
                    added += addMissingFields(om, (ArrayNode) container.get("children"),
                            (List<?>) children, existingNames);
                }
                continue;
            }
            if (fieldName == null) {
                continue;
            }
            int existingIndex = indexOfField(fields, fieldName);
            if (existingIndex >= 0) {
                cursor = existingIndex + 1;
                continue;
            }
            fields.insert(Math.min(cursor, fields.size()), om.valueToTree(declaredField));
            existingNames.add(fieldName);
            cursor += 1;
            added++;
        }
        return added;
    }

    /** 在既有布局的同一层里按 fieldName 找字段下标（-1 = 没有） */
    private static int indexOfField(JsonNode fields, String fieldName) {
        if (fields == null || !fields.isArray() || fieldName == null) {
            return -1;
        }
        for (int i = 0; i < fields.size(); i++) {
            JsonNode name = fields.get(i).get("fieldName");
            if (name != null && fieldName.equals(name.asText())) {
                return i;
            }
        }
        return -1;
    }

    /** 按 fieldName 找同名容器（group / layout-row），找不到返回 null —— 不新建容器 */
    private static JsonNode findContainer(JsonNode fields, String fieldName) {
        int index = indexOfField(fields, fieldName);
        if (index < 0) {
            return null;
        }
        JsonNode children = fields.get(index).get("children");
        return children != null && children.isArray() ? fields.get(index) : null;
    }

    /** 递归收集布局里的字段名（含 group / layout-row 的子字段） */
    private static void collectFieldNames(JsonNode fields, Set<String> out) {
        if (fields == null || !fields.isArray()) {
            return;
        }
        for (JsonNode field : fields) {
            JsonNode name = field.get("fieldName");
            if (name != null && !name.asText().isEmpty()) {
                out.add(name.asText());
            }
            collectFieldNames(field.get("children"), out);
        }
    }

    /** 落一条类型：类型行 + 三张绑定表 + 域归属 */
    private InstallResult insertType(Map<?, ?> row, TypeDefinition parent, String tenant) {
        String code = text(row.get("code"));
        String name = text(row.get("name"));
        String kind = TypeDefinition.KIND_SOFT_TYPE.equalsIgnoreCase(text(row.get("kind")))
                ? TypeDefinition.KIND_SOFT_TYPE : TypeDefinition.KIND_OOTB;

        TypeDefinition td = new TypeDefinition(code, name, kind);
        td.setIcon(text(row.get("icon")));
        td.setSource(text(row.get("source")));
        td.setDescription(text(row.get("description")));
        td.setSortOrder(intOr(row.get("sortOrder"), 0));
        td.setTenantOid(tenant);
        td.setParentOid(parent != null ? parent.getOid() : null);
        String hostCode = text(row.get("hostCode"));
        td.setRootTypeCode(hostCode != null ? hostCode : (parent != null
                ? (parent.getRootTypeCode() != null ? parent.getRootTypeCode() : parent.getCode())
                : code));
        mapper.insert(td);

        String numberRule = text(row.get("numberRule"));
        String versionRule = text(row.get("versionRule"));
        String lifecycleTemplate = text(row.get("lifecycleTemplate"));
        bindNumberRule(td, numberRule, tenant);
        bindVersionRule(td, versionRule, tenant);
        bindLifecycleTemplate(td, lifecycleTemplate, tenant);
        bindDomain(td, text(row.get("domain")));
        // 第四绑：分类节点（分类树由 CLASSIFICATION 资源先装 —— 包顺序约定见各 pack.json）
        String clsNote = bindClassification(td, text(row.get("clsIdentifier")), tenant);

        StringBuilder why = new StringBuilder();
        why.append("类型：").append(name).append("（").append(kind)
                .append("，宿主 ").append(td.getRootTypeCode());
        if (parent != null) {
            why.append("，父 ").append(parent.getCode());
        }
        List<String> binds = new ArrayList<>();
        if (numberRule != null) {
            binds.add("编码 " + numberRule);
        }
        if (versionRule != null) {
            binds.add("版本 " + versionRule);
        }
        if (lifecycleTemplate != null) {
            binds.add("生命周期 " + lifecycleTemplate);
        }
        if (clsNote != null) {
            binds.add(clsNote);
        }
        why.append(binds.isEmpty() ? "" : "；绑定 " + String.join(" / ", binds)).append("）");
        return InstallResult.inserted(resourceType(), code).why(why.toString())
                .on("ck_type_definition", td.getOid());
    }

    private void bindNumberRule(TypeDefinition td, String ruleCode, String tenant) {
        if (ruleCode == null || numberRuleLinkMapper.existsByTypeOid(td.getOid()) > 0) {
            return;
        }
        TypeNumberRuleLink link = new TypeNumberRuleLink(td.getOid(), ruleCode);
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenant);
        numberRuleLinkMapper.insert(link);
    }

    private void bindVersionRule(TypeDefinition td, String ruleCode, String tenant) {
        if (ruleCode == null || versionRuleLinkMapper.existsByTypeOid(td.getOid()) > 0) {
            return;
        }
        TypeVersionRuleLink link = new TypeVersionRuleLink(td.getOid(), ruleCode);
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenant);
        versionRuleLinkMapper.insert(link);
    }

    private void bindLifecycleTemplate(TypeDefinition td, String templateCode, String tenant) {
        if (templateCode == null || lifecycleTemplateLinkMapper.existsByTypeOid(td.getOid()) > 0) {
            return;
        }
        TypeLifecycleTemplateLink link = new TypeLifecycleTemplateLink(td.getOid(), templateCode);
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenant);
        lifecycleTemplateLinkMapper.insert(link);
    }

    private void bindDomain(TypeDefinition td, String domainCode) {
        if (domainCode == null) {
            return;
        }
        BusinessDomain domain = businessDomainMapper.selectByCode(domainCode);
        if (domain != null) {
            mapper.updateDomainOid(td.getOid(), domain.getOid());
        } else {
            log.warn("业务域未就绪，类型 {} 的域归属留空（{}）", td.getCode(), domainCode);
        }
    }

    /**
     * 类型 → 分类节点绑定（{@code ck_type_classification_link}，声明字段 {@code clsIdentifier}）。
     *
     * <p>用 identifier 而不是 name / oid：identifier 是分类树的业务唯一键（父 identifier + '-' + slug
     * 逐级拼出，如 {@code tpl-structure} / {@code tpl-structure-standard-part} / {@code tpl-electronic}），
     * 跨包、跨租户稳定；oid 每次装包都会变，没法写进声明。
     *
     * <p><b>分类树必须先装</b>：包顺序已约定 CLASSIFICATION 排在 SOFT_TYPE 之前（见各 pack.json 的
     * resources[] —— 结构件 → {@code tpl-structure}、标准件 → {@code tpl-structure-standard-part}、
     * 电子元器件 → {@code tpl-electronic}）。本方法只查<b>本租户</b>的节点；查不到就如实记一笔、
     * 不抛错 —— 类型本身装好了，不该因一个绑定把整条类型判失败。
     *
     * <p><b>幂等且不覆盖</b>：已有绑定即跳过（与编码 / 版本 / 生命周期三绑定的口径一致）。
     *
     * @return 给安装报告的一句话（{@code "分类 结构件"} / {@code "分类 tpl-x（未就绪，未绑定）"}）；
     *         未声明分类或已有绑定时返回 {@code null}
     */
    private String bindClassification(TypeDefinition td, String clsIdentifier, String tenant) {
        if (clsIdentifier == null || td == null || clsLinkMapper.existsByTypeOid(td.getOid()) > 0) {
            return null;
        }
        Classification cls = classificationMapper.selectByIdentifier(clsIdentifier, tenant);
        if (cls == null) {
            log.warn("分类节点未就绪，类型 {} 的分类绑定留空（{}）—— 请确认分类树所在包排在业务对象定义之前",
                    td.getCode(), clsIdentifier);
            return "分类 " + clsIdentifier + "（未就绪，未绑定）";
        }
        TypeClassificationLink link = new TypeClassificationLink(td.getOid(), cls.getOid());
        link.setOid(UUID.randomUUID().toString());
        link.setTenantOid(tenant);
        clsLinkMapper.insert(link);
        return "分类 " + cls.getName();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOr(Object value, int defaultValue) {
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
}
