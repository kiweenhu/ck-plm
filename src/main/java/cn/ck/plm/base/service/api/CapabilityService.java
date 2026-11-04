/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.TenantCapability;

import java.util.List;
import java.util.Set;

/**
 * 能力服务 —— 读 {@code ck_tenant_capability} 这张"本租户能用哪些功能"的账本。
 *
 * <p>这正是 {@link TenantCapability} 类注释里点明的"读侧（③）"：账本早就有了（装包时幂等写入），
 * 但在此之前没有任何地方按它判定"功能入口该不该显示"。菜单 / 路由 / 页面据此收敛 ——
 * 包里声明而平台未实现的能力**不会出现在账本里**，所以读侧天然不会"假就绪"。
 */
public interface CapabilityService {

    /** 本租户已启用的能力（含名称与来源包，便于排查"我为什么没有这个功能"） */
    List<TenantCapability> listEnabled();

    /** 本租户已启用的能力码集合（前端门控用的最简形态） */
    Set<String> enabledCodes();

    /** 本租户是否启用了某能力 */
    boolean has(String capabilityCode);
}
