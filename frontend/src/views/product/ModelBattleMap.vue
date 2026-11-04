<template>
  <div class="bm">
    <a-spin :spinning="loading">
      <!-- ① 阶段就绪度：我在哪一段、差多少 -->
      <section v-if="hasWidget('STAGE_READINESS')" class="bm-block">
        <div class="bm-head">
          <h4 class="bm-title">阶段就绪度</h4>
          <span class="bm-hint">
            点阶段可筛选下方两块；完成率 = 已完成 / 应交付（分母与阶段门的 DELIVERABLE_RATE 同一口径：
            排除「本项目不适用」与 NA，只有 DONE 算完成）
          </span>
        </div>
        <div v-if="stageStats.length" class="bm-track">
          <div
            v-for="s in stageStats"
            :key="s.oid"
            class="bm-step"
            :class="{ 'bm-step--active': s.code === currentCode }"
            :title="s.percent == null ? `${s.name}：本阶段无应交付项` : `${s.name}：${s.done}/${s.total}`"
            @click="currentCode = s.code"
          >
            <div class="bm-step-head">
              <span class="bm-gate-dot" :style="{ background: gateDotColor(s) }" />
              <span class="bm-step-name">{{ s.name }}</span>
            </div>
            <a-progress
              :percent="s.percent == null ? 0 : s.percent"
              :show-info="false"
              size="small"
              :stroke-color="progressColor(s.percent)"
            />
            <div class="bm-step-foot">
              <span v-if="s.percent == null" class="bm-muted">— 无应交付项</span>
              <span v-else :class="{ 'bm-muted': s.percent === 0 }">{{ s.done }}/{{ s.total }} · {{ s.percent }}%</span>
            </div>
          </div>
        </div>
        <a-empty v-else-if="!loading" description="尚未初始化研发阶段" />
      </section>

      <!-- ② 交付件矩阵：差的具体是什么、在哪个专业 -->
      <section v-if="hasWidget('DELIVERABLE_MATRIX')" class="bm-block">
        <div class="bm-head">
          <h4 class="bm-title">交付件矩阵</h4>
          <span class="bm-hint">
            行 = 专业，列 = 阶段；格内为「已完成 / 应交付」。空白 = 该专业在此阶段无交付件（不是 0%），
            点格子进详情页该阶段的交付件面板
          </span>
        </div>
        <div v-if="matrixRows.length" class="bm-matrix-wrap">
          <table class="bm-matrix">
            <thead>
              <tr>
                <th class="bm-corner">专业</th>
                <th
                  v-for="st in stageStats"
                  :key="st.oid"
                  :class="{ 'bm-th-active': st.code === currentCode }"
                >{{ st.name }}</th>
                <th class="bm-th-total">合计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in matrixRows" :key="row.code">
                <th class="bm-rowhead">{{ row.label }}</th>
                <td
                  v-for="st in stageStats"
                  :key="st.oid"
                  class="bm-cell"
                  :class="cellClass(row.code, st.code)"
                  :title="cellTitle(row.code, st.code)"
                  @click="openStage(st.code, 'deliverable')"
                >
                  <template v-if="cellOf(row.code, st.code).total">
                    {{ cellOf(row.code, st.code).done }}/{{ cellOf(row.code, st.code).total }}
                  </template>
                </td>
                <td class="bm-cell bm-cell--total">{{ row.done }}/{{ row.total }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <a-empty v-else-if="!loading" description="暂无交付件" />
      </section>

      <!-- ③ 评审状态：下一步该开什么会、谁在办 -->
      <section v-if="hasWidget('REVIEW_STATUS')" class="bm-block">
        <div class="bm-head">
          <h4 class="bm-title">评审状态</h4>
          <span class="bm-hint">
            按阶段归位（技术评审直接挂阶段）；默认只看当前阶段及之后 2 段（其余折叠）。点行进详情页该阶段的「阶段门」面板
          </span>
        </div>
        <table v-if="visibleReviews.length" class="bm-reviews">
          <thead>
            <tr>
              <th style="width: 96px">阶段</th>
              <th style="width: 150px">门</th>
              <th>评审</th>
              <th style="width: 96px">类型</th>
              <th style="width: 104px">状态</th>
              <th style="width: 88px">结论</th>
              <th style="width: 150px">当前办理</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in visibleReviews"
              :key="r.oid"
              class="bm-review-row"
              @click="openStage(r.stageCode, 'gate')"
            >
              <td class="bm-muted">{{ r.stageName }}</td>
              <td>{{ r.gateName }}</td>
              <td>{{ r.name }}</td>
              <td class="bm-muted">{{ r.type || '—' }}</td>
              <td>
                <a-tag :color="reviewState(r).color" size="small">{{ reviewState(r).text }}</a-tag>
              </td>
              <td>{{ r.verdict || '—' }}</td>
              <td class="bm-muted">{{ r.currentAssignees || (r.processStatus ? '—' : '未发起') }}</td>
            </tr>
          </tbody>
        </table>
        <a-empty v-else-if="!loading" description="暂无评审" />
        <div v-if="foldedCount > 0" class="bm-more">
          <a @click="reviewsExpanded = true">还有 {{ foldedCount }} 项评审</a>
        </div>
      </section>

      <!-- 该视角还声明了别的码（风险清单 / 行动入口…）：如实列出来，别让人以为漏了 -->
      <div v-if="otherWidgets.length" class="bm-extras">
        <span class="bm-muted">本视角还声明了：</span>
        <a-tag v-for="w in otherWidgets" :key="w" size="small">{{ widgetLabel(w) }}</a-tag>
        <span class="bm-muted">—— 暂无渲染实现，数据源待接</span>
      </div>

      <a-empty
        v-if="!loading && !hasWidget('STAGE_READINESS') && !hasWidget('DELIVERABLE_MATRIX') && !hasWidget('REVIEW_STATUS')"
        description="该视角的布局里没有可渲染的分组件（阶段就绪度 / 交付件矩阵 / 评审状态）"
      />
    </a-spin>
  </div>
</template>

<script setup>
/**
 * 「型号作战地图」主视角的三块分组件渲染（2026-10-26）。
 *
 * <p>为什么抽成独立组件：驾驶舱实例（ck_dashboard）只快照了布局 —— {@code widgets} 是分组件码数组，
 * 后端不存"渲染结果"。整机视图页拿到的是码，需要有人把它变成可看的视图。本组件只做这三块
 * （{@code STAGE_READINESS} / {@code DELIVERABLE_MATRIX} / {@code REVIEW_STATUS}），因为它们的
 * 数据在 {@code GET /api/instances} 一次返回里已经齐了（deliverables / gates / reviews），零新增接口。
 *
 * <h3>顺序即问题链</h3>
 * 我在哪（阶段就绪度）→ 差什么（交付件矩阵）→ 下一步开什么会（评审状态）。三块共用"当前阶段"：
 * 默认选中第一个未完成的阶段，点①的某段联动②③。
 *
 * <h3>两条不可让的口径</h3>
 * <ul>
 *   <li>完成率分母与后端 {@code IssueServiceImpl#countedDeliverable} 逐字一致：
 *       未裁剪（{@code disabled}）+ 非 NA；只有 {@code DONE} 算完成。前端另立算法 = 同一页两个数字打架。</li>
 *   <li>没有要交的东西显示「—」而不是 100%。后端"无应交付项按 100% 处理"是判门逻辑，
 *       不能被界面借去当结论（与指标区"未评估就写暂未评估"同一条原则）。</li>
 * </ul>
 */
import { ref, computed, watch } from 'vue'
import { BATTLE_MAP_WIDGETS, widgetLabel } from '@/utils/dashboardWidgets'
import { isDecisionGate } from '@/utils/gateDefs'

const props = defineProps({
  /** 本容器的研发阶段（ck_stage，按 sortOrder 排；含不在仪表盘页签显示的阶段——整机视角要看全） */
  stages: { type: Array, default: () => [] },
  /** ck_deliverable 实例（status 已由 DeliverableStatusEvaluator 重算） */
  deliverables: { type: Array, default: () => [] },
  /** ck_gate 实例 */
  gates: { type: Array, default: () => [] },
  /** ck_review 实例（含流程进展：processStatus / currentAssignees…） */
  reviews: { type: Array, default: () => [] },
  /** 该视角声明的分组件码；空数组 = 老数据没配，按三件套全渲染 */
  widgets: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['open-stage'])

const DELIVERABLE_DONE = 'DONE'

/** 专业：顺序即矩阵行序（与 ck_deliverable.discipline 的取值一致） */
const DISCIPLINE_ORDER = ['PRODUCT', 'SYSTEM', 'HARDWARE', 'SOFTWARE', 'STRUCTURE', 'TEST', 'SUPPLY']
const DISCIPLINE_LABEL = {
  PRODUCT: '产品', SYSTEM: '系统', HARDWARE: '硬件', SOFTWARE: '软件',
  STRUCTURE: '结构', TEST: '测试', SUPPLY: '供应链'
}

const GATE_STATUS_LABEL = { PENDING: '待开始', READY: '可评审', PASSED: '已通过', REJECTED: '已驳回' }

const currentCode = ref(null)
const reviewsExpanded = ref(false)

/** 某视角是否声明了该分组件（没配过 widgets 的老数据：三块都渲染） */
function hasWidget(code) {
  if (!props.widgets.length) return true
  return props.widgets.includes(code)
}

/** 本视角声明了、但不归作战地图渲染的码（风险清单 / 行动入口）—— 底部如实列出 */
const otherWidgets = computed(() => props.widgets.filter((w) => !BATTLE_MAP_WIDGETS.includes(w)))

/* ==================== ① 阶段就绪度 ==================== */

/** 分母口径：未裁剪、且不是 NA —— 与后端 DELIVERABLE_RATE 完全一致 */
function counted(d) {
  return !d.disabled && d.status !== 'NA'
}

const stageStats = computed(() => {
  const list = [...(props.stages || [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  return list.map((st) => {
    const own = (props.deliverables || []).filter((d) => d.stageKey === st.code && counted(d))
    const done = own.filter((d) => d.status === DELIVERABLE_DONE).length
    return {
      oid: st.oid,
      code: st.code,
      name: st.name || st.code,
      sortOrder: st.sortOrder ?? 0,
      done,
      total: own.length,
      percent: own.length === 0 ? null : Math.round((done * 100) / own.length),
      // 只算决策门：老库残留的技术门行没有决策，留着只会让每个阶段的圆点永远是"未通过"
      gates: (props.gates || []).filter((g) => g.stageKey === st.code && isDecisionGate(g) && !g.disabled)
    }
  })
})

/** 默认选中"第一个未完成的阶段"；全部完成则选第一个有交付件的阶段 */
watch(
  stageStats,
  (list) => {
    if (!list.length) return
    if (currentCode.value && list.some((s) => s.code === currentCode.value)) return
    const firstOpen = list.find((s) => s.total > 0 && s.done < s.total)
    currentCode.value = (firstOpen || list.find((s) => s.total > 0) || list[0]).code
  },
  { immediate: true }
)

/** 门状态圆点：全通过绿 / 有驳回红 / 有可评审蓝 / 其余灰（不显示"未通过"字样的红——免得和驳回混） */
function gateDotColor(s) {
  const gs = s.gates
  if (!gs.length) return '#d9d9d9'
  if (gs.some((g) => g.status === 'REJECTED')) return '#ff4d4f'
  if (gs.every((g) => g.status === 'PASSED')) return '#52c41a'
  if (gs.some((g) => g.status === 'READY')) return '#1677ff'
  return '#d9d9d9'
}

function progressColor(percent) {
  if (percent == null) return '#d9d9d9'
  if (percent >= 100) return '#52c41a'
  if (percent === 0) return '#d9d9d9'
  return '#fa8c16'
}

/* ==================== ② 交付件矩阵 ==================== */

/** 格子索引：`专业|阶段` → { done, total, names } */
const cellMap = computed(() => {
  const map = {}
  for (const d of props.deliverables || []) {
    if (!counted(d)) continue
    const key = `${d.discipline || 'OTHER'}|${d.stageKey}`
    if (!map[key]) map[key] = { done: 0, total: 0, names: [] }
    map[key].total++
    if (d.status === DELIVERABLE_DONE) map[key].done++
    map[key].names.push(d.name || d.code)
  }
  return map
})

function cellOf(disc, stageCode) {
  return cellMap.value[`${disc}|${stageCode}`] || { done: 0, total: 0, names: [] }
}

/** 行：有交付件的专业，按固定顺序；未在字典里的专业排到最后（不吞数据） */
const matrixRows = computed(() => {
  const byDisc = new Map()
  for (const d of props.deliverables || []) {
    if (!counted(d)) continue
    const code = d.discipline || 'OTHER'
    if (!byDisc.has(code)) byDisc.set(code, { code, done: 0, total: 0 })
    const row = byDisc.get(code)
    row.total++
    if (d.status === DELIVERABLE_DONE) row.done++
  }
  const ordered = DISCIPLINE_ORDER.filter((c) => byDisc.has(c))
  const extra = [...byDisc.keys()].filter((c) => !DISCIPLINE_ORDER.includes(c))
  return [...ordered, ...extra].map((c) => ({ ...byDisc.get(c), label: DISCIPLINE_LABEL[c] || c }))
})

function cellClass(disc, stageCode) {
  const cell = cellOf(disc, stageCode)
  if (!cell.total) return 'bm-cell--empty'
  if (cell.done >= cell.total) return 'bm-cell--done'
  if (cell.done === 0) return 'bm-cell--zero'
  return 'bm-cell--partial'
}

function cellTitle(disc, stageCode) {
  const cell = cellOf(disc, stageCode)
  if (!cell.total) return '该专业在此阶段无交付件'
  return `${cell.done}/${cell.total}：${cell.names.join('、')}`
}

/* ==================== ③ 评审状态 ==================== */

const gateByCode = computed(() => {
  const map = {}
  for (const g of props.gates || []) map[g.code] = g
  return map
})

const stageIndexOf = computed(() => {
  const map = {}
  stageStats.value.forEach((s, i) => { map[s.code] = i })
  return map
})

/**
 * 评审按阶段归位后排序。
 *
 * <p>阶段来源两级：① 评审自己的 {@code stageKey}（2026-10-29 起技术评审直接挂阶段）；
 * ② 老数据回落到「门 → 阶段」。两级都没有（模板换过）时排到最后，不丢。
 * 「门」这一列对内嵌评审已无意义，统一显示「技术评审」（不再有技术门那层壳）。
 */
const reviewRows = computed(() => {
  return (props.reviews || [])
    .filter((r) => !r.disabled)
    .map((r) => {
      const gate = r.gate ? gateByCode.value[r.gate] : null
      const stageCode = r.stageKey || gate?.stageKey || null
      const idx = stageCode != null ? stageIndexOf.value[stageCode] : undefined
      return {
        ...r,
        stageCode,
        stageName: idx != null ? stageStats.value[idx].name : '未关联阶段',
        gateName: gate ? `${gate.code} ${gate.name}` : '技术评审',
        order: idx != null ? idx * 1000 + (r.sortOrder ?? 0) : 900000 + (r.sortOrder ?? 0)
      }
    })
    .sort((a, b) => a.order - b.order)
})

/** 默认只露「当前阶段及之后 2 段」的评审 —— 10 道门全列出来会把下面指标区挤到屏幕外 */
const visibleReviews = computed(() => {
  if (reviewsExpanded.value) return reviewRows.value
  const idx = stageIndexOf.value[currentCode.value]
  if (idx == null) return reviewRows.value
  return reviewRows.value.filter((r) => {
    const i = r.stageCode != null ? stageIndexOf.value[r.stageCode] : undefined
    return i == null || i <= idx + 2
  })
})

const foldedCount = computed(() => reviewRows.value.length - visibleReviews.value.length)

/** 状态口径：DONE 以库为准；未完成时看流程（running / suspended 都算没结束，与后端闸门一致） */
function reviewState(r) {
  if (r.status === 'DONE') return { text: '已完成', color: 'green' }
  if (r.processStatus === 'running') return { text: '评审中', color: 'blue' }
  if (r.processStatus === 'suspended') return { text: '流程挂起', color: 'orange' }
  return { text: '未开始', color: 'default' }
}

/* ==================== 下钻 ==================== */

/** 下钻到详情页对应阶段 + 页签（交付件 / 评审）；无阶段归属的行不响应 */
function openStage(stageCode, tab) {
  if (!stageCode) return
  emit('open-stage', { code: stageCode, tab })
}
</script>

<style scoped>
.bm { display: flex; flex-direction: column; gap: 14px; }
.bm-block { border: 1px solid #f0f0f0; border-radius: 6px; padding: 10px 12px 12px; background: #fff; }
.bm-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.bm-title { margin: 0; font-size: 13px; font-weight: 600; color: #262626; }
.bm-hint { color: #8c8c8c; font-size: 11px; line-height: 1.5; }
.bm-muted { color: #8c8c8c; }

/* ① 阶段条 */
.bm-track { display: flex; gap: 8px; align-items: stretch; }
.bm-step {
  flex: 1 1 0; min-width: 108px; padding: 6px 8px 4px; border: 1px solid #f0f0f0; border-radius: 4px;
  cursor: pointer; transition: border-color 0.15s, background 0.15s; background: #fafafa;
}
.bm-step:hover { border-color: #91caff; }
.bm-step--active { border-color: #1677ff; background: #f0f7ff; }
.bm-step-head { display: flex; align-items: center; gap: 5px; margin-bottom: 2px; }
.bm-gate-dot { width: 7px; height: 7px; border-radius: 50%; flex: none; }
.bm-step-name { font-size: 12px; color: #262626; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bm-step-foot { font-size: 11px; color: #595959; text-align: right; }

/* ② 矩阵 */
.bm-matrix-wrap { overflow-x: auto; }
.bm-matrix { border-collapse: separate; border-spacing: 2px; width: 100%; font-size: 12px; }
.bm-matrix th { font-weight: 500; color: #595959; padding: 4px 6px; white-space: nowrap; text-align: center; }
.bm-corner, .bm-rowhead { text-align: left; background: #fafafa; }
.bm-rowhead { color: #262626; }
.bm-th-active { color: #1677ff; }
.bm-th-total { border-left: 1px solid #f0f0f0; }
.bm-cell {
  text-align: center; padding: 5px 6px; border-radius: 3px; cursor: pointer;
  font-variant-numeric: tabular-nums; min-width: 62px;
}
.bm-cell--empty { background: transparent; cursor: default; }
.bm-cell--zero { background: #fafafa; color: #bfbfbf; }
.bm-cell--partial { background: #fff7e6; color: #d46b08; }
.bm-cell--done { background: #f6ffed; color: #389e0d; }
.bm-cell--total { border-left: 1px solid #f0f0f0; background: #fafafa; color: #595959; cursor: default; }
.bm-cell:hover { outline: 1px solid #91caff; }

/* ③ 评审 */
.bm-reviews { width: 100%; border-collapse: collapse; font-size: 12px; }
.bm-reviews th {
  text-align: left; font-weight: 500; color: #8c8c8c; padding: 4px 6px;
  border-bottom: 1px solid #f0f0f0; white-space: nowrap;
}
.bm-reviews td { padding: 5px 6px; border-bottom: 1px solid #fafafa; color: #262626; }
.bm-review-row { cursor: pointer; }
.bm-review-row:hover td { background: #f5faff; }
.bm-more { margin-top: 6px; font-size: 12px; }

/* 未实现分组件的小结：一行小字，不抢三块的视觉重心 */
.bm-extras { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 12px; }
</style>
