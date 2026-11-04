/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Metric;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 指标实例（ck_metric）数据访问接口 —— 业务表，租户由拦截器注入，语句不显式写租户条件。
 */
public interface MetricMapper {

    int insert(Metric metric);

    Metric selectByOid(String oid);

    List<Metric> selectByOwnerOid(String ownerOid);

    int countByOwnerOid(String ownerOid);

    int deleteByOwnerOid(String ownerOid);

    /**
     * 写回一次评估结果（{@code value / value_text / readiness / evaluated_at}）。
     *
     * <p><b>只碰这四个字段</b>：口径（layer / level / source / industry_note）随模板快照，
     * 评估过程不改它 —— 口径若被"算数的人"顺手改了，这个数就没人能复核了。
     *
     * <p>{@code value} 传 null 表示"算不出来"（同时用 {@code valueText} 说明缺什么），
     * 不能拿 0 冒充 —— 成本缺单价时算出的 0 会让 GM 以为"成本很低"。
     */
    int updateEvaluation(@Param("oid") String oid,
                         @Param("value") BigDecimal value,
                         @Param("valueText") String valueText,
                         @Param("readiness") String readiness);
}
