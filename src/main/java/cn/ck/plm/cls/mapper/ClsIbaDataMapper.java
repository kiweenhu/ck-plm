package cn.ck.plm.cls.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分类 IBA 数据 Mapper 接口，操作 ck_cls_iba_data 表。
 * <p>
 * 采用 EAV 行存储模式：每个对象实例（entity_oid）的每个分类的每个 IBA 属性对应一条记录。
 * entity_oid 为空字符串时表示「分类节点默认值」（分类管理界面配置），
 * 非空时表示具体对象实例（Part/Document）的分类 IBA 属性值。
 */
public interface ClsIbaDataMapper {

    /**
     * 删除指定对象实例在指定分类下的全部 IBA 属性数据。
     */
    int deleteByEntity(@Param("entityOid") String entityOid,
                       @Param("classificationOid") String classificationOid);

    /**
     * 插入一条 IBA 属性值。
     */
    int insert(@Param("entityOid") String entityOid,
               @Param("classificationOid") String classificationOid,
               @Param("attrCode") String attrCode,
               @Param("attrValue") String attrValue,
               @Param("tenantOid") String tenantOid,
               @Param("creator") String creator,
               @Param("updater") String updater);

    /**
     * 查询指定对象实例在指定分类下的全部 IBA 属性数据，返回 [{attr_code, attr_value}, ...]。
     */
    List<Map<String, Object>> selectByEntity(@Param("entityOid") String entityOid,
                                             @Param("classificationOid") String classificationOid);

    /**
     * 查询指定对象实例在指定分类下的单个 IBA 属性值。
     */
    String selectAttrValue(@Param("entityOid") String entityOid,
                           @Param("classificationOid") String classificationOid,
                           @Param("attrCode") String attrCode);
}
