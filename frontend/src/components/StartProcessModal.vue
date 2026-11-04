<!--
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * 发起流程弹框（共享组件）—— 所有业务对象的行操作下拉都用它，
 * 保证「发起流程」只有一套交互与一份解析规则：
 *
 *   1. 解析：业务对象的类型编码 + 当前状态 → 该发起哪个流程
 *      （GET /workflow/instance/start-options，规则在类型模块的「规则绑定」里配置）；
 *   2. 展示：流程名称 / key / 最新版本 / 变更说明 / 部署状态；
 *   3. 发起：POST /workflow/instance/start，由 Flowable 按 key 启动
 *      （businessKey / entityOid 传业务对象 oid，并带上业务上下文变量与实体信息
 *       → 后端写入 ck_process_entity_set，即"该实例关联了哪个业务对象"）。
 *
 * <p>未配置 / 模板已删 / 尚未部署时不给出「发起」按钮，弹框直接说明原因与去哪配置
 * —— 不让用户点了才吃报错。
 *
 * <h3>这一版的三个修改（用户反馈）</h3>
 * <ol>
 *   <li><b>弹框不再憋屈</b>：560 → 780px，流程信息两列铺开；三段式（结论 / 业务对象 / 流程信息）
 *       与其它弹框同一套层次语言（ck-summary / ck-section）；</li>
 *   <li><b>不再套第二个弹框</b>：添加对象改成清单下方的"搜索 + 添加到本批"，
 *       选完立刻入表，少一层弹窗、也看得见自己加了什么；</li>
 *   <li><b>只允许同类型 + 同生命周期状态</b>：候选在下拉里就过滤掉（并说明有几条被状态挡下），
 *       提交前再用 {@code sameProcessingBucket} 复检一次 —— 一批对象是"一起走同一道审批"，
 *       混类型/混状态会让流程变量与审批意见对不上。</li>
 * </ol>
-->
<template>
  <a-modal
    :visible="visible"
    :title="title"
    width="780px"
    @cancel="close"
  >
    <!--
      底部按钮自绘（不用 antd 的 ok/cancel）：配了申请单时是**向导**，
      按钮要随步骤在「下一步 / 提交发起」之间切换，还要在第二步多一个「上一步」。
    -->
    <template #footer>
      <a-button @click="close">取消</a-button>
      <!-- 单步（没配申请单）或向导的第 1 步 -->
      <a-button
        v-if="!hasForm || step === 0"
        type="primary"
        :loading="starting"
        :disabled="loading || !option || !option.startable"
        @click="advance"
      >
        {{ hasForm ? '下一步' : '发起' }}
      </a-button>
      <!-- 向导的第 2 步：能回上一步改业务对象，改完内容也不丢（表单是 v-show 保持挂载的） -->
      <template v-else>
        <!-- 只有表单支持草稿（暴露了 snapshot）才给这个按钮 -->
        <a-button v-if="draftSupported" @click="saveDraft">保存草稿</a-button>
        <a-button @click="step = 0">上一步</a-button>
        <a-button type="primary" :loading="starting" @click="submitApply">提交发起</a-button>
      </template>
    </template>

    <a-spin :spinning="loading">
      <!--
        向导步骤条（2026-10-13 用户要求）：开始节点配了流程表单才分两步走；
        没配表单的老流程不显示步骤条、一步直达 —— 与从前完全一致。
      -->
      <a-steps v-if="hasForm" :current="step" size="small" class="sp-steps">
        <a-step title="选择业务对象" description="确认流程与关联对象" />
        <a-step title="填写申请信息" :description="startFormTitle" />
      </a-steps>

      <!-- 第 1 步（也是唯一一步）：确认流程 + 业务对象清单 + 流程信息 -->
      <div v-show="!hasForm || step === 0">
      <!-- ① 结论：发的是哪条流程 + 部署状态（不可发起时下面直接给原因） -->
      <div class="ck-summary">
        <div class="ck-summary__object">
          <span class="ck-summary__name">{{ processName || '（未解析到流程）' }}</span>
          <code v-if="option?.processTemplate?.key" class="ck-summary__code">
            {{ option.processTemplate.key }}
          </code>
        </div>
        <div class="sp-state" v-if="option?.processTemplate">
          <a-tag v-if="option.processTemplate.deployedVersion" color="green" size="small">
            已部署 v{{ option.processTemplate.deployedVersion }}
          </a-tag>
          <a-tag v-else color="orange" size="small">尚未部署</a-tag>
          <a-tag color="blue" size="small">
            v{{ option.processVersion?.version ?? option.processTemplate.latestVersion ?? '-' }}
          </a-tag>
        </div>
      </div>

      <a-alert
        v-if="option && !option.startable"
        type="warning"
        show-icon
        message="暂不能发起流程"
        :description="option.reason || '未解析到可用流程'"
        class="sp-alert"
      />

      <!--
        ② 业务对象清单：一个流程关联的多个对象记在 ck_process_entity_set（一行一个）。
        <b>清单里不分主次</b>：它们是同一道审批里的同行者，谁也不是谁的附件 ——
        只在"只剩一条"时不给移除（发起总得带上至少一个对象）。
      -->
      <div class="ck-section">
        <div class="ck-section__title">
          业务对象
          <span class="ck-section__hint">
            共 {{ entities.length }} 个 · 只有<b>同类型 + 同生命周期状态</b>的对象能一起走同一道审批
          </span>
        </div>

        <a-table
          :columns="entityColumns"
          :data-source="entities"
          size="small"
          row-key="oid"
          :pagination="false"
          :locale="{ emptyText: '至少需要一个业务对象' }"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'code'">
              <code class="sp-code">{{ record.code || record.entityCode || '-' }}</code>
            </template>
            <template v-else-if="column.key === 'name'">
              {{ record.name || '-' }}
            </template>
            <template v-else-if="column.key === 'type'">
              <a-tag size="small">{{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag size="small" color="blue">{{ record.statusName || record.statusCode || '-' }}</a-tag>
            </template>
            <template v-else>
              <a
                v-if="entities.length > 1"
                class="sp-remove"
                @click="removeEntity(index)"
              >移除</a>
              <span v-else class="ck-cell-muted">不可移除</span>
            </template>
          </template>
        </a-table>

        <!-- 内联添加：一行搜索下拉 + 添加按钮（不再开第二个弹窗） -->
        <div class="sp-add">
          <a-select
            v-model:value="pickOid"
            show-search
            allow-clear
            style="flex:1"
            placeholder="输入编码 / 名称筛选，选中后点右侧添加"
            :filter-option="localFilter"
            :options="candidateOptions"
            :loading="searching"
            @search="onSearchEntities"
          >
            <template #notFoundContent>
              <a-empty :image="false" description="输入编码或名称搜索" />
            </template>
          </a-select>
          <a-button type="primary" ghost :disabled="!pickOid" :loading="adding" @click="addPicked">
            添加到本批
          </a-button>
        </div>
        <div class="sp-add__hint">
          只列「{{ typeLabel }}」的对象；<b>状态与本批不一致的会置灰，添加时也会被拦下并说明原因</b>
        </div>
      </div>

      <!--
        配了表单却填不了（如靠 taskId 带出关联器件的认证表）：说清"为什么没有表单"，
        否则用户会以为是自己没配好，或对着一个空框发呆。
        （这种情况没有第二步 —— 步骤条也不显示，仍是一步直达。）
      -->
      <a-alert
        v-if="startFormUnsupported"
        type="info"
        show-icon
        message="开始节点配的表单不适用于发起"
        :description="startFormUnsupported"
        class="sp-alert"
      />

      <!--
        ④ 流程信息：紧凑键值网格。
        不用 a-descriptions：bordered 表格里标签列会被内容挤到只剩一两个字宽，
        「流程名称」竖排成四行，整行高度被标签撑起来 —— 看着就是一张过高的表。
        这里标签 nowrap、值占满剩余宽度，长文本（变更/流程说明）整行铺开。
      -->
      <div class="ck-section" v-if="option?.processTemplate">
        <div class="ck-section__title">流程信息</div>
        <div class="sp-info">
          <div class="sp-info__item">
            <span class="sp-info__label">流程名称</span>
            <span class="sp-info__value">
              {{ option.processTemplate.displayName || option.processTemplate.name }}
            </span>
          </div>
          <div class="sp-info__item">
            <span class="sp-info__label">流程 key</span>
            <code class="sp-info__value sp-info__code">{{ option.processTemplate.key }}</code>
          </div>
          <div class="sp-info__item">
            <span class="sp-info__label">最新版本</span>
            <span class="sp-info__value sp-info__value--tags">
              <a-tag color="blue" size="small">
                v{{ option.processVersion?.version ?? option.processTemplate.latestVersion }}
              </a-tag>
              <span v-if="option.processVersion?.createdAt" class="ck-cell-muted">
                {{ String(option.processVersion.createdAt).substring(0, 19).replace('T', ' ') }}
              </span>
            </span>
          </div>
          <div class="sp-info__item">
            <span class="sp-info__label">部署状态</span>
            <span class="sp-info__value sp-info__value--tags">
              <template v-if="option.processTemplate.deployedVersion">
                <a-tag color="green" size="small">已部署 v{{ option.processTemplate.deployedVersion }}</a-tag>
                <span v-if="option.processTemplate.enabled === false" class="ck-cell-muted">
                  （已停止部署，不影响已部署定义发起）
                </span>
              </template>
              <a-tag v-else color="orange" size="small">尚未部署</a-tag>
            </span>
          </div>
          <div v-if="option.processVersion?.changeNote" class="sp-info__item sp-info__item--full">
            <span class="sp-info__label">变更说明</span>
            <div class="sp-info__value sp-info__value--text">
              {{ option.processVersion.changeNote }}
            </div>
          </div>
          <div v-if="option.processTemplate.description" class="sp-info__item sp-info__item--full">
            <span class="sp-info__label">流程说明</span>
            <div class="sp-info__value sp-info__value--text">
              {{ option.processTemplate.description }}
            </div>
          </div>
        </div>
      </div>
      </div>

      <!--
        第 2 步：填写申请信息。表单组件**保持挂载**（v-show 而不是 v-if）：
        回上一步改业务对象再回来，填过的内容不会丢，collect() 也随时可用。
      -->
      <div v-show="hasForm && step === 1" class="sp-apply">
        <div class="ck-section__title">
          {{ startFormTitle }}
          <span class="ck-section__hint">发起前填写 · 字段随发起一并记入流程变量，评审 / 会签环节都会看到</span>
        </div>
        <!-- 恢复了上次的草稿：说清是哪次存的，并给"不要它"的出口 -->
        <a-alert
          v-if="draftRestored"
          type="info"
          show-icon
          class="sp-draft"
          :message="`已恢复 ${draftSavedAtText(draftRestored.savedAt)} 保存的草稿`"
        >
          <template #action>
            <a-button size="small" type="link" @click="discardDraft">清除草稿</a-button>
          </template>
        </a-alert>
        <component
          :is="startFormView"
          v-if="startFormView"
          :key="applyFormKey"
          ref="applyFormRef"
          :form="startFormPayload"
        />
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch, h, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getEntityByCode,
  getProcessStartOption,
  getProcessStartOptionByKey,
  getRunningEntityOids,
  getStartForm,
  globalSearch,
  startProcessInstance,
} from '@/api'
import {
  entityCandidates,
  entityLine,
  runningBlockReason,
  sameProcessingBucket,
  sameStatus,
  startedNotice,
  startEntityPayload,
  statusKeyOf,
  statusLabelOf,
} from '@/utils/processEntities'
import { findFormTemplate } from '@flow-dsl-core'
import { effectiveFormCode, resolveStartFormView, taskFormLabel } from '@/components/task/taskForms'
import { useUserStore } from '@/stores/user'
// 草稿：填到一半关掉弹框不该白填 —— 本地存一份，下次打开自动恢复（见 utils/startFormDraft）
import {
  clearStartFormDraft,
  draftKeyOf,
  draftSavedAtText,
  hasDraftContent,
  loadStartFormDraft,
  saveStartFormDraft,
} from '@/utils/startFormDraft'
// 模板清单（含行业包带来的自定义表单）：发起表单要按模板声明的 component 名派发视图，
// 不先注入就会找不到模板、表格区一律不显示 —— 见 form-templates.ts 的 ensureFormTemplates
import { ensureFormTemplates } from '@/flow-designer/app/form-templates'

const router = useRouter()

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** 弹窗标题：默认「发起流程」；元器件（IPN）语境传「发起器件认证」等更贴切的措辞 */
  title: { type: String, default: '发起流程' },
  /**
   * 业务对象：
   * {
   *   oid,                          // 主对象 oid（也用作 businessKey / entityOid）
   *   name, code,
   *   typeDefinitionCode,           // 类型编码
   *   typeDefinitionName?,
   *   statusCode, statusName?,      // 生命周期状态
   *   lifecycleTemplateIterationOid?, // 迭代固化的【生命周期模板子版本】oid（解析"该发哪个流程"用）
   * }
   *
   * <p>业务对象自己的版本不在这里传：关联记的是「大版本」，由后端按对象当前版本解析
   * （见 ck_process_entity_set.version）。这里只需要生命周期模板的版本 —— 两者是两回事。
   */
  business: { type: Object, default: null },
  /**
   * 直传流程 key：非空时**跳过「类型 + 状态 → 流程」解析**，直接按该 key 准备发起
   * （仍做部署判定与"该对象是否已有流程在执行"的闸门）。
   *
   * <p>供"行上自带 processKey"的入口使用 —— 产品系列页的**阶段门 / 评审**行：
   * TR1 → {@code technical-review}、CHARTER → {@code gate-decision} …。
   * 为空时保持原行为（业务对象的类型与状态解析）。
   */
  processKey: { type: String, default: '' },
  /**
   * 申请单预设值：`{ reviewTemplate?, stage?, gate? }`（值一律是 code）。
   *
   * <p>阶段门 / 评审行发起时，这些字段在行上就已经定了（行自带门码 / 评审码 / 阶段），
   * 由宿主算好后传进来，表单按 code 预填 —— 口径见 `ProductLineDashboard#presetFromGateRow`。
   * 普通入口为空，行为与从前一致（表单全空手填）。
   */
  preset: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'success'])

const loading = ref(false)
const starting = ref(false)
/** 解析结果（含不可发起的原因） */
const option = ref(null)

const processName = computed(() =>
  option.value?.processTemplate?.displayName || option.value?.processTemplate?.name || '',
)

// ==================== 申请信息（开始节点配的表单） ====================

/**
 * 发起表单上下文（后端 {@code GET /workflow/instance/start-form}）：
 * {@code { formKey, startEventId, dslJson, ... }} —— 开始节点在设计器里选的那张表单。
 * 为空 = 没配 / 取不到，弹框不渲染表单区。
 */
const startForm = ref(null)
/**
 * 向导当前步：0 = 选择业务对象，1 = 填写申请信息。
 *
 * <p>只有开始节点配了"可填表单"才有多步（见 {@link hasForm}）——
 * 没配表单的老流程永远停在 0 步，行为与从前完全一致（一步直达）。
 */
const step = ref(0)
/** 第 2 步里模板组件的实例：提交前调它的 {@code collect()} 收字段（与办理侧同一契约） */
const applyFormRef = ref(null)
/** 表单重建用的 key：「清除草稿」时 +1，让组件重新挂载回空表单 */
const applyFormKey = ref(0)

// ==================== 草稿（填到一半关掉不该白填，2026-10-13 用户要求） ====================

const userStore = useUserStore()

/**
 * 草稿键：租户 + 用户 + 流程 + **起点业务对象** + 表单 —— 同一器件、同一条流程、
 * 同一张表才互相恢复，不会串到别的器件上。
 */
const draftKey = computed(() => {
  const f = startForm.value
  if (!f?.formKey) {
    return ''
  }
  return draftKeyOf({
    tenantOid: userStore.tenantOid,
    username: userStore.username,
    processKey: option.value?.processTemplate?.key,
    entityOid: props.business?.oid,
    formKey: f.formKey,
  })
})

/** 当前表单是否支持草稿（组件暴露了 snapshot 才显示「保存草稿」按钮） */
const draftSupported = ref(false)
/** 本次恢复的草稿：{ savedAt } | null（非空时第 2 步顶部显示提示 + 清除入口） */
const draftRestored = ref(null)

/**
 * 传给模板组件的上下文 —— 刻意做成与办理侧 {@code ProcessTaskFormVO} 同一形状
 * （{@code taskDefinitionKey} ← {@code startEventId}），组件因此不必区分"发起 / 办理"两种来源。
 */
const startFormPayload = computed(() => {
  const f = startForm.value
  if (!f?.formKey) {
    return null
  }
  return {
    formKey: f.formKey,
    taskDefinitionKey: f.startEventId || 'start_1',
    dslJson: f.dslJson,
    processDefinitionKey: f.processDefinitionKey,
    processDefinitionVersion: f.processDefinitionVersion,
    // 本次发起选中的业务对象（与提交给后端的 entities 是同一份选择）：
    // 「双来源」表单（如元器件认证申请）在发起场景没有任务上下文可读，
    // 就用它把器件与 MPN 带出来 —— 见 ComponentQualificationForm#resolveEntityRefs
    entities: startEntityPayload(entities.value),
    // 主业务对象（清单第一条 = 后端记的 primary）：表单按它现算数据 ——
    // 如门决策申请单的"未关闭阻断问题数"自动带出（与办理侧 ProcessTaskFormVO.businessOid 同义）
    businessOid: entities.value[0]?.oid || props.business?.oid || undefined,
    businessTypeCode: entities.value[0]?.typeDefinitionCode || props.business?.typeDefinitionCode || undefined,
    // 门 / 评审行带来的预设值（评审模板 / 所处阶段 / 关联阶段门）：
    // 表单自己判断认不认这些 key、值在不在下拉里 —— 这里只做透传
    presets: props.preset || undefined,
  }
})

/** 能填才有视图；依赖任务上下文的表单（认证表、评审表…）这里得到 null */
const startFormView = computed(() => resolveStartFormView(startFormPayload.value))

const startFormTitle = computed(() => taskFormLabel(effectiveFormCode(startFormPayload.value)))

/**
 * 配了表单、却填不了时的说明（空串 = 不提示）。
 *
 * <p>两种情形要分开说，否则给出的是**误导性**的原因：
 * <ol>
 *   <li><b>模板本身没了</b>（被删除或停用，而流程里还写着这个 code）——
 *       这是"配置悬空"，要让用户去设计器重选或清空，而不是告诉他"缺上下文"；</li>
 *   <li><b>模板在、但发起时填不了</b>（认证表 / 评审表 / 会签表都靠任务上下文）。</li>
 * </ol>
 */
const startFormUnsupported = computed(() => {
  const f = startForm.value
  if (!f?.formKey || startFormView.value) {
    return ''
  }
  if (!findFormTemplate(f.formKey)) {
    return `开始节点配的表单「${f.formKey}」已经不存在了（可能被删除或停用）—— `
      + '请到设计器为开始节点重新选一张申请单，或清空该字段（发起时就不填表单）。'
  }
  return `「${startFormTitle.value}」需要流程实例上下文（关联器件或流程变量），`
    + '无法在发起时填写。请到设计器把开始节点的表单换成自足的申请单'
    + '（如「阶段门决策申请单」），或把这张表留给流程里的办理节点使用。'
})

/**
 * 是否走向导：开始节点配了**能填的**申请单就分两步
 * （用户口径：配了流程表单就用 wizard 形式；没配 = 一步直达，与从前一致）。
 */
const hasForm = computed(() => !!startFormView.value)

/**
 * 本次要关联的业务对象：<b>不分主次</b>，打开弹框时以"从哪进来"的那个对象为起点。
 *
 * <p>起点对象同时定下这一批的"类型 + 状态"口径 —— 后面能加进来的必须与它一致
 * （见 entityCandidates / sameProcessingBucket）。
 */
const entities = ref([])

/** 本批的类型 / 状态口径：取清单里第一个能拿到的（起点对象） */
const batchTypeCode = computed(
  () => entities.value[0]?.typeDefinitionCode || props.business?.typeDefinitionCode,
)
const batchStatusCode = computed(
  () => entities.value[0]?.statusCode || props.business?.statusCode,
)
/**
 * 本批的状态**标识**（code 优先、缺了退回显示名）。
 *
 * <p>候选与"本批"的比较必须用同一个口径：企业资源/元器件库的列表行常常只有状态显示名
 * （`statusName` = 「草稿」）而没有 `statusCode`，只比 code 会让比较恒为"未知"、
 * 校验被整体跳过 —— 「工作中」的对象就是这样混进「草稿」批次的。
 */
const batchStatusKey = computed(() => statusKeyOf(entities.value[0] || props.business))
const typeLabel = computed(
  () => entities.value[0]?.typeDefinitionName || props.business?.typeDefinitionName
    || batchTypeCode.value || '同类型',
)
const statusLabel = computed(
  () => entities.value[0]?.statusName || props.business?.statusName || batchStatusCode.value || '',
)

const entityColumns = [
  { key: 'code', title: '编码', width: 190 },
  { key: 'name', title: '名称' },
  { key: 'type', title: '类型', width: 130 },
  { key: 'status', title: '状态', width: 110 },
  { key: 'action', title: '操作', width: 90 },
]

// ==================== 添加业务对象（内联，不再套第二个弹窗） ====================

/** 当前下拉里选中的候选 oid（点"添加到本批"才真正入表） */
const pickOid = ref('')
const candidateOptions = ref([])
const searching = ref(false)
const adding = ref(false)

/**
 * 候选池：把搜索过的结果按 oid 累积下来。
 *
 * <p>为什么要有池子、且**不在搜索阶段卡状态**：全局搜索返回的是
 * `{ type, oid, code, name, link, typeDefinitionCode }` —— <b>没有生命周期状态</b>。
 * 一度在这里按"同类型 + 同状态"过滤，结果就是"搜到了却什么都不显示"（下拉空掉）。
 * 现在改成：同类型的结果全部进池子并展示，状态在**添加那一刻**单独核（见 resolveStatus），
 * 不一致就明确拦下并说明原因 —— 用户始终看得见"有哪些对象、为什么不能选"。
 */
const candidatePool = ref([])

/** 本地即时筛选：按标签匹配池子里的候选，输入即出结果（不必每敲一个字都打远程） */
function localFilter(input, option) {
  const kw = String(input || '').trim().toLowerCase()
  if (!kw) {
    return true
  }
  return String(option?.label || '').toLowerCase().includes(kw)
}

/** 状态 code 归一：忽略大小写与空白 */
function normalizeStatus(value) {
  return String(value ?? '').trim().toUpperCase()
}

function resetPicker() {
  pickOid.value = ''
  candidateOptions.value = []
  candidatePool.value = []
  searching.value = false
  adding.value = false
}

/**
 * 在流程中的候选（后端判定，见 {@link getRunningEntityOids}）。
 *
 * <p>结果按 oid 缓存：搜索是逐键触发的，同一个对象不该反复问后端。
 * 判定放在这里（而不是状态那一段）是因为它只服务于候选列表。
 */
const runningOids = ref(new Set())
const runningChecked = new Set()

/** 池子 → 下拉选项：已选中的不再出现；**在流程中 / 状态不符**的置灰并写明原因 */
function rebuildOptions() {
  const excluded = new Set(entities.value.map((e) => e.oid))
  const head = entities.value[0] || props.business
  candidateOptions.value = candidatePool.value
    .filter((c) => !excluded.has(c.oid))
    .map((c) => {
      // 「正在流程中」优先于"状态不符"：前者根本不可选，后者只是这一批不匹配
      const running = runningBlockReason(c, runningOids.value)
      // 状态比较用 sameStatus（code 对 code、名对名），与本批口径一致
      const verdict = sameStatus(head, c)
      const mismatch = verdict.known && !verdict.same
      const label = statusLabelOf(c)
      const statusText = statusKeyOf(c) ? ` · ${label}` : ''
      const reason = running
        ? `（${running}）`
        : (mismatch ? `（与本批「${statusLabel.value || batchStatusCode.value}」不一致）` : '')
      return {
        value: c.oid,
        label: `${entityLine(c)}${statusText}${reason}`,
        disabled: !!running || mismatch,
      }
    })
}

/**
 * 批量判定候选里哪些正在流程中（并入 runningOids；查过的 oid 不再问）。
 *
 * <p>为什么在"搜索"阶段就判、而不是等"添加到本批"：用户扫列表时就该看出谁不能选 ——
 * 允许点、点了才被拒，正是这次要消灭的体验。
 */
async function probeRunning(candidates) {
  const todo = candidates
    .map((c) => c.oid)
    .filter((oid) => oid && !runningChecked.has(oid))
  if (!todo.length) {
    return
  }
  todo.forEach((oid) => runningChecked.add(oid))
  try {
    const res = await getRunningEntityOids(batchTypeCode.value, todo)
    const hits = new Set(res?.code === 200 ? (res.data || []) : [])
    // 以本次结果为准（加进命中的、去掉已结束的）：对象可能在这两次搜索之间跑完了流程，
    // 只加不删会让它一直灰着
    const next = new Set(runningOids.value)
    todo.forEach((oid) => (hits.has(oid) ? next.add(oid) : next.delete(oid)))
    runningOids.value = next
  } catch {
    // 查不动就当"都没在流程中"（并把 oid 放回待查）：真正的拦截在发起那一步，
    // 不该因为一次查询失败就把候选全清空
    todo.forEach((oid) => runningChecked.delete(oid))
  }
}

/**
 * 搜索业务对象：全局搜索（按名称/编码）→ 同类型的结果进池子，并**补查它们的状态**。
 *
 * <p>为什么要补查：全局搜索不返回生命周期状态，不补的话候选里"看不出谁不合规"，
 * 用户还以为能加（下拉里既没有状态、也不置灰）。补查后每个候选都带状态，
 * 与本批不一致的直接置灰并写明原因 —— 在"选"这一刻就说清，而不是等添加时才拦。
 *
 * <p>只补前 {@code STATUS_PROBE_LIMIT} 条：一次搜索 30 条各发一个详情请求太重，
 * 而这几个足够覆盖用户"从最近的结果里挑"的用法。
 */
const STATUS_PROBE_LIMIT = 10

async function onSearchEntities(keyword) {
  const q = (keyword || '').trim()
  if (!q) {
    return
  }
  searching.value = true
  try {
    const res = await globalSearch(q, 30)
    const list = res?.code === 200 ? (res.data || []) : []
    const sameType = entityCandidates(list, { typeCode: batchTypeCode.value })
    const merged = new Map(candidatePool.value.map((c) => [c.oid, c]))
    sameType.forEach((c) => merged.set(c.oid, { ...merged.get(c.oid), ...c }))
    candidatePool.value = [...merged.values()]
    // 先判"是否在流程中"再渲染：正在流程中的对象根本不该可选（后端判定，见 probeRunning）
    await probeRunning(sameType)
    rebuildOptions()

    // 补查状态（并发，失败的保持"未知"——未知不会置灰，但添加时仍会被拦下）
    const needProbe = sameType
      .filter((c) => !statusKeyOf(c))
      .slice(0, STATUS_PROBE_LIMIT)
    if (needProbe.length) {
      const probed = await Promise.all(needProbe.map((c) => resolveStatus(c)))
      const byOid = new Map(probed.map((c) => [c.oid, c]))
      candidatePool.value = candidatePool.value.map((c) => byOid.get(c.oid) || c)
      rebuildOptions()
    }
  } finally {
    searching.value = false
  }
}

/**
 * 取某个对象的当前生命周期状态。
 *
 * <p>搜索结果不带状态，所以要在"添加"这一步补一次：宁可多一次请求，
 * 也不能因为"看不到状态"就把不合规的对象放进同一道审批。
 */
async function resolveStatus(candidate) {
  if (candidate.statusCode) {
    return candidate
  }
  try {
    const res = await getEntityByCode(candidate.typeDefinitionCode, candidate.oid)
    const detail = res?.code === 200 ? res.data : null
    if (detail) {
      return {
        ...candidate,
        statusCode: detail.statusCode || detail.status?.code || candidate.statusCode,
        statusName: detail.statusName || detail.status?.displayName || candidate.statusName,
        typeDefinitionName: detail.typeDefinitionName || candidate.typeDefinitionName,
      }
    }
  } catch {
    // 取不到状态就按"未知"处理，下面会拦下并说明
  }
  return candidate
}

/** 把选中的候选加进清单：入表前核状态，并复检"同类型 + 同状态" */
async function addPicked() {
  const picked = candidatePool.value.find((c) => c.oid === pickOid.value)
  if (!picked) {
    return
  }
  adding.value = true
  try {
    // 复检"是否在流程中"：候选里已置灰，但对象可能刚被别人发起（池子是上一次搜索的快照）。
    // 刻意绕过缓存（先把它从"已查过"里去掉）—— 这一步要的是当下的判定，不是搜索结果
    runningChecked.delete(picked.oid)
    await probeRunning([picked])
    const running = runningBlockReason(picked, runningOids.value)
    if (running) {
      message.warning(`「${picked.code || picked.name}」${running}`)
      rebuildOptions()
      return
    }
    const resolved = await resolveStatus(picked)
    // 补查到的状态写回池子：下拉里就能看到它的状态、该置灰的置灰
    candidatePool.value = candidatePool.value.map((c) => (c.oid === resolved.oid ? resolved : c))
    if (!statusKeyOf(resolved)) {
      message.warning(
        `无法确认「${resolved.code || resolved.name}」的当前状态，暂不加入本批`
        + '（同一道审批要求所有对象状态一致），请刷新列表后重试',
      )
      return
    }
    const check = sameProcessingBucket([...entities.value, resolved])
    if (!check.ok) {
      message.warning(check.reason)
      return
    }
    entities.value = [...entities.value, resolved]
    pickOid.value = ''
    rebuildOptions()
  } finally {
    adding.value = false
  }
}

/** 移除一个对象：只剩一条时不给移除（发起总得带上至少一个业务对象） */
function removeEntity(index) {
  if (entities.value.length <= 1) {
    message.warning('至少保留一个业务对象')
    return
  }
  entities.value = entities.value.filter((_, i) => i !== index)
  // 移除后它要能重新出现在候选里
  rebuildOptions()
}

watch(() => props.visible, (open) => {
  // 每次打开都是一次新的发起：清单回到"只有进来的那个对象"，
  // 上一轮加的对象若残留，下次发起会莫名其妙多关联几个
  entities.value = props.business ? [props.business] : []
  resetPicker()
  // 向导步数不跨次残留：下次打开一律从第 1 步开始
  step.value = 0
  draftSupported.value = false
  draftRestored.value = null
  if (open) {
    load()
    // 定时兜底：刷新 / 崩溃不经过 close，靠它把第 2 步的内容定期落本地
    startDraftTimer()
  } else {
    // 外部直接置 visible=false（父组件关闭）也落一份，再停表
    persistDraft()
    stopDraftTimer()
  }
})

/**
 * 起点对象若只有状态**显示名**（企业资源/元器件库的列表行如此，如「草稿」），
 * 补一次详情把 `statusCode` 拿到。
 *
 * <p>两个原因：① 解析"类型 + 状态 → 流程"必须要 statusCode；
 * ② 与候选比较时"code 对 code"最可靠（否则会拿「草稿」去比 `DRAFT`，口径不一致）。
 * 拿不到就按原样继续，比较时退回显示名口径，不误拦。
 */
async function ensureBatchStatus() {
  const head = entities.value[0] || props.business
  if (!head?.oid || head.statusCode) {
    return
  }
  try {
    const res = await getEntityByCode(
      head.typeDefinitionCode || props.business?.typeDefinitionCode,
      head.oid,
    )
    if (res?.code === 200 && res.data) {
      const merged = { ...head, ...res.data }
      entities.value = entities.value.length ? [merged, ...entities.value.slice(1)] : [merged]
    }
  } catch {
    // 忽略：保持原样
  }
}

/** 解析：按 key 直传（有 processKey 时）或 类型 + 状态 → 该发起的流程模板（含最新版本信息） */
async function load() {
  option.value = null
  const preset = (props.processKey || '').trim()
  if (preset) {
    // 直传模式：行上已明确"发这条流程"（阶段门 / 评审行），不再走类型-状态解析；
    // 部署判定与"该对象是否已有流程在执行"的闸门仍由后端同一份逻辑给出，
    // startable / reason 的语义与另一条入口完全一致
    const head = entities.value[0] || props.business
    loading.value = true
    try {
      const res = await getProcessStartOptionByKey({
        processKey: preset,
        typeCode: head?.typeDefinitionCode || undefined,
        // 列表 DTO 的字段名不统一：有的只给显示名口径的 status
        statusCode: head?.statusCode || head?.status || undefined,
        entityOid: head?.oid || undefined,
      })
      option.value = res?.code === 200
        ? (res.data || null)
        : { startable: false, reason: res?.message || '解析发起流程信息失败' }
    } catch {
      option.value = { startable: false, reason: '解析发起流程信息失败，请稍后重试' }
    } finally {
      loading.value = false
    }
    await loadStartForm()
    return
  }
  await ensureBatchStatus()
  const business = entities.value[0] || props.business
  if (!business?.typeDefinitionCode || !business?.statusCode) {
    // 不同列表 DTO 的字段名略有差异，缺字段时先在这里说清，不白跑一次请求
    option.value = {
      startable: false,
      reason: '无法确定该对象的类型或当前状态（缺少 typeDefinitionCode / statusCode），请刷新列表后重试',
    }
    return
  }
  loading.value = true
  try {
    const res = await getProcessStartOption({
      typeDefinitionCode: business.typeDefinitionCode,
      statusCode: business.statusCode,
      // 传"迭代固化的生命周期模板子版本"：优先按对象出生时那一版配置解析
      iterationOid: business.lifecycleTemplateIterationOid || undefined,
      // 传业务对象 oid：后端据此判定"该对象是否已有流程在执行"（有则 startable=false，直接禁用发起）
      entityOid: business.oid || undefined,
    })
    option.value = res?.code === 200
      ? (res.data || null)
      : { startable: false, reason: res?.message || '解析发起流程信息失败' }
  } catch {
    option.value = { startable: false, reason: '解析发起流程信息失败，请稍后重试' }
  } finally {
    loading.value = false
  }
  // 流程解析出来之后才有 key 可取"开始节点配的表单"
  await loadStartForm()
}

/**
 * 取发起表单上下文（设计期在开始节点上选的那张表单）。
 *
 * <p>失败/取不到一律静默：没有它只是少一段申请信息，不该挡住发起
 * （后端未部署时也只返回空字段，本就不会给「发起」按钮）。
 */
async function loadStartForm() {
  startForm.value = null
  const key = option.value?.processTemplate?.key
  if (!option.value?.startable || !key) {
    return
  }
  // 模板清单先注入（幂等、失败静默）：行业表单按"模板声明的 component 名"派发视图，
  // 不先注入 findFormTemplate 就找不到它，表单区会整段消失
  await ensureFormTemplates()
  try {
    const res = await getStartForm(key)
    startForm.value = res?.code === 200 ? (res.data || null) : null
  } catch {
    startForm.value = null
  }
}

/**
 * 底部「下一步 / 发起」：复检"同类型 + 同状态"，过了才前进 ——
 * 有申请单 → 进第 2 步；没有 → 直接发起。
 *
 * <p>复检的必要性：候选虽已过滤，清单里仍可能混进历史数据或中途变过状态的对象。
 */
async function advance() {
  const resolved = option.value
  const business = props.business
  if (!resolved?.startable || !resolved.processTemplate || !business) {
    return
  }
  const bucket = sameProcessingBucket(entities.value)
  if (!bucket.ok) {
    message.warning(bucket.reason)
    return
  }
  if (hasForm.value) {
    step.value = 1
    // 进入第 2 步再探测"是否支持草稿"并尝试恢复（此时表单组件才挂载）
    afterApplyShown()
    return
  }
  await doStart({})
}

/**
 * 向导第 2 步「提交发起」：先让模板自己校验并收集字段（与办理侧同一契约 {@code collect()}），
 * 校验不过就停在这一步（提示并留在填写页）—— 别把发起做完了才发现申请信息没填全。
 */
async function submitApply() {
  const collected = applyFormRef.value?.collect?.() || { ok: true, payload: {} }
  if (!collected.ok) {
    message.warning(collected.message || '申请信息填写有误')
    return
  }
  await doStart(collected.payload?.variables || {})
}

/**
 * 真正发起（第二步确认后；没有申请单时由主弹框直接调）。
 *
 * <p>{@code businessKey} 与 {@code lifecycleStatus} 取的是<b>发起上下文</b>
 * （从哪个对象进来的、它当时什么状态）—— 流程模板就是按这两者解析出来的，
 * 所以它们不随清单增删变化：清单里是"这次要关联哪些对象"，与"从哪儿发起"是两件事。
 *
 * @param formVariables 申请单收集来的流程变量（没有申请单时给空对象）
 */
async function doStart(formVariables) {
  const resolved = option.value
  const business = props.business
  starting.value = true
  try {
    const res = await startProcessInstance({
      processKey: resolved.processTemplate.key,
      // businessKey = 整个实例的业务标识（发起上下文，不是"清单老大"）
      businessKey: business.oid,
      // 实体清单 → 后端逐个过闸门、逐个写 ck_process_entity_set（一行一个），
      // 并产出流程变量 businessObjectSet（primary 只是数组第一条，前端不分主次）。
      // 版本不用前端传：关联记的是业务对象的「大版本」，由后端按对象当前版本解析
      // （大版本下还会继续产出小版本，前端手里的迭代 oid 一旦对象检出就过时了）。
      entities: startEntityPayload(entities.value),
      // 发起上下文的单对象字段：老形态入参靠它，别的发起入口也走同一条路
      entityOid: business.oid,
      typeCode: resolved.typeDefinitionCode,
      // 业务对象编码：只用于流程变量 businessObjectSet.code 的显示（流程模块不回查业务表，
      // 编码只有发起方手里有）。对象 oid / 类型 / 大版本一律由后端按关联表产出，前端不重复传。
      entityCode: business.code || undefined,
      // 发起那一刻的对象状态：与"清单里有哪些对象"不是一回事，单独作为变量
      variables: {
        // 申请单字段在前、系统口径在后：表单变量覆盖不了 lifecycleStatus 这类系统事实
        ...formVariables,
        lifecycleStatus: resolved.statusCode,
      },
    })
    if (res?.code === 200) {
      notifyStarted(res.data)
      // 提交成功：草稿使命结束 —— 先清掉并关掉"支持草稿"标记，
      // 否则 close() 里的 persistDraft 会把刚提交的内容又存回来
      clearStartFormDraft(draftKey.value)
      draftSupported.value = false
      close()
      emit('success')
    }
    // 失败原因由响应拦截器统一提示（含"该对象已有流程在执行中"这类 409），此处不再重复弹一次
  } catch {
    message.error('发起流程失败')
  } finally {
    starting.value = false
  }
}

/**
 * 发起成功的提示：先给「人话结论 + 下一步」，实例号退到第二行浅灰小字。
 *
 * <p>原来是「流程已发起（实例 5dcba301-…）」—— UUID 对用户没有意义，也没说清
 * 发的是哪条对象、哪个流程、接下来去哪。现在正文是
 * 「20W电容（PART-202609-0020）的『元部件引入』已启动，请到任务中心跟进。」，
 * 并给一个可点的「去任务中心」：发起之后紧接着就是去办，不该让人自己翻菜单。
 */
function notifyStarted(instanceId) {
  const template = option.value?.processTemplate
  const text = startedNotice({
    entities: entities.value,
    business: props.business,
    processName: template?.displayName || template?.name || template?.key || '',
  })
  message.success({
    // 提示里放了可点的链接，停留久一点（默认 3s 常常来不及点）
    duration: 6,
    content: h('div', null, [
      h('div', null, text),
      h('div', { style: 'margin-top:4px;font-size:12px' }, [
        h('a', { onClick: goTaskCenter }, '去任务中心'),
        // 实例号留着但不占正文：真出了问题，把它交给管理员比"我刚才点的那个流程"有用。
        // 样式写在内联（message 渲染在组件之外，scoped 样式到不了那里）
        instanceId
          ? h('span', { style: 'margin-left:12px;color:#bfbfbf' }, `实例 ${instanceId}`)
          : null,
      ]),
    ]),
  })
}

/** 跳到任务中心：提示里的"下一步"就该能一点到达 */
function goTaskCenter() {
  close()
  router.push({ name: 'TaskCenter' })
}

/**
 * 保存草稿（空表单不存 —— 免得下次提示"已恢复"却什么都没有）。
 *
 * @returns {boolean} 是否真的存了
 */
function persistDraft() {
  if (!draftSupported.value || !draftKey.value) {
    return false
  }
  const snap = applyFormRef.value?.snapshot?.()
  if (!hasDraftContent(snap)) {
    return false
  }
  return saveStartFormDraft(draftKey.value, snap)
}

/** 手动「保存草稿」 */
function saveDraft() {
  if (persistDraft()) {
    message.success('草稿已保存，下次打开这张表单会自动恢复')
  } else {
    message.info('当前没有可保存的内容')
  }
}

/**
 * 第 2 步就绪后：探测表单是否支持草稿（暴露了 snapshot 才支持），有草稿就恢复并提示。
 */
async function afterApplyShown() {
  await nextTick()
  draftSupported.value = typeof applyFormRef.value?.snapshot === 'function'
  draftRestored.value = null
  if (!draftSupported.value || !draftKey.value) {
    return
  }
  const draft = loadStartFormDraft(draftKey.value)
  if (!draft || !hasDraftContent(draft.data)) {
    return
  }
  applyFormRef.value.restoreDraft(draft.data)
  draftRestored.value = { savedAt: draft.savedAt }
  message.info(`已恢复 ${draftSavedAtText(draft.savedAt)} 保存的草稿`)
}

/** 清除草稿：清掉本地那份并重建表单（回到空表单） */
function discardDraft() {
  clearStartFormDraft(draftKey.value)
  draftRestored.value = null
  applyFormKey.value += 1
  message.info('草稿已清除')
}

/**
 * 定时兜底：每 5 秒把第 2 步的内容存一次。
 *
 * <p>为什么要它：关闭弹框时会存（见 close），但**刷新 / 崩溃**不经过 close ——
 * "不小心"里正包含这两种。定时快照只写本地，开销可忽略。
 */
let draftTimer = null
function startDraftTimer() {
  stopDraftTimer()
  draftTimer = setInterval(() => {
    if (step.value === 1) {
      persistDraft()
    }
  }, 5000)
}
function stopDraftTimer() {
  if (draftTimer) {
    clearInterval(draftTimer)
    draftTimer = null
  }
}

function close() {
  // 关掉之前先把第 2 步的内容落一份 —— "填了一大堆手滑关掉"正是要挡的场景
  persistDraft()
  stopDraftTimer()
  emit('update:visible', false)
}
</script>

<style scoped>
.sp-state {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sp-alert {
  margin-top: 12px;
}

/* 向导步骤条：与下面的内容拉开距离，不要贴在一起 */
.sp-steps {
  margin-bottom: 16px;
}

/* 第 2 步（填写申请信息）的内容区 */
.sp-apply {
  padding-top: 4px;
}

/* 草稿恢复提示：与下方表单拉开一点 */
.sp-draft {
  margin-bottom: 12px;
}

/* 编码：等宽，便于逐行比对 */
.sp-code {
  padding: 1px 6px;
  border-radius: 3px;
  background: #f5f5f5;
  font-family: monospace;
  font-size: 12px;
  color: #595959;
}

.sp-remove {
  font-size: 12px;
  color: #ff4d4f;
}

/*
  流程信息：两列键值网格。
  标签 nowrap（一行一字竖排会把每行撑到三四行高），值吃掉剩余宽度；
  长文本项整行铺开，宽度是标签+值各占一半时的两倍，行数自然少一半。
*/
.sp-info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  /* 用背景色当分隔线：比给每个格子画四边边框少一半样式，也不会有双线 */
  background: #f0f0f0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}

.sp-info__item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding: 6px 10px;
  background: #fff;
}

/* 变更说明 / 流程说明 */
.sp-info__item--full {
  grid-column: 1 / -1;
}

.sp-info__label {
  flex: 0 0 auto;
  font-size: 12px;
  line-height: 20px;
  color: #8c8c8c;
  white-space: nowrap;
}

.sp-info__value {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  line-height: 20px;
  color: rgba(0, 0, 0, 0.88);
  word-break: break-word;
}

/* 带标签（tag）的值：横向排列，窄屏自动折行而不是溢出 */
.sp-info__value--tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.sp-info__value--text {
  line-height: 1.65;
  white-space: pre-wrap;
}

.sp-info__code {
  font-family: monospace;
  font-size: 12px;
  color: #595959;
}

/* 添加对象：一行搜索 + 按钮，贴在本批清单下面 */
.sp-add {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.sp-add__hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}
</style>
