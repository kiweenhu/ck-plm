/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

/**
 * 工程数据引用类型枚举 —— 对齐 Windchill {@code wt.epm.structure.EPMReferenceType}。
 *
 * <p>对应 {@link EngDocRefLink#getReferenceType()}，即 {@code ck_eng_doc_ref_link.reference_type} 列。
 * Windchill 中 {@code EPMReferenceType} 是 {@code EnumeratedType}（与 CAD 应用无关的引用关系类型），
 * 其合法取值<b>不公开枚举</b>，需运行时 {@code getValueSet()} 才能取得，故此处定义一组
 * 应用无关的常用值，业务可按需扩展（列类型为 {@code VARCHAR(50)}，非数据库枚举，扩展无需改表）。
 *
 * <p>与 {@link EngDocMemberLink}（装配成员）的区别：成员链接表达<b>组成/装配</b>关系（必有数量），
 * 引用链接表达<b>横向引用</b>关系（无数量概念）。
 */
public enum EngDocRefType {

    /** 依赖：父文档的处理依赖子文档（最通用的默认值） */
    DEPENDENCY("依赖"),

    /** 复制几何：子文档的几何被复制进父文档（几何一旦复制即断开关联） */
    COPY_GEOMETRY("复制几何"),

    /** 继承：父文档继承子文档（如骨架模型驱动的自顶向下设计） */
    INHERITANCE("继承"),

    /** 外部参考：父文档引用外部（非同装配体系）文档 */
    EXTERNAL_REFERENCE("外部参考"),

    /** 模型参考：父文档引用子文档作为模型参考 */
    MODEL_REFERENCE("模型参考");

    private final String displayName;

    EngDocRefType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 按 code 解析（忽略大小写）；无法识别时回退 {@link #DEPENDENCY}，避免脏值导致运行期异常 */
    public static EngDocRefType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return DEPENDENCY;
        }
        String c = code.trim().toUpperCase();
        for (EngDocRefType t : values()) {
            if (t.name().equals(c)) {
                return t;
            }
        }
        return DEPENDENCY;
    }
}
