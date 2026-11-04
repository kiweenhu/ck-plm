/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

/**
 * 流程实例结束后的业务回调 —— 业务模块实现它，把"审批结论"回写成自己的状态。
 *
 * <h3>契约</h3>
 * <ul>
 *   <li>{@link #supports(String)} 按流程 key 过滤：一个 handler 只处理自己认识的流程，
 *       避免每个流程结束都惊动全部业务模块；</li>
 *   <li>{@link #onFinished(ProcessOutcome)} <b>必须自己吞掉异常</b>（或抛出后由分发方兜底）——
 *       流程已经结束，回调失败不能回滚办理，更不能让用户看到"点同意报错"；</li>
 *   <li>实现方要<b>幂等</b>：同一次结束可能被重复分发（重试、人工补发），
 *       先判业务状态再改，不要无条件覆盖。</li>
 * </ul>
 *
 * <h3>怎么被调用</h3>
 * <p>{@code ProcessServiceImpl#notifyIfFinished} 在确认实例已结束后按
 * {@link #supports} 分发。用 {@code ObjectProvider} 延迟取 bean，避免
 * "流程服务 ↔ 业务服务"的构造器循环依赖。
 */
public interface ProcessOutcomeHandler {

    /** 是否处理该流程定义 key（BPMN 的 process id） */
    boolean supports(String processKey);

    /**
     * 流程结束后回写业务状态。
     *
     * @param outcome 实例结论（含变量快照）；不会为 null
     */
    void onFinished(ProcessOutcome outcome);
}
