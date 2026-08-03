/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.product.entity.Folder;
import cn.ck.plm.product.mapper.FolderMapper;
import cn.ck.plm.product.service.api.FolderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link FolderService} 的数据库实现。
 */
@Service
public class FolderServiceImpl implements FolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderServiceImpl.class);

    private final FolderMapper folderMapper;

    public FolderServiceImpl(FolderMapper folderMapper) {
        this.folderMapper = folderMapper;
    }

    @Override
    @Transactional
    public Folder create(Folder folder) {
        if (folder.getName() == null || folder.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("文件夹名称不能为空");
        }
        if (folder.getOwnerOid() == null || folder.getOwnerOid().trim().isEmpty()) {
            throw new IllegalArgumentException("所属业务对象不能为空");
        }
        if (folder.getStageOid() == null || folder.getStageOid().trim().isEmpty()) {
            throw new IllegalArgumentException("所属阶段不能为空");
        }

        // 用户通过 API 创建的文件夹默认 type = USER
        if (folder.getType() == null || folder.getType().trim().isEmpty()) {
            folder.setType(Folder.TYPE_USER);
        }

        String name = folder.getName().trim();
        String parentOid = folder.getParentFolderOid();

        // 检查同一业务对象 + 同一父级下是否有重名
        if (folderMapper.existsByName(parentOid, name, null, folder.getOwnerOid()) > 0) {
            throw new IllegalArgumentException("同级下已存在同名文件夹 '" + name + "'");
        }

        if (folder.getSortOrder() == null) {
            folder.setSortOrder(0);
        }

        folderMapper.insert(folder);
        return folder;
    }

    @Override
    @Transactional
    public Folder update(Folder folder) {
        if (folder.getOid() == null) {
            throw new IllegalArgumentException("文件夹 oid 不能为空");
        }
        Folder existing = folderMapper.selectByOid(folder.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("文件夹不存在");
        }

        if (folder.getName() != null && !folder.getName().trim().isEmpty()) {
            String name = folder.getName().trim();
            if (folderMapper.existsByName(existing.getParentFolderOid(), name, folder.getOid(), existing.getOwnerOid()) > 0) {
                throw new IllegalArgumentException("同级下已存在同名文件夹 '" + name + "'");
            }
            existing.setName(name);
        }
        if (folder.getSortOrder() != null) {
            existing.setSortOrder(folder.getSortOrder());
        }

        folderMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        Folder existing = folderMapper.selectByOid(oid);
        if (existing == null) {
            return false;
        }
        if (existing.isSystem()) {
            throw new IllegalArgumentException("系统文件夹不可删除");
        }
        // 递归删除子文件夹
        deleteChildren(oid);
        folderMapper.deleteByOid(oid);
        return true;
    }

    private void deleteChildren(String parentOid) {
        List<Folder> children = folderMapper.selectByParentOid(parentOid);
        for (Folder child : children) {
            deleteChildren(child.getOid());
            folderMapper.deleteByOid(child.getOid());
        }
    }

    @Override
    public Folder findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return folderMapper.selectByOid(oid);
    }

    @Override
    public List<Folder> findTree(String ownerOid, String stageOid) {
        List<Folder> roots = folderMapper.selectRoots(ownerOid, stageOid);
        for (Folder root : roots) {
            buildChildren(root);
        }
        return roots;
    }

    @Override
    public List<Folder> findByOwnerAndStage(String ownerOid, String stageOid) {
        return folderMapper.selectByOwnerAndStage(ownerOid, stageOid);
    }

    @Override
    @Transactional
    public void initSystemFolders(String ownerOid, String stageOid, List<String> folderNames) {
        if (ownerOid == null || stageOid == null || folderNames == null) {
            return;
        }
        for (int i = 0; i < folderNames.size(); i++) {
            String name = folderNames.get(i);
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            Folder folder = new Folder();
            folder.setOwnerOid(ownerOid);
            folder.setStageOid(stageOid);
            folder.setName(name.trim());
            folder.setType(Folder.TYPE_SYSTEM);
            folder.setSortOrder(i);
            try {
                create(folder);
            } catch (IllegalArgumentException e) {
                // 同名已存在则跳过
                log.debug("跳过系统文件夹创建: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<Folder> findAllTree() {
        List<Folder> roots = folderMapper.selectAllRoots();
        for (Folder root : roots) {
            buildChildren(root);
        }
        return roots;
    }

    private void buildChildren(Folder parent) {
        List<Folder> children = folderMapper.selectByParentOid(parent.getOid());
        parent.setChildren(children);
        for (Folder child : children) {
            buildChildren(child);
        }
    }
}
