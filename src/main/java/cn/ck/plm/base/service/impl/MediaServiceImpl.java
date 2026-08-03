/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.Media;
import cn.ck.plm.base.mapper.MediaMapper;
import cn.ck.plm.base.service.api.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link MediaService} 的数据库实现。
 */
@Service
public class MediaServiceImpl implements MediaService {

    private final MediaMapper mediaMapper;

    public MediaServiceImpl(MediaMapper mediaMapper) {
        this.mediaMapper = mediaMapper;
    }

    @Override
    @Transactional
    public Media create(Media media) {
        if (media == null || media.getOriginalName() == null || media.getOriginalName().trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (media.getStoragePath() == null || media.getStoragePath().trim().isEmpty()) {
            throw new IllegalArgumentException("存储路径不能为空");
        }
        mediaMapper.insert(media);
        return media;
    }

    @Override
    @Transactional
    public Media update(Media media) {
        if (media == null || media.getOid() == null) {
            throw new IllegalArgumentException("媒体 oid 不能为空");
        }
        Media existing = mediaMapper.selectByOid(media.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("媒体记录不存在");
        }
        mediaMapper.update(media);
        existing.setOriginalName(media.getOriginalName());
        existing.setDescription(media.getDescription());
        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        Media existing = mediaMapper.selectByOid(oid);
        if (existing == null) {
            return false;
        }
        mediaMapper.deleteByOid(oid);
        return true;
    }

    @Override
    public Media findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return mediaMapper.selectByOid(oid);
    }

    @Override
    public List<Media> findAll() {
        return mediaMapper.selectAll();
    }

    @Override
    public List<Media> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return mediaMapper.search(keyword.trim());
    }

    @Override
    public Map<String, Boolean> checkUsage(List<String> oids) {
        Map<String, Boolean> result = new HashMap<>();
        if (oids == null || oids.isEmpty()) {
            return result;
        }
        // 初始化所有 oid 为未使用
        for (String oid : oids) {
            result.put(oid, false);
        }
        // 查询已被引用的 oid
        List<String> usedOids = mediaMapper.findUsedOids(oids);
        if (usedOids != null) {
            for (String oid : usedOids) {
                result.put(oid, true);
            }
        }
        return result;
    }
}
