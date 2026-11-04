<template>
  <div class="pmp-panel">
    <!-- 工具栏：新增 AML -->
    <div class="pmp-bar">
      <a-button type="primary" :disabled="!partOid" @click="openAmlForm()">
        <PlusOutlined /> 新增制造商料号
      </a-button>
      <span class="pmp-hint">
        认可制造商料号（AML）+ 每家料号的供货渠道（AVL）；单一来源风险按检查规则 R-SH-11 提示
      </span>
    </div>

    <!-- 汇总条：AML 数 / 去重供应商数 / 是否单一来源（对应 R-SH-11） -->
    <div v-if="summary" class="pmp-summary">
      <span class="pmp-summary-item">认可料号（AML）：<b>{{ summary.amlCount }}</b> 个</span>
      <span class="pmp-summary-item">
        去重供应商（supplierCount）：<b :class="{ 'pmp-danger': summary.singleSource }">{{ summary.supplierCount }}</b> 家
      </span>
      <a-tag v-if="summary.singleSource" color="error">
        <WarningOutlined /> 单一来源风险（R-SH-11）
      </a-tag>
      <a-tag v-else-if="summary.amlCount" color="success">多源可控</a-tag>
      <span class="pmp-hint">{{ summary.derivation }}</span>
    </div>

    <!-- AML 表（行可展开 → AVL 供货渠道子表） -->
    <a-table
      :columns="amlColumns" :data-source="tree" :loading="treeLoading"
      :pagination="false" row-key="rowKey" size="small"
    >
      <template #expandedRowRender="{ record }">
        <div class="pmp-avl-wrap">
          <div class="pmp-avl-head">
            <span>
              供货渠道（AVL）—— <b>{{ record.aml.mpn }}</b>
              共 {{ record.avls.length }} 家
            </span>
            <a-button type="link" size="small" @click="openAvlForm(record.aml)">
              <PlusOutlined /> 新增渠道
            </a-button>
          </div>
          <a-table
            :columns="avlColumns" :data-source="record.avls"
            :pagination="false" row-key="oid" size="small"
          >
            <template #bodyCell="{ column, record: avl }">
              <template v-if="column.key === 'preferred'">
                <a-tag v-if="avl.preferred" color="blue">首选</a-tag>
                <span v-else class="pmp-hint">—</span>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="openAvlForm(record.aml, avl)">编辑</a-button>
                <a-popconfirm title="确定删除该供货渠道？" ok-text="删除" cancel-text="取消" @confirm="removeAvl(avl)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
            </template>
          </a-table>
        </div>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'manufacturer'">
          {{ mfrName(record.aml.manufacturerOid) }}
        </template>
        <template v-else-if="column.key === 'approvedStatus'">
          <a-tag :color="statusColor(record.aml.approvedStatus)">
            {{ statusText(record.aml.approvedStatus) }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'supplierCount'">
          <a-tag :color="record.avls.length ? 'blue' : 'warning'">
            {{ record.avls.length }} 家
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openAmlForm(record.aml)">编辑</a-button>
          <a-popconfirm title="删除该 AML 行会一并删掉它下面的全部供货渠道，确定？" ok-text="删除" cancel-text="取消" @confirm="removeAml(record.aml)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
      <template #emptyText>
        <span v-if="!partOid">未指定内部件</span>
        <span v-else>该内部件还没有认可任何制造商料号 —— 点「新增制造商料号」开始建 AML</span>
      </template>
    </a-table>

    <!-- AML 弹窗 -->
    <a-modal v-model:open="amlFormOpen" :title="amlForm.oid ? '编辑制造商料号（AML）' : '新增制造商料号（AML）'" :width="560" @ok="submitAml" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="制造商" required>
              <a-select v-model:value="amlForm.manufacturerOid" show-search :filter-option="false"
                        :options="mfrOptions" placeholder="选择制造商" @search="searchMfrOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="制造商料号 MPN" required><a-input v-model:value="amlForm.mpn" placeholder="原厂料号，如 TPS54331DR" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="原厂描述"><a-input v-model:value="amlForm.mpnDesc" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="认证状态">
              <a-select v-model:value="amlForm.approvedStatus" :options="statusOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="原厂生命周期">
              <a-select v-model:value="amlForm.lifecycleState" allow-clear :options="lifecycleOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="显示顺序"><a-input-number v-model:value="amlForm.sortOrder" style="width:100%" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="amlForm.note" :rows="2" placeholder="禁用（DISALLOWED）时建议写明原因，供采购追溯" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>

    <!-- AVL 弹窗 -->
    <a-modal v-model:open="avlFormOpen" :title="avlForm.oid ? '编辑供货渠道（AVL）' : '新增供货渠道（AVL）'" :width="560" @ok="submitAvl" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="14">
            <a-form-item label="供应商" required>
              <a-select v-model:value="avlForm.supplierOid" show-search :filter-option="false"
                        :options="supOptions" placeholder="选择供应商" @search="searchSupOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="10"><a-form-item label="供应商料号"><a-input v-model:value="avlForm.supplierPartNo" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="参考单价"><a-input-number v-model:value="avlForm.price" :min="0" :precision="4" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="币种"><a-input v-model:value="avlForm.currency" placeholder="CNY / USD" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="交期（天）"><a-input-number v-model:value="avlForm.leadTimeDays" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="MOQ 最小起订量"><a-input-number v-model:value="avlForm.moq" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="SPQ 最小包装量"><a-input-number v-model:value="avlForm.spq" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="显示顺序"><a-input-number v-model:value="avlForm.sortOrder" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="首选货源"><a-switch v-model:checked="avlForm.preferred" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="avlForm.note" :rows="2" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
/**
 * 内部件的 MPN 面板（AML · AVL）—— 共享组件。
 *
 * <p>来源：2026-10-11 自 `views/resource/MpnManagement.vue` 抽出（该页的「AML · AVL」页签与
 * Part 详情页新增的「MPN」页签共用同一实现 —— 同一能力不做两份）。
 * 与页面版的唯一差别：选件器去掉了，件由 {@code partOid} prop 传入（详情页天然有件、管理页选件后传入）。
 *
 * <p>能力：AML（认可制造商料号）增删改 + 行内展开 AVL（供货渠道：单价/交期/MOQ/SPQ/首选）增删改 +
 * 汇总（AML 数 / 去重供应商数 / 单一来源风险 —— 对应检查规则 R-SH-11）。
 */
import { ref, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, WarningOutlined } from '@ant-design/icons-vue'
import {
  listManufacturers, listSuppliers,
  listPartAmlTree, summarizePartMpn,
  createPartAml, updatePartAml, deletePartAml,
  createPartAvl, updatePartAvl, deletePartAvl
} from '@/api'

const props = defineProps({
  /** 内部件（Part 主对象）oid —— 本面板管理它的 AML/AVL */
  partOid: { type: String, required: true },
})

const submitting = ref(false)

// ==================== 制造商 / 供应商候选（弹窗选择器与表格名称列共用）====================
const mfrOptions = ref([])
const supOptions = ref([])

async function searchMfrOptions(keyword) {
  const res = await listManufacturers(typeof keyword === 'string' ? keyword : '')
  mfrOptions.value = (res?.code === 200 ? (res.data || []) : []).map(m => ({ value: m.oid, label: `${m.code} · ${m.name}` }))
}
async function searchSupOptions(keyword) {
  const res = await listSuppliers(typeof keyword === 'string' ? keyword : '')
  supOptions.value = (res?.code === 200 ? (res.data || []) : []).map(s => ({ value: s.oid, label: `${s.code} · ${s.name}` }))
}
function mfrName(oid) {
  const hit = mfrOptions.value.find(o => o.value === oid)
  return hit ? hit.label : oid
}
function supLabel(oid) {
  const hit = supOptions.value.find(o => o.value === oid)
  return hit ? hit.label : oid
}

// ==================== AML · AVL ====================
const treeLoading = ref(false)
const tree = ref([])
const summary = ref(null)

const amlColumns = [
  { title: '制造商', key: 'manufacturer', width: 200 },
  { title: '制造商料号 MPN', dataIndex: ['aml', 'mpn'], key: 'mpn', width: 180 },
  { title: '原厂描述', dataIndex: ['aml', 'mpnDesc'], key: 'mpnDesc', ellipsis: true },
  { title: '认证状态', key: 'approvedStatus', width: 110 },
  { title: '原厂生命周期', dataIndex: ['aml', 'lifecycleState'], key: 'lifecycleState', width: 120 },
  { title: '渠道', key: 'supplierCount', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]
const avlColumns = [
  { title: '供应商', key: 'supplier', width: 220,
    customRender: ({ record }) => supLabel(record.supplierOid) },
  { title: '供应商料号', dataIndex: 'supplierPartNo', key: 'supplierPartNo', width: 160 },
  { title: '单价', key: 'price', width: 120,
    customRender: ({ record }) => (record.price == null ? '—' : `${record.price} ${record.currency || ''}`) },
  { title: '交期(天)', dataIndex: 'leadTimeDays', key: 'leadTimeDays', width: 90 },
  { title: 'MOQ', dataIndex: 'moq', key: 'moq', width: 80 },
  { title: 'SPQ', dataIndex: 'spq', key: 'spq', width: 80 },
  { title: '首选', key: 'preferred', width: 80 },
  { title: '操作', key: 'action', width: 130 }
]

const statusOptions = [
  { value: 'PREFERRED', label: '首选（PREFERRED）' },
  { value: 'APPROVED', label: '认可（APPROVED）' },
  { value: 'DISALLOWED', label: '禁用（DISALLOWED）' }
]
const lifecycleOptions = [
  { value: 'ACTIVE', label: 'ACTIVE 量产' },
  { value: 'NRND', label: 'NRND 不推荐新设计' },
  { value: 'EOL', label: 'EOL 停产' }
]
const statusText = (s) => ({ PREFERRED: '首选', APPROVED: '认可', DISALLOWED: '禁用' }[s] || s || '—')
const statusColor = (s) => ({ PREFERRED: 'blue', APPROVED: 'green', DISALLOWED: 'red' }[s] || 'default')

async function loadTree() {
  if (!props.partOid) {
    tree.value = []
    summary.value = null
    return
  }
  treeLoading.value = true
  try {
    const [treeRes, sumRes] = await Promise.all([
      listPartAmlTree(props.partOid),
      summarizePartMpn(props.partOid)
    ])
    const list = treeRes?.code === 200 ? (treeRes.data || []) : []
    tree.value = list.map(node => ({ ...node, rowKey: node.aml?.oid }))
    summary.value = sumRes?.code === 200 ? sumRes.data : null
    // 顺带保证候选已加载：否则「制造商 / 供应商」两列只能显示 oid（原页面在打开弹窗时才加载）
    if (!mfrOptions.value.length) searchMfrOptions('')
    if (!supOptions.value.length) searchSupOptions('')
  } catch { tree.value = [] } finally { treeLoading.value = false }
}

// AML 表单
const amlFormOpen = ref(false)
const emptyAml = () => ({
  oid: null, manufacturerOid: undefined, mpn: '', mpnDesc: '',
  approvedStatus: 'APPROVED', lifecycleState: undefined, note: '', sortOrder: 0
})
const amlForm = ref(emptyAml())
function openAmlForm(record) {
  amlForm.value = record ? { ...record } : emptyAml()
  amlFormOpen.value = true
  searchMfrOptions('')
}
async function submitAml() {
  if (!amlForm.value.manufacturerOid || !amlForm.value.mpn) { message.warning('制造商与料号必填'); return }
  amlForm.value.partOid = props.partOid
  submitting.value = true
  try {
    const res = amlForm.value.oid
      ? await updatePartAml(amlForm.value.oid, amlForm.value)
      : await createPartAml(amlForm.value)
    if (res?.code === 200) { message.success('已保存'); amlFormOpen.value = false; loadTree() }
    else message.error(res?.message || '保存失败')
  } finally { submitting.value = false }
}
async function removeAml(record) {
  const res = await deletePartAml(record.oid)
  if (res?.code === 200) { message.success('已删除'); loadTree() }
  else message.error(res?.message || '删除失败')
}

// AVL 表单
const avlFormOpen = ref(false)
const emptyAvl = () => ({
  oid: null, amlOid: null, supplierOid: undefined, supplierPartNo: '', preferred: false,
  price: null, currency: 'CNY', leadTimeDays: null, moq: null, spq: null, note: '', sortOrder: 0
})
const avlForm = ref(emptyAvl())
function openAvlForm(aml, avl) {
  avlForm.value = avl ? { ...avl } : { ...emptyAvl(), amlOid: aml.oid }
  avlFormOpen.value = true
  searchSupOptions('')
}
async function submitAvl() {
  if (!avlForm.value.supplierOid) { message.warning('供应商必填'); return }
  submitting.value = true
  try {
    const res = avlForm.value.oid
      ? await updatePartAvl(avlForm.value.oid, avlForm.value)
      : await createPartAvl(avlForm.value)
    if (res?.code === 200) { message.success('已保存'); avlFormOpen.value = false; loadTree() }
    else message.error(res?.message || '保存失败')
  } finally { submitting.value = false }
}
async function removeAvl(avl) {
  const res = await deletePartAvl(avl.oid)
  if (res?.code === 200) { message.success('已删除'); loadTree() }
  else message.error(res?.message || '删除失败')
}

// ==================== 初始化 / 换件重载 ====================
onMounted(() => loadTree())
watch(() => props.partOid, () => loadTree())
</script>

<style scoped>
.pmp-panel { padding: 2px 0; }

.pmp-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.pmp-hint { color: #8c8c8c; font-size: 12px; }

.pmp-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
.pmp-summary-item { font-size: 13px; }
.pmp-danger { color: #cf1322; font-weight: 700; }

.pmp-avl-wrap { padding: 4px 8px 8px 8px; background: #fcfcfc; }
.pmp-avl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}
</style>
