<template>
  <div class="qrs">
    <a-spin :spinning="loading">
      <!-- ① 流程关联的器件 + MPN（若流程绑定了业务对象） -->
      <div v-for="row in entities" :key="row.ref.entityOid" class="qrs-device">
        <div class="qrs-device__head">
          <span class="qrs-device__code">{{ row.info?.code || row.ref.entityOid }}</span>
          <span class="qrs-device__name">{{ row.info?.name || '—' }}</span>
          <a-tag v-if="row.ref.entityVersion" color="blue">版本 {{ row.ref.entityVersion }}</a-tag>
          <a-tag v-if="row.info?.statusName" color="green">{{ row.info.statusName }}</a-tag>
          <span class="qrs-device__type">{{ row.ref.typeCode }}</span>
        </div>
        <template v-if="row.isPart">
          <a-table
            v-if="row.mpn && row.mpn.tree.length"
            :columns="MPN_COLUMNS" :data-source="row.mpn.tree"
            :pagination="false" row-key="rowKey" size="small" class="qrs-mpn"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'manufacturer'">{{ mfrName(record.aml.manufacturerOid) }}</template>
              <template v-else-if="column.key === 'approvedStatus'">
                <a-tag :color="statusColor(record.aml.approvedStatus)">{{ statusText(record.aml.approvedStatus) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'supplierCount'">
                <!-- 渠道明细与「元器件认证申请」共用一份渲染（2026-10-13 用户反馈：
                     审批侧 hover 到「N 家」没有任何内容，而申请侧有） -->
                <AvlChannelPopover
                  :avls="record.avls"
                  :mpn="record.aml.mpn"
                  :supplier-names="supMap"
                />
              </template>
            </template>
          </a-table>
          <div v-else class="qrs-hint">该器件暂无 MPN（AML）数据</div>
          <div v-if="row.mpn && row.mpn.summary" class="qrs-summary">
            <span>认可料号（AML）：<b>{{ row.mpn.summary.amlCount }}</b> 个</span>
            <span>去重供应商：<b :class="{ 'qrs-danger': row.mpn.summary.singleSource }">{{ row.mpn.summary.supplierCount }}</b> 家</span>
            <a-tag v-if="row.mpn.summary.singleSource" color="error">单一来源风险（R-SH-11）</a-tag>
            <a-tag v-else-if="row.mpn.summary.amlCount" color="success">多源可控</a-tag>
          </div>
        </template>
        <div v-else class="qrs-hint">该对象不是器件（Part），无 MPN 信息</div>
      </div>

      <!-- ② 申请信息（流程变量：申请单/认证表节点填的要点） -->
      <a-descriptions v-if="fields.length" size="small" bordered :column="1" class="qrs-fields">
        <a-descriptions-item v-for="item in fields" :key="item.label" :label="item.label">
          {{ item.value }}
        </a-descriptions-item>
      </a-descriptions>

      <a-empty
        v-if="!loading && !entities.length && !fields.length"
        description="未取到申请信息 —— 该流程既未关联业务对象，也没有填过的申请单变量"
        class="qrs-empty"
      />
    </a-spin>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getTaskContext, getProcessInstanceDetail, listPartAmlTree, summarizePartMpn, listManufacturers, listSuppliers } from '@/api'
import { fetchEntityInfo } from '@/composables/useEntityInfo'
import AvlChannelPopover from './AvlChannelPopover.vue'

/**
 * 「元器件引入申请信息」只读摘要 —— 评审表 / 会签表 / 认证申请表**共用**的一块。
 *
 * <h3>为什么要单独抽出来</h3>
 * <p>用户要求：新器件引入流程的评审与会签<b>不能用系统内置的审批/会签表单</b> ——
 * 因为内置表单只看得到"同意 / 驳回 + 意见"，<b>看不到这颗器件的申请信息</b>。
 * 而"申请信息"由两处构成（申请单填的、绑定的器件与其 MPN），三张表都要看同一份 ——
 * 各写一遍必然漂移（含格式化口径），所以收口在这里。
 *
 * <h3>数据来源</h3>
 * <ol>
 *   <li>流程实例详情里的<b>流程变量</b>（申请单 / 认证表节点写入的要点）；
 *       兼容两种前缀：{@code ckplmQualify_*}（元器件认证申请）与
 *       {@code ckplmComponentIntro_*}（元器件引入申请单）—— 谁在跑读谁，取不到就不显示该行；</li>
 *   <li>流程关联的<b>业务实体</b>（{@code context.entities}）：器件行 + MPN（AML）表 +
 *       单一来源汇总 —— 与「元器件认证申请」同一口径。</li>
 * </ol>
 *
 * <p>只读：本组件不暴露 collect()，由使用它的表单负责收集自己的结论与意见。
 */
const props = defineProps({
  /** 任务 id */
  taskId: { type: String, default: '' },
})

const MPN_COLUMNS = [
  { title: '制造商', key: 'manufacturer', width: 150 },
  { title: '制造商料号 MPN', dataIndex: ['aml', 'mpn'], key: 'mpn', width: 170 },
  { title: '原厂描述', dataIndex: ['aml', 'mpnDesc'], key: 'mpnDesc', ellipsis: true },
  { title: '认证状态', key: 'approvedStatus', width: 90 },
  { title: '原厂生命周期', dataIndex: ['aml', 'lifecycleState'], key: 'lifecycleState', width: 110 },
  { title: '供货渠道', key: 'supplierCount', width: 90 },
]

const loading = ref(true)
const entities = ref([])
const fields = ref([])
const mfrMap = ref({})
/** 供应商 oid → 名称（渠道 hover 面板要显示名称，库里存的是 oid） */
const supMap = ref({})

function mfrName(oid) {
  return mfrMap.value[oid] || oid || '—'
}
const statusText = (s) => ({ PREFERRED: '首选', APPROVED: '认可', DISALLOWED: '禁用' }[s] || s || '—')
const statusColor = (s) => ({ PREFERRED: 'blue', APPROVED: 'green', DISALLOWED: 'red' }[s] || 'default')

/** 变量结构容错：可能是 { name: value }，也可能是 [{name, value}] */
function variableOf(variables, name) {
  if (!variables) return null
  if (Array.isArray(variables)) {
    const hit = variables.find((v) => v && v.name === name)
    return hit ? hit.value : null
  }
  return variables[name] ?? null
}

/** 同义字段：按顺序取第一个有值的（两种表单前缀的字段名不同，这里统一成人读的一行） */
function firstOf(vars, names) {
  for (const name of names) {
    const value = variableOf(vars, name)
    if (value !== null && value !== undefined && value !== '') return value
  }
  return null
}

const ENUM_TEXT = {
  R_UL: 'UL', R_CE: 'CE', R_GB: '国标（GB）', R_AECQ: '车规 AEC-Q',
  R_SRRC: '无线 SRRC', R_CCC: '3C', R_CONSISTENCY: '整机认证一致性',
  V_HTOL: 'HTOL 高温工作寿命', V_TC: '温度循环', V_DH: '湿热',
  V_VIB: '振动', V_ESD: 'ESD', V_SOLDER: '可焊性',
  SAMPLING: 'SAMPLING（试产可用）', QUALIFIED: 'QUALIFIED（正式准入）',
}

/** 变量值 → 展示文本（数组拼接；枚举码翻中文；布尔给是/否） */
function displayOf(value) {
  if (value === null || value === undefined || value === '') return null
  if (Array.isArray(value)) {
    return value.length ? value.map((v) => ENUM_TEXT[v] || v).join('、') : null
  }
  if (typeof value === 'boolean') return value ? '是' : '否'
  return ENUM_TEXT[value] || String(value)
}

function buildFields(vars) {
  const rows = [
    { label: '需求名称', value: displayOf(firstOf(vars, ['ckplmComponentIntro_partName'])) },
    { label: '目标参数要求', value: displayOf(firstOf(vars, ['ckplmComponentIntro_targetParams'])) },
    { label: '使用位置 / 场景', value: displayOf(firstOf(vars, ['ckplmQualify_usageLocation', 'ckplmComponentIntro_usageScenario'])) },
    { label: '合规要求', value: displayOf(firstOf(vars, ['ckplmComponentIntro_complianceRequired'])) },
    { label: '认证要求', value: displayOf(firstOf(vars, ['ckplmQualify_certRequirements'])) },
    { label: '可靠性验证项', value: displayOf(firstOf(vars, ['ckplmQualify_reliabilityItems'])) },
    { label: '是否关键件（必须维护替代料）', value: displayOf(firstOf(vars, ['ckplmQualify_needAlternate', 'ckplmComponentIntro_needAlternate'])) },
    { label: '替代与 AVL 影响', value: displayOf(firstOf(vars, ['ckplmQualify_avlImpact'])) },
    { label: '目标准入状态', value: displayOf(firstOf(vars, ['ckplmQualify_targetQualification', 'ckplmComponentIntro_targetQualification'])) },
  ]
  return rows.filter((r) => r.value !== null)
}

async function load() {
  loading.value = true
  try {
    entities.value = []
    fields.value = []
    if (!props.taskId) return

    const ctxRes = await getTaskContext(props.taskId).catch(() => null)
    const ctx = ctxRes?.code === 200 ? ctxRes.data : null

    // 器件（业务实体）—— 与「元器件认证申请」同一手法
    const refs = ctx?.entities || []
    entities.value = refs.map((ref) => ({
      ref,
      isPart: (ref.rootTypeCode || '') === 'PART',
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

    // 申请变量 —— 从流程实例详情拿
    const instId = ctx?.process?.id
    if (instId) {
      const detailRes = await getProcessInstanceDetail(instId).catch(() => null)
      const vars = detailRes?.code === 200 ? detailRes.data?.variables : null
      fields.value = buildFields(vars)
    }
  } catch {
    entities.value = []
    fields.value = []
  } finally {
    loading.value = false
  }
}

/** 制造商 oid → 名称 */
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

/** 供应商 oid → 名称（与制造商同一手法：查不到就退化显示 oid） */
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

watch(() => props.taskId, load, { immediate: true })
loadManufacturers()
loadSuppliers()
</script>

<style scoped>
.qrs-device {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.qrs-device__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.qrs-device__code {
  font-weight: 600;
  color: #1677ff;
}

.qrs-device__name {
  color: #333;
}

.qrs-device__type {
  margin-left: auto;
  font-size: 12px;
  color: #bfbfbf;
}

.qrs-mpn {
  margin-bottom: 8px;
}

.qrs-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #666;
}

.qrs-danger {
  color: #cf1322;
}

.qrs-hint {
  font-size: 12px;
  color: #999;
}

.qrs-fields {
  margin-top: 4px;
}

.qrs-empty {
  margin: 4px 0 12px;
}
</style>
