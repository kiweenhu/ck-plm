<template>
  <a-form layout="vertical">
    <!-- ① 只读：流程绑定的待评对象 —— 与「新器件申请认证」同一手法（引用由流程带出，不手抄） -->
    <div class="tra-section">待评对象</div>
    <a-spin :spinning="loadingEntities">
      <a-empty
        v-if="!loadingEntities && !entities.length"
        description="该流程未关联业务对象 —— 从对象详情页发起评审时，待评对象会自动带出"
        class="tra-empty"
      />
      <div v-for="row in entities" :key="row.ref.entityOid" class="tra-object">
        <span class="tra-object__code">{{ row.info?.code || row.ref.entityOid }}</span>
        <span class="tra-object__name">{{ row.info?.name || '—' }}</span>
        <a-tag v-if="row.ref.entityVersion" color="blue">版本 {{ row.ref.entityVersion }}</a-tag>
        <a-tag v-if="row.info?.statusName" color="green">{{ row.info.statusName }}</a-tag>
        <span class="tra-object__type">{{ row.ref.typeCode }}</span>
      </div>
    </a-spin>

    <div class="tra-section">评审安排</div>

    <a-form-item label="评审模板" required>
      <a-select
        v-model:value="model.reviewTemplate"
        :options="reviewOptions"
        :loading="loadingMeta"
        show-search
        option-filter-prop="label"
        placeholder="选择评审模板（取值 = 行业包技术门内嵌 reviews 的 code）"
        allow-clear
      />
    </a-form-item>

    <a-form-item label="所处阶段" required>
      <a-select
        v-model:value="model.stage"
        :options="stageOptions"
        :loading="loadingMeta"
        show-search
        option-filter-prop="label"
        placeholder="选择所处研发阶段"
        allow-clear
      />
    </a-form-item>

    <a-form-item label="关联阶段门">
      <a-select
        v-model:value="model.gate"
        :options="gateOptions"
        :loading="loadingMeta"
        show-search
        option-filter-prop="label"
        placeholder="选择关联的阶段门（可空）"
        allow-clear
      />
    </a-form-item>

    <!--
      送审材料 = 从本系列的「交付件」里勾交付项（2026-10-26 用户要求）。
      原来是自由文本：手抄名称 / 编码既容易打错，也跟交付件面板对不上号 ——
      评审要的"材料"本来就是本系列该交的那些交付项，直接选它。
    -->
    <a-form-item label="送审材料">
      <a-select
        v-model:value="model.materials"
        mode="multiple"
        :options="materialOptions"
        :loading="loadingMaterials"
        show-search
        option-filter-prop="label"
        :placeholder="materialPlaceholder"
        :max-tag-count="8"
        :not-found-content="loadingMaterials ? '正在读取交付件…' : materialEmptyText"
        allow-clear
      />
      <!--
        已选材料逐条列出并**可点开详情**（2026-10-29 用户要求）：
        下拉里只显示「名称（编码）」，选完就只剩标签，看不清这一项要交什么、
        归谁、完成条件是什么 —— 那恰恰是评审最该看的东西。点名称开右侧抽屉。
      -->
      <div v-if="selectedMaterials.length" class="tra-materials">
        <span class="tra-materials__label">已选 {{ selectedMaterials.length }} 项，点名称看交付项详情：</span>
        <a v-for="item in selectedMaterials" :key="item.code" class="tra-material-link" @click="openMaterial(item)">
          {{ item.name || item.code }}
        </a>
      </div>
      <div class="tra-hint">{{ materialHint }}</div>
    </a-form-item>

    <a-form-item label="申请说明">
      <a-textarea v-model:value="model.remark" :rows="3"
        placeholder="补充说明（提交后记入本节点的办理意见，便于追溯）" />
    </a-form-item>
  </a-form>

  <!--
    交付项详情 = 复用交付件面板那个「产出物关联」弹窗（2026-10-29 用户口径：
    "交付件超链接查看的是产出物关联里那些产出物的详情"）。
    复用同一个组件而不是再造一个抽屉：口径、类别约束、跨容器标注全都一致；
    传 readonly 去掉「解除 / 添加产出物」—— 写入入口仍然只有交付件管理页。
  -->
  <DeliverableArtifactModal
    v-model:open="materialVisible"
    :deliverable="materialDetail"
    :owner-oid="ownerOid"
    readonly
  />
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import {
  getTaskContext,
  listGateTemplates,
  listMethodologyInstances,
  listReviewTemplates,
  listStageTemplates,
} from '@/api'
import { fetchEntityInfo } from '@/composables/useEntityInfo'
import DeliverableArtifactModal from '@/components/DeliverableArtifactModal.vue'
import { asCodeList, variableOf } from './variables'

/**
 * 「技术评审申请单」表单 —— 技术评审（TR）流程的发起与补录（行业包 `electronics-core` 的
 * `technical-review-apply`，组件名字由包 `forms.json` 标注、运行期按名派发）。
 *
 * <h3>字段来源（与行业包 forms.json 的原 fields 声明一致）</h3>
 * <ul>
 *   <li><b>待评对象</b>：不手填 —— <b>双来源</b>（2026-10-14 起本表也作为 start 节点的表单）：
 *       办理节点读流程关联的 PBS（{@code getTaskContext(taskId)}），
 *       发起时读发起弹框里选中的对象（{@code form.entities}），
 *       两者都经 {@code fetchEntityInfo} 回查显示名（与办理页页头同一口径）；</li>
 *   <li><b>评审模板</b>：下拉取自本租户评审模板（{@code /review-templates}），值 = code ——
 *       下游「技术评审检查表」节点按它拉检查项清单（经流程变量传递）；</li>
 *   <li><b>所处阶段 / 关联阶段门</b>：分别取阶段模板与阶段门模板（值 = code）；</li>
 *   <li><b>三者的默认值</b>（2026-10-26 用户要求「这三个选项没有默认带过来」）：从阶段门 / 评审行
 *       发起时，宿主管线把行上的门码 / 评审码 / 阶段算成 {@code form.presets} 传进来，本表按 code 预填
 *       —— 只填空字段、且值必须在下拉里（见 {@link applyPresets}）；</li>
 *   <li><b>送审材料</b>（2026-10-26 用户要求「应该是添加交付件中的交付项」）：<b>不手填</b> ——
 *       多选本归属单元（产品系列 / 型号）「交付件」面板里的交付项（{@code GET /api/instances} 的
 *       {@code deliverables}，剔除标记「本项目不适用」的），默认再按所选阶段收窄；值 = 交付件 code；</li>
 * </ul>
 *
 * <h3>办理节点回填</h3>
 * <p>本表同时挂在开始节点与「材料准备与确认」上（同一个 {@code formRef}）。发起时填的内容已作为
 * {@code ckplmTechReview_*} 流程变量存下，办理节点据此回填（见 {@link restoreSelects} /
 * {@link restoreMaterials}）—— 否则同一张表换个节点就变成空白，等于让申请人重填一遍。
 *
 * <h3>提交口径</h3>
 * <p>与其它模板一致：{@code collect() → { ok, payload?, message? }}，payload 走渲染器统一的
 * {@code completeTask}（{@code { action, comment, variables }}）。本表挂 START / TASK（填报类），
 * action 固定 {@code approve}；变量统一 {@code ckplmTechReview_} 前缀。
 */
const props = defineProps({
  /** 任务 id —— 取任务上下文（关联业务对象）用 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const model = ref({
  reviewTemplate: undefined,
  stage: undefined,
  gate: undefined,
  /** 送审材料：交付件 code 数组（提交时拼成逗号串，见 collect） */
  materials: [],
  remark: '',
})

// ==================== 只读：待评对象 ====================

const loadingEntities = ref(true)
const entities = ref([])

/**
 * 待评对象的**两种来源**（2026-10-14 用户要求：本表也可作为 start 节点的表单）。
 *
 * <ol>
 *   <li><b>发起流程时 —— 发起弹框里选中的对象</b>：发起时还没有任务实例，
 *       {@code getTaskContext(taskId)} 无从谈起；但"评的是谁"此刻已经定了
 *       （用户就是从它的行操作 / 系列页的门行点「发起」进来的），由发起页
 *       通过表单上下文 {@code form.entities} 带进来（形状同 {@code startEntityPayload}：
 *       {@code entityOid / typeCode / entityCode}）—— 与提交给后端的实体清单是**同一份选择**；</li>
 *   <li><b>办理节点 —— 流程关联的 PBS（{@code ck_process_entity_set}）</b>：
 *       仍走 {@code getTaskContext(taskId)}，以**实例实际关联**为准；</li>
 * </ol>
 *
 * <p>拿 ref 之后的一切（编码 / 名称 / 状态回查）两条来源完全共用 —— 它们只吃 {@code entityOid}。
 */
async function entityRefs() {
  if (props.taskId) {
    const res = await getTaskContext(props.taskId)
    return res?.code === 200 ? (res.data?.entities || []) : []
  }
  return (props.form?.entities || []).map((e) => ({
    entityOid: e.entityOid,
    entityCode: e.entityCode,
    typeCode: e.typeCode,
  }))
}

async function loadEntities() {
  loadingEntities.value = true
  try {
    const refs = await entityRefs()
    entities.value = refs.map((ref) => ({ ref, info: null }))
    await Promise.all(entities.value.map(async (row) => {
      row.info = await fetchEntityInfo(row.ref)
    }))
  } catch {
    entities.value = []
  } finally {
    loadingEntities.value = false
  }
  // 送审材料的候选 = 这个归属单元（系列 / 型号）自己的交付件 —— 先等对象定下来再去取
  await loadDeliverables()
}

// ==================== 送审材料：本系列的交付项 ====================

const loadingMaterials = ref(false)
/** 本归属单元的全部交付件（已剔除「本项目不适用」的裁剪项） */
const deliverables = ref([])

/**
 * 取候选交付项。
 *
 * <p>归属单元 = 主业务对象（清单第一条）：门 / 评审发起时它就是当前产品系列，办理时是流程关联的 PBS
 * —— 与交付件面板 {@code GET /api/instances} 同一数据源，所以这里看到的条目跟那个面板一模一样。
 * 取不到（流程没关联对象 / 没装方法论包）就空着，不挡提交。
 */
async function loadDeliverables() {
  const owner = ownerOid.value
  if (!owner) {
    deliverables.value = []
    return
  }
  loadingMaterials.value = true
  try {
    const res = await listMethodologyInstances(owner)
    const rows = res?.code === 200 ? (res.data?.deliverables || []) : []
    deliverables.value = rows.filter((row) => row && row.code && !row.disabled)
  } catch {
    deliverables.value = []
  } finally {
    loadingMaterials.value = false
  }
}

/**
 * 候选范围：优先本阶段的交付项（评审评的就是本阶段该交的东西），
 * 本阶段一条都没有时退回全部 —— 宁可多给几条让人挑，也不给一个永远空着的下拉。
 */
const materialCandidates = computed(() => {
  const stage = model.value.stage
  const rows = deliverables.value
  if (!stage) {
    return rows
  }
  const inStage = rows.filter((row) => row.stageKey === stage)
  return inStage.length ? inStage : rows
})

const materialOptions = computed(() => materialCandidates.value.map((row) => ({
  value: row.code,
  label: `${row.name || row.code}（${row.code}）`,
})))

const materialPlaceholder = computed(() => (
  deliverables.value.length
    ? '从本系列的交付件里选送审材料（可多选）'
    : '没有可选的交付件（该流程未关联产品系列，或本项目未装方法论包）'
))

const materialEmptyText = computed(() => (
  deliverables.value.length ? '本阶段暂无匹配的交付件' : '没有可选的交付件'
))

/** 一句话说清候选从哪来、按什么过滤 —— 空下拉时最需要这句 */
const materialHint = computed(() => {
  if (loadingMaterials.value) {
    return '正在读取本系列的交付件…'
  }
  if (!deliverables.value.length) {
    return '候选取自交付件面板；取不到时该字段留空，不影响提交。'
  }
  const stageLabel = stageOptions.value.find((opt) => opt.value === model.value.stage)?.label
  const scope = model.value.stage && materialCandidates.value.length !== deliverables.value.length
    ? `「${stageLabel || model.value.stage}」阶段`
    : '本系列'
  return `${scope}共 ${materialCandidates.value.length} 项交付件可选（标记「本项目不适用」的不列出）。`
})

// ==================== 交付项详情（2026-10-29） ====================

const materialVisible = ref(false)
const materialDetail = ref(null)

/** 当前选中、且能在候选里找到的交付项（找不到的说明已被裁剪/换阶段，不回显成裸 code） */
const selectedMaterials = computed(() => (model.value.materials || [])
  .map((code) => deliverables.value.find((row) => row.code === code))
  .filter(Boolean))

/** 归属单元（本系列 / 型号）的 oid：产出物弹窗用它标注「本型号 / 其它容器」 */
const ownerOid = computed(() => entities.value[0]?.ref?.entityOid || '')

/** 点名称 → 打开「产出物关联」（只读）：看的正是交付件面板里那些产出物 */
function openMaterial(row) {
  if (!row) return
  materialDetail.value = row
  materialVisible.value = true
}

/**
 * 换了阶段就把"已经不在候选里"的选择去掉 ——
 * 否则下拉里只剩一串裸 code，谁也看不出它是什么。
 */
watch(() => model.value.stage, () => {
  if (!model.value.materials.length) {
    return
  }
  const allowed = new Set(materialOptions.value.map((opt) => opt.value))
  model.value.materials = model.value.materials.filter((code) => allowed.has(code))
})

// ==================== 下拉数据：评审模板 / 阶段 / 阶段门 ====================

const loadingMeta = ref(false)
const reviewOptions = ref([])
const stageOptions = ref([])
const gateOptions = ref([])

/** 模板行 → 下拉项：值一律用 code（下游按 code 关联），显示名带 code 便于核对 */
function toOptions(rows, codeField = 'code') {
  return (rows || [])
    .filter((row) => row && (row[codeField] || row.code))
    .map((row) => {
      const code = row[codeField] || row.code
      const label = row.name || row.displayName || code
      return { value: code, label: `${label}（${code}）` }
    })
}

async function loadMeta() {
  loadingMeta.value = true
  try {
    const [reviews, stages, gates] = await Promise.all([
      listReviewTemplates().catch(() => null),
      listStageTemplates().catch(() => null),
      listGateTemplates().catch(() => null),
    ])
    reviewOptions.value = toOptions(reviews?.code === 200 ? reviews.data : [])
    stageOptions.value = toOptions(stages?.code === 200 ? stages.data : [])
    gateOptions.value = toOptions(gates?.code === 200 ? gates.data : [])
  } finally {
    loadingMeta.value = false
  }
}

/** 值必须在当前下拉里：行上的 code 可能被裁剪或不属于本租户，填进去只会显示裸 code */
function pickInOptions(options, code) {
  return code && options.some((opt) => opt.value === code) ? code : undefined
}

/**
 * 阶段门 / 评审行发起时带来的预设值（{@code form.presets}，值一律是 code）：
 * 评审模板 / 所处阶段 / 关联阶段门 按行上的事实预填（口径见 `ProductLineDashboard#presetFromGateRow`）。
 *
 * <p>三条约束：<b>只填空字段</b>（用户选过的、草稿恢复的都不覆盖）、
 * <b>值必须在下拉里</b>（否则宁可不填）、<b>等下拉加载完再填</b>。
 */
function applyPresets() {
  const presets = props.form?.presets
  if (!presets) {
    return
  }
  if (!model.value.reviewTemplate) {
    model.value.reviewTemplate = pickInOptions(reviewOptions.value, presets.reviewTemplate)
  }
  if (!model.value.stage) {
    model.value.stage = pickInOptions(stageOptions.value, presets.stage)
  }
  if (!model.value.gate) {
    model.value.gate = pickInOptions(gateOptions.value, presets.gate)
  }
}

// ==================== 办理节点回填（与发起共用同一张表） ====================

/**
 * 把**发起时**填过的三项（评审模板 / 所处阶段 / 关联阶段门）带进同模板的办理节点。
 *
 * <p>为什么必须回填：本表既挂开始节点（发起即填申请），也挂「材料准备与确认」——
 * 变量 {@code ckplmTechReview_*} 在发起那一刻就写进去了（见 {@link collect}），
 * 不回填就表现为"发起时填过，办到这一步却是空表，得重填一遍"
 * （2026-10-26 用户反馈「发起时已经填过了，没有带过来，且流程节点模板是同一个」）。
 *
 * <p>三条约束（与 {@link applyPresets} 同一套）：只跑**有任务**的场景（发起侧没有"之前"）、
 * **只填空字段**（用户在本页改过的、草稿恢复的都不覆盖）、**值必须还在下拉里**。
 */
function restoreSelects() {
  const vars = props.form?.variables
  if (!props.taskId || !vars) {
    return
  }
  if (!model.value.reviewTemplate) {
    model.value.reviewTemplate = pickInOptions(
      reviewOptions.value, variableOf(vars, 'ckplmTechReview_reviewTemplate'))
  }
  if (!model.value.stage) {
    model.value.stage = pickInOptions(stageOptions.value, variableOf(vars, 'ckplmTechReview_stage'))
  }
  if (!model.value.gate) {
    model.value.gate = pickInOptions(gateOptions.value, variableOf(vars, 'ckplmTechReview_gate'))
  }
  // 申请说明：发起侧只传 variables，collect() 里的 comment 在发起时会被丢掉 ——
  // 不单独落一个变量，办理节点就永远回填不出来（与阶段门决策申请单同一处缺陷，2026-10-30 一并修）
  if (!model.value.remark) {
    const remark = variableOf(vars, 'ckplmTechReview_remark')
    if (remark) model.value.remark = String(remark)
  }
}

/**
 * 送审材料回填：变量里是**逗号串**（见 {@link collect}）。
 *
 * <p>只保留**本归属单元还存在的**交付件 code —— 已删除 / 已标「本项目不适用」的项填回来
 * 只会是一串没有名字的裸 code。候选一条都取不到时（如未装方法论包）原样保留：
 * 宁可显示裸 code，也不静默丢掉用户填过的内容。
 */
function restoreMaterials() {
  const vars = props.form?.variables
  if (!props.taskId || !vars || model.value.materials.length) {
    return
  }
  const codes = asCodeList(variableOf(vars, 'ckplmTechReview_materialRefs'))
  if (!codes.length) {
    return
  }
  const known = new Set(deliverables.value.map((row) => row.code))
  model.value.materials = known.size ? codes.filter((code) => known.has(code)) : codes
}

onMounted(async () => {
  // 下拉（评审模板 / 阶段 / 门）与交付件（送审材料候选）都就绪后才回填 ——
  // 先填后到的话，选中的 code 找不到对应项，界面上只剩一串裸 code
  await Promise.all([loadEntities(), loadMeta()])
  // 顺序：先按发起时填过的变量回填，再用行上的预设补空 —— 预设只填空字段，
  // 反过来的话回填会被预设挡住，"用户填过的"就丢了
  restoreSelects()
  applyPresets()
  // 让「换阶段 → 收窄材料候选」的 watcher 先跑（此刻材料还是空的），再回填材料：
  // 否则刚填上的材料会被它当成"上一阶段的残留"清掉（发起时选的材料未必属于所选阶段）
  await nextTick()
  restoreMaterials()
})

/**
 * 发起场景：回到上一步改了业务对象清单、再回到本步时，待评对象要跟着变
 * （表单是 v-show 保持挂载的，不会重新 onMounted）。办理场景 taskId 不变，不触发。
 */
watch(
  () => props.form?.entities,
  () => {
    if (!props.taskId) {
      loadEntities()
    }
  },
  { deep: true },
)

/** 预设值在表单挂载之后才到（或改了业务对象换了门）时，补填一次 */
watch(() => props.form?.presets, () => applyPresets(), { deep: true })

// ==================== 提交 ====================

const submitText = computed(() => '提交申请')
const successText = computed(() => '申请已提交，流程继续流转')
/** 只依赖自身字段与任务上下文，任何情况下都可渲染（下拉取不到也不挡提交） */
const renderable = computed(() => true)

function collect() {
  if (!model.value.reviewTemplate) {
    return { ok: false, message: '请选择评审模板' }
  }
  if (!model.value.stage) {
    return { ok: false, message: '请选择所处阶段' }
  }
  return {
    ok: true,
    payload: {
      action: 'approve',
      comment: model.value.remark.trim() || '提交技术评审申请',
      variables: {
        ckplmTechReview_reviewTemplate: model.value.reviewTemplate,
        ckplmTechReview_stage: model.value.stage,
        ckplmTechReview_gate: model.value.gate || null,
        // 送审材料记交付件 code（逗号分隔）：与交付件面板同一口径，后续可据此回查交付项
        ckplmTechReview_materialRefs: model.value.materials.length
          ? model.value.materials.join(',')
          : null,
        // 待评对象一并留档（流程已绑实体，但评审单里显式记一份便于追溯，多个用逗号分隔）
        ckplmTechReview_targetObjects: entities.value
          .map((row) => row.info?.code || row.ref.entityOid)
          .join(','),
        // 申请说明必须自己落一个变量：发起（开始节点）只提交 variables，payload.comment 到不了后端
        ckplmTechReview_remark: model.value.remark.trim() || null,
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.tra-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.tra-empty {
  margin: 4px 0 18px;
}

/* 字段下方的口径说明：小一号、灰一点，别和正文抢注意力 */
.tra-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}

/* 已选送审材料：逐条可点，点开是交付项详情抽屉 */
.tra-materials {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
  margin-top: 6px;
  font-size: 12px;
}
.tra-materials__label { color: #8c8c8c; }
.tra-material-link {
  color: #1677ff;
  cursor: pointer;
  border-bottom: 1px dashed transparent;
}
.tra-material-link:hover { border-bottom-color: #1677ff; }

.tra-object {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.tra-object__code {
  font-weight: 600;
  color: #1677ff;
}

.tra-object__name {
  color: #333;
}

.tra-object__type {
  margin-left: auto;
  font-size: 12px;
  color: #bfbfbf;
}
</style>
