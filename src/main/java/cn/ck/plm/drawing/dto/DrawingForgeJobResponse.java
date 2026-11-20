/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.dto;

/** 转换器受理任务后的应答（异步：拿到的只是 QUEUED）。 */
public class DrawingForgeJobResponse {

    private String jobId;
    private String status;
    private boolean accepted;
    private String message;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
