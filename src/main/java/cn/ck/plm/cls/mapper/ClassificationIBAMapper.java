package cn.ck.plm.cls.mapper;

import cn.ck.plm.cls.entity.ClassificationIBA;

import java.util.List;

/**
 * 分类-IBA属性关联数据访问接口。
 */
public interface ClassificationIBAMapper {

    int insert(ClassificationIBA mapping);

    int update(ClassificationIBA mapping);

    int deleteByOid(String oid);

    ClassificationIBA selectByOid(String oid);

    /** 查询某分类下的所有 IBA 关联（含 IBA 基本信息） */
    List<ClassificationIBA> selectByClassificationOid(String classificationOid);

    /** 查询某 IBA 被哪些分类使用 */
    List<ClassificationIBA> selectByIbaOid(String ibaOid);

    int existsByClsAndIba(String classificationOid, String ibaOid);

    /** 批量删除某分类的所有 IBA 关联 */
    int deleteByClassificationOid(String classificationOid);
}
