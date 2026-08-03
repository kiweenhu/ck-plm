package cn.ck.plm.cls.service.api;

import java.util.Map;

/**
 * 分类 IBA 数据存取服务接口。
 */
public interface ClsIbaDataService {

    /**
     * 保存指定分类的全部 IBA 属性值。
     * <p>采用 delete-all + insert-all 策略，保证与前端提交完全一致。
     */
    void saveValues(String classificationOid, Map<String, Object> values);

    /**
     * 合并保存：先加载已有数据再合并，避免误删未提交的字段。
     */
    void mergeValues(String classificationOid, Map<String, Object> values);

    /**
     * 获取指定分类的全部 IBA 属性值。
     */
    Map<String, Object> getValues(String classificationOid);

    /**
     * 获取指定分类的单个 IBA 属性值。
     */
    String getValue(String classificationOid, String attrCode);

    /**
     * 删除指定分类的全部 IBA 属性数据。
     */
    void deleteByClassificationOid(String classificationOid);
}
