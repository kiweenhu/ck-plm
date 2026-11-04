/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.api;

import cn.ck.plm.product.dto.MethodologyInstances;
import cn.ck.plm.product.service.impl.ProductTeamRoleResolver;

import java.util.Map;

/**
 * 方法论实例服务：创建产品系列 / 型号时，把本租户的六类方法论模板<b>快照</b>成该归属单元的实例。
 *
 * <h3>六类</h3>
 * 交付件（ck_deliverable）/ 阶段门（ck_gate）/ 评审（ck_review）/
 * 检查规则（ck_rule）/ 指标（ck_metric）/ 驾驶舱（ck_dashboard）。
 *
 * <h3>为什么是快照而不是引用</h3>
 * 实例化即定型：模板以后改了不回灌，实例上的裁剪也不会被模板覆盖 —— 可追溯，
 * 且与研发阶段（{@code ck_stage} ← {@code ck_stage_template}）的现行做法一致。
 *
 * <h3>幂等</h3>
 * 以「该归属单元是否已有该类实例」为守卫：已有即跳过（返回现有条数），不会重复插入。
 * 故本方法可以安全地在创建流程里调用，也适合将来做存量补齐。
 */
public interface MethodologyInstanceService {

    /**
     * 为归属单元初始化六类方法论实例。
     *
     * @param ownerOid  产品系列或产品型号的 oid
     * @param ownerType 归属类型，取 {@code Stage.OWNER_TYPE_LINE} / {@code Stage.OWNER_TYPE_MODEL}
     * @return 每类实例的条数（key：deliverable / gate / review / rule / metric / dashboard）
     */
    Map<String, Integer> initFor(String ownerOid, String ownerType);

    /**
     * 取某归属单元的全部六类实例（系列 / 型号页展示用，一次往返）。
     *
     * @param ownerOid 产品系列或产品型号的 oid；为空时返回"六个空列表"而不是抛错
     *                 （页面还没选中归属时不该报红）
     */
    MethodologyInstances findByOwner(String ownerOid);

    /**
     * 裁剪一条实例：标记「本项目不适用」（{@code disabled}）或恢复；
     * 交付件还可改强制项与**责任人**。
     *
     * <p><b>作用范围 = 本归属单元</b>（系列 / 型号）：模板与其它项目不受影响 ——
     * 与模板层"企业裁剪"（改以后新建的项目发什么）是两层，别混。
     *
     * <p><b>权限改为项目级（2026-10-14 用户要求）</b>：由"我在<b>该系列 / 型号团队</b>里的角色"决定 ——
     * 团队角色 = 产品经理（{@code 产品经理} / 经 {@code ck_role} 映射为 {@code PRODUCT_MANAGER}）可裁剪；
     * 管理员（{@code PLATFORM_ADMIN / TENANT_ADMIN}）豁免。判定见 {@link ProductTeamRoleResolver}。
     *
     * @param kind      四类之一：{@code deliverable / gate / review / rule}
     * @param oid       实例 oid
     * @param disabled  停用（本项目不适用）或恢复；{@code null} = 不改这一项
     * @param mandatory 改交付件的强制项；{@code null} = 不改（其余三类不支持，给了就报错）
     * @param ownerRole 改交付件的**责任人**（角色码，如 {@code PRODUCT_MANAGER}）；
     *                  {@code null} = 不改（其余三类不支持，给了就报错）
     * @throws IllegalArgumentException 类别不支持 / oid 为空 / 三个字段都没给 /
     *                                  非交付件却要改强制项或责任人 / 实例不存在
     * @throws SecurityException        当前用户在该归属单元的团队角色不是产品经理（且非管理员）
     */
    void tailor(String kind, String oid, Boolean disabled, Boolean mandatory, String ownerRole);
}
