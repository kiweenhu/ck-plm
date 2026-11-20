/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper;

import cn.ck.plm.ecad.entity.EcadBomSyncLog;

import java.util.List;

/**
 * BOM 同步日志（{@code ck_ecad_bom_sync_log}）数据访问接口。
 */
public interface EcadBomSyncLogMapper {

    /** 新增日志 */
    int insert(EcadBomSyncLog syncLog);

    /** 按原理图查询同步历史（最新在前） */
    List<EcadBomSyncLog> selectBySchematicOid(String schematicOid);
}
