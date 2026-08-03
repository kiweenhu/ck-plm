/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper;

import cn.ck.plm.product.entity.Folder;

import java.util.List;

/**
 * 文件夹数据访问接口。
 */
public interface FolderMapper {

    int insert(Folder folder);

    int update(Folder folder);

    int deleteByOid(String oid);

    Folder selectByOid(String oid);

    /** 查询指定业务对象 + 阶段下的根文件夹列表 */
    List<Folder> selectRoots(String ownerOid, String stageOid);

    /** 查询指定父文件夹的直接子文件夹 */
    List<Folder> selectByParentOid(String parentFolderOid);

    /** 查询指定业务对象 + 阶段下的所有文件夹（扁平列表） */
    List<Folder> selectByOwnerAndStage(String ownerOid, String stageOid);

    /** 统计某父文件夹下的子文件夹数量 */
    int countByParentOid(String parentFolderOid);

    /** 检查文件夹名称是否已存在（同一业务对象 + 同一父级下） */
    int existsByName(String parentFolderOid, String name, String excludeOid, String ownerOid);

    /** 查询所有根文件夹列表（不限定产品线和阶段） */
    List<Folder> selectAllRoots();
}
