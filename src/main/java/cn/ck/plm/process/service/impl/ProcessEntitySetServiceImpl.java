/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.process.dto.ProcessStartConflictVO;
import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.mapper.ProcessEntitySetMapper;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import org.flowable.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link ProcessEntitySetService} 默认实现。
 *
 * <p>三条约定：
 * <ol>
 *   <li><b>幂等</b>：靠唯一键 + {@code ON CONFLICT DO NOTHING}，重试不会产生重复行；</li>
 *   <li><b>无版本对象</b>的空版本归一为 {@code null}（不写空串，否则唯一键/反查都会别扭）；</li>
 *   <li><b>能力宿主兜底</b>：{@code rootTypeCode} 缺失时按 {@code typeCode} 解析
 *       （OOTB 类型自身即宿主；软类型取 {@code root_type_code}）—— 它是"这份关联落在哪张业务表"的依据。</li>
 * </ol>
 *
 * <p>带版本对象记的是<b>大版本</b>（{@code revision}，如 A），不是发起那一刻的迭代 oid：
 * 大版本下还会继续产出小版本（检出 → A.2 → 检入 → A.3 …），记迭代 oid 会随对象推进而过时。
 * 调用方没给大版本时，本服务按对象<b>当前</b>的大版本解析（见 {@link #resolveCurrentRevision}）。
 */
@Service
public class ProcessEntitySetServiceImpl implements ProcessEntitySetService {

    private static final Logger log = LoggerFactory.getLogger(ProcessEntitySetServiceImpl.class);

    private final ProcessEntitySetMapper mapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    /** 兜底解析业务对象的最新迭代 oid（调用方没带时用） */
    private final SoftTypeInstanceService softTypeInstanceService;
    /** 判定"是否在跑"以引擎运行时为准（本表只有关联，没有结束与否） */
    private final RuntimeService runtimeService;

    public ProcessEntitySetServiceImpl(ProcessEntitySetMapper mapper,
                                       TypeDefinitionMapper typeDefinitionMapper,
                                       SoftTypeInstanceService softTypeInstanceService,
                                       RuntimeService runtimeService) {
        this.mapper = mapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.softTypeInstanceService = softTypeInstanceService;
        this.runtimeService = runtimeService;
    }

    @Override
    @Transactional
    public ProcessEntitySet record(ProcessEntitySet row) {
        if (row == null) {
            throw new IllegalArgumentException("关联不能为空");
        }
        if (trimToNull(row.getProcessInstanceId()) == null) {
            throw new IllegalArgumentException("processInstanceId 不能为空");
        }
        if (trimToNull(row.getEntityOid()) == null) {
            throw new IllegalArgumentException("entityOid 不能为空");
        }
        row.setProcessInstanceId(row.getProcessInstanceId().trim());
        row.setEntityOid(row.getEntityOid().trim());
        // 无版本对象：这里保持 null（不要写空串）
        row.setEntityVersion(trimToNull(row.getEntityVersion()));
        row.setBusinessKey(trimToNull(row.getBusinessKey()));
        String typeCode = trimToNull(row.getTypeCode());
        row.setTypeCode(typeCode);
        row.setRootTypeCode(resolveRootTypeCode(trimToNull(row.getRootTypeCode()), typeCode));
        // 业务对象的大版本调用方没给就自己解析：各列表页未必带得上版本字段
        // （例如资源库清单只取了主对象字段），而"带版本对象记大版本"是本表自己的口径，
        // 不该反过来要求每个调用方都记得传。无版本对象解析不到，保持 null。
        if (row.getEntityVersion() == null) {
            row.setEntityVersion(resolveCurrentRevision(row.getRootTypeCode(), row.getEntityOid()));
        }

        if (trimToNull(row.getOid()) == null) {
            row.setOid(UUID.randomUUID().toString());
        }
        if (trimToNull(row.getTenantOid()) == null) {
            row.setTenantOid(TenantContext.get());
        }
        String operator = UserContext.get();
        row.setCreator(operator);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdater(operator);
        row.setUpdatedAt(LocalDateTime.now());

        int inserted = mapper.insertIfAbsent(row);
        if (inserted == 0) {
            // 同一实例重复记录同一实体：幂等跳过（重试 / 重复点击）
            log.debug("流程业务实体关联已存在，跳过: instance={} entity={} entityVersion={}",
                    row.getProcessInstanceId(), row.getEntityOid(), row.getEntityVersion());
        } else {
            log.info("流程实例已关联业务实体: instance={} type={} host={} entity={} entityVersion={}",
                    row.getProcessInstanceId(), row.getTypeCode(), row.getRootTypeCode(),
                    row.getEntityOid(), row.getEntityVersion());
        }
        return row;
    }

    @Override
    public List<ProcessEntitySet> findByProcessInstanceId(String processInstanceId) {
        String id = trimToNull(processInstanceId);
        return id == null ? List.of() : mapper.selectByProcessInstanceId(id);
    }

    @Override
    public List<ProcessEntitySet> findByEntityOid(String entityOid) {
        String oid = trimToNull(entityOid);
        return oid == null ? List.of() : mapper.selectByEntityOid(oid);
    }

    @Override
    public List<ProcessEntitySet> findByEntityOidAndEntityVersion(String entityOid, String entityVersion) {
        String oid = trimToNull(entityOid);
        String ver = trimToNull(entityVersion);
        return oid == null || ver == null
                ? List.of()
                : mapper.selectByEntityOidAndEntityVersion(oid, ver);
    }

    @Override
    public List<ProcessEntitySet> findByBusinessKey(String businessKey) {
        String key = trimToNull(businessKey);
        return key == null ? List.of() : mapper.selectByBusinessKey(key);
    }

    @Override
    public ProcessStartConflictVO resolveStartConflict(String entityOid, String typeCode,
                                                       String entityVersion) {
        ProcessStartConflictVO conflict = new ProcessStartConflictVO();
        String oid = trimToNull(entityOid);
        conflict.setEntityOid(oid);
        if (oid == null) {
            return conflict;
        }
        String hostCode = resolveRootTypeCode(null, trimToNull(typeCode));
        conflict.setRootTypeCode(hostCode);
        Map<?, ?> detail = hostDetail(hostCode, oid);
        // 大版本：调用方给了就用；否则按"对象当前大版本"（= 最新小版本所在的大版本）解析
        String ver = trimToNull(entityVersion);
        if (ver == null && detail != null) {
            ver = stringOf(detail.get("revision"));
        }
        conflict.setEntityVersion(ver);
        // 以下只用于把拒绝原因说清楚：该大版本当前的最新小版本及其状态。
        // 只在"判定的大版本就是对象当前大版本"时才带 —— 调用方问的是旧版本时，
        // 把当前版本的小版本/状态贴上去反而误导（版本对不上，信息也就不是那一版的）。
        if (detail != null && ver != null && ver.equals(stringOf(detail.get("revision")))) {
            conflict.setDisplayVersion(stringOf(detail.get("displayVersion")));
            conflict.setStatusCode(stringOf(detail.get("statusCode")));
            conflict.setStatusName(stringOf(detail.get("statusName")));
        }
        if (ver != null) {
            conflict.setRunningInstanceIds(runningInstanceIds(oid, ver));
        }
        return conflict;
    }

    @Override
    public List<String> findRunningEntityOids(List<String> entityOids, String typeCode) {
        List<String> oids = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (entityOids != null) {
            for (String raw : entityOids) {
                String oid = trimToNull(raw);
                if (oid != null && seen.add(oid)) {
                    oids.add(oid);
                }
            }
        }
        if (oids.isEmpty()) {
            return new ArrayList<>();
        }
        // 粗筛：只有"参与过流程"的对象才值得逐个体检 —— 多数候选从没进过流程，
        // 一条 IN 查询就能把它们排除，不必对着每个候选去问引擎
        Set<String> withAnyLink = new LinkedHashSet<>();
        for (ProcessEntitySet row : mapper.selectByEntityOids(oids)) {
            String oid = trimToNull(row.getEntityOid());
            if (oid != null) {
                withAnyLink.add(oid);
            }
        }
        List<String> running = new ArrayList<>();
        for (String oid : oids) {
            if (!withAnyLink.contains(oid)) {
                continue;
            }
            // 逐个复用发起闸门本身：候选过滤口径 = 能不能发起，且天然带大版本粒度
            // （B 版在跑流程时 A 版照样能发起，这是闸门刻意留的口子）
            ProcessStartConflictVO conflict = resolveStartConflict(oid, typeCode, null);
            if (conflict.isBlocked()) {
                running.add(oid);
            }
        }
        return running;
    }

    /**
     * 该对象在指定大版本下仍在执行的流程实例。
     *
     * <p>候选来自关联表的 {@code (entity_oid, entity_version)} 反查，是否"在跑"以引擎运行时为准。
     * 不再按 {@code business_key} 兜底：业务标识里没有大版本，按它兜底会把别的版本也一起挡住
     * —— 而这正是本次要放开的（闸门粒度＝对象 + 大版本）。
     */
    private List<String> runningInstanceIds(String entityOid, String entityVersion) {
        Set<String> candidates = new LinkedHashSet<>();
        for (ProcessEntitySet row : mapper.selectByEntityOidAndEntityVersion(entityOid, entityVersion)) {
            String id = trimToNull(row.getProcessInstanceId());
            if (id != null) {
                candidates.add(id);
            }
        }
        List<String> running = new ArrayList<>();
        for (String instanceId : candidates) {
            try {
                if (runtimeService.createProcessInstanceQuery()
                        .processInstanceId(instanceId).count() > 0) {
                    running.add(instanceId);
                }
            } catch (Exception e) {
                // 单个实例查不动不影响其余判定（多数情况是历史已清理）
                log.warn("判定流程实例是否在运行失败，跳过该实例: instance={} error={}",
                        instanceId, e.getMessage());
            }
        }
        return running;
    }

    @Override
    @Transactional
    public void removeByProcessInstanceId(String processInstanceId) {
        String id = trimToNull(processInstanceId);
        if (id == null) {
            return;
        }
        int removed = mapper.deleteByProcessInstanceId(id);
        if (removed > 0) {
            log.info("流程实例已删除，其业务实体关联一并清理: instance={} 共 {} 条", id, removed);
        }
    }

    // ==================== 私有工具 ====================

    /**
     * 能力宿主（{@code root_type_code}）：调用方给了就用；没给则按类型解析
     * —— OOTB 类型自身即宿主，软类型取其 {@code root_type_code}；都拿不到则保留 null（不猜）。
     */
    private String resolveRootTypeCode(String given, String typeCode) {
        if (given != null) {
            return given;
        }
        if (typeCode == null) {
            return null;
        }
        TypeDefinition type = typeDefinitionMapper.selectByCode(
                typeCode, TenantContext.get(), TenantContext.PLATFORM_TENANT_OID);
        if (type == null) {
            return null;
        }
        String root = trimToNull(type.getRootTypeCode());
        return root != null ? root : trimToNull(type.getCode());
    }

    /**
     * 解析业务对象的<b>当前大版本</b>（{@code revision}，如 A）。
     *
     * <p>取的是"最新小版本所在的大版本"：对象被检出后会产生同大版本的新小版本（A.1 → A.2），
     * 故最新小版本的大版本就是对象<b>当前</b>的大版本。
     *
     * <p>为什么记大版本而不是迭代 oid：流程针对的是"某个大版本"，而大版本下还会继续产出小版本；
     * 记迭代 oid 的话，对象每推进一个小版本，这份关联就指向了一个已过时的版本。需要具体版本时
     * 统一解析为"该大版本当前的最新小版本"。
     *
     * <p>数据来源是能力宿主的实例详情（与该对象"落在哪张表"同一套路由规则）：
     * 拿不到（无版本对象没有该字段）返回 {@code null}；解析异常也不让关联写入失败
     * —— 退化成"只记对象"，链路仍可用。
     */
    private String resolveCurrentRevision(String hostCode, String entityOid) {
        Map<?, ?> detail = hostDetail(hostCode, entityOid);
        return detail == null ? null : stringOf(detail.get("revision"));
    }

    /**
     * 能力宿主的实例详情（与"对象落在哪张表"同一套路由）。
     *
     * <p>宿主未知 / 对象不存在 / 查询异常一律返回 {@code null}：调用方据此退化为"只记对象"
     * 或"只按关联表判定"，不让一次详情读取失败把发起流程整条链路打断。
     */
    private Map<?, ?> hostDetail(String hostCode, String entityOid) {
        if (hostCode == null || entityOid == null) {
            return null;
        }
        try {
            Object detail = softTypeInstanceService.getForHost(hostCode, entityOid, Map.of());
            return detail instanceof Map<?, ?> map ? map : null;
        } catch (Exception e) {
            log.warn("读取业务对象详情失败: host={} entityOid={} error={}",
                    hostCode, entityOid, e.getMessage());
            return null;
        }
    }

    private static String stringOf(Object value) {
        return value == null ? null : trimToNull(value.toString());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
