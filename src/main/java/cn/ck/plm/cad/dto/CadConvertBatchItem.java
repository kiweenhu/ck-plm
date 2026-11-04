/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

/**
 * 批量转换请求里的一个条目 —— 就是「产品数模」列表里被勾中的那一行。
 *
 * <p>两个 oid 缺一不可：{@code iterationOid} 决定产物挂在哪次迭代上，
 * {@code ckfileOid} 决定转哪个文件。只给文件不行（同一文件可能被多个迭代引用），
 * 只给迭代也不行（一次迭代理论上可挂多个 CAD 主文件）。
 */
public class CadConvertBatchItem {

    private String iterationOid;
    private String ckfileOid;

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getCkfileOid() { return ckfileOid; }
    public void setCkfileOid(String ckfileOid) { this.ckfileOid = ckfileOid; }
}
