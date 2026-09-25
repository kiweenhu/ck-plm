<template>
  <a-drawer
    :open="open"
    :width="620"
    :title="`节点执行情况 · ${activity?.name || activity?.activityId || ''}`"
    @close="close"
  >
    <!-- 节点本身：谁办的、什么时候、什么结论（人工节点与自动节点共用同一套字段） -->
    <a-descriptions :column="2" size="small" :label-style="{ width: '76px', color: '#8c8c8c' }">
      <a-descriptions-item label="节点类型">{{ activity?.type || '—' }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor" size="small">{{ statusText }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="责任人">{{ personText }}</a-descriptions-item>
      <a-descriptions-item label="结论">{{ activity?.outcome || '—' }}</a-descriptions-item>
      <a-descriptions-item label="时间" :span="2">{{ timeText }}</a-descriptions-item>
      <a-descriptions-item v-if="activity?.comment" label="意见" :span="2">
        {{ activity.comment }}
      </a-descriptions-item>
      <a-descriptions-item label="节点 ID" :span="2">
        <span class="pnd-mono">{{ activity?.activityId || '—' }}</span>
      </a-descriptions-item>
    </a-descriptions>

    <div class="pnd-section">
      <div class="pnd-section-title">
        执行日志
        <span class="pnd-section-hint">
          {{ logs.length }} 条<template v-if="errorCount">，其中错误 {{ errorCount }} 条</template>
        </span>
        <a-button size="small" type="link" :loading="loading" @click="load">刷新</a-button>
      </div>

      <a-spin :spinning="loading" size="small">
        <a-empty
          v-if="!logs.length && !loading"
          :image-style="{ height: '32px' }"
          description="该节点没有后台执行日志"
        />
        <div v-else class="pnd-logs">
          <div v-for="(log, idx) in logs" :key="idx" class="pnd-log" :class="`is-${(log.level || '').toLowerCase()}`">
            <div class="pnd-log-head">
              <span class="pnd-time">{{ fmtTime(log.createdAt) }}</span>
              <a-tag :color="levelColor(log.level)" size="small">{{ log.level }}</a-tag>
              <span class="pnd-msg">{{ log.message }}</span>
            </div>
            <!-- 明细（参数 / 异常堆栈）：默认折起来，需要时再展开；可一键复制去提单 -->
            <div v-if="log.detail" class="pnd-detail">
              <div class="pnd-detail-head">
                <a @click="toggle(idx)">{{ expanded[idx] ? '收起明细' : '展开明细（错误堆栈 / 参数）' }}</a>
                <a-button size="small" type="link" @click="copy(log.detail)">复制</a-button>
              </div>
              <pre v-if="expanded[idx]" class="pnd-pre">{{ log.detail }}</pre>
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <div v-if="!loading && !logs.length" class="pnd-note">
      没有日志通常有三种原因：该节点是人工节点（办理痕迹看上面的意见与结论）；
      该实例是在「节点日志」能力上线前跑的；或该节点确实没有后台动作。
    </div>
  </a-drawer>
</template>

<script setup>
/**
 * 节点执行详情抽屉 —— 点开流程进度里的某个节点，看它"后台执行了什么、报了什么错"。
 *
 * <p><b>为什么需要它</b>：自动服务（设置状态 / 自动服务 / 通知）是后台跑的，
 * 失败时用户只看到"流程卡住了"，原因原本只留在服务器日志里 —— 得登服务器翻。
 * 后端已把执行痕迹按「实例 + 节点」落库（{@code ck_process_node_log}），这里如实呈现。
 *
 * <p>人工节点也能点开：它的"执行情况"就是谁办的、什么时候、什么意见与结论。
 * 所以抽屉对两类节点都成立，只是日志区可能是空的（会说明原因，而不是给一片空白）。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getInstanceNodeLogs } from '@/api'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 流程图上的那个节点（ProcessActivityVO：activityId / name / type / status / …） */
  activity: { type: Object, default: null },
  /** 流程实例 id（查该节点的日志用） */
  instanceId: { type: String, default: '' },
})
const emit = defineEmits(['update:open'])

const loading = ref(false)
const logs = ref([])
/** 每条明细的展开态（默认收起：堆栈很长，先给结论） */
const expanded = ref({})

const errorCount = computed(() => logs.value.filter((log) => log.level === 'ERROR').length)

const statusText = computed(() => {
  const status = props.activity?.status
  return status === 'completed' ? '已办' : status === 'running' ? '当前节点' : '未开始'
})

const statusColor = computed(() => {
  const status = props.activity?.status
  return status === 'completed' ? 'green' : status === 'running' ? 'blue' : 'default'
})

const personText = computed(() => {
  const item = props.activity || {}
  const names = (item.assigneeNames && item.assigneeNames.length ? item.assigneeNames : item.assignees) || []
  if (names.length) return names.join('、')
  return item.status === 'pending' ? '待指派' : '未指派'
})

const timeText = computed(() => {
  const item = props.activity || {}
  if (!item.startTime) return '—'
  const start = fmtTime(item.startTime)
  return item.endTime ? `${start} → ${fmtTime(item.endTime)}` : `${start} 起`
})

/** 打开时按该节点拉一次日志（切节点即重拉） */
watch(() => [props.open, props.activity?.activityId, props.instanceId], ([open]) => {
  if (!open) return
  expanded.value = {}
  load()
})

async function load() {
  if (!props.instanceId || !props.activity?.activityId) {
    logs.value = []
    return
  }
  loading.value = true
  try {
    const res = await getInstanceNodeLogs(props.instanceId, { activityId: props.activity.activityId })
    logs.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

function toggle(index) {
  expanded.value = { ...expanded.value, [index]: !expanded.value[index] }
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.warning('复制失败，请手动选中复制')
  }
}

function levelColor(level) {
  return level === 'ERROR' ? 'red' : level === 'WARN' ? 'orange' : 'default'
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return Number.isNaN(d.getTime()) ? String(t) : d.toLocaleString('zh-CN', { hour12: false })
}

function close() {
  emit('update:open', false)
}
</script>

<style scoped>
.pnd-section {
  margin-top: 16px;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.pnd-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #595959;
  margin-bottom: 8px;
}

.pnd-section-hint {
  font-weight: 400;
  color: #8c8c8c;
}

/* 刷新推到行尾：它是这一块的次要动作 */
.pnd-section-title .ant-btn {
  margin-left: auto;
}

.pnd-logs {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pnd-log {
  border-radius: 6px;
  padding: 8px 10px;
  background: #fafafa;
  border-left: 3px solid #d9d9d9;
}

.pnd-log.is-error {
  background: #fff1f0;
  border-left-color: #ff4d4f;
}

.pnd-log.is-warn {
  background: #fffbe6;
  border-left-color: #faad14;
}

.pnd-log-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.pnd-time {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #8c8c8c;
}

.pnd-msg {
  font-size: 13px;
  color: #262626;
  word-break: break-word;
}

.pnd-detail {
  margin-top: 6px;
}

.pnd-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.pnd-pre {
  margin: 6px 0 0;
  max-height: 320px;
  overflow: auto;
  padding: 8px;
  border-radius: 4px;
  background: #262626;
  color: #f0f0f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.pnd-mono {
  font-family: 'Consolas', 'Monaco', monospace;
}

.pnd-note {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.8;
  color: #8c8c8c;
  background: #fafafa;
  border-radius: 4px;
  padding: 8px 10px;
}
</style>
