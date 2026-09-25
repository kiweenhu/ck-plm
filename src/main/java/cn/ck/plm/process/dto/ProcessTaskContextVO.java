/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务办理页的<b>渲染上下文</b> —— 一次请求给齐「通用信息 + 任务表单 + 完整进度」所需的一切。
 *
 * <h3>为什么要有这个聚合对象</h3>
 * <p>办理页是<b>冷启动</b>的独立窗口：打开时手里只有一个任务 id，而页面要展示的东西来自五个地方
 * （业务实体关联、流程实例、当前任务、节点表单上下文、活动经路）。让前端自己拼 4~5 个请求，
 * 会带来两个真问题：
 * <ul>
 *   <li><b>先后依赖</b>：要拿流程实例 id 才能查流程与进度，要拿 formKey 才能选表单 —— 串行 3 跳，
 *       每跳都可能失败，页面就得处理各种"半加载"状态；</li>
 *   <li><b>口径漂移</b>：同一个"当前节点"由两处接口分别算，迟早不一致。</li>
 * </ul>
 * 所以由服务端按<b>一个</b>上下文给出，前端只负责"拿到什么渲染什么"。
 *
 * <h3>通用信息的四个来源</h3>
 * <ol>
 *   <li>{@link #task}：当前任务（名称 / 负责人 / 创建时间 / 截止时间 / 节点表单 code）；</li>
 *   <li>{@link #process}：流程实例（流程名、状态、发起人、起止时间）；</li>
 *   <li>{@link #entities}：该流程实例关联的<b>业务实体</b>（{@code ck_process_entity_set} 一行一个成员，
 *       一个实例可能关联多个）；</li>
 *   <li>{@link #activities}：完整进度（已办 / 当前在办 / 尚未到达）。</li>
 * </ol>
 * 加上 {@link #form}（节点表单模板上下文），构成办理页的全部渲染输入。
 *
 * <p>实体的<b>显示名（编码/名称）</b>不在这里解析：那要按能力宿主回查各自的业务表，
 * 属于展示层按需补的细节（前端可容忍失败，实体引用本身照常显示）。
 */
public class ProcessTaskContextVO {

    /** 当前任务（在办） */
    private ProcessTaskVO task;

    /** 流程实例信息（定义名 / 状态 / 发起人 / 起止时间）；取不到时为 null（页面降级显示） */
    private ProcessInstanceVO process;

    /** 该流程实例关联的业务实体（可能多个） */
    private List<EntityRef> entities = new ArrayList<>();

    /** 任务表单模板上下文（formKey / 活动 id / 该实例在跑那一版的 DSL） */
    private ProcessTaskFormVO form;

    /** 完整进度：已办 / 在办 / 未到达 */
    private List<ProcessActivityVO> activities = new ArrayList<>();

    /**
     * 业务实体引用（{@code ck_process_entity_set} 的一行）。
     *
     * <p>只给"引用 + 类型"，不回查业务表 —— 一是避免流程模块反向依赖各业务模块，
     * 二是办理页对实体名称的展示是<b>锦上添花</b>，不该因为它查不到就整页失败。
     */
    public static class EntityRef {

        /** 业务对象主 oid */
        private String entityOid;

        /** 业务对象大版本（无版本对象为 null） */
        private String entityVersion;

        /** 业务对象类型编码（{@code ck_type_definition.code}） */
        private String typeCode;

        /** 能力宿主编码（PART / DOCUMENT / ENG_DOCUMENT …），决定对象落在哪张业务表 */
        private String rootTypeCode;

        /** 发起流程时传入的业务标识（一般就是对象 oid） */
        private String businessKey;

        public String getEntityOid() { return entityOid; }
        public void setEntityOid(String entityOid) { this.entityOid = entityOid; }

        public String getEntityVersion() { return entityVersion; }
        public void setEntityVersion(String entityVersion) { this.entityVersion = entityVersion; }

        public String getTypeCode() { return typeCode; }
        public void setTypeCode(String typeCode) { this.typeCode = typeCode; }

        public String getRootTypeCode() { return rootTypeCode; }
        public void setRootTypeCode(String rootTypeCode) { this.rootTypeCode = rootTypeCode; }

        public String getBusinessKey() { return businessKey; }
        public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    }

    // ==================== Getter / Setter ====================

    public ProcessTaskVO getTask() { return task; }
    public void setTask(ProcessTaskVO task) { this.task = task; }

    public ProcessInstanceVO getProcess() { return process; }
    public void setProcess(ProcessInstanceVO process) { this.process = process; }

    public List<EntityRef> getEntities() { return entities; }
    public void setEntities(List<EntityRef> entities) { this.entities = entities; }

    public ProcessTaskFormVO getForm() { return form; }
    public void setForm(ProcessTaskFormVO form) { this.form = form; }

    public List<ProcessActivityVO> getActivities() { return activities; }
    public void setActivities(List<ProcessActivityVO> activities) { this.activities = activities; }
}
