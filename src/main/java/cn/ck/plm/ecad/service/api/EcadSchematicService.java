/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.service.api;

import cn.ck.plm.ecad.dto.BomSyncItem;
import cn.ck.plm.ecad.dto.BomSyncResult;
import cn.ck.plm.ecad.entity.EcadDesignInstance;
import cn.ck.plm.ecad.entity.EcadSchematic;
import cn.ck.plm.ecad.entity.EcadSchematicIteration;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * ECAD 原理图服务 —— Cadence 原理图对象的版本控制、文件管理与 BOM 同步。
 *
 * <p>版本流转规则与文档迭代一致：检出派生新迭代（iteration+1），检入解除锁定，
 * 新建大版本 iteration 归 1。检出/检入统一走
 * {@code cn.ck.plm.checkout.service.impl.EcadSchematicCheckoutProvider}。
 */
public interface EcadSchematicService {

    // ==================== 主数据 ====================

    /** 创建原理图（自动生成首次迭代 A.1；number 为空时按规则生成） */
    EcadSchematic create(EcadSchematic schematic, String user);

    /** 按 oid 查询 */
    EcadSchematic findByOid(String oid);

    /** 按编号查询（查不到返回 null） */
    EcadSchematic findByNumber(String number);

    /** 按设计项目查询原理图列表 */
    List<EcadSchematic> listByProjectOid(String projectOid);

    /** 更新主数据 */
    EcadSchematic update(EcadSchematic schematic, String user);

    // ==================== 版本控制 ====================

    /** 检出（派生新迭代，锁定） */
    EcadSchematicIteration checkout(String masterOid, String comment, String user);

    /** 检入（解除锁定，工作副本成为正式版本） */
    EcadSchematicIteration checkin(String masterOid, String user);

    /** 取消检出（删除派生副本，恢复源版本） */
    void undoCheckout(String masterOid, String user);

    /** 新建大版本（revision+1，iteration 归 1） */
    EcadSchematicIteration newRevision(String masterOid, String user);

    /** 版本历史 */
    List<EcadSchematicIteration> listIterations(String masterOid);

    /** 最新迭代 */
    EcadSchematicIteration latestIteration(String masterOid);

    // ==================== 文件 ====================

    /**
     * 上传 .DSN 文件到检出的迭代（须为检出人本人；检入后文件随迭代生效）。
     *
     * @return 更新后的迭代（含 ckfileOid）
     */
    EcadSchematicIteration uploadDsn(String masterOid, MultipartFile file,
                                     Integer sheetCount, Integer componentCount,
                                     String user) throws IOException;

    // ==================== BOM 同步 ====================

    /**
     * BOM 同步：上传 Capture 导出的 BOM 行，落地为设计实例并输出与上次的差异。
     *
     * <p>匹配规则：优先按 {@code partNumber} 精确匹配 ck_part.number；
     * 未携带编号时按 {@code value} 关键词匹配。差异写入同步日志。
     */
    BomSyncResult syncBom(String masterOid, List<BomSyncItem> items, String user);

    /** 设计实例（当前同步状态） */
    List<EcadDesignInstance> listInstances(String masterOid);
}
