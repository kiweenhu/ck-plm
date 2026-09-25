<template>
  <a-modal
    :open="open"
    title="BOM 成本报告"
    width="1000px"
    :footer="null"
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <!-- ① 结论区：报告的主人公 + 结论数字。总项独立成块，与下方明细在视觉上分开 -->
      <div class="ck-summary">
        <div class="ck-summary__object">
          <span class="ck-summary__name">{{ report?.parentName || fallbackLabel || '当前对象' }}</span>
          <span class="ck-summary__code">{{ report?.parentCode || '—' }}</span>
          <a-tag v-if="report?.parentVersion" color="blue">{{ report.parentVersion }}</a-tag>
          <a-tag v-if="report?.parentStatus">{{ report.parentStatus }}</a-tag>
        </div>
        <div class="ck-summary__hero">
          <span class="ck-summary__hero-label">卷积总成本（含全部下级）</span>
          <span class="ck-summary__hero-value">{{ formatMoney(report?.totalCost) }}</span>
        </div>
      </div>

      <!-- ② 指标区：解释总成本由什么构成，弱一档，不与结论争视线 -->
      <div class="ck-metrics">
        <div class="ck-metric">
          <span class="ck-metric__label">本层直接成本</span>
          <span class="ck-metric__value">{{ formatMoney(report?.directCost) }}</span>
        </div>
        <div class="ck-metric">
          <span class="ck-metric__label">参与计算行数</span>
          <span class="ck-metric__value">{{ report?.totalLines ?? 0 }}</span>
        </div>
        <div class="ck-metric">
          <span class="ck-metric__label">未填单位成本(RMB)</span>
          <span class="ck-metric__value" :class="{ 'ck-metric__value--warn': missingCount > 0 }">
            {{ missingCount }} 行
          </span>
        </div>
      </div>

      <!-- 数据缺口如实提示：悄悄按 0 算会让成本整体偏小且无人察觉 -->
      <a-alert
        v-for="(warning, index) in report?.warnings || []"
        :key="index"
        type="warning"
        show-icon
        class="bcr-warning"
        :message="warning"
      />

      <!-- ③ 明细区：把"总成本是怎么来的"摊开。合计行在表尾，分组行加粗，叶子行退到次要色 -->
      <div class="ck-section">
        <div class="ck-section__title">
          成本构成明细
          <span class="ck-section__hint">
            共 {{ report?.totalLines ?? 0 }} 行 · 「本层金额」只算本行，「累计金额」含全部下级 ·
            合计行 = 父件本层各行之和
          </span>
        </div>

        <a-table
          class="ck-tree-table"
          :columns="columns"
          :data-source="report?.lines || []"
          size="small"
          row-key="oid"
          :pagination="false"
          v-model:expandedRowKeys="expandedRowKeys"
          :row-class-name="rowClassName"
          :scroll="{ y: 340 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'lineNumber'">
              <span class="ck-num ck-cell-muted">{{ record.lineNumber ?? '-' }}</span>
            </template>
            <template v-else-if="column.key === 'code'">
              <div class="bcr-node">
                <span class="bcr-node-code">{{ record.childPartNumber || record.code || '—' }}</span>
                <span class="bcr-node-name">{{ record.childPartName || record.name }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'quantity'">
              <span class="ck-num">{{ record.quantity ?? '-' }}</span>
            </template>
            <template v-else-if="column.key === 'unitCost'">
              <span class="ck-num" :class="{ 'bcr-missing': record.unitCost == null }">
                {{ record.unitCost == null ? '未填' : formatMoney(record.unitCost) }}
              </span>
            </template>
            <template v-else-if="column.key === 'extendedCost'">
              <span class="ck-num">{{ formatMoney(record.extendedCost) }}</span>
            </template>
            <template v-else>
              <!-- 分组行的累计金额是"小计"，加粗；叶子行与「本层金额」相等，退到次要色避免抢视线 -->
              <span
                class="ck-num"
                :class="hasChildren(record) ? 'bcr-rollup' : 'ck-cell-muted'"
              >
                {{ formatMoney(record.rolledUpCost) }}
              </span>
            </template>
          </template>

          <!-- ④ 合计行：整表唯一的"总项"，用底色与顶部分隔线压实 -->
          <template #summary>
            <a-table-summary fixed>
              <a-table-summary-row>
                <a-table-summary-cell :index="0" :col-span="2">
                  <span class="ck-summary-label">合计 · 父件本层</span>
                </a-table-summary-cell>
                <a-table-summary-cell :index="2" align="right">
                  <span class="ck-cell-muted">—</span>
                </a-table-summary-cell>
                <a-table-summary-cell :index="3" align="right">
                  <span class="ck-cell-muted">—</span>
                </a-table-summary-cell>
                <a-table-summary-cell :index="4" align="right">
                  <span class="ck-cell-muted">—</span>
                </a-table-summary-cell>
                <a-table-summary-cell :index="5" align="right">
                  <span class="ck-num">{{ formatMoney(report?.directCost) }}</span>
                </a-table-summary-cell>
                <a-table-summary-cell :index="6" align="right">
                  <span class="ck-num bcr-rollup">{{ formatMoney(report?.totalCost) }}</span>
                </a-table-summary-cell>
              </a-table-summary-row>
            </a-table-summary>
          </template>
        </a-table>

        <div v-if="!loading && !(report?.lines || []).length" class="bcr-empty">
          该版本没有 BOM 子件，因此没有可卷积的成本。
        </div>
      </div>

      <!-- ⑤ 口径：写在报告里才能被复核（最弱一档，读得到但不抢视线） -->
      <div class="ck-note">
        <span class="ck-note__label">口径</span>{{ COST_METHOD_TEXT }}
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup>
/**
 * BOM 成本报告（卷积）。
 *
 * <p>三件事必须同时给出，缺一件这个报告就没法用：
 * <ol>
 *   <li><b>总成本</b>（一眼看到结论）；</li>
 *   <li><b>逐层明细</b>（总成本是怎么来的，要能展开核对到每一行）；</li>
 *   <li><b>口径与数据缺口</b>（每层落分后明细与合计一致、哪几行没填单位成本 —— 不提示就等于悄悄少算）。</li>
 * </ol>
 *
 * <p>取数只调一个接口（后端一次把树与成本算好）：前端再自己卷积一遍，就会出现
 * "界面上的合计与后端算的不一致"，而两边都不算错。
 *
 * <p>视觉上按「结论 → 指标 → 明细 → 口径」四档排布（层次类见全局 `style.css` 的
 * `ck-summary` / `ck-metrics` / `ck-section` / `ck-note`）：早先标题、总成本、合计与
 * 明细行用同样的字号与字重，用户得逐字读才知道哪一行是结论。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getBomCostReport } from '@/api'
import { COST_METHOD_TEXT, formatMoney } from '@/utils/cost'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 计价口径：父件迭代 oid（成本是有版本的：A.4 与 B.1 可能不同） */
  parentIterationOid: { type: String, default: '' },
  /** 取不到后端对象信息时在标题处兜底显示的名称 */
  fallbackLabel: { type: String, default: '' },
})

const emit = defineEmits(['update:open'])

const loading = ref(false)
const report = ref(null)

const missingCount = computed(() => report.value?.missingCostLines ?? 0)

/**
 * 明细默认全展开。
 *
 * <p>不能用 `default-expand-all-rows`：它只在表格初始化那一刻生效，而这份数据是
 * <b>打开弹窗后异步到</b>的（初始化时 dataSource 还是空的）—— 结果就是明细只显示顶层，
 * "总成本是怎么来的"得靠用户逐层点开，报告也就白给了。
 */
const expandedRowKeys = ref([])

function expandAll(rows) {
  const keys = []
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.children?.length) {
        keys.push(node.oid)
        walk(node.children)
      }
    }
  }
  walk(rows)
  return keys
}

// 数字列统一右对齐 + 等宽数字（见 .ck-num）：金额要能竖着比大小
const columns = [
  { key: 'lineNumber', title: '行号', width: 88, align: 'center' },
  { key: 'code', title: '子件', dataIndex: 'childPartNumber', ellipsis: true },
  { key: 'quantity', title: '数量', width: 80, align: 'right' },
  { key: 'unit', title: '单位', dataIndex: 'unit', width: 64, align: 'center' },
  { key: 'unitCost', title: '单位成本(RMB)', width: 132, align: 'right' },
  { key: 'extendedCost', title: '本层金额', width: 110, align: 'right' },
  { key: 'rolledUpCost', title: '累计金额', width: 120, align: 'right' },
]

/** 有下级 = 这一行是"小计"（加粗），叶子行是明细（退到次要色） */
function hasChildren(record) {
  return !!(record?.children && record.children.length)
}

/** 行级别 class：交给全局表格层次样式（见 style.css 的 .ck-row-group / .ck-row-leaf） */
function rowClassName(record) {
  return hasChildren(record) ? 'ck-row-group' : 'ck-row-leaf'
}

function close() {
  emit('update:open', false)
}

async function load() {
  if (!props.parentIterationOid) {
    report.value = null
    return
  }
  loading.value = true
  try {
    const res = await getBomCostReport(props.parentIterationOid)
    if (res?.code !== 200) {
      // 失败原因由拦截器统一提示，这里只保证不把上一次的报告留在屏幕上
      report.value = null
      expandedRowKeys.value = []
      return
    }
    report.value = res.data || null
    // 数据到了再决定展开哪些行（见 expandedRowKeys 的说明）
    expandedRowKeys.value = expandAll(report.value?.lines)
  } catch {
    report.value = null
    message.warning('成本报告加载失败')
  } finally {
    loading.value = false
  }
}

// 每次打开都重新取：单位成本可以在"子件清单"里直接改，缓存住的数字比没有更糟
watch(() => props.open, (value) => {
  if (value) {
    load()
  }
})
</script>

<style scoped>
.bcr-warning {
  margin-top: 12px;
}

.bcr-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bcr-node-code {
  font-family: monospace;
}

.bcr-node-name {
  color: #8c8c8c;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 小计金额：整行唯一要抢视线的东西 */
.bcr-rollup {
  font-weight: 600;
  color: #262626;
}

.bcr-missing {
  color: #d46b08;
}

.bcr-empty {
  padding: 24px 0;
  text-align: center;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
