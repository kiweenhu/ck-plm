/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.home.mapper;

import cn.ck.plm.home.dto.RecentObjectVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我最近创建/修改的对象」数据访问契约。
 *
 * <p>与检出模块的 {@code CheckoutProvider} 属同一族：都是"跨实体类型的对象聚合"，
 * 实体类型清单也与之保持一致（部件 / 文档 / 工程数据）。
 */
public interface RecentObjectMapper {

    /**
     * 某类型下，此用户在时间窗口内创建或修改过的对象（按对象去重，取最近一次）。
     *
     * @param entityType     实体类型编码（PART / DOCUMENT / ENG_DOCUMENT）
     * @param entityTypeName 实体类型中文名（展示用）
     * @param masterTable    主表名（内部常量，不接受外部输入）
     * @param iterTable      版本表名（内部常量，不接受外部输入）
     * @param linkPath       前端跳转路径（与检出列表同一套）
     * @param tenantOid      租户 oid
     * @param user           用户名（对象表的 creator/updater 存的就是用户名）
     * @param since          时间窗口起点
     * @param limit          该类型最多返回几条
     */
    List<RecentObjectVO> selectRecentlyTouched(String entityType, String entityTypeName,
                                               String masterTable, String iterTable, String linkPath,
                                               String tenantOid, String user,
                                               LocalDateTime since, int limit);
}
