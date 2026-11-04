/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper.impl.postgresql;

import cn.ck.plm.part.entity.Manufacturer;
import cn.ck.plm.part.entity.PartAml;
import cn.ck.plm.part.entity.PartAvl;
import cn.ck.plm.part.entity.Supplier;
import cn.ck.plm.part.mapper.MpnMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * MPN 管理 PostgreSQL 持久化实现（四张表，见 {@link MpnMapper} 类注释说明为何合用一个接口）。
 *
 * <p>本类不书写 {@code tenant_oid} 条件：租户隔离由 {@code TenantStatementInterceptor}
 * 自动注入。唯一例外是 INSERT —— 显式带上 {@code tenant_oid} 列（与
 * {@code PostgreSqlTenantCapabilityMapper} 同口径：列已存在则拦截器原样放行，
 * 避免"当前上下文租户"与"目标租户"不一致时的意外归属）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlMpnMapper extends MpnMapper {

    // ==================== 制造商 ====================

    String MFR_COLUMNS = "oid, code, name, description, english_name, website, country, note, " +
            "disabled, sort_order, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Select("SELECT " + MFR_COLUMNS + " FROM ck_manufacturer " +
            "WHERE (#{keyword}::text IS NULL OR #{keyword}::text = '' " +
            "   OR code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%' " +
            "   OR english_name ILIKE '%' || #{keyword} || '%') " +
            "ORDER BY sort_order, code")
    @Results(id = "manufacturerResult", value = {
            @Result(property = "oid",          column = "oid"),
            @Result(property = "code",         column = "code"),
            @Result(property = "name",         column = "name"),
            @Result(property = "description",  column = "description"),
            @Result(property = "englishName",  column = "english_name"),
            @Result(property = "website",      column = "website"),
            @Result(property = "country",      column = "country"),
            @Result(property = "note",         column = "note"),
            @Result(property = "disabled",     column = "disabled"),
            @Result(property = "sortOrder",    column = "sort_order"),
            @Result(property = "tenantOid",    column = "tenant_oid"),
            @Result(property = "creator",      column = "creator"),
            @Result(property = "createdAt",    column = "created_at"),
            @Result(property = "updater",      column = "updater"),
            @Result(property = "updatedAt",    column = "updated_at")
    })
    List<Manufacturer> listManufacturers(@Param("keyword") String keyword);

    @Override
    @Select("SELECT " + MFR_COLUMNS + " FROM ck_manufacturer WHERE oid = #{oid}")
    @ResultMap("manufacturerResult")
    Manufacturer selectManufacturerByOid(@Param("oid") String oid);

    @Override
    @Insert("INSERT INTO ck_manufacturer (" + MFR_COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{englishName}, #{website}, #{country}, #{note}, " +
            "#{disabled}, #{sortOrder}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insertManufacturer(Manufacturer manufacturer);

    @Override
    @Update("UPDATE ck_manufacturer SET code = #{code}, name = #{name}, description = #{description}, " +
            "english_name = #{englishName}, website = #{website}, country = #{country}, note = #{note}, " +
            "disabled = #{disabled}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updateManufacturer(Manufacturer manufacturer);

    @Override
    @Delete("DELETE FROM ck_manufacturer WHERE oid = #{oid}")
    int deleteManufacturer(@Param("oid") String oid);

    @Override
    @Select("SELECT count(*) FROM ck_part_aml WHERE manufacturer_oid = #{manufacturerOid}")
    int countAmlByManufacturer(@Param("manufacturerOid") String manufacturerOid);

    // ==================== 供应商 ====================

    String SUPPLIER_COLUMNS = "oid, code, name, description, full_name, contact, phone, email, website, note, " +
            "disabled, sort_order, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Select("SELECT " + SUPPLIER_COLUMNS + " FROM ck_supplier " +
            "WHERE (#{keyword}::text IS NULL OR #{keyword}::text = '' " +
            "   OR code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%' " +
            "   OR full_name ILIKE '%' || #{keyword} || '%') " +
            "ORDER BY sort_order, code")
    @Results(id = "supplierResult", value = {
            @Result(property = "oid",          column = "oid"),
            @Result(property = "code",         column = "code"),
            @Result(property = "name",         column = "name"),
            @Result(property = "description",  column = "description"),
            @Result(property = "fullName",     column = "full_name"),
            @Result(property = "contact",      column = "contact"),
            @Result(property = "phone",        column = "phone"),
            @Result(property = "email",        column = "email"),
            @Result(property = "website",      column = "website"),
            @Result(property = "note",         column = "note"),
            @Result(property = "disabled",     column = "disabled"),
            @Result(property = "sortOrder",    column = "sort_order"),
            @Result(property = "tenantOid",    column = "tenant_oid"),
            @Result(property = "creator",      column = "creator"),
            @Result(property = "createdAt",    column = "created_at"),
            @Result(property = "updater",      column = "updater"),
            @Result(property = "updatedAt",    column = "updated_at")
    })
    List<Supplier> listSuppliers(@Param("keyword") String keyword);

    @Override
    @Select("SELECT " + SUPPLIER_COLUMNS + " FROM ck_supplier WHERE oid = #{oid}")
    @ResultMap("supplierResult")
    Supplier selectSupplierByOid(@Param("oid") String oid);

    @Override
    @Insert("INSERT INTO ck_supplier (" + SUPPLIER_COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{fullName}, #{contact}, #{phone}, #{email}, " +
            "#{website}, #{note}, #{disabled}, #{sortOrder}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insertSupplier(Supplier supplier);

    @Override
    @Update("UPDATE ck_supplier SET code = #{code}, name = #{name}, description = #{description}, " +
            "full_name = #{fullName}, contact = #{contact}, phone = #{phone}, email = #{email}, " +
            "website = #{website}, note = #{note}, disabled = #{disabled}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updateSupplier(Supplier supplier);

    @Override
    @Delete("DELETE FROM ck_supplier WHERE oid = #{oid}")
    int deleteSupplier(@Param("oid") String oid);

    @Override
    @Select("SELECT count(*) FROM ck_part_avl WHERE supplier_oid = #{supplierOid}")
    int countAvlBySupplier(@Param("supplierOid") String supplierOid);

    // ==================== AML ====================

    String AML_COLUMNS = "oid, code, name, description, part_oid, manufacturer_oid, mpn, mpn_desc, " +
            "approved_status, lifecycle_state, effectivity_json, note, sort_order, tenant_oid, " +
            "creator, created_at, updater, updated_at";

    // 状态语义序：首选 → 认可 → 禁用。
    // 原实现 "ORDER BY sort_order, approved_status, mpn"：当 sort_order 同为 0（默认）时按字母序排
    // （APPROVED < DISALLOWED < PREFERRED）→ **首选反而排在最后** ✗。本处与 AVL 的
    // "ORDER BY preferred DESC, sort_order, oid" 对齐：先按状态语义、同档再按显示顺序、最后按料号。
    @Override
    @Select("SELECT " + AML_COLUMNS + " FROM ck_part_aml WHERE part_oid = #{partOid} " +
            "ORDER BY CASE approved_status WHEN 'PREFERRED' THEN 0 WHEN 'APPROVED' THEN 1 " +
            "WHEN 'DISALLOWED' THEN 2 ELSE 3 END, sort_order, mpn")
    @Results(id = "partAmlResult", value = {
            @Result(property = "oid",             column = "oid"),
            @Result(property = "code",            column = "code"),
            @Result(property = "name",            column = "name"),
            @Result(property = "description",     column = "description"),
            @Result(property = "partOid",         column = "part_oid"),
            @Result(property = "manufacturerOid", column = "manufacturer_oid"),
            @Result(property = "mpn",             column = "mpn"),
            @Result(property = "mpnDesc",         column = "mpn_desc"),
            @Result(property = "approvedStatus",  column = "approved_status"),
            @Result(property = "lifecycleState",  column = "lifecycle_state"),
            @Result(property = "effectivityJson", column = "effectivity_json"),
            @Result(property = "note",            column = "note"),
            @Result(property = "sortOrder",       column = "sort_order"),
            @Result(property = "tenantOid",       column = "tenant_oid"),
            @Result(property = "creator",         column = "creator"),
            @Result(property = "createdAt",       column = "created_at"),
            @Result(property = "updater",         column = "updater"),
            @Result(property = "updatedAt",       column = "updated_at")
    })
    List<PartAml> listAmlByPart(@Param("partOid") String partOid);

    @Override
    @Select("SELECT " + AML_COLUMNS + " FROM ck_part_aml WHERE oid = #{oid}")
    @ResultMap("partAmlResult")
    PartAml selectAmlByOid(@Param("oid") String oid);

    @Override
    @Insert("INSERT INTO ck_part_aml (" + AML_COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{partOid}, #{manufacturerOid}, #{mpn}, #{mpnDesc}, " +
            "#{approvedStatus}, #{lifecycleState}, #{effectivityJson}::jsonb, #{note}, #{sortOrder}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insertAml(PartAml aml);

    @Override
    @Update("UPDATE ck_part_aml SET code = #{code}, name = #{name}, description = #{description}, " +
            "part_oid = #{partOid}, manufacturer_oid = #{manufacturerOid}, mpn = #{mpn}, " +
            "mpn_desc = #{mpnDesc}, approved_status = #{approvedStatus}, " +
            "lifecycle_state = #{lifecycleState}, effectivity_json = #{effectivityJson}::jsonb, " +
            "note = #{note}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updateAml(PartAml aml);

    @Override
    @Delete("DELETE FROM ck_part_aml WHERE oid = #{oid}")
    int deleteAml(@Param("oid") String oid);

    // 注：excludeOid 需显式 ::text —— 传 null 时 PG 推不出参数类型（无法确定参数类型），
    //     这是 "#{x} IS NULL" 这类可选条件的经典坑。
    @Override
    @Select("SELECT count(*) FROM ck_part_aml WHERE part_oid = #{partOid} " +
            "AND manufacturer_oid = #{manufacturerOid} AND mpn = #{mpn} " +
            "AND (#{excludeOid}::text IS NULL OR oid <> #{excludeOid}::text)")
    int countAmlByMpn(@Param("partOid") String partOid,
                      @Param("manufacturerOid") String manufacturerOid,
                      @Param("mpn") String mpn,
                      @Param("excludeOid") String excludeOid);

    // ==================== AVL ====================

    String AVL_COLUMNS = "oid, code, name, description, aml_oid, supplier_oid, supplier_part_no, preferred, " +
            "price, currency, lead_time_days, moq, spq, effectivity_json, note, sort_order, tenant_oid, " +
            "creator, created_at, updater, updated_at";

    @Override
    @Select("SELECT " + AVL_COLUMNS + " FROM ck_part_avl WHERE aml_oid = #{amlOid} " +
            "ORDER BY preferred DESC, sort_order, oid")
    @Results(id = "partAvlResult", value = {
            @Result(property = "oid",             column = "oid"),
            @Result(property = "code",            column = "code"),
            @Result(property = "name",            column = "name"),
            @Result(property = "description",     column = "description"),
            @Result(property = "amlOid",          column = "aml_oid"),
            @Result(property = "supplierOid",     column = "supplier_oid"),
            @Result(property = "supplierPartNo",  column = "supplier_part_no"),
            @Result(property = "preferred",       column = "preferred"),
            @Result(property = "price",           column = "price"),
            @Result(property = "currency",        column = "currency"),
            @Result(property = "leadTimeDays",    column = "lead_time_days"),
            @Result(property = "moq",             column = "moq"),
            @Result(property = "spq",             column = "spq"),
            @Result(property = "effectivityJson", column = "effectivity_json"),
            @Result(property = "note",            column = "note"),
            @Result(property = "sortOrder",       column = "sort_order"),
            @Result(property = "tenantOid",       column = "tenant_oid"),
            @Result(property = "creator",         column = "creator"),
            @Result(property = "createdAt",       column = "created_at"),
            @Result(property = "updater",         column = "updater"),
            @Result(property = "updatedAt",       column = "updated_at")
    })
    List<PartAvl> listAvlByAml(@Param("amlOid") String amlOid);

    @Override
    @Select("<script>SELECT " + AVL_COLUMNS + " FROM ck_part_avl WHERE aml_oid IN " +
            "<foreach collection='amlOids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY preferred DESC, sort_order, oid</script>")
    @ResultMap("partAvlResult")
    List<PartAvl> listAvlByAmls(@Param("amlOids") List<String> amlOids);

    @Override
    @Select("SELECT " + AVL_COLUMNS + " FROM ck_part_avl WHERE oid = #{oid}")
    @ResultMap("partAvlResult")
    PartAvl selectAvlByOid(@Param("oid") String oid);

    @Override
    @Insert("INSERT INTO ck_part_avl (" + AVL_COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{amlOid}, #{supplierOid}, #{supplierPartNo}, " +
            "#{preferred}, #{price}, #{currency}, #{leadTimeDays}, #{moq}, #{spq}, " +
            "#{effectivityJson}::jsonb, #{note}, #{sortOrder}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insertAvl(PartAvl avl);

    @Override
    @Update("UPDATE ck_part_avl SET code = #{code}, name = #{name}, description = #{description}, " +
            "aml_oid = #{amlOid}, supplier_oid = #{supplierOid}, supplier_part_no = #{supplierPartNo}, " +
            "preferred = #{preferred}, price = #{price}, currency = #{currency}, " +
            "lead_time_days = #{leadTimeDays}, moq = #{moq}, spq = #{spq}, " +
            "effectivity_json = #{effectivityJson}::jsonb, note = #{note}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updateAvl(PartAvl avl);

    @Override
    @Delete("DELETE FROM ck_part_avl WHERE oid = #{oid}")
    int deleteAvl(@Param("oid") String oid);

    // 同 countAmlByMpn：excludeOid 必须显式 ::text，否则传 null 时 PG 推不出参数类型
    @Override
    @Select("SELECT count(*) FROM ck_part_avl WHERE aml_oid = #{amlOid} AND supplier_oid = #{supplierOid} " +
            "AND (#{excludeOid}::text IS NULL OR oid <> #{excludeOid}::text)")
    int countAvlBySupplierPair(@Param("amlOid") String amlOid,
                               @Param("supplierOid") String supplierOid,
                               @Param("excludeOid") String excludeOid);

    // ==================== 汇总 ====================

    @Override
    @Select("SELECT count(DISTINCT v.supplier_oid) FROM ck_part_avl v " +
            "JOIN ck_part_aml a ON a.oid = v.aml_oid WHERE a.part_oid = #{partOid}")
    int countDistinctSuppliersByPart(@Param("partOid") String partOid);

    @Override
    @Select("SELECT count(*) FROM ck_part_aml WHERE part_oid = #{partOid}")
    int countAmlByPart(@Param("partOid") String partOid);

    // ==================== 内部件选择器 ====================

    @Override
    // 注：ck_part 的编号列叫 number（不是 code）—— 别名成 code，前端选择器无需特判
    @Select("SELECT oid, number AS code, name FROM ck_part " +
            "WHERE (#{keyword}::text IS NULL OR #{keyword}::text = '' " +
            "   OR number ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%') " +
            "ORDER BY number LIMIT #{limit}")
    List<java.util.Map<String, Object>> searchParts(@Param("keyword") String keyword,
                                                    @Param("limit") int limit);
}
