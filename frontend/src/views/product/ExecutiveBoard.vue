<template>
  <div class="eb">
    <!-- ① 健康度 -->
    <section v-if="hasWidget('HEALTH_SCORE_TREND')" class="eb-block">
      <div class="eb-head">
        <h4 class="eb-title">健康度</h4>
        <span class="eb-hint">
          四项等权平均（交付件完成率 / 阶段门通过率 / 评审完成率 / 未闭环阻塞问题）；
          只给<b>当期值</b> —— 趋势线需要按日快照积累，平台目前没有历史快照，不画假曲线
        </span>
      </div>
      <div class="eb-health">
        <div class="eb-score">
          <span class="eb-score-num" :style="{ color: scoreColor }">{{ health.score == null ? '—' : health.score }}</span>
          <span class="eb-score-unit">分</span>
          <span class="eb-score-cap">当期</span>
        </div>
        <div class="eb-dims">
          <div v-for="d in health.dims" :key="d.key" class="eb-dim">
            <span class="eb-dim-label">{{ d.label }}</span>
            <a-progress
              :percent="d.value == null ? 0 : d.value"
              size="small"
              :show-info="false"
              :stroke-color="dimColor(d.value)"
            />
            <span class="eb-dim-value">{{ d.value == null ? '—' : d.value + '%' }}</span>
            <span class="eb-dim-note">{{ d.note }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ② 成本达成 -->
    <section v-if="hasWidget('COST_ACHIEVE')" class="eb-block">
      <div class="eb-head">
        <h4 class="eb-title">成本达成</h4>
        <span class="eb-hint">
          口径 目标 / 实际（≥100% 为达成）：目标价来自系列 / 型号上的<b>「目标成本(元)」</b>属性（就在本卡片里设置），
          实际价来自 BOM 卷积；系列的实际成本含旗下型号
        </span>
      </div>
      <div class="eb-cost">
        <div class="eb-cost-value">
          <span class="eb-cost-num">{{ costAchieve == null ? '暂未评估' : costAchieve + '%' }}</span>
          <span class="eb-cost-cap">成本达成率</span>
        </div>
        <div class="eb-cost-meta">
          <p>
            <span class="eb-meta-key">目标成本</span>
            <!-- 就地编辑：GM 看到目标不对应当场能改，而不是让人去找「编辑系列」 -->
            <template v-if="targetEditing">
              <a-input-number
                v-model:value="targetDraft"
                class="eb-target-input"
                size="small"
                :min="0"
                :precision="2"
                :step="10000"
                placeholder="人民币元；留空保存 = 清除"
                @press-enter="saveTarget"
              />
              <span class="eb-target-unit">元</span>
              <a-button size="small" type="primary" :loading="targetSaving" @click="saveTarget">保存</a-button>
              <a-button size="small" :disabled="targetSaving" @click="cancelTargetEdit">取消</a-button>
            </template>
            <template v-else>
              <span class="eb-meta-val" :class="{ 'eb-target-empty': targetValue == null }">{{ targetText }}</span>
              <a v-if="canManageTarget" class="eb-target-set" @click="startTargetEdit">
                {{ targetValue == null ? '设置' : '修改' }}
              </a>
              <span v-else class="eb-target-readonly">（仅产品经理 / 管理员可设置）</span>
            </template>
          </p>
          <p>
            <span class="eb-meta-key">取数依据</span>
            <span class="eb-meta-val">{{ costMetric?.source || 'BOM 卷积 / TARGET_COST' }}</span>
            <a-tag v-if="costMetric?.level" size="small">就绪度 {{ costMetric.level }}</a-tag>
          </p>
          <p class="eb-meta-tip">
            要让这个数出得来：① 在这里登记目标成本；② BOM 各层填全单位成本。
            两者缺一，这里就显示「暂未评估」—— 不估算、不填默认值。
            系列与型号各算各的，型号没填不用系列的值顶替。
          </p>
        </div>
      </div>
    </section>

    <!-- ③ 上市时间（2026-10-30 与目标成本同一做法：目标日期就地设置，实际日期取 LDCP 通过日） -->
    <section v-if="hasWidget('TIME_TO_MARKET_DEV')" class="eb-block">
      <div class="eb-head">
        <h4 class="eb-title">上市时间</h4>
        <span class="eb-hint">
          偏差 = 上市决策评审（LDCP）通过日 − 系列 / 型号上的<b>「目标上市日期」</b>，正数为延期（天）
        </span>
      </div>
      <div class="eb-cost">
        <div class="eb-cost-value">
          <span class="eb-cost-num">{{ launchDevText }}</span>
          <span class="eb-cost-cap">上市时间偏差</span>
        </div>
        <div class="eb-cost-meta">
          <p>
            <span class="eb-meta-key">目标上市日期</span>
            <template v-if="launchEditing">
              <a-date-picker
                v-model:value="launchDraft"
                class="eb-target-input"
                size="small"
                value-format="YYYY-MM-DD"
                placeholder="选择日期；留空保存 = 清除"
              />
              <a-button size="small" type="primary" :loading="launchSaving" @click="saveLaunchDate">保存</a-button>
              <a-button size="small" :disabled="launchSaving" @click="cancelLaunchEdit">取消</a-button>
            </template>
            <template v-else>
              <span class="eb-meta-val" :class="{ 'eb-target-empty': !launchDate }">{{ launchDateText }}</span>
              <a v-if="canManageTarget" class="eb-target-set" @click="startLaunchEdit">
                {{ launchDate ? '修改' : '设置' }}
              </a>
              <span v-else class="eb-target-readonly">（仅产品经理 / 管理员可设置）</span>
            </template>
          </p>
          <p>
            <span class="eb-meta-key">取数依据</span>
            <span class="eb-meta-val">{{ launchMetric?.source || 'TARGET_LAUNCH_DATE / LDCP' }}</span>
            <a-tag v-if="launchMetric?.level" size="small">就绪度 {{ launchMetric.level }}</a-tag>
          </p>
          <p class="eb-meta-tip">{{ launchTip }}</p>
        </div>
      </div>
    </section>

    <!-- ④ 风险 Top5 -->
    <section v-if="hasWidget('RISK_TOP5')" class="eb-block">
      <div class="eb-head">
        <h4 class="eb-title">风险 Top5</h4>
        <span class="eb-hint">未闭环问题（OPEN / IN_PROGRESS / VERIFYING），阻断在前、逾期在前；数据源与阶段门的 ISSUE_OPEN 检查同一份</span>
      </div>
      <table v-if="topRisks.length" class="eb-table">
        <thead>
          <tr>
            <th style="width: 70px">严重度</th>
            <th style="width: 96px">编号</th>
            <th>标题</th>
            <th style="width: 96px">状态</th>
            <th style="width: 104px">计划完成</th>
            <th style="width: 100px">负责人</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in topRisks" :key="r.oid">
            <td><a-tag :color="r.severity === 'BLOCK' ? 'red' : 'orange'" size="small">{{ r.severity === 'BLOCK' ? '阻断' : '警告' }}</a-tag></td>
            <td class="eb-mono">{{ r.code }}</td>
            <td>{{ r.name }}</td>
            <td class="eb-muted">{{ ISSUE_STATUS_LABEL[r.status] || r.status }}</td>
            <td :class="{ 'eb-overdue': isOverdue(r) }">{{ r.dueDate || '—' }}</td>
            <td class="eb-muted">{{ r.owner || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <a-empty v-else-if="!loading" description="无未闭环问题" />
      <p v-if="risks.length > topRisks.length" class="eb-more">共 {{ risks.length }} 条未闭环，这里只列前 5</p>
    </section>

    <!-- ⑤ 型号排名 -->
    <section v-if="hasWidget('MODEL_RANKING')" class="eb-block">
      <div class="eb-head">
        <h4 class="eb-title">型号排名</h4>
        <span class="eb-hint">
          本系列下各型号按<b>交付件完成率</b>排序（分母排除「本项目不适用」与 NA，只有 DONE 算完成）——
          与作战地图同一口径
        </span>
      </div>
      <table v-if="rankRows.length" class="eb-table">
        <thead>
          <tr>
            <th style="width: 56px">名次</th>
            <th style="width: 120px">型号编码</th>
            <th>名称</th>
            <th style="width: 130px">交付件</th>
            <th style="width: 120px">阶段门</th>
            <th style="width: 90px">完成率</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, i) in rankRows" :key="m.oid">
            <td class="eb-mono">{{ i + 1 }}</td>
            <td class="eb-mono">{{ m.code }}</td>
            <td>{{ m.name }}</td>
            <td>{{ m.done }}/{{ m.total }}</td>
            <td>{{ m.gatePassed }}/{{ m.gateTotal }}</td>
            <td :style="{ color: dimColor(m.percent) }">{{ m.percent == null ? '—' : m.percent + '%' }}</td>
          </tr>
        </tbody>
      </table>
      <a-empty v-else-if="!loading" description="本系列下暂无型号" />
      <p v-if="rankRows.length === 1" class="eb-more">本系列当前只有 1 个型号 —— 排名要等有第二个型号才有比较意义</p>
    </section>
  </div>
</template>

<script setup>
/**
 * 「经营战报」视角的四块分组件（2026-10-26）：健康度 / 成本达成 / 风险 Top5 / 型号排名。
 *
 * <p>受众是 GM，所以四块都回答"整体怎么样"，不按阶段切。数据来源全部是现成接口：
 * <ul>
 *   <li>健康度的三个分母（交付件 / 门 / 评审）来自 {@code GET /api/instances}（父页面已取，直接传入）；</li>
 *   <li>风险来自 {@code GET /api/issues/gate-check?ownerOid=}（与阶段门 ISSUE_OPEN 检查同一份数据）；</li>
 *   <li>型号清单来自 {@code GET /api/product-models?productLineOid=}，逐个型号再取一次六类实例算完成率；</li>
 *   <li>成本来自指标实例 {@code COST_ACHIEVE} 本身（本组件不算成本，只如实呈现有没有数）。</li>
 * </ul>
 *
 * <h3>不编造的三条纪律</h3>
 * <ol>
 *   <li><b>健康度不画趋势线</b>：平台没有健康度快照表，一个点连不成线。这里只给当期分数，
 *       并在说明里写清构成与"无历史"。</li>
 *   <li><b>成本达成率不算 0%、不估算</b>：缺目标价就显示「暂未评估」。目标价是业务输入，
 *       不是能猜的东西。</li>
 *   <li><b>排名分母口径与作战地图逐字一致</b>：{@code !disabled && status !== 'NA'}，
 *       只有 DONE 算完成 —— 同一页出现两个完成率而数字打架，比没有数字更糟。</li>
 * </ol>
 */
import { ref, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getIssueGateCheck, getProductModels, listMethodologyInstances,
  getTargetCost, setTargetCost, getTargetLaunchDate, setTargetLaunchDate, getMyManagePermissions
} from '@/api'
import { isDecisionGate } from '@/utils/gateDefs'
import { formatMoney } from '@/utils/cost'

const props = defineProps({
  /** 当前容器 oid（系列或型号）—— 风险按它统计 */
  ownerOid: { type: String, default: '' },
  /** 取型号清单用的系列 oid（当前是型号时传它的父系列） */
  seriesOid: { type: String, default: '' },
  /** 该视角声明的分组件码 */
  widgets: { type: Array, default: () => [] },
  /** 父页面已取回的六类实例 */
  deliverables: { type: Array, default: () => [] },
  gates: { type: Array, default: () => [] },
  reviews: { type: Array, default: () => [] },
  metrics: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const ISSUE_STATUS_LABEL = {
  OPEN: '待处理', IN_PROGRESS: '处理中', VERIFYING: '验证中', CLOSED: '已关闭', CANCELLED: '已取消'
}

/** 某视角是否声明了该分组件（没配过 widgets 的老数据：四块都渲染） */
function hasWidget(code) {
  if (!props.widgets.length) return true
  return props.widgets.includes(code)
}

/* ==================== 风险（未闭环问题） ==================== */

const risks = ref([])

async function loadRisks() {
  if (!props.ownerOid) return
  try {
    const res = await getIssueGateCheck(props.ownerOid)
    risks.value = res?.code === 200 ? (res.data?.items || []) : []
  } catch {
    risks.value = []
  }
}

function isOverdue(row) {
  if (!row?.dueDate) return false
  const today = new Date()
  const d = new Date(`${row.dueDate}T23:59:59`)
  return d.getTime() < today.getTime()
}

/** Top5：阻断在前 → 逾期在前 → 创建时间倒序（接口已按前两级大致排过，这里补逾期与截断） */
const topRisks = computed(() => {
  const list = [...risks.value]
  list.sort((a, b) => {
    const sa = a.severity === 'BLOCK' ? 0 : 1
    const sb = b.severity === 'BLOCK' ? 0 : 1
    if (sa !== sb) return sa - sb
    const oa = isOverdue(a) ? 0 : 1
    const ob = isOverdue(b) ? 0 : 1
    if (oa !== ob) return oa - ob
    return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
  })
  return list.slice(0, 5)
})

/* ==================== 健康度（当期） ==================== */

/** 分母口径：未裁剪、且不是 NA —— 与阶段门的 DELIVERABLE_RATE 一致 */
function counted(d) {
  return !d.disabled && d.status !== 'NA'
}

const health = computed(() => {
  const ds = (props.deliverables || []).filter(counted)
  const dv = ds.length ? Math.round((ds.filter((d) => d.status === 'DONE').length * 100) / ds.length) : null

  // 阶段门通过率只算决策门（老库残留的技术门行没有决策、永远 0 通过，会把这一维拖死）
  const gs = (props.gates || []).filter((g) => isDecisionGate(g) && !g.disabled)
  const gv = gs.length ? Math.round((gs.filter((g) => g.status === 'PASSED').length * 100) / gs.length) : null

  const rs = (props.reviews || []).filter((r) => !r.disabled)
  const rv = rs.length ? Math.round((rs.filter((r) => r.status === 'DONE').length * 100) / rs.length) : null

  // 阻塞问题：一条扣 20 分。扣分规则写在这里，就是为了让人能反驳它
  const blockOpen = risks.value.filter((r) => r.severity === 'BLOCK').length
  const kv = Math.max(0, 100 - blockOpen * 20)

  const dims = [
    { key: 'deliverable', label: '交付件', value: dv, note: `${ds.filter((d) => d.status === 'DONE').length}/${ds.length}` },
    { key: 'gate', label: '阶段门', value: gv, note: `${gs.filter((g) => g.status === 'PASSED').length}/${gs.length}` },
    { key: 'review', label: '评审', value: rv, note: `${rs.filter((r) => r.status === 'DONE').length}/${rs.length}` },
    { key: 'risk', label: '阻塞问题', value: kv, note: `${blockOpen} 条未闭环` }
  ]
  const valid = dims.map((d) => d.value).filter((v) => v != null)
  return {
    dims,
    score: valid.length ? Math.round(valid.reduce((a, b) => a + b, 0) / valid.length) : null
  }
})

const scoreColor = computed(() => {
  const s = health.value.score
  if (s == null) return '#bfbfbf'
  if (s >= 80) return '#52c41a'
  if (s >= 50) return '#fa8c16'
  return '#ff4d4f'
})

function dimColor(v) {
  if (v == null) return '#d9d9d9'
  if (v >= 80) return '#52c41a'
  if (v >= 50) return '#fa8c16'
  return '#ff4d4f'
}

/* ==================== 成本达成 ==================== */

const costMetric = computed(() => (props.metrics || []).find((m) => m.code === 'COST_ACHIEVE') || null)
const costAchieve = computed(() => {
  const v = costMetric.value?.value
  return v == null ? null : v
})

/* ---- 目标成本（IBA TARGET_COST）：GM 就地设置，不走「编辑系列 / 型号」整表单 ---- */

const targetValue = ref(null)
const targetLoading = ref(false)
const targetEditing = ref(false)
const targetDraft = ref(null)
const targetSaving = ref(false)
/** 我能不能改这条系列 / 型号 —— 与「编辑系列」同一口径（产品经理 / 管理员） */
const canManageTarget = ref(false)

const targetText = computed(() => {
  if (targetLoading.value) return '读取中…'
  return targetValue.value == null ? '未设定' : `¥ ${formatMoney(targetValue.value)}`
})

async function loadTarget() {
  if (!props.ownerOid) return
  targetLoading.value = true
  try {
    const res = await getTargetCost(props.ownerOid)
    const raw = res?.data?.targetCost
    targetValue.value = raw == null || raw === '' ? null : Number(raw)
  } catch {
    // 读不到就按"未设定"显示；不拿 0 顶替（0 是个合法的目标，缺数不是）
    targetValue.value = null
  } finally {
    targetLoading.value = false
  }
}

async function loadTargetPermission() {
  if (!props.ownerOid) return
  try {
    const res = await getMyManagePermissions([props.ownerOid])
    canManageTarget.value = !!res?.data?.[props.ownerOid]
  } catch {
    canManageTarget.value = false
  }
}

function startTargetEdit() {
  targetDraft.value = targetValue.value
  targetEditing.value = true
}

function cancelTargetEdit() {
  targetEditing.value = false
  targetDraft.value = null
}

/**
 * 保存目标成本。
 *
 * <p>空值 = 清除（后端把该条 IBA 删掉，不写一个 "null" 进去）；负数在输入框已被 min 拦住，
 * 这里再判一次是因为手输可以绕过步进器。保存后用接口回显的值，不拿草稿值冒充已落库。
 */
async function saveTarget() {
  const draft = targetDraft.value
  if (draft != null && (!Number.isFinite(Number(draft)) || Number(draft) < 0)) {
    message.warning('目标成本必须是不小于 0 的数字')
    return
  }
  const payload = draft == null ? null : Number(draft)
  targetSaving.value = true
  try {
    const res = await setTargetCost(props.ownerOid, payload)
    if (res?.code !== 200) {
      message.error(res?.message || '保存目标成本失败')
      return
    }
    const raw = res?.data?.targetCost
    targetValue.value = raw == null || raw === '' ? null : Number(raw)
    targetEditing.value = false
    targetDraft.value = null
    message.success(payload == null ? '已清除目标成本' : '目标成本已保存')
  } catch (e) {
    message.error(e?.response?.data?.message || '保存目标成本失败')
  } finally {
    targetSaving.value = false
  }
}

/* ---- 目标上市日期（IBA TARGET_LAUNCH_DATE）：与目标成本同一套就地编辑 ---- */

const launchMetric = computed(() => (props.metrics || []).find((m) => m.code === 'TIME_TO_MARKET_DEV') || null)

/** 偏差天数：正数标 "+"（延期），负数本身就是提前，直接显示 -3 天 */
const launchDevText = computed(() => {
  const v = launchMetric.value?.value
  if (v == null) return '暂未评估'
  const days = Number(v)
  if (!Number.isFinite(days)) return '暂未评估'
  return days > 0 ? `+${days} 天` : `${days} 天`
})

/** 有指标文案就用它（含"计划 / 实际 / 延期 N 天"），没有就说明缺哪两样 */
const launchTip = computed(() => launchMetric.value?.valueText
  || '要让这个数出得来：① 在这里登记目标上市日期；② 走到「上市决策评审（LDCP）」并通过。两者缺一显示「暂未评估」。')

const launchDate = ref(null)
const launchLoading = ref(false)
const launchEditing = ref(false)
const launchDraft = ref(null)
const launchSaving = ref(false)

const launchDateText = computed(() => {
  if (launchLoading.value) return '读取中…'
  return launchDate.value || '未设定'
})

async function loadLaunchDate() {
  if (!props.ownerOid) return
  launchLoading.value = true
  try {
    const res = await getTargetLaunchDate(props.ownerOid)
    const raw = res?.data?.targetLaunchDate
    launchDate.value = raw == null || raw === '' ? null : String(raw)
  } catch {
    launchDate.value = null
  } finally {
    launchLoading.value = false
  }
}

function startLaunchEdit() {
  launchDraft.value = launchDate.value
  launchEditing.value = true
}

function cancelLaunchEdit() {
  launchEditing.value = false
  launchDraft.value = null
}

/** 空值 = 清除（后端把该条 IBA 删掉）；保存后回显接口返回值，不拿草稿值冒充已落库 */
async function saveLaunchDate() {
  const payload = launchDraft.value ? String(launchDraft.value) : null
  launchSaving.value = true
  try {
    const res = await setTargetLaunchDate(props.ownerOid, payload)
    if (res?.code !== 200) {
      message.error(res?.message || '保存目标上市日期失败')
      return
    }
    const raw = res?.data?.targetLaunchDate
    launchDate.value = raw == null || raw === '' ? null : String(raw)
    launchEditing.value = false
    launchDraft.value = null
    message.success(payload ? '目标上市日期已保存' : '已清除目标上市日期')
  } catch (e) {
    message.error(e?.response?.data?.message || '保存目标上市日期失败')
  } finally {
    launchSaving.value = false
  }
}

/* ==================== 型号排名 ==================== */

const rankRows = ref([])
const rankingLoading = ref(false)

async function loadRanking() {
  const seriesOid = props.seriesOid || props.ownerOid
  if (!seriesOid) return
  rankingLoading.value = true
  try {
    const res = await getProductModels({ productLineOid: seriesOid })
    const models = res?.code === 200 ? (res.data || []) : []
    // 型号数量是个位数，逐个取实例比新造一个聚合接口更省事；失败的行按"无数据"排最后
    const rows = await Promise.all(models.map(async (m) => {
      try {
        const r = await listMethodologyInstances(m.oid)
        const ds = (r?.data?.deliverables || []).filter(counted)
        const gs = (r?.data?.gates || []).filter((g) => isDecisionGate(g) && !g.disabled)
        const done = ds.filter((d) => d.status === 'DONE').length
        return {
          oid: m.oid,
          code: m.code,
          name: m.name,
          done,
          total: ds.length,
          gatePassed: gs.filter((g) => g.status === 'PASSED').length,
          gateTotal: gs.length,
          percent: ds.length ? Math.round((done * 100) / ds.length) : null
        }
      } catch {
        return { oid: m.oid, code: m.code, name: m.name, done: 0, total: 0, gatePassed: 0, gateTotal: 0, percent: null }
      }
    }))
    rankRows.value = rows.sort((a, b) => (b.percent ?? -1) - (a.percent ?? -1))
  } catch {
    rankRows.value = []
  } finally {
    rankingLoading.value = false
  }
}

/* ==================== 加载 ==================== */

watch(
  () => [props.ownerOid, props.seriesOid],
  () => { loadRisks(); loadRanking(); loadTarget(); loadLaunchDate(); loadTargetPermission() }
)
onMounted(() => { loadRisks(); loadRanking(); loadTarget(); loadLaunchDate(); loadTargetPermission() })
</script>

<style scoped>
.eb { display: flex; flex-direction: column; gap: 14px; }
.eb-block { border: 1px solid #f0f0f0; border-radius: 6px; padding: 10px 12px 12px; background: #fff; }
.eb-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.eb-title { margin: 0; font-size: 13px; font-weight: 600; color: #262626; }
.eb-hint { color: #8c8c8c; font-size: 11px; line-height: 1.5; }
.eb-muted { color: #8c8c8c; }
.eb-mono { font-family: monospace; font-size: 12px; }
.eb-overdue { color: #ff4d4f; }
.eb-more { margin: 6px 0 0; font-size: 12px; color: #8c8c8c; }

/* 健康度 */
.eb-health { display: flex; align-items: center; gap: 20px; }
.eb-score { display: flex; flex-direction: column; align-items: center; min-width: 84px; }
.eb-score-num { font-size: 30px; font-weight: 700; line-height: 1.1; }
.eb-score-unit { font-size: 12px; color: #8c8c8c; }
.eb-score-cap { margin-top: 2px; font-size: 11px; color: #bfbfbf; }
.eb-dims { flex: 1; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 18px; }
.eb-dim { display: grid; grid-template-columns: 62px 1fr 44px; align-items: center; gap: 8px; }
.eb-dim-label { font-size: 12px; color: #595959; }
.eb-dim-value { font-size: 12px; color: #262626; text-align: right; font-variant-numeric: tabular-nums; }
.eb-dim-note { grid-column: 2 / span 2; font-size: 11px; color: #bfbfbf; margin-top: -4px; }

/* 成本 */
.eb-cost { display: flex; align-items: flex-start; gap: 24px; }
.eb-cost-value { display: flex; flex-direction: column; align-items: center; min-width: 110px; }
.eb-cost-num { font-size: 20px; font-weight: 700; color: #bfbfbf; }
.eb-cost-cap { font-size: 11px; color: #8c8c8c; }
.eb-cost-meta { flex: 1; min-width: 0; }
.eb-cost-meta p { margin: 0 0 4px; font-size: 12px; color: #262626; display: flex; align-items: center; gap: 8px; }
.eb-meta-key { color: #8c8c8c; min-width: 64px; }
.eb-meta-val { color: #262626; }
.eb-meta-tip { color: #8c8c8c !important; font-size: 11px !important; line-height: 1.6; display: block !important; }
/* 目标成本就地编辑（2026-10-30 用户要求：GM 在这个卡片里就能设置，不必进编辑弹窗） */
.eb-target-input { width: 168px; }
.eb-target-unit { color: #8c8c8c; margin-left: -4px; }
.eb-target-set { font-size: 12px; }
.eb-target-empty { color: #bfbfbf; }
.eb-target-readonly { font-size: 11px; color: #bfbfbf; }

/* 表格（风险 / 排名共用） */
.eb-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.eb-table th {
  text-align: left; font-weight: 500; color: #8c8c8c; padding: 4px 6px;
  border-bottom: 1px solid #f0f0f0; white-space: nowrap;
}
.eb-table td { padding: 5px 6px; border-bottom: 1px solid #fafafa; color: #262626; }
</style>
