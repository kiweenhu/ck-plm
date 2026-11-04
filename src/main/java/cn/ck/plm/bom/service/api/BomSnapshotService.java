/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.dto.BomSnapshotCompareVO;
import cn.ck.plm.bom.entity.BomSnapshot;

import java.util.List;

/**
 * BomSnapshot 业务服务接口。
 */
public interface BomSnapshotService {

    /**
     * 把某迭代当前的顶层 BOM 行定格为一份快照。
     *
     * @param iterationOid 父件迭代 oid（页面上正在看的那个版本）
     * @param name         快照名称（必填）
     * @param remark       备注（可选）
     */
    BomSnapshot createSnapshot(String iterationOid, String name, String remark);

    /** 快照记录：按零件（跨版本）或按迭代过滤；返回体不含 snapshotJson（列表用不上，几百行很占带宽） */
    List<BomSnapshot> listSnapshots(String partOid, String iterationOid);

    /** 单份快照（含 snapshotJson） */
    BomSnapshot getByOid(String oid);

    void delete(String oid);

    /**
     * 对比快照与另一份快照或某个迭代的当前 BOM。
     *
     * @param fromSnapshotOid 源快照 oid（必填）
     * @param toSnapshotOid   目标快照 oid（与 toIterationOid 二选一）
     * @param toIterationOid  目标迭代 oid（取该迭代当下的 BOM 行）
     */
    BomSnapshotCompareVO compare(String fromSnapshotOid, String toSnapshotOid, String toIterationOid);
}
