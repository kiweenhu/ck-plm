<template>
  <div class="ai-entry">
    <!-- 顶栏入口：一个小胶囊，和铃铛/滚动条同一排 -->
    <a-tooltip title="CK-PLM 助手：用聊天的方式查系统里的数据">
      <div class="ai-pill" @click="open">
        <ThunderboltOutlined class="ai-pill-icon" />
        <span class="ai-pill-text">CK-PLM 助手</span>
      </div>
    </a-tooltip>

    <!-- 560：回答常是"结论 + 分点 + 编码"，440 会把它们挤成一小条、来回折行 -->
    <a-drawer v-model:visible="visible" title="CK-PLM 助手" placement="right" :width="560" class="ai-drawer">
      <template #extra>
        <a-space>
          <!-- 后端配了多个模型才给下拉：只有一个还摆个选择器，反倒让人以为"是不是没配全" -->
          <a-select v-if="modelOptions.length > 1" v-model:value="modelId" size="small"
            style="width: 150px" :options="modelOptions" @change="rememberModel" />
          <a-tag v-else-if="modelTag" size="small" color="blue">{{ modelTag }}</a-tag>
          <a-button size="small" :disabled="!messages.length || loading" @click="clear">清空</a-button>
        </a-space>
      </template>

      <!-- 标题与说明都按后端给的 state/hint 渲染："总开关没打开"和"没有可用模型"是两回事，
           混成一句会让人明明配好了模型却一直被告知"去配置" -->
      <a-alert v-if="statusLoaded && !status.configured" type="warning" show-icon class="ai-notice"
        :message="status.state === 'disabled' ? '助手的总开关还没打开' : '助手还没有可用的大模型'"
        :description="status.hint || '请在 application.yml 的 plm.ai 里配置 enabled: true 与 models（base-url + api-key 或账号密码），重启后端即可。'" />

      <div ref="listRef" class="ai-list">
        <div v-if="!messages.length" class="ai-welcome">
          <div class="ai-welcome-title">问我系统里的事，我去查数据再回答</div>
          <div class="ai-welcome-tip">例如：</div>
          <a-space wrap>
            <a-tag v-for="q in samples" :key="q" class="ai-sample" @click="ask(q)">{{ q }}</a-tag>
          </a-space>
        </div>

        <div v-for="(m, i) in messages" :key="i" :class="['ai-msg', `ai-msg-${m.role}`]">
          <div class="ai-bubble">
            <div class="ai-text">{{ m.content }}</div>
            <div v-if="m.tools && m.tools.length" class="ai-tools">
              查询了：{{ toolSummary(m.tools) }}
            </div>
          </div>
        </div>

        <div v-if="loading" class="ai-msg ai-msg-assistant">
          <div class="ai-bubble ai-bubble-loading">
            <a-spin size="small" /> <span class="ai-loading-text">正在查询系统数据…</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="ai-input">
          <a-textarea v-model:value="draft" :rows="2" :maxlength="500" placeholder="问点什么，例如：我有哪些待办？"
            @press-enter="onEnter" />
          <a-button type="primary" :loading="loading" :disabled="!draft.trim()" @click="ask()">发送</a-button>
        </div>
        <div class="ai-input-hint">Enter 发送，Shift + Enter 换行 · 查询以你本人的权限执行</div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import { askAi, getAiStatus } from '@/api'

/**
 * CK-PLM 助手 —— 顶栏聊天入口。
 *
 * <p>它本身不做任何"智能"：把问题交给后端，后端让大模型调 CK-PLM 的工具查数（见 PlmTools），
 * 所以回答里的编码、状态、人名都来自真实数据，而不是模型编的。
 * 界面上把"查了哪些工具"显式列出来 —— 让用户能判断这个回答的可靠程度。
 *
 * <p>后端可以配多个模型（{@code plm.ai.models}），这里按需给一个下拉；只有一个模型时
 * 只显示一个标签 —— 摆一个"只有一个选项"的下拉，容易让人以为配置没配全。
 */
const visible = ref(false)
const statusLoaded = ref(false)
const status = ref({
  enabled: false, configured: false, model: '', authMode: 'none',
  models: [], state: 'disabled', hint: '',
})
const messages = ref([])
const draft = ref('')
const loading = ref(false)
const listRef = ref(null)

/** 选中的模型；记在本地，下次打开还是它 */
const AI_MODEL_KEY = 'ckplm.ai.model'
const modelId = ref(localStorage.getItem(AI_MODEL_KEY) || '')

const modelOptions = computed(() =>
  (status.value.models || []).map(m => ({ value: m.id, label: m.label || m.id })))

/** 只有一个模型时显示的标签 */
const modelTag = computed(() => (status.value.models || [])[0]?.label || status.value.model || '')

/**
 * 定下当前用哪个模型。
 *
 * <p>本地记的模型可能已经下线（配置改了、换了网关），所以**必须在清单里校验一次** ——
 * 否则会把一个不存在的 id 一直发上去，每次都只得到"没有这个模型"。
 */
function pickModel() {
  const list = status.value.models || []
  if (!list.length) {
    modelId.value = ''
    return
  }
  if (!list.some(m => m.id === modelId.value)) {
    modelId.value = status.value.defaultModel || list[0].id
  }
}

function rememberModel() {
  try { localStorage.setItem(AI_MODEL_KEY, modelId.value) } catch { /* 隐私模式等，忽略 */ }
}

const samples = ['我有哪些待办任务？', '我最近改过哪些对象？', '有哪些流程正在运行？', '企业最近发了什么公告？']

/** 工具名 → 中文（给用户看"我查了什么"） */
const TOOL_LABELS = {
  search_objects: '搜索对象',
  my_todo_tasks: '我的待办',
  my_recent_objects: '我最近的对象',
  my_checkouts: '我的检出',
  running_processes: '运行中的流程',
  latest_announcements: '企业公告',
}
const toolLabel = (name) => TOOL_LABELS[name] || name

/**
 * 工具清单摘要：同名合并成「搜索对象 ×3」。
 *
 * <p>模型经常连着调同一个工具（换个关键字再搜一次），逐个列出来就是一串重复的
 * 「搜索对象、搜索对象、搜索对象」，看不出它到底查了几次、查了什么。
 */
function toolSummary(tools) {
  const times = new Map()
  tools.forEach(name => times.set(name, (times.get(name) || 0) + 1))
  return [...times.entries()]
    .map(([name, n]) => (n > 1 ? `${toolLabel(name)} ×${n}` : toolLabel(name)))
    .join('、')
}

async function open() {
  visible.value = true
  if (!statusLoaded.value) {
    try {
      const res = await getAiStatus()
      if (res?.code === 200 && res.data) status.value = res.data
    } catch { /* 拿不到状态就按未配置处理 */ }
    pickModel()
    statusLoaded.value = true
  }
  await scrollToBottom()
}

/** Enter 发送、Shift+Enter 换行 */
function onEnter(e) {
  if (e?.shiftKey) return
  e?.preventDefault?.()
  ask()
}

async function ask(preset) {
  const question = (preset || draft.value).trim()
  if (!question || loading.value) return
  draft.value = ''
  const history = messages.value.map(m => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: question })
  loading.value = true
  await scrollToBottom()
  try {
    const res = await askAi({ message: question, history, modelId: modelId.value || undefined })
    if (res?.code === 200 && res.data) {
      messages.value.push({ role: 'assistant', content: res.data.answer, tools: res.data.tools || [] })
    } else {
      message.error(res?.message || '助手暂时不可用')
    }
  } catch {
    // 失败原因由响应拦截器统一提示
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function clear() {
  messages.value = []
}

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}
</script>

<style scoped>
/* 高度必须写死：顶栏是 line-height: 56px，会继承进来的，
   12px 的文字行盒被撑成 56px → 胶囊比顶栏还高。这里用固定 height + line-height: 1 挡掉继承。 */
.ai-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  box-sizing: border-box;
  padding: 0 10px;
  border-radius: 14px;
  background: linear-gradient(135deg, #e6f4ff 0%, #f0f5ff 100%);
  border: 1px solid #d6e4ff;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.ai-pill:hover {
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.2);
  transform: translateY(-1px);
}

.ai-pill-icon {
  color: #1677ff;
  font-size: 13px;
  line-height: 1;
}

.ai-pill-text {
  font-size: 12px;
  line-height: 1;
  color: #1677ff;
  font-weight: 500;
}

.ai-notice {
  margin-bottom: 12px;
}

.ai-list {
  height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 4px;
}

.ai-welcome {
  padding: 8px 0;
}

.ai-welcome-title {
  font-size: 14px;
  color: #262626;
  margin-bottom: 8px;
}

.ai-welcome-tip {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 6px;
}

.ai-sample {
  cursor: pointer;
}

.ai-msg {
  display: flex;
  margin-bottom: 14px;
}

.ai-msg-user {
  justify-content: flex-end;
}

.ai-bubble {
  max-width: 90%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-msg-user .ai-bubble {
  background: #1677ff;
  color: #fff;
}

.ai-msg-assistant .ai-bubble {
  background: #f5f5f5;
  color: #262626;
}

.ai-bubble-loading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-loading-text {
  color: #595959;
}

.ai-tools {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #d9d9d9;
  font-size: 12px;
  color: #8c8c8c;
}

.ai-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.ai-input-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #bfbfbf;
}
</style>
