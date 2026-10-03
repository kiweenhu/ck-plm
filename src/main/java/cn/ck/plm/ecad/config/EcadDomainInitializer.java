/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ecad.config;

import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * 电子域（ECAD）基础数据初始化 —— <b>只做种子，不做建表</b>。
 *
 * <h3>结构来源</h3>
 * 电子域这 11 张表 —— {@code ck_ecad_domain} / {@code ck_ecad_footprint_ext} /
 * {@code ck_ecad_symbol_ext} / {@code ck_ecad_schematic_ext} / {@code ck_ecad_pcb_ext} /
 * {@code ck_ecad_project} / {@code ck_ecad_project_design_link} / {@code ck_component_footprint_link} /
 * {@code ck_component_symbol_link} / {@code ck_footprint_3d_link} / {@code ck_design_instance} ——
 * 以及它们的 13 个索引，全部由 {@code schema.sql} 建（新装即完整）。
 * 本类原先在代码里把同样的建表语句又写了一遍（{@code EcadSchemaInitializer}），已退场：
 * 结构只留一处来源，避免"脚本改了、代码又建回旧结构"的隐性冲突。
 *
 * <h3>历史上还做过两件事（一并退场）</h3>
 * <ul>
 *   <li>{@code DROP TABLE ck_ecad_domain_member}：域成员挂载表废弃后的清理。域归属改由类型树
 *       （{@code parent_oid}）+ {@code ck_business_domain} 表达，该表早已不存在，DROP 是空操作；</li>
 *   <li>封装（FOOTPRINT）/ 图符（SYMBOL）主数据与版本记录从 {@code ck_part} 迁到
 *       {@code ck_eng_document}：面向老库的一次性迁移，属"不做老库迁移"口径之外
 *       （动迁前实测：两表相关数据均为 0 条）。</li>
 * </ul>
 *
 * <h3>域归属</h3>
 * 业务域（含电子设计域）已拆为独立实体 {@code ck_business_domain}，种子见
 * {@code BusinessDomainInitializer}；{@code ck_ecad_domain} 只存"域实例"本身。
 *
 * <h3>顺序与幂等</h3>
 * 无 {@code @Order}（默认最低优先级，排在其它初始化器之后）；按 {@code code='ECAD'} 判断，
 * 已存在即跳过，可反复启动。写入显式切到平台租户 —— 启动期无请求上下文时，租户拦截器会按
 * "默认租户"过滤，否则查不到平台行、还会撞 {@code uk_ecad_domain_code} 唯一键。
 */
@Component
public class EcadDomainInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EcadDomainInitializer.class);

    private final DataSource dataSource;

    public EcadDomainInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        String previousTenant = TenantContext.getOrNull();
        TenantContext.set(TenantContext.PLATFORM_TENANT_OID);
        try (Connection conn = dataSource.getConnection()) {
            if (existsEcadDomain(conn)) {
                log.debug("电子域 ECAD 已存在，跳过");
                return;
            }
            insertEcadDomain(conn);
        } catch (Exception e) {
            // 初始化失败不能让应用起不来：电子域是"用到才配"的能力
            log.warn("电子域（ECAD）初始化失败: {}", e.getMessage(), e);
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previousTenant);
            }
        }
    }

    private boolean existsEcadDomain(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM ck_ecad_domain WHERE code = ? LIMIT 1")) {
            ps.setString(1, "ECAD");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertEcadDomain(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ck_ecad_domain (oid, code, name, description, enabled, tenant_oid, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "ECAD");
            ps.setString(3, "电子设计域");
            ps.setString(4, "电子设计域（ECAD）：封装、原理图图符、原理图、PCB 设计、电子设计项目等对象的统一域锚点");
            ps.setString(5, TenantContext.PLATFORM_TENANT_OID);
            ps.executeUpdate();
            log.info("已初始化电子域: ECAD（电子设计域）");
        }
    }

    // 保留 Statement 的引用仅为 import 一致性说明：本类已无任何 DDL / Statement 用法。
}
