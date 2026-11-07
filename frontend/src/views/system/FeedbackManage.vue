<template>
  <div class="feedback-manage">
    <!-- 非平台管理员：无权限提示 -->
    <a-result
      v-if="!isPlatformAdmin"
      status="403"
      title="无访问权限"
      sub-title="仅平台管理员可查看和处理各租户用户提交的反馈"
    >
      <template #extra>
        <a-button type="primary" @click="$router.push('/home')">返回首页</a-button>
      </template>
    </a-result>

    <template v-else>
      <a-card :bordered="false">
        <template #title>
          <span>用户反馈</span>
          <a-tag v-if="unprocessed > 0" color="red" style="margin-left: 8px">
            {{ unprocessed }} 条待处理
          </a-tag>
        </template>
        <template #extra>
          <a-space>
            <a-radio-group v-model:value="statusFilter" button-style="solid" @change="fetchList">
              <a-radio-button value="">全部</a-radio-button>
              <a-radio-button value="NEW">待处理</a-radio-button>
              <a-radio-button value="DONE">已回复</a-radio-button>
            </a-radio-group>
            <a-button :loading="loading" @click="fetchList">
              <template #icon><reload-outlined /></template>
              刷新
            </a-button>
          </a-space>
        </template>

        <a-table
          :columns="columns"
          :data-source="list"
          :loading="loading"
          :pagination="{ pageSize: 15, showTotal: (t) => `共 ${t} 条` }"
          row-key="oid"
          size="middle"
          :scroll="{ x: 'max-content' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'createdAt'">
              <span class="time-text">{{ formatTime(record.createdAt) }}</span>
            </template>
            <template v-else-if="column.key === 'category'">
              <a-tag :color="categoryMeta(record.category).color" size="small">
                {{ categoryMeta(record.category).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'tenant'">
              {{ record.tenantName || '平台' }}
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusMeta(record.status).color" size="small">
                {{ statusMeta(record.status).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button
                size="small"
                :type="record.status === 'NEW' ? 'primary' : undefined"
                @click="openDetail(record.oid)"
              >
                {{ record.status === 'NEW' ? '查看并回复' : '查看' }}
              </a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 详情 + 回复 -->
      <a-drawer v-model:open="drawerOpen" title="反馈详情" :width="760" :mask-closable="false">
        <a-spin :spinning="detailLoading">
          <template v-if="detail">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="类型">
                {{ categoryMeta(detail.category).label }}
              </a-descriptions-item>
              <a-descriptions-item label="状态">
                <a-tag :color="statusMeta(detail.status).color">
                  {{ statusMeta(detail.status).label }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="租户">
                {{ detail.tenantName || '平台' }}
              </a-descriptions-item>
              <a-descriptions-item label="提交人">
                {{ detail.reporterName || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="提交时间">
                {{ formatTime(detail.createdAt) }}
              </a-descriptions-item>
              <a-descriptions-item label="联系方式">
                {{ detail.contact || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="页面" :span="2">
                {{ detail.pageUrl || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="客户端" :span="2">
                <span class="client-info">{{ detail.clientInfo || '-' }}</span>
              </a-descriptions-item>
            </a-descriptions>

            <h3 class="fb-title">{{ detail.title }}</h3>
            <div class="fb-content" v-html="safeContent"></div>

            <a-divider />

            <div v-if="detail.reply" class="exist-reply">
              <div class="section-title">
                已回复
                <span class="muted">{{ detail.replierName || '' }} · {{ formatTime(detail.replyAt) }}</span>
              </div>
              <div class="reply-text">{{ detail.reply }}</div>
            </div>

            <a-form layout="vertical" class="reply-form">
              <a-form-item :label="detail.reply ? '再次回复' : '回复用户'" required>
                <a-textarea
                  v-model:value="replyText"
                  :rows="5"
                  :maxlength="2000"
                  show-count
                  placeholder="说明问题原因 / 处理结果 / 后续计划。回复会以站内通知发给提交人。"
                />
              </a-form-item>
              <a-space>
                <a-button type="primary" :loading="replying" @click="submitReply">
                  {{ detail.reply ? '回复并再次通知' : '回复并通知用户' }}
                </a-button>
                <span class="muted">提交人会在右上角铃铛收到这条回复</span>
              </a-space>
            </a-form>
          </template>
        </a-spin>
      </a-drawer>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  getFeedbackList, getFeedbackDetail, replyFeedback, getUnprocessedFeedbackCount
} from '@/api'
import { useUserStore } from '@/stores/user'
import {
  feedbackCategoryMeta as categoryMeta,
  feedbackStatusMeta as statusMeta,
  feedbackTime as formatTime
} from '@/utils/feedback'

const route = useRoute()
const userStore = useUserStore()

const isPlatformAdmin = computed(() => {
  return userStore.roles && userStore.roles.includes('PLATFORM_ADMIN')
})

/** 与后端 FeedbackServiceImpl.REPLY_MAX 一致 */
const REPLY_MAX = 2000

const loading = ref(false)
const list = ref([])
const unprocessed = ref(0)
const statusFilter = ref('')

const drawerOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const replyText = ref('')
const replying = ref(false)

const columns = [
  { title: '提交时间', dataIndex: 'createdAt', key: 'createdAt', width: 165 },
  { title: '类型', dataIndex: 'category', key: 'category', width: 100 },
  { title: '租户', dataIndex: 'tenantName', key: 'tenant', width: 160, ellipsis: true },
  { title: '提交人', dataIndex: 'reporterName', key: 'reporterName', width: 110 },
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 110, fixed: 'right' }
]

/**
 * 正文是租户用户提交的富文本（可能带 base64 截图），直接 v-html 等于让任意租户
 * 往平台管理员的浏览器里塞脚本。这里按白名单过滤：只留排版标签与安全属性，
 * script / iframe / on* 事件 / style 一律去掉。
 */
const ALLOWED_TAGS = new Set([
  'P', 'BR', 'DIV', 'SPAN', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'DEL', 'SUB', 'SUP',
  'UL', 'OL', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
  'A', 'IMG', 'CODE', 'PRE', 'HR', 'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD'
])
const SAFE_ATTRS = new Set(['alt', 'title', 'width', 'height'])

function sanitizeHtml(html) {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const walk = (node) => {
    Array.from(node.children).forEach((el) => {
      const tag = el.tagName
      if (!ALLOWED_TAGS.has(tag)) {
        // 不认识的标签整块丢掉（含 script / style / iframe 里的内容）
        el.remove()
        return
      }
      Array.from(el.attributes).forEach((attr) => {
        const name = attr.name.toLowerCase()
        const value = attr.value || ''
        const safeHref = tag === 'A' && name === 'href' && /^(https?:|mailto:|\/)/i.test(value)
        const safeSrc = tag === 'IMG' && name === 'src' && /^(https?:|data:image\/|\/)/i.test(value)
        if (!safeHref && !safeSrc && !SAFE_ATTRS.has(name)) {
          el.removeAttribute(attr.name)
        }
      })
      if (tag === 'A' && el.getAttribute('href')) {
        el.setAttribute('target', '_blank')
        el.setAttribute('rel', 'noopener noreferrer')
      }
      walk(el)
    })
  }
  walk(doc.body)
  return doc.body.innerHTML
}

const safeContent = computed(() => sanitizeHtml(detail.value?.content))

const fetchList = async () => {
  loading.value = true
  try {
    const params = { limit: 500 }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await getFeedbackList(params)
    if (res.code === 200) {
      list.value = res.data || []
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const fetchUnprocessed = async () => {
  try {
    const res = await getUnprocessedFeedbackCount()
    if (res.code === 200) unprocessed.value = res.data || 0
  } catch {
    // 角标拿不到不影响主流程
  }
}

const openDetail = async (oid) => {
  drawerOpen.value = true
  detailLoading.value = true
  detail.value = null
  replyText.value = ''
  try {
    const res = await getFeedbackDetail(oid)
    if (res.code === 200) {
      detail.value = res.data
    } else {
      message.warning(res.message || '反馈不存在或已被删除')
      drawerOpen.value = false
      await fetchList()
    }
  } catch {
    drawerOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

const submitReply = async () => {
  const text = (replyText.value || '').trim()
  if (!text) {
    message.warning('请填写回复内容')
    return
  }
  if (text.length > REPLY_MAX) {
    message.warning(`回复最多 ${REPLY_MAX} 个字，当前 ${text.length} 字`)
    return
  }
  replying.value = true
  try {
    const res = await replyFeedback(detail.value.oid, { reply: text, status: 'DONE' })
    if (res.code === 200) {
      if (res.data?.notified) {
        message.success('已回复，站内通知已发送给提交人')
      } else {
        message.warning('回复已保存，但提交人账号已不存在，通知未能送达')
      }
      replyText.value = ''
      await openDetail(detail.value.oid)
      await fetchList()
      await fetchUnprocessed()
    }
  } catch {
    // 拦截器已提示
  } finally {
    replying.value = false
  }
}

onMounted(() => {
  if (!isPlatformAdmin.value) return
  fetchList()
  fetchUnprocessed()
  // 从「收到使用反馈」的站内通知点进来时带着 oid，直接打开这条
  const oid = route.query.oid
  if (oid) {
    openDetail(String(oid))
  }
})
</script>

<style scoped>
.feedback-manage {
  padding: 16px;
}

.time-text {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.65);
  white-space: nowrap;
}

.fb-title {
  margin: 20px 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.fb-content {
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.fb-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.fb-content :deep(p) {
  margin: 0 0 8px;
}

.client-info {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  word-break: break-all;
}

.section-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}

.muted {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.45);
}

.exist-reply .reply-text {
  padding: 10px 12px;
  border-left: 3px solid #52c41a;
  background: #f6ffed;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.reply-form {
  margin-top: 16px;
}
</style>
