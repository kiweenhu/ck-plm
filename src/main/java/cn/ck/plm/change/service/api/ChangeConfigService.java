/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.service.api;

import cn.ck.plm.change.entity.ChangeConfig;

/**
 * 变更租户配置服务（免审开关）。
 *
 * <h3>默认值即策略</h3>
 * <p>读不到配置时返回默认值（{@code approvalRequired = true}），<b>不落库</b>：
 * 一次 GET 不该产生写操作。首次 {@link #update} 时才插入该租户的行。
 */
public interface ChangeConfigService {

    /** 当前租户配置；不存在时返回默认值（不落库） */
    ChangeConfig get();

    /**
     * 更新（{@code null} = 本次不改）。
     *
     * <p>三个流程 key 各自独立：内置三流程是父子链（request → notice → task），
     * 但流程可被用户在设计器里改，改完不一定仍是父子链，故按单据分别配置。
     *
     * @param approvalRequired   是否必须审批（{@code false} = 免审）
     * @param bypassForReleased  免审时是否允许修订已发布对象
     * @param requestProcessKey  变更请求（ECR）流程 key；空串 = 回落内置 change-request
     * @param approvalProcessKey 变更通告（ECN）审批流程 key；空串 = 回落内置 change-notice
     * @param taskProcessKey     变更任务（ECT）流程 key；空串 = 回落内置 change-task
     * @param requestEnabled     是否启用变更请求（{@code false} = 「变更控制」不出现 ECR 页签）
     */
    ChangeConfig update(Boolean approvalRequired, Boolean bypassForReleased,
                        String requestProcessKey, String approvalProcessKey, String taskProcessKey,
                        Boolean requestEnabled);
}
