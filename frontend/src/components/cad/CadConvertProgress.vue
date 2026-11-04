<template>
  <!--
    转换进度（STEP → GLB）的唯一展示口径。

    为什么单独抽一个组件：三个入口（文档预览弹窗 / 三维浏览页 / 产品数模列表）
    都在等同一件事，文案与"进度缺失时怎么办"必须一致 —— 否则同一个任务在
    不同页面上一处说"转换中"、一处说"请稍后刷新"，用户只会更困惑。

    两条硬规矩：
      1. **百分比未知不等于 0%**。转换器只在阶段边界上报，QUEUED 或刚进入
         网格化时 progress 可能是空的 —— 画一根 0% 的死条会让人以为卡住了，
         这种情况走不定长动画条。
      2. **进度久未更新要说出来**。回调丢一次，进度就停在原地；不给提示的话
         用户会一直盯着 42% 等下去。
  -->
  <div class="ccp" :class="{ 'ccp-compact': compact }">
    <div class="ccp-head">
      <span class="ccp-stage" :title="stageText">{{ stageText }}</span>
      <span v-if="percent !== null" class="ccp-pct">{{ percent }}%</span>
    </div>

    <a-progress
      v-if="percent !== null"
      :percent="percent"
      status="active"
      size="small"
      :show-info="false"
      class="ccp-bar"
    />
    <div v-else class="ccp-indeterminate"><i /></div>

    <div v-if="hint && !compact" class="ccp-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  /** QUEUED / RUNNING / ... */
  status: { type: String, default: '' },
  /** 0-100；为空表示转换器还没上报 */
  progress: { type: Number, default: null },
  /** 转换器给的一句话阶段名，如「网格化零件 42/118」 */
  stage: { type: String, default: '' },
  /** 记录最后更新时间（用于判断"进度是否已经不动了"） */
  updatedAt: { type: [String, Array], default: '' },
  /** 排队位次：前面还有几张（仅 QUEUED 有意义）—— 批量提交时用户最想知道的就是这个 */
  queuePosition: { type: Number, default: null },
  /** 紧凑模式（列表单元格用）：只留一行，不显示补充说明 */
  compact: { type: Boolean, default: false },
})

const running = computed(() => props.status === 'RUNNING')
const queued = computed(() => props.status === 'QUEUED' || !props.status)

/** 百分比：只有"在跑且有值"才画确定进度；排队阶段走不定长 */
const percent = computed(() => {
  if (!running.value) return null
  const p = Number(props.progress)
  if (props.progress === null || props.progress === undefined || Number.isNaN(p)) return null
  return Math.max(0, Math.min(100, Math.round(p)))
})

const stageText = computed(() => {
  if (queued.value) {
    // 位次比"等待处理"有信息量：前者能估算还要多久，后者只能干等
    const n = Number(props.queuePosition)
    return Number.isFinite(n) && n > 0
      ? `已排队，前面还有 ${n} 个`
      : '已排队，等待转换器领取'
  }
  if (!running.value) return '正在处理'
  return props.stage || '转换中'
})

/** 距上次进度更新的秒数（解析不出来就给 null，绝不因此报错） */
function parseTime(value) {
  if (!value) return null
  // Jackson 可能给数组 [y,m,d,h,mi,s]，也可能是 ISO 字符串
  if (Array.isArray(value) && value.length >= 5) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value
    return new Date(y, mo - 1, d, h, mi, s).getTime()
  }
  const t = new Date(String(value).replace(' ', 'T')).getTime()
  return Number.isNaN(t) ? null : t
}

const now = ref(Date.now())
let ticker = null
onMounted(() => {
  ticker = setInterval(() => { now.value = Date.now() }, 5000)
})
onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
})

const staleSeconds = computed(() => {
  if (!running.value) return 0
  const t = parseTime(props.updatedAt)
  if (!t) return 0
  const diff = Math.floor((now.value - t) / 1000)
  return diff > 45 ? diff : 0
})

const hint = computed(() => {
  if (queued.value) return '转换器空闲时会自动开始，无需重复提交'
  if (!running.value) return ''
  if (staleSeconds.value) {
    const s = staleSeconds.value > 90 ? `${Math.floor(staleSeconds.value / 60)} 分钟` : `${staleSeconds.value} 秒`
    return `进度已 ${s} 未更新：转换器可能正忙或回调延迟，系统会自动补查，不必重复提交`
  }
  if (percent.value === null) return '转换器尚未上报进度（大装配体读入阶段耗时较久）'
  return ''
})
</script>

<style scoped>
.ccp {
  width: 100%;
  max-width: 420px;
}
.ccp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 2px;
}
.ccp-stage {
  font-size: 12px;
  color: #595959;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ccp-pct {
  font-size: 12px;
  font-weight: 600;
  color: #1677ff;
  font-variant-numeric: tabular-nums;
}
.ccp-bar {
  margin: 0;
}
.ccp-bar :deep(.ant-progress-outer) {
  margin-right: 0;
}
.ccp-hint {
  margin-top: 4px;
  font-size: 11px;
  color: #bfbfbf;
  line-height: 1.5;
}
.ccp-indeterminate {
  position: relative;
  height: 6px;
  border-radius: 3px;
  background: #f0f0f0;
  overflow: hidden;
}
.ccp-indeterminate i {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 40%;
  border-radius: 3px;
  background: #1677ff;
  animation: ccp-slide 1.4s ease-in-out infinite;
}
@keyframes ccp-slide {
  0% { left: -40%; }
  100% { left: 100%; }
}
.ccp-compact .ccp-stage {
  font-size: 11px;
}
.ccp-compact {
  max-width: none;
}
</style>
