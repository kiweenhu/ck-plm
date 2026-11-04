<template>
  <div class="sb">
    <!-- ① 型号对比表 -->
    <section v-if="hasWidget('MODEL_COMPARE_TABLE')" class="sb-block">
      <div class="sb-head">
        <h4 class="sb-title">型号对比表</h4>
        <span class="sb-hint">
          一行一个型号，四项同口径（交付件分母排除「本项目不适用」与 NA，只有 DONE 算完成；
          阶段门只算决策门 —— 与下面「阶段分布」的门不同，那里把技术评审也算作关卡）——
          <b>这里每一行是这个型号自己的数据</b>，与下面「阶段分布」的当前容器不是一回事；
          当前查看的型号置顶高亮
        </span>
      </div>
      <table v-if="modelRows.length" class="sb-table">
        <thead>
          <tr>
            <th style="width: 120px">型号编码</th>
            <th>名称</th>
            <th style="width: 130px">交付件</th>
            <th style="width: 108px">阶段门</th>
            <th style="width: 108px">评审</th>
            <th style="width: 120px">未闭环问题</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in modelRows" :key="m.oid" :class="{ 'sb-row--current': m.oid === ownerOid }">
            <td class="sb-mono">
              {{ m.code }}
              <a-tag v-if="m.oid === ownerOid" size="small" color="blue">当前</a-tag>
            </td>
            <td>{{ m.name }}</td>
            <td>{{ m.deliverableDone }}/{{ m.deliverableTotal }}
              <span class="sb-muted">· {{ m.percent == null ? '—' : m.percent + '%' }}</span>
            </td>
            <td>{{ m.gatePassed }}/{{ m.gateTotal }}</td>
            <td>{{ m.reviewDone }}/{{ m.reviewTotal }}</td>
            <td>
              <span :class="{ 'sb-block-warn': m.blockOpen > 0 }">{{ m.openTotal }}</span>
              <span v-if="m.blockOpen > 0" class="sb-muted">（阻塞 {{ m.blockOpen }}）</span>
            </td>
          </tr>
        </tbody>
      </table>
      <a-empty v-else-if="!loading && !seriesLoading" description="本系列下暂无型号" />
    </section>

    <!-- ② 阶段分布 -->
    <section v-if="hasWidget('STAGE_DISTRIBUTION')" class="sb-block">
      <div class="sb-head">
        <h4 class="sb-title">阶段分布</h4>
        <span class="sb-hint">
          每个阶段的交付件完成率（绿 = 已完成，橙 = 未完成；<b>条长一律等宽</b>，完成率只看绿色占比）
          + 该阶段关卡「门 x/y」= <b>决策门 + 技术评审</b>（技术评审按「评审结论已完成」计通过，决策门按门状态）；
          <b>这里看的是当前容器自己</b>（系列页 = 本系列，型号页 = 本型号），不是上面各型号的横向对比
        </span>
      </div>
      <div v-if="stageRows.length" class="sb-stage-list">
        <div v-for="s in stageRows" :key="s.code" class="sb-stage">
          <span class="sb-stage-name" :title="s.code">{{ s.name }}</span>
          <!--
            悬停一律用 antd tooltip，不用原生 title（2026-10-30 用户反馈"提示字号与阶段门页签不一致"）：
            原生 title 是系统字号与系统配色，同一屏里两套浮层看起来像两个产品。
            浮层正文统一走 .sb-tip（12px / 1.6），与 MethodologyInstancePanel 的 .mi-tip 同口径。
          -->
          <a-tooltip placement="top" :overlay-style="{ maxWidth: '420px' }">
            <template #title>
              <div class="sb-tip">
                <div class="sb-tip-head">{{ s.barTip.head }}</div>
                <div v-if="s.barTip.note" class="sb-tip-note">{{ s.barTip.note }}</div>
                <div v-for="(line, i) in s.barTip.items" :key="i" class="sb-tip-line">· {{ line }}</div>
              </div>
            </template>
            <div class="sb-bar">
              <div class="sb-bar-total">
                <div class="sb-bar-done" :style="{ width: s.donePercent + '%' }" />
              </div>
            </div>
          </a-tooltip>
          <span class="sb-stage-count">{{ s.done }}/{{ s.total }}</span>
          <a-tooltip placement="top" :overlay-style="{ maxWidth: '420px' }">
            <template #title>
              <div class="sb-tip">
                <div class="sb-tip-head">{{ s.gateTip.head }}</div>
                <div v-for="(g, i) in s.gateTip.items" :key="i" class="sb-tip-line">
                  <span class="sb-tip-verdict" :class="g.passed ? 'is-pass' : 'is-fail'">{{ g.passed ? '✓' : '✗' }}</span>{{ g.text }}
                </div>
              </div>
            </template>
            <span
              class="sb-stage-gate"
              :class="{ 'sb-block-warn': s.gateTotal && s.gatePassed < s.gateTotal }"
            >
              {{ s.gateTotal ? `门 ${s.gatePassed}/${s.gateTotal}` : '无门' }}
            </span>
          </a-tooltip>
        </div>
      </div>
      <a-empty v-else-if="!loading" description="尚未初始化研发阶段" />
    </section>

    <!-- ③ 成本趋势 -->
    <section v-if="hasWidget('COST_TREND')" class="sb-block">
      <div class="sb-head">
        <h4 class="sb-title">成本趋势</h4>
        <span class="sb-hint">{{ costTrend.basis || '折线＝各 BOM 快照时点的卷积成本，末点为当前 BOM 的卷积成本' }}</span>
      </div>

      <div v-if="costLoading" class="sb-cost-loading">
        <a-spin size="small" />
        <span>正在计算成本...</span>
      </div>
      <template v-else>
        <!-- 现值：这是能直接对外汇报的数字，所以单独放大；口径为卷积（含各层） -->
        <div class="sb-cost-summary">
          <div class="sb-cost-main">
            <span class="sb-cost-label">当前卷积总成本</span>
            <span class="sb-cost-value">{{ formatMoney(costTrend.currentTotalCost) }}</span>
            <span class="sb-cost-sub">曲线末点即该值</span>
          </div>
          <div class="sb-cost-stats">
            <span>顶层件 {{ costTrend.topPartCount || 0 }}</span>
            <span>BOM 行 {{ costTrend.totalLines || 0 }}</span>
            <span :class="{ 'sb-block-warn': costTrend.missingCostLines > 0 }">
              缺单位成本 {{ costTrend.missingCostLines || 0 }} 行
            </span>
          </div>
        </div>

        <!--
          曲线数据源就是 BOM 快照（每个顶层件一条线，末点为"当前"实时值）：
          只要在「BOM 结构 → 快照」存过 1 份，这里就有"那时 → 现在"的一段变化，不需要另记成本快照。

          画法：先量出容器真实宽度（ResizeObserver），再按像素算坐标 ——
          不能用 viewBox + preserveAspectRatio="none" 硬拉伸：那样圆点会被压成椭圆、线宽和文字全变形，
          两点数据会被摊成一条贯穿全宽的斜线，看着像"成本翻了几倍"。
        -->
        <div v-if="costChart.ready" class="sb-chart">
          <div class="sb-chart-body">
            <!-- 纵轴刻度：最大 / 中值 / 最小（金额取整到元，避免小卡片里挤一长串小数） -->
            <div class="sb-chart-y">
              <span>{{ costChart.maxLabel }}</span>
              <span class="sb-chart-y-mid">{{ costChart.midLabel }}</span>
              <span>{{ costChart.minLabel }}</span>
            </div>
            <div ref="plotRef" class="sb-chart-plot" :style="{ height: `${PLOT_H}px` }">
              <svg :width="plotWidth" :height="PLOT_H" class="sb-chart-svg">
                <defs>
                  <linearGradient id="sbCostFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#1677ff" stop-opacity="0.16" />
                    <stop offset="100%" stop-color="#1677ff" stop-opacity="0" />
                  </linearGradient>
                </defs>
                <line
                  v-for="g in costChart.grids"
                  :key="g.key"
                  :x1="0" :y1="g.y" :x2="plotWidth" :y2="g.y"
                  class="sb-chart-grid"
                />
                <path v-if="costChart.areaPath" :d="costChart.areaPath" fill="url(#sbCostFill)" />
                <path
                  v-for="line in costChart.lines"
                  :key="line.key"
                  :d="line.path"
                  fill="none"
                  :stroke="line.color"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  class="sb-chart-line"
                />
                <circle
                  v-for="dot in costChart.dots"
                  :key="dot.key"
                  :cx="dot.x" :cy="dot.y"
                  :r="dot.current ? 5 : 3.5"
                  :fill="dot.current ? dot.color : '#fff'"
                  :stroke="dot.color"
                  stroke-width="2"
                >
                  <title>{{ dot.title }}</title>
                </circle>
              </svg>
              <!-- 末点气泡：直接给"较首份快照变化多少"，比再复述一遍总额有用 -->
              <span v-if="costChart.tagText" class="sb-chart-tag" :class="costChart.tagClass" :style="costChart.tagStyle">
                {{ costChart.tagText }}
              </span>
            </div>
          </div>
          <div class="sb-chart-x">
            <span>{{ costChart.firstText }}</span>
            <span class="sb-chart-x-mid">{{ costChart.midText }}</span>
            <span>{{ costChart.lastText }}</span>
          </div>
          <!-- 多个顶层件才需要图例；只有一条线时件号已在上面说明里 -->
          <div v-if="costChart.lines.length > 1" class="sb-cost-legend">
            <span v-for="line in costChart.lines" :key="line.key">
              <i :style="{ background: line.color }" />{{ line.label }}{{ line.currentText }}
            </span>
          </div>
        </div>

        <!-- 画不了线时：说清真实缺口（有没有 BOM、缺哪些单位成本、有没有快照） -->
        <div v-else class="sb-cost-empty">
          <p class="sb-cost-lead">
            {{ costTrend.hasBom
              ? '当前成本已可算，但还没有 BOM 快照，画不出"过去 → 现在"。'
              : '当前容器（含旗下型号）还没有 BOM 行，成本为 0。' }}
          </p>
          <ul v-if="costTrend.warnings?.length" class="sb-cost-why">
            <li v-for="(w, i) in costTrend.warnings" :key="i">{{ w }}</li>
          </ul>
          <p class="sb-cost-tip">
            补数路径：先补齐「物料清单」每行的「单位成本」，再到「BOM 结构 → 快照」按版本保存 BOM 快照；
            快照会被直接画成成本曲线（末点为当前值）。
            <template v-if="costTrend.missingCostNumbers?.length">
              当前缺单位成本的顶层件：{{ costTrend.missingCostNumbers.join('、') }}。
            </template>
          </p>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup>
/**
 * 「系列战况简报」视角的三块分组件（2026-10-26）：型号对比表 / 阶段分布 / 成本趋势。
 *
 * <p>受众是 GM + 研发总监，看的是"这个系列下各型号横向比"。数据来源：
 * <ul>
 *   <li>型号清单 {@code GET /api/product-models?productLineOid=}；</li>
 *   <li>每个型号的六类实例 {@code GET /api/instances}（算交付件 / 门 / 评审）；</li>
 *   <li>每个型号的未闭环问题 {@code GET /api/issues/gate-check}（与经营战报的风险同一份）；</li>
 *   <li>成本趋势 {@code GET /api/bom-links/cost-trend/{containerOid}}（后端算卷积成本 + 快照点）；</li>
 *   <li>阶段分布直接用父页面已取的实例，不再发请求。</li>
 * </ul>
 *
 * <h3>成本趋势：一条曲线，口径统一为卷积</h3>
 * <p>大数字与折线是同口径的<b>卷积总成本</b>（每行「数量 × 子件完整单位成本」逐层累加）：
 * 快照点用「快照里的用量 × 子件在那个版本上的完整单位成本」回放，末点是当前 BOM 的实时值。
 * 所以只要存过 1 份 BOM 快照，图上就有「那时 → 现在」两个点，无需额外的"成本打点"。
 * 一条平线等于宣称"成本没变过"，所以点不足 2 个时宁可不画。
 */
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import {
  getIssueGateCheck,
  getProductModels,
  listMethodologyInstances,
  getContainerCostTrend,
} from '@/api'
import { isDecisionGate } from '@/utils/gateDefs'

const props = defineProps({
  /** 当前容器 oid（系列或型号）—— 用于高亮"当前"那一行 */
  ownerOid: { type: String, default: '' },
  /** 型号清单的取数依据（当前是型号时传它的父系列） */
  seriesOid: { type: String, default: '' },
  widgets: { type: Array, default: () => [] },
  stages: { type: Array, default: () => [] },
  deliverables: { type: Array, default: () => [] },
  gates: { type: Array, default: () => [] },
  reviews: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

/** 某视角是否声明了该分组件（没配过 widgets 的老数据：三块都渲染） */
function hasWidget(code) {
  if (!props.widgets.length) return true
  return props.widgets.includes(code)
}

/** 分母口径：未裁剪、且不是 NA —— 与阶段门的 DELIVERABLE_RATE 一致 */
function counted(d) {
  return !d.disabled && d.status !== 'NA'
}

/* ==================== ① 型号对比表 ==================== */

const modelRows = ref([])
const seriesLoading = ref(false)

async function loadModels() {
  const seriesOid = props.seriesOid || props.ownerOid
  if (!seriesOid) return
  seriesLoading.value = true
  try {
    const res = await getProductModels({ productLineOid: seriesOid })
    const models = res?.code === 200 ? (res.data || []) : []
    const rows = await Promise.all(models.map(async (m) => {
      const row = {
        oid: m.oid, code: m.code, name: m.name,
        deliverableDone: 0, deliverableTotal: 0, percent: null,
        gatePassed: 0, gateTotal: 0, reviewDone: 0, reviewTotal: 0,
        openTotal: 0, blockOpen: 0
      }
      // 实例与问题并行：两者互不依赖，串起来要等两个往返
      const [inst, issues] = await Promise.all([
        listMethodologyInstances(m.oid).catch(() => null),
        getIssueGateCheck(m.oid).catch(() => null)
      ])
      if (inst?.code === 200 && inst.data) {
        const ds = (inst.data.deliverables || []).filter(counted)
        // 只算决策门：老库里残留的技术门行没有决策、永远不会 PASSED，算进来分母虚高（见 isDecisionGate）
        const gs = (inst.data.gates || []).filter((g) => isDecisionGate(g) && !g.disabled)
        const rs = (inst.data.reviews || []).filter((r) => !r.disabled)
        row.deliverableDone = ds.filter((d) => d.status === 'DONE').length
        row.deliverableTotal = ds.length
        row.percent = ds.length ? Math.round((row.deliverableDone * 100) / ds.length) : null
        row.gateTotal = gs.length
        row.gatePassed = gs.filter((g) => g.status === 'PASSED').length
        row.reviewTotal = rs.length
        row.reviewDone = rs.filter((r) => r.status === 'DONE').length
      }
      if (issues?.code === 200 && issues.data) {
        row.openTotal = issues.data.openTotal ?? 0
        row.blockOpen = issues.data.blockOpen ?? 0
      }
      return row
    }))
    // 当前查看的型号置顶：对比表的第一行应该是"我正在看的那个"
    modelRows.value = rows.sort((a, b) => {
      if (a.oid === props.ownerOid) return -1
      if (b.oid === props.ownerOid) return 1
      return String(a.code || '').localeCompare(String(b.code || ''))
    })
  } catch {
    modelRows.value = []
  } finally {
    seriesLoading.value = false
  }
}

/* ==================== ② 阶段分布 ==================== */

/**
 * 阶段「关卡」是否已通过 —— 两类门两套判据（2026-10-30 用户口径："两个门也已经完成了"）：
 * <ul>
 *   <li><b>决策门</b>：看门本身的 {@code status}（{@link GateProcessEvaluator} 现算：流程走完且登记了
 *       决策才 PASSED）；</li>
 *   <li><b>技术评审</b>：技术门这一层已在 2026-10-29 取消，老容器里还留着
 *       {@code type=TECHNICAL_REVIEW} 的门行 —— 它们永远不会有决策，只能按<b>它名下那几场评审</b>
 *       判断（全部 DONE 才算过）。一场评审都没挂的量不算过，宁缺勿滥。</li>
 * </ul>
 */
/** 该门名下那几场评审（老数据：评审行的 gate 列直接指向门码 TR1 / TR3 …；
 *  新数据没这一列时按评审码前缀认亲：TR1 ↔ TR1_CONCEPT） */
function reviewsOfGate(gate) {
  const rows = (props.reviews || []).filter((r) => r && !r.disabled)
  const byGate = rows.filter((r) => r.gate === gate.code)
  if (byGate.length) return byGate
  return rows.filter((r) => r.code === gate.code || (r.code || '').startsWith(`${gate.code}_`))
}

/** 决策门的状态中文（码与门面板同一套：GateProcessEvaluator 现算） */
const GATE_STATUS_TEXT = {
  PENDING: '未开始',
  IN_PROGRESS: '进行中',
  PASSED: '已通过',
  REJECTED: '已否决',
  DONE: '已走完（未登记决策）'
}

/**
 * 一道门「过没过 + 为什么」—— 计数与悬停提示共用这一份结果，避免"提示说过了、数字说没过"。
 *
 * <p>{@code note} 就是给用户看的一句人话：决策门给状态（进行中 / 未开始 / 已否决），
 * 技术评审给它名下评审的进度（{@code 评审 1/2 已完成}）。
 */
function gateDetail(gate) {
  const name = gate.name || gate.code
  if (isDecisionGate(gate)) {
    return { code: gate.code, name, passed: gate.status === 'PASSED', note: GATE_STATUS_TEXT[gate.status] || '未开始' }
  }
  const related = reviewsOfGate(gate)
  const done = related.filter((r) => r.status === 'DONE').length
  return {
    code: gate.code,
    name,
    passed: related.length > 0 && done === related.length,
    note: related.length ? `名下评审完成 ${done}/${related.length}` : '名下无评审'
  }
}

/** 浮层里最多列几条明细：再多就盖住整张卡了，超出的只报数量 */
const MAX_TIP_ITEMS = 10

/**
 * 交付件进度条的悬停内容（结构化，交给 antd tooltip 渲染）。
 *
 * <p>条长改成等宽之后，绿条只能读出"完成率"这一个数，到底哪几项还没交必须能问出来
 * （2026-10-30 用户反馈"鼠标移到交付件进度条上没有提示"）。
 */
function stageDeliverableTip(stageName, rows, done) {
  if (!rows.length) {
    return { head: `${stageName}：本阶段没有计入统计的交付件（不适用 / NA 不计入）`, note: '', items: [] }
  }
  const head = `${stageName}：交付件 ${done}/${rows.length} 已完成（${Math.round((done / rows.length) * 100)}%）`
  const pending = rows.filter((d) => d.status !== 'DONE')
  if (!pending.length) {
    return { head, note: '全部完成', items: [] }
  }
  return {
    head,
    note: pending.length > MAX_TIP_ITEMS
      ? `未完成 ${pending.length} 项（只列前 ${MAX_TIP_ITEMS} 项）：`
      : `未完成 ${pending.length} 项：`,
    items: pending
      .slice(0, MAX_TIP_ITEMS)
      .map((d) => [d.code, d.name].filter(Boolean).join(' ') || '未命名交付件')
  }
}

const stageRows = computed(() => {
  const list = [...(props.stages || [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  return list.map((st) => {
    const own = (props.deliverables || []).filter((d) => d.stageKey === st.code && counted(d))
    const done = own.filter((d) => d.status === 'DONE').length
    // 关卡 = 该阶段的门（决策门 + 技术门）：技术门按名下评审的结论判，见 gateDetail
    const gs = (props.gates || []).filter((g) => g && g.stageKey === st.code && !g.disabled)
    const details = gs.map((g) => gateDetail(g))
    const passed = details.filter((g) => g.passed).length
    return {
      code: st.code,
      name: st.name || st.code,
      done,
      total: own.length,
      // 条长改为等宽（2026-10-30 用户反馈："概念阶段 2/2，绿色却只占一小截" ——
      // 原来条长=该阶段交付件数占比，把"数量少"画成了"没做完"；数量差异改由右侧 x/y 表达）
      donePercent: own.length ? Math.round((done / own.length) * 100) : 0,
      // 悬停提示：说清本阶段还有哪几项没交（绿条只表达比例，明细只能靠这句话）
      barTip: stageDeliverableTip(st.name || st.code, own, done),
      gateTotal: gs.length,
      gatePassed: passed,
      // 悬停提示：逐条说清哪道门过了、哪道没过（2026-10-30 用户反馈"鼠标移动到门上没有提示"）
      gateTip: {
        head: details.length ? `阶段门 ${passed}/${details.length}（决策门 + 技术评审）` : '本阶段没有门',
        items: details.map((d) => ({ passed: d.passed, text: `${d.code} ${d.name} —— ${d.note}` }))
      }
    }
  })
})

/* ==================== ③ 成本趋势 ==================== */

const costLoading = ref(false)
/** 后端口径：{ currentTotalCost, currentDirectCost, series[{partNumber,versionLabel,currentCost,points[]}], warnings[] } */
const costTrend = ref({})

/**
 * 画布：高度固定，宽度按容器真实像素量出来（ResizeObserver）。
 * 坐标 = 像素，所以圆点不会被压成椭圆、线宽也不会随容器变形。
 */
const PLOT_H = 124
const PAD_L = 8
const PAD_R = 16
const PAD_T = 18
const PAD_B = 14
/** 线多了这张小卡就成蜘蛛网：最多画 3 个顶层件（后端已按当前成本降序，取最贵的几个） */
const MAX_COST_LINES = 3
const COST_LINE_COLORS = ['#1677ff', '#52c41a', '#fa8c16']

const plotRef = ref(null)
const plotWidth = ref(600)
let plotObserver = null

// 图是 v-if 出来的，ref 出现/消失都要重挂钩子（模板 ref 的 watch 正好覆盖这两种时机）
watch(plotRef, (el) => {
  if (plotObserver) {
    plotObserver.disconnect()
    plotObserver = null
  }
  if (!el || typeof ResizeObserver === 'undefined') return
  plotObserver = new ResizeObserver((entries) => {
    const width = Math.round(entries[0]?.contentRect?.width || 0)
    if (width > 0) plotWidth.value = width
  })
  plotObserver.observe(el)
  plotWidth.value = Math.round(el.clientWidth || plotWidth.value)
})

onBeforeUnmount(() => {
  if (plotObserver) plotObserver.disconnect()
})

function fmtDate(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.slice(0, 16)
}

function formatMoney(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return `¥ ${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/** 纵轴刻度用的短金额：整数不带小数，否则一位小数 —— 小卡片里塞不下 ¥295.00 */
function compactMoney(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return `¥${num.toLocaleString('zh-CN', { maximumFractionDigits: Number.isInteger(num) ? 0 : 1 })}`
}

/** 时间戳：后端是 LocalDateTime（无时区），按本地时间解析即可 */
function toTs(value) {
  if (!value) return null
  const ts = Date.parse(String(value))
  return Number.isFinite(ts) ? ts : null
}

/** 时间戳 → 本地时间文案（不能用 toISOString，那是 UTC，会差 8 小时） */
function fmtTs(ts) {
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const round2 = (v) => Math.round(v * 100) / 100

/**
 * 纵轴刻度取"整数档"（1 / 2 / 5 × 10ⁿ）：否则会出现 ¥283.2 / ¥290 / ¥296.8 这种刻度，
 * 看着像随手算的。取整后线也不会贴着上下边框。
 */
function niceDomain(minValue, maxValue) {
  const span = maxValue - minValue
  const rawStep = span > 0 ? span / 2 : Math.max(Math.abs(maxValue) * 0.1, 1)
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep) || 0))
  const normalized = rawStep / magnitude
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude
  const min = Math.max(0, Math.floor(minValue / step) * step)
  const max = Math.ceil(maxValue / step) * step
  return { min, max: max > min ? max : min + step }
}

/**
 * 曲线路径：2 个点直连；3 个点以上用「中点二次贝塞尔」轻平滑。
 * 不用 Catmull-Rom：它会过冲，成本曲线冲到负值区间比折线难看一百倍。
 */
function buildPath(points) {
  if (!points.length) return ''
  if (points.length === 1) {
    return `M${round2(points[0].x)},${round2(points[0].y)}`
  }
  if (points.length === 2) {
    return `M${round2(points[0].x)},${round2(points[0].y)} L${round2(points[1].x)},${round2(points[1].y)}`
  }
  let d = `M${round2(points[0].x)},${round2(points[0].y)}`
  for (let i = 1; i < points.length; i += 1) {
    const prev = points[i - 1]
    const cur = points[i]
    d += ` Q${round2(prev.x)},${round2(prev.y)} ${round2((prev.x + cur.x) / 2)},${round2((prev.y + cur.y) / 2)}`
  }
  const last = points[points.length - 1]
  return `${d} L${round2(last.x)},${round2(last.y)}`
}

/**
 * 曲线几何：横轴时间、纵轴卷积成本，坐标按像素算。
 *
 * <p>三条视觉规则：
 * <ul>
 *   <li>纵轴上下各留 18% 余量：3% 的波动不该画成"贴天花板"，也不该被压成一条直线看不出变化；</li>
 *   <li>多条线共用同一纵轴（否则"谁更贵"看不出来），面积渐变只在单条线时画（多了会糊）；</li>
 *   <li>末点气泡给"较首份快照的变化"，比把总额再说一遍有用。</li>
 * </ul>
 */
const costChart = computed(() => {
  const empty = {
    ready: false, lines: [], dots: [], grids: [],
    maxLabel: '', midLabel: '', minLabel: '',
    firstText: '', midText: '', lastText: '',
    areaPath: '', tagText: '', tagStyle: {}, tagClass: 'flat',
  }
  const rawSeries = (costTrend.value?.series || []).slice(0, MAX_COST_LINES)
  const series = rawSeries
    .map((s, i) => ({
      key: s.partOid || `${s.partNumber || i}`,
      color: COST_LINE_COLORS[i % COST_LINE_COLORS.length],
      label: `${s.partNumber || ''} ${s.versionLabel || ''}`.trim() || '当前零件',
      currentText: s.currentCost === undefined ? '' : ` ${formatMoney(s.currentCost)}`,
      points: (s.points || [])
        .map((p) => ({
          ts: toTs(p.time),
          cost: Number(p.cost) || 0,
          label: p.label || '',
          versionLabel: p.versionLabel || '',
          current: !!p.current,
          timeText: fmtDate(p.time),
        }))
        .filter((p) => p.ts !== null),
    }))
    // 只有一个点（没有快照、只有"当前"）画不出趋势，直接不画
    .filter((s) => s.points.length >= 2)

  if (!series.length) return empty

  const width = Math.max(plotWidth.value, 240)
  const allPoints = series.flatMap((s) => s.points)
  const times = allPoints.map((p) => p.ts)
  const costs = allPoints.map((p) => p.cost)
  const tMin = Math.min(...times)
  const tMax = Math.max(...times)
  const vMin = Math.min(...costs)
  const vMax = Math.max(...costs)
  // 先留 18% 余量再取整到整数档：3% 的波动不该画成"贴天花板"，也不该被压成一条直线
  const cushion = vMax - vMin > 0 ? (vMax - vMin) * 0.18 : Math.max(vMax * 0.05, 1)
  const domain = niceDomain(Math.max(0, vMin - cushion), vMax + cushion)
  const dMin = domain.min
  const dMax = domain.max
  const innerW = Math.max(width - PAD_L - PAD_R, 10)
  const innerH = PLOT_H - PAD_T - PAD_B
  const tSpan = tMax - tMin
  const xOf = (ts) => (tSpan === 0 ? PAD_L + innerW / 2 : PAD_L + (innerW * (ts - tMin)) / tSpan)
  const yOf = (cost) => PAD_T + innerH - (innerH * (cost - dMin)) / (dMax - dMin)

  const lines = []
  const dots = []
  for (const s of series) {
    const coords = s.points.map((p) => ({ x: xOf(p.ts), y: yOf(p.cost) }))
    lines.push({ key: s.key, color: s.color, label: s.label, currentText: s.currentText, path: buildPath(coords) })
    s.points.forEach((p, idx) => {
      dots.push({
        key: `${s.key}-${idx}`,
        x: round2(coords[idx].x),
        y: round2(coords[idx].y),
        color: s.color,
        current: p.current,
        title: `${p.timeText}｜${s.label}${p.versionLabel ? ` ${p.versionLabel}` : ''}｜${p.label}｜卷积成本 ${formatMoney(p.cost)}`,
      })
    })
  }

  // 面积只在单条线时画：多条线叠一块儿会糊成一片
  const baseline = PLOT_H - PAD_B
  const areaPath = series.length === 1
    ? `${lines[0].path} L${round2(PAD_L + innerW)},${baseline} L${round2(PAD_L)},${baseline} Z`
    : ''

  // 末点气泡：较首份快照涨/跌多少（第一个点就是最早那份快照）
  const main = series[0]
  const firstPoint = main.points[0]
  const lastPoint = main.points[main.points.length - 1]
  const delta = lastPoint.cost - firstPoint.cost
  const pct = firstPoint.cost > 0 ? (delta / firstPoint.cost) * 100 : null
  const tagText = Math.abs(delta) < 0.005
    ? '较首份快照持平'
    : `${delta > 0 ? '+' : '−'}¥${Math.abs(delta).toFixed(2)}${pct === null ? '' : `（${delta > 0 ? '+' : '−'}${Math.abs(pct).toFixed(1)}%）`}`
  const lastX = xOf(lastPoint.ts)
  const lastY = yOf(lastPoint.cost)
  // 末点靠顶时把气泡放到点的下方（否则会顶到摘要条上），靠右边时翻到点左侧
  const above = lastY > PAD_T + 26
  const flipX = lastX > width - 110
  const tagStyle = {
    left: `${round2(lastX)}px`,
    top: `${round2(above ? lastY - 12 : lastY + 14)}px`,
    transform: `translate(${flipX ? '-100%' : '0'}, ${above ? '-100%' : '0'})`,
  }

  const snapshotCount = series.reduce(
    (sum, s) => sum + s.points.filter((p) => !p.current).length,
    0,
  )

  return {
    ready: true,
    lines,
    dots,
    grids: [
      { key: 'top', y: PAD_T },
      { key: 'mid', y: round2(PAD_T + innerH / 2) },
      { key: 'bottom', y: round2(PAD_T + innerH) },
    ],
    maxLabel: compactMoney(dMax),
    midLabel: compactMoney((dMin + dMax) / 2),
    minLabel: compactMoney(dMin),
    firstText: fmtTs(tMin),
    lastText: fmtTs(tMax),
    midText: `${snapshotCount} 个快照点 + 当前值`,
    areaPath,
    tagText,
    tagStyle,
    tagClass: Math.abs(delta) < 0.005 ? 'flat' : (delta > 0 ? 'up' : 'down'),
  }
})

async function loadCostTrend() {
  const containerOid = props.ownerOid
  if (!containerOid) {
    costTrend.value = {}
    return
  }
  costLoading.value = true
  try {
    const res = await getContainerCostTrend(containerOid)
    costTrend.value = res?.code === 200 ? (res.data || {}) : {}
    if (res?.code !== 200) {
      message.error(res?.message || '成本趋势取数失败')
    }
  } catch (e) {
    costTrend.value = {}
    message.error(e?.response?.data?.message || '成本趋势取数失败')
  } finally {
    costLoading.value = false
  }
}

/* ==================== 加载 ==================== */

watch(() => [props.ownerOid, props.seriesOid], () => loadModels())
onMounted(() => loadModels())

// 成本跟随"当前容器"：系列页看整条系列，型号页只看该型号
watch(() => props.ownerOid, () => loadCostTrend(), { immediate: true })
</script>

<style scoped>
.sb { display: flex; flex-direction: column; gap: 14px; }
.sb-block { border: 1px solid #f0f0f0; border-radius: 6px; padding: 10px 12px 12px; background: #fff; }
.sb-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.sb-title { margin: 0; font-size: 13px; font-weight: 600; color: #262626; }
.sb-hint { color: #8c8c8c; font-size: 11px; line-height: 1.5; }
.sb-muted { color: #8c8c8c; }
.sb-mono { font-family: monospace; font-size: 12px; }
.sb-block-warn { color: #ff4d4f; }

/* ① 型号对比表 */
.sb-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.sb-table th {
  text-align: left; font-weight: 500; color: #8c8c8c; padding: 4px 6px;
  border-bottom: 1px solid #f0f0f0; white-space: nowrap;
}
.sb-table td { padding: 6px; border-bottom: 1px solid #fafafa; color: #262626; }
.sb-row--current td { background: #f0f7ff; }

/* ② 阶段分布 */
.sb-stage-list { display: flex; flex-direction: column; gap: 7px; }
.sb-stage { display: grid; grid-template-columns: 96px 1fr 64px 76px; align-items: center; gap: 10px; }
.sb-stage-name { font-size: 12px; color: #595959; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 悬停看本阶段未完成的交付件明细，所以给个"可问"的手型（与门那一列同一约定） */
.sb-bar { height: 12px; background: #fafafa; border-radius: 3px; overflow: hidden; cursor: help; }
/* 轨道等宽：条长不再编码"该阶段有几件事"，只把完成率画出来（数量看右侧 x/y） */
.sb-bar-total { width: 100%; height: 100%; background: #ffe7ba; border-radius: 3px; }
.sb-bar-done { height: 100%; background: #95de64; transition: width .2s; }
.sb-stage-count { font-size: 12px; color: #262626; text-align: right; font-variant-numeric: tabular-nums; }
/* 悬停看逐门明细，所以给个"可问"的手型（内容由 antd tooltip 出） */
.sb-stage-gate { font-size: 12px; color: #8c8c8c; text-align: right; white-space: nowrap; cursor: help; }

/* ===== 悬停浮层正文 =====
   深色 antd tooltip 底上的浅色文字，字号 / 行高 / 小标题字重与「阶段门」页签
   的 .mi-tip（MethodologyInstancePanel.vue）逐项一致 —— 同一屏两套字号会被一眼看出来。 */
.sb-tip { max-width: 420px; font-size: 12px; line-height: 1.6; }
.sb-tip-head { font-weight: 600; margin-bottom: 4px; }
.sb-tip-note { opacity: 0.8; }
.sb-tip-line { opacity: 0.9; }
.sb-tip-verdict { display: inline-block; width: 12px; font-weight: 700; }
.sb-tip-verdict.is-pass { color: #b7eb8f; }
.sb-tip-verdict.is-fail { color: #ff7875; }

/* ③ 成本趋势 */
.sb-cost-loading { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #8c8c8c; padding: 8px 0; }
/* 现值条：大数字给汇报用，右边三个计数用于判断这个数字可不可信（缺成本行越多越不可信） */
.sb-cost-summary {
  display: flex; align-items: flex-end; justify-content: space-between;
  gap: 12px; flex-wrap: wrap; padding: 2px 0 10px;
}
.sb-cost-main { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.sb-cost-label { font-size: 12px; color: #8c8c8c; }
.sb-cost-value { font-size: 20px; font-weight: 600; color: #262626; font-variant-numeric: tabular-nums; }
.sb-cost-sub { font-size: 11px; color: #8c8c8c; }
.sb-cost-stats { display: flex; gap: 12px; font-size: 12px; color: #595959; }
/* 曲线：左侧纵轴刻度 + 右侧 1:1 像素画布（宽度由 ResizeObserver 量出来） */
.sb-chart { padding: 2px 0 0; }
.sb-chart-body { display: flex; align-items: stretch; gap: 8px; }
.sb-chart-y {
  width: 54px; display: flex; flex-direction: column; justify-content: space-between;
  /* 上下留白与画布 PAD_T / PAD_B 对齐，刻度才正好压在网格线上 */
  padding: 18px 0 14px; text-align: right;
  font-size: 11px; color: #8c8c8c; font-variant-numeric: tabular-nums; line-height: 1;
}
.sb-chart-y-mid { color: #bfbfbf; }
.sb-chart-plot { position: relative; flex: 1; min-width: 0; }
.sb-chart-svg { display: block; overflow: visible; }
.sb-chart-grid { stroke: #f0f0f0; stroke-width: 1; stroke-dasharray: 3 4; shape-rendering: crispEdges; }
/* 线条淡入：数据刷新时不要"闪一下换形状" */
.sb-chart-line { animation: sb-line-in .35s ease-out; }
@keyframes sb-line-in { from { opacity: 0; } to { opacity: 1; } }
/* 末点气泡：贴在最后一点上方，右边界自动翻到左侧 */
.sb-chart-tag {
  position: absolute; white-space: nowrap; pointer-events: none;
  font-size: 11px; font-variant-numeric: tabular-nums;
  padding: 1px 6px; border-radius: 4px;
  background: rgba(255, 255, 255, .92); border: 1px solid #e8e8e8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .06);
}
.sb-chart-tag.up { color: #cf1322; border-color: #ffccc7; }
.sb-chart-tag.down { color: #389e0d; border-color: #b7eb8f; }
.sb-chart-tag.flat { color: #8c8c8c; }
.sb-chart-x {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 11px; color: #8c8c8c; margin-top: 2px; padding-left: 62px;
}
.sb-chart-x-mid { color: #bfbfbf; }
/* 图例只在多条线时出现：色块 + 件号版本 + 当前值 */
.sb-cost-legend { display: flex; gap: 14px; flex-wrap: wrap; font-size: 11px; color: #595959; margin-top: 4px; }
.sb-cost-legend i { display: inline-block; width: 10px; height: 3px; border-radius: 2px; margin-right: 5px; vertical-align: middle; }
.sb-cost-empty { padding: 4px 0 2px; }
.sb-cost-lead { margin: 0 0 8px; font-size: 12px; color: #d46b08; }
.sb-cost-why { margin: 0 0 8px; padding-left: 18px; font-size: 12px; color: #595959; line-height: 1.9; }
.sb-cost-why code { padding: 1px 4px; background: #fafafa; border: 1px solid #f0f0f0; border-radius: 3px; font-size: 11px; }
.sb-cost-tip { margin: 0; font-size: 11px; color: #8c8c8c; }
</style>
