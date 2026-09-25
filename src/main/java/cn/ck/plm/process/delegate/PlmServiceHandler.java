/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

/**
 * 一个内建服务（SERVICE 白名单）的运行期实现。
 *
 * <p><b>为什么是一个服务一个处理器</b>：编译层把所有服务节点都收敛成
 * 「一个委托 Bean（{@code ${plmServiceDelegate}}）+ {@code flowable:field} 传参」，
 * 服务 id 只是字段值。一个 id 一个 Bean 的做法（{@code ${object.setLifecycleState}}）
 * 是非法 UEL，引擎直接拒绝部署；而一个大 switch 会让"加一个服务"要改核心类。
 * 这里用 Spring 收集（与 {@code CheckoutProvider} 同一套模式）：新增服务 = 新增一个类。
 */
public interface PlmServiceHandler {

    /**
     * 服务 id —— 与前端服务白名单、编译产物 {@code flowable:field name="serviceId"} 三处一致。
     *
     * @see <a href="file:../../../../../../../frontend/src/flow-designer/dsl-core/constants.ts">BUILTIN_SERVICES</a>
     */
    String serviceId();

    /**
     * 服务的显示名（给人看）。
     *
     * <p>默认回落服务 id —— 内置服务的显示名与参数声明在前端描述符里（面板要按它们渲染控件），
     * 这里不必重复维护。<b>自定义函数建议覆写它</b>：让设计者的下拉里直接显示
     * {@code custom.sendMail} 这种 id 很难看懂，而它正是选错函数的主要原因。
     */
    default String label() {
        return serviceId();
    }

    /**
     * 执行服务。
     *
     * <p>失败请抛 {@link PlmServiceException} 并写清"哪个对象、为什么"——
     * 消息会原样出现在办理人的失败提示里（引擎把它包一层后返回）。
     */
    void execute(PlmServiceContext context);
}
