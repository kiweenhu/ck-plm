<template>
  <div class="notif-page">
    <a-card size="small" class="notif-card">
      <template #title>
        <SoundOutlined class="card-title-icon" />{{ pageTitle }}
      </template>
      <template #extra>
        <a-space>
          <a-button v-if="isAdmin" type="primary" size="small" @click="openAnnounce">
            <SoundOutlined /> 发布公告
          </a-button>
          <a-button size="small" :disabled="unreadCount === 0" @click="markAll">全部已读</a-button>
          <a-button size="small" :loading="loading" @click="load">
            <ReloadOutlined />
          </a-button>
        </a-space>
      </template>

      <a-radio-group v-model:value="unreadOnly" button-style="solid" size="small" class="notif-filter"
        @change="load">
        <a-radio-button :value="false">全部</a-radio-button>
        <a-radio-button :value="true">未读{{ unreadCount ? ` (${unreadCount})` : '' }}</a-radio-button>
      </a-radio-group>

      <a-spin :spinning="loading">
        <div v-if="list.length" class="notif-list">
          <div v-for="item in list" :key="item.oid" class="notif-row" :class="{ unread: !item.isRead }"
            @click="open(item)">
            <component :is="meta(item.type).icon" class="notif-icon"
              :style="{ color: iconColor(item.type) }" />

            <div class="notif-main">
              <div class="notif-head">
                <span v-if="!item.isRead" class="notif-unread-dot"></span>
                <span class="notif-title">{{ item.title }}</span>
                <a-tag size="small" :color="meta(item.type).color">{{ meta(item.type).label }}</a-tag>
                <a-tooltip :title="fullTime(item.createdAt)">
                  <span class="notif-time">{{ relativeTime(item.createdAt) }}</span>
                </a-tooltip>
              </div>
              <div v-if="item.content" class="notif-content">{{ item.content }}</div>
            </div>

            <div class="notif-actions" @click.stop>
              <a v-if="notifLink(item)" @click="open(item)">查看</a>
              <a v-if="!item.isRead" @click="read(item)">标为已读</a>
            </div>
          </div>
        </div>
        <a-empty v-else :image-style="{ height: '64px' }"
          :description="unreadOnly
            ? (isAnnouncementPage ? '没有未读公告' : '没有未读通知')
            : (isAnnouncementPage ? '本企业还没有发布过公告' : '暂无通知')" />
      </a-spin>
    </a-card>

    <!-- 发布公告：发给本租户全部启用用户 -->
    <a-modal v-model:visible="announceVisible" title="发布公告" :confirm-loading="publishing"
      ok-text="发布" @ok="publish" width="560px">
      <a-alert type="info" show-icon class="announce-tip"
        message="公告将发送给本企业的全部启用用户，出现在他们的通知中心与右上角铃铛里。" />
      <a-form layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model:value="form.title" :maxlength="60" show-count
            placeholder="如：9 月 20 日 20:00 系统维护通知" />
        </a-form-item>
        <a-form-item label="内容">
          <a-textarea v-model:value="form.content" :rows="4" :maxlength="500" show-count
            placeholder="写清时间、影响范围，以及需要用户做什么" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SoundOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getNotifications, getUnreadCount, markAllNotifRead, markNotifRead, publishAnnouncement,
} from '@/api'
import { notifTypeMeta, notifLink, notifRelativeTime, notifFullTime, emitNotifChanged } from '@/utils/notifications'

/**
 * 通知中心 / 公告。
 *
 * <p>与右上角铃铛是同一份数据（同一个接口、同一套类型元信息），区别只是这里能一次看全、
 * 能筛未读、管理员还能发公告 —— 以前通知只能在下拉里看 8 条，"查看全部"却跳到租户审核页。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/**
 * 本页是"通知中心"还是"企业公告"由路由 meta 决定 —— 同一个组件两处入口，
 * 不再为公告另写一套列表（那种复制迟早会漂移）。
 */
const typeFilter = computed(() => route.meta?.notificationType || null)
const pageTitle = computed(() => (typeFilter.value ? '企业公告' : '通知公告'))
const isAnnouncementPage = computed(() => typeFilter.value === 'ANNOUNCEMENT')

const loading = ref(false)
const list = ref([])
const unreadCount = ref(0)
const unreadOnly = ref(false)

/** 能发公告的人：租户管理员 / 平台管理员（服务端仍会再校验一次，这里只管按钮显示） */
const isAdmin = computed(() => (userStore.roles || [])
  .some(role => ['TENANT_ADMIN', 'PLATFORM_ADMIN', 'ADMIN'].includes(role)))

const ICON_COLORS = {
  TASK: '#1677ff',
  TASK_DELEGATE: '#fa8c16',
  TASK_TRANSFER: '#fa8c16',
  PROCESS_RESULT: '#52c41a',
  ANNOUNCEMENT: '#faad14',
  TENANT_REGISTRATION: '#722ed1',
}

const meta = notifTypeMeta
const relativeTime = notifRelativeTime
const fullTime = notifFullTime

function iconColor(type) {
  return ICON_COLORS[type] || '#8c8c8c'
}

async function load() {
  loading.value = true
  try {
    const [listRes, countRes] = await Promise.all([
      getNotifications(50, unreadOnly.value, typeFilter.value || undefined),
      getUnreadCount(),
    ])
    list.value = (listRes?.code === 200 && Array.isArray(listRes.data)) ? listRes.data : []
    unreadCount.value = (countRes?.code === 200 && countRes.data) || 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function read(item) {
  if (item.isRead) return
  await markNotifRead(item.oid)
  item.isRead = true
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  emitNotifChanged()
  if (unreadOnly.value) await load()
}

/** 点击一条：先标记已读，再按 targetType 跳过去（跳转表在 utils/notifications） */
async function open(item) {
  if (!item.isRead) {
    try { await markNotifRead(item.oid) } catch { /* 标记失败不挡跳转 */ }
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (unreadOnly.value) { await load(); return }
  }
  const path = notifLink(item)
  if (path) router.push(path)
}

async function markAll() {
  await markAllNotifRead()
  unreadCount.value = 0
  emitNotifChanged()
  await load()
}

// ---- 发布公告 ----

const announceVisible = ref(false)
const publishing = ref(false)
const form = reactive({ title: '', content: '' })

function openAnnounce() {
  form.title = ''
  form.content = ''
  announceVisible.value = true
}

async function publish() {
  if (!form.title.trim()) {
    message.warning('请填写公告标题')
    return
  }
  publishing.value = true
  try {
    const res = await publishAnnouncement({ title: form.title.trim(), content: form.content.trim() })
    if (res?.code === 200) {
      message.success(`公告已送达 ${res.data ?? 0} 人`)
      announceVisible.value = false
      unreadOnly.value = false
      // 发布后立刻让右上角铃铛亮起来（否则要等 30 秒轮询，看着像"没通知"）
      emitNotifChanged()
      await load()
    }
    // 失败原因由响应拦截器统一提示（含"只有管理员可以发布"）
  } finally {
    publishing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.notif-card {
  border-radius: 8px;
}

.card-title-icon {
  color: #1677ff;
  margin-right: 4px;
}

.notif-filter {
  margin-bottom: 12px;
}

.notif-list {
  display: flex;
  flex-direction: column;
}

.notif-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.notif-row:hover {
  background: #fafafa;
}

.notif-row.unread {
  background: #f0f7ff;
}

.notif-icon {
  font-size: 16px;
  margin-top: 2px;
}

.notif-main {
  flex: 1 1 auto;
  min-width: 0;
}

.notif-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notif-unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1677ff;
  flex: 0 0 auto;
}

.notif-title {
  font-size: 14px;
  color: #1f1f1f;
}

.notif-row.unread .notif-title {
  font-weight: 600;
}

.notif-time {
  margin-left: auto;
  font-size: 12px;
  color: #bfbfbf;
  white-space: nowrap;
}

.notif-content {
  margin-top: 4px;
  font-size: 13px;
  color: #595959;
  line-height: 1.6;
  word-break: break-word;
}

.notif-actions {
  flex: 0 0 auto;
  display: flex;
  gap: 10px;
  font-size: 12px;
  margin-top: 2px;
}

.announce-tip {
  margin-bottom: 12px;
}
</style>
