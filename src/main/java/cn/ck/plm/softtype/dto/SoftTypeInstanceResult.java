/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.dto;

/**
 * 软类型实例创建结果 —— 统一创建入口 {@code POST /api/softtype-instances} 的响应载荷。
 *
 * <p>使前端在「只传 typeDefinitionCode」的前提下也能拿到与各实体原生端点一致的信息：
 * 主对象 oid / 编号 / 名称、初始迭代 oid / 显示版本，以及路由到的能力宿主。
 */
public class SoftTypeInstanceResult {

    /** 路由命中的能力宿主 code（= {@code type_definition.root_type_code}），如 PART / ENG_DOCUMENT */
    private String hostCode;

    /** 实际落库使用的类型编码（软类型时为软类型 code，如 FOOTPRINT） */
    private String typeDefinitionCode;

    /** 类型标志位：OOTB / SOFT_TYPE */
    private String typeKind;

    /** 创建出的主对象 oid */
    private String oid;

    /** 编号 */
    private String number;

    /** 名称 */
    private String name;

    /** 初始子版本 oid */
    private String iterationOid;

    /** 显示版本，如 A.1 */
    private String displayVersion;

    /** 原始主对象实体（供前端复用，如展示 number/name 或跳转详情） */
    private Object entity;

    // ==================== Getter / Setter ====================

    public String getHostCode() { return hostCode; }
    public void setHostCode(String hostCode) { this.hostCode = hostCode; }

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getTypeKind() { return typeKind; }
    public void setTypeKind(String typeKind) { this.typeKind = typeKind; }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getDisplayVersion() { return displayVersion; }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    public Object getEntity() { return entity; }
    public void setEntity(Object entity) { this.entity = entity; }

    @Override
    public String toString() {
        return "SoftTypeInstanceResult{host='" + hostCode + "', type='" + typeDefinitionCode
                + "', oid='" + oid + "', number='" + number + "'}";
    }
}
