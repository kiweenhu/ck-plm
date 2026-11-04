/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

/**
 * 转换器接受任务后的应答。
 *
 * <p>提交是<b>异步</b>的：这里拿到的只是"任务已受理"（QUEUED），
 * 真正的结果由转换器完成后回调 CK-PLM。前端靠轮询本工程的转换状态拿到进展。
 */
public class ForgeJobResponse {

    private String jobId;
    private String status;
    private boolean accepted;
    private String message;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
