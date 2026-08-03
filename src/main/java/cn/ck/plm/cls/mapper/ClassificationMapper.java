package cn.ck.plm.cls.mapper;

import cn.ck.plm.cls.entity.Classification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类管理 Mapper 接口。
 */
public interface ClassificationMapper {

    int insert(Classification c);

    int update(Classification c);

    int deleteByOid(@Param("oid") String oid);

    Classification selectByOid(@Param("oid") String oid);

    Classification selectByIdentifier(@Param("identifier") String identifier,
                                       @Param("tenantOid") String tenantOid);

    List<Classification> selectAll(@Param("tenantOid") String tenantOid);

    List<Classification> search(@Param("keyword") String keyword,
                                 @Param("tenantOid") String tenantOid);

    List<Classification> selectRoots(@Param("tenantOid") String tenantOid);

    List<Classification> selectByParentOid(@Param("parentOid") String parentOid,
                                            @Param("tenantOid") String tenantOid);

    int existsByIdentifier(@Param("identifier") String identifier,
                           @Param("tenantOid") String tenantOid);

    int countChildren(@Param("oid") String oid);
}
