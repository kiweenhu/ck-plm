package cn.ck.plm.cls.mapper.impl.postgresql;

import cn.ck.plm.cls.mapper.ClsIbaDataMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlClsIbaDataMapper extends ClsIbaDataMapper {

    @Override
    @Delete("DELETE FROM ck_cls_iba_data WHERE entity_oid = #{entityOid} AND classification_oid = #{classificationOid}")
    int deleteByEntity(@Param("entityOid") String entityOid,
                       @Param("classificationOid") String classificationOid);

    @Override
    @Insert("INSERT INTO ck_cls_iba_data (entity_oid, classification_oid, attr_code, attr_value, tenant_oid, creator, updater) "
            + "VALUES (#{entityOid}, #{classificationOid}, #{attrCode}, #{attrValue}::jsonb, #{tenantOid}, #{creator}, #{updater}) "
            + "ON CONFLICT (entity_oid, classification_oid, attr_code) DO UPDATE SET "
            + "attr_value = EXCLUDED.attr_value, updater = EXCLUDED.updater, updated_at = CURRENT_TIMESTAMP")
    int insert(@Param("entityOid") String entityOid,
               @Param("classificationOid") String classificationOid,
               @Param("attrCode") String attrCode,
               @Param("attrValue") String attrValue,
               @Param("tenantOid") String tenantOid,
               @Param("creator") String creator,
               @Param("updater") String updater);

    @Override
    @Select("SELECT attr_code, attr_value::text AS attr_value FROM ck_cls_iba_data "
            + "WHERE entity_oid = #{entityOid} AND classification_oid = #{classificationOid} "
            + "ORDER BY attr_code")
    List<Map<String, Object>> selectByEntity(@Param("entityOid") String entityOid,
                                             @Param("classificationOid") String classificationOid);

    @Override
    @Select("SELECT attr_value::text FROM ck_cls_iba_data "
            + "WHERE entity_oid = #{entityOid} AND classification_oid = #{classificationOid} AND attr_code = #{attrCode}")
    String selectAttrValue(@Param("entityOid") String entityOid,
                           @Param("classificationOid") String classificationOid,
                           @Param("attrCode") String attrCode);
}
