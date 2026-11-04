/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper;

import cn.ck.plm.bom.entity.BomSnapshot;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BomSnapshot 数据访问接口，定义数据库无关的持久化契约。
 */
public interface BomSnapshotMapper {

    /** 插入快照（oid / creator / tenant 由审计与租户拦截器补齐） */
    int insert(BomSnapshot snapshot);

    int deleteByOid(String oid);

    BomSnapshot selectByOid(String oid);

    /**
     * 快照记录列表。
     *
     * @param partOid      按零件主对象过滤（为空不过滤）—— 页面按零件看全部版本的快照
     * @param iterationOid 按迭代过滤（为空不过滤）
     */
    List<BomSnapshot> selectList(@Param("partOid") String partOid,
                                 @Param("iterationOid") String iterationOid);
}
