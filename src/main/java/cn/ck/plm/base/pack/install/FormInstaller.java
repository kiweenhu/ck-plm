/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.process.entity.ProcessFormTemplate;
import cn.ck.plm.process.mapper.ProcessFormTemplateMapper;
import cn.ck.plm.process.service.api.ProcessFormTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流程表单安装器（{@code FORM}）—— 读包内 {@code forms.json}，把表单模板写进
 * {@code ck_process_form_template}（按 code 判存在，已存在即跳过、不覆盖）。
 *
 * <h3>两类表单，用同一份声明格式区分</h3>
 * <ul>
 *   <li><b>内置表单</b>（{@code "builtin": true}）—— 引擎与前端的<b>渲染契约</b>：前端
 *       {@code dsl-core/forms.ts} 按 code 派发到同名 Vue 组件，DSL 的 {@code formRef}、
 *       编译出的 {@code flowable:formKey}、运行期渲染都用同一个 code，故 {@code component} 与 code 同值。
 *       平台包三张（设置流程参与者 / 审批意见 / 会签）声明在
 *       {@code platform-base/v1.0.0/forms.json}，由 {@code IndustryPackBootstrap} 启动时装到平台租户；
 *       租户在引导装包时各得一份（表已按租户隔离）。
 *       <b>它们不走 {@code formService.create()}</b> —— 那个入口按设计强制 {@code builtin=false}
 *       （防止租户自建"假内置"），内置只能由装载入，故这里直插；</li>
 *   <li><b>行业自定义表单</b>（不写 {@code builtin}）—— 走 {@code formService.create()}，
 *       由服务层取当前租户并落 {@code builtin=false}。它们的渲染<b>同样固化在前端</b>：
 *       包在 {@code forms.json} 里「开启 + 标注 component 名」（2026-10-12 起的新范式），
 *       组件名原样落库，运行期由前端视图注册表按名解析；
 *       尚未实现组件的表单会落到兜底通用表单（不影响办理）。</li>
 * </ul>
 *
 * <h3>数据来源变迁</h3>
 * 内置三张原先由 {@code ProcessFormTemplateInitializer} 在启动时登记（硬编码在 Java 里），
 * 2026-10-10 起随 {@code platform-base} 包播种 —— 与该包的生命周期 / 编码规则 / 计量单位 / 视图
 * 同一模式：增删内置表单改包即可，不必改代码重编译；那个 Initializer 已退场。
 * 契约的另一端仍是前端 {@code dsl-core/forms.ts}，<b>code 必须一字不差</b>。
 *
 * <h3>三件事说清楚</h3>
 * <ol>
 *   <li><b>租户</b>：内置表单显式写 {@code context.getTenantOid()}（目标租户）；自定义表单由
 *       {@code create()} 取当前上下文（装包期间引擎已把上下文切到目标租户）。</li>
 *   <li><b>幂等</b>：一律按 code 判存在；已存在跳过（管理员改过的表单不该被重装冲掉）。</li>
 *   <li><b>fields 是前向声明</b>：表结构里没有字段列，当前只提供"叫什么、能挂哪些节点"，
 *       字段清单先在 JSON 里写下来，等表单引擎支持字段级渲染后直接生效 —— 故本安装器只写元数据，
 *       不假装写了字段（写不了的就不写）。</li>
 * </ol>
 */
@Component
public class FormInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(FormInstaller.class);

    /** 表单默认排序（内置三张用 10/20/30，自定义排在它们之后） */
    private static final int DEFAULT_SORT = 100;

    private final ProcessFormTemplateService formService;
    private final ProcessFormTemplateMapper formMapper;
    private final ObjectMapper objectMapper;

    public FormInstaller(ProcessFormTemplateService formService,
                         ProcessFormTemplateMapper formMapper,
                         ObjectMapper objectMapper) {
        this.formService = formService;
        this.formMapper = formMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String resourceType() {
        return "FORM";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取表单文件失败: " + e.getMessage()));
            return results;
        }
        Object forms = json.get("forms");
        if (!(forms instanceof List)) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "表单文件里没有 forms[] 数组（本包不含表单）"));
            return results;
        }
        for (Object element : (List<?>) forms) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            try {
                installOne(context, results, row, code);
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("表单模板 {} 安装失败: {}", code, e.getMessage());
            }
        }
        log.debug("流程表单安装完成: {} 条明细", results.size());
        return results;
    }

    private void installOne(InstallContext context, List<InstallResult> results, Map<?, ?> row, String code) {
        String name = text(row.get("name")) == null ? code : text(row.get("name"));
        String nodeTypes = text(row.get("nodeTypes"));
        String description = text(row.get("description"));
        String component = text(row.get("component"));
        boolean builtin = Boolean.TRUE.equals(row.get("builtin"));
        int sortOrder = row.get("sortOrder") instanceof Number
                ? ((Number) row.get("sortOrder")).intValue() : DEFAULT_SORT;

        if (context.isDryRun()) {
            results.add(InstallResult.inserted(resourceType(), code)
                    .why("预演：将新增" + (builtin ? "内置" : "") + "表单模板「" + name
                            + "」（适用节点 " + nodeTypes + "）"));
            return;
        }
        ProcessFormTemplate existing = formMapper.selectByCode(code);
        if (existing != null) {
            results.add(InstallResult.skipped(resourceType(), code,
                    existing.getBuiltin() != null && existing.getBuiltin()
                            ? "同名内置表单已存在（契约 code，跳过）"
                            : "表单模板已存在（未覆盖）"));
            return;
        }

        if (builtin) {
            // 内置表单不能走 create()：那里按设计强制 builtin=false（防租户自建"假内置"），
            // 内置只由装载入 —— 故此处直插，并显式写契约三字段（code/nodeTypes/component）。
            ProcessFormTemplate created = new ProcessFormTemplate(
                    code, name, nodeTypes, component, true, sortOrder, description);
            created.setOid(UUID.randomUUID().toString());
            created.setEnabled(true);
            created.setTenantOid(context.getTenantOid());
            created.setCreator("system");
            created.setUpdater("system");
            LocalDateTime now = LocalDateTime.now();
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            formMapper.insert(created);
            results.add(InstallResult.inserted(resourceType(), code)
                    .why("内置表单：「" + name + "」（component=" + component
                            + "，不可删除、契约字段不可改）")
                    .on("ck_process_form_template", created.getOid()));
            return;
        }

        // component 原样落库（2026-10-12 修：此前硬编码 null —— 行业包按新范式
        // 在 forms.json 上「标注 component 名」就白标了；组件固化在前端，
        // 运行期按这个名字解析渲染器，见 components/task/taskForms/index.js）
        ProcessFormTemplate created = formService.create(
                new ProcessFormTemplate(code, name, nodeTypes, component, false, sortOrder, description));
        results.add(InstallResult.inserted(resourceType(), code)
                .why("表单模板：「" + name + "」（适用节点 " + nodeTypes + "；"
                        + (component == null || component.trim().isEmpty()
                                ? "未标注 component，运行期走兜底渲染" : "component=" + component)
                        + "）")
                .on("ck_process_form_template", created == null ? null : created.getOid()));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
