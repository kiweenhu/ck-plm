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

    public SearchResultVO() {
    }

    public SearchResultVO(String type, String oid, String code, String name, String link) {
        this.type = type;
        this.oid = oid;
        this.code = code;
        this.name = name;
        this.link = link;
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
}