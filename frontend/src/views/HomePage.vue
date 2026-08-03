<template>
  <div class="home-page">
    <!-- 欢迎区 -->
    <div class="welcome-row">
      <div class="welcome-text">
        <h2>你好，{{ userStore.userDisplayName }}</h2>
        <span>{{ currentDate }}</span>
      </div>
    </div>

    <!-- 两栏布局：左列(最近访问+我的检出) + 右列(最近操作) -->
    <a-row :gutter="16" class="main-row">
      <a-col :span="12">
        <a-card title="最近访问" size="small" class="section-card">
          <template #extra>
            <a-button type="link" size="small">查看全部</a-button>
          </template>
          <a-table
            :columns="accessColumns"
            :data-source="recentAccess"
            :pagination="false"
            size="small"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'name'">
                <a @click="handleClickAccess(record)">{{ record.name }}</a>
              </template>
              <template v-if="column.key === 'type'">
                <a-tag :color="record.typeColor" size="small">{{ record.type }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>

        <a-card size="small" class="section-card" style="margin-top:8px">
          <template #title>
            <LockOutlined style="color:#fa8c16;margin-right:4px" />我的检出
            <a-tag v-if="myCheckouts.length > 0" color="orange" size="small" style="margin-left:8px">{{ myCheckouts.length }}</a-tag>
          </template>
          <a-table
            :columns="checkoutColumns"
            :data-source="myCheckouts"
            :pagination="false"
            size="small"
            row-key="oid"
            :loading="checkoutLoading"
            :locale="{ emptyText: '暂无检出对象' }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'name'">
                <a @click="goToEntity(record)">{{ record.name || '-' }}</a>
              </template>
              <template v-else-if="column.key === 'entityType'">
                <a-tag :color="entityTypeColor(record.entityType)" size="small">{{ record.entityTypeName || record.entityType }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>

      <a-col :span="12">
        <a-card title="最近操作" size="small" class="section-card">
          <template #extra>
            <a-button type="link" size="small" @click="$router.push('/system/log')">全部日志</a-button>
          </template>
          <a-timeline class="operation-timeline">
            <a-timeline-item
              v-for="item in recentOperations.slice(0, 6)"
              :key="item.id"
              :color="item.color"
            >
              <div class="timeline-row">
                <span class="timeline-action">{{ item.action }}</span>
                <span class="timeline-target">{{ item.target }}</span>
              </div>
              <div class="timeline-time">{{ item.displayTime }}</div>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import { LockOutlined } from '@ant-design/icons-vue'
import { getRecentAccess, getRecentOperations, recordAccess, fetchFromServer } from '@/composables/useActivity'
import { getMyCheckouts } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const currentDate = computed(() => {
  const now = new Date()
  const weekMap = ['日', '一', '二', '三', '四', '五', '六']
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${weekMap[now.getDay()]}`
})

const accessColumns = [
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '类型', dataIndex: 'type', key: 'type', width: 70 },
  { title: '时间', dataIndex: 'displayTime', key: 'time', width: 130 }
]

const recentAccess = computed(() => getRecentAccess())
const recentOperations = computed(() => getRecentOperations())

const handleClickAccess = (record) => {
  if (record.path) router.push(record.path)
  else message.info(`跳转到 ${record.type}: ${record.name}`)
}

const checkoutLoading = ref(false)
const myCheckouts = ref([])

const checkoutColumns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '类型', dataIndex: 'entityType', key: 'entityType', width: 80 },
  { title: '检出时间', dataIndex: 'checkedOutAt', key: 'checkedOutAt', width: 150 }
]

function entityTypeColor(type) {
  const map = { DOCUMENT: 'blue', PART: 'purple' }
  return map[type] || 'default'
}

function goToEntity(record) {
  if (record.linkPath) router.push(record.linkPath)
  else message.info(`尚未配置跳转: ${record.name}`)
}

async function loadMyCheckouts() {
  checkoutLoading.value = true
  try {
    const res = await getMyCheckouts()
    if (res.code === 200) {
      myCheckouts.value = (res.data || []).map(c => ({
        ...c,
        checkedOutAt: c.checkedOutAt ? c.checkedOutAt.substring(0, 19).replace('T', ' ') : ''
      }))
    }
  } catch { myCheckouts.value = [] }
  finally { checkoutLoading.value = false }
}

onMounted(async () => {
  await fetchFromServer()
  recordAccess({ name: '个人中心', type: '系统', path: '/home' })
  loadMyCheckouts()
})
</script>

<style scoped>
.home-page {
  max-width: 100%;
}

/* 欢迎区 */
.welcome-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 20px;
  background: linear-gradient(135deg, #f0f5ff 0%, #e6f4ff 50%, #f6ffed 100%);
  border-radius: 8px;
  border: 1px solid #e6f0ff;
}

.welcome-text h2 {
  margin: 0 0 2px;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.welcome-text span {
  font-size: 12px;
  color: #8c8c8c;
}

/* 卡片 */
.main-row {
  margin-bottom: 0;
}

.section-card {
  border-radius: 8px;
}

/* 时间线 */
.operation-timeline {
  margin-top: 4px;
}

.timeline-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.timeline-action {
  color: #595959;
  font-size: 13px;
}

.timeline-target {
  color: #1677ff;
  font-size: 12px;
}

.timeline-time {
  font-size: 11px;
  color: #bfbfbf;
  margin-top: 1px;
}

</style>
