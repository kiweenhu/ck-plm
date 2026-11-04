/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

import java.util.List;

/**
 * 转换中心的一页数据：行 + 总数 + 顶部计数。
 *
 * <p>统计跟着列表一起返回（而不是单独一个接口）：顶部卡片与表格本来就该是同一瞬间的快照，
 * 分两次请求会出现"筛选后 0 条、但顶部写着转换中 3 条"这种自相矛盾的画面。
 */
public class CadConvertJobPageVO {

    private List<CadConvertJobVO> list;
    private int total;
    private int page;
    private int size;
    private CadConvertStatsVO stats;

    public List<CadConvertJobVO> getList() { return list; }
    public void setList(List<CadConvertJobVO> list) { this.list = list; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public CadConvertStatsVO getStats() { return stats; }
    public void setStats(CadConvertStatsVO stats) { this.stats = stats; }
}
