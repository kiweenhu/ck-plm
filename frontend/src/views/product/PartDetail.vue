<template>
  <div class="part-detail">
    <!-- 顶部标题栏 -->
    <div class="pd-header">
      <div class="pd-title">
        <h2 class="pd-name">{{ part?.name || '零组件详情' }}</h2>
        <div class="pd-meta">
          <code class="pd-code">{{ part?.number || '-' }}</code>
          <a-tag color="blue">版本 {{ part?.displayVersion || '-' }}</a-tag>
          <a-tag :color="statusColor(part?.statusCode)">{{ part?.statusName || part?.statusCode || '-' }}</a-tag>
          <a-tag v-if="part?.checkedOut" color="orange">已检出: {{ part.checkedOutBy }}</a-tag>
          <a-tag v-else color="green">已检入</a-tag>
        </div>
      </div>
      <a-button @click="goBack" style="margin-left:auto">
        <ArrowLeftOutlined /> 返回
      </a-button>
    </div>

    <a-spin :spinning="loading" tip="加载零组件详情...">
      <a-card title="基本信息" size="small" :bordered="false" style="margin-bottom:16px">
        <a-descriptions :column="3" bordered size="small">
          <a-descriptions-item label="名称">{{ part?.name || '-' }}</a-descriptions-item>
          <a-descriptions-item label="编码">{{ part?.number || '-' }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ part?.typeDefinitionCode || '-' }}</a-descriptions-item>
          <a-descriptions-item label="描述" :span="3">{{ part?.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="单位">{{ part?.unit || '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ part?.source || '-' }}</a-descriptions-item>
          <a-descriptions-item label="视图">{{ part?.view || '-' }}</a-descriptions-item>
          <a-descriptions-item label="大版本">{{ part?.revision || '-' }}</a-descriptions-item>
          <a-descriptions-item label="小版本">{{ part?.iteration != null ? part.iteration : '-' }}</a-descriptions-item>
          <a-descriptions-item label="生命周期">
            <a-tag :color="statusColor(part?.statusCode)" size="small">{{ part?.statusName || part?.statusCode || '-' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="检出状态">
            <a-tag v-if="part?.checkedOut" color="orange" size="small">已检出: {{ part.checkedOutBy }}</a-tag>
            <a-tag v-else color="green" size="small">已检入</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="检出注释">{{ part?.checkedOutComment || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建人">{{ part?.creator || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ fmtTime(part?.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="更新人">{{ part?.updater || '-' }}</a-descriptions-item>
        </a-descriptions>
      </a-card>

      <!-- IBA 属性 -->
      <a-card title="IBA 属性" size="small" :bordered="false" style="margin-bottom:16px">
        <a-empty v-if="ibaEntries.length === 0" description="暂无 IBA 属性" :image-style="{ height: '32px' }" />
        <a-descriptions v-else :column="3" bordered size="small">
          <a-descriptions-item v-for="(item, idx) in ibaEntries" :key="idx" :label="item.label">
            {{ item.value }}
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <!-- 历史版本 -->
      <a-card title="历史版本" size="small" :bordered="false">
        <a-table
          :columns="historyColumns"
          :data-source="historyList"
          :loading="historyLoading"
          :pagination="false"
          row-key="oid"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'version'">
              <span style="font-weight:500">{{ record.displayVersion || (record.revision ? `${record.revision}.${record.iteration}` : '-') }}</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status?.code)" size="small">
                {{ record.status?.displayName || record.status?.code || '-' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'checkedOut'">
              <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
              <a-tag v-else color="green" size="small">已检入</a-tag>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              <span style="font-size:12px;color:#8c8c8c">{{ fmtTime(record.createdAt) }}</span>
            </template>
          </template>
        </a-table>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { getEntityByCode, getPartIterations } from '@/api'

const route = useRoute()
const router = useRouter()

const oid = computed(() => route.params.oid)

const loading = ref(false)
const part = ref(null)

const historyLoading = ref(false)
const historyList = ref([])

const historyColumns = [
  { title: '版本', key: 'version', width: 100 },
  { title: '大版本', dataIndex: 'revision', key: 'revision', width: 90 },
  { title: '小版本', dataIndex: 'iteration', key: 'iteration', width: 90 },
  { title: '生命周期状态', key: 'status', width: 140 },
  { title: '检出状态', key: 'checkedOut', width: 150 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 100 },
  { title: '来源', dataIndex: 'source', key: 'source', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 170 },
]

/** 固定字段（不视为 IBA 属性） */
const FIXED_FIELDS = new Set([
  'oid', 'name', 'number', 'description', 'typeDefinitionCode',
  'containerOid', 'containerType', 'folderOid', 'stageOid', 'clsOid',
  'tenantOid', 'creator', 'createdAt', 'updater', 'updatedAt',
  'revision', 'iteration', 'displayVersion', 'checkedOut', 'checkedOutBy', 'checkedOutComment',
  'unit', 'source', 'view', 'statusCode', 'statusName',
  'new', 'persisted',
])

/** 提取 IBA 属性（排除固定字段） */
const ibaEntries = computed(() => {
  if (!part.value) return []
  return Object.entries(part.value)
    .filter(([k, v]) => !FIXED_FIELDS.has(k) && v !== null && v !== undefined && v !== '')
    .map(([k, v]) => ({ label: k, value: formatIbaValue(v) }))
})

function formatIbaValue(v) {
  if (v == null) return ''
  if (typeof v === 'string') return v
  try { return JSON.stringify(v) } catch { return String(v) }
}

function statusColor(code) {
  const map = { DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning', APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error' }
  return map[code] || 'default'
}

function fmtTime(v) {
  return v ? String(v).substring(0, 19).replace('T', ' ') : '-'
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    window.close()
  }
}

async function loadDetail() {
  if (!oid.value) return
  loading.value = true
  try {
    const res = await getEntityByCode('PART', oid.value)
    if (res.code === 200 && res.data) {
      part.value = res.data
    }
  } catch {
    part.value = null
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!oid.value) return
  historyLoading.value = true
  try {
    const res = await getPartIterations(oid.value)
    const list = res?.data || res || []
    historyList.value = (Array.isArray(list) ? list : []).map(it => ({
      ...it,
      displayVersion: it.displayVersion || (it.revision ? `${it.revision}.${it.iteration}` : '-'),
    }))
  } catch {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  loadDetail()
  loadHistory()
})
</script>

<style scoped>
.part-detail {
  padding: 16px;
  background: #f5f5f5;
  min-height: 100vh;
}
.pd-header {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 8px;
  padding: 16px 24px;
  margin-bottom: 16px;
}
.pd-name {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
}
.pd-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.pd-code {
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
}
</style>
