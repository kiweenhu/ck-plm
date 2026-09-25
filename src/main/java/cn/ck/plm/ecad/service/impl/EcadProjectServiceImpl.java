/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ecad.service.impl;

import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.ecad.entity.EcadProject;
import cn.ck.plm.ecad.mapper.EcadProjectMapper;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 能力宿主 {@code ECAD_PROJECT}（电子设计项目，{@code ck_ecad_project}）的策略实现。
 *
 * <p><b>为何是本类而非 {@code EcadProjectService}</b>：该宿主目前没有对外域服务
 * （建表见 {@code EcadSchemaInitializer#createProjectTable}，此前只有实体 + Mapper），
 * 因此能力直接落在本实现类上 —— 与「能力实现在宿主自己的 Service 实现类」的约定一致，
 * 只是此宿主的 Service 尚未细分出独立接口。
 *
 * <h3>与其他宿主的差异（建模事实，非缺陷）</h3>
 * <ul>
 *   <li><b>无版本控制</b>：{@code EcadProject} 继承 {@code BaseEntity}（非 MasterEntity），
 *       无 iteration 表，故 {@code iterationOid / displayVersion} 为空，
 *       也不支持删除小版本 / 新建视图版本 / 查看历史版本。</li>
 *   <li><b>标识字段是 code</b>：非 {@code number}，结果的 {@code number} 由 {@code code} 回填；
 *       表无 typeDefinitionCode 列，不做类型编码覆写。</li>
 *   <li><b>编码</b>：{@code code} 为 NOT NULL 唯一键，按类型绑定的规则
 *       （{@code ECAD_PROJECT-NUM}）生成；未绑定规则时要求调用方显式给出 {@code code}。</li>
 * </ul>
 */
@Service
public class EcadProjectServiceImpl implements SoftTypeInstanceCapability {

    /** 能力宿主 code */
    public static final String HOST = "ECAD_PROJECT";

    /** 实体 IBA 属性归属的实体类型编码 */
    private static final String IBA_ENTITY_TYPE = "ECAD_PROJECT";

    /** 默认容器类型（与建表默认值一致） */
    private static final String DEFAULT_CONTAINER_TYPE = "ECAD_PROJECT";

    /** 默认项目阶段（与建表默认值一致） */
    private static final String DEFAULT_PHASE = "PLAN";

    private final EcadProjectMapper ecadProjectMapper;
    private final NumberService numberService;
    private final IbaDataSupport ibaDataSupport;
    private final ObjectMapper objectMapper;

    public EcadProjectServiceImpl(EcadProjectMapper ecadProjectMapper,
                                  NumberService numberService,
                                  IbaDataSupport ibaDataSupport,
                                  ObjectMapper objectMapper) {
        this.ecadProjectMapper = ecadProjectMapper;
        this.numberService = numberService;
        this.ibaDataSupport = ibaDataSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public String hostCode() {
        return HOST;
    }

    @Override
    public Set<SoftTypeInstanceCapability.Operation> supportedOperations() {
        return EnumSet.of(
                SoftTypeInstanceCapability.Operation.CREATE,
                SoftTypeInstanceCapability.Operation.READ,
                SoftTypeInstanceCapability.Operation.UPDATE);
    }

    @Override
    public SoftTypeInstanceResult createInstance(TypeDefinition type, Map<String, Object> payload) {
        EcadProject project = objectMapper.convertValue(payload, EcadProject.class);

        if (project.getOid() == null || project.getOid().isEmpty()) {
            project.setOid(UUID.randomUUID().toString());
        }
        if (project.getName() == null || project.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("电子设计项目名称（name）不能为空");
        }
        project.setName(project.getName().trim());

        // code 为 NOT NULL 唯一键：未提供时按类型绑定规则生成
        if (project.getCode() == null || project.getCode().trim().isEmpty()) {
            String generated = numberService.generateNumberForType(type.getCode());
            if (generated == null || generated.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "类型 " + type.getCode() + " 未绑定编码规则，请显式提供 code");
            }
            project.setCode(generated.trim());
        } else {
            project.setCode(project.getCode().trim());
        }

        // 建表默认值在实体侧兜底（MyBatis 会显式写入 NULL）
        if (project.getContainerType() == null || project.getContainerType().trim().isEmpty()) {
            project.setContainerType(DEFAULT_CONTAINER_TYPE);
        }
        if (project.getProjectPhase() == null || project.getProjectPhase().trim().isEmpty()) {
            project.setProjectPhase(DEFAULT_PHASE);
        }
        if (project.getCreatedAt() == null) {
            project.setCreatedAt(LocalDateTime.now());
        }
        if (project.getUpdatedAt() == null) {
            project.setUpdatedAt(LocalDateTime.now());
        }

        ecadProjectMapper.insert(project);
        ibaDataSupport.saveIbaValues(IBA_ENTITY_TYPE, project.getOid(), payload);

        SoftTypeInstanceResult result = new SoftTypeInstanceResult();
        result.setOid(project.getOid());
        result.setNumber(project.getCode());
        result.setName(project.getName());
        result.setEntity(project);
        return result;
    }

    @Override
    public Object getInstance(String oid, Map<String, Object> params) {
        return ecadProjectMapper.selectByOid(oid);
    }

    @Override
    public Object updateInstance(String oid, Map<String, Object> body) {
        EcadProject project = objectMapper.convertValue(body, EcadProject.class);
        project.setOid(oid);
        if (project.getUpdatedAt() == null) {
            project.setUpdatedAt(LocalDateTime.now());
        }
        ecadProjectMapper.update(project);
        // 实体 IBA（合并保存，保留未提交字段）
        ibaDataSupport.mergeIbaValues(IBA_ENTITY_TYPE, oid, body);
        return ecadProjectMapper.selectByOid(oid);
    }
}
