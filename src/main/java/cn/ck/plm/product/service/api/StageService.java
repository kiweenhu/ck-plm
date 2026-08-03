/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.api;

import cn.ck.plm.product.entity.Stage;

import java.util.List;

/**
 * 研发阶段管理服务接口。
 */
public interface StageService {

    /** 初始化归属单元的 6 个默认研发阶段（幂等：已存在则跳过），返回创建的所有阶段列表 */
    List<Stage> initDefaultStages(String ownerOid, String ownerType);

    /** 查询归属单元下所有阶段（按 sortOrder 排序） */
    List<Stage> findByOwnerOid(String ownerOid);

    /** 根据 oid 查询单个阶段 */
    Stage findByOid(String oid);

    /** 更新单个阶段信息 */
    Stage update(Stage stage);

    /** 删除归属单元下所有阶段 */
    int deleteByOwnerOid(String ownerOid);
}
