/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.service.api;

import cn.ck.plm.product.entity.Folder;

import java.util.List;

/**
 * 文件夹管理服务接口。
 */
public interface FolderService {

    /** 创建文件夹 */
    Folder create(Folder folder);

    /** 更新文件夹（仅名称和排序） */
    Folder update(Folder folder);

    /** 删除文件夹及其子文件夹（系统文件夹不可删除） */
    boolean delete(String oid);

    /** 根据 oid 查询 */
    Folder findByOid(String oid);

    /** 查询指定业务对象 + 阶段下的文件夹树 */
    List<Folder> findTree(String ownerOid, String stageOid);

    /** 查询指定业务对象 + 阶段下的所有文件夹（扁平列表） */
    List<Folder> findByOwnerAndStage(String ownerOid, String stageOid);

    /**
     * 为业务对象的某个阶段批量创建系统默认文件夹。
     *
     * @param ownerOid     业务对象 oid（产品线/产品型号等）
     * @param stageOid     阶段 oid
     * @param folderNames  文件夹名称列表（按顺序创建，sortOrder 从 0 递增）
     */
    void initSystemFolders(String ownerOid, String stageOid, List<String> folderNames);

    /** 查询所有文件夹树（不限定产品线和阶段，用于页面设计器预览等场景） */
    List<Folder> findAllTree();
}
