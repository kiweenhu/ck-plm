/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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
     * 解析该类型的状态候选项。
     *
     * @param typeDefinitionCode 类型编码（如 ELECTRONIC）
     * @param currentStatusCode  对象当前状态 code（可为 null：对象还没有状态）
     * @return 候选项；未绑定模板时 {@code reason} 说明原因、{@code states} 为空
     */
    LifecycleStateOptionsVO options(String typeDefinitionCode, String currentStatusCode);
}
