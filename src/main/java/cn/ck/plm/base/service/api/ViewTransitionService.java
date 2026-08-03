/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.ViewTransition;

import java.util.List;

/**
 * 视图切换规则服务接口。
 */
public interface ViewTransitionService {

    /** 创建切换规则 */
    ViewTransition create(ViewTransition transition);

    /** 更新切换规则 */
    ViewTransition update(ViewTransition transition);

    /** 删除切换规则 */
    boolean delete(String oid);

    /** 按 oid 查询 */
    ViewTransition findByOid(String oid);

    /** 查询从指定视图出发的所有已启用规则 */
    List<ViewTransition> findEnabledByFromViewCode(String fromViewCode);

    /** 查询从指定视图出发的所有规则 */
    List<ViewTransition> findByFromViewCode(String fromViewCode);

    /** 查询所有规则 */
    List<ViewTransition> findAll();

    /** 判断规则是否已存在 */
    boolean exists(String fromViewCode, String toViewCode);
}
