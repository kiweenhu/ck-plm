/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.dto.LifecycleStateOptionsVO;

/**
 * 「该类型能设到哪些生命周期状态」—— 行操作「设置生命周期状态」弹窗的候选来源。
 *
 * <p>依据是<b>类型绑定的生命周期模板</b>（{@code ck_type_lifecycle_template_link} →
 * 模板的最新子版本）：状态清单、初始状态、以及"从当前状态一步能迁到谁"的规则都在那里。
 * 与流程里的「设置状态」服务节点、跑批的状态迁移用的是同一份模板，不会出现
 * "界面让选、后端拒收"。
 */
public interface TypeLifecycleStateService {

    /**
     * 解析该类型的状态候选项（按业务配置的模板；兼容旧调用）。
     *
     * @param typeDefinitionCode 类型编码（如 ELECTRONIC）
     * @param currentStatusCode  对象当前状态 code（可为 null：对象还没有状态）
     * @return 候选项；未绑定模板时 {@code reason} 说明原因、{@code states} 为空
     */
    LifecycleStateOptionsVO options(String typeDefinitionCode, String currentStatusCode);

    /**
     * 同上，并带上对象当前的模板绑定，用于提示「对象仍绑着旧模板」。
     *
     * <p><b>候选一律按业务配置的模板给</b>：设置状态 = 在类型当前绑定的模板上设置。
     * 历史对象（绑着旧模板 / 旧版本，或根本没绑）会在同一次操作里先切换到新模板，
     * 候选必须与新模板一致，否则会出现"界面给旧模板的状态、后端按新模板拒收"。
     * VO 里同时带上迁移后的状态映射（{@code migrationStatusCode}）供界面提示。
     *
     * @param boundTemplateIterationOid 对象迭代里固化的模板子版本 oid，可为 null（未绑定 / 已失效）
     */
    LifecycleStateOptionsVO options(String typeDefinitionCode, String currentStatusCode,
                                    String boundTemplateIterationOid);

    /**
     * 业务配置里该类型绑定模板的<b>最新子版本</b> oid（即"迁移到新模板"的目标）；未绑定返回 null。
     */
    String configuredTemplateIteration(String typeDefinitionCode);
}
