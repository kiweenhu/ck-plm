/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.repo;

import cn.ck.plm.base.pack.install.InstallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 行业包机制表的仓储 —— {@code ck_industry_pack} 与 {@code ck_industry_pack_resource}。
 *
 * <h3>为什么用 JdbcTemplate 而不是 MyBatis + 多租户实体</h3>
 * 这两张表是<b>机制表</b>（平台基础设施），不是业务表：
 * <ul>
 *   <li>它们要记录"平台级包"（{@code tenant_oid=平台}）与"租户级包"两种归属，
 *       而多租户拦截器是给业务表设计的口径（按当前租户过滤），套上来只会互相打架；</li>
 *   <li>安装过程本身就是"显式指定归属"的行为：{@code tenant_oid} 由引擎明确传入，
 *       不该被隐式改写 —— 用原生 SQL 把这条意图写在代码里，比藏在拦截器行为里可读。</li>
 * </ul>
 *
 * <p>表结构见 {@code schema.sql} 的「行业包（Industry Pack）机制表」一节；
 * 该表的 {@code UNIQUE(pack_code, pack_version, tenant_oid)} 是幂等的最后一道保险：
 * 即使并发装同一个包，也只会留下一行。
 */
@Repository
public class IndustryPackRepository {

    private static final Logger log = LoggerFactory.getLogger(IndustryPackRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public IndustryPackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 包记录 ====================

    /** 查某包在某租户下的安装记录（没有则返回 null） */
    public Map<String, Object> findPack(String packCode, String packVersion, String tenantOid) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ck_industry_pack WHERE pack_code = ? AND pack_version = ? AND tenant_oid = ?",
                packCode, packVersion, tenantOid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 全部安装记录（可按租户过滤；tenantOid 为空则查全部） */
    public List<Map<String, Object>> listPacks(String tenantOid) {
        if (tenantOid == null || tenantOid.trim().isEmpty()) {
            return jdbcTemplate.queryForList("SELECT * FROM ck_industry_pack ORDER BY installed_at, pack_code");
        }
        return jdbcTemplate.queryForList(
                "SELECT * FROM ck_industry_pack WHERE tenant_oid = ? ORDER BY installed_at, pack_code", tenantOid);
    }

    /** 写入一条包安装记录，返回 oid */
    public String insertPack(String packCode, String packVersion, String basePack, String engineRange,
                             String industryCode, String devMethodCode, String targetScale,
                             String installState, String contentHash, String parametersJson,
                             String installedBy, String tenantOid) {
        String oid = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO ck_industry_pack (oid, pack_code, pack_version, base_pack, engine_range, "
                        + "industry_code, dev_method_code, target_scale, install_state, content_hash, "
                        + "parameters_json, installed_at, installed_by, tenant_oid) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                oid, packCode, packVersion, basePack, engineRange,
                industryCode, devMethodCode, targetScale, installState, contentHash,
                parametersJson, Timestamp.valueOf(LocalDateTime.now()), installedBy, tenantOid);
        return oid;
    }

    /** 更新已存在记录的安装状态与指纹（重复安装/升级时用） */
    public void updatePackState(String oid, String installState, String contentHash, String parametersJson) {
        jdbcTemplate.update(
                "UPDATE ck_industry_pack SET install_state = ?, content_hash = ?, parameters_json = ?, "
                        + "installed_at = ? WHERE oid = ?",
                installState, contentHash, parametersJson, Timestamp.valueOf(LocalDateTime.now()), oid);
    }

    /** 该租户是否已装过某包（任意版本） */
    public boolean isInstalled(String packCode, String tenantOid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ck_industry_pack WHERE pack_code = ? AND tenant_oid = ?",
                Integer.class, packCode, tenantOid);
        return count != null && count > 0;
    }

    // ==================== 资源明细 ====================

    /**
     * 记一条资源明细（预演时不调用）。
     *
     * <p>同一资源（pack + type + key）可能先后经历"跳过 → 被删 → 重建"，因此冲突时<b>覆盖为最新结果</b>
     * 而不是 DO NOTHING —— 明细表的价值是回答"这个包在这台库里现在是什么状态"，
     * 停在第一次的 SKIP 会让排障看错方向。
     */
    public void insertResource(String packOid, InstallResult result, String tenantOid) {
        jdbcTemplate.update(
                "INSERT INTO ck_industry_pack_resource (oid, pack_oid, resource_type, resource_key, action, "
                        + "target_table, target_oid, created_object_oid, message, content_hash, tenant_oid, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT (pack_oid, resource_type, resource_key) DO UPDATE SET "
                        + "action = EXCLUDED.action, target_table = EXCLUDED.target_table, "
                        + "target_oid = EXCLUDED.target_oid, created_object_oid = EXCLUDED.created_object_oid, "
                        + "message = EXCLUDED.message, content_hash = EXCLUDED.content_hash, "
                        + "created_at = EXCLUDED.created_at",
                UUID.randomUUID().toString(), packOid, result.getResourceType(), result.getResourceKey(),
                result.getAction() == null ? "SKIP" : result.getAction().name(),
                result.getTargetTable(), result.getTargetOid(), result.getCreatedObjectOid(),
                trim(result.getMessage(), 500), result.getContentHash(), tenantOid,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /** 某包已记账的资源明细（报告与"清理示例"用） */
    public List<Map<String, Object>> listResources(String packOid) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ck_industry_pack_resource WHERE pack_oid = ? "
                        + "ORDER BY resource_type, resource_key", packOid);
    }

    /** 示例数据创建过的业务对象 oid（精确回滚用） */
    public List<String> listSampleObjects(String packOid) {
        return jdbcTemplate.queryForList(
                "SELECT created_object_oid FROM ck_industry_pack_resource "
                        + "WHERE pack_oid = ? AND created_object_oid IS NOT NULL", String.class, packOid);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
