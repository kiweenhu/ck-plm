/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.entity.LifecycleTemplateIteration;
import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.mapper.LifecycleTemplateIterationMapper;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.softtype.entity.TypeLifecycleStateProcessLink;
import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.mapper.TypeLifecycleStateProcessMapper;
import cn.ck.plm.softtype.mapper.TypeLifecycleTemplateLinkMapper;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * {@link TypeLifecycleStateProcessService} 默认实现。
 *
 * <p>四条关键约定：
 * <ol>
 *   <li><b>配置挂子版本</b>：与业务对象迭代固化的 {@code lifecycleTemplateIterationOid} 同层，
 *       运行期才能用实例自带的版本精确命中"当时那一版配置"；</li>
 *   <li><b>配置的读写都落在最新子版本</b>（不回溯旧版本）：界面展示与操作的就是最新版；
 *       若允许"最新版没有就回头看旧版"，用户在最新版解绑后旧版的行会重新变成生效 —— 配置会复活；</li>
 *   <li><b>编辑模板时把配置继承到新子版本</b>（见 {@link #inherit}，由生命周期模板服务回调）：
 *       否则用户一编辑模板，配置就留在旧版本上（界面上看不到）；</li>
 *   <li><b>归属租户 = 保存时当前用户的租户</b>：各租户可各自配置同一类型同一状态
 *       （唯一键含租户；本表按 {@code tenant_oid IN (平台, 当前)} 过滤）；
 *       读取时<b>本租户优先，平台租户的行作共享默认</b>。</li>
 * </ol>
 *
 * <p><b>运行期解析（未来接入自动发起时）</b>：拿实例自带的子版本 oid + 状态查本表即可命中
 * "当时那一版配置"；旧子版本的行正是为此保留的（不会因后续改配置被改写）。
 */
@Service
public class DefaultTypeLifecycleStateProcessService implements TypeLifecycleStateProcessService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTypeLifecycleStateProcessService.class);

    private final TypeLifecycleStateProcessMapper mapper;
    private final TypeLifecycleTemplateLinkMapper typeTemplateLinkMapper;
    private final LifecycleTemplateMapper templateMapper;
    private final LifecycleTemplateIterationMapper iterationMapper;

    public DefaultTypeLifecycleStateProcessService(TypeLifecycleStateProcessMapper mapper,
                                                   TypeLifecycleTemplateLinkMapper typeTemplateLinkMapper,
                                                   LifecycleTemplateMapper templateMapper,
                                                   LifecycleTemplateIterationMapper iterationMapper) {
        this.mapper = mapper;
        this.typeTemplateLinkMapper = typeTemplateLinkMapper;
        this.templateMapper = templateMapper;
        this.iterationMapper = iterationMapper;
    }

    @Override
    public Map<String, String> mapByType(String typeOid) {
        Map<String, String> map = new LinkedHashMap<>();
        String type = trimToNull(typeOid);
        if (type == null) {
            return map;
        }
        LifecycleTemplateIteration iteration = resolveCurrentIteration(type);
        if (iteration == null) {
            return map;
        }
        // 同一状态可能同时有"本租户的行"和"平台默认行"：排序后 putIfAbsent 即得"本租户优先"
        for (TypeLifecycleStateProcessLink row : preferCurrentTenant(
                mapper.selectByTypeAndIteration(type, iteration.getOid()))) {
            map.putIfAbsent(row.getStatusCode(), row.getProcessTemplateOid());
        }
        return map;
    }

    @Override
    @Transactional
    public Map<String, String> bind(String typeOid, String statusCode, String processTemplateOid) {
        String type = trimToNull(typeOid);
        if (type == null) {
            throw new IllegalArgumentException("类型不能为空");
        }
        LifecycleTemplateIteration iteration = resolveCurrentIteration(type);
        if (iteration == null) {
            throw new IllegalArgumentException("该类型还未绑定生命周期模板（或模板还没有子版本），无法为状态指定流程");
        }
        String status = trimToNull(statusCode);
        if (status == null) {
            throw new IllegalArgumentException("状态编码不能为空");
        }
        // 状态必须是该子版本里的状态：否则会配到一个已删除/不存在的状态上（界面上永远看不到它）
        if (!statusCodesOf(iteration.getOid()).contains(status)) {
            throw new IllegalArgumentException("状态 " + status + " 不在该类型所绑生命周期模板的最新版本中");
        }

        String wanted = trimToNull(processTemplateOid);
        // 只认"本租户的那条行"：别的租户的同键配置不归我改（平台租户的行就是平台自己的那条）
        TypeLifecycleStateProcessLink existing = rowOfTenant(
                mapper.selectByTypeIterationStatus(type, iteration.getOid(), status), TenantContext.get());
        if (wanted == null) {
            if (existing != null) {
                mapper.deleteByOid(existing.getOid());
                log.info("类型状态已解绑流程模板: type={} iteration={} status={}",
                        type, iteration.getOid(), status);
            }
        } else if (existing == null) {
            TypeLifecycleStateProcessLink row =
                    new TypeLifecycleStateProcessLink(type, iteration.getOid(), status, wanted);
            row.setOid(UUID.randomUUID().toString());
            // 归属租户 = 保存时当前用户的租户：各租户各配各的，平台租户的行作共享默认
            row.setTenantOid(TenantContext.get());
            row.setCreator(UserContext.get());
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdater(UserContext.get());
            row.setUpdatedAt(LocalDateTime.now());
            mapper.insert(row);
            log.info("类型状态已绑定流程模板: type={} iteration={} status={} processTemplate={}",
                    type, iteration.getOid(), status, wanted);
        } else {
            mapper.updateProcessTemplateOid(existing.getOid(), wanted, UserContext.get(), LocalDateTime.now());
            log.info("类型状态已改绑流程模板: type={} iteration={} status={} processTemplate={}",
                    type, iteration.getOid(), status, wanted);
        }
        return mapByType(type);
    }

    @Override
    @Transactional
    public void clearByType(String typeOid) {
        String type = trimToNull(typeOid);
        if (type == null) {
            return;
        }
        int removed = mapper.deleteByTypeOid(type);
        if (removed > 0) {
            // 类型换模板了：旧模板下的「状态 → 流程」对它已无意义，
            // 留着会变成"界面看不到却挡着流程模板删除"的幽灵引用
            log.info("类型改绑生命周期模板，已清理其状态→流程配置: type={} 共 {} 条", type, removed);
        }
    }

    @Override
    public TypeLifecycleStateProcessLink resolveLink(String typeOid, String statusCode, String iterationOid) {
        String type = trimToNull(typeOid);
        String status = trimToNull(statusCode);
        if (type == null || status == null) {
            return null;
        }
        // 1) 业务对象迭代固化的子版本优先：那是它"出生"时用的那一版配置
        String pinned = trimToNull(iterationOid);
        if (pinned != null) {
            TypeLifecycleStateProcessLink pinnedRow = pickPreferred(
                    mapper.selectByTypeIterationStatus(type, pinned, status));
            if (pinnedRow != null) {
                return pinnedRow;
            }
        }
        // 2) 回落到该类型当前（最新）子版本的配置：状态是后来才配的，也要能发起
        LifecycleTemplateIteration current = resolveCurrentIteration(type);
        if (current == null || current.getOid().equals(pinned)) {
            return null;
        }
        return pickPreferred(mapper.selectByTypeIterationStatus(type, current.getOid(), status));
    }

    @Override
    @Transactional
    public void clearByIterations(List<String> lifecycleTemplateIterationOids) {
        if (lifecycleTemplateIterationOids == null || lifecycleTemplateIterationOids.isEmpty()) {
            return;
        }
        int removed = mapper.deleteByIterations(lifecycleTemplateIterationOids);
        if (removed > 0) {
            log.info("生命周期模板被删除，其状态→流程配置一并清理: {} 条", removed);
        }
    }

    @Override
    public int countByProcessTemplate(String processTemplateOid) {
        String oid = trimToNull(processTemplateOid);
        if (oid == null) {
            return 0;
        }
        // 只数【最新子版本】上的配置：
        //  - 界面展示与可操作的就是它，用户解绑后必然能删；
        //  - 继承会让同一份配置在多个子版本各留一行（历史留痕，供在途实例按旧子版本解析），
        //    把历史行算进来会让用户在"界面上找不到引用处"的情况下删不掉流程模板。
        Map<String, String> currentByType = new HashMap<>();
        Set<String> places = new LinkedHashSet<>();
        for (TypeLifecycleStateProcessLink row : mapper.selectByProcessTemplateOid(oid)) {
            String type = row.getTypeOid();
            String current = currentByType.computeIfAbsent(type, this::currentIterationOid);
            if (current != null && current.equals(row.getLifecycleTemplateIterationOid())) {
                // 键含租户：各租户各配一份，是"几处"就报几处（否则会把两个租户的配置算成一处）
                places.add(row.getTenantOid() + "\u0000" + type + "\u0000" + row.getStatusCode());
            }
        }
        return places.size();
    }

    @Override
    @Transactional
    public void inherit(String oldIterationOid, String newIterationOid) {
        String oldIteration = trimToNull(oldIterationOid);
        String newIteration = trimToNull(newIterationOid);
        if (oldIteration == null || newIteration == null || oldIteration.equals(newIteration)) {
            return;
        }
        List<TypeLifecycleStateProcessLink> oldRows = mapper.selectByIterationOid(oldIteration);
        if (oldRows.isEmpty()) {
            return;
        }
        Set<String> validStatuses = statusCodesOf(newIteration);
        String operator = UserContext.get();
        LocalDateTime now = LocalDateTime.now();
        int inherited = 0;
        for (TypeLifecycleStateProcessLink old : oldRows) {
            // 新版本已删掉的状态不再继承（配置了也没有状态可挂）
            if (!validStatuses.contains(old.getStatusCode())) {
                continue;
            }
            // 继承是"逐条旧行搬过去"，行原样保留自己的租户：同租户在新版本已有该状态才跳过
            if (rowOfTenant(mapper.selectByTypeIterationStatus(
                    old.getTypeOid(), newIteration, old.getStatusCode()), old.getTenantOid()) != null) {
                continue;
            }
            TypeLifecycleStateProcessLink copy = new TypeLifecycleStateProcessLink(
                    old.getTypeOid(), newIteration, old.getStatusCode(), old.getProcessTemplateOid());
            copy.setOid(UUID.randomUUID().toString());
            copy.setTenantOid(old.getTenantOid());
            copy.setCreator(operator);
            copy.setCreatedAt(now);
            copy.setUpdater(operator);
            copy.setUpdatedAt(now);
            mapper.insert(copy);
            inherited++;
        }
        log.info("生命周期模板新子版本已继承状态→流程配置: 旧版本 {} 条 → 新版本 {} 条（丢弃已删状态 {} 条）",
                oldRows.size(), inherited, oldRows.size() - inherited);
    }

    // ==================== 私有工具 ====================

    /**
     * 同一 (类型, 子版本, 状态) 可能同时有多租户的行（各租户各配一份、平台行作共享默认），
     * 按读取优先级排序：<b>本租户 → 平台租户 → 其他</b>。排序稳定，同级保持数据库返回顺序。
     */
    private static List<TypeLifecycleStateProcessLink> preferCurrentTenant(List<TypeLifecycleStateProcessLink> rows) {
        if (rows == null || rows.size() < 2) {
            return rows == null ? Collections.emptyList() : rows;
        }
        String tenant = TenantContext.get();
        List<TypeLifecycleStateProcessLink> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(row -> tenantPriority(row.getTenantOid(), tenant)));
        return sorted;
    }

    /** 单行场景取优先级最高的一条（本租户 → 平台 → 其他）；一条都没有则 null */
    private static TypeLifecycleStateProcessLink pickPreferred(List<TypeLifecycleStateProcessLink> rows) {
        List<TypeLifecycleStateProcessLink> sorted = preferCurrentTenant(rows);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /** 在这些行里找指定租户的那条 —— 写操作（改绑/解绑/继承去重）只认自己租户的行 */
    private static TypeLifecycleStateProcessLink rowOfTenant(List<TypeLifecycleStateProcessLink> rows, String tenantOid) {
        if (rows == null) {
            return null;
        }
        for (TypeLifecycleStateProcessLink row : rows) {
            if (Objects.equals(tenantOid, row.getTenantOid())) {
                return row;
            }
        }
        return null;
    }

    private static int tenantPriority(String rowTenant, String currentTenant) {
        if (rowTenant != null && rowTenant.equals(currentTenant)) {
            return 0;   // 本租户的行
        }
        if (TenantContext.PLATFORM_TENANT_OID.equals(rowTenant)) {
            return 1;   // 平台租户的行 = 共享默认
        }
        return 2;       // 其他租户
    }

    /**
     * 该类型"当前该用/该配"的生命周期模板子版本 = 其绑定模板的<b>最新子版本</b>。
     *
     * <p>刻意不做"最新版没有配置就回溯旧版"的兜底：那会让用户在最新版解绑后，
     * 旧版的行重新变成生效 —— 配置复活。旧版本的行留给运行期按实例子版本解析。
     */
    private LifecycleTemplateIteration resolveCurrentIteration(String typeOid) {
        TypeLifecycleTemplateLink link = typeTemplateLinkMapper.selectByTypeOid(typeOid);
        String code = link == null ? null : trimToNull(link.getLifecycleTemplateCode());
        if (code == null) {
            return null;
        }
        LifecycleTemplateMaster master = templateMapper.selectByCode(code);
        return master == null ? null : iterationMapper.selectLatestByMasterOid(master.getOid());
    }

    /** 该类型当前子版本 oid（引用计数用）；未绑模板或模板无子版本时返回 null */
    private String currentIterationOid(String typeOid) {
        LifecycleTemplateIteration iteration = resolveCurrentIteration(typeOid);
        return iteration == null ? null : iteration.getOid();
    }

    /** 某子版本包含的状态 code 集合（子版本不存在时抛异常） */
    private Set<String> statusCodesOf(String lifecycleTemplateIterationOid) {
        LifecycleTemplateIteration iteration = iterationMapper.selectByOid(lifecycleTemplateIterationOid);
        if (iteration == null) {
            throw new IllegalArgumentException("生命周期模板子版本不存在: " + lifecycleTemplateIterationOid);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (LifecycleTemplateStatusRef ref : templateMapper.selectStateRefsByIterationOid(iteration.getOid())) {
            if (ref.getStatusCode() != null && !ref.getStatusCode().trim().isEmpty()) {
                codes.add(ref.getStatusCode().trim());
            }
        }
        return codes;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
