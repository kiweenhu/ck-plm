<template>
  <div ref="rootEl" :class="['pa', { 'pa-compact': compact }]">
    <a-spin :spinning="loading" size="small">
      <ol v-if="activities && activities.length" class="pa-list">
        <li v-for="(item, idx) in activities"
          :key="`${item.activityId}-${idx}`"
          :class="['pa-item', `pa-item-${item.status}`, { 'pa-item-clickable': clickable }]"
          @click="onSelect(item)">
          <!-- 状态一眼可辨：已办=实心勾、在办=蓝环、未开始=灰环 -->
          <span class="pa-dot">
            <CheckOutlined v-if="item.status === 'completed'" />
          </span>

          <div class="pa-body">
            <div class="pa-title">
              <span class="pa-name">{{ item.name || item.activityId }}</span>
              <!-- 活动类型是次要信息：素文本、不加框，免得抢了节点名的视觉 -->
              <span v-if="typeText(item)" class="pa-type">{{ typeText(item) }}</span>
              <!-- 后台执行痕迹的角标：有错误优先报错误（红色），否则只报"有日志"。
                   让"哪个节点出过错"在时间轴上一眼可见，不必逐条点进去才发现 -->
              <span v-if="item.nodeErrorCount" class="pa-badge pa-badge-error">
                <CloseCircleFilled /> 错误 {{ item.nodeErrorCount }}
              </span>
              <span v-else-if="item.nodeLogCount" class="pa-badge pa-badge-log">
                日志 {{ item.nodeLogCount }}
              </span>
              <span class="pa-status">{{ statusText(item.status) }}</span>
            </div>

            <div class="pa-meta">
              <span class="pa-label">责任人</span>
              <span class="pa-value">{{ personText(item) }}</span>
              <template v-if="timeText(item)">
                <span class="pa-label">时间</span>
                <span class="pa-value">{{ timeText(item) }}</span>
              </template>
              <!-- 实际走出的结论（同意 / 驳回）：结论本身有色，比灰色时间更该被看见 -->
              <span v-if="item.outcome" :class="['pa-outcome', outcomeClass(item.outcome)]">
                {{ item.outcome }}
              </span>
            </div>

            <div v-if="item.comment" class="pa-comment">
              <span class="pa-label">意见</span>{{ item.comment }}
            </div>

            <!-- 可点时给一句明确的引导：行本身可点这件事，不写出来没人知道 -->
            <div v-if="clickable" class="pa-open">查看执行情况与日志</div>
          </div>
        </li>
      </ol>
      <div v-else-if="!loading" class="pa-empty">暂无节点记录</div>
    </a-spin>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { CheckOutlined, CloseCircleFilled } from '@ant-design/icons-vue'

/**
 * 流程节点经路（时间轴）—— 纯展示组件：已办 / 当前在办 / 未开始。
 *
 * <p>数据由 `GET /workflow/instance/{id}/activities` 给出，组件本身不发请求，
 * 因此在「关联流程」的左栏与详情弹窗里共用同一份数据、同一套排版。
 *
 * <h3>排版口径（为什么这么排）</h3>
 * <ul>
 *   <li><b>节点名是标题</b>：大一号、加粗、最深色 —— 用户找的是"走到哪个节点了"，名字必须是第一眼。</li>
 *   <li><b>活动类型是次要信息</b>：素文本、不加框。名字与类型同框并列时，框会抢视觉，
 *       看起来反而像"没显示节点名"。</li>
 *   <li><b>状态靠整行区分</b>，不靠一个小标签：已办=绿勾+常规色，在办=蓝底蓝环+加粗，
 *       未开始=灰环灰字。三种状态离远也能分清。</li>
 *   <li><b>呼吸感</b>：行内留白、行距、意见单独成块。这一栏是给人读进度用的，不是看表格。</li>
 *   <li><b>后台执行痕迹有角标</b>：有 ERROR 标红（"哪个节点出过错"一眼可见），
 *       只有普通日志给个中性角标 —— 免得"有日志"和"出过错"看起来一样。</li>
 * </ul>
 */
const props = defineProps({
  /** 节点列表（后端 ProcessActivityVO 数组） */
  activities: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /**
   * 紧凑排版：给<b>窄栏</b>（办理页右栏）用。
   *
   * <p>宽栏那套是"节点名 + 类型 …… 状态（右推）"一行、元信息再一行三列；
   * 放到 300 多像素的窄栏里，状态会被挤到换行、元信息折成两三行，看着零碎。
   * 紧凑模式只改排版口径（元信息逐行堆叠、字号收一档），信息一条不少。
   */
  compact: { type: Boolean, default: false },
  /**
   * 节点是否可点开（点了发 {@code select} 事件）。
   *
   * <p>默认<b>不可点</b>：能不能点取决于宿主有没有地方呈现执行详情 ——
   * 没有任何反应的行却显示手型光标，比不可点更让人困惑。
   */
  clickable: { type: Boolean, default: false },
  /**
   * 数据到位后，自动把「当前节点」对齐到滚动区<b>顶部</b>。
   *
   * <p>为什么需要：流程一长（几十个节点），办理页左栏要滚动才看得到"卡在哪一步"，
   * 而这块恰恰是办理过程中被看得最频繁的 —— 每次打开都要手动往下拖一次。
   *
   * <p>默认关闭：只有宿主确实给了滚动区（如办理页左栏）时才开；
   * 弹窗、关联流程列表里整块都在视野内，自动滚动反而会把人拽走。
   */
  scrollToCurrent: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])

const rootEl = ref(null)

/** 最近的可滚动祖先（宿主提供的滚动区）：找不到就返回 null，什么也不做 */
function findScrollBox(el) {
  let box = el?.parentElement
  while (box && box !== document.body) {
    const style = getComputedStyle(box)
    if (/(auto|scroll)/.test(style.overflowY) && box.scrollHeight > box.clientHeight) {
      return box
    }
    box = box.parentElement
  }
  return null
}

/** 把当前节点（.pa-item-running）对齐到滚动区顶部，留 8px 呼吸 */
function alignCurrentToTop() {
  if (!props.scrollToCurrent) return
  const root = rootEl.value
  const current = root?.querySelector('.pa-item-running')
  const box = root ? findScrollBox(root) : null
  if (!current || !box) return
  const delta = current.getBoundingClientRect().top - box.getBoundingClientRect().top
  box.scrollTop += delta - 8
}

// 数据到位 / 加载结束 后各对齐一次：只跟数据状态走，不监听用户滚动（免得把人拽回去）
watch(() => props.activities, async () => {
  await nextTick()
  alignCurrentToTop()
})
watch(() => props.loading, async (spinning) => {
  if (spinning) return
  await nextTick()
  alignCurrentToTop()
})
onMounted(async () => {
  await nextTick()
  alignCurrentToTop()
})

/** 点击一个节点：只有宿主声明可点时才发事件（由宿主打开执行详情抽屉） */
function onSelect(item) {
  if (props.clickable) {
    emit('select', item)
  }
}

function statusText(status) {
  return status === 'completed' ? '已办' : status === 'running' ? '当前节点' : '未开始'
}

/**
 * 活动类型文案。
 *
 * <p>与节点名重复时不显示（如开始事件的名字和类型都叫「开始」）—— 同一行写两遍"开始"
 * 只会让人怀疑哪个才是名字。
 */
function typeText(item) {
  const type = item.type || ''
  if (!type || type === item.name) return ''
  return type
}

/** 责任人：优先显示名（assignee 可能是人员 oid，后端已换成人名） */
function personText(item) {
  const names = (item.assigneeNames && item.assigneeNames.length
    ? item.assigneeNames
    : item.assignees) || []
  if (names.length) return names.join('、')
  return item.status === 'pending' ? '待指派' : '未指派'
}

/** 时间：已办给"起 → 止"，在办给"起（已耗时）"，未开始不给 */
function timeText(item) {
  if (!item.startTime || item.status === 'pending') return ''
  const start = fmtTime(item.startTime)
  const end = item.endTime ? fmtTime(item.endTime, item.startTime) : ''
  if (end) {
    const cost = durationText(item.startTime, item.endTime)
    return `${start} → ${end}${cost ? `（${cost}）` : ''}`
  }
  const cost = durationText(item.startTime, Date.now())
  return `${start} 起${cost ? `（已 ${cost}）` : ''}`
}

function durationText(from, to) {
  const ms = new Date(to).getTime() - new Date(from).getTime()
  if (!Number.isFinite(ms) || ms < 0) return ''
  const minutes = Math.floor(ms / 60000)
  if (minutes < 1) return '不到 1 分钟'
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分钟`
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`
}

function outcomeClass(outcome) {
  return outcome === '驳回' ? 'pa-outcome-reject' : 'pa-outcome-approve'
}

/**
 * 时间格式化。
 *
 * <p>与 `sameDayAs` 同一天时只给时间部分（`19:28:40`）—— 半宽栏位里省下的这十来个字符，
 * 正好让"责任人 + 时间 + 耗时"留在一行，不至于折成两行看着零碎。
 */
function fmtTime(t, sameDayAs) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  if (sameDayAs) {
    const base = new Date(sameDayAs)
    if (!Number.isNaN(base.getTime()) && d.toDateString() === base.toDateString()) {
      return d.toLocaleString('zh-CN', {
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
      })
    }
  }
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.pa {
  padding: 4px 0 2px;
}

.pa-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.pa-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

/* 相邻节点之间的竖线：只做"顺序"的暗示，不抢内容 */
.pa-item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 18px;
  top: 30px;
  bottom: 2px;
  width: 1px;
  background: #f0f0f0;
}

.pa-item + .pa-item {
  margin-top: 4px;
}

.pa-item-completed {
  background: transparent;
}

/* 当前在办：整行浅蓝 + 左侧蓝条 + 蓝环，远看就知道卡在哪 */
.pa-item-running {
  background: #f0f7ff;
  border-left: 3px solid #1677ff;
  padding-left: 9px;
}

.pa-item-pending {
  background: transparent;
}

.pa-dot {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  margin-top: 3px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  color: #fff;
  background: #d9d9d9;
}

.pa-item-completed .pa-dot {
  background: #52c41a;
}

.pa-item-running .pa-dot {
  background: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.15);
}

.pa-item-pending .pa-dot {
  background: #fff;
  border: 1px dashed #d9d9d9;
}

.pa-body {
  flex: 1 1 auto;
  min-width: 0;
}

.pa-title {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0 8px;
}

.pa-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f1f1f;
  line-height: 1.5;
}

/* 已办的名字略淡一档，把黑度留给"当前在办"那一条 —— 三种状态三种重量 */
.pa-item-completed .pa-name {
  color: #434343;
}

.pa-item-pending .pa-name {
  font-weight: 400;
  color: #bfbfbf;
}

.pa-type {
  font-size: 12px;
  color: #8c8c8c;
}

.pa-item-pending .pa-type {
  color: #cfcfcf;
}

/* 状态：整行区分之外再给一句明确的话（当前节点 / 已办 / 未开始） */
.pa-status {
  margin-left: auto;
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
}

.pa-item-completed .pa-status {
  color: #52c41a;
}

.pa-item-running .pa-status {
  color: #1677ff;
  font-weight: 500;
}

.pa-meta {
  margin-top: 5px;
  font-size: 12px;
  color: #434343;
  line-height: 1.9;
}

.pa-meta .pa-label {
  color: #a6a6a6;
  margin-right: 4px;
}

.pa-meta .pa-label:not(:first-child) {
  margin-left: 16px;
}

.pa-item-pending .pa-meta {
  color: #bfbfbf;
}

.pa-value {
  margin-right: 2px;
}

.pa-outcome {
  margin-left: 16px;
  font-weight: 500;
}

.pa-outcome-approve {
  color: #52c41a;
}

.pa-outcome-reject {
  color: #ff4d4f;
}

.pa-comment {
  margin-top: 7px;
  font-size: 12px;
  color: #434343;
  background: #fafafa;
  border-radius: 4px;
  padding: 6px 10px;
  line-height: 1.7;
}

.pa-comment .pa-label {
  color: #a6a6a6;
  margin-right: 4px;
}

/* 后台执行痕迹角标：错误用红、普通日志用灰，二者重量不同（"出过错"要跳出来） */
.pa-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  line-height: 1.6;
  padding: 0 6px;
  border-radius: 9px;
}

.pa-badge-error {
  color: #cf1322;
  background: #fff1f0;
  border: 1px solid #ffccc7;
}

.pa-badge-log {
  color: #595959;
  background: #fafafa;
  border: 1px solid #f0f0f0;
}

/* 可点开的节点：悬停给背景 + "查看执行情况与日志" 引导 */
.pa-item-clickable {
  cursor: pointer;
  transition: background 0.15s;
}

.pa-item-clickable:hover {
  background: #fafafa;
}

.pa-item-clickable.pa-item-running:hover {
  background: #e9f2ff;
}

.pa-open {
  margin-top: 4px;
  font-size: 12px;
  color: #1677ff;
  opacity: 0;
  transition: opacity 0.15s;
}

.pa-item-clickable:hover .pa-open {
  opacity: 1;
}

.pa-compact .pa-open {
  font-size: 11px;
}

.pa-empty {
  color: #8c8c8c;
  font-size: 12px;
  padding: 6px 0;
}

/* ==================== 紧凑排版（窄栏用） ==================== */

.pa-compact {
  padding: 0;
}

.pa-compact .pa-item {
  gap: 8px;
  padding: 8px;
}

/* 竖线要跟着紧凑后的内边距与圆点位置走，否则会飘到左边 */
.pa-compact .pa-item:not(:last-child)::after {
  left: 14px;
  top: 26px;
}

.pa-compact .pa-item-running {
  padding-left: 5px;
}

.pa-compact .pa-name {
  font-size: 13px;
}

.pa-compact .pa-type,
.pa-compact .pa-status {
  font-size: 11px;
}

/* 元信息逐行堆叠：窄栏里"责任人 ｜ 时间 ｜ 耗时"挤一行必然折行，不如一行一项看得清 */
.pa-compact .pa-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
}

.pa-compact .pa-meta .pa-label:not(:first-child),
.pa-compact .pa-outcome {
  margin-left: 0;
}

.pa-compact .pa-comment {
  margin-top: 6px;
  padding: 5px 8px;
  font-size: 12px;
}
</style>
