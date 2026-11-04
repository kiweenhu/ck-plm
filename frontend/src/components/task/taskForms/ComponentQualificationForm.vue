<template>
  <a-form layout="vertical">
    <!-- ① 只读：流程绑定的器件 + 其 MPN —— 数据来自任务上下文（业务对象），不是手填 -->
    <div class="cqf-section">关联器件与 MPN</div>
    <a-spin :spinning="loading">
      <a-empty
        v-if="!loading && !entities.length"
        :description="emptyHint"
        class="cqf-empty"
      />
      <div v-for="row in entities" :key="row.ref.entityOid" class="cqf-device">
        <div class="cqf-device__head">
          <span class="cqf-device__code">{{ row.info?.code || row.ref.entityOid }}</span>
          <span class="cqf-device__name">{{ row.info?.name || '—' }}</span>
          <a-tag v-if="row.ref.entityVersion" color="blue">版本 {{ row.ref.entityVersion }}</a-tag>
          <a-tag v-if="row.info?.statusName" color="green">{{ row.info.statusName }}</a-tag>
          <span class="cqf-device__type">{{ row.ref.typeCode }}</span>
        </div>

        <template v-if="row.isPart">
          <a-table
            v-if="row.mpn && row.mpn.tree.length"
            :columns="MPN_COLUMNS"
            :data-source="row.mpn.tree"
            :pagination="false"
            row-key="rowKey"
            size="small"
            class="cqf-mpn-table"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'manufacturer'">{{ mfrName(record.aml.manufacturerOid) }}</template>
              <template v-else-if="column.key === 'approvedStatus'">
                <a-tag :color="statusColor(record.aml.approvedStatus)">{{ statusText(record.aml.approvedStatus) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'supplierCount'">
                <!-- 渠道明细则渲染在共享组件里（与审批/会签侧的摘要卡同一份 —— 曾经各写一遍，
                     审批侧就漏了 hover 内容，见 AvlChannelPopover 的说明） -->
                <AvlChannelPopover
                  :avls="record.avls"
                  :mpn="record.aml.mpn"
                  :supplier-names="supMap"
                />
              </template>
            </template>
          </a-table>
          <div v-else class="cqf-device__nohint">该器件暂无 MPN（AML）数据</div>

          <div v-if="row.mpn && row.mpn.summary" class="cqf-summary">
            <span>认可料号（AML）：<b>{{ row.mpn.summary.amlCount }}</b> 个</span>
            <span>
              去重供应商：<b :class="{ 'cqf-danger': row.mpn.summary.singleSource }">{{ row.mpn.summary.supplierCount }}</b> 家
            </span>
            <a-tag v-if="row.mpn.summary.singleSource" color="error">单一来源风险（R-SH-11）</a-tag>
            <a-tag v-else-if="row.mpn.summary.amlCount" color="success">多源可控</a-tag>
          </div>
        </template>
        <div v-else class="cqf-device__nohint">该对象不是器件（Part），无 MPN 信息</div>
      </div>
    </a-spin>

    <!-- ② 可填：使用位置（选系列 / 型号，不手填）与认证要求 -->
    <div class="cqf-section">使用与认证</div>

    <a-form-item label="使用位置" required>
      <a-tree-select
        v-model:value="model.usageLocationOid"
        :tree-data="usageTree"
        :loading="usageLoading"
        tree-default-expand-all
        show-search
        tree-node-filter-prop="title"
        placeholder="选择系列或型号"
        allow-clear
        class="cqf-usage"
      />
    </a-form-item>

    <a-form-item label="认证要求">
      <a-select v-model:value="model.certRequirements" mode="multiple"
        :options="CERT_OPTIONS" placeholder="可多选；器件变更影响整机认证时请勾「整机认证一致性」"
        allow-clear />
    </a-form-item>

    <a-form-item label="可靠性验证项">
      <a-select v-model:value="model.reliabilityItems" mode="multiple"
        :options="RELIABILITY_OPTIONS" placeholder="可多选；由器件类别与使用环境决定" allow-clear />
    </a-form-item>

    <div class="cqf-section">供应与准入</div>

    <a-form-item label="是否关键件（必须维护替代料）">
      <a-radio-group v-model:value="model.needAlternate">
        <a-radio :value="true">是 —— 关键件，须有替代料</a-radio>
        <a-radio :value="false">否</a-radio>
      </a-radio-group>
      <template #extra>
        <span class="cqf-extra">
          这是<b>要求标记</b>，不是现状：选「是」= 声明该器件为关键件，
          检查规则 <b>R-SH-09</b> 将要求它必须维护替代料（替代件数量取自器件替代件台账，
          为 0 时给出"关键器件没有替代料"的提醒）。与上方「单一来源风险」互补 ——
          那条看<b>供货渠道够不够</b>（R-SH-11，自动算），这条看<b>有没有准备替代件</b>。
        </span>
      </template>
    </a-form-item>

    <a-form-item label="替代与 AVL 影响">
      <a-textarea v-model:value="model.avlImpact" :rows="2"
        placeholder="如：可替代 XX 料号；涉及 AVL 新增供应商 YY" />
    </a-form-item>

    <a-form-item label="目标准入状态" required>
      <a-radio-group v-model:value="model.targetQualification">
        <a-radio value="SAMPLING">SAMPLING（试产可用）</a-radio>
        <a-radio value="QUALIFIED">QUALIFIED（正式准入）</a-radio>
      </a-radio-group>
    </a-form-item>

    <a-form-item label="申请说明">
      <a-textarea v-model:value="model.remark" :rows="3"
        placeholder="补充说明（提交后记入本节点的办理意见，便于追溯）" />
    </a-form-item>
  </a-form>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import {
  getTaskContext, getProcessInstanceDetail, listPartAmlTree, summarizePartMpn, listManufacturers, listSuppliers,
  getProductLineTree, getProductModels,
} from '@/api'
import { fetchEntityInfo } from '@/composables/useEntityInfo'
import AvlChannelPopover from './AvlChannelPopover.vue'

/**
 * 「新器件申请认证」表单 —— 器件引入 / 认定流程的发起与补录（**行业表单**）。
 *
 * <h3>它从哪来、为什么长这样</h3>
 * <p>模板体（名称 / 适用节点 / 描述 / 字段词表）由行业包
 * {@code electronics-core/processes/forms/forms.json} 播种进 {@code ck_process_form_template}；
 * 渲染组件在前端（本文件），运行期按 formKey 派发，见 {@code taskForms/index.js} 的
 * {@code TASK_FORM_VIEWS}。
 *
 * <h3>2026-10-12 改版（用户要求）</h3>
 * <p>① 器件名称 / 目标料号 / 器件类别 / 关键参数 <b>四个手填字段去掉</b> ——
 * 器件身份不该由申请人手抄（抄错就与库里不一致）；改为从<b>流程关联的业务对象</b>带出
 * （{@code GET /workflow/task/{id}/context} 的 {@code entities}），并把该器件的
 * <b>MPN（AML 认可制造商料号）</b>一并只读展示。
 * <p>② 「使用位置」由自由文本改为 <b>选择系列或型号</b>（{@code a-tree-select}）——
 * 文本手写会写出"SW-2026"“主控板”这类对不上的说法，选出来才是可追溯的对象引用。
 * 数据与 DynamicForm 的「所属产品」同一口径：产品线树（{@code /product-lines/tree}）+
 * 每个系列挂其型号（{@code /product-models?productLineOid=…}）。
 * <p>③ 卡片顶部的提示条已按用户要求移除。
 * <p>④ 「是否必需替代料」改名「**是否关键件（必须维护替代料）**」并补常显说明 ——
 * 用户反馈该字段语义与上下文不清。它的真实语义是**要求标记**，服务检查规则
 * <b>R-SH-09</b>「关键器件必须维护替代料」（{@code needAlternate=true} 且替代件台账计数为 0 时提醒），
 * 不是"现状有没有替代料"；与 MPN 汇总里的「单一来源风险（R-SH-11）」互补 ——
 * 那条看供货渠道数量（自动算），本标记看是否准备了替代件。变量名保持
 * {@code ckplmQualify_needAlternate}（与器件属性 {@code NEED_ALTERNATE} 对齐，便于将来属性安装器落地后对接）。
 *
 * <h3>提交口径</h3>
 * <p>与其它模板一致：暴露 {@code collect() → { ok, payload?, message? }}，
 * payload 走渲染器统一的 {@code completeTask}（{@code { action, comment, variables }}）。
 * 本表挂 START / TASK（填报类节点），action 固定 {@code approve}；申请说明同时作为办理意见
 * 写入 {@code act_hi_comment}。字段变量统一加 {@code ckplmQualify_} 前缀；
 * 使用位置同时记 oid 与人读名（{@code _usageLocation} 为名，便于流程详情直接展示）。
 */
const props = defineProps({
  /** 任务 id —— 取任务上下文（关联业务对象）用 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const CERT_OPTIONS = [
  { value: 'R_UL', label: 'UL' },
  { value: 'R_CE', label: 'CE' },
  { value: 'R_GB', label: '国标（GB）' },
  { value: 'R_AECQ', label: '车规 AEC-Q' },
  { value: 'R_SRRC', label: '无线 SRRC' },
  { value: 'R_CCC', label: '3C' },
  { value: 'R_CONSISTENCY', label: '整机认证一致性（器件变更影响整机认证）' },
]

const RELIABILITY_OPTIONS = [
  { value: 'V_HTOL', label: 'HTOL 高温工作寿命' },
  { value: 'V_TC', label: '温度循环' },
  { value: 'V_DH', label: '湿热' },
  { value: 'V_VIB', label: '振动' },
  { value: 'V_ESD', label: 'ESD' },
  { value: 'V_SOLDER', label: '可焊性' },
]

/** MPN 表列 —— 与 PartMpnPanel 同口径，但只读（本表不管理 MPN，只引用） */
const MPN_COLUMNS = [
  { title: '制造商', key: 'manufacturer', width: 180 },
  { title: '制造商料号 MPN', dataIndex: ['aml', 'mpn'], key: 'mpn', width: 180 },
  { title: '原厂描述', dataIndex: ['aml', 'mpnDesc'], key: 'mpnDesc', ellipsis: true },
  { title: '认证状态', key: 'approvedStatus', width: 110 },
  { title: '原厂生命周期', dataIndex: ['aml', 'lifecycleState'], key: 'lifecycleState', width: 120 },
  { title: '供货渠道', key: 'supplierCount', width: 100 },
]

const model = ref({
  usageLocationOid: undefined,
  certRequirements: [],
  reliabilityItems: [],
  needAlternate: false,
  avlImpact: '',
  targetQualification: 'QUALIFIED',
  remark: '',
})

// ==================== 只读区：流程关联器件 + MPN ====================

const loading = ref(true)
const entities = ref([])
const mfrMap = ref({})
const supMap = ref({})

const emptyHint = computed(() => (props.taskId
  ? '该流程未关联业务实体 —— 从业务对象（器件）页发起流程时，器件与 MPN 会自动带出'
  : '未选择业务对象 —— 发起时以清单里选中的对象为准，器件与 MPN 会自动带出'))

function mfrName(oid) {
  return mfrMap.value[oid] || oid || '—'
}

const statusText = (s) => ({ PREFERRED: '首选', APPROVED: '认可', DISALLOWED: '禁用' }[s] || s || '—')
const statusColor = (s) => ({ PREFERRED: 'blue', APPROVED: 'green', DISALLOWED: 'red' }[s] || 'default')

/**
 * 器件引用的**两种来源**（2026-10-13 用户要求：本表也可作为 start 节点的表单）。
 *
 * <ol>
 *   <li><b>发起流程时 —— 发起弹框里选中的对象</b>：发起时还没有任务实例，
 *       {@code getTaskContext(taskId)} 无从谈起；但"是哪颗器件"此刻已经定了
 *       （用户就是从它的行操作点「发起器件认证」进来的），由发起页通过表单上下文
 *       {@code form.entities} 带进来（形状同 {@code startEntityPayload}：
 *       {@code entityOid / typeCode / entityCode}）—— 与提交给后端的实体清单是**同一份选择**；</li>
 *   <li><b>办理 / 其它人工节点 —— 流程关联的 PBS（{@code ck_process_entity_set}）</b>：
 *       仍走 {@code getTaskContext(taskId)}。以**实例实际关联**为准，而不是"发起时选的"
 *       —— 两者在极端情况下会不同（发起失败重试、后续增删对象），能看实例就按实例来。</li>
 * </ol>
 *
 * <p>拿 ref 之后的一切（器件详情 / AML / AVL / MPN 汇总）两条来源完全共用 ——
 * 它们只吃 {@code entityOid}，与"任务 / 发起"无关。
 */
async function resolveEntityRefs() {
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

/**
 * 是否按器件处理（决定要不要拉 AML/AVL）。
 *
 * <p>办理来源带 {@code rootTypeCode}（准）；**发起来源没有它** —— 那时先按器件试一次：
 * 非器件会查空并被吞掉，模板里"MPN 表为空就不渲染"，不会露出一个空表头。
 */
function isPartRef(ref) {
  if (ref?.rootTypeCode) {
    return ref.rootTypeCode === 'PART'
  }
  return true
}

async function loadEntities() {
  loading.value = true
  try {
    const refs = await resolveEntityRefs()
    entities.value = refs.map((ref) => ({
      ref,
      // 只有器件（能力宿主 PART）才有 AML/AVL 可看；其它宿主（文档等）只显示引用
      isPart: isPartRef(ref),
      info: null,
      mpn: null,
    }))
    await Promise.all(entities.value.map(async (row) => {
      row.info = await fetchEntityInfo(row.ref)
      if (row.isPart) {
        try {
          const [treeRes, sumRes] = await Promise.all([
            listPartAmlTree(row.ref.entityOid),
            summarizePartMpn(row.ref.entityOid),
          ])
          const list = treeRes?.code === 200 ? (treeRes.data || []) : []
          row.mpn = {
            tree: list.map((node) => ({ ...node, rowKey: node.aml?.oid })),
            summary: sumRes?.code === 200 ? sumRes.data : null,
          }
        } catch {
          row.mpn = { tree: [], summary: null }
        }
      }
    }))
  } catch {
    entities.value = []
  } finally {
    loading.value = false
  }
}

/** 制造商 oid → 名称（MPN 表要显示人名可读的制造商，库里存的是 oid） */
async function loadManufacturers() {
  try {
    const res = await listManufacturers()
    const map = {}
    for (const item of (res?.data || [])) {
      map[item.oid] = item.name
    }
    mfrMap.value = map
  } catch {
    mfrMap.value = {}
  }
}

/** 供应商 oid → 名称（供货渠道 hover 面板里显示供应商名，库里存的是 oid） */
async function loadSuppliers() {
  try {
    const res = await listSuppliers()
    const map = {}
    for (const item of (res?.data || [])) {
      map[item.oid] = item.name
    }
    supMap.value = map
  } catch {
    supMap.value = {}
  }
}

// ==================== 使用位置：系列 / 型号选择器 ====================

const usageTree = ref([])
const usageLoading = ref(false)
/** oid → 显示名（提交时要把它一并写进流程变量，流程详情页才不用再回查） */
const usageNameMap = ref({})

/**
 * 组合树：系列（可含子系列）→ 型号。
 *
 * <p>与 DynamicForm 的「所属产品」同一口径（两处都从产品线树派生，避免同一份数据两种形状）。
 * 取不到时给空树 —— 选择器空着总比填错强；提交时必填校验会拦住。
 */
async function loadUsageTree() {
  usageLoading.value = true
  try {
    const res = await getProductLineTree()
    const lines = res?.code === 200 ? (res.data || []) : []
    const nameMap = {}

    async function buildNode(lineNode) {
      const node = {
        title: lineNode.name || lineNode.code,
        value: lineNode.oid,
        key: lineNode.oid,
      }
      nameMap[lineNode.oid] = node.title
      try {
        const modelsRes = await getProductModels({ productLineOid: lineNode.oid })
        const models = modelsRes?.code === 200 ? (modelsRes.data || []) : []
        if (models.length) {
          node.children = models.map((m) => {
            const title = m.name || m.code
            nameMap[m.oid] = title
            return { title, value: m.oid, key: m.oid }
          })
        }
      } catch { /* 型号取不到不影响系列可选 */ }
      if (lineNode.children?.length) {
        const childNodes = await Promise.all(lineNode.children.map(buildNode))
        node.children = [...(node.children || []), ...childNodes]
      }
      return node
    }

    const tree = []
    for (const line of lines) {
      tree.push(await buildNode(line))
    }
    usageTree.value = tree
    usageNameMap.value = nameMap
  } catch {
    usageTree.value = []
  } finally {
    usageLoading.value = false
  }
}

/** 变量读取容错：可能是 { name: value }，也可能是 [{ name, value }]（与摘要卡同一口径） */
function variableOf(variables, name) {
  if (!variables) return null
  if (Array.isArray(variables)) {
    const hit = variables.find((v) => v && v.name === name)
    return hit ? hit.value : null
  }
  return variables[name] ?? null
}

/** 多选值容错：可能是数组，也可能是 JSON 字符串（Flowable 存 List 的两种落法） */
function asArray(value) {
  if (Array.isArray(value)) return value
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

/**
 * 「重新提交申请」等**办理节点**：把之前填过的申请内容**回填**进表单。
 *
 * <p>为什么必须回填：这类节点的存在意义就是"改一改再交"（如初审驳回后重新提交）——
 * 让人从空白重填一遍，等于把已经躺在流程变量里的信息当成不存在（用户反馈 2026-10-13：
 * "重新提交元器件认证申请页面…没有将之前填写的数据带过来"）。
 *
 * <p>三条约束：
 * <ul>
 *   <li>只跑**有任务**的场景（无 taskId 的发起侧没有"之前"，且首次发起本就该是空的）；</li>
 *   <li>**不覆盖已填的值**：草稿恢复（发起侧）排在它之后、优先级更高；</li>
 *   <li>失败静默：回填是锦上添花，读不到变量不该挡住填写。</li>
 * </ul>
 *
 * <p>兼容两种前缀：{@code ckplmQualify_*}（本表）与 {@code ckplmComponentIntro_*}
 * （已下线的「元器件引入申请单」，在途老实例的变量仍在）。
 */
async function restoreFromVariables() {
  if (!props.taskId) return
  try {
    const ctxRes = await getTaskContext(props.taskId)
    const instId = ctxRes?.code === 200 ? ctxRes.data?.process?.id : null
    if (!instId) return
    const detailRes = await getProcessInstanceDetail(instId)
    const vars = detailRes?.code === 200 ? detailRes.data?.variables : null
    if (!vars) return

    const pick = (...names) => {
      for (const n of names) {
        const v = variableOf(vars, n)
        if (v !== null && v !== undefined && v !== '') return v
      }
      return null
    }

    const usageOid = pick('ckplmQualify_usageLocationOid')
    if (usageOid && !model.value.usageLocationOid) {
      model.value.usageLocationOid = usageOid
    }
    const cert = asArray(pick('ckplmQualify_certRequirements'))
    if (cert.length && !model.value.certRequirements.length) {
      model.value.certRequirements = cert
    }
    const reliability = asArray(pick('ckplmQualify_reliabilityItems'))
    if (reliability.length && !model.value.reliabilityItems.length) {
      model.value.reliabilityItems = reliability
    }
    const needAlternate = pick('ckplmQualify_needAlternate')
    if (needAlternate !== null && (needAlternate === true || needAlternate === 'true')) {
      model.value.needAlternate = true
    }
    const avlImpact = pick('ckplmQualify_avlImpact')
    if (avlImpact && !model.value.avlImpact) {
      model.value.avlImpact = String(avlImpact)
    }
    const qualification = pick('ckplmQualify_targetQualification')
    if (qualification) {
      model.value.targetQualification = String(qualification)
    }
  } catch {
    /* 回填失败不影响填写 */
  }
}

onMounted(() => {
  loadEntities()
  // 「重新提交申请」这类办理节点：把之前填过的内容回填（见 restoreFromVariables）
  restoreFromVariables()
  loadManufacturers()
  loadSuppliers()
  loadUsageTree()
})

/**
 * 发起场景：回到上一步改了业务对象清单、再回到本步时，器件区要跟着变
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

// ==================== 提交 ====================

const submitText = computed(() => '提交申请')
const successText = computed(() => '申请已提交，流程继续流转')
/** 本模板只依赖自身字段与任务上下文，任何情况下都可渲染（器件取不到也不挡提交） */
const renderable = computed(() => true)

/** 收集提交载荷；不合规时返回原因（由渲染器统一提示） */
function collect() {
  if (!model.value.usageLocationOid) {
    return { ok: false, message: '请选择使用位置（系列或型号）' }
  }
  if (!model.value.targetQualification) {
    return { ok: false, message: '请选择目标准入状态' }
  }
  const variables = {
    // 使用位置：同时记 oid（可追溯）与人读名（流程详情直接展示，不必再回查）
    ckplmQualify_usageLocationOid: model.value.usageLocationOid,
    ckplmQualify_usageLocation: usageNameMap.value[model.value.usageLocationOid] || null,
    ckplmQualify_certRequirements: model.value.certRequirements,
    ckplmQualify_reliabilityItems: model.value.reliabilityItems,
    ckplmQualify_needAlternate: model.value.needAlternate === true,
    ckplmQualify_avlImpact: model.value.avlImpact.trim() || null,
    ckplmQualify_targetQualification: model.value.targetQualification,
  }
  return {
    ok: true,
    payload: {
      action: 'approve',
      comment: model.value.remark.trim() || '提交新器件申请认证',
      variables,
    },
  }
}

/**
 * 草稿快照：**model 的浅拷贝**（2026-10-13 用户要求支持保存草稿）。
 *
 * <p>与 {@link collect} 的区别：不做任何校验、不做变量名转换 —— 草稿就是要能容纳"刚填了一半"
 * （比如使用位置还没选）。发起弹框在关闭时自动调它存本地，下次打开再 {@link restoreDraft} 回来。
 *
 * <p>为什么给 model 形状而不是提交变量形状：草稿是本地的、只有本组件读，
 * 用 model 键名可以直接 Object.assign 回来，省一层易错的双向映射。
 */
function snapshot() {
  return { ...model.value }
}

/** 恢复草稿：缺字段的旧草稿也能安全写回（只覆盖快照里有的键） */
function restoreDraft(data) {
  if (data && typeof data === 'object') {
    Object.assign(model.value, data)
  }
}

defineExpose({ collect, snapshot, restoreDraft, submitText, successText, renderable })
</script>

<style scoped>
.cqf-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.cqf-empty {
  margin: 4px 0 18px;
}

.cqf-usage {
  width: 100%;
}

/* 常显说明（如「是否关键件」的语义解释）：小字灰字，不抢主字段的注意力 */
.cqf-extra {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #999;
  line-height: 1.7;
}

/* 供货渠道 hover 面板的样式已随组件搬到 AvlChannelPopover.vue */

.cqf-device {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.cqf-device__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.cqf-device__code {
  font-weight: 600;
  color: #1677ff;
}

.cqf-device__name {
  color: #333;
}

.cqf-device__type {
  margin-left: auto;
  font-size: 12px;
  color: #bfbfbf;
}

.cqf-device__nohint {
  font-size: 12px;
  color: #999;
}

.cqf-mpn-table {
  margin-bottom: 8px;
}

.cqf-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #666;
}

.cqf-danger {
  color: #cf1322;
}
</style>
