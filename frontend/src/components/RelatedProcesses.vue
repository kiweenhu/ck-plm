<template>
  <div class="related-processes">
    <!-- 工具条：一眼看清"在跑几个、跑完几个"，并可手动刷新 -->
    <div class="rp-toolbar">
      <a-space size="middle" class="rp-stats">
        <span class="rp-stat">
          <SyncOutlined class="rp-stat-icon rp-stat-icon-running" />
          正在执行中 <b>{{ runningList.length }}</b>
        </span>
        <span class="rp-stat">
          <CheckCircleOutlined class="rp-stat-icon" />
          已执行 <b>{{ finishedList.length }}</b>
        </span>
      </a-space>
      <a-button size="small" :loading="loading" @click="load">
        <ReloadOutlined /> 刷新
      </a-button>
    </div>

    <a-spin :spinning="loading">
      <a-row :gutter="16">
        <!-- ========== 左栏：正在执行中的流程 ========== -->
        <a-col :xs="24" :md="12">
          <a-card size="small" class="rp-card">
            <template #title>
              <span class="rp-card-title">
                <SyncOutlined class="rp-running-icon" /> 正在执行中的流程
              </span>
            </template>
            <template #extra>
              <a-tag color="processing" size="small">{{ runningList.length }}</a-tag>
            </template>

            <div v-if="runningList.length" class="rp-list">
              <div v-for="item in runningList" :key="item.id" class="rp-item rp-item-running">
                <div class="rp-item-head">
                  <span class="rp-item-name" @click="openDetail(item)">
                    {{ item.processDefinitionName || item.processDefinitionKey || '未命名流程' }}
                  </span>
                  <a-tag v-if="item.entityVersion" size="small" class="rp-ver-tag">版本 {{ item.entityVersion }}</a-tag>
                  <a-tag :color="statusColor(item.status)" size="small">{{ statusText(item.status) }}</a-tag>
                  <span class="rp-item-actions">
                    <a @click="toggleProgress(item)">{{ isExpanded(item.id) ? '收起进度' : '进度' }}</a>
                    <a @click="openDetail(item)">详情</a>
                  </span>
                </div>
                <div class="rp-item-meta">
                  发起人 {{ item.startUserId || '—' }} · 开始 {{ fmtTime(item.startTime) }}
                </div>
                <div class="rp-item-meta rp-item-current">
                  当前节点：{{ item.currentActivityName || '—' }}（{{ item.currentAssignees || '未指派' }}）
                </div>
                <!-- 节点经路：已办（责任人 / 活动类型 / 意见 / 实际走向）+ 在办 + 未开始 -->
                <ProcessActivityTimeline v-if="isExpanded(item.id)"
                  :activities="progressOf(item.id)" :loading="progressLoadingOf(item.id)" />
              </div>
            </div>
            <a-empty v-else description="暂无执行中的流程" :image-style="{ height: '48px' }" />
          </a-card>
        </a-col>

        <!-- ========== 右栏：已执行的流程 ========== -->
        <a-col :xs="24" :md="12">
          <a-card size="small" class="rp-card">
            <template #title>
              <span class="rp-card-title">
                <CheckCircleOutlined class="rp-done-icon" /> 已执行的流程
              </span>
            </template>
            <template #extra>
              <a-tag size="small">{{ finishedList.length }}</a-tag>
            </template>

            <div v-if="finishedList.length" class="rp-list">
              <div v-for="item in finishedList" :key="item.id" class="rp-item">
                <div class="rp-item-head">
                  <span class="rp-item-name" @click="openDetail(item)">
                    {{ item.processDefinitionName || item.processDefinitionKey || '未命名流程' }}
                  </span>
                  <a-tag v-if="item.entityVersion" size="small" class="rp-ver-tag">版本 {{ item.entityVersion }}</a-tag>
                  <a-tag :color="statusColor(item.status)" size="small">{{ statusText(item.status) }}</a-tag>
                  <span class="rp-item-actions">
                    <a @click="toggleProgress(item)">{{ isExpanded(item.id) ? '收起进度' : '进度' }}</a>
                    <a @click="openDetail(item)">详情</a>
                  </span>
                </div>
                <div class="rp-item-meta">
                  发起人 {{ item.startUserId || '—' }} · 开始 {{ fmtTime(item.startTime) }}
                </div>
                <div class="rp-item-meta">
                  结束 {{ fmtTime(item.endTime) }}
                  <span v-if="item.deleteReason" class="rp-item-reason">· {{ item.deleteReason }}</span>
                </div>
                <!-- 已结束的同样给经路：谁在哪一步点了同意/驳回，是这条流程的审计轨迹 -->
                <ProcessActivityTimeline v-if="isExpanded(item.id)"
                  :activities="progressOf(item.id)" :loading="progressLoadingOf(item.id)" />
              </div>
            </div>
            <a-empty v-else description="暂无已执行的流程" :image-style="{ height: '48px' }" />
          </a-card>
        </a-col>
      </a-row>
    </a-spin>

    <!-- 详情：列表行信息 + 流程变量（变量由详情接口单独取，列表接口不带以减少载荷） -->
    <a-modal v-model:visible="detailVisible" title="流程详情" :footer="null" width="720px">
      <a-spin :spinning="detailLoading">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="流程名称">
            {{ detail.processDefinitionName || detail.processDefinitionKey || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="流程 Key">{{ detail.processDefinitionKey || '—' }}</a-descriptions-item>
          <a-descriptions-item label="业务对象版本">{{ detail.entityVersion || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(detail.status)">{{ statusText(detail.status) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="发起人">{{ detail.startUserId || '—' }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ fmtTime(detail.startTime) }}</a-descriptions-item>
          <a-descriptions-item label="结束时间">{{ detail.endTime ? fmtTime(detail.endTime) : '—' }}</a-descriptions-item>
          <a-descriptions-item label="流程实例 id">
            <span class="rp-mono">{{ detail.id || '—' }}</span>
          </a-descriptions-item>
          <a-descriptions-item v-if="isRunning(detail)" label="当前节点" :span="2">
            {{ detail.currentActivityName || '—' }}（{{ detail.currentAssignees || '未指派' }}）
          </a-descriptions-item>
          <a-descriptions-item v-if="detail.deleteReason" label="结束原因" :span="2">
            {{ detail.deleteReason }}
          </a-descriptions-item>
        </a-descriptions>

        <div class="rp-vars">
          <div class="rp-vars-title">节点经路</div>
          <ProcessActivityTimeline :activities="progressOf(detail.id)"
            :loading="progressLoadingOf(detail.id)" />
        </div>

        <div v-if="variableRows.length" class="rp-vars">
          <div class="rp-vars-title">流程变量</div>
          <a-table size="small" :columns="variableColumns" :data-source="variableRows"
            :pagination="false" row-key="name" />
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import {
  CheckCircleOutlined, ReloadOutlined, SyncOutlined,
} from '@ant-design/icons-vue'
import { getEntityProcesses, getInstanceActivities, getProcessInstanceDetail } from '@/api'
import ProcessActivityTimeline from '@/components/ProcessActivityTimeline.vue'

/**
 * 关联流程面板 —— 所有业务对象详情页共用（两栏：正在执行中 / 已执行）。
 *
 * <p>数据源是后端的"实例 ↔ 业务实体"关联表（ck_process_entity_set），因此它回答的是
 * "这个对象**参与过**哪些流程"，而不是"谁发起的"——换个人看同一个对象，看到的是同一份事实。
 *
 * <p>刻意不传 entityVersion 时按**该对象全部大版本**查，每行带版本标签：大版本之间是两轮工作，
 * 把 A 版的历史藏起来反而让人以为"这对象从没走过流程"。
 * 需要只看某一版时（例如从版本历史进的页面）传 `entity-version` 即可。
 */
const props = defineProps({
  /** 业务对象主 oid（必填） */
  entityOid: { type: String, required: true },
  /** 业务对象大版本（可空 = 全部大版本） */
  entityVersion: { type: String, default: '' },
})

const loading = ref(false)
const list = ref([])

/** 已展开经路的实例 id（执行中的默认展开：点开这个页签就是想看"走到哪了"） */
const expandedIds = ref([])
/** 实例 id → { loading, list }：经路按需拉取（列表接口不带，避免每次列表都翻一遍引擎历史） */
const progressMap = ref({})

const runningList = computed(() => list.value.filter(isRunning))
const finishedList = computed(() => list.value.filter(item => !isRunning(item)))

function isRunning(item) {
  return item?.status === 'running' || item?.status === 'suspended'
}

function statusColor(status) {
  return status === 'running' ? 'processing'
    : status === 'suspended' ? 'warning'
      : status === 'completed' ? 'success' : 'error'
}

function statusText(status) {
  return status === 'running' ? '执行中'
    : status === 'suspended' ? '已挂起'
      : status === 'completed' ? '已完成' : '已终止'
}

function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('zh-CN', { hour12: false })
}

// ==================== 节点经路（走到哪了） ====================

function isExpanded(instanceId) {
  return expandedIds.value.includes(instanceId)
}

function progressOf(instanceId) {
  return (instanceId && progressMap.value[instanceId]?.list) || []
}

function progressLoadingOf(instanceId) {
  return !!(instanceId && progressMap.value[instanceId]?.loading)
}

async function loadProgress(instanceId) {
  progressMap.value = {
    ...progressMap.value,
    [instanceId]: { loading: true, list: progressOf(instanceId) },
  }
  let list = []
  try {
    const res = await getInstanceActivities(instanceId)
    list = (res?.code === 200 && Array.isArray(res.data)) ? res.data : []
  } catch {
    list = []
  }
  progressMap.value = { ...progressMap.value, [instanceId]: { loading: false, list } }
}

function toggleProgress(item) {
  if (!item?.id) return
  if (isExpanded(item.id)) {
    expandedIds.value = expandedIds.value.filter(id => id !== item.id)
    return
  }
  expandedIds.value = [...expandedIds.value, item.id]
  if (!progressOf(item.id).length) loadProgress(item.id)
}

async function load() {
  if (!props.entityOid) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const res = await getEntityProcesses({
      entityOid: props.entityOid,
      entityVersion: props.entityVersion || undefined,
    })
    list.value = (res?.code === 200 && Array.isArray(res.data)) ? res.data : []
    // 执行中的流程默认展开经路：用户点开这个页签，第一眼要看到"走到哪、还剩哪几步"
    const runningIds = runningList.value.map(item => item.id)
    expandedIds.value = runningIds
    runningIds.forEach(id => loadProgress(id))
  } catch {
    // 失败原因由响应拦截器统一提示；面板退化为空列表，不挡住详情页其余内容
    list.value = []
  } finally {
    loading.value = false
  }
}

// ==================== 详情 ====================

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})

const variableColumns = [
  { title: '变量', dataIndex: 'name', width: '38%' },
  { title: '值', dataIndex: 'value' },
]

const variableRows = computed(() =>
  Object.entries(detail.value?.variables || {}).map(([name, value]) => ({
    name,
    value: fmtVariable(value),
  })))

function fmtVariable(value) {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') {
    try { return JSON.stringify(value) } catch { return String(value) }
  }
  return String(value)
}

async function openDetail(item) {
  // 先用列表行已有信息铺满（点开即见），再取详情补流程变量
  detail.value = { ...item }
  detailVisible.value = true
  if (item?.id && !progressOf(item.id).length) {
    loadProgress(item.id)
  }
  detailLoading.value = true
  try {
    const res = await getProcessInstanceDetail(item.id)
    if (res?.code === 200 && res.data) {
      detail.value = res.data
    }
  } catch {
    /* 取不到就保留列表行信息，不弹错 */
  } finally {
    detailLoading.value = false
  }
}

watch(() => [props.entityOid, props.entityVersion], load)
onMounted(load)

defineExpose({ reload: load })
</script>

<style scoped>
.related-processes {
  padding: 0;
}

.rp-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.rp-stats {
  color: #595959;
  font-size: 13px;
}

.rp-stat b {
  color: #262626;
  font-size: 15px;
  margin-left: 2px;
}

.rp-stat-icon {
  margin-right: 4px;
  color: #8c8c8c;
}

.rp-stat-icon-running {
  color: #1677ff;
}

.rp-card {
  height: 100%;
}

.rp-card-title {
  font-size: 14px;
}

.rp-running-icon {
  color: #1677ff;
  margin-right: 4px;
}

.rp-done-icon {
  color: #8c8c8c;
  margin-right: 4px;
}

.rp-list {
  display: flex;
  flex-direction: column;
}

.rp-item {
  padding: 10px 12px;
  border-radius: 6px;
  background: #fafafa;
  border-left: 3px solid #d9d9d9;
  margin-bottom: 10px;
}

.rp-item:last-child {
  margin-bottom: 0;
}

/* 流程行本身只保留蓝边（表示"这条在跑"），不给整行蓝底 ——
   蓝底留给经路里"当前在办"的那个节点，否则两种状态会糊在一起 */
.rp-item-running {
  background: #f8f8f8;
  border-left-color: #1677ff;
}

.rp-item-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.rp-item-name {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
  cursor: pointer;
}

.rp-item-name:hover {
  color: #1677ff;
}

.rp-ver-tag {
  background: #fff;
}

.rp-item-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
  font-size: 12px;
}

.rp-item-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}

.rp-item-current {
  color: #595959;
}

.rp-item-reason {
  margin-left: 4px;
}

.rp-vars {
  margin-top: 16px;
}

.rp-vars-title {
  margin-bottom: 8px;
  font-weight: 500;
  color: #262626;
}

.rp-mono {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}
</style>
