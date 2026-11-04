/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Review;

import java.util.List;

/**
 * 评审实例（ck_review）数据访问接口 —— 业务表，租户由拦截器注入，语句不显式写租户条件。
 */
public interface ReviewMapper {

    int insert(Review review);

    Review selectByOid(String oid);

    List<Review> selectByOwnerOid(String ownerOid);

    int countByOwnerOid(String ownerOid);

    int deleteByOwnerOid(String ownerOid);

    /**
     * 裁剪：停用 / 恢复（"本项目不适用"）—— 只影响本归属单元，不删条目、可随时恢复。
     */
    int updateDisabled(String oid, boolean disabled);
}
