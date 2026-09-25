/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.home.service.api;

import cn.ck.plm.home.dto.RecentObjectVO;

import java.util.List;

/**
 * 「我最近创建/修改过的对象」服务契约 —— 个人中心用。
 */
public interface RecentObjectService {

    /**
     * 查询当前用户最近创建或修改过的业务对象（跨类型，按时间倒序）。
     *
     * @param days  时间窗口（天，1~90）
     * @param limit 总条数上限（1~50）
     */
    List<RecentObjectVO> findMyRecentObjects(int days, int limit);
}
