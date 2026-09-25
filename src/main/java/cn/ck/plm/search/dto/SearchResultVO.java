/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.search.dto;

/**
 * 全局搜索结果视图对象。
 *
 * <p>跨产品系列/产品型号/零组件/文档四个核心业务表 UNION 后的统一结果格式。
 */
public class SearchResultVO {

    /** 对象类型：PRODUCT_LINE / PRODUCT_MODEL / PART / DOCUMENT */
    private String type;

    /** 对象 OID */
    private String oid;

    /** 编码（产品系列/型号=code；零组件/文档=number） */
    private String code;

    /** 名称 */
    private String name;

    /** 前端跳转链接（带 query 参数） */
    private String link;

    /**
     * 具体类型编码（如 ELECTRONIC / STRUCTURAL；产品系列·型号那两张表没有这个概念，为 null）。
     *
     * <p>{@link #type} 是"哪张表"（PART / DOCUMENT），这里是"哪种类型" ——
     * 「发起流程」要多选对象时必须按具体类型过滤：一次流程只针对同一种类型，
     * 把 STRUCTURAL 的零件塞进 ELECTRONIC 的流程里，流程变量与审批意见都会对不上。
     */
    private String typeDefinitionCode;

    public SearchResultVO() {
    }

    public SearchResultVO(String type, String oid, String code, String name, String link,
                          String typeDefinitionCode) {
        this.type = type;
        this.oid = oid;
        this.code = code;
        this.name = name;
        this.link = link;
        this.typeDefinitionCode = typeDefinitionCode;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }
}