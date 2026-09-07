/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.Part;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Part 主对象数据访问接口，定义数据库无关的持久化契约。
 */
public interface PartMapper {

    int insert(Part part);

    int update(Part part);

    int deleteByOid(String oid);

    Part selectByOid(String oid);

    List<Part> selectByContainerOid(String containerOid);

    /** 按所属容器 + 关键字（名称/编码）模糊查询，keyword 为空时返回容器下全部 */
    List<Part> selectByContainerAndKeyword(@Param("containerOid") String containerOid, @Param("keyword") String keyword);

    /** 按所属容器 + 阶段查询 */
    List<Part> selectByContainerAndStage(String containerOid, String stageOid);

    /** 按文件夹查询 */
    List<Part> selectByFolderOid(String folderOid);

    /** 按分类查询 */
    List<Part> selectByClassificationOid(String classificationOid);

    /** 统计绑定了指定分类的零组件数量 */
    int countByClassificationOid(@Param("classificationOid") String classificationOid);

    List<Part> selectAll();
}
