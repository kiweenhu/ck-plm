<template>
  <a-spin :spinning="loading">
    <div class="ecn-card">
      <div class="ecn-head">
        <span class="ecn-title">变更通告</span>
        <a-tag v-if="notice.code" color="blue">{{ notice.code }}</a-tag>
        <a-tag v-if="notice.changeLevel" :color="CHANGE_LEVEL_COLOR[notice.changeLevel] || 'default'">
          {{ CHANGE_LEVEL_LABEL[notice.changeLevel] || notice.changeLevel }}变更
        </a-tag>
        <a-tag v-if="notice.status" :color="CHANGE_NOTICE_STATUS_COLOR[notice.status] || 'default'">
          {{ CHANGE_NOTICE_STATUS_LABEL[notice.status] || notice.status }}
        </a-tag>
        <span v-if="loadError" class="ecn-warn">{{ loadError }}</span>
      </div>
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="标题" :span="2">{{ notice.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变更类型">{{ CHANGE_TYPE_LABEL[notice.changeType] || '—' }}</a-descriptions-item>
        <a-descriptions-item label="优先级">{{ PRIORITY_LABEL[notice.priority] || '—' }}</a-descriptions-item>
        <a-descriptions-item label="责任人">{{ notice.owner || '—' }}</a-descriptions-item>
        <a-descriptions-item label="计划完成">{{ notice.planEnd || '—' }}</a-descriptions-item>
        <a-descriptions-item label="来源变更请求" :span="2">
          <template v-if="request && request.code">
            <span class="ecn-code">{{ request.code }}</span>
            {{ request.name || '' }}
          </template>
          <span v-else class="ecn-muted">独立发起（无来源 ECR）</span>
        </a-descriptions-item>
        <a-descriptions-item label="变更原因" :span="2">
          <div class="ecn-pre">{{ notice.reason || '—' }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="建议方案" :span="2">
          <div class="ecn-pre">{{ notice.proposal || '—' }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="发起时的影响初判" :span="2">
          <a-space size="large" wrap>
            <span>认证/合规：{{ CERT_IMPACT_LABEL[notice.certImpact] || '未评估' }}</span>
            <span>客户/合同：{{ CUSTOMER_IMPACT_LABEL[notice.customerImpact] || '未评估' }}</span>
            <span>在制/库存：{{ INVENTORY_IMPACT_LABEL[notice.inventoryImpact] || '未评估' }}</span>
          </a-space>
        </a-descriptions-item>
      </a-descriptions>
    </div>

    <div v-if="affectedObjects.length" class="ecn-card">
      <div class="ecn-head">
        <span class="ecn-title">受影响对象</span>
        <span class="ecn-muted">共 {{ affectedObjects.length }} 项</span>
      </div>
      <a-table
        :columns="objectColumns"
        :data-source="affectedObjects"
        size="small"
        row-key="oid"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'target'">
            <ChangeObjectLink :record="record" class="ecn-code" />
            {{ record.entityName }}
          </template>
          <template v-else-if="column.key === 'container'">
            {{ record.containerName || '—' }}
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag v-if="record.executedAt" color="green">已执行</a-tag>
            <a-tag v-else color="default">待执行</a-tag>
          </template>
          <template v-else-if="column.key === 'versions'">
            <span class="ecn-version">{{ record.baseVersion || '—' }} → {{ record.currentResultVersion || record.resultVersion || '—' }}</span>
          </template>
          <template v-else-if="column.key === 'executor'">
            {{ record.executedBy || '—' }}
          </template>
          <template v-else-if="column.key === 'note'">
            {{ record.note || '—' }}
          </template>
        </template>
      </a-table>
    </div>

    <div v-if="tasks.length && !hideTasks" class="ecn-card">
      <div class="ecn-head">
        <span class="ecn-title">更改事项（执行清单）</span>
        <span class="ecn-muted">
          共 {{ tasks.length }} 项{{ detailed ? '' : ' · 审批通过后自动派发' }}
        </span>
      </div>
      <a-table
        :columns="taskColumns"
        :data-source="tasks"
        size="small"
        row-key="oid"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'task'">
            <span class="ecn-code">{{ record.code }}</span>
            {{ record.name || '—' }}
          </template>
          <template v-else-if="column.key === 'type'">
            {{ TASK_TYPE_LABEL[record.taskType] || '未分类' }}
          </template>
          <template v-else-if="column.key === 'plan'">
            {{ record.planStart || '—' }} ~ {{ record.planEnd || '—' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="CHANGE_TASK_STATUS_COLOR[record.status] || 'default'">
              {{ CHANGE_TASK_STATUS_LABEL[record.status] || record.status }}
            </a-tag>
            <!-- 完成时间只在真完成时才有：汇总确认的人要靠它判断"什么时候改完的" -->
            <div v-if="record.completedAt" class="ecn-muted ecn-time">{{ record.completedAt }}</div>
          </template>
          <template v-else-if="column.key === 'objects'">
            {{ taskObjectText(record) }}
          </template>
        </template>
      </a-table>
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getChangeNotice } from '@/api'
import ChangeObjectLink from '@/components/change/ChangeObjectLink.vue'
import {
  CERT_IMPACT_LABEL,
  CHANGE_LEVEL_COLOR,
  CHANGE_LEVEL_LABEL,
  CHANGE_NOTICE_STATUS_COLOR,
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
  CHANGE_TYPE_LABEL,
  CUSTOMER_IMPACT_LABEL,
  INVENTORY_IMPACT_LABEL,
  PRIORITY_LABEL,
  TASK_TYPE_LABEL
} from '@/utils/changeFlow'

/**
 * 变更通告的只读摘要 —— 变更通告的审批表 / 会签表共用的那一块。
 *
 * <p>审批人（部门审批、终审）和会签人（各专业）要先看清"这张通告到底要改什么、影响哪些对象、
 * 拆成了哪几个任务"，才谈得上给结论。写两份就会出现"审批表加了字段、会签表没跟"的偏差。
 *
 * <p>数据从流程变量 {@code noticeOid} 回查单据：ECN 发起审批时不写
 * {@code ck_process_entity_set}，{@code form.businessOid} 恒为空，不能靠它取业务对象。
 *
 * <p>取不到单据不让整张表崩：摘要降级为空，审批/会签本身照常能办完（见调用方的 collect）。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO），只用到 variables.noticeOid */
  form: { type: Object, default: null },
  /**
   * 汇总确认视图（变更实施汇总确认节点用）。
   *
   * <p>审批 / 会签阶段任务都还没跑完，"变更后版本、执行人、每个任务改了几个对象"这些列
   * 全是空值，只会让人以为漏填。所以默认关掉，只在汇总确认那里打开。
   */
  detailed: { type: Boolean, default: false },
  /**
   * 隐藏只读的「更改事项」卡（「变更任务确认与派发」用）。
   *
   * <p>那一节点要在同一页里给出可编辑的清单 —— 上面一块只读、下面一块可编辑，出现两份
   * 一样的表，办理人不知道该看哪份。留摘要 + 受影响对象，清单交给调用方的编辑器渲染。
   */
  hideTasks: { type: Boolean, default: false }
})

const loading = ref(false)
const loadError = ref('')
const notice = ref({})
const request = ref(null)
const affectedObjects = ref([])
const tasks = ref([])

const BASE_OBJECT_COLUMNS = [
  { key: 'target', title: '对象', dataIndex: 'entityName' },
  { key: 'container', title: '所属', dataIndex: 'containerName', width: 160 },
  { key: 'state', title: '执行状态', dataIndex: 'executedAt', width: 100 },
  { key: 'note', title: '备注', dataIndex: 'note', width: 140 }
]

/** 汇总确认才显示的列：变更前 → 变更后、谁执行的 */
const DETAILED_OBJECT_COLUMNS = [
  { key: 'versions', title: '变更前 → 变更后', width: 150 },
  { key: 'executor', title: '执行人', width: 110 }
]

const BASE_TASK_COLUMNS = [
  { key: 'task', title: '任务', dataIndex: 'name' },
  { key: 'type', title: '类型', dataIndex: 'taskType', width: 90 },
  { key: 'assignee', title: '责任人', dataIndex: 'assignee', width: 110 },
  { key: 'plan', title: '计划', dataIndex: 'planEnd', width: 180 },
  { key: 'status', title: '状态', dataIndex: 'status', width: 100 }
]

/** 汇总确认才显示：这个任务改了几个对象（已执行 / 总数） */
const DETAILED_TASK_COLUMNS = [
  { key: 'objects', title: '对象', width: 80 }
]

const objectColumns = computed(() =>
  props.detailed ? [...BASE_OBJECT_COLUMNS, ...DETAILED_OBJECT_COLUMNS] : BASE_OBJECT_COLUMNS)

const taskColumns = computed(() =>
  props.detailed ? [...BASE_TASK_COLUMNS, ...DETAILED_TASK_COLUMNS] : BASE_TASK_COLUMNS)

/**
 * 按任务聚合受影响对象 —— "这个任务改了什么、改完没有"。
 *
 * <p>受影响对象按 {@code taskOid} 归属到任务；未分配的（taskOid 为空）不进任何任务行，
 * 只在下面的对象清单里出现 —— 汇总确认本来就是要抓这种漏掉的。
 */
const taskObjectStats = computed(() => {
  const map = new Map()
  for (const item of affectedObjects.value) {
    const key = item.taskOid || ''
    if (!key) continue
    const stat = map.get(key) || { total: 0, executed: 0 }
    stat.total += 1
    if (item.executedAt) stat.executed += 1
    map.set(key, stat)
  }
  return map
})

function taskObjectText(record) {
  const stat = taskObjectStats.value.get(record.oid)
  if (!stat) return '—'
  return `${stat.executed}/${stat.total}`
}

const noticeOid = computed(() => {
  const value = props.form?.variables?.noticeOid
  return value ? String(value) : ''
})

async function load() {
  const oid = noticeOid.value
  if (!oid) {
    loadError.value = '取不到变更通告标识（流程变量 noticeOid 为空）'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getChangeNotice(oid)
    if (res?.code === 200 && res.data) {
      notice.value = res.data.notice || {}
      request.value = res.data.request || null
      affectedObjects.value = res.data.affectedObjects || []
      tasks.value = res.data.tasks || []
    } else {
      loadError.value = res?.message || '变更通告详情加载失败'
    }
  } catch {
    loadError.value = '变更通告详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

defineExpose({
  reload: load,
  /**
   * 已加载的数据快照 —— 汇总确认表单在 collect 时读它做前置校验
   * （还差哪些任务 / 哪些对象没执行）。返回函数而不是响应式对象：
   * 调用方拿到的一定是"提交这一刻"的值，不会读到挂载前的空数组。
   */
  snapshot: () => ({
    notice: notice.value,
    tasks: tasks.value,
    affectedObjects: affectedObjects.value
  })
})
</script>

<style scoped>
.ecn-card {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.ecn-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 10px;
}

.ecn-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.ecn-warn {
  font-size: 12px;
  color: #d46b08;
}

.ecn-muted {
  font-size: 12px;
  color: #8c8c8c;
}

/* 任务状态下方的完成时间：比正文小一号，别和状态标签抢注意力 */
.ecn-time {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.3;
}

/* 版本跃迁用等宽：A.2 → B.1 这类值对齐了才扫得快 */
.ecn-version {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  white-space: nowrap;
}

.ecn-pre {
  white-space: pre-wrap;
  line-height: 1.6;
}

.ecn-code {
  margin-right: 6px;
  font-family: 'Consolas', monospace;
  font-size: 12px;
  color: #595959;
}
</style>
