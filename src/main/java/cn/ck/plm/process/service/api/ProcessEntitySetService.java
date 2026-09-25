/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.dto.ProcessStartConflictVO;
import cn.ck.plm.process.entity.ProcessEntitySet;

import java.util.List;

/**
 * 流程实例 ↔ 业务实体 关联服务。
 *
 * <p>用途（取代流程模板上原来的单一 {@code primary_object_type}）：
 * <ul>
 *   <li>正向：这个流程实例关联了哪些业务实体（流程详情 / 监控展示）；</li>
 *   <li>反向：这个业务对象（或其某个大版本）参与过哪些流程。</li>
 * </ul>
 *
 * <p><b>实体引用规则</b>：无版本对象只填 {@code entityOid}；带版本对象填 {@code entityVersion}
 * （业务对象的<b>大版本</b>，如 A，同时保留 {@code entityOid} = 主对象 oid）。记大版本而不是
 * 迭代 oid：流程针对的是大版本，其下还会继续产出小版本（A.2 → A.3…），记迭代 oid 会随对象
 * 推进而过时。{@code rootTypeCode}（能力宿主）调用方没给时，由本服务按 {@code typeCode} 补。
 */
public interface ProcessEntitySetService {

    /**
     * 记录一条关联（<b>幂等</b>）。
     *
     * <p>同一流程实例重复记录同一实体时静默跳过（重试 / 重复点击不该产生第二行）；
     * 会补齐 oid、租户、审计字段与 {@code rootTypeCode}。
     *
     * @return 落库的那条（含补齐后的字段）
     * @throws IllegalArgumentException 流程实例 id 或业务实体 oid 为空
     */
    ProcessEntitySet record(ProcessEntitySet row);

    /** 某流程实例关联的全部业务实体 */
    List<ProcessEntitySet> findByProcessInstanceId(String processInstanceId);

    /** 某业务实体（主对象）参与过的全部流程关联 */
    List<ProcessEntitySet> findByEntityOid(String entityOid);

    /** 某业务对象在指定<b>大版本</b>下参与过的全部流程关联（按大版本反查） */
    List<ProcessEntitySet> findByEntityOidAndEntityVersion(String entityOid, String entityVersion);

    /** 按发起时传入的业务标识查询 */
    List<ProcessEntitySet> findByBusinessKey(String businessKey);

    /**
     * 「发起流程」闸门：按【业务对象 + 大版本】判定该版本是否已有流程在执行。
     *
     * <p><b>粒度是大版本，不是整个对象</b>：同一对象的大版本之间彼此独立 —— B 版在跑流程时，
     * A 版的收尾工作不该被挡在门外（大版本本就是两轮工作）。判定依据＝本表的
     * {@code (entity_oid, version)} 关联 + 流程引擎运行时状态：
     * <ul>
     *   <li>是否"在跑"以引擎运行时为准：已结束的不算，<b>挂起仍算</b>（它没有结束）；</li>
     *   <li>只在本租户内判定（本表是业务表，租户过滤由拦截器注入）。</li>
     * </ul>
     *
     * <p>{@code version} 没给时按对象<b>当前大版本</b>解析（与写关联时同一套口径）——
     * 闸门放行的版本与记进关联的版本必须是同一个，否则会出现"这次放行、那次挡住"。
     *
     * @param entityOid     业务对象主 oid；为空时返回"未被拦住"的结果（不做判定）
     * @param typeCode      类型编码（可空；解析"当前大版本"所在的宿主用）
     * @param entityVersion 大版本（可空 = 按对象当前大版本）
     */
    ProcessStartConflictVO resolveStartConflict(String entityOid, String typeCode, String entityVersion);

    /**
     * 批量判定：这些业务对象里，哪些的<b>当前大版本</b>已有流程在执行中。
     *
     * <p>给「发起流程」弹窗的候选列表用 —— 正在流程中的对象不该出现在可选范围里
     * （真实反馈：候选里能看到正在跑的，选到提交时又被拒）。判定刻意复用
     * {@link #resolveStartConflict} 逐个来做，不另写一段 SQL：候选过滤的口径与
     * "能不能发起"必须永远一致（大版本粒度、挂起仍算在跑、只在租户内），
     * 两套口径的后果就是"列表里能选、点了却被拒"。
     *
     * <p>先用一条 IN 查询粗筛（只有参与过流程的对象才值得逐个体检），
     * 所以常见路径是"1 次查询 + 0 次引擎问询"。
     *
     * @param entityOids 业务对象 oid 列表（可空 / 可重复）
     * @param typeCode   类型编码（解析"对象当前大版本"要用；给不准会退化成"没有在跑"）
     * @return 已有流程在执行中的对象 oid（保持入参顺序、已去重）
     */
    List<String> findRunningEntityOids(List<String> entityOids, String typeCode);

    /**
     * 「该版本已有流程在执行中」的统一措辞（动作 = 发起）。
     *
     * <p>发起闸门（{@code ProcessInstanceController#start}）与发起前解析
     * （{@code ProcessStartOptionServiceImpl}）共用一句，避免两处提示不一致。
     * 带上大版本与"该版本当前最新小版本 · 状态"，让用户知道<b>忙的是哪一版</b>。
     */
    static String conflictMessage(ProcessStartConflictVO conflict) {
        return conflictMessage(conflict, "发起");
    }

    /**
     * 同上，但由调用方给出「被挡住的是哪个动作」—— 同一个闸门也挡"手工设置生命周期状态"
     * （对象在流程中时不允许改状态），说成"请先完成后再发起"会让人以为点错了入口。
     *
     * @param actionLabel 动作名，如「发起」「设置生命周期状态」
     */
    static String conflictMessage(ProcessStartConflictVO conflict, String actionLabel) {
        StringBuilder where = new StringBuilder();
        where.append(conflict.getEntityVersion() == null
                ? "该业务对象"
                : "该业务对象的版本「" + conflict.getEntityVersion() + "」");
        if (conflict.getDisplayVersion() != null) {
            where.append("（当前 ").append(conflict.getDisplayVersion());
            String status = conflict.getStatusName() != null ? conflict.getStatusName() : conflict.getStatusCode();
            if (status != null) {
                where.append(" · ").append(status);
            }
            where.append("）");
        }
        String ids = String.join("、", conflict.getRunningInstanceIds());
        String tail = "：请先完成或终止该流程后再" + actionLabel;
        return conflict.getRunningInstanceIds().size() == 1
                ? where + "已有流程在执行中（实例 " + ids + "）" + tail
                : where + "已有 " + conflict.getRunningInstanceIds().size() + " 个流程在执行中（实例 " + ids + "）" + tail;
    }

    /** 清理某流程实例的全部关联（流程实例被删除时一并清理，避免悬挂） */
    void removeByProcessInstanceId(String processInstanceId);
}
