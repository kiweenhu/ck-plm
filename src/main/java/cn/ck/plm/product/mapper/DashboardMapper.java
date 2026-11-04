/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Dashboard;

import java.util.List;

/**
 * 驾驶舱实例（ck_dashboard）数据访问接口 —— 业务表，租户由拦截器注入，语句不显式写租户条件。
 */
public interface DashboardMapper {

    int insert(Dashboard dashboard);

    Dashboard selectByOid(String oid);

    List<Dashboard> selectByOwnerOid(String ownerOid);

    int countByOwnerOid(String ownerOid);

    int deleteByOwnerOid(String ownerOid);
}
