/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.process.dto.ProcessStartConflictVO;
import cn.ck.plm.process.dto.ProcessStartOptionVO;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.entity.ProcessTemplateVersion;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.mapper.ProcessTemplateVersionMapper;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.process.service.api.ProcessStartOptionService;
import cn.ck.plm.product.service.impl.ProductTeamRoleResolver;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeLifecycleStateProcessLink;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateProcessService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link ProcessStartOptionService} 默认实现。
 *
 * <p>跨模块说明：解析本身完全依赖<b>类型模块</b>（类型 oid、状态 → 流程模板配置），
 * 本类只做"取配置 → 读流程模板 → 组装展示信息"，不复制任何解析规则
 * —— 单一事实源在 {@code TypeLifecycleStateProcessService}。
 */
@Service
public class ProcessStartOptionServiceImpl implements ProcessStartOptionService {

    private static final Logger log = LoggerFactory.getLogger(ProcessStartOptionServiceImpl.class);

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final TypeLifecycleStateProcessService typeLifecycleStateProcessService;
    private final ProcessTemplateMapper templateMapper;
    private final ProcessTemplateVersionMapper versionMapper;
    private final RepositoryService repositoryService;
    /** 判定"该对象是否已有流程在执行"（发起闸门，与 start 端点同一口径） */
    private final ProcessEntitySetService entitySetService;
    /**
     * 项目级发起权限（2026-10-14）：按"我在该系列 / 型号团队里的角色"判 —— 见 {@link ProductTeamRoleResolver}。
     * 非项目对象（器件 / 文档…）不受此限。
     */
    private final ProductTeamRoleResolver roleResolver;

    public ProcessStartOptionServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                         TypeLifecycleStateProcessService typeLifecycleStateProcessService,
                                         ProcessTemplateMapper templateMapper,
                                         ProcessTemplateVersionMapper versionMapper,
                                         RepositoryService repositoryService,
                                         ProcessEntitySetService entitySetService,
                                         ProductTeamRoleResolver roleResolver) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.typeLifecycleStateProcessService = typeLifecycleStateProcessService;
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.repositoryService = repositoryService;
        this.entitySetService = entitySetService;
        this.roleResolver = roleResolver;
    }

    @Override
    public ProcessStartOptionVO resolve(String typeDefinitionCode, String statusCode, String iterationOid,
                                        String entityOid) {
        String typeCode = trimToNull(typeDefinitionCode);
        String status = trimToNull(statusCode);
        if (typeCode == null) {
            throw new IllegalArgumentException("typeDefinitionCode 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("statusCode 不能为空");
        }
        TypeDefinition type = typeDefinitionMapper.selectByCode(
                typeCode, TenantContext.get(), TenantContext.PLATFORM_TENANT_OID);
        if (type == null) {
            throw new IllegalArgumentException("类型不存在: " + typeCode);
        }

        ProcessStartOptionVO vo = new ProcessStartOptionVO();
        vo.setTypeDefinitionCode(type.getCode());
        vo.setTypeDefinitionName(type.getName());
        vo.setTypeOid(type.getOid());
        vo.setStatusCode(status);

        // 闸门：同一业务对象的【同一个大版本】同时只允许一个流程在执行（大版本之间独立）。
        // 放在"配置是否齐全"之前判定 —— 对用户来说"这个版本已经在走流程了"比"这个状态没配流程"
        // 更贴近当下该做的事（去流程监控里看/终止，而不是去改配置）。
        ProcessStartConflictVO conflict = entitySetService.resolveStartConflict(entityOid, typeCode, null);
        if (conflict.isBlocked()) {
            vo.setStartable(false);
            vo.setReason(ProcessEntitySetService.conflictMessage(conflict)
                    + "（可在「流程监控 → 运行中」查看或终止）");
            return vo;
        }

        // 类型 + 状态 → 流程模板配置（单一事实源在类型模块）
        TypeLifecycleStateProcessLink link =
                typeLifecycleStateProcessService.resolveLink(type.getOid(), status, iterationOid);
        if (link == null) {
            vo.setStartable(false);
            vo.setReason("该类型在状态「" + status + "」下未绑定流程模板："
                    + "请先在「业务配置 → 模型定义 → 规则绑定 → 生命周期模板」中为该状态配置流程");
            return vo;
        }
        ProcessTemplate template = templateMapper.selectByOid(link.getProcessTemplateOid());
        if (template == null) {
            vo.setStartable(false);
            // 流程模板是租户数据：跨租户不可见与"已被删除"在这一层看起来一样，措辞要覆盖两种
            vo.setReason("该状态绑定的流程模板在当前租户下不存在：可能已被删除，或它属于其他租户；"
                    + "请在本租户能看到该流程的前提下重新配置");
            return vo;
        }
        return fillTemplateAndVersion(vo, template, type.getCode(), status);
    }

    @Override
    public ProcessStartOptionVO resolveByKey(String processKey, String typeCode, String statusCode,
                                             String entityOid) {
        String key = trimToNull(processKey);
        if (key == null) {
            throw new IllegalArgumentException("processKey 不能为空");
        }
        ProcessStartOptionVO vo = new ProcessStartOptionVO();
        vo.setTypeDefinitionCode(trimToNull(typeCode));
        vo.setStatusCode(trimToNull(statusCode));

        // 冲突闸门与按类型解析同一口径（同一对象同一大版本只允许一个流程在执行）
        ProcessStartConflictVO conflict =
                entitySetService.resolveStartConflict(entityOid, trimToNull(typeCode), null);
        if (conflict.isBlocked()) {
            vo.setStartable(false);
            vo.setReason(ProcessEntitySetService.conflictMessage(conflict)
                    + "（可在「流程监控 → 运行中」查看或终止）");
            return vo;
        }

        // 项目级发起权限（2026-10-14 用户要求）：门 / 评审的发起上下文是产品系列 / 型号 ——
        // 若该 oid 确实是系列 / 型号，则要求"我在其团队里的角色可发起"
        // （产品经理 / 项目经理 / IPDT 组长；管理员豁免）。非项目对象（器件、文档…）不在此拦。
        if (roleResolver.isProductOwner(entityOid) && !roleResolver.canStartProcessUnder(entityOid)) {
            vo.setStartable(false);
            vo.setReason("没有发起权：本操作限该系列 / 型号团队里的「产品经理 / 项目经理 / IPDT 组长」（或管理员）");
            return vo;
        }

        // 调用方已明确"要发起哪个流程"（如阶段门 / 评审行上的 process_key），不再查状态-流程绑定
        ProcessTemplate template = templateMapper.selectByKey(key);
        if (template == null) {
            vo.setStartable(false);
            vo.setReason("流程模板在当前租户下不存在: " + key + "（可能已被删除，或它属于其他租户）");
            return vo;
        }
        return fillTemplateAndVersion(vo, template, trimToNull(typeCode), trimToNull(statusCode));
    }

    /**
     * 公共出口：组装模板/版本展示信息 + 判定"当前租户下能否发起"。
     *
     * <p>两种解析（按类型绑定 / 按 key 直传）共用本方法 —— 部署判定只有一份实现，
     * 两条入口的 {@code startable} 语义因此不会漂移。
     */
    private ProcessStartOptionVO fillTemplateAndVersion(ProcessStartOptionVO vo, ProcessTemplate template,
                                                        String typeCode, String statusCode) {
        ProcessStartOptionVO.ProcessTemplateInfo info = new ProcessStartOptionVO.ProcessTemplateInfo();
        info.setOid(template.getOid());
        info.setKey(template.getKey());
        info.setName(template.getName());
        info.setDisplayName(template.getDisplayName());
        info.setDescription(template.getDescription());
        info.setEnabled(template.getEnabled());
        info.setLatestVersion(template.getLatestVersion());
        info.setDeployedVersion(template.getDeployedVersion());
        vo.setProcessTemplate(info);

        Integer latest = template.getLatestVersion();
        if (latest != null && latest > 0) {
            ProcessTemplateVersion version = versionMapper.selectByTemplateAndVersion(template.getOid(), latest);
            if (version != null) {
                ProcessStartOptionVO.ProcessVersionInfo versionInfo = new ProcessStartOptionVO.ProcessVersionInfo();
                versionInfo.setVersion(version.getVersion());
                versionInfo.setChangeNote(version.getChangeNote());
                versionInfo.setDeployed(version.getDeployed());
                versionInfo.setCreatedAt(version.getCreatedAt());
                vo.setProcessVersion(versionInfo);
            }
        }

        // 能不能发起：Flowable 按 key + <b>当前租户</b>启动，用的是该租户下这个 key 的最新已部署定义。
        // 所以两件事都要查清：模板是否部署过、以及当前租户下是否真的有这个流程定义
        // —— 后者不能只看模板（部署是按租户的，A 租户部署过不代表 B 租户能发起）。
        boolean deployedInTenant = deployedInCurrentTenant(template.getKey());
        info.setDeployedInCurrentTenant(deployedInTenant);
        if (!deployedInTenant) {
            vo.setStartable(false);
            // 唯一有效的判据是"当前租户里有没有这个 key 的定义"——它直接决定发起会不会成功；
            // 模板行上的 deployedVersion 只是元数据（可能缺失或滞后），故只用来把原因说得更准
            vo.setReason(template.getDeployedVersion() == null
                    ? "该流程尚未部署到流程引擎：请先在流程清单页对「" + template.getKey() + "」完成部署"
                    : "该流程在当前租户下没有已部署的流程定义（流程定义按租户部署）："
                            + "请以当前租户在流程清单页对「" + template.getKey() + "」重新部署一次");
            return vo;
        }
        vo.setStartable(true);
        log.debug("发起流程解析: type={} status={} template={} 当前租户内已有部署定义",
                typeCode, statusCode, template.getKey());
        return vo;
    }

    /**
     * 当前租户下是否存在该 key 的流程定义。
     *
     * <p>与 {@code ProcessServiceImpl#startProcess} 的启动口径保持一致：
     * 那里用 {@code startProcessInstanceByKeyAndTenantId(key, …, TenantContext.get())}，
     * 即只在<b>当前租户</b>里找定义。
     */
    private boolean deployedInCurrentTenant(String processKey) {
        String tenant = TenantContext.get();
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processKey);
        if (tenant == null || tenant.trim().isEmpty()) {
            query = query.processDefinitionWithoutTenantId();
        } else {
            query = query.processDefinitionTenantId(tenant);
        }
        return query.count() > 0;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
