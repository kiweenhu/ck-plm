package cn.ck.plm.cls.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 分类 IBA 数据 Mapper 接口，操作 ck_cls_iba_data 表。
 * <p>
 * 采用 EAV 行存储模式：每个分类的每个 IBA 属性对应一条记录。
 */
public interface ClsIbaDataMapper {

    /**
     * 删除指定分类的全部 IBA 属性数据。
     */
    int deleteByClassificationOid(@Param("classificationOid") String classificationOid);

    /**
     * 插入一条 IBA 属性值。
     */
    int insert(@Param("classificationOid") String classificationOid,
               @Param("attrCode") String attrCode,
               @Param("attrValue") String attrValue,
               @Param("tenantOid") String tenantOid,
               @Param("creator") String creator,
               @Param("updater") String updater);

    /**
     * 查询指定分类的全部 IBA 属性数据，返回 Map&lt;attrCode, attrValue&gt;。
     */
    Map<String, Object> selectByClassificationOid(@Param("classificationOid") String classificationOid);

    /**
     * 查询指定分类的单个 IBA 属性值。
     */
    String selectAttrValue(@Param("classificationOid") String classificationOid,
                           @Param("attrCode") String attrCode);
}
