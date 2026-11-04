<template>
  <div class="gdrs">
    <a-empty
      v-if="!hasApplication"
      description="本实例没有决策申请内容（发起时可能走的不是「决策申请与自检结果」那张表单）—— 仍可继续办理"
      class="gdrs-empty"
    />
    <template v-else>
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="阶段门">
          <span class="gdrs-strong">{{ gateText }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="交付物完成率">
          <span class="gdrs-strong">{{ rateText }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="未关闭阻断问题数">
          <span class="gdrs-strong">{{ issuesText }}</span>
        </a-descriptions-item>
        <a-descriptions-item v-if="certText" label="认证齐套">
          <a-tag :color="certComplete ? 'green' : 'orange'">{{ certText }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item v-if="supplyText" label="供应已确认">
          <a-tag :color="supplyConfirmed ? 'green' : 'orange'">{{ supplyText }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="决策材料" :span="2">
          <template v-if="materials.length">
            <div v-for="item in materials" :key="item.code" class="gdrs-material">
              <a v-if="item.row" class="gdrs-material-link" :title="'查看交付项详情'" @click="openMaterial(item.row)">{{ item.label }}</a>
              <span v-else>{{ item.label }}</span>
            </div>
          </template>
          <span v-else class="gdrs-muted">未选择决策材料</span>
        </a-descriptions-item>
      </a-descriptions>
      <div class="gdrs-hint">
        以上为<b>申请人提交时</b>留痕的值（流程变量），不是此刻的实时数据 ——
        实时自检在门的「自动检查」列看，两个口径不混。
      </div>
    </template>
  </div>

  <!--
    交付项详情 = 与「阶段门决策申请单」同一个弹窗（产出物关联，只读）：
    决策材料列的是交付件 code，读的人要能点开看它到底是什么（2026-10-30 用户反馈"决策材料没有超级链接，参考申请单"）。
    查不到对应交付件（已删除 / 已裁剪 / 没装方法论包）时退回纯文本，不给一个点了没反应的链接。
  -->
  <DeliverableArtifactModal
    v-model:open="materialVisible"
    :deliverable="materialDetail"
    :owner-oid="ownerOid"
    readonly
  />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { listMethodologyInstances } from '@/api'
import DeliverableArtifactModal from '@/components/DeliverableArtifactModal.vue'
import { asCodeList, variableOf } from './variables'

/**
 * 「决策申请（只读）」摘要 —— 阶段决策流程里**所有办理 / 审批节点共用**的一块。
 *
 * <h3>为什么单独一个组件</h3>
 * <p>决策流程的每个下游节点（材料齐备性核对、决策会）都要回答同一个问题：
 * "这次申请的是哪道门、自检结果多少、要交哪些材料"。写两遍必然漂移 ——
 * 与技术评审的 {@code TechnicalReviewRequestSummary} / 元器件认证的
 * {@code QualificationRequestSummary} 是同一种拆法。
 *
 * <h3>数据从哪来</h3>
 * <p>流程变量 {@code ckplmGateDecision_*}（由发起节点的 {@code GateDecisionApplyForm#collect} 写入）。
 * 刻意<b>不现算</b>：核对 / 表决的对象是"申请时提交的那份"，实时自检另有一处可看。
 *
 * <p>门的名称与交付件名称按 {@code GET /api/instances} 回查，查不到就显示 code ——
 * 名称是锦上添花，不该让办理卡住。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：variables / businessOid */
  form: { type: Object, default: null },
})

const vars = computed(() => props.form?.variables || {})

function textOf(name) {
  const value = variableOf(vars.value, name)
  return value === null || value === undefined || value === '' ? '' : String(value)
}

function numberTextOf(name) {
  const raw = textOf(name)
  if (!raw) return ''
  const n = Number(raw)
  return Number.isFinite(n) ? String(n) : raw
}

/** 有没有申请内容可显示（老实例没走过申请节点时为空） */
const hasApplication = computed(() => !!textOf('ckplmGateDecision_gate'))

const gateNames = ref({})
/** 交付件 code → 交付件行（点名称要打开「产出物关联」弹窗，所以留着整行而不只是名字） */
const deliverableRows = ref({})

async function loadNames() {
  const owner = props.form?.businessOid
  if (!owner) return
  try {
    const res = await listMethodologyInstances(owner)
    const data = res?.code === 200 ? res.data : null
    const gates = {}
    for (const gate of (data?.gates || [])) {
      if (gate?.code) gates[gate.code] = gate.name || ''
    }
    const items = {}
    for (const row of (data?.deliverables || [])) {
      if (row?.code) items[row.code] = row
    }
    gateNames.value = gates
    deliverableRows.value = items
  } catch {
    gateNames.value = {}
    deliverableRows.value = {}
  }
}

const gateText = computed(() => {
  const code = textOf('ckplmGateDecision_gate')
  if (!code) return '—'
  const name = gateNames.value[code]
  return name ? `${name}（${code}）` : code
})

const rateText = computed(() => {
  const value = numberTextOf('ckplmGateDecision_deliverableRate')
  return value ? `${value}%` : '—'
})

const issuesText = computed(() => {
  const value = numberTextOf('ckplmGateDecision_openBlockIssues')
  return value === '' ? '—' : `${value} 个`
})

/**
 * 专项确认：变量只在**该门声明了对应检查**时才有值（申请单对不适用的门写 null），
 * 所以"没有这个变量"就整行不显示 —— 与申请单的显隐规则同一口径。
 */
const certRaw = computed(() => variableOf(vars.value, 'ckplmGateDecision_certComplete'))
const supplyRaw = computed(() => variableOf(vars.value, 'ckplmGateDecision_supplyConfirmed'))
const certComplete = computed(() => certRaw.value === true || certRaw.value === 'true')
const supplyConfirmed = computed(() => supplyRaw.value === true || supplyRaw.value === 'true')
const certText = computed(() => (certRaw.value === null || certRaw.value === undefined
  ? '' : (certComplete.value ? '已齐套' : '未齐套')))
const supplyText = computed(() => (supplyRaw.value === null || supplyRaw.value === undefined
  ? '' : (supplyConfirmed.value ? '已确认' : '未确认')))

/**
 * 决策材料：变量是交付件 code 逗号串（与申请单一致），按名字回显，认不出的原样显示 code。
 *
 * <p>{@code row} 为空 = 本容器里找不到这条交付件（已删除 / 标记「本项目不适用」/ 没装方法论包）——
 * 此时只显示文本，不给一个点开是空白的链接。
 */
const materials = computed(() => asCodeList(variableOf(vars.value, 'ckplmGateDecision_decisionMaterial'))
  .map((code) => {
    const row = deliverableRows.value[code] || null
    return { code, row, label: row?.name ? `${row.name}（${code}）` : code }
  }))

/** 归属单元 oid（产出物弹窗用它标注「本型号 / 其它容器」） */
const ownerOid = computed(() => props.form?.businessOid || '')

const materialVisible = ref(false)
const materialDetail = ref(null)
function openMaterial(row) {
  if (!row) return
  materialDetail.value = row
  materialVisible.value = true
}

onMounted(loadNames)
</script>

<style scoped>
.gdrs-empty {
  margin: 4px 0 12px;
}

.gdrs-strong {
  color: #333;
  font-weight: 600;
}

.gdrs-muted {
  color: #bfbfbf;
}

.gdrs-material {
  line-height: 1.8;
}

/* 与申请单的「已选材料」同一视觉：蓝色虚下划线，悬停变实线 */
.gdrs-material-link {
  color: #1677ff;
  cursor: pointer;
  border-bottom: 1px dashed transparent;
}

.gdrs-material-link:hover {
  border-bottom-color: #1677ff;
}

.gdrs-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}
</style>
