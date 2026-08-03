package cn.ck.plm.cls.service.api;

import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.softtype.entity.IBA;

import java.util.List;

public interface ClassificationService {

    Classification create(Classification c);

    Classification update(Classification c);

    void delete(String oid);

    Classification findByOid(String oid);

    Classification findByIdentifier(String identifier);

    List<Classification> findAll();

    List<Classification> search(String keyword);

    List<Classification> findRoots();

    List<Classification> findChildren(String parentOid);

    List<Classification> findTree();

    // ===== 分类-IBA 关联 =====

    /** 为分类分配 IBA 属性 */
    ClassificationIBA assignIba(ClassificationIBA mapping);

    /** 批量分配 IBA 属性 */
    List<ClassificationIBA> batchAssignIbas(String classificationOid, List<String> ibaOids);

    /** 更新 IBA 映射（覆写 required/defaultValue） */
    ClassificationIBA updateIBAMapping(ClassificationIBA mapping);

    /** 移除 IBA 关联 */
    void removeIBAMapping(String mappingOid);

    /** 查询分类关联的所有 IBA */
    List<ClassificationIBA> findIBAsByClassificationOid(String classificationOid);

    /** 查询分类未分配的 IBA（可用于分配） */
    List<IBA> findUnassignedIBAs(String classificationOid, String keyword);
}
