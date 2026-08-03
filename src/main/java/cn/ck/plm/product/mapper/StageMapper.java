/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Stage;

import java.util.List;

/**
 * 研发阶段数据访问接口，定义数据库无关的持久化契约。
 */
public interface StageMapper {

    int insert(Stage stage);

    int update(Stage stage);

    int deleteByOid(String oid);

    Stage selectByOid(String oid);

    /** 按归属单元 + ownerType + stage code 查询 */
    Stage selectByOwnerAndCode(String ownerOid, String ownerType, String code);

    /** 查询归属单元下所有阶段（按 sortOrder 排序） */
    List<Stage> selectByOwnerOid(String ownerOid);

    /** 删除归属单元下全部阶段 */
    int deleteByOwnerOid(String ownerOid);

    /** 归属单元下阶段数量 */
    int countByOwnerOid(String ownerOid);
}
