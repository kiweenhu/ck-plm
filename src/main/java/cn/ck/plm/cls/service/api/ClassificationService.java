package cn.ck.plm.cls.service.api;

import cn.ck.plm.cls.dto.ClassificationCloneResult;
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

    /** 获取以 rootOid 为根的子树（包含该节点及其所有后代） */
    Classification findSubtree(String rootOid);

    /** 获取平台租户的分类树（用于克隆预览） */
    List<Classification> findPlatformTree();

    /** 从平台克隆指定根分类到当前租户（含 IBA 定义与关联），返回克隆统计 */
    ClassificationCloneResult cloneFromPlatform(List<String> rootOids);

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
