<template>
  <a-modal
    :open="open"
    title="问题反馈"
    :width="'min(880px, 94vw)'"
    :mask-closable="false"
    centered
    :body-style="{ maxHeight: 'calc(100vh - 168px)', overflowY: 'auto' }"
    @cancel="close"
  >
    <!-- 底栏固定在弹窗里：内容再长也不用滚动才能点提交（页签不同，按钮不同） -->
    <template #footer>
      <div class="fb-actions">
        <template v-if="tab === 'submit'">
          <a-button @click="close">取消</a-button>
          <a-button type="primary" :loading="submitting" @click="handleSubmit">提交</a-button>
        </template>
        <template v-else>
          <a-button :loading="mineLoading" @click="loadMine">
            <template #icon><reload-outlined /></template>
            刷新
          </a-button>
          <a-button type="primary" @click="close">关闭</a-button>
        </template>
      </div>
    </template>

    <a-tabs v-model:activeKey="tab" @change="onTabChange">
      <!-- 提交 -->
      <a-tab-pane key="submit" tab="提交反馈">
        <p class="fb-hint">
          遇到问题、发现缺陷，或者希望增加某个功能？写在这里，我们会尽快跟进。
          提交时会自动带上当前页面和登录身份，省得你再描述一遍环境。
        </p>

        <a-form layout="vertical" class="fb-form">
          <a-form-item label="类型" required>
            <a-radio-group v-model:value="form.category" button-style="solid">
              <a-radio-button value="ISSUE">使用问题</a-radio-button>
              <a-radio-button value="BUG">缺陷 / Bug</a-radio-button>
              <a-radio-button value="FEATURE">新功能建议</a-radio-button>
            </a-radio-group>
          </a-form-item>

          <a-form-item label="标题" required>
            <a-input
              v-model:value="form.title"
              :maxlength="200"
              show-count
              placeholder="一句话说明，如：变更通告导出 PDF 缺少审批记录"
            />
          </a-form-item>

          <a-form-item label="详细描述" required>
            <RichTextEditor
              v-model="form.content"
              placeholder="做了什么、期望是什么、实际发生了什么；能复现的步骤越具体越好，可用工具栏插入截图"
            />
            <div class="fb-count" :class="{ 'is-over': plainText.length > CONTENT_MAX }">
              {{ plainText.length }} / {{ CONTENT_MAX }}
            </div>
          </a-form-item>

          <a-form-item label="联系方式（选填）">
            <a-input
              v-model:value="form.contact"
              :maxlength="200"
              :placeholder="contactPlaceholder"
            />
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- 我提交过的：能看到状态与平台回复 -->
      <a-tab-pane key="mine" :tab="`我的反馈（${mineList.length}）`">
        <a-spin :spinning="mineLoading">
          <a-empty
            v-if="!mineList.length"
            :image-style="{ height: '56px' }"
            description="还没有提交过反馈"
          />
          <div v-else class="mine-list">
            <div v-for="item in mineList" :key="item.oid" class="mine-item">
              <div class="mine-head">
                <a-tag :color="feedbackStatusMeta(item.status).color" size="small">
                  {{ feedbackStatusMeta(item.status).label }}
                </a-tag>
                <a-tag :color="feedbackCategoryMeta(item.category).color" size="small">
                  {{ feedbackCategoryMeta(item.category).label }}
                </a-tag>
                <span class="mine-title" :title="item.title">{{ item.title }}</span>
              </div>

              <div class="mine-meta">提交于 {{ feedbackTime(item.createdAt) }}</div>

              <div v-if="item.reply" class="mine-reply">
                <div class="mine-reply-head">
                  <comment-outlined />
                  <span>{{ item.replierName || '平台' }} 回复</span>
                  <span class="when">{{ feedbackTime(item.replyAt) }}</span>
                </div>
                <div class="mine-reply-body">{{ item.reply }}</div>
              </div>
              <div v-else class="mine-wait">已收到，我们会尽快跟进；处理结果会以站内通知发给你。</div>
            </div>
          </div>
        </a-spin>
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined, CommentOutlined } from '@ant-design/icons-vue'
import { submitFeedback, getMyFeedbacks } from '@/api'
import { useUserStore } from '@/stores/user'
import { feedbackCategoryMeta, feedbackStatusMeta, feedbackTime } from '@/utils/feedback'
import RichTextEditor from '@/components/RichTextEditor.vue'

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['update:open'])

const route = useRoute()
const userStore = useUserStore()
const submitting = ref(false)

/** submit 填写 / mine 我提交过的 */
const tab = ref('submit')

/** 纯文本字数上限（与后端 FeedbackServiceImpl 一致） */
const CONTENT_MAX = 4000
/** HTML 总长上限：富文本可能内嵌 base64 截图，按字符数兜一道，防止提交几 MB 的内容 */
const HTML_MAX = 500000

const mineLoading = ref(false)
const mineList = ref([])

const form = reactive({
  category: 'ISSUE',
  title: '',
  content: '',
  contact: ''
})

const contactPlaceholder = computed(
  () => userStore.email || '邮箱 / 微信 / 电话，方便我们回访'
)

/**
 * 富文本 → 纯文本：用于"是否为空"与字数统计。
 *
 * <p>只贴了一张截图时纯文本是空的，但反馈是有效的 —— 所以把 img 换成「[图片]」占位，
 * 否则用户截了图点提交会被拦"请描述问题"。
 */
const plainText = computed(() => {
  const html = form.content || ''
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = html
  div.querySelectorAll('img').forEach((img) => img.replaceWith('[图片]'))
  return (div.textContent || '').replace(/\u00a0/g, ' ').trim()
})

const close = () => emit('update:open', false)

/** 浏览器信息：复现问题要用，截断到后端 500 字符上限内 */
const clientInfo = () => {
  const raw = `${navigator.userAgent} | ${window.screen.width}x${window.screen.height}`
  return raw.length > 480 ? raw.slice(0, 480) : raw
}

/** 我提交过的反馈：状态与回复都在列表里，不必再进详情 */
const loadMine = async () => {
  mineLoading.value = true
  try {
    const res = await getMyFeedbacks(50)
    if (res.code === 200) {
      mineList.value = res.data || []
    }
  } catch {
    // 拦截器已提示；列表保持原样
  } finally {
    mineLoading.value = false
  }
}

const onTabChange = (key) => {
  // 每次切到「我的反馈」都取一次最新：管理员可能刚刚回复过
  if (key === 'mine') loadMine()
}

// 打开时刷新一次：用户常是"提完过几天再来看状态"
watch(() => props.open, (open) => {
  if (open) loadMine()
})

const handleSubmit = async () => {
  if (!form.title.trim()) {
    message.warning('请填写标题')
    return
  }
  if (!plainText.value) {
    message.warning('请描述你遇到的问题或想要的功能')
    return
  }
  if (plainText.value.length > CONTENT_MAX) {
    message.warning(`描述最多 ${CONTENT_MAX} 字，当前 ${plainText.value.length} 字`)
    return
  }
  if ((form.content || '').length > HTML_MAX) {
    message.warning('内容过大（截图可能太多），请压缩图片后重试')
    return
  }
  submitting.value = true
  try {
    const res = await submitFeedback({
      category: form.category,
      title: form.title.trim(),
      content: form.content,
      // 没填就带上账号邮箱，方便回访
      contact: form.contact.trim() || userStore.email || '',
      pageUrl: route.fullPath,
      clientInfo: clientInfo()
    })
    if (res.code === 200) {
      message.success('已提交，感谢反馈！')
      form.title = ''
      form.content = ''
      // 直接切到「我的反馈」：让用户当场看到这条已经在列表里、状态是待处理
      tab.value = 'mine'
      await loadMine()
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.fb-hint {
  margin: 0 0 16px;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(0, 0, 0, 0.55);
}

.fb-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

/* 编辑器默认 120px 偏窄，反馈场景给足空间 */
.fb-form :deep(.rte-content .tiptap) {
  min-height: 150px;
}

.fb-count {
  margin-top: 4px;
  text-align: right;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.fb-count.is-over {
  color: #ff4d4f;
}

/* 弹窗底栏：按钮按页签切换，位置固定（不随表单滚动） */
.fb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ===== 我的反馈 ===== */
.mine-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mine-item {
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.mine-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mine-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mine-meta {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.mine-reply {
  margin-top: 8px;
  padding: 8px 10px;
  border-left: 3px solid #52c41a;
  border-radius: 4px;
  background: #f6ffed;
}

.mine-reply-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.65);
}

.mine-reply-head .when {
  color: rgba(0, 0, 0, 0.4);
}

.mine-reply-body {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.mine-wait {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
