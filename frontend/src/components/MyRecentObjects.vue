<template>
  <a-card size="small" class="recent-card">
    <template #title>
      <HistoryOutlined class="recent-title-icon" />
      我最近创建/修改的对象
      <a-tag v-if="objects.length" color="blue" size="small" class="recent-title-tag">{{ objects.length }}</a-tag>
    </template>
    <template #extra>
      <!-- 时间窗口可切：库里最新动作可能是十几天前，固定 5 天会让人以为功能坏了 -->
      <a-radio-group v-model:value="days" size="small" button-style="solid" @change="load">
        <a-radio-button :value="5">近 5 天</a-radio-button>
        <a-radio-button :value="30">近 30 天</a-radio-button>
      </a-radio-group>
    </template>

    <a-table
      :columns="columns"
      :data-source="objects"
      :loading="loading"
      :pagination="false"
      row-key="oid"
      size="small"
      :locale="{ emptyText: `近 ${days} 天没有你创建或修改的对象` }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'object'">
          <div class="recent-object">
            <span class="recent-object-name">
              <a v-if="record.linkPath" @click="open(record)">{{ record.name || record.oid }}</a>
              <span v-else>{{ record.name || record.oid }}</span>
            </span>
            <span class="recent-object-sub">
              <a-tag size="small" :color="typeColor(record.entityType)">
                {{ record.entityTypeName || record.entityType }}
              </a-tag>
              <span class="recent-object-code">{{ record.code || '—' }}</span>
              <span v-if="record.displayVersion" class="recent-object-ver">版本 {{ record.displayVersion }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'touchType'">
          <a-tag :color="touchColor(record.touchType)" size="small">{{ touchText(record.touchType) }}</a-tag>
        </template>

        <template v-else-if="column.key === 'touchedAt'">
          {{ fmtTime(record.touchedAt) }}
          <span class="recent-relative">{{ relative(record.touchedAt) }}</span>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { HistoryOutlined } from '@ant-design/icons-vue'
import { getMyRecentObjects } from '@/api'

/**
 * 「我最近创建/修改的对象」卡片 —— 个人中心用。
 *
 * <p>数据来自对象表自身的创建/修改人 + 时间戳（后端已按当前用户与租户过滤），
 * 是<b>最后状态口径</b>：它回答"这些对象最近一次是我动的"，不回答"历史上我改过它"。
 */
const props = defineProps({
  /** 最多列几条 */
  limit: { type: Number, default: 20 },
})

const router = useRouter()
const days = ref(5)
const loading = ref(false)
const objects = ref([])

// 列宽按"个人中心左栏 3/5"配（相对时间换行显示，不挤占宽度），
// 时间列另起一行放"x 天前"
const columns = [
  { title: '对象', key: 'object' },
  { title: '变更', key: 'touchType', width: 95 },
  { title: '时间', key: 'touchedAt', width: 175 },
]

async function load() {
  loading.value = true
  try {
    const res = await getMyRecentObjects({ days: days.value, limit: props.limit })
    objects.value = (res?.code === 200 && Array.isArray(res.data)) ? res.data : []
  } catch {
    objects.value = []
  } finally {
    loading.value = false
  }
}

function open(record) {
  if (record.linkPath) router.push(record.linkPath)
}

function typeColor(type) {
  const map = { PART: 'purple', DOCUMENT: 'blue', ENG_DOCUMENT: 'cyan' }
  return map[type] || 'default'
}

function touchText(type) {
  return type === 'CREATED' ? '创建' : type === 'UPDATED' ? '修改' : '创建后修改'
}

function touchColor(type) {
  return type === 'CREATED' ? 'green' : 'blue'
}

/** 精确到分钟：卡片里空间宝贵，"8 天前"由 relative() 另起一行给 */
function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t).substring(0, 16).replace('T', ' ')
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 相对时间：一眼看出"多久没动了"比绝对时间更有用 */
function relative(t) {
  if (!t) return ''
  const diff = Date.now() - new Date(t).getTime()
  if (!Number.isFinite(diff) || diff < 0) return ''
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '（刚刚）'
  if (minutes < 60) return `（${minutes} 分钟前）`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `（${hours} 小时前）`
  return `（${Math.floor(hours / 24)} 天前）`
}

onMounted(load)

defineExpose({ reload: load })
</script>

<style scoped>
.recent-card {
  border-radius: 8px;
}

.recent-title-icon {
  color: #1677ff;
  margin-right: 4px;
}

.recent-title-tag {
  margin-left: 8px;
}

.recent-object {
  display: flex;
  flex-direction: column;
  line-height: 1.6;
}

.recent-object-name {
  font-size: 13px;
  color: #262626;
}

.recent-object-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.recent-object-code {
  font-family: Consolas, Menlo, monospace;
}

.recent-relative {
  margin-left: 6px;
  color: #bfbfbf;
}
</style>
