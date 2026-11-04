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
            <span class="ecn-code">{{ record.entityCode }}</span>
            {{ record.entityName }}
          </template>
          <template v-else-if="column.key === 'container'">
            {{ record.containerName || '—' }}
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag v-if="record.executedAt" color="green">已执行</a-tag>
            <a-tag v-else color="default">待执行</a-tag>
          </template>
          <template v-else-if="column.key === 'note'">
            {{ record.note || '—' }}
          </template>
        </template>
      </a-table>
    </div>

    <div v-if="tasks.length" class="ecn-card">
      <div class="ecn-head">
        <span class="ecn-title">更改事项（执行清单）</span>
        <span class="ecn-muted">共 {{ tasks.length }} 项 · 审批通过后自动派发</span>
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
          </template>
        </template>
      </a-table>
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getChangeNotice } from '@/api'
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
  form: { type: Object, default: null }
})

const loading = ref(false)
const loadError = ref('')
const notice = ref({})
const request = ref(null)
const affectedObjects = ref([])
const tasks = ref([])

const objectColumns = [
  { key: 'target', title: '对象', dataIndex: 'entityName' },
  { key: 'container', title: '所属', dataIndex: 'containerName', width: 180 },
  { key: 'state', title: '执行状态', dataIndex: 'executedAt', width: 100 },
  { key: 'note', title: '备注', dataIndex: 'note', width: 160 }
]

const taskColumns = [
  { key: 'task', title: '任务', dataIndex: 'name' },
  { key: 'type', title: '类型', dataIndex: 'taskType', width: 90 },
  { key: 'assignee', title: '责任人', dataIndex: 'assignee', width: 120 },
  { key: 'plan', title: '计划', dataIndex: 'planEnd', width: 190 },
  { key: 'status', title: '状态', dataIndex: 'status', width: 100 }
]

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

defineExpose({ reload: load })
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
