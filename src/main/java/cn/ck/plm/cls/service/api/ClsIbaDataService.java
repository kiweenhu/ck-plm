package cn.ck.plm.cls.service.api;

import java.util.Map;

/**
 * 分类 IBA 数据存取服务接口。
 * <p>
 * entityOid 用于区分数据归属：
 * <ul>
 *   <li>空字符串（""）：分类节点默认值（分类管理界面配置）</li>
 *   <li>非空：具体对象实例（Part/Document）的分类 IBA 属性值</li>
 * </ul>
 */
public interface ClsIbaDataService {

    /**
     * 保存指定对象实例在指定分类下的全部 IBA 属性值。
     * <p>采用 delete-all + insert-all 策略，保证与前端提交完全一致。
     */
    void saveValues(String entityOid, String classificationOid, Map<String, Object> values);

    /**
     * 合并保存：先加载已有数据再合并，避免误删未提交的字段。
     */
    void mergeValues(String entityOid, String classificationOid, Map<String, Object> values);

    /**
     * 获取指定对象实例在指定分类下的全部 IBA 属性值。
     */
    Map<String, Object> getValues(String entityOid, String classificationOid);

    /**
     * 获取指定对象实例在指定分类下的单个 IBA 属性值。
     */
    String getValue(String entityOid, String classificationOid, String attrCode);

    /**
     * 删除指定对象实例在指定分类下的全部 IBA 属性数据。
     */
    void deleteByEntity(String entityOid, String classificationOid);
}
