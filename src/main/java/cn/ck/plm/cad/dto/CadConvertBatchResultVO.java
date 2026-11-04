/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量提交的结果：分类计数 + 逐条明细。
 *
 * <h3>为什么必须逐条给结果</h3>
 * 批量最怕"提交了 30 张，提示成功，其实 8 张被跳过"。用户勾的是具体的文件，
 * 就必须能知道<b>哪一个</b>没进去、为什么 —— 分类计数（已提交/已存在/跳过/失败）
 * 只够画个摘要，修不了问题。
 *
 * <h3>四种结果的含义</h3>
 * <ul>
 *   <li>{@code ACCEPTED} —— 新任务已入队（QUEUED），转换器会跑</li>
 *   <li>{@code CACHED} —— 内容指纹命中，直接复用上次产物（SUCCESS），没有重复烧 CPU</li>
 *   <li>{@code SKIPPED} —— 转换器判定不该转（格式不支持 / 超限 / 熔断），重试无意义</li>
 *   <li>{@code FAILED} —— 这次没提交成功，可以再试</li>
 * </ul>
 */
public class CadConvertBatchResultVO {

    private int total;
    private int accepted;
    private int cached;
    private int skipped;
    private int failed;

    private List<Item> items = new ArrayList<>();

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getAccepted() { return accepted; }
    public void setAccepted(int accepted) { this.accepted = accepted; }

    public int getCached() { return cached; }
    public void setCached(int cached) { this.cached = cached; }

    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    /** 逐条明细 */
    public static class Item {

        private String iterationOid;
        private String ckfileOid;
        /** 生成的转换记录 oid —— 前端据此定位/重试 */
        private String visOid;
        /** 记录当前状态（QUEUED / SUCCESS / SKIPPED / FAILED） */
        private String status;
        /** ACCEPTED / CACHED / SKIPPED / FAILED */
        private String result;
        private String message;

        public String getIterationOid() { return iterationOid; }
        public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

        public String getCkfileOid() { return ckfileOid; }
        public void setCkfileOid(String ckfileOid) { this.ckfileOid = ckfileOid; }

        public String getVisOid() { return visOid; }
        public void setVisOid(String visOid) { this.visOid = visOid; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
