/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.List;

/**
 * 按版本删除的结果。
 *
 * <p>删除是<b>按版本</b>的（流程删除的唯一方式），所以调用方需要知道三件事：
 * <ol>
 *   <li>{@code deleted}：本次真正删掉的版本号；</li>
 *   <li>{@code templateRemoved}：删完最后一个版本后，该流程是否已<b>整体消失</b>；</li>
 *   <li>{@code latestVersion}：删完之后的最新版本号 —— 如果被删的正是"最新版"，
 *       流程内容会<b>回落到</b>剩下的最新版，UI 需要据此提示用户。</li>
 * </ol>
 */
public class ProcessVersionDeleteResult {

    /** 模板 oid */
    private String templateOid;

    /** 本次删除的版本号（升序） */
    private List<Integer> deleted;

    /** 删除后该流程已无任何版本 → 主档一并删除 */
    private Boolean templateRemoved;

    /** 删除后的最新版本号（流程整体消失时为 null） */
    private Integer latestVersion;

    public String getTemplateOid() {
        return templateOid;
    }

    public void setTemplateOid(String templateOid) {
        this.templateOid = templateOid;
    }

    public List<Integer> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<Integer> deleted) {
        this.deleted = deleted;
    }

    public Boolean getTemplateRemoved() {
        return templateRemoved;
    }

    public void setTemplateRemoved(Boolean templateRemoved) {
        this.templateRemoved = templateRemoved;
    }

    public Integer getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(Integer latestVersion) {
        this.latestVersion = latestVersion;
    }
}
