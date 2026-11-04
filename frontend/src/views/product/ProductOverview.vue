<template>
  <div class="po-page">
    <!-- 页头组织与系列/型号详情页同一套：左摘要 · 中面包屑+标题 · 右统计+描述 -->
    <header class="po-header">
      <div class="po-header-main">
        <!-- 左：整机视角摘要。本页不分阶段，故这一格放"整体进度"（门的通过数 + 交付件完成数），
             位置与详情页的「当前阶段」卡片对齐；说明文字走 title 悬停（页头寸土寸金） -->
        <div v-if="line" class="po-summary">
          <div class="po-summary-current">
            <BarChartOutlined class="po-summary-icon" />
            <span class="po-summary-title" title="系列 / 型号整体的统计视角：上部作战地图（阶段就绪度 · 交付件矩阵 · 评审状态）· 下部指标">整机视图</span>
            <!--
              返回放在「整机视图」标题右侧（2026-10-29 用户要求）：原来挤在标题行里、夹在编码标签与
              描述之间，既不好找也把中间那列撑得很长 —— 它是"离开本页"的动作，跟页头标题待在一起才对。
            -->
            <a-button class="po-summary-back" size="small" @click="goBack">
              <template #icon><ArrowLeftOutlined /></template>返回
            </a-button>
          </div>
          <div class="po-summary-row">
            <a-tag :color="gateTagColor" :title="'本容器的阶段门通过情况（已排除已停用的门）'">{{ gateTagText }}</a-tag>
            <a-tag :color="deliverableTagColor" :title="'口径与阶段门的 DELIVERABLE_RATE 一致：排除「本项目不适用」与 NA，只有 DONE 算完成'">{{ deliverableTagText }}</a-tag>
          </div>
        </div>

        <!-- 中：面包屑 + 标题 -->
        <div class="po-header-info">
          <a-breadcrumb class="po-breadcrumb">
            <a-breadcrumb-item><router-link to="/product">产品系列</router-link></a-breadcrumb-item>
            <!-- 产品型号：显示所属产品系列 -->
            <a-breadcrumb-item v-if="line?.nodeType === 'PRODUCT_MODEL'">
              {{ parentLineName || '加载中...' }}
            </a-breadcrumb-item>
            <!-- 产品系列且有父级：显示父系列 -->
            <a-breadcrumb-item v-else-if="line?.parentOid && parentLineName">
              {{ parentLineName }}
            </a-breadcrumb-item>
            <a-breadcrumb-item>{{ line?.name || '加载中...' }}</a-breadcrumb-item>
            <a-breadcrumb-item>整机视图</a-breadcrumb-item>
          </a-breadcrumb>

          <div class="po-title-row">
            <a-tag :color="nodeTagColor" size="small">{{ nodeTagText }}</a-tag>
            <h2 class="po-title">{{ line?.name || '—' }}</h2>
            <a-tag v-if="line?.code" color="default" class="po-code-tag">{{ line.code }}</a-tag>
          </div>
        </div>

        <!-- 右：规模统计在上，描述与创建信息在它下面 -->
        <div v-if="line" class="po-header-side">
          <div class="po-header-stats">
            <div class="po-stat-pill">
              <ExperimentOutlined class="po-stat-pill-icon" style="color:#722ed1" />
              <span class="po-stat-pill-num">{{ stages.length }}</span>
              <span class="po-stat-pill-label">阶段</span>
            </div>
            <div class="po-stat-pill">
              <AuditOutlined class="po-stat-pill-icon" style="color:#1677ff" />
              <span class="po-stat-pill-num">{{ overall.reviewTotal }}</span>
              <span class="po-stat-pill-label">评审</span>
            </div>
            <div class="po-stat-pill">
              <SafetyCertificateOutlined class="po-stat-pill-icon" style="color:#fa8c16" />
              <span class="po-stat-pill-num">{{ overall.ruleTotal }}</span>
              <span class="po-stat-pill-label">检查规则</span>
            </div>
          </div>

          <!-- 描述是变长的：自己截断（悬停看全），"创建于"始终留在可见位置 -->
          <p class="po-meta">
            <span v-if="line.description" class="po-meta-desc" :title="line.description">{{ line.description }}</span>
            <span v-if="line.description" class="po-meta-sep" />
            <span class="po-meta-created">创建于 {{ formatTime(line.createdAt) }}</span>
          </p>
        </div>
      </div>
    </header>

    <!-- 驾驶舱 —— 页签：经营战报 / 系列战况简报（系列）或 型号作战地图（型号）/ 数据指标。
         视角按容器归属挂载，默认落在本容器的作战图；数据指标排在作战图之后（用户 2026-10-26） -->
    <section v-if="has('DASHBOARD') || has('METRIC_MANAGEMENT')" class="po-section">
      <a-tabs
        v-if="dashboards.length || has('METRIC_MANAGEMENT')"
        v-model:activeKey="activeTabKey"
        size="small"
        class="po-dash-tabs"
      >
        <a-tab-pane v-for="d in dashboards" :key="d.oid">
          <template #tab>
            <span class="po-dash-tab">
              <StarFilled v-if="d.code === ownDashboardCode" class="po-dash-star" title="本容器的作战图（默认页签）" />
              {{ d.name }}
              <span v-if="d.audience" class="po-dash-aud">{{ d.audience }}</span>
            </span>
          </template>

          <!-- 视角类型即稳定身份（code）：作战地图 → 三块视图；经营战报 → 四块经营视图 -->
          <ModelBattleMap
            v-if="d.code === 'PRODUCT_MODEL'"
            :stages="stages"
            :deliverables="instances.deliverables"
            :gates="instances.gates"
            :reviews="instances.reviews"
            :widgets="widgetsOf(d)"
            :loading="loading || stagesLoading"
            @open-stage="openStage"
          />
          <ExecutiveBoard
            v-else-if="d.code === 'ENTERPRISE'"
            :owner-oid="oid"
            :series-oid="seriesOid"
            :widgets="widgetsOf(d)"
            :deliverables="instances.deliverables"
            :gates="instances.gates"
            :reviews="instances.reviews"
            :metrics="instances.metrics"
            :loading="loading || stagesLoading"
          />

          <SeriesBriefing
            v-else-if="d.code === 'PRODUCT_LINE'"
            :owner-oid="oid"
            :series-oid="seriesOid"
            :widgets="widgetsOf(d)"
            :stages="stages"
            :deliverables="instances.deliverables"
            :gates="instances.gates"
            :reviews="instances.reviews"
            :loading="loading || stagesLoading"
          />

          <!-- 其余视角（包外新增 / 老数据）没有渲染器：列出分组件，说明待接 -->
          <div v-else class="po-widget-list">
            <div v-for="w in widgetsOf(d)" :key="w" class="po-widget-item">
              <span class="po-widget-name">{{ widgetLabel(w) }}</span>
              <a-tag size="small" color="default">{{ w }}</a-tag>
              <span class="po-widget-hint">暂无渲染实现</span>
            </div>
            <a-empty v-if="!widgetsOf(d).length" description="该视角未声明分组件" />
          </div>
        </a-tab-pane>

        <!-- 数据指标：不是"视角"，是计算口径 + 数据就绪度清单 —— 排在作战图之后 -->
        <a-tab-pane v-if="has('METRIC_MANAGEMENT')" :key="METRICS_TAB_KEY">
          <template #tab>
            <span class="po-dash-tab">数据指标</span>
          </template>
          <MethodologyInstancePanel kind="metric" :rows="instances.metrics" :loading="loading" />
        </a-tab-pane>
      </a-tabs>
      <a-empty v-else description="本容器尚未生成驾驶舱视角" />
    </section>

    <a-empty
      v-if="!has('DASHBOARD') && !has('METRIC_MANAGEMENT') && !loading"
      description="本企业未启用驾驶舱 / 指标能力"
      style="margin-top: 48px"
    />
  </div>
</template>

<script setup>
/**
 * 整机视图页（系列 / 型号整体）—— 上部驾驶舱 + 下部指标。
 *
 * <p>来源：2026-10-11 从 ProductLineDashboard 的页尾"整机视角"抽成独立页（用户要求）：
 *  · 系列/型号详情页的页头新增「整机视图」入口，页尾内嵌区块移除；
 *  · 本页不含阶段过滤 —— ck_metric / ck_dashboard 无 stage_key，本就挂"系列/型号整体"；
 *  · 能力开关与详情页同源（useCapabilities；账本由 MainLayout 统一加载，本页只读）。
 *
 * <p>2026-10-26：驾驶舱从"一行标签"改为三个页签（经营战报 / 系列战况简报 / 型号作战地图），
 * 默认落在 ★主视角；有渲染件的视角走 ModelBattleMap（阶段就绪度 / 交付件矩阵 / 评审状态），
 * 其余视角如实列出它声明的分组件码。数据都在 /api/instances 一次返回里，只需再补一次 getStages。
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined, AuditOutlined, BarChartOutlined,
  ExperimentOutlined, SafetyCertificateOutlined, StarFilled
} from '@ant-design/icons-vue'
import { getProductLine, getStages, listMethodologyInstances } from '@/api'
import MethodologyInstancePanel from '@/components/MethodologyInstancePanel.vue'
import ModelBattleMap from './ModelBattleMap.vue'
import ExecutiveBoard from './ExecutiveBoard.vue'
import SeriesBriefing from './SeriesBriefing.vue'
import { parseWidgets, widgetLabel } from '@/utils/dashboardWidgets'
import { isDecisionGate } from '@/utils/gateDefs'
import { useCapabilities } from '@/composables/useCapabilities'

const route = useRoute()
const router = useRouter()
const { has } = useCapabilities()

const oid = computed(() => route.params.oid)
const line = ref(null)
const parentLineName = ref('')
/** 六类一次取回（与产品页同一接口，量小不做拆分）：驾驶舱要用交付件 / 门 / 评审 */
const instances = ref({
  deliverables: [], gates: [], reviews: [], rules: [], metrics: [], dashboards: []
})
/** 本容器的研发阶段（阶段条 / 矩阵列 / 评审归位都靠它） */
const stages = ref([])
const loading = ref(false)
const stagesLoading = ref(false)

/** 详情加载：与 ProductLineDashboard 同款（getProductLine 对系列 / 型号统一） */
async function loadLine() {
  if (!oid.value) return
  try {
    const res = await getProductLine(oid.value)
    if (res.code === 200) line.value = res.data
    // parentOid 统一表示父级（系列自身 parentOid 或型号的归属产品系列）
    const parentOid = line.value?.parentOid || line.value?.productLineOid
    if (parentOid) {
      const parentRes = await getProductLine(parentOid)
      if (parentRes.code === 200) parentLineName.value = parentRes.data.name || '上级产品系列'
    } else {
      parentLineName.value = ''
    }
  } catch { message.error('加载系列 / 型号信息失败') }
}

async function loadInstances() {
  if (!oid.value) return
  loading.value = true
  try {
    const res = await listMethodologyInstances(oid.value)
    if (res?.code === 200 && res.data) {
      instances.value = {
        deliverables: res.data.deliverables || [],
        gates: res.data.gates || [],
        reviews: res.data.reviews || [],
        rules: res.data.rules || [],
        metrics: res.data.metrics || [],
        dashboards: res.data.dashboards || []
      }
    }
  } catch { message.error('加载整机视图数据失败') }
  finally { loading.value = false }
}

/** 阶段清单：矩阵的行列与"当前阶段"都以它为准（拉不到就退化为空，不阻断驾驶舱） */
async function loadStages() {
  if (!oid.value) return
  stagesLoading.value = true
  try {
    const res = await getStages(oid.value)
    stages.value = res?.code === 200 ? (res.data || []) : []
  } catch { stages.value = [] }
  finally { stagesLoading.value = false }
}

/**
 * 当前容器是系列还是型号 —— 决定挂哪张作战图（用户 2026-10-26：系列作战图挂系列、型号作战图挂型号）。
 *
 * <p>优先看主数据（line.nodeType）；主数据还没回来时用视角实例自带的 ownerType 兜底
 * （同一 owner 的快照，两者不会打架），避免先渲染一版再跳动的闪动。
 */
const containerOwnerType = computed(() => {
  if (line.value?.nodeType) {
    return line.value.nodeType === 'PRODUCT_MODEL' ? 'MODEL' : 'LINE'
  }
  return instances.value.dashboards[0]?.ownerType || 'LINE'
})

/** 本容器自己的作战图码：系列 → 系列战况简报，型号 → 型号作战地图 */
const ownDashboardCode = computed(() =>
  containerOwnerType.value === 'MODEL' ? 'PRODUCT_MODEL' : 'PRODUCT_LINE'
)

/**
 * 驾驶舱页签 = 属于本容器的视角：
 *  · 经营战报（ENTERPRISE）—— 两边都有，企业级视角，任何容器都能看；
 *  · 系列战况简报（PRODUCT_LINE）—— 只在系列上有；
 *  · 型号作战地图（PRODUCT_MODEL）—— 只在型号上有。
 * 停用的视角不出现；顺序按 sortOrder。
 */
const dashboards = computed(() =>
  (instances.value.dashboards || [])
    .filter((d) => {
      if (d.disabled) return false
      if (d.code === 'PRODUCT_LINE') return containerOwnerType.value !== 'MODEL'
      if (d.code === 'PRODUCT_MODEL') return containerOwnerType.value === 'MODEL'
      return true
    })
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
)

/** 「数据指标」页签的固定 key —— 它不对应任何 dashboard 实例，只是页签位 */
const METRICS_TAB_KEY = '__metrics__'

/** 当前页签：dashboard 实例 oid，或 {@link METRICS_TAB_KEY} */
const activeTabKey = ref(null)
watch(
  dashboards,
  (list) => {
    if (!list.length) return
    // 已选中的视角还在就不动它（切容器时它不在新列表里 → 回到本容器的作战图）
    if (activeTabKey.value && list.some((d) => d.oid === activeTabKey.value)) return
    activeTabKey.value = (list.find((d) => d.code === ownDashboardCode.value) || list[0]).oid
  },
  { immediate: true }
)

/** 某视角声明的分组件码（widgets 是 JSON 字符串，解析失败按空处理） */
const widgetsOf = (d) => parseWidgets(d?.widgets)

/** 型号排名要按"同系列"取型号清单：当前是型号就往上找系列，是系列就用自己 */
const seriesOid = computed(() => line.value?.parentOid || line.value?.productLineOid || line.value?.oid || '')

/** 下钻：详情页对应阶段 + 页签（交付件 / 评审）—— 阶段以 code 传，详情页按 code 匹配 */
function openStage({ code, tab }) {
  if (!code) return
  router.push({ path: `/product/${oid.value}`, query: { stage: code, tab: tab || 'bom' } })
}

/** 页头摘要与规模统计：全部取自已加载的实例，不发额外请求 */
const overall = computed(() => {
  // 交付件分母与阶段门 DELIVERABLE_RATE 同一口径：排除裁剪与 NA
  const ds = (instances.value.deliverables || []).filter((d) => !d.disabled && d.status !== 'NA')
  // 阶段门只算决策门：老库残留的技术门行没有决策、永远不会 PASSED，算进来会让"门未通过 x/y"永远红着
  const gs = (instances.value.gates || []).filter((g) => isDecisionGate(g) && !g.disabled)
  return {
    deliverableDone: ds.filter((d) => d.status === 'DONE').length,
    deliverableTotal: ds.length,
    gatePassed: gs.filter((g) => g.status === 'PASSED').length,
    gateTotal: gs.length,
    reviewTotal: (instances.value.reviews || []).filter((r) => !r.disabled).length,
    ruleTotal: (instances.value.rules || []).filter((r) => !r.disabled).length
  }
})

/** 门汇总：与详情页「当前阶段的概括」同一个句式（门未通过 x/y），只是这里算的是整机 */
const gateTagText = computed(() => {
  const { gatePassed, gateTotal } = overall.value
  if (!gateTotal) return '本容器未配置门'
  return gatePassed === gateTotal ? `门已通过 ${gatePassed}/${gateTotal}` : `门未通过 ${gatePassed}/${gateTotal}`
})
const gateTagColor = computed(() =>
  overall.value.gateTotal && overall.value.gatePassed === overall.value.gateTotal ? 'green' : 'default'
)

/** 交付件完成数：没有应交付项时写 0/0（不写 100% —— 那会把"没东西要交"说成"全做完了"） */
const deliverableTagText = computed(
  () => `交付件 ${overall.value.deliverableDone}/${overall.value.deliverableTotal}`
)
const deliverableTagColor = computed(() =>
  overall.value.deliverableTotal && overall.value.deliverableDone === overall.value.deliverableTotal
    ? 'green' : 'default'
)

/** 节点类型标签：与详情页页头同一个色/词（产品型号紫 / 子系列绿 / 根系列蓝） */
const nodeTagText = computed(() =>
  line.value?.nodeType === 'PRODUCT_MODEL' ? '产品型号' : (line.value?.parentOid ? '子系列' : '根系列')
)
const nodeTagColor = computed(() =>
  line.value?.nodeType === 'PRODUCT_MODEL' ? 'purple' : (line.value?.parentOid ? 'green' : 'blue')
)

/** ISO 时间 → 可读（与详情页同一套截断方式） */
function formatTime(s) {
  return s ? s.replace('T', ' ').substring(0, 19) : '—'
}

/** 返回来源页（详情页）；直接打开本 URL（无历史）时回列表 */
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/product')
}

watch(oid, () => { loadLine(); loadInstances(); loadStages() })
onMounted(() => { loadLine(); loadInstances(); loadStages() })
</script>

<style scoped>
/* ===== 设计变量：与系列/型号详情页同一套取值 —— 页头要能一眼认出是"同族页面" ===== */
.po-page {
  --po-text: #1a1a2e;
  --po-text-muted: #8c8c8c;
  --po-border: #f0f0f0;
  --po-bg: #fafbfc;
  /* 上 / 左右不再额外留白：外层 layout-content 已有 16px / 24px，再叠一层会让页头离顶栏
     多出 12px、左右各多 16px，与阶段页（.pl-dashboard，0）对不上（2026-10-30 用户反馈
     "整机视图的红框间隙太大，参考概念阶段页"）。底部留 12px，内层滚动到底时不贴边 */
  padding: 0 0 12px;
  height: 100%;
  overflow: auto;
  box-sizing: border-box;
}

/* ===== 页头：左摘要 · 中面包屑+标题 · 右统计+描述 ===== */
.po-header {
  background: #fff;
  border: 1px solid var(--po-border);
  border-radius: 8px;
  padding: 12px 20px 10px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .04);
  position: relative;
  overflow: hidden;
}
.po-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #1677ff, #722ed1, #eb2f96);
}
.po-header-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
/* 三块的左右次序用 order：摘要最左、标题块吃满中间空档并贴住统计列、统计最右 */
.po-header-main > .po-summary { order: 1; }
.po-header-main > .po-header-info { order: 2; margin-left: auto; }
.po-header-main > .po-header-side { order: 3; }

/* 左：摘要（站详情页「当前阶段」那个位置；本页不分阶段，故放整体进度） */
.po-summary {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding-right: 16px;
  border-right: 1px solid var(--po-border);
}
.po-summary-current { display: flex; align-items: center; gap: 8px; min-width: 0; }
.po-summary-icon { font-size: 14px; color: #1677ff; }
.po-summary-title { font-size: 15px; font-weight: 600; color: var(--po-text); }
/* 返回挂在标题右侧：不参与收缩，窄屏也不折行（页头三列会整体换行，按钮跟着标题走） */
.po-summary-back { flex-shrink: 0; white-space: nowrap; }
.po-summary-row { display: flex; align-items: center; gap: 8px; }

/* 中：面包屑 + 标题 */
.po-header-info { min-width: 0; }
.po-breadcrumb { margin-bottom: 2px; }
.po-title-row { display: flex; align-items: center; gap: 10px; }
.po-title { margin: 0; font-size: 22px; font-weight: 700; color: var(--po-text); letter-spacing: -.3px; }
.po-code-tag { font-family: monospace; font-size: 12px; }

/* 右：统计 pill 在上，描述/创建信息在它下面（同一列、靠右） */
.po-header-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
  max-width: 52%;
  min-width: 0;
}
.po-header-stats { display: flex; gap: 8px; flex-shrink: 0; }
.po-stat-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  background: var(--po-bg);
  border: 1px solid var(--po-border);
  cursor: default;
}
.po-stat-pill-icon { font-size: 18px; flex-shrink: 0; }
.po-stat-pill-num { font-size: 20px; font-weight: 700; color: var(--po-text); line-height: 1; }
.po-stat-pill-label { font-size: 12px; color: var(--po-text-muted); }

/* 描述是变长的：由它自己截断（悬停看全），"创建于"始终留在可见位置 */
.po-meta {
  margin: 0;
  font-size: 12px;
  color: var(--po-text-muted);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
}
.po-meta-desc { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.po-meta-created { flex-shrink: 0; }
.po-meta-sep { display: inline-block; width: 3px; height: 3px; border-radius: 50%; background: #d9d9d9; flex-shrink: 0; }

/* ===== 主体区块 ===== */
/* 页签自带标题，不再有 section 级标题（2026-10-26：数据指标并入页签后，标题重复） */
.po-section { margin-top: 14px; }

/* ===== 驾驶舱页签（经营战报 / 系列战况简报 / 型号作战地图） ===== */
.po-dash-tabs :deep(.ant-tabs-nav) { margin-bottom: 8px; }
.po-dash-tab { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; }
.po-dash-star { color: #faad14; font-size: 12px; }
/* 受众（GM / GM+RDD / RD/PM）：贴着视角名，别让它抢占 tab 的点击面积 */
.po-dash-aud {
  padding: 0 6px;
  font-size: 11px;
  color: var(--po-text-muted);
  background: var(--po-bg);
  border: 1px solid var(--po-border);
  border-radius: 10px;
}

/* 只有码、没有渲染实现的视角：分组件清单（一行一个码，说明待接） */
.po-widget-list { display: flex; flex-direction: column; gap: 6px; padding: 2px 0; }
.po-widget-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px dashed var(--po-border);
  border-radius: 6px;
  background: #fcfcfc;
}
.po-widget-name { font-size: 13px; font-weight: 500; color: #262626; }
.po-widget-hint { margin-left: auto; font-size: 12px; color: #bfbfbf; }
</style>
