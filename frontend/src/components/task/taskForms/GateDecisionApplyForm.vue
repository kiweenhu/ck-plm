<template>
  <a-form layout="vertical">
    <div class="gda-section">阶段门与自检</div>

    <a-form-item label="阶段门" required>
      <a-select
        v-model:value="model.gate"
        :options="gateOptions"
        :loading="loadingMeta"
        show-search
        option-filter-prop="label"
        :placeholder="gatePlaceholder"
        allow-clear
      />
      <div v-if="gatePresetHint" class="gda-auto gda-auto--plain">{{ gatePresetHint }}</div>
    </a-form-item>

    <a-row :gutter="12">
      <a-col :span="12">
        <a-form-item label="交付物完成率（%）" required>
          <a-input-number
            v-model:value="model.deliverableRate"
            :min="0" :max="100" :precision="0"
            style="width: 100%"
            placeholder="0 - 100"
            :disabled="rateAutoFilled"
          />
          <div v-if="rateAutoFilled" class="gda-auto">{{ rateSummary }} —— 自动统计自交付件清单</div>
          <div v-else-if="form?.businessOid" class="gda-auto gda-auto--warn">
            请选择阶段门后自动带出（未选门前请如实填写）
          </div>
        </a-form-item>
      </a-col>
      <a-col :span="12">
        <a-form-item label="未关闭阻断问题数" required>
          <a-input-number
            v-model:value="model.openBlockIssues"
            :min="0" :precision="0"
            style="width: 100%"
            :disabled="issuesAutoFilled"
          />
          <div v-if="issuesAutoFilled" class="gda-auto">
            {{ issuesSummary }} —— 自动统计自「持续改进」，无需手填
          </div>
          <div v-else-if="form?.businessOid" class="gda-auto gda-auto--warn">
            请选择阶段门后自动带出（未选门前请如实填写）
          </div>
        </a-form-item>
      </a-col>
    </a-row>
    <div class="gda-hint">
      这两项都按<b>所选这道门</b>现算：「未关闭阻断问题数」= 挂在这道门上、状态未闭环（OPEN / IN_PROGRESS /
      VERIFYING）且级别为阻塞的问题数；「交付物完成率」= 本门所属阶段的交付件完成率（与门面板「自动检查」列
      同一份数据）。登记时没选阶段门的问题不算到任何门上。
    </div>

    <!--
      门专项确认（2026-10-29 用户口径：只在对应的决策门中出现）：
      开关跟门的 auto_checks 走 —— 声明了 CERT_COMPLETE 才出现「认证齐套」，
      声明了 SUPPLY_CONFIRMED 才出现「供应已确认」。没声明的门不拿一个永远"未确认"的开关糊人，
      也不把这两项写成所有门都得勾的通用项。
    -->
    <template v-if="showCert || showSupply">
      <div class="gda-section">门专项确认</div>

      <a-form-item v-if="showCert" label="认证齐套">
        <a-switch v-model:checked="model.certComplete" checked-children="已齐套" un-checked-children="未齐套" />
        <span class="gda-note">本门声明了 CERT_COMPLETE 检查</span>
      </a-form-item>

      <a-form-item v-if="showSupply" label="供应已确认">
        <a-switch v-model:checked="model.supplyConfirmed" checked-children="已确认" un-checked-children="未确认" />
        <span class="gda-note">本门声明了 SUPPLY_CONFIRMED 检查</span>
      </a-form-item>
    </template>

    <!--
      决策材料 = 从本系列的「交付件」里勾交付项（2026-10-29 用户口径："与技术评审一样，选择交付件"）。
      原来是自由文本：手写名称 / 编码既容易打错，也跟交付件面板对不上号；
      决策要看的"材料"就是本阶段该交的那些交付项，直接选它。值 = 交付件 code。
    -->
    <a-form-item label="决策材料">
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
      <!-- 已选材料逐条列出并**可点开详情**：与评审申请单同一手法（下拉只是名字，材料要看得见是什么） -->
      <div v-if="selectedMaterials.length" class="gda-materials">
        <span class="gda-materials__label">已选 {{ selectedMaterials.length }} 项，点名称看交付项详情：</span>
        <a v-for="item in selectedMaterials" :key="item.code" class="gda-material-link" @click="openMaterial(item)">
          {{ item.name || item.code }}
        </a>
      </div>
      <div class="gda-hint">{{ materialHint }}</div>
    </a-form-item>

    <a-form-item label="申请说明">
      <a-textarea v-model:value="model.remark" :rows="3"
        placeholder="补充说明（提交后记入本节点的办理意见，便于追溯）" />
    </a-form-item>
  </a-form>

  <!--
    交付项详情 = 复用交付件面板那个「产出物关联」弹窗（只读）：
    与技术评审申请单的「送审材料」看的是同一份东西，写入入口仍只有交付件管理页。
  -->
  <DeliverableArtifactModal
    v-model:open="materialVisible"
    :deliverable="materialDetail"
    :owner-oid="ownerOid"
    readonly
  />
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import {
  getIssueGateCheck,
  listGateTemplates,
  listMethodologyInstances,
} from '@/api'
import DeliverableArtifactModal from '@/components/DeliverableArtifactModal.vue'
import { asCodeList, variableOf } from './variables'

/**
 * 「阶段门决策申请单」表单 —— 阶段门决策（DCP）流程的发起与补录（行业包 `electronics-core` 的
 * `gate-decision-apply`，组件名字由包 `forms.json` 标注、运行期按名派发）。
 *
 * <h3>字段来源（与行业包 forms.json 的原 fields 声明一致）</h3>
 * <ul>
 *   <li><b>阶段门</b>：下拉取<b>本归属单元的门实例</b>（{@code GET /api/instances} 的 {@code gates}，
 *       与门面板同一份，只列决策门 {@code DECISION_REVIEW}）；实例取不到才退回本租户阶段门模板
 *       （{@code /gate-templates}）。值 = code；<b>默认值</b>（2026-10-29 用户反馈"阶段门没有自动选择"）：
 *       从门的行点「发起」时，宿主管线把门码算成 {@code form.presets.gate} 传进来
 *       （口径见 `ProductLineDashboard#presetFromGateRow`），本表按 code 预填 —— 见 {@link applyPresets}；</li>
 *   <li><b>未关闭阻断问题数</b>：<b>自动带出</b> —— 按上下文里的业务对象（{@code form.businessOid}）
 *       现算自 {@code /api/issues/gate-check}，取<b>所选这道门</b>的 ISSUE_OPEN 检查（2026-10-29 起
 *       门级口径：只数挂在该门上的问题，与门面板、发起前提示同一份），字段随之只读；</li>
 *   <li><b>交付物完成率</b>：同样自动带出 —— {@code gate-check} 的 {@code stageRates} 里按
 *       <b>所选门的阶段</b>取（对应 DELIVERABLE_RATE）；没选门 / 该阶段无数据时保持人工填写；</li>
 *   <li><b>认证齐套 / 供应已确认</b>：布尔开关，<b>只在门的 auto_checks 声明了</b>
 *       CERT_COMPLETE / SUPPLY_CONFIRMED 时才出现（ADCP 两项都有，LDCP 只有认证齐套，
 *       CHARTER / PDCP 两项都没有）—— 专项确认不是通用项；</li>
 *   <li><b>决策材料</b>：<b>不手填</b> —— 多选本归属单元「交付件」面板里的交付项
 *       （{@code GET /api/instances} 的 {@code deliverables}，剔除标记「本项目不适用」的），
 *       默认按所选门的阶段收窄；值 = 交付件 code（与技术评审申请单的送审材料同一口径）。</li>
 * </ul>
 *
 * <h3>办理节点回填</h3>
 * <p>本表既挂开始节点（发起即填申请）也挂办理节点。发起时填的内容已作为
 * {@code ckplmGateDecision_*} 流程变量存下，办理节点据此回填（见 {@link restoreFromVariables}）——
 * 否则同一张表换个节点就变成空白，等于让申请人把同一份申请重填一遍。
 *
 * <h3>提交口径</h3>
 * <p>{@code collect() → { ok, payload?, message? }}，payload 走渲染器统一的 {@code completeTask}。
 * 本表挂 START / TASK（填报类），action 固定 {@code approve}；变量统一 {@code ckplmGateDecision_} 前缀。
 */
const props = defineProps({
  /** 任务 id（本表不依赖关联对象，保留以满足统一契约） */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO） */
  form: { type: Object, default: null },
})

const model = ref({
  gate: undefined,
  deliverableRate: null,
  openBlockIssues: null,
  certComplete: false,
  supplyConfirmed: false,
  /** 决策材料：交付件 code 数组（提交时拼成逗号串，见 collect） */
  materials: [],
  remark: '',
})

const loadingMeta = ref(false)
/** 下拉项：value=code，另带 stageKey（完成率要按阶段取）与 checks（专项确认按声明显隐） */
const gateOptions = ref([])

const selectedGate = computed(() => gateOptions.value.find((opt) => opt.value === model.value.gate) || null)

// ==================== 两项自动检查的自动带出 ====================

/** 「未关闭阻断问题数」是否成功自动带出（成功 → 字段只读，人工改不了权威值） */
const issuesAutoFilled = ref(false)
/** 未闭环问题的补充说明（门级：本门有几个、其中几个阻塞） */
const issuesSummary = ref('')

/** 「交付物完成率」是否成功自动带出 */
const rateAutoFilled = ref(false)
/** 完成率的补充说明 */
const rateSummary = ref('')

/** gate-check 的整份结果：stageRates（按阶段完成率）+ gates（逐门检查） */
const gateCheck = ref(null)

/**
 * 门模板的 auto_checks 是 JSON 字符串（库里 TEXT），也可能是已解析的数组 —— 两种都认，
 * 解析失败不抛错（拿不到就当没声明，界面上少一个开关而已）。
 */
function checkTypesOf(raw) {
  let list = raw
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw || '[]')
      list = Array.isArray(parsed) ? parsed : []
    } catch {
      list = []
    }
  }
  if (!Array.isArray(list)) return []
  return list.map((item) => item && item.type).filter(Boolean)
}

/** 本门是否声明了某类专项检查（开关只在该出现的时候出现） */
function declaresCheck(type) {
  const checks = selectedGate.value?.checks || []
  return checks.includes(type)
}

const showCert = computed(() => declaresCheck('CERT_COMPLETE'))
const showSupply = computed(() => declaresCheck('SUPPLY_CONFIRMED'))

/**
 * 按上下文里的业务对象（产品系列 / 型号）现算检查结果 —— 数据源 = {@code /api/issues/gate-check}，
 * 与门面板「自动检查」列、发起前提示是**同一份**。
 *
 * <p>业务对象从哪来：办理时 = {@code ProcessTaskFormVO.businessOid}（后端按 ck_process_entity_set 填）；
 * 发起时 = StartProcessModal 的 startFormPayload（entities 第一条）。都没有（老实例 / 无关联）
 * → 保持人工填写，并在字段下明说"未能自动带出"，不假装有值。
 */
async function loadGateCheck() {
  const ownerOid = props.form?.businessOid
  if (!ownerOid) return
  try {
    const res = await getIssueGateCheck(ownerOid)
    if (res?.code === 200 && res.data) {
      gateCheck.value = res.data
      applyGateDerived()
    }
  } catch { /* 拉不到 = 退化为人工填写（字段下方有提示） */ }
}

/**
 * 按所选门同时刷新两项自动值。
 *
 * <p>顺序无关：先设的会被后到的覆盖 —— 用户换门、`gate-check` 后到、变量回填都走这一个出口，
 * 不会出现"完成率是 A 门的、问题数是 B 门的"。
 */
function applyGateDerived() {
  applyStageRate()
  applyGateIssues()
}

/**
 * 按所选门的**所属阶段**取该阶段交付件完成率（DELIVERABLE_RATE 同口径）。
 * 没选门 / 门没带阶段 / 该阶段无数据 → 保持人工填写，不猜；原本是自动值则清空（别留一个看似权威的旧数）。
 */
function applyStageRate() {
  const wasAuto = rateAutoFilled.value
  rateAutoFilled.value = false
  const stage = selectedGate.value?.stageKey
  const rates = gateCheck.value?.stageRates || []
  const row = stage ? rates.find((r) => r.stageKey === stage) : null
  if (!row) {
    if (wasAuto) model.value.deliverableRate = null
    return
  }
  model.value.deliverableRate = row.percent
  rateSummary.value = `本阶段交付件 ${row.doneCount}/${row.totalCount} 已完成（${row.percent}%）`
  rateAutoFilled.value = true
}

/**
 * 取**所选这道门**的 ISSUE_OPEN 检查（2026-10-29 门级口径）。
 *
 * <p>值 = 挂在该门上的阻塞问题数（字段名就是"未关闭阻断问题数"）；补充说明里同时给出
 * 该门的未闭环总数与提示数 —— 只报一个数字会让人以为 WARN 不算问题。
 * 该门没声明 ISSUE_OPEN（或还没算出来）→ 不填，保持人工。
 */
function applyGateIssues() {
  const wasAuto = issuesAutoFilled.value
  issuesAutoFilled.value = false
  issuesSummary.value = ''
  const code = model.value.gate
  if (!code) {
    if (wasAuto) model.value.openBlockIssues = null
    return
  }
  const gates = gateCheck.value?.gates || []
  const check = (gates.find((g) => g.gateCode === code)?.checks || [])
    .find((c) => c.type === 'ISSUE_OPEN')
  if (!check) {
    if (wasAuto) model.value.openBlockIssues = null
    return
  }
  model.value.openBlockIssues = Number(check.blockCount ?? 0)
  issuesSummary.value = `本门未闭环 ${check.actual ?? 0} 个（阻塞 ${check.blockCount ?? 0} / 提示 ${check.warnCount ?? 0}），要求 ≤ ${check.maxCount ?? 0}`
  issuesAutoFilled.value = true
}

// ==================== 决策材料：本系列的交付项 ====================

const loadingMaterials = ref(false)
/** 本归属单元的全部交付件（已剔除「本项目不适用」的裁剪项） */
const deliverables = ref([])
/** 本归属单元的门实例（{@code /instances} 的 gates）—— 下拉与专项确认优先用它 */
const instanceGates = ref([])

/**
 * 取本归属单元的实例数据（交付件 + 门）：归属单元 = 上下文里的业务对象（系列 / 型号），
 * 与交付件面板、门面板 {@code GET /api/instances} 同一数据源，所以这里看到的条目跟那些面板一模一样。
 * 取不到（没关联对象 / 没装方法论包）就空着，不挡提交。
 */
async function loadOwnerData() {
  const owner = props.form?.businessOid
  if (!owner) {
    deliverables.value = []
    instanceGates.value = []
    return
  }
  loadingMaterials.value = true
  try {
    const res = await listMethodologyInstances(owner)
    const data = res?.code === 200 ? res.data : null
    deliverables.value = (data?.deliverables || []).filter((row) => row && row.code && !row.disabled)
    instanceGates.value = (data?.gates || []).filter((row) => row && row.code)
  } catch {
    deliverables.value = []
    instanceGates.value = []
  } finally {
    loadingMaterials.value = false
  }
}

/**
 * 候选范围：优先所选门**本阶段**的交付项（决策看的就是本阶段该交的东西），
 * 本阶段一条都没有时退回全部 —— 宁可多给几条让人挑，也不给一个永远空着的下拉。
 */
const materialCandidates = computed(() => {
  const rows = deliverables.value
  const stage = selectedGate.value?.stageKey
  if (!stage) return rows
  const inStage = rows.filter((row) => row.stageKey === stage)
  return inStage.length ? inStage : rows
})

const materialOptions = computed(() => materialCandidates.value.map((row) => ({
  value: row.code,
  label: `${row.name || row.code}（${row.code}）`,
})))

const materialPlaceholder = computed(() => (
  deliverables.value.length
    ? '从本系列的交付件里选决策材料（可多选）'
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
  const scope = selectedGate.value?.stageKey && materialCandidates.value.length !== deliverables.value.length
    ? `「${selectedGate.value.label}」所属阶段`
    : '本系列'
  return `${scope}共 ${materialCandidates.value.length} 项交付件可选（标记「本项目不适用」的不列出）。`
})

/** 当前选中、且能在候选里找到的交付项（找不到的说明已被裁剪/换阶段，不回显成裸 code） */
const selectedMaterials = computed(() => (model.value.materials || [])
  .map((code) => deliverables.value.find((row) => row.code === code))
  .filter(Boolean))

/** 归属单元的 oid（产出物弹窗用它标注「本型号 / 其它容器」） */
const ownerOid = computed(() => props.form?.businessOid || '')

/** 点名称 → 打开「产出物关联」（只读）：看的正是交付件面板里那些产出物 */
const materialVisible = ref(false)
const materialDetail = ref(null)
function openMaterial(row) {
  if (!row) return
  materialDetail.value = row
  materialVisible.value = true
}

/**
 * 换了门就换了阶段：把"已经不在候选里"的材料去掉 ——
 * 否则下拉里只剩一串裸 code，谁也看不出它是什么。
 */
watch(() => model.value.gate, () => {
  if (!model.value.materials.length) return
  const allowed = new Set(materialOptions.value.map((opt) => opt.value))
  model.value.materials = model.value.materials.filter((code) => allowed.has(code))
})

/** 门一变，两项自动值立刻跟着换（用户手选、变量回填、行上预设都走这里） */
watch(() => model.value.gate, () => applyGateDerived())

// ==================== 下拉数据：阶段门（实例优先，模板兜底） ====================

const gatePlaceholder = computed(() => (
  gateOptions.value.length
    ? '选择阶段门（取值 = 行业包 decision-gates.json 的 code）'
    : '正在读取本系列的阶段门…'
))

async function loadMeta() {
  loadingMeta.value = true
  try {
    // 本表是 DCP 申请单：只认决策门（DECISION_REVIEW），技术评审有自己的申请单。
    // **门实例优先** —— 门面板判定用的是实例上的 auto_checks，申请单看同一份才不会
    // "表单要求认证齐套、门面板却没这项检查"；实例取不到（没装方法论包）才退回模板。
    let rows = instanceGates.value.filter((row) => row.type === 'DECISION_REVIEW')
    if (!rows.length) {
      const res = await listGateTemplates().catch(() => null)
      const all = (res?.code === 200 ? (res.data || []) : []).filter((row) => row?.code)
      const decisions = all.filter((row) => row.type === 'DECISION_REVIEW')
      // 一门决策门都没有的老租户退回全部 —— 下拉不能因为口径升级而永远是空的
      rows = decisions.length ? decisions : all
    }
    gateOptions.value = rows.map((row) => ({
      value: row.code,
      label: `${row.name || row.code}（${row.code}）`,
      stageKey: row.stageKey,
      checks: checkTypesOf(row.autoChecks),
    }))
  } finally {
    loadingMeta.value = false
  }
}

// ==================== 默认值 / 办理节点回填 ====================

/**
 * 阶段门行发起时带来的预设值（{@code form.presets.gate}，值 = 门码）：
 * "是哪道门"在行上就已经定了，不该让人在表单里再从一长串 code 里挑一遍。
 *
 * <p>三条约束（与技术评审申请单同一套）：<b>只填空字段</b>、<b>值必须在下拉里</b>
 * （裁剪 / 停用后填进去只会显示裸 code）、<b>等下拉加载完再填</b>。
 */
function applyPresets() {
  const presetGate = props.form?.presets?.gate
  if (!presetGate || model.value.gate) return
  if (gateOptions.value.some((opt) => opt.value === presetGate)) {
    model.value.gate = presetGate
    applyGateDerived()
  }
}

/** 预设门没填进来时的一句说明（只在该有值却没有的时候出现，避免误导） */
const gatePresetHint = ref('')

/** 数字型变量容错：Flowable 可能给 number / 数字串；拿不到或不合法 → null（保持"未填"） */
function toNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

/** 布尔型变量容错：Boolean / 'true' / '1' 都算真（老数据里存过字符串） */
function isTrue(value) {
  return value === true || value === 'true' || value === '1' || value === 1
}

/**
 * 把**发起时**填过的申请内容带进同模板的办理节点。
 *
 * <p>为什么必须回填：本表既挂开始节点（发起即填申请），也挂流程里的填报节点 ——
 * 变量 {@code ckplmGateDecision_*} 在发起那一刻就写进去了（见 {@link collect}），
 * 不回填就表现为"发起时填过，办到这一步却是空表，得重填一遍"。
 *
 * <p>三条约束：
 * <ul>
 *   <li>只跑**有任务**的场景（发起侧没有"之前"，首次发起本就该是空的）；</li>
 *   <li><b>自动带出的两项（阻断问题数 / 完成率）优先</b>：已经由现算得出权威值
 *       时不再用变量覆盖 —— 旧值只补"没算出来"的位置；</li>
 *   <li><b>只填空字段</b>，且阶段门必须还在下拉里。</li>
 * </ul>
 */
function restoreFromVariables() {
  const vars = props.form?.variables
  if (!props.taskId || !vars) {
    return
  }
  if (!model.value.gate) {
    const gate = variableOf(vars, 'ckplmGateDecision_gate')
    if (gate && gateOptions.value.some((opt) => opt.value === gate)) {
      model.value.gate = gate
      // 门定了才知道该看哪个阶段 —— 与用户手选走同一个出口
      applyGateDerived()
    }
  }
  if (autoNeedsFill()) {
    const rate = toNumber(variableOf(vars, 'ckplmGateDecision_deliverableRate'))
    if (rate !== null) model.value.deliverableRate = rate
  }
  if (!issuesAutoFilled.value && model.value.openBlockIssues === null) {
    const count = toNumber(variableOf(vars, 'ckplmGateDecision_openBlockIssues'))
    if (count !== null) model.value.openBlockIssues = count
  }
  if (isTrue(variableOf(vars, 'ckplmGateDecision_certComplete'))) {
    model.value.certComplete = true
  }
  if (isTrue(variableOf(vars, 'ckplmGateDecision_supplyConfirmed'))) {
    model.value.supplyConfirmed = true
  }
  // 申请说明：发起侧只传 variables，collect() 里的 comment 在发起时会被丢掉 ——
  // 不单独落一个变量，办理节点就永远回填不出来（2026-10-30 用户反馈
  // "申请说明没有从提交流程的节点带过来"）
  if (!model.value.remark) {
    const remark = variableOf(vars, 'ckplmGateDecision_remark')
    if (remark) model.value.remark = String(remark)
  }
}

/** 完成率有没有现算值（没有才允许用变量里的旧值补） */
function autoNeedsFill() {
  return !rateAutoFilled.value && model.value.deliverableRate === null
}

/**
 * 决策材料回填：变量里是**逗号串**（见 {@link collect}）。
 *
 * <p>只保留**本归属单元还存在的**交付件 code —— 已删除 / 已标「本项目不适用」的项填回来
 * 只会是一串没有名字的裸 code。候选一条都取不到时（如未装方法论包）原样保留：
 * 宁可显示裸 code，也不静默丢掉用户填过的内容。
 */
function restoreMaterials(vars) {
  if (model.value.materials.length) return
  const codes = asCodeList(variableOf(vars, 'ckplmGateDecision_decisionMaterial'))
  if (!codes.length) return
  const known = new Set(deliverables.value.map((row) => row.code))
  model.value.materials = known.size ? codes.filter((code) => known.has(code)) : codes
}

onMounted(async () => {
  // 自动检查（权威值）与实例数据（交付件 / 门）并行发，下拉就绪后再填 ——
  // 先填后到的话，选中的 code 找不到对应项，界面上只剩一串裸 code
  loadGateCheck()
  await loadOwnerData()
  // 门下拉要用实例上的门（含各自的 auto_checks），所以等实例回来再建
  await loadMeta()
  // 顺序：先按发起时填过的变量回填，再用行上的预设补空 —— 预设只填空字段，
  // 反过来的话回填会被预设挡住，"用户填过的"就丢了
  restoreFromVariables()
  applyPresets()
  // 让「换门 → 收窄材料候选」的 watcher 先跑（此刻材料还是空的），再回填材料：
  // 否则刚填上的材料会被它当成"上一道门的残留"清掉（发起时选的材料未必属于所选门的阶段）
  await nextTick()
  if (props.form?.variables) restoreMaterials(props.form.variables)
  // 兜一句实话：行上带了门、下拉里却没有（门被裁剪或属于别的行业包）
  const presetGate = props.form?.presets?.gate
  if (presetGate && !model.value.gate) {
    gatePresetHint.value = `行上的阶段门「${presetGate}」不在可选列表里（可能已裁剪 / 停用），请手动选择`
  }
})

/** 预设值在表单挂载之后才到（或改了业务对象换了门）时，补填一次 */
watch(() => props.form?.presets, () => applyPresets(), { deep: true })

// ==================== 提交 ====================

const submitText = computed(() => '提交申请')
const successText = computed(() => '申请已提交，流程继续流转')
/** 只依赖自身字段（下拉取不到也不挡提交） */
const renderable = computed(() => true)

function collect() {
  if (!model.value.gate) {
    return { ok: false, message: '请选择阶段门' }
  }
  if (model.value.deliverableRate === null || model.value.deliverableRate === undefined) {
    return { ok: false, message: '请填写交付物完成率' }
  }
  if (model.value.openBlockIssues === null || model.value.openBlockIssues === undefined) {
    return { ok: false, message: '请填写未关闭阻断问题数' }
  }
  return {
    ok: true,
    payload: {
      action: 'approve',
      comment: model.value.remark.trim() || '提交阶段门决策申请',
      variables: {
        ckplmGateDecision_gate: model.value.gate,
        ckplmGateDecision_deliverableRate: model.value.deliverableRate,
        ckplmGateDecision_openBlockIssues: model.value.openBlockIssues,
        // 本门没声明这类专项检查时写 null：写 false 会被读成"明确未齐套 / 未确认"
        ckplmGateDecision_certComplete: showCert.value ? model.value.certComplete === true : null,
        ckplmGateDecision_supplyConfirmed: showSupply.value ? model.value.supplyConfirmed === true : null,
        // 决策材料记交付件 code（逗号分隔）：与交付件面板同一口径，后续可据此回查交付项
        ckplmGateDecision_decisionMaterial: model.value.materials.length
          ? model.value.materials.join(',')
          : null,
        // 申请说明必须自己落一个变量：发起（开始节点）只提交 variables，payload.comment 到不了后端，
        // 于是办理节点的这张表永远回填空 —— 见 {@link restoreFromVariables}
        ckplmGateDecision_remark: model.value.remark.trim() || null,
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.gda-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.gda-hint {
  margin: -4px 0 16px;
  font-size: 12px;
  color: #999;
  line-height: 1.7;
}

.gda-auto {
  margin-top: 4px;
  font-size: 12px;
  color: #52c41a;
}

.gda-auto--warn { color: #faad14; }

/* 中性说明（如"门不在可选列表里"）：不该用成功色，也不该用警告色喊人 */
.gda-auto--plain { color: #8c8c8c; }

.gda-note {
  margin-left: 10px;
  font-size: 12px;
  color: #999;
}

/* 已选决策材料：逐条可点，点开是交付项详情弹窗 */
.gda-materials {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
  margin-top: 6px;
  font-size: 12px;
}
.gda-materials__label { color: #8c8c8c; }
.gda-material-link {
  color: #1677ff;
  cursor: pointer;
  border-bottom: 1px dashed transparent;
}
.gda-material-link:hover { border-bottom-color: #1677ff; }
</style>
